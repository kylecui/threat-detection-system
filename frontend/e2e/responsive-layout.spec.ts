import { expect, test, type Locator, type Page } from "@playwright/test";
import { getApiMockReport, setAuthState } from "./helpers/auth";
import { tr, type E2ELanguage } from "./helpers/locale";
import { SCOPE_SELECTOR, TABLE_SURFACES } from "./helpers/tableSurfaces";
import { gotoReady } from "./helpers/ready";

/**
 * INTERACTION-006 / INTERACTION-007: element-level responsive checks for the
 * two non-table blocks the table matrix used to register as NEW-A / NEW-B.
 * The table matrix only sees "a container overflows"; these tests pin the
 * intended layout (column count, order, full-width controls) and make sure
 * nothing is "fixed" by hiding or clipping content.
 */
interface Combo {
  width: number;
  language: E2ELanguage;
}

const COMBOS: Combo[] = [
  { width: 390, language: "en-US" },
  { width: 390, language: "zh-CN" },
  { width: 768, language: "en-US" },
  { width: 768, language: "zh-CN" },
  { width: 1024, language: "en-US" },
  { width: 1440, language: "en-US" },
  { width: 1440, language: "zh-CN" },
];

type Box = { x: number; y: number; width: number; height: number };

/** antd inserts a space inside two-character CJK button labels ("关 闭"). */
function buttonName(text: string): RegExp {
  return new RegExp(`^\\s*${[...text].map((char) => char.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("\\s*")}\\s*$`);
}

/** Geometry is only meaningful once the open (zoom) animation has finished. */
async function waitForStableBox(locator: Locator) {
  let previous = "";
  await expect
    .poll(async () => {
      const current = JSON.stringify(await locator.boundingBox());
      const stable = current === previous;
      previous = current;
      return stable;
    }, { intervals: [150], timeout: 10_000 })
    .toBe(true);
}

async function box(locator: Locator): Promise<Box> {
  const value = await locator.boundingBox();
  expect(value, "element is rendered").not.toBeNull();
  return value!;
}

function expectInside(inner: Box, outer: Box, what: string) {
  expect(inner.width, `${what} has a size`).toBeGreaterThan(0);
  expect(inner.height, `${what} has a size`).toBeGreaterThan(0);
  expect(inner.x, `${what} starts inside its container`).toBeGreaterThanOrEqual(outer.x - 1);
  expect(inner.x + inner.width, `${what} ends inside its container`).toBeLessThanOrEqual(outer.x + outer.width + 1);
}

async function noHorizontalOverflow(locator: Locator, what: string) {
  const { scrollWidth, clientWidth } = await locator.evaluate((element) => ({
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }));
  expect(scrollWidth, `${what}: scrollWidth <= clientWidth`).toBeLessThanOrEqual(clientWidth + 1);
}

async function pageDoesNotScroll(page: Page) {
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth),
    "page never scrolls horizontally",
  ).toBeLessThanOrEqual(0);
}

test.afterEach(async ({ page }) => {
  expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
});

