import { expect, test, type Locator, type Page } from "@playwright/test";
import { setAuthState } from "./helpers/auth";
import { tableQueryField } from "./helpers/queryFilter";
import { gotoReady, waitForAppReady, waitForTableReady } from "./helpers/ready";

/** INTERACTION-014: workspaces whose sidebar has a collapsible parent menu. */
const WORKSPACE_SUBMENU_SCENARIOS = [
  { workspace: "Threat operations", parent: "Investigate" },
  { workspace: "Platform health", parent: "Operate" },
  { workspace: "Organization", parent: "Admin" },
  { workspace: "Configuration", parent: "Configuration" },
] as const;

type WorkspaceSubmenuScenario = (typeof WORKSPACE_SUBMENU_SCENARIOS)[number];

/**
 * Switch workspace and wait until the switch has completed: switcher label,
 * committed route of the workspace (app-ready contract) and its parent menu.
 */
async function switchWorkspaceAndWait(page: Page, workspace: string) {
  const switcher = page.getByRole("button", { name: "Switch workspace" });
  await switcher.click();
  await page.getByRole("menuitem").filter({ hasText: workspace }).click();
  await expect(switcher).toContainText(workspace);
  await waitForAppReady(page);
}

/** One atomic browser-side snapshot of the submenu (all fields read in the same task). */
interface MotionFrame {
  time: number;
  source: "start" | "mutation" | "frame";
  className: string;
  inlineHeight: string;
  computedHeight: string;
  rectHeight: number;
  scrollHeight: number;
  maxHeight: string;
  opacity: string;
  transform: string;
  transitionDuration: string;
}

interface MotionRecorderHost {
  __tdsMotionRecorder?: { frames: MotionFrame[]; stop: () => void };
}

const MOTION_FRAME_LIMIT = 600;

/**
 * INTERACTION-014/016: record the submenu from *before* the click on.
 *
 * Motion classes exist for ~210ms (leave) / ~260ms (enter). Waiting for a
 * class in Node and then reading styles in a second round trip is a TOCTOU
 * race: the animation can end in between (computed height back to `auto`).
 * Each frame here is one synchronous snapshot taken on every class/style
 * mutation and on every animation frame while a motion class is present, so
 * "class is active" and "geometry" always belong to the same frame.
 * Call `stop()` (in `finally`): disconnects the observer, cancels rAF and
 * removes the test-only property from the node.
 */
async function recordSubmenuMotionFrames(element: Locator) {
  await element.evaluate((node, limit) => {
    const host = node as HTMLElement & MotionRecorderHost;
    host.__tdsMotionRecorder?.stop();
    const frames: MotionFrame[] = [];
    const sample = (source: MotionFrame["source"]) => {
      if (frames.length >= limit) return;
      const style = window.getComputedStyle(host);
      frames.push({
        time: performance.now(),
        source,
        className: host.className,
        inlineHeight: host.style.height,
        computedHeight: style.height,
        rectHeight: host.getBoundingClientRect().height,
        scrollHeight: host.scrollHeight,
        maxHeight: style.maxHeight,
        opacity: style.opacity,
        transform: style.transform,
        transitionDuration: style.transitionDuration,
      });
    };
    const observer = new MutationObserver(() => sample("mutation"));
    observer.observe(host, { attributes: true, attributeFilter: ["class", "style"] });
    let rafId = 0;
    const tick = () => {
      if (/tds-submenu-motion-(?:enter|leave)/.test(host.className)) sample("frame");
      rafId = frames.length < limit ? requestAnimationFrame(tick) : 0;
    };
    rafId = requestAnimationFrame(tick);
    sample("start");
    host.__tdsMotionRecorder = {
      frames,
      stop: () => {
        observer.disconnect();
        cancelAnimationFrame(rafId);
        delete host.__tdsMotionRecorder;
      },
    };
  }, MOTION_FRAME_LIMIT);
  return {
    frames: () => element.evaluate((node) => (node as HTMLElement & MotionRecorderHost).__tdsMotionRecorder?.frames ?? []),
    /** Safe after a failure or detach. */
    stop: () =>
      element
        .evaluate((node) => (node as HTMLElement & MotionRecorderHost).__tdsMotionRecorder?.stop(), undefined, { timeout: 1_000 })
        .catch(() => undefined),
  };
}

