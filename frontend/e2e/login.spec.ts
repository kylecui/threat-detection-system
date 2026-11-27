import { expect, test, type Page, type Route } from "@playwright/test";
import { stubApiAvailability } from "./helpers/auth";
import { tr } from "./helpers/locale";
import { gotoReady, waitForAppReady } from "./helpers/ready";

const loginResponse = {
  token: "mock-token",
  refreshToken: "mock-refresh-token",
  user: {
    id: 1,
    username: "admin",
    displayName: "Admin",
    roles: ["SUPER_ADMIN"],
    customerId: "demo-customer",
    tenantId: 1,
  },
};

async function requireBootstrap(page: Page) {
  await page.route("**/api/v1/auth/bootstrap/status", (route) =>
    route.fulfill({ json: { bootstrapRequired: true } }),
  );
}

async function assertLoginLayout(page: Page, width: number) {
  const card = await page.locator(".tds-login__card").boundingBox();
  const brand = await page.locator(".tds-login__brand").boundingBox();
  const panel = await page.locator(".tds-login__panel").boundingBox();
  expect(card).not.toBeNull();
  expect(brand).not.toBeNull();
  expect(panel).not.toBeNull();
  expect(card!.x).toBeGreaterThanOrEqual(11);
  expect(card!.x + card!.width).toBeLessThanOrEqual(width - 11);
  expect(Math.abs(card!.x + card!.width / 2 - width / 2)).toBeLessThan(2);
  if (width >= 768) {
    expect(card!.width).toBeGreaterThanOrEqual(700);
    expect(card!.width).toBeLessThanOrEqual(801);
    expect(brand!.x + brand!.width).toBeLessThanOrEqual(panel!.x + 1);
    expect(Math.abs(brand!.y - panel!.y)).toBeLessThan(2);
    expect(brand!.width / card!.width).toBeGreaterThan(0.5);
    expect(brand!.width / card!.width).toBeLessThan(0.56);
  } else {
    expect(brand!.y + brand!.height).toBeLessThanOrEqual(panel!.y + 1);
    expect(Math.abs(brand!.x - panel!.x)).toBeLessThan(2);
    expect(Math.abs(brand!.width - panel!.width)).toBeLessThan(2);
  }
  const documentWidth = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    visible: document.documentElement.clientWidth,
  }));
  expect(documentWidth.scroll).toBeLessThanOrEqual(documentWidth.visible + 1);
  for (const control of await page
    .locator(
      ".tds-login input, .tds-login .ant-input-password-icon, .tds-login button[type='submit']",
    )
    .all()) {
    await control.scrollIntoViewIfNeeded();
    await expect(control).toBeInViewport();
    const box = await control.boundingBox();
    if (
      await control.evaluate((element) =>
        element.matches(".ant-input-password-icon"),
      )
    ) {
      expect(box!.width).toBeGreaterThanOrEqual(44);
      expect(box!.height).toBeGreaterThanOrEqual(44);
    }
    expect(box!.x).toBeGreaterThanOrEqual(panel!.x);
    expect(box!.x + box!.width).toBeLessThanOrEqual(
      panel!.x + panel!.width + 1,
    );
  }
}

