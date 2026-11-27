import { expect, test, type Locator, type Page } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { stubApiAvailability } from "./helpers/auth";
import { tr, type E2ELanguage } from "./helpers/locale";
import { gotoReady } from "./helpers/ready";

type Preference = "light" | "dark" | "system-dark";
type RGBA = [number, number, number, number];

interface ButtonSample {
  state: string;
  foreground: string;
  background: string;
  composedForeground: number[];
  composedBackground: number[];
  fontSize: string;
  fontWeight: string;
  ratio: number;
  hover: boolean;
  active: boolean;
  focusVisible: boolean;
}

// Wait for this control's final colors, rather than waiting for global animations
// (the login page can contain an intentionally infinite spinner).
async function settleColors(control: Locator) {
  await control.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
  );
  let previous = "";
  let stableSince = 0;
  await expect
    .poll(
      async () => {
        const colors = await control.evaluate((element) => {
          const style = getComputedStyle(element);
          return JSON.stringify([
            style.color,
            style.backgroundColor,
            style.borderColor,
            style.boxShadow,
          ]);
        });
        if (colors !== previous) {
          previous = colors;
          stableSince = Date.now();
        }
        return Date.now() - stableSince >= 200;
      },
      { timeout: 3_000, intervals: [30, 50] },
    )
    .toBe(true);
}

async function sampleButton(
  button: Locator,
  state: string,
): Promise<ButtonSample> {
  await settleColors(button);
  return button.evaluate((element, name) => {
    function rgba(value: string): RGBA {
      const match = /^rgba?\(([^)]+)\)$/.exec(value);
      if (!match) throw new Error(`Unsupported computed color: ${value}`);
      const channels = match[1]
        .split(/[\s,/]+/)
        .filter(Boolean)
        .map(Number);
      if (
        (channels.length !== 3 && channels.length !== 4) ||
        channels.some((channel) => !Number.isFinite(channel))
      )
        throw new Error(`Invalid computed color: ${value}`);
      return [channels[0], channels[1], channels[2], channels[3] ?? 1];
    }
    function over(front: RGBA, back: RGBA): RGBA {
      const alpha = front[3] + back[3] * (1 - front[3]);
      if (!alpha) return [0, 0, 0, 0];
      return [0, 1, 2]
        .map(
          (index) =>
            (front[index] * front[3] + back[index] * back[3] * (1 - front[3])) /
            alpha,
        )
        .concat(alpha) as RGBA;
    }
    const layers: RGBA[] = [];
    for (
      let current: Element | null = element;
      current;
      current = current.parentElement
    ) {
      const style = getComputedStyle(current);
      if (style.backgroundImage !== "none" || Number(style.opacity) !== 1)
        throw new Error(
          "Contrast sampling requires resolved solid backgrounds and opacity=1",
        );
      const layer = rgba(style.backgroundColor);
      layers.push(layer);
      if (layer[3] === 1) break;
    }
    if (layers.at(-1)?.[3] !== 1)
      throw new Error(
        "No opaque ancestor background: contrast cannot be resolved",
      );
    const background = layers
      .reverse()
      .reduce((back, front) => over(front, back));
    const style = getComputedStyle(element);
    const foreground = over(rgba(style.color), background);
    function luminance(color: RGBA) {
      const channels = color.slice(0, 3).map((channel) => {
        const srgb = channel / 255;
        return srgb <= 0.04045 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
      });
      return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
    }
    const values = [luminance(foreground), luminance(background)].sort(
      (a, b) => b - a,
    );
    return {
      state: name,
      foreground: style.color,
      background: style.backgroundColor,
      composedForeground: foreground.slice(0, 3),
      composedBackground: background.slice(0, 3),
      fontSize: style.fontSize,
      fontWeight: style.fontWeight,
      ratio: (values[0] + 0.05) / (values[1] + 0.05),
      hover: element.matches(":hover"),
      active: element.matches(":active"),
      focusVisible: element.matches(":focus-visible"),
    };
  }, state);
}

async function keyboardFocusSubmit(page: Page, button: Locator) {
  for (let index = 0; index < 12; index += 1) {
    await page.keyboard.press("Tab");
    if (await button.evaluate((element) => document.activeElement === element))
      break;
  }
  await expect(button).toBeFocused();
  await expect
    .poll(() => button.evaluate((element) => element.matches(":focus-visible")))
    .toBe(true);
}

