import { expect, test, type Locator, type Page } from "@playwright/test";
import { stubApiAvailability } from "./helpers/auth";
import { tr } from "./helpers/locale";
import { gotoReady } from "./helpers/ready";

type ToggleField = "password" | "confirmPassword";
interface KeyRecord {
  type: string;
  key: string;
  repeat: boolean;
  field: string;
}
type RecordedForm = HTMLFormElement & {
  repeatEvents?: KeyRecord[];
  removeRepeatRecorder?: () => void;
};

const PASSWORD = "Valid 019";
const MODES = [
  { name: "ordinary", bootstrap: false, language: "en-US", mobile: false },
  { name: "bootstrap", bootstrap: true, language: "zh-CN", mobile: true },
] as const;

async function installRecorder(page: Page) {
  await page.locator("form").evaluate((element) => {
    const form = element as RecordedForm;
    form.repeatEvents = [];
    const record = (event: Event) => {
      if (!(event instanceof KeyboardEvent)) return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      const button = target.closest("button.ant-input-password-icon");
      if (!button) return;
      form.repeatEvents!.push({
        type: event.type,
        key: event.key,
        repeat: event.repeat,
        field:
          button.closest(".ant-input-affix-wrapper")?.querySelector("input")
            ?.id ?? "missing-input",
      });
    };
    form.addEventListener("keydown", record, true);
    form.addEventListener("keyup", record, true);
    form.removeRepeatRecorder = () => {
      form.removeEventListener("keydown", record, true);
      form.removeEventListener("keyup", record, true);
    };
  });
}

async function recordedKeys(page: Page): Promise<KeyRecord[]> {
  return page
    .locator("form")
    .evaluate((element) => (element as RecordedForm).repeatEvents ?? []);
}

async function fieldsState(page: Page) {
  return page.locator(".ant-input-password").evaluateAll((wrappers) =>
    wrappers.map((wrapper) => {
      const input = wrapper.querySelector<HTMLInputElement>("input")!;
      const button = wrapper.querySelector<HTMLButtonElement>("button")!;
      const rect = button.getBoundingClientRect();
      return {
        id: input.id,
        type: input.type,
        value: input.value,
        pressed: button.getAttribute("aria-pressed"),
        name: button.getAttribute("aria-label"),
        focused: document.activeElement === button,
        width: rect.width,
        height: rect.height,
      };
    }),
  );
}

function toggleFor(input: Locator) {
  return input
    .locator("xpath=..", {})
    .locator("button.ant-input-password-icon");
}