const LEAVE_ACTIVE = /tds-submenu-motion-leave-active/;
const ENTER_ACTIVE = /tds-submenu-motion-enter-active/;

/** `matrix(a, b, c, d, tx, ty)` → ty; `none` → 0; anything else → NaN. */
function translateY(transform: string): number {
  if (transform === "none") return 0;
  const values = /^matrix\(([^)]+)\)$/.exec(transform)?.[1].split(",").map(Number);
  return values?.length === 6 ? values[5] : Number.NaN;
}

/** Compact failure detail: class history + every motion frame with raw values. */
function describeFrames(frames: MotionFrame[]): string {
  const t0 = frames[0]?.time ?? 0;
  return frames
    .map((frame) =>
      [
        `+${(frame.time - t0).toFixed(1)}ms ${frame.source}`,
        `class="${frame.className.replace(/ant-menu(?:-sub|-inline)? /g, "").trim()}"`,
        `inline=${frame.inlineHeight || "-"} computed=${frame.computedHeight} rect=${frame.rectHeight} scroll=${frame.scrollHeight}`,
        `max=${frame.maxHeight} opacity=${frame.opacity} transform=${frame.transform} duration=${frame.transitionDuration}`,
      ].join(" "),
    )
    .join("\n");
}

/** A leave-active frame with finite in-range geometry and the leave transition. */
function validLeaveFrame(frame: MotionFrame, naturalHeight: number): boolean {
  const opacity = Number.parseFloat(frame.opacity);
  const ty = translateY(frame.transform);
  return (
    LEAVE_ACTIVE.test(frame.className) &&
    Number.isFinite(frame.rectHeight) &&
    frame.rectHeight >= 0 &&
    frame.rectHeight <= naturalHeight + 0.5 &&
    frame.transitionDuration.includes("0.21s") &&
    frame.maxHeight === "none" &&
    Number.isFinite(opacity) &&
    opacity >= 0 &&
    opacity <= 1 &&
    Number.isFinite(ty) &&
    ty <= 0 && // collapsing upwards (towards translate3d(0, -5px, 0))
    ty >= -5.5
  );
}

/**
 * An enter-active frame whose inline target height is the measured content
 * height: equal to that frame's scrollHeight *and* to the natural height
 * measured before the collapse (scrollHeight alone grows with an oversized box).
 */
function validEnterFrame(frame: MotionFrame, naturalHeight: number): boolean {
  const target = /^(\d+(?:\.\d+)?)px$/.exec(frame.inlineHeight);
  const opacity = Number.parseFloat(frame.opacity);
  return (
    ENTER_ACTIVE.test(frame.className) &&
    !!target &&
    Number.isFinite(Number(target[1])) &&
    Number(target[1]) === frame.scrollHeight &&
    Number(target[1]) === naturalHeight &&
    Number.isFinite(frame.rectHeight) &&
    frame.rectHeight >= 0 &&
    frame.rectHeight <= naturalHeight + 0.5 &&
    frame.transitionDuration.includes("0.26s") &&
    Number.isFinite(opacity)
  );
}