test.describe("INTERACTION-006 device data-flow summary", () => {
  const surface = TABLE_SURFACES.find((item) => item.id === "customers-device-events-modal")!;

  for (const { width, language } of COMBOS) {
    test(`@${width} ${language}`, async ({ page }) => {
      await page.setViewportSize({ width, height: width < 768 ? 844 : 900 });
      await setAuthState(page, surface.role, undefined, language);
      await gotoReady(page, surface.route);
      await surface.enter!({ page, language });

      const modal = page.locator(SCOPE_SELECTOR.modal);
      const body = modal.locator(".ant-modal-body");
      const summary = body.locator(".tds-device-flow-descriptions");
      await expect(summary).toBeVisible();
      await expect(body.locator(".ant-table-tbody tr.ant-table-row").first()).toBeVisible();
      await waitForStableBox(modal);

      // 1 column on phones, 2 on tablets, 3 on desktop (antd md / lg breakpoints).
      const expectedColumns = width < 768 ? 1 : width < 992 ? 2 : 3;
      const rows = summary.locator("tr.ant-descriptions-row");
      const labelsPerRow = await rows.evaluateAll((items) =>
        items.map((row) => row.querySelectorAll(".ant-descriptions-item-label").length),
      );
      expect(Math.max(...labelsPerRow), "summary column count").toBe(expectedColumns);

      // The heartbeat policy (longest value) always owns a full row.
      const policyRow = rows.filter({ hasText: tr(language, "customersDevices.heartbeatPolicy") });
      await expect(policyRow.locator(".ant-descriptions-item-label")).toHaveCount(1);
      // The description spans the whole row whenever there are <= 2 columns.
      const descriptionRow = rows.filter({ hasText: tr(language, "common.description") }).first();
      if (expectedColumns < 3) {
        await expect(descriptionRow.locator(".ant-descriptions-item-label")).toHaveCount(1);
      }

      // Nothing is clipped, hidden or removed: all 12 fields, each inside the body.
      await noHorizontalOverflow(body, "modal body");
      const bodyBox = await box(body);
      expectInside(await box(summary), bodyBox, "summary");
      const labels = summary.locator(".ant-descriptions-item-label");
      const contents = summary.locator(".ant-descriptions-item-content");
      await expect(labels).toHaveCount(12);
      await expect(contents).toHaveCount(12);
      for (let index = 0; index < 12; index += 1) {
        const label = labels.nth(index);
        await expect(label).toBeVisible();
        expect((await label.innerText()).trim().length, "label text").toBeGreaterThan(0);
        expectInside(await box(label), bodyBox, `label ${index + 1}`);
        expectInside(await box(contents.nth(index)), bodyBox, `value ${index + 1}`);
      }
      for (const key of ["customersDevices.lastThreatAt", "customersDevices.heartbeatPolicy", "customersDevices.discoveredHosts"]) {
        await expect(labels.filter({ hasText: tr(language, key) })).toBeVisible();
      }

      // The events table may still scroll, but only inside its own container.
      const tableContainer = body.locator(".ant-table-container").first();
      expectInside(await box(tableContainer), bodyBox, "events table container");
      await pageDoesNotScroll(page);

      // The close button in the footer stays reachable on phones.
      const close = modal.locator(".ant-modal-footer").getByRole("button", { name: buttonName(tr(language, "common.close")) });
      await close.scrollIntoViewIfNeeded();
      await expect(close).toBeVisible();
      await close.click();
      await expect(modal).toBeHidden();
    });
  }
});

