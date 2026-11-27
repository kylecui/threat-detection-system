import { expect, test } from "@playwright/test";
import { getApiMockReport, setAuthState } from "./helpers/auth";
import { collectErrors, isAllowedConsoleError } from "./helpers/consoleGate";
import { gotoReady, waitForTableReady } from "./helpers/ready";
import { TABLE_SURFACES } from "./helpers/tableSurfaces";

/**
 * INTERACTION-012: the ProTable toolbar keeps its density feature through the
 * app-owned TableDensityControl (no StrictMode `findDOMNode` warnings), and
 * the console gate stays strict.
 */
const REAL_FIND_DOM_NODE_WARNING =
  "Warning: findDOMNode is deprecated and will be removed in the next major release. Instead, add a ref directly to the element you want to reference.";

test.describe("console gate stays strict", () => {
  test("only reviewed antd deprecation notices are allowed", () => {
    expect(isAllowedConsoleError("Warning: [antd: Tooltip] `overlay` is deprecated. Please use `title` instead.")).toBe(true);
    expect(isAllowedConsoleError(REAL_FIND_DOM_NODE_WARNING)).toBe(false);
    expect(isAllowedConsoleError("Warning: %s is deprecated in StrictMode. findDOMNode was passed an instance of DomWrapper")).toBe(false);
    expect(isAllowedConsoleError("Failed to load overview data: Error: boom")).toBe(false);
    expect(isAllowedConsoleError("prefix Warning: [antd: Table] not at the start")).toBe(false);
  });

  test("application console errors and page errors are collected in the browser", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    const errors = collectErrors(page);
    await gotoReady(page, "/overview");
    await page.evaluate((findDomNode) => {
      console.error("app failure from a component");
      console.error(findDomNode);
      console.error("Warning: [antd: Tooltip] `overlay` is deprecated.");
      setTimeout(() => {
        throw new Error("uncaught page failure");
      });
    }, REAL_FIND_DOM_NODE_WARNING);
    await expect.poll(() => errors.pageErrors).toEqual(["uncaught page failure"]);
    expect(errors.consoleErrors).toEqual(["app failure from a component", REAL_FIND_DOM_NODE_WARNING]);
  });
});

test.describe("ProTable toolbar", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test.afterEach(async ({ page }) => {
    expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
  });

  for (const id of ["customers", "customers-device-list", "alerts-detail", "intel-indicators"]) {
    const surface = TABLE_SURFACES.find((item) => item.id === id)!;

    test(`${id}: reload, density and column settings keep working without console errors`, async ({ page }) => {
      const errors = collectErrors(page);
      await setAuthState(page, surface.role, undefined, "en-US");
      await gotoReady(page, surface.route);
      await surface.enter?.({ page, language: "en-US" });
      const content = page.locator(".tds-content");
      await waitForTableReady(content);

      const toolbar = content.locator(".ant-pro-table-list-toolbar:visible").first();
      // Accessible name = Pro Components' own "density" label for the active locale.
      const density = toolbar.locator("button.tds-table-density-control");
      await expect(density).toHaveCount(1);
      await expect(density).toHaveAttribute("aria-label", /\S/);
      await expect(toolbar.getByRole("button", { name: (await density.getAttribute("aria-label"))! })).toHaveCount(1);
      // Order unchanged: reload, density, column settings.
      const order = await toolbar.locator(".anticon-reload, .tds-table-density-control, .anticon-setting").evaluateAll((nodes) =>
        nodes.map((node) => (node.classList.contains("tds-table-density-control") ? "density" : node.classList.contains("anticon-reload") ? "reload" : "setting")),
      );
      expect(order.filter((item, index) => order.indexOf(item) === index)).toEqual(["reload", "density", "setting"]);

      const table = content.locator(".ant-table:visible").first();
      await expect(table).not.toHaveClass(/ant-table-small/);

      // Mouse: open the menu and choose the compact size.
      await density.click();
      const menu = page.locator(".ant-dropdown:visible .ant-dropdown-menu");
      await expect(menu.getByRole("menuitem")).toHaveCount(3);
      await menu.getByRole("menuitem").nth(2).click();
      await expect(table).toHaveClass(/ant-table-small/);

      // Keyboard: the native button is focusable and opens the menu with Enter.
      await density.focus();
      await expect(density).toBeFocused();
      await page.keyboard.press("Enter");
      await expect(menu.getByRole("menuitem").nth(2)).toHaveClass(/ant-dropdown-menu-item-selected/);
      await menu.getByRole("menuitem").nth(1).click();
      await expect(table).not.toHaveClass(/ant-table-small/);
      await expect(table).toHaveClass(/ant-table-middle/);

      // Column settings popover still opens.
      await toolbar.locator(".anticon-setting").first().click();
      await expect(page.locator(".ant-popover:visible")).toHaveCount(1);
      await page.keyboard.press("Escape");

      // Reload still re-runs the request.
      const reload = page.waitForResponse((response) => response.request().method() === "GET" && response.url().includes("/api/"));
      await toolbar.locator(".anticon-reload").first().click();
      await reload;
      await waitForTableReady(content);

      expect(errors.pageErrors).toEqual([]);
      expect(errors.consoleErrors).toEqual([]);
    });
  }
});