test.describe("Login page", () => {
  test.beforeEach(async ({ page }) => {
    await stubApiAvailability(page);
    await page.addInitScript(() => {
      if (!localStorage.getItem("tds-lang"))
        localStorage.setItem("tds-lang", "en-US");
    });
    await page.route("**/api/v1/auth/bootstrap/status", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ bootstrapRequired: false }),
      });
    });
  });

  test("login page renders title, fields, and submit button", async ({
    page,
  }) => {
    await gotoReady(page, "/login");

    await expect(
      page.getByRole("heading", { name: "Threat Detection System" }),
    ).toBeVisible();
    await expect(page.getByLabel("Username")).toBeVisible();
    await expect(page.getByLabel("Password", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Login" })).toBeVisible();
  });

  test("login form validation for empty username/password", async ({
    page,
  }) => {
    await gotoReady(page, "/login");

    await page.getByRole("button", { name: "Login" }).click();

    await expect(page.getByText("Please enter username")).toBeVisible();
    await expect(page.getByText("Please enter password")).toBeVisible();
  });

  test("bootstrap accepts eight-character passwords and rejects out-of-range lengths", async ({
    page,
  }) => {
    let bootstrapPassword = "";
    await page.route("**/api/v1/auth/bootstrap/status", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ bootstrapRequired: true }),
      });
    });
    await page.route("**/api/v1/auth/bootstrap", async (route) => {
      bootstrapPassword = String(route.request().postDataJSON().password ?? "");
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "{}",
      });
    });
    await page.route("**/api/v1/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          token: "bootstrap-token",
          refreshToken: "bootstrap-refresh-token",
          user: {
            id: 1,
            username: "admin",
            displayName: "Admin",
            roles: ["SUPER_ADMIN"],
          },
        }),
      });
    });

    await gotoReady(page, "/login");
    await page.getByLabel("Username").fill("admin");
    await page.getByLabel("Password", { exact: true }).fill("1234567");
    await page.getByLabel("Confirm password").fill("1234567");
    await page
      .getByRole("button", { name: "Create administrator and sign in" })
      .click();
    await expect(page.getByText("Use 8–16 characters")).toBeVisible();
    expect(bootstrapPassword).toBe("");

    await page
      .getByLabel("Password", { exact: true })
      .fill("12345678901234567");
    await page.getByLabel("Confirm password").fill("12345678901234567");
    await page
      .getByRole("button", { name: "Create administrator and sign in" })
      .click();
    await expect(page.getByText("Use 8–16 characters")).toBeVisible();
    expect(bootstrapPassword).toBe("");

    await page.getByLabel("Password", { exact: true }).fill("12345678");
    await page.getByLabel("Confirm password").fill("12345678");
    await page
      .getByRole("button", { name: "Create administrator and sign in" })
      .click();

    await expect.poll(() => bootstrapPassword).toBe("12345678");
    await expect(page).toHaveURL(/\/overview$/);
  });

  test("successful login stores auth and navigates into app", async ({
    page,
  }) => {
    await page.route("**/api/v1/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ...loginResponse,
        }),
      });
    });

    await gotoReady(page, "/login");
    await page.getByLabel("Username").fill("admin");
    await page.getByLabel("Password", { exact: true }).fill("admin123");
    await page.getByRole("button", { name: "Login" }).click();

    await expect(page).toHaveURL(/\/(dashboard|overview)$/);

    const stored = await page.evaluate(() => ({
      token: localStorage.getItem("token"),
      refreshToken: localStorage.getItem("refreshToken"),
      user: JSON.parse(localStorage.getItem("user") || "null"),
      customer: localStorage.getItem("customer_id"),
      tenant: localStorage.getItem("tenant_id"),
    }));
    expect(stored).toEqual({
      token: loginResponse.token,
      refreshToken: loginResponse.refreshToken,
      user: loginResponse.user,
      customer: "demo-customer",
      tenant: "1",
    });
  });

  test("login page is accessible at /login", async ({ page }) => {
    await gotoReady(page, "/login");
    await expect(page).toHaveURL(/\/login$/);
  });

  for (const language of ["zh-CN", "en-US"] as const) {
    test(`split login and expanded bootstrap remain reachable at all target widths (${language})`, async ({
      page,
    }) => {
      await page.addInitScript(
        (value) => localStorage.setItem("tds-lang", value),
        language,
      );
      await gotoReady(page, "/login");
      for (const width of [320, 390, 768, 1024, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await assertLoginLayout(page, width);
      }

      await requireBootstrap(page);
      await gotoReady(page, "/login?reason=session_expired");
      await expect(page.locator(".ant-input-password-icon")).toHaveCount(2);
      await page
        .getByRole("button", {
          name: tr(language, "login.createAdmin"),
          exact: true,
        })
        .click();
      await expect(
        page.getByText(tr(language, "login.validationUsernameRequired"), {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        page.getByText(tr(language, "login.sessionExpired"), { exact: true }),
      ).toBeVisible();
      for (const viewport of [
        { width: 320, height: 600 },
        { width: 390, height: 600 },
        { width: 768, height: 600 },
        { width: 1280, height: 600 },
        // CSS viewport equivalents of 125%, 150% and 200% on a 1280x600 display.
        { width: 1024, height: 480 },
        { width: 853, height: 400 },
        { width: 640, height: 300 },
      ]) {
        await page.setViewportSize(viewport);
        await assertLoginLayout(page, viewport.width);
        await expect(
          page.getByRole("button", {
            name: tr(language, "login.createAdmin"),
            exact: true,
          }),
        ).toBeInViewport();
      }
    });
  }

  for (const preference of ["light", "dark", "system-dark"] as const) {
    test(`login stays light and retains the workspace preference (${preference})`, async ({
      page,
    }) => {
      await page.emulateMedia({ colorScheme: "dark" });
      await page.addInitScript((value) => {
        if (value === "system-dark") localStorage.removeItem("tds-theme");
        else localStorage.setItem("tds-theme", value);
      }, preference);
      await page.route("**/api/v1/auth/login", (route) =>
        route.fulfill({ json: loginResponse }),
      );
      await gotoReady(page, "/login");
      const colors = await page.evaluate(() => {
        const rgb = (selector: string) =>
          getComputedStyle(document.querySelector(selector)!)
            .backgroundColor.match(/\d+/g)!
            .slice(0, 3)
            .map(Number);
        return {
          page: rgb(".tds-login"),
          panel: rgb(".tds-login__panel"),
          input: rgb(".tds-login .ant-input-affix-wrapper"),
        };
      });
      for (const channels of Object.values(colors))
        expect(Math.min(...channels)).toBeGreaterThan(220);
      const expectedTheme = preference === "light" ? "light" : "dark";
      await expect(page.locator("html")).toHaveAttribute(
        "data-theme",
        expectedTheme,
      );
      await page.getByLabel("Username", { exact: true }).fill("admin");
      await page.getByLabel("Password", { exact: true }).fill("admin123");
      await page.getByRole("button", { name: "Login", exact: true }).click();
      await expect(page).toHaveURL(/\/overview$/);
      await expect(page.locator("html")).toHaveAttribute(
        "data-theme",
        expectedTheme,
      );
      expect(await page.evaluate(() => localStorage.getItem("tds-theme"))).toBe(
        expectedTheme,
      );
    });
  }

  test("bootstrap failure and expired-session notices wrap without hiding retry or submit", async ({
    page,
  }) => {
    let failed = true;
    await page.setViewportSize({ width: 320, height: 600 });
    await page.route("**/api/v1/auth/bootstrap/status", (route) =>
      failed
        ? route.fulfill({
            status: 500,
            json: { message: "bootstrap unavailable" },
          })
        : route.fulfill({ json: { bootstrapRequired: true } }),
    );
    await gotoReady(page, "/login?reason=session_expired");
    await expect(
      page.getByText(tr("en-US", "login.platformNotReady"), { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText(tr("en-US", "login.sessionExpired"), { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Login", exact: true }),
    ).toBeDisabled();
    await assertLoginLayout(page, 320);
    failed = false;
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await waitForAppReady(page);
    await expect(
      page.getByText(tr("en-US", "login.platformNotReady"), { exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", {
        name: tr("en-US", "login.createAdmin"),
        exact: true,
      }),
    ).toBeEnabled();
    await assertLoginLayout(page, 320);
  });

  test("bootstrap validates usernames and linked confirmation before accepting sixteen characters", async ({
    page,
  }) => {
    await requireBootstrap(page);
    const bootstrapPasswords: string[] = [];
    let loginRequests = 0;
    await page.route("**/api/v1/auth/bootstrap", (route) => {
      bootstrapPasswords.push(route.request().postDataJSON().password);
      return route.fulfill({
        status: 400,
        json: { message: "bootstrap rejected" },
      });
    });
    await page.route("**/api/v1/auth/login", (route) => {
      loginRequests += 1;
      return route.fulfill({ json: loginResponse });
    });
    await gotoReady(page, "/login");
    const username = page.getByLabel("Username", { exact: true });
    const password = page.getByLabel("Password", { exact: true });
    const confirmation = page.getByLabel("Confirm password", { exact: true });
    const submit = page.locator(".tds-login__submit button[type='submit']");
    await password.fill("12345678");
    await confirmation.fill("12345678");
    for (const invalid of ["ab", "a".repeat(65), "bad username"]) {
      await username.fill(invalid);
      await submit.click();
      await expect(
        page.getByText(tr("en-US", "login.validationBootstrapUsername"), {
          exact: true,
        }),
      ).toBeVisible();
    }
    await username.fill("a_1.valid-name");
    await confirmation.clear();
    await submit.click();
    await expect(
      page.getByText(tr("en-US", "login.validationConfirmPassword"), {
        exact: true,
      }),
    ).toBeVisible();
    await confirmation.fill("different");
    await submit.click();
    await expect(
      page.getByText(tr("en-US", "login.validationPasswordMismatch"), {
        exact: true,
      }),
    ).toBeVisible();
    await confirmation.fill("12345678");
    await password.fill("1234567890123456");
    await password.blur();
    await expect(
      page.getByText(tr("en-US", "login.validationPasswordMismatch"), {
        exact: true,
      }),
    ).toBeVisible();
    expect(bootstrapPasswords).toEqual([]);
    await confirmation.fill("1234567890123456");
    await submit.click();
    await expect.poll(() => bootstrapPasswords).toEqual(["1234567890123456"]);
    await expect(
      page
        .locator(".ant-message-notice-content")
        .filter({ hasText: "bootstrap rejected" }),
    ).toHaveCount(1);
    await expect(submit).not.toHaveClass(/ant-btn-loading/);
    expect(bootstrapPasswords).toEqual(["1234567890123456"]);
    expect(loginRequests).toBe(0);
    await expect(confirmation).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test("a completed bootstrap falls back to ordinary login when automatic sign-in fails", async ({
    page,
  }) => {
    await requireBootstrap(page);
    const requests: string[] = [];
    await page.route("**/api/v1/auth/bootstrap", (route) => {
      requests.push("bootstrap");
      return route.fulfill({ json: {} });
    });
    await page.route("**/api/v1/auth/login", (route) => {
      requests.push("login");
      return requests.length === 2
        ? route.fulfill({ status: 401, json: {} })
        : route.fulfill({ json: loginResponse });
    });
    await gotoReady(page, "/login");
    await page.getByLabel("Username", { exact: true }).fill("admin");
    await page.getByLabel("Password", { exact: true }).fill("12345678");
    await page.getByLabel("Confirm password", { exact: true }).fill("12345678");
    await page
      .getByRole("button", {
        name: tr("en-US", "login.createAdmin"),
        exact: true,
      })
      .click();
    const submit = page.locator(".tds-login__submit button[type='submit']");
    await expect(submit).not.toHaveClass(/ant-btn-loading/);
    await expect(
      page.getByLabel("Confirm password", { exact: true }),
    ).toHaveCount(0);
    expect(requests).toEqual(["bootstrap", "login"]);
    await submit.click();
    await expect(page).toHaveURL(/\/overview$/);
    expect(requests).toEqual(["bootstrap", "login", "login"]);
  });

  for (const outcome of ["unauthorized", "network", "missing-token"] as const) {
    test(`ordinary login reports ${outcome} once and retains the form`, async ({
      page,
    }) => {
      let loginRequests = 0;
      await page.route("**/api/v1/auth/login", (route) => {
        loginRequests += 1;
        if (outcome === "network") return route.abort("failed");
        return route.fulfill({
          status: outcome === "unauthorized" ? 401 : 200,
          json: {},
        });
      });
      await gotoReady(page, "/login");
      await page.getByLabel("Username", { exact: true }).fill("admin");
      // Existing accounts only require a nonempty password; bootstrap length rules must not leak.
      await page.getByLabel("Password", { exact: true }).fill("x");
      await page.getByRole("button", { name: "Login", exact: true }).click();
      const key =
        outcome === "unauthorized"
          ? "apiErrors.unauthorized"
          : outcome === "network"
            ? "apiErrors.networkError"
            : "login.errorInvalidCredentials";
      await expect(
        page
          .locator(".ant-message-notice-content")
          .filter({ hasText: tr("en-US", key) }),
      ).toHaveCount(1);
      expect(loginRequests).toBe(1);
      expect(
        await page.evaluate(() => localStorage.getItem("token")),
      ).toBeNull();
      await expect(page).toHaveURL(/\/login$/);
      await expect(
        page.locator(".tds-login__submit button[type='submit']"),
      ).not.toHaveClass(/ant-btn-loading/);
    });
  }

  test("visible labels, keyboard submit, password visibility and loading clicks keep one login request", async ({
    page,
  }) => {
    const pending: Route[] = [];
    let requests = 0;
    await page.route("**/api/v1/auth/login", (route) => {
      requests += 1;
      pending.push(route);
    });
    try {
      await gotoReady(page, "/login");
      const username = page.getByLabel("Username", { exact: true });
      const password = page.getByLabel("Password", { exact: true });
      const focusAppearance = () =>
        username.evaluate((input) => {
          const style = getComputedStyle(
            input.closest(".ant-input-affix-wrapper")!,
          );
          return {
            border: style.borderColor,
            shadow: style.boxShadow,
            outline: style.outline,
          };
        });
      const unfocused = await focusAppearance();
      await expect(
        page.locator("label").filter({ hasText: /^Username$/ }),
      ).toBeVisible();
      await expect(
        page.locator("label").filter({ hasText: /^Password$/ }),
      ).toBeVisible();
      await page.keyboard.press("Tab");
      await expect(username).toBeFocused();
      await expect.poll(focusAppearance).not.toEqual(unfocused);
      await username.fill("admin");
      await page.keyboard.press("Tab");
      await expect(password).toBeFocused();
      await password.fill("x");
      const visibility = page.locator(".ant-input-password-icon");
      await page.keyboard.press("Tab");
      await expect(visibility).toBeFocused();
      await expect(visibility).toHaveAttribute("type", "button");
      await expect(visibility).toHaveAccessibleName(
        tr("en-US", "login.showPassword"),
      );
      await expect(visibility).toHaveAttribute("aria-pressed", "false");
      const visibilityBox = await visibility.boundingBox();
      expect(visibilityBox!.width).toBeGreaterThanOrEqual(44);
      expect(visibilityBox!.height).toBeGreaterThanOrEqual(44);
      await page.keyboard.press("Space");
      await expect(password).toHaveAttribute("type", "text");
      await expect(visibility).toBeFocused();
      await expect(visibility).toHaveAttribute("aria-pressed", "true");
      await expect(visibility).toHaveAccessibleName(
        tr("en-US", "login.hidePassword"),
      );
      await page.keyboard.press("Space");
      await expect(password).toHaveAttribute("type", "password");
      await expect(visibility).toHaveAttribute("aria-pressed", "false");
      await visibility.click();
      await expect(password).toHaveAttribute("type", "text");
      await visibility.click();
      await expect(password).toHaveAttribute("type", "password");
      await password.press("Enter");
      await expect.poll(() => requests).toBe(1);
      const submit = page.locator(".tds-login__submit button[type='submit']");
      await expect(submit).toHaveClass(/ant-btn-loading/);
      const box = await submit.boundingBox();
      await page.mouse.click(
        box!.x + box!.width / 2,
        box!.y + box!.height / 2,
        { clickCount: 2 },
      );
      expect(requests).toBe(1);
      await Promise.all(
        pending
          .splice(0)
          .map((route) => route.fulfill({ json: loginResponse })),
      );
      await expect(page).toHaveURL(/\/overview$/);
      expect(requests).toBe(1);
    } finally {
      await Promise.all(
        pending.map((route) => route.abort().catch(() => undefined)),
      );
    }
  });
});