for (const mode of MODES) {
  test.describe(`Password key repeat (${mode.name}, ${mode.language})`, () => {
    test.use({
      viewport: mode.mobile
        ? { width: 390, height: 600 }
        : { width: 1440, height: 900 },
      hasTouch: mode.mobile,
    });

    for (const key of ["Space", "Enter"] as const) {
      test(`${key} holds toggle once, release safely and do not submit valid forms`, async ({
        page,
      }, testInfo) => {
        await stubApiAvailability(page);
        await page.addInitScript((language) => {
          localStorage.setItem("tds-lang", language);
        }, mode.language);
        await page.route("**/api/v1/auth/bootstrap/status", (route) =>
          route.fulfill({ json: { bootstrapRequired: mode.bootstrap } }),
        );
        const posts = { bootstrap: 0, login: 0 };
        for (const endpoint of ["bootstrap", "login"] as const) {
          await page.route(`**/api/v1/auth/${endpoint}`, (route) => {
            posts[endpoint] += 1;
            return route.fulfill({
              status: 400,
              json: { message: "Unexpected visibility-control submission" },
            });
          });
        }
        await gotoReady(page, "/login");
        await page
          .getByLabel(tr(mode.language, "login.username"), { exact: true })
          .fill("admin_019");
        const fields: ToggleField[] = mode.bootstrap
          ? ["password", "confirmPassword"]
          : ["password"];
        for (const field of fields) {
          await page
            .getByLabel(tr(mode.language, `login.${field}`), { exact: true })
            .fill(PASSWORD);
        }
        if (mode.bootstrap) {
          await page
            .getByLabel(tr(mode.language, "login.displayName"), { exact: true })
            .fill("Key repeat regression");
        }
        await installRecorder(page);
        const snapshots: Array<{
          stage: string;
          fields: Awaited<ReturnType<typeof fieldsState>>;
          posts: typeof posts;
        }> = [];

        try {
          for (const field of fields) {
            const input = page.getByLabel(tr(mode.language, `login.${field}`), {
              exact: true,
            });
            const button = toggleFor(input);
            await input.focus();
            await page.keyboard.press("Tab");
            await expect(button).toBeFocused();
            await expect(button).toHaveAttribute("type", "button");
            await expect(button).toBeInViewport();
            const outline = await button.evaluate((element) => {
              const style = getComputedStyle(element);
              return { style: style.outlineStyle, width: style.outlineWidth };
            });
            expect(outline.style).not.toBe("none");
            expect(parseFloat(outline.width)).toBeGreaterThanOrEqual(2);
            const initial = await fieldsState(page);

            const assertState = async (
              stage: string,
              visible: boolean,
              keyboardFocus = true,
            ) => {
              const state = await fieldsState(page);
              snapshots.push({
                stage: `${field}:${stage}`,
                fields: state,
                posts: { ...posts },
              });
              await expect(input).toHaveAttribute(
                "type",
                visible ? "text" : "password",
              );
              await expect(input).toHaveValue(PASSWORD);
              await expect(button).toHaveAttribute(
                "aria-pressed",
                String(visible),
              );
              await expect(button).toHaveAccessibleName(
                tr(
                  mode.language,
                  visible ? "login.hidePassword" : "login.showPassword",
                ),
              );
              if (keyboardFocus) await expect(button).toBeFocused();
              for (const item of state) {
                expect(item.value).toBe(PASSWORD);
                expect(item.width).toBeGreaterThanOrEqual(44);
                expect(item.height).toBeGreaterThanOrEqual(44);
                if (item.id !== `login_${field}`) {
                  expect(item).toMatchObject(
                    initial.find((previous) => previous.id === item.id)!,
                  );
                }
              }
              expect(posts).toEqual({ bootstrap: 0, login: 0 });
            };

            // Each cycle has one initial keydown, three OS-style repeats and keyup.
            // The second cycle starts visible, and must still toggle only once.
            for (const visible of [true, false]) {
              const before = (await recordedKeys(page)).length;
              try {
                await page.keyboard.down(key);
                await assertState(`${key}:initial-${visible}`, visible);
                for (let repeat = 1; repeat <= 3; repeat += 1) {
                  await page.keyboard.down(key);
                  await assertState(
                    `${key}:repeat-${repeat}-${visible}`,
                    visible,
                  );
                }
              } finally {
                await page.keyboard.up(key);
              }
              await assertState(`${key}:released-${visible}`, visible);
              expect((await recordedKeys(page)).slice(before)).toEqual([
                {
                  type: "keydown",
                  key: key === "Space" ? " " : "Enter",
                  repeat: false,
                  field: `login_${field}`,
                },
                ...Array.from({ length: 3 }, () => ({
                  type: "keydown",
                  key: key === "Space" ? " " : "Enter",
                  repeat: true,
                  field: `login_${field}`,
                })),
                {
                  type: "keyup",
                  key: key === "Space" ? " " : "Enter",
                  repeat: false,
                  field: `login_${field}`,
                },
              ]);
            }

            await page.keyboard.press(key);
            await assertState(`${key}:independent-press-1`, true);
            await page.keyboard.press(key);
            await assertState(`${key}:independent-press-2`, false);
            if (mode.mobile) await button.tap();
            else await button.click();
            await assertState("pointer-click-1", true, false);
            if (mode.mobile) await button.tap();
            else await button.click();
            await assertState("pointer-click-2", false, false);
          }
          await expect(page.locator(".ant-form-item-has-error")).toHaveCount(0);
          await expect(page).toHaveURL(/\/login$/);
          expect(posts).toEqual({ bootstrap: 0, login: 0 });
        } finally {
          const events = await recordedKeys(page);
          await testInfo.attach("password-key-repeat", {
            contentType: "application/json",
            body: JSON.stringify(
              { mode, key, events, snapshots, posts },
              null,
              2,
            ),
          });
          await page.locator("form").evaluate((element) => {
            const form = element as RecordedForm;
            form.removeRepeatRecorder?.();
            delete form.repeatEvents;
            delete form.removeRepeatRecorder;
          });
        }
      });
    }
  });
}
