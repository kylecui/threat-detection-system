import { expect, test, type Page } from "@playwright/test";
import { getApiMockReport, setAuthState } from "./helpers/auth";
import * as F from "./helpers/apiFixtures";
import { tr } from "./helpers/locale";
import { waitForAppReady, waitForOverviewReady } from "./helpers/ready";

/**
 * INTERACTION-008: Overview renders the trend from the real backend DTO
 * (`timestamp`/`count`) only — the fixtures no longer carry `time`/`value`.
 *
 * INTERACTION-010: every assertion is synchronised on the snapshot *response*
 * (path + `hours` + 2xx), never on the request merely being observed. The
 * listener is always created before the navigation/click that triggers it.
 * Only the rendering waits below get a local timeout; the global expect
 * timeout, workers and retries are unchanged.
 *
 * INTERACTION-011: the business budget (RESPONSE_TIMEOUT) starts only after
 * the shared app-ready contract (lazy chunk loaded, Scope initialised, route
 * committed). Cold compilation and bootstrap are covered by `waitForAppReady`
 * (APP_READY_TIMEOUT) and the global warm-up, never by this budget. Responses
 * are recorded from before navigation, so a fast response is never missed.
 */
const LANGUAGE = "en-US" as const;
const SNAPSHOT_PATH = "/api/v1/assessment/overview-snapshot";
const SNAPSHOT_ROUTE = `**${SNAPSHOT_PATH}**`;
/** Snapshot response, measured from "app ready" (or from the range click). */
const RESPONSE_TIMEOUT = 15_000;
/** Response -> state commit -> chart / empty state mounted. */
const RENDER_TIMEOUT = 15_000;

/**
 * Records every successful overview snapshot response (by `hours`) from the
 * moment it is created; create it before the navigation/click. `waitFor()`
 * starts the business budget when it is called. Each range has its own cache
 * key in threatService, so every range in these tests is fetched exactly once.
 */
function trackSnapshots(page: Page) {
  const received = new Set<string>();
  page.on("response", (response) => {
    const url = new URL(response.url());
    if (url.pathname.endsWith(SNAPSHOT_PATH) && response.ok()) received.add(url.searchParams.get("hours") ?? "");
  });
  return {
    async waitFor(hours: string) {
      await expect
        .poll(() => received.has(hours), {
          message: `successful overview snapshot response for hours=${hours}`,
          timeout: RESPONSE_TIMEOUT,
        })
        .toBe(true);
    },
  };
}

function collectPageErrors(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

const trendPanel = (page: Page) => page.locator(".tds-overview__panel--trend");
const chart = (page: Page) => trendPanel(page).getByRole("img", { name: tr(LANGUAGE, "overview.trendChartAria") });
const emptyTrend = (page: Page) => trendPanel(page).getByText(tr(LANGUAGE, "overview.noTrendData"));
/** `loadData` clears the refresh button's loading state in `finally`, after the trend state is set. */
const refreshing = (page: Page) => page.locator(".tds-overview__toolbar-actions .ant-btn-loading");

async function expectChart(page: Page) {
  await expect(chart(page)).toBeVisible({ timeout: RENDER_TIMEOUT });
  await expect(chart(page).locator("canvas")).toHaveCount(1, { timeout: RENDER_TIMEOUT });
  await expect(emptyTrend(page)).toHaveCount(0);
}

async function openOverview(page: Page) {
  const snapshots = trackSnapshots(page);
  await page.goto("/overview");
  await waitForAppReady(page);
  await snapshots.waitFor("24");
  await waitForOverviewReady(page);
  return snapshots;
}

test.describe("overview trend contract", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test.afterEach(async ({ page }) => {
    expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
  });

  test("renders the DTO trend and refetches when the range changes", async ({ page }) => {
    const errors = collectPageErrors(page);
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    const snapshots = await openOverview(page);
    await expectChart(page);

    for (const [label, hours] of [["overview.range7d", "168"], ["overview.range30d", "720"]] as const) {
      const item = page.locator(".tds-overview__range .ant-segmented-item", { hasText: tr(LANGUAGE, label) });
      await item.click();
      await snapshots.waitFor(hours);
      await expect(item).toHaveClass(/ant-segmented-item-selected/);
      await expect(refreshing(page)).toHaveCount(0, { timeout: RENDER_TIMEOUT });
      await expectChart(page);
    }
    expect(errors).toEqual([]);
  });

  test("an all-zero trend is still drawn (count 0 is data, not missing)", async ({ page }) => {
    const errors = collectPageErrors(page);
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await page.route(SNAPSHOT_ROUTE, (route) =>
      route.fulfill({
        json: { ...F.OVERVIEW_SNAPSHOT, trend: F.THREAT_TREND.map((point) => ({ ...point, count: 0 })) },
      }),
    );
    await openOverview(page);
    await expectChart(page);
    expect(errors).toEqual([]);
  });

  test("an empty trend shows the empty state without errors", async ({ page }) => {
    const errors = collectPageErrors(page);
    await setAuthState(page, "SUPER_ADMIN", undefined, LANGUAGE);
    await page.route(SNAPSHOT_ROUTE, (route) => route.fulfill({ json: { ...F.OVERVIEW_SNAPSHOT, trend: [] } }));
    await openOverview(page);
    await expect(emptyTrend(page)).toBeVisible({ timeout: RENDER_TIMEOUT });
    await expect(chart(page)).toHaveCount(0);
    expect(errors).toEqual([]);
  });
});
