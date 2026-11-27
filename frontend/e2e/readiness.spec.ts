import { expect, test, type Route } from "@playwright/test";
import { getApiMockReport, setAuthState, stubApiAvailability } from "./helpers/auth";
import {
  describeAppState,
  gotoReady,
  isAppRootMounted,
  waitForAppReady,
  waitForAppRootMounted,
  waitForOverviewReady,
  waitForTableReady,
} from "./helpers/ready";

/** Attribute checks after the root is confirmed mounted (not a mount wait). */
const ATTRIBUTE_TIMEOUT = 1_000;

/**
 * INTERACTION-011: the readiness contract itself.
 *
 * - It must name the layer that is not ready (here: Scope never finishes).
 * - Legitimate error / empty states must still count as "ready" so that
 *   specs can assert them (no "never ready" for a failed load).
 */
test.describe("readiness contract", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("a Scope that never initialises is reported as such, not as a missing locator", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    const pending: Route[] = [];
    await page.route("**/api/v1/tenants/search**", (route) => {
      pending.push(route); // never answered while the assertion runs
    });
    try {
      await page.goto("/overview");
      // INTERACTION-015: shell mounted under the cold-start budget, without
      // requiring data-app-ready=true (Scope is pending on purpose).
      await waitForAppRootMounted(page);
      const shell = page.locator(".tds-shell");
      // The root is confirmed above: short explicit windows, never the default.
      await expect(shell).toHaveAttribute("data-route-path", "/overview", { timeout: ATTRIBUTE_TIMEOUT });
      await expect(shell).toHaveAttribute("data-app-ready", "false", { timeout: ATTRIBUTE_TIMEOUT });
      const failure = await waitForAppReady(page, { timeout: 4_000 }).then(
        () => undefined,
        (error: Error) => error.message,
      );
      expect(failure).toMatch(/^App not ready within 4000ms: Scope \/ login bootstrap not initialised \(data-app-ready=false\)/);
      expect(failure).toContain("url=http://localhost:3000/overview");
    } finally {
      // Also on failure: no request left hanging into teardown / reporting.
      await Promise.all(pending.map((route) => route.abort().catch(() => undefined)));
    }
  });

  test("the root-mount wait accepts an uninitialised root and names the missing layer", async ({ page }) => {
    // Static DOM on about:blank (path "blank"): the predicate only, no app.
    const mounted = (html: string, timeout = 1_000) =>
      page.setContent(html).then(() =>
        waitForAppRootMounted(page, { timeout }).then(
          () => "mounted",
          (error: Error) => error.message,
        ),
      );
    expect(await mounted('<main data-app-ready="false" data-route-path="blank"></main>')).toBe("mounted");
    expect(await mounted('<div class="tds-app-loading"></div><main data-app-ready="false" data-route-path="blank"></main>')).toMatch(
      /^App root mount not ready within 1000ms: global Suspense fallback/,
    );
    expect(await mounted("<main></main>")).toMatch(/no app shell, login page or harness root mounted/);
    expect(await mounted('<main data-app-ready="true" data-route-path="/overview"></main>')).toMatch(
      /route not committed: URL path blank, rendered \/overview/,
    );
    const base = { url: "http://localhost:3000/overview", path: "/overview", spinning: 0, busy: 0, globalLoading: false };
    expect(isAppRootMounted(undefined)).toBe(false);
    expect(isAppRootMounted({ ...base, roots: [{ ready: "false", route: "/overview" }] })).toBe(true);
    expect(isAppRootMounted({ ...base, roots: [{ ready: "true", route: "/login" }] })).toBe(false);
    expect(isAppRootMounted({ ...base, globalLoading: true, roots: [{ ready: "true", route: "/overview" }] })).toBe(false);
  });

  test("every unready layer has its own message", () => {
    const base = { url: "http://localhost:3000/overview", path: "/overview", spinning: 0, busy: 0 };
    expect(describeAppState(undefined)).toContain("navigating");
    expect(describeAppState({ ...base, globalLoading: true, roots: [] })).toContain("global Suspense fallback");
    expect(describeAppState({ ...base, globalLoading: false, roots: [] })).toContain("no app shell");
    expect(describeAppState({ ...base, globalLoading: false, roots: [{ ready: "false", route: "/overview" }] })).toContain(
      "Scope / login bootstrap not initialised",
    );
    expect(describeAppState({ ...base, globalLoading: false, roots: [{ ready: "true", route: "/investigate/alerts" }] })).toContain(
      "route not committed: URL path /overview, rendered /investigate/alerts",
    );
    expect(describeAppState({ ...base, globalLoading: false, roots: [{ ready: "true", route: "/overview" }] })).toMatch(/^ready;/);
  });

  test("a failed Scope request still ends initialisation (app becomes ready)", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    await page.route("**/api/v1/tenants/search**", (route) =>
      route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "scope failure" }) }),
    );
    await gotoReady(page, "/overview");
    await expect(page.locator(".tds-shell")).toHaveAttribute("data-app-ready", "true");
  });

  test("the login page is ready once the bootstrap check settles, even when it fails", async ({ page }) => {
    await stubApiAvailability(page);
    await page.route("**/api/v1/auth/bootstrap/status", (route) =>
      route.fulfill({ status: 503, contentType: "application/json", body: "{}" }),
    );
    await gotoReady(page, "/login");
    await expect(page.locator(".tds-login")).toHaveAttribute("data-app-ready", "true");
    await expect(page.locator(".tds-login")).toHaveAttribute("data-route-path", "/login");
  });

  test("bootstrap checking blocks login submission and finishes readiness when released", async ({ page }) => {
    await stubApiAvailability(page);
    await page.addInitScript(() => localStorage.setItem("tds-lang", "en-US"));
    const pending: Route[] = [];
    let loginRequests = 0;
    await page.route("**/api/v1/auth/bootstrap/status", (route) => { pending.push(route); });
    await page.route("**/api/v1/auth/login", (route) => {
      loginRequests += 1;
      return route.fulfill({ json: {} });
    });
    try {
      await page.goto("/login");
      // Deliberately pending: the root-mount contract is the correct wait here.
      await waitForAppRootMounted(page);
      await expect(page.locator(".tds-login")).toHaveAttribute("data-app-ready", "false", { timeout: ATTRIBUTE_TIMEOUT });
      await expect(page.locator(".tds-login [role='status']")).toBeVisible();
      const submit = page.locator(".tds-login__submit button[type='submit']");
      await expect(submit).toHaveClass(/ant-btn-loading/);
      await page.getByLabel("Username", { exact: true }).fill("admin");
      await page.getByLabel("Password", { exact: true }).fill("admin123");
      const box = await submit.boundingBox();
      await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);
      expect(loginRequests).toBe(0);
      await expect.poll(() => pending.length).toBeGreaterThan(0);
      await Promise.all(pending.splice(0).map((route) => route.fulfill({ json: { bootstrapRequired: false } })));
      await waitForAppReady(page);
      await expect(submit).not.toHaveClass(/ant-btn-loading/);
      await expect(page.locator(".tds-login")).toHaveAttribute("data-route-path", "/login", { timeout: ATTRIBUTE_TIMEOUT });
    } finally {
      await Promise.all(pending.map((route) => route.abort().catch(() => undefined)));
    }
  });

  test("an empty table is a ready state in 'empty' mode", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    await page.route("**/api/v1/users**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
    );
    await gotoReady(page, "/admin/users");
    await waitForTableReady(page.locator(".tds-content"), { mode: "empty" });
  });

  test("Overview readiness covers data, empty and error outcomes", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    let status = 500;
    await page.route("**/api/v1/assessment/overview-snapshot**", (route) =>
      status === 500
        ? route.fulfill({ status, contentType: "application/json", body: JSON.stringify({ message: "boom" }) })
        : route.fallback(),
    );
    await gotoReady(page, "/overview");
    await waitForOverviewReady(page);
    await expect(page.locator(".tds-overview__error")).toBeVisible();
    status = 200;
    await page.locator(".tds-overview__error").getByRole("button").click();
    await waitForOverviewReady(page);
    await expect(page.locator(".tds-overview__error")).toHaveCount(0);
    expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
  });
});
