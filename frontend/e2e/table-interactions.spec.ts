import { expect, test, type Page } from "@playwright/test";
import { getApiMockReport, setAuthState } from "./helpers/auth";
import { tr } from "./helpers/locale";
import { gotoReady, waitForOverviewReady, waitForTableReady } from "./helpers/ready";

/**
 * INTERACTION-002/004/005: the layout work must not change table behaviour.
 * One real interaction per kind: sorting, row selection, expanding, tabs,
 * icon actions (dialogs/confirmations unchanged), keyboard reachability.
 */
const LANGUAGE = "en-US" as const;

/** INTERACTION-011: rows only once the table in `scope` is loaded and stable. */
async function rows(page: Page, scope = ".tds-content") {
  await waitForTableReady(page.locator(scope));
  return page.locator(`${scope} .ant-table-tbody tr.ant-table-row`);
}

test.describe("table interactions", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test.afterEach(async ({ page }) => {
    expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
  });

  test("sorting reorders the top attackers table", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/overview");
    await waitForOverviewReady(page);
    const table = page.locator(".tds-overview__desktop-table").nth(1);
    await waitForTableReady(table, { minRows: 2 });
    const firstMac = () => table.locator(".ant-table-tbody tr.ant-table-row").first().locator(".tds-network-identifier--mac").innerText();
    await expect(table.locator(".ant-table-tbody tr.ant-table-row")).toHaveCount(2);
    const before = await firstMac();
    await table.getByRole("columnheader", { name: tr(LANGUAGE, "overview.attackCount") }).click();
    await expect.poll(firstMac).not.toBe(before);
  });

  test("row selection still works in the threat detail table", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/investigate/threats");
    await page.locator(".tds-content .ant-segmented-item").nth(0).click();
    const first = (await rows(page)).first();
    await first.locator(".ant-table-selection-column .ant-checkbox-input").check();
    await expect(first).toHaveClass(/ant-table-row-selected/);
  });

  test("expanding a grouped alert row loads the nested table", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/investigate/alerts");
    await page.locator(".tds-content .ant-segmented-item").nth(1).click();
    await rows(page);
    await page.locator(".tds-content .ant-table-row-expand-icon").first().click();
    await expect(page.locator(".tds-content .ant-table-expanded-row .ant-table-tbody tr.ant-table-row").first()).toBeVisible();
  });

  test("ConfigCascading icon actions keep their dialogs and confirmations", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/e2e/harness/config-cascading.html");
    const groupRow = (await rows(page)).first();
    const actions = groupRow.getByRole("group", { name: tr(LANGUAGE, "common.actions") });
    const names = ["common.edit", "configCascading.manageMembers", "configCascading.batchAssign", "common.delete"].map((key) => tr(LANGUAGE, key));
    for (const name of names) await expect(actions.getByRole("button", { name, exact: true })).toBeVisible();

    // Edit group -> edit modal
    await actions.getByRole("button", { name: names[0], exact: true }).click();
    await expect(page.getByRole("dialog").filter({ hasText: tr(LANGUAGE, "configCascading.editGroup") })).toBeVisible();
    await page.locator(".ant-modal:visible .ant-modal-close").click();
    await expect(page.locator(".ant-modal-root .ant-modal:visible")).toHaveCount(0);

    // Batch assign -> batch modal
    await actions.getByRole("button", { name: names[2], exact: true }).click();
    await expect(page.locator(".ant-modal:visible")).toHaveCount(1);
    await page.locator(".ant-modal:visible .ant-modal-close").click();
    await expect(page.locator(".ant-modal-root .ant-modal:visible")).toHaveCount(0);

    // Delete group -> confirmation (cancelled, nothing deleted)
    await actions.getByRole("button", { name: names[3], exact: true }).click();
    const confirm = page.locator(".ant-modal-confirm:visible");
    await expect(confirm).toContainText(tr(LANGUAGE, "configCascading.confirmDeleteGroup").replace('"{{name}}"?', "").trim());
    await confirm.getByRole("button", { name: tr(LANGUAGE, "common.cancel") }).click();
    await expect(confirm).toHaveCount(0);

    // Manage members -> members modal -> remove member asks for confirmation
    await actions.getByRole("button", { name: names[1], exact: true }).click();
    const membersModal = page.locator(".ant-modal-root .ant-modal").filter({ has: page.locator(".ant-table") });
    await expect(membersModal.locator(".ant-table-tbody tr.ant-table-row").first()).toBeVisible();
    await membersModal.getByRole("button", { name: tr(LANGUAGE, "configCascading.removeMember"), exact: true }).first().click();
    await expect(page.locator(".ant-modal-confirm:visible")).toContainText(tr(LANGUAGE, "configCascading.confirmRemoveMember"));
  });

  test("ConfigCascading action buttons are keyboard reachable with a visible focus state", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/e2e/harness/config-cascading.html");
    const groupRow = (await rows(page)).first();
    const editButton = groupRow.getByRole("button", { name: tr(LANGUAGE, "common.edit"), exact: true });
    // Start from the row's last text cell, then tab forward into the action group.
    await groupRow.locator("td").nth(1).click();
    let reached = false;
    for (let step = 0; step < 40 && !reached; step += 1) {
      await page.keyboard.press("Tab");
      reached = await editButton.evaluate((button) => document.activeElement === button);
    }
    expect(reached, "edit action reachable with Tab").toBe(true);
    expect(await editButton.evaluate((button) => button.matches(":focus-visible"))).toBe(true);
  });

  test("device drawer actions use the shared group and keep their behaviour", async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await gotoReady(page, "/admin/customers");
    await (await rows(page)).first().getByRole("button", { name: tr(LANGUAGE, "customersDevices.devices"), exact: true }).click();
    const drawer = page.locator(".ant-drawer-open .ant-drawer-content");
    const deviceRow = (await rows(page, ".ant-drawer-open .ant-drawer-content")).first();
    const group = deviceRow.getByRole("group", { name: tr(LANGUAGE, "common.actions") });
    await expect(group).toHaveClass(/tds-table-actions/);
    await expect(group.getByRole("button")).toHaveCount(3);
    await expect(drawer.locator(".tds-device-actions")).toHaveCount(0);

    // Unbind asks for confirmation (Popconfirm), cancel keeps the device.
    await group.getByRole("button", { name: tr(LANGUAGE, "customersDevices.unbind"), exact: true }).click();
    const popconfirm = page.locator(".ant-popconfirm:visible");
    await expect(popconfirm).toContainText(tr(LANGUAGE, "customersDevices.confirmUnbind"));
    await popconfirm.getByRole("button", { name: /cancel|取消/i }).click();
    await expect(deviceRow).toBeVisible();

    // Edit opens the edit modal above the drawer.
    await group.getByRole("button", { name: tr(LANGUAGE, "customersDevices.editDevice"), exact: true }).click();
    await expect(page.locator(".ant-modal:visible")).toHaveCount(1);
  });
});