/** Collapse then expand the workspace parent menu and check the real motion. */
async function assertWorkspaceSubmenuMotion(page: Page, scenario: WorkspaceSubmenuScenario) {
  const parent = page
    .locator(".tds-primary-menu .ant-menu-submenu-title")
    .filter({ hasText: scenario.parent });
  await expect(parent).toHaveCount(1);
  await expect(parent).toBeVisible();
  await expect(parent).toHaveAttribute("aria-expanded", "true");
  const controlsId = await parent.getAttribute("aria-controls");
  expect(controlsId).toBeTruthy();
  const submenu = page.locator(`[id="${controlsId}"]`);
  await expect(submenu).not.toHaveClass(/ant-menu-hidden/);

  // Collapse: leave motion runs, then the list is hidden within the deadline.
  let recorder = await recordSubmenuMotionFrames(submenu);
  try {
    await parent.click();
    await expect(parent).toHaveAttribute("aria-expanded", "false");
    await expect.poll(async () => (await recorder.frames()).some((frame) => /tds-submenu-motion-leave-(?:start|active)/.test(frame.className)), {
      message: `${scenario.parent}: leave motion classes were applied`,
    }).toBe(true);
    await expect(submenu).toHaveClass(/ant-menu-hidden/, { timeout: 600 });
  } finally {
    await recorder.stop();
  }

  // Expand: enter motion runs and its classes are cleaned up within the deadline.
  recorder = await recordSubmenuMotionFrames(submenu);
  try {
    await parent.click();
    await expect(parent).toHaveAttribute("aria-expanded", "true");
    await expect.poll(async () => (await recorder.frames()).some((frame) => /tds-submenu-motion-enter-active/.test(frame.className)), {
      message: `${scenario.parent}: enter motion classes were applied`,
    }).toBe(true);
    await expect(submenu).not.toHaveClass(/tds-submenu-motion-enter-active/, {
      timeout: 700,
    });
    await expect(submenu).not.toHaveClass(/ant-menu-hidden/);
  } finally {
    await recorder.stop();
  }
}