for (const language of ["zh-CN", "en-US"] as const) {
  for (const preference of ["light", "dark", "system-dark"] as const) {
    for (const bootstrap of [false, true]) {
      test(`login primary text contrast: ${language}, ${preference}, ${bootstrap ? "bootstrap" : "ordinary"}`, async ({
        page,
      }, testInfo) => {
        await stubApiAvailability(page);
        await page.emulateMedia({ colorScheme: "dark" });
        await page.addInitScript(
          ({ lang, mode }: { lang: E2ELanguage; mode: Preference }) => {
            localStorage.clear();
            localStorage.setItem("tds-lang", lang);
            if (mode !== "system-dark") localStorage.setItem("tds-theme", mode);
          },
          { lang: language, mode: preference },
        );
        await page.route("**/api/v1/auth/bootstrap/status", (route) =>
          route.fulfill({ json: { bootstrapRequired: bootstrap } }),
        );
        const submitted: string[] = [];
        for (const endpoint of ["bootstrap", "login"]) {
          await page.route(`**/api/v1/auth/${endpoint}`, (route) => {
            submitted.push(endpoint);
            return route.fulfill({ status: 400, json: {} });
          });
        }
        await gotoReady(page, "/login");
        const button = page.locator(".tds-login__submit button[type='submit']");
        const label = tr(
          language,
          bootstrap ? "login.createAdmin" : "login.login",
        );
        await expect(button).toBeEnabled();
        await expect(button).not.toHaveClass(/ant-btn-loading/);
        await expect(button).toHaveAccessibleName(label);
        const samples: ButtonSample[] = [];

        await page.mouse.move(1, 1);
        samples.push(await sampleButton(button, "normal"));
        await button.hover();
        samples.push(await sampleButton(button, "hover"));
        const evidenceName = `${language}-${preference}-${bootstrap ? "bootstrap" : "ordinary"}`;
        const evidenceDir = process.env.TDS_LOGIN_EVIDENCE_DIR;
        if (evidenceDir) {
          await mkdir(evidenceDir, { recursive: true });
          await page.screenshot({
            path: path.join(evidenceDir, `${evidenceName}-hover.png`),
            fullPage: true,
          });
        }
        await page.mouse.move(1, 1);
        await keyboardFocusSubmit(page, button);
        samples.push(await sampleButton(button, "keyboard-focus"));

        await button.hover();
        await page.mouse.down();
        try {
          samples.push(await sampleButton(button, "hover-active"));
          await page.mouse.move(1, 1);
          samples.push(await sampleButton(button, "active-without-hover"));
        } finally {
          await page.mouse.up();
          await page.mouse.move(1, 1);
        }

        const fieldStates = [];
        for (const field of ["username", "password"] as const) {
          const input = page.getByLabel(tr(language, `login.${field}`), {
            exact: true,
          });
          const wrapper = input.locator("xpath=..");
          await input.hover();
          await settleColors(wrapper);
          const hoverBorder = await wrapper.evaluate(
            (element) => getComputedStyle(element).borderColor,
          );
          await page.mouse.move(1, 1);
          await input.focus();
          await settleColors(wrapper);
          await expect(input).toBeFocused();
          const focus = await wrapper.evaluate((element) => {
            const style = getComputedStyle(element);
            return { border: style.borderColor, shadow: style.boxShadow };
          });
          expect(focus.shadow).not.toBe("none");
          fieldStates.push({ field, hoverBorder, focus });
        }
        const expectedTheme = preference === "light" ? "light" : "dark";
        await expect(page.locator("html")).toHaveAttribute(
          "data-theme",
          expectedTheme,
        );
        expect(
          await page.evaluate(() => localStorage.getItem("tds-theme")),
        ).toBe(expectedTheme);
        expect(submitted).toEqual([]);
        const report = {
          language,
          preference,
          bootstrap,
          samples,
          fieldStates,
          submitted,
        };
        await testInfo.attach("login-button-contrast", {
          body: JSON.stringify(report, null, 2),
          contentType: "application/json",
        });
        if (evidenceDir)
          await writeFile(
            path.join(evidenceDir, `${evidenceName}.json`),
            JSON.stringify(report, null, 2) + "\n",
          );

        expect(samples[0].hover).toBe(false);
        expect(samples[1].hover).toBe(true);
        expect(samples[2].focusVisible).toBe(true);
        expect(samples[2].hover).toBe(false);
        expect(samples[3].active).toBe(true);
        expect(samples[3].hover).toBe(true);
        expect(samples[4].active).toBe(true);
        expect(samples[4].hover).toBe(false);
        for (const sample of samples)
          expect(
            sample.ratio,
            `${sample.state}: ${sample.foreground} on ${sample.background}`,
          ).toBeGreaterThanOrEqual(4.5);
      });
    }
  }
}

// The existing login/readiness suites retain disabled, bootstrap-pending,
// stable loading names, failure/retry and duplicate-submit behavior coverage.
