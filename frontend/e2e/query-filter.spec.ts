import { expect, test, type Page } from "@playwright/test";
import { getApiMockReport, setAuthState } from "./helpers/auth";
import type { E2ELanguage } from "./helpers/locale";
import { queryGeometry, tableQuery, type QueryGeometry } from "./helpers/queryFilter";
import { gotoReady, waitForTableReady } from "./helpers/ready";
import { TABLE_SURFACES } from "./helpers/tableSurfaces";

/**
 * INTERACTION-013: every ProTable query form (customers, devices, alerts,
 * threat-intel indicators) at 1440 / 390, en-US / zh-CN, collapsed and
 * expanded. Located through the app-owned `.tds-table-query` hook only.
 *
 * - Desktop: each label is vertically centred on its control (<= 2px).
 * - Phone: labels, controls and buttons stay inside the form, never overlap,
 *   and the page never scrolls horizontally.
 */
const SURFACES = ["customers", "customers-device-list", "alerts-detail", "intel-indicators"];
const WIDTHS = [1440, 390];
const LANGUAGES: E2ELanguage[] = ["en-US", "zh-CN"];
const TOLERANCE = 2;
const EXPAND = /^(Expand|展开)/;
const COLLAPSE = /^(Collapse|收起)/;

/**
 * The local verification build (no npm registry) renders ProTable from the
 * pro-components UMD bundle with its own antd copy, so the app theme's 38px
 * control height does not reach the query form there. Alignment is only
 * meaningful with the real Vite build; never set this against `npm run dev`.
 */
const LOCAL_UMD_BUILD = process.env.E2E_LOCAL_UMD_BUILD === "1";

function checkGeometry(geometry: QueryGeometry, width: number, state: string) {
  const form = geometry.form!;
  expect(geometry.fields.length, `${state}: visible query fields`).toBeGreaterThan(0);
  const inside = (box: { left: number; right: number }, what: string) => {
    expect(box.left, `${state}: ${what} starts inside the form`).toBeGreaterThanOrEqual(form.x - 1);
    expect(box.right, `${state}: ${what} ends inside the form`).toBeLessThanOrEqual(form.x + form.width + 1);
  };
  for (const field of geometry.fields) {
    inside(field.labelBox, `label "${field.label}"`);
    inside(field.controlBox, `control of "${field.label}"`);
    const separated =
      field.labelBox.right <= field.controlBox.left + 1 || field.labelBox.bottom <= field.controlBox.top + 1;
    expect(separated, `${state}: label "${field.label}" does not overlap its control`).toBe(true);
    if (width >= 1024 && !LOCAL_UMD_BUILD) {
      expect(
        Math.abs(field.labelCenter - field.controlCenter),
        `${state}: label "${field.label}" centred on its control`,
      ).toBeLessThanOrEqual(TOLERANCE);
    }
  }
  for (const button of geometry.buttons) inside(button.box, `button "${button.text}"`);
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

for (const id of SURFACES) {
  const surface = TABLE_SURFACES.find((item) => item.id === id)!;
  for (const width of WIDTHS) {
    for (const language of LANGUAGES) {
      test(`${id} query form @${width} ${language}`, async ({ page }) => {
        if (LOCAL_UMD_BUILD && width >= 1024) {
          test.info().annotations.push({ type: "local-umd-build", description: "label/control centring not checked (ProTable UMD has its own antd theme)" });
        }
        await page.setViewportSize({ width, height: width < 768 ? 844 : 900 });
        await setAuthState(page, surface.role, undefined, language);
        await gotoReady(page, surface.route);
        await surface.enter?.({ page, language });
        await waitForTableReady(page.locator(".tds-content"));

        const query = tableQuery(page);
        await expect(query).toBeVisible();
        checkGeometry(await queryGeometry(query), width, "collapsed");
        await pageDoesNotScroll(page);

        // Expanded state (only when the form has hidden fields).
        const expand = query.getByText(EXPAND).first();
        if (await expand.count()) {
          const collapsedFields = (await queryGeometry(query)).fields.length;
          await expand.click();
          await expect(query.getByText(COLLAPSE).first()).toBeVisible();
          await expect.poll(async () => (await queryGeometry(query)).fields.length).toBeGreaterThan(collapsedFields);
          checkGeometry(await queryGeometry(query), width, "expanded");
          await pageDoesNotScroll(page);
          await query.getByText(COLLAPSE).first().click();
          await expect(query.getByText(EXPAND).first()).toBeVisible();
        }
      });
    }
  }
}