test.describe("Navigation and routing", () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN");
  });

  // INTERACTION-011: one independent test per route instead of 14 full
  // page loads under a single 30s test budget.
  for (const route of [
    "/overview",
    "/investigate/alerts",
    "/investigate/threats",
    "/investigate/intel",
    "/operate/pipeline",
    "/operate/ml",
    "/admin/customers",
    "/admin/users",
    "/admin/tenants",
    "/config/general",
    "/config/notifications",
    "/config/integrations",
    "/config/ai",
    "/config/plugins",
  ]) {
    test(`main route ${route} is accessible for SUPER_ADMIN`, async ({ page }) => {
      await gotoReady(page, route);
      await expect(page).toHaveURL(new RegExp(`${route.replace(/\//g, "\\/")}$`));
      await expect(page.locator(`.tds-shell[data-route-path="${route}"]`)).toHaveCount(1);
    });
  }

  test("workspace switcher exposes all permitted workspaces", async ({
    page,
  }) => {
    await gotoReady(page, "/overview");

    await page.getByRole("button", { name: "Switch workspace" }).click();
    await expect(
      page.getByRole("menuitem").filter({ hasText: "Threat operations" }),
    ).toBeVisible();
    await expect(
      page.getByRole("menuitem").filter({ hasText: "Platform health" }),
    ).toBeVisible();
    await expect(
      page.getByRole("menuitem").filter({ hasText: "Organization" }),
    ).toBeVisible();
    await expect(
      page.getByRole("menuitem").filter({ hasText: "Configuration" }),
    ).toBeVisible();
  });

  test("workspace switcher stays geometrically stable while pressed", async ({
    page,
  }) => {
    await gotoReady(page, "/overview");

    const switcher = page.getByRole("button", { name: "Switch workspace" });
    await switcher.hover();
    const initialBox = await switcher.boundingBox();
    const hoverStyle = await switcher.evaluate((element) => {
      const style = window.getComputedStyle(element);
      return {
        backgroundColor: style.backgroundColor,
        borderColor: style.borderColor,
      };
    });
    expect(initialBox).not.toBeNull();

    await page.mouse.move(
      (initialBox?.x ?? 0) + (initialBox?.width ?? 0) / 2,
      (initialBox?.y ?? 0) + (initialBox?.height ?? 0) / 2,
    );
    await page.mouse.down();
    await page.waitForTimeout(200);

    const pressedBox = await switcher.boundingBox();
    const pressedStyle = await switcher.evaluate((element) => {
      const style = window.getComputedStyle(element);
      return {
        backgroundColor: style.backgroundColor,
        borderColor: style.borderColor,
        transform: style.transform,
      };
    });
    expect(pressedStyle.transform).toBe("none");
    expect(pressedStyle.backgroundColor).not.toBe(hoverStyle.backgroundColor);
    expect(pressedStyle.borderColor).not.toBe(hoverStyle.borderColor);
    expect(pressedBox).toEqual(initialBox);

    await page.mouse.up();
    await expect(
      page.getByRole("menuitem").filter({ hasText: "Platform health" }),
    ).toBeVisible();
    expect(await switcher.boundingBox()).toEqual(initialBox);
  });

  test("sidebar runtime status stays pinned while the main content scrolls", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 640 });
    await gotoReady(page, "/overview");

    const sider = page.locator(".tds-sider");
    const runtimeStatus = sider.locator(".tds-navigation__footer");
    const collapseTrigger = sider.locator(".ant-layout-sider-trigger");
    await expect(runtimeStatus).toBeVisible();
    await expect(collapseTrigger).toBeVisible();

    await page.locator("#main-content").evaluate((element) => {
      const spacer = document.createElement("div");
      spacer.dataset.scrollFixture = "true";
      spacer.style.height = "1600px";
      spacer.setAttribute("aria-hidden", "true");
      element.appendChild(spacer);
    });

    const initialStatusBox = await runtimeStatus.boundingBox();
    const initialTriggerBox = await collapseTrigger.boundingBox();
    expect(initialStatusBox).not.toBeNull();
    expect(initialTriggerBox).not.toBeNull();

    const content = page.locator("#main-content");
    await content.evaluate((element) =>
      element.scrollTo({ top: 900, behavior: "instant" }),
    );
    await expect
      .poll(() => content.evaluate((element) => element.scrollTop))
      .toBeGreaterThan(500);

    const scrolledStatusBox = await runtimeStatus.boundingBox();
    const scrolledTriggerBox = await collapseTrigger.boundingBox();
    const siderStyle = await sider.evaluate((element) => {
      const style = window.getComputedStyle(element);
      return {
        height: Number.parseFloat(style.height),
        position: style.position,
        top: style.top,
      };
    });

    expect(siderStyle.position).toBe("relative");
    expect(Math.abs(siderStyle.height - 640)).toBeLessThan(1);
    expect(await page.evaluate(() => window.scrollY)).toBe(0);
    expect(
      Math.abs((scrolledStatusBox?.y ?? 0) - (initialStatusBox?.y ?? 0)),
    ).toBeLessThan(1);
    expect(
      Math.abs((scrolledTriggerBox?.y ?? 0) - (initialTriggerBox?.y ?? 0)),
    ).toBeLessThan(1);
    const footerToTriggerGap =
      (scrolledTriggerBox?.y ?? 0) -
      ((scrolledStatusBox?.y ?? 0) + (scrolledStatusBox?.height ?? 0));
    expect(footerToTriggerGap).toBeGreaterThanOrEqual(-1);
    expect(footerToTriggerGap).toBeLessThanOrEqual(8);
  });

  test("opening a modal preserves the scrolled app shell geometry", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 640 });
    await gotoReady(page, "/config/ai");

    const content = page.locator("#main-content");
    const sider = page.locator(".tds-sider");
    const runtimeStatus = sider.locator(".tds-navigation__footer");
    const collapseTrigger = sider.locator(".ant-layout-sider-trigger");
    const header = page.locator(".tds-header");
    const addProvider = page
      .getByRole("button")
      .filter({ hasText: "Add LLM Provider" });

    await content.evaluate((element) =>
      element.scrollTo({ top: 240, behavior: "instant" }),
    );
    await expect
      .poll(() => content.evaluate((element) => element.scrollTop))
      .toBeGreaterThan(100);
    await expect(addProvider).toBeVisible();

    const scrollTop = await content.evaluate((element) => element.scrollTop);
    const initialStatusBox = await runtimeStatus.boundingBox();
    const initialTriggerBox = await collapseTrigger.boundingBox();
    const initialHeaderBox = await header.boundingBox();

    await addProvider.click();
    await expect(
      page.getByRole("dialog", { name: "Add LLM Provider" }),
    ).toBeVisible();

    expect(await content.evaluate((element) => element.scrollTop)).toBe(
      scrollTop,
    );
    expect(await runtimeStatus.boundingBox()).toEqual(initialStatusBox);
    expect(await collapseTrigger.boundingBox()).toEqual(initialTriggerBox);
    expect(await header.boundingBox()).toEqual(initialHeaderBox);
    expect(await page.evaluate(() => window.scrollY)).toBe(0);
    expect(
      await page.evaluate(() => window.getComputedStyle(document.body).overflowY),
    ).toBe("hidden");

    await page.getByRole("button", { name: "Cancel", exact: true }).click();
    await expect(
      page.getByRole("dialog", { name: "Add LLM Provider" }),
    ).toBeHidden();
    expect(await content.evaluate((element) => element.scrollTop)).toBe(
      scrollTop,
    );
  });

  test("horizontal table search labels align with their controls", async ({ page }) => {
    await gotoReady(page, "/admin/customers");
    await waitForTableReady(page.locator(".tds-content"));

    // INTERACTION-013: app-owned query hook + accessible field name, not
    // Pro Components' internal class names.
    const field = tableQueryField(page, /Customer ID/);
    await expect(field.input).toBeVisible();
    const { label, control } = field;
    await expect(label).toBeVisible();
    await expect(control).toBeVisible();

    const [labelBox, controlBox] = await Promise.all([
      label.boundingBox(),
      control.boundingBox(),
    ]);
    expect(labelBox).not.toBeNull();
    expect(controlBox).not.toBeNull();
    const labelCenter = (labelBox?.y ?? 0) + (labelBox?.height ?? 0) / 2;
    const controlCenter = (controlBox?.y ?? 0) + (controlBox?.height ?? 0) / 2;
    // The local UMD verification build renders ProTable with its own antd copy
    // (no 38px app control height); centring is only meaningful with Vite.
    if (process.env.E2E_LOCAL_UMD_BUILD === "1") {
      test.info().annotations.push({ type: "local-umd-build", description: "label/control centring not checked" });
      return;
    }
    expect(Math.abs(labelCenter - controlCenter)).toBeLessThanOrEqual(2);
  });

  test("legacy URLs redirect to new paths", async ({ page }) => {
    await gotoReady(page, "/threats");
    await expect(page).toHaveURL(/\/investigate\/threats$/);

    await gotoReady(page, "/pipeline");
    await expect(page).toHaveURL(/\/operate\/pipeline$/);
  });

  test("default route redirects to /overview", async ({ page }) => {
    await gotoReady(page, "/");
    await expect(page).toHaveURL(/\/overview$/);
  });

  test("page context identifies the active sub-page", async ({ page }) => {
    await gotoReady(page, "/config/general");
    await expect(page.getByRole("heading", { name: "General" })).toBeVisible();
  });

  test("menu highlight and route content transition stay synchronized", async ({
    page,
  }) => {
    await page.addInitScript(() => {
      const nativeAnimate = Element.prototype.animate;
      Object.defineProperty(Element.prototype, "animate", {
        configurable: true,
        writable: true,
        value: function animate(
          this: Element,
          keyframes: Keyframe[] | PropertyIndexedKeyframes | null,
          options?: number | KeyframeAnimationOptions,
        ) {
          const animation = nativeAnimate.call(this, keyframes, options);
          if (
            this instanceof HTMLElement &&
            this.classList.contains("tds-route-stage")
          ) {
            this.dataset.routeAnimationStarted = "true";
            animation.updatePlaybackRate(0.1);
          }
          return animation;
        },
      });
    });

    await gotoReady(page, "/overview");
    const indicator = page.locator(".tds-menu-selection-indicator");
    const routeStage = page.locator("[data-route-stage]");
    await expect(indicator).toBeVisible();
    await expect(routeStage).not.toHaveAttribute(
      "data-route-animation-started",
      "true",
    );
    const initialBox = await indicator.boundingBox();

    await page.getByRole("link", { name: "Alert Center" }).click();
    await expect(page).toHaveURL(/\/investigate\/alerts$/);

    await expect(routeStage).toHaveAttribute(
      "data-route",
      "/investigate/alerts",
    );
    await expect(routeStage).toHaveAttribute(
      "data-route-animation-started",
      "true",
    );

    const transitionFrame = await routeStage.evaluate((element) => {
      const style = window.getComputedStyle(element);
      return {
        opacity: Number(style.opacity),
        transform: style.transform,
      };
    });
    expect(transitionFrame.opacity).toBeLessThan(1);
    expect(transitionFrame.transform).not.toBe("none");

    const selectedItem = page.locator(".ant-menu-item-selected");
    await expect(selectedItem).toContainText("Alert Center");
    await expect
      .poll(async () => {
        const [indicatorBox, selectedBox] = await Promise.all([
          indicator.boundingBox(),
          selectedItem.boundingBox(),
        ]);
        if (!indicatorBox || !selectedBox) return Number.POSITIVE_INFINITY;
        return Math.max(
          Math.abs(indicatorBox.x - selectedBox.x),
          Math.abs(indicatorBox.y - selectedBox.y),
          Math.abs(indicatorBox.width - selectedBox.width),
          Math.abs(indicatorBox.height - selectedBox.height),
        );
      })
      .toBeLessThan(1);

    const finalBox = await indicator.boundingBox();
    expect(initialBox).not.toBeNull();
    expect(finalBox).not.toBeNull();
    expect(Math.abs((finalBox?.y ?? 0) - (initialBox?.y ?? 0))).toBeGreaterThan(
      20,
    );
  });

  test("rapid navigation settles on one highlight without residual animation", async ({
    page,
  }) => {
    await gotoReady(page, "/overview");

    await page.getByRole("link", { name: "Threat List" }).click();
    await page.getByRole("link", { name: "Threat Intel" }).click();
    await expect(page).toHaveURL(/\/investigate\/intel$/);

    const indicator = page.locator(".tds-menu-selection-indicator");
    const selectedItem = page.locator(".ant-menu-item-selected");
    await expect(indicator).toHaveCount(1);
    await expect(selectedItem).toContainText("Threat Intel");
    await page.waitForTimeout(360);

    for (let sample = 0; sample < 3; sample += 1) {
      const geometryDifference = await Promise.all([
        indicator.boundingBox(),
        selectedItem.boundingBox(),
      ]).then(([indicatorBox, selectedBox]) => {
        if (!indicatorBox || !selectedBox) return Number.POSITIVE_INFINITY;
        return Math.max(
          Math.abs(indicatorBox.x - selectedBox.x),
          Math.abs(indicatorBox.y - selectedBox.y),
          Math.abs(indicatorBox.width - selectedBox.width),
          Math.abs(indicatorBox.height - selectedBox.height),
        );
      });
      expect(geometryDifference).toBeLessThan(1);
      await page.waitForTimeout(50);
    }

    const activeRouteAnimations = await page
      .locator("[data-route-stage]")
      .evaluate(
        (element) =>
          element
            .getAnimations()
            .filter((animation) => animation.playState === "running").length,
      );
    expect(activeRouteAnimations).toBe(0);
  });

  test("submenu uses measured height and synchronized enter and leave motion", async ({
    page,
  }) => {
    await gotoReady(page, "/investigate/alerts");

    const parent = page
      .locator(".tds-primary-menu .ant-menu-submenu-title")
      .filter({ hasText: "Investigate" });
    await expect(parent).toHaveAttribute("aria-expanded", "true");
    const controlsId = await parent.getAttribute("aria-controls");
    expect(controlsId).toBeTruthy();
    const submenu = page.locator(`[id="${controlsId}"]`);
    const naturalHeight = await submenu.evaluate(
      (element) => (element as HTMLElement).scrollHeight,
    );
    expect(naturalHeight).toBeGreaterThan(100);

    // INTERACTION-016: frames are recorded from before the click; "leave is
    // active" and "geometry" are judged on the *same* frame (no second read
    // after the class wait, no parseFloat of a possibly-`auto` CSS height).
    let recorder = await recordSubmenuMotionFrames(submenu);
    try {
      await parent.click();
      await expect
        .poll(async () => (await recorder.frames()).some((frame) => LEAVE_ACTIVE.test(frame.className)), {
          message: "leave-active class was applied",
        })
        .toBe(true);
      await expect(parent).toHaveAttribute("aria-expanded", "false");
      await expect(submenu).toHaveClass(/ant-menu-hidden/, { timeout: 600 });
      const frames = await recorder.frames();
      const leaveFrames = frames.filter((frame) => LEAVE_ACTIVE.test(frame.className));
      const detail = `naturalHeight=${naturalHeight}\n${describeFrames(frames)}`;
      for (const frame of leaveFrames) {
        expect(Number.isFinite(frame.rectHeight), `leave frame geometry is finite\n${detail}`).toBe(true);
      }
      expect(
        leaveFrames.some((frame) => validLeaveFrame(frame, naturalHeight)),
        `a leave-active frame has 0 <= height <= natural, max-height none, the 0.21s transition and an upward transform\n${detail}`,
      ).toBe(true);
      // Height only shrinks while leaving (it may start from a reversed, partial height).
      const heights = leaveFrames.map((frame) => frame.rectHeight);
      expect(heights.every((height, index) => index === 0 || height <= heights[index - 1] + 0.5), `leave height is monotonic\n${detail}`).toBe(true);
    } finally {
      await recorder.stop();
    }

    recorder = await recordSubmenuMotionFrames(submenu);
    try {
      await parent.click();
      await expect
        .poll(async () => (await recorder.frames()).some((frame) => ENTER_ACTIVE.test(frame.className)), {
          message: "enter-active class was applied",
        })
        .toBe(true);
      await expect(parent).toHaveAttribute("aria-expanded", "true");
      await expect(submenu).not.toHaveClass(/tds-submenu-motion-enter-active/, {
        timeout: 700,
      });
      const frames = await recorder.frames();
      const detail = `naturalHeight=${naturalHeight}\n${describeFrames(frames)}`;
      expect(
        frames.some((frame) => validEnterFrame(frame, naturalHeight)),
        `an enter-active frame has inline height === its scrollHeight === natural height (finite px) and the 0.26s transition\n${detail}`,
      ).toBe(true);
    } finally {
      await recorder.stop();
    }
    await expect(
      page.getByRole("link", { name: "Alert Center" }),
    ).toBeVisible();
    await expect(parent.locator(".tds-submenu-chevron")).toHaveClass(
      /tds-submenu-chevron--open/,
    );
    expect(await submenu.getAttribute("style")).not.toContain("height");
  });

  test("rapid submenu reversals settle without residue or highlight drift", async ({
    page,
  }) => {
    await gotoReady(page, "/investigate/alerts");

    const parent = page
      .locator(".tds-primary-menu .ant-menu-submenu-title")
      .filter({ hasText: "Investigate" });
    for (let index = 0; index < 4; index += 1) {
      await parent.click();
      await page.waitForTimeout(40);
    }

    await expect(parent).toHaveAttribute("aria-expanded", "true");
    await page.waitForTimeout(450);
    const menu = page.locator(".tds-primary-menu");
    await expect(
      menu.locator(
        ".tds-submenu-motion-enter-active, .tds-submenu-motion-leave-active",
      ),
    ).toHaveCount(0);
    const runningAnimations = await menu.evaluate(
      (element) =>
        element
          .getAnimations({ subtree: true })
          .filter((animation) => animation.playState === "running").length,
    );
    expect(runningAnimations).toBe(0);

    const indicator = page.locator(".tds-menu-selection-indicator");
    const selectedItem = page.locator(".ant-menu-item-selected");
    await expect(selectedItem).toContainText("Alert Center");
    for (let sample = 0; sample < 3; sample += 1) {
      const difference = await Promise.all([
        indicator.boundingBox(),
        selectedItem.boundingBox(),
      ]).then(([indicatorBox, selectedBox]) => {
        if (!indicatorBox || !selectedBox) return Number.POSITIVE_INFINITY;
        return Math.max(
          Math.abs(indicatorBox.x - selectedBox.x),
          Math.abs(indicatorBox.y - selectedBox.y),
          Math.abs(indicatorBox.width - selectedBox.width),
          Math.abs(indicatorBox.height - selectedBox.height),
        );
      });
      expect(difference).toBeLessThan(1);
      await page.waitForTimeout(50);
    }
  });

  // INTERACTION-014: one independent test per workspace (previously four
  // scenarios in one test exceeded the 30s budget and hid which one failed).
  for (const scenario of WORKSPACE_SUBMENU_SCENARIOS) {
    test(`workspace "${scenario.workspace}" submenu "${scenario.parent}" shares the motion behavior`, async ({
      page,
    }) => {
      await gotoReady(page, "/overview");
      await switchWorkspaceAndWait(page, scenario.workspace);
      await assertWorkspaceSubmenuMotion(page, scenario);
    });
  }

  test("reduced motion removes spatial route animation", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await gotoReady(page, "/overview");

    await page.getByRole("link", { name: "Alert Center" }).click();
    await expect(page).toHaveURL(/\/investigate\/alerts$/);

    const routeStage = page.locator("[data-route-stage]");
    await expect(routeStage).toHaveAttribute(
      "data-route",
      "/investigate/alerts",
    );
    await expect
      .poll(() =>
        routeStage.evaluate(
          (element) =>
            element
              .getAnimations()
              .filter((animation) => animation.playState !== "finished").length,
        ),
      )
      .toBe(0);
  });

  test("reduced motion resolves submenu state without lingering animation", async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await gotoReady(page, "/investigate/alerts");

    const parent = page
      .locator(".tds-primary-menu .ant-menu-submenu-title")
      .filter({ hasText: "Investigate" });
    await expect(parent).toHaveAttribute("aria-expanded", "true");
    const controlsId = await parent.getAttribute("aria-controls");
    expect(controlsId).toBeTruthy();
    const submenu = page.locator(`[id="${controlsId}"]`);

    await parent.click();
    await expect(parent).toHaveAttribute("aria-expanded", "false");
    await expect(submenu).toHaveClass(/ant-menu-hidden/, { timeout: 500 });
    const runningAnimations = await page
      .locator(".tds-primary-menu")
      .evaluate(
        (element) =>
          element
            .getAnimations({ subtree: true })
            .filter((animation) => animation.playState === "running").length,
      );
    expect(runningAnimations).toBe(0);
    await expect(parent.locator(".tds-submenu-chevron")).not.toHaveClass(
      /tds-submenu-chevron--open/,
    );
  });
});