test.describe("INTERACTION-007 ConfigCascading lock query", () => {
  const HARNESS = "/e2e/harness/config-cascading.html";

  for (const { width, language } of COMBOS) {
    test(`@${width} ${language}`, async ({ page }) => {
      await page.setViewportSize({ width, height: width < 768 ? 844 : 900 });
      await setAuthState(page, "SUPER_ADMIN", undefined, language);
      await gotoReady(page, HARNESS);

      const form = page.locator(".tds-config-cascading__lock-query");
      await expect(form).toBeVisible();
      const card = form.locator("xpath=ancestor::div[contains(@class,'ant-card-body')][1]");
      const fields = form.locator(".tds-config-cascading__field-control");
      const buttons = form.locator(".tds-config-cascading__lock-actions .ant-btn");
      await expect(fields).toHaveCount(2);
      await expect(buttons).toHaveCount(3);
      const names = ["configCascading.loadLocks", "configCascading.addLock", "configCascading.removeAllLocks"];
      for (let index = 0; index < 3; index += 1) {
        await expect(buttons.nth(index)).toHaveText(tr(language, names[index]));
      }

      const check = async (state: string) => {
        await noHorizontalOverflow(page.locator(".tds-content"), `${state}: .tds-content`);
        await noHorizontalOverflow(page.locator(".tds-content__inner"), `${state}: .tds-content__inner`);
        await noHorizontalOverflow(form, `${state}: lock query form`);
        const cardBox = await box(card);
        const ordered: Box[] = [];
        for (const control of [fields.nth(0), fields.nth(1), buttons.nth(0), buttons.nth(1), buttons.nth(2)]) {
          const controlBox = await box(control);
          expectInside(controlBox, cardBox, `${state}: query control`);
          ordered.push(controlBox);
        }
        // Visual reading order (top-to-bottom, then left-to-right) equals DOM / Tab order.
        const visual = ordered
          .map((item, index) => ({ item, index }))
          .sort((a, b) => (Math.abs(a.item.y - b.item.y) > 4 ? a.item.y - b.item.y : a.item.x - b.item.x))
          .map(({ index }) => index);
        expect(visual, `${state}: visual order`).toEqual([0, 1, 2, 3, 4]);

        if (width < 576) {
          // Phones: one column, full-width controls, 44px touch targets.
          const formBox = await box(form);
          for (const item of ordered) expect(Math.abs(item.width - formBox.width), `${state}: full width`).toBeLessThanOrEqual(2);
          for (const item of ordered.slice(2)) expect(item.height, `${state}: touch target`).toBeGreaterThanOrEqual(44);
        }
        if (width >= 1024) {
          // Desktop keeps the compact inline layout: both fields share a row.
          expect(Math.abs(ordered[1].y - ordered[0].y), `${state}: fields share a row`).toBeLessThanOrEqual(4);
          // The three actions always stay together on one row.
          for (const item of ordered.slice(3)) expect(Math.abs(item.y - ordered[2].y), `${state}: actions share a row`).toBeLessThanOrEqual(4);
        }
        if (width >= 1440) {
          // Wide desktop: everything on a single row (as before the fix).
          const top = ordered[0].y;
          for (const item of ordered) expect(Math.abs(item.y - top), `${state}: single desktop row`).toBeLessThanOrEqual(4);
        }
        await pageDoesNotScroll(page);
      };

      await check("initial");

      // Validation messages must not push anything out of view.
      await buttons.nth(0).click();
      await expect(form.locator(".ant-form-item-explain-error").first()).toBeVisible();
      await check("validation");

      // Keyboard order follows the same sequence.
      await fields.nth(1).focus();
      await page.keyboard.press("Tab");
      await expect(buttons.nth(0)).toBeFocused();
      await page.keyboard.press("Tab");
      await expect(buttons.nth(1)).toBeFocused();
      await page.keyboard.press("Tab");
      await expect(buttons.nth(2)).toBeFocused();

      // Behaviour unchanged: "remove all" still needs a selected entity first.
      await buttons.nth(2).click();
      await expect(page.locator(".ant-message-notice").last()).toContainText(tr(language, "configCascading.selectEntityFirst"));
      await expect(page.locator(".ant-modal-confirm")).toHaveCount(0);

      // After loading an entity the danger action still asks for confirmation.
      await fields.nth(0).click();
      await page.locator(".ant-select-dropdown:visible .ant-select-item-option", { hasText: tr(language, "configCascading.customer") }).click();
      await fields.nth(1).fill("101");
      await buttons.nth(0).click();
      await expect(page.locator(".tds-content .ant-table-tbody tr.ant-table-row").first()).toBeVisible();
      await check("loaded");
      await buttons.nth(2).click();
      const confirm = page.locator(".ant-modal-confirm");
      await expect(confirm).toContainText(tr(language, "configCascading.confirmRemoveAllLocks"));
      await waitForStableBox(confirm);
      await confirm.getByRole("button", { name: buttonName(tr(language, "common.cancel")) }).click();
      await expect(confirm).toHaveCount(0);
    });
  }

  test("non-admin users still only see the access warning", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await setAuthState(page, "CUSTOMER_USER", undefined, "en-US");
    await gotoReady(page, HARNESS);
    await expect(page.locator(".tds-content .ant-alert-warning")).toContainText(tr("en-US", "apiErrors.forbidden"));
    await expect(page.locator(".tds-config-cascading")).toHaveCount(0);
    await expect(page.locator(".tds-content .ant-table")).toHaveCount(0);
  });
});
