import { expect, test, type Page, type Route } from "@playwright/test";
import { setAuthState, stubApiAvailability } from "./helpers/auth";
import { gotoReady, waitForAppReady, waitForOverviewReady, waitForTableReady } from "./helpers/ready";

const threat = (id: number, score: number, mac: string) => ({
  id,
  customerId: "e2e-customer",
  assessmentTime: `2026-01-${String(id).padStart(2, "0")}T00:00:00Z`,
  attackMac: mac,
  attackIp: `203.0.113.${id}`,
  threatLevel: score >= 80 ? "HIGH" : "LOW",
  threatScore: score,
  attackCount: id,
  uniqueIps: 1,
  uniquePorts: 2,
  uniqueDevices: 1,
  portList: "22,443",
  portRiskScore: 70,
  detectionTier: 1,
  createdAt: `2026-01-${String(id).padStart(2, "0")}T00:00:00Z`,
  mitigationRecommendations: ["Block the source address"],
});

const pageResponse = (content: unknown[], totalElements = content.length) => ({
  content,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / 20)),
  size: 20,
  number: 0,
  numberOfElements: content.length,
  first: true,
  last: totalElements <= 20,
  empty: content.length === 0,
});

const pipelineHealth = {
  status: "healthy",
  timestamp: "2026-01-01T00:00:00Z",
  services: {
    "data-ingestion": { status: "UP", latencyMs: 21 },
    kafka: { status: "UP", latencyMs: 7 },
    "stream-processing": { status: "UP", latencyMs: 18 },
    "ml-detection": { status: "UP", latencyMs: 42 },
    postgres: { status: "UP", latencyMs: 9 },
    "threat-assessment": { status: "UP", latencyMs: 27 },
    "alert-management": { status: "UP", latencyMs: 24 },
    tire: { status: "UP", latencyMs: 35 },
  },
  pipeline: {
    lastEventReceived: "2026-01-01T00:00:00Z",
    eventsLastHour: 12840,
    kafkaLag: 0,
    flinkRunning: true,
  },
};

const emptyOverview = {
  generatedAt: "2026-01-01T00:00:00Z",
  statistics: {
    customerId: "e2e-customer",
    totalCount: 0,
    criticalCount: 0,
    highCount: 0,
    mediumCount: 0,
    lowCount: 0,
    infoCount: 0,
    averageThreatScore: 0,
    maxThreatScore: 0,
    minThreatScore: 0,
    levelDistribution: {},
  },
  trend: [],
  portDistribution: [],
  nonPortProtocols: [],
  topAttackers: [],
  recentThreats: [],
};

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function authenticate(page: Page) {
  await setAuthState(page, "SUPER_ADMIN");
}

test.describe("audit regression coverage", () => {
  test("logout clears the session and returns to the login page", async ({ page }) => {
    await stubApiAvailability(page);
    await gotoReady(page, "/login");
    await page.evaluate(() => {
      localStorage.setItem("token", "audit-logout-token");
      localStorage.setItem("refreshToken", "audit-logout-refresh-token");
      localStorage.setItem(
        "user",
        JSON.stringify({
          id: 1,
          username: "super-admin",
          displayName: "Super Admin",
          roles: ["SUPER_ADMIN"],
          tenantId: 1,
          customerId: "e2e-customer",
        }),
      );
      localStorage.setItem("tds-lang", "en-US");
      localStorage.setItem("tenant_id", "1");
      localStorage.setItem("customer_id", "e2e-customer");
    });
    await gotoReady(page, "/overview");

    await page.getByRole("button", { name: "User" }).click();
    await page.getByText("Logout", { exact: true }).click();

    await expect(page).toHaveURL(/\/login$/);
    await waitForAppReady(page);
    await expect
      .poll(() => page.evaluate(() => localStorage.getItem("token")))
      .toBeNull();
  });

  test("general settings save visible feedback and persisted values", async ({ page }) => {
    await authenticate(page);
    await gotoReady(page, "/config/general");

    const autoRefresh = page.getByRole("switch", { name: "Auto Refresh" });
    await expect(autoRefresh).toBeChecked();
    await autoRefresh.click();
    await page.getByRole("spinbutton", { name: "Refresh Interval" }).fill("45");
    await page.getByRole("button", { name: "Save" }).click();

    await expect(page.getByText("Settings saved")).toBeVisible();
    await expect
      .poll(() =>
        page.evaluate(() => ({
          autoRefresh: localStorage.getItem("auto_refresh"),
          refreshInterval: localStorage.getItem("refresh_interval"),
        })),
      )
      .toEqual({ autoRefresh: "false", refreshInterval: "45" });
  });

  test("threat filters and pagination change the outgoing query", async ({ page }) => {
    await authenticate(page);
    const requests: URL[] = [];
    await page.route("**/api/v1/assessment/assessments**", async (route) => {
      requests.push(new URL(route.request().url()));
      await fulfillJson(
        route,
        pageResponse(
          [
            threat(1, 86, "AA:BB:CC:DD:EE:01"),
            threat(2, 42, "AA:BB:CC:DD:EE:02"),
          ],
          45,
        ),
      );
    });

    await gotoReady(page, "/investigate/threats");
    await page.locator(".ant-segmented-item").filter({ hasText: "Detail View" }).click();
    await expect(page.getByRole("columnheader", { name: "Suspicious Source MAC" })).toBeVisible();

    await page
      .locator(".ant-select")
      .filter({ has: page.locator('input[aria-label="Threat Level"]') })
      .click();
    await page
      .locator(".ant-select-item-option")
      .filter({ hasText: "HIGH" })
      .click();
    await expect
      .poll(() => requests.some((url) => url.searchParams.get("threat_level") === "HIGH"))
      .toBe(true);

    const search = page.getByPlaceholder("Search suspicious source MAC");
    await search.fill("AA:BB:CC:DD:EE:01");
    await search.press("Enter");
    await expect
      .poll(() =>
        requests.some(
          (url) => url.searchParams.get("attack_mac") === "AA:BB:CC:DD:EE:01",
        ),
      )
      .toBe(true);

    await page.locator(".ant-pagination-next button").click();
    await expect
      .poll(() => requests.some((url) => url.searchParams.get("page") === "1"))
      .toBe(true);
  });

  test("grouped threat score sort changes the observable row order", async ({ page }) => {
    await authenticate(page);
    await page.route("**/api/v1/assessment/grouped**", (route) =>
      fulfillJson(
        route,
        pageResponse([
          {
            attackMac: "AA:BB:CC:DD:EE:90",
            customerId: "e2e-customer",
            maxThreatLevel: "HIGH",
            maxThreatScore: 90,
            assessmentCount: 4,
            latestAssessmentTime: "2026-01-02T00:00:00Z",
            totalAttackCount: 18,
            tierCount: 2,
          },
          {
            attackMac: "AA:BB:CC:DD:EE:30",
            customerId: "e2e-customer",
            maxThreatLevel: "LOW",
            maxThreatScore: 30,
            assessmentCount: 2,
            latestAssessmentTime: "2026-01-01T00:00:00Z",
            totalAttackCount: 5,
            tierCount: 1,
          },
        ]),
      ),
    );

    await gotoReady(page, "/investigate/threats");
    await waitForTableReady(page.locator(".tds-content"));
    await expect(page.getByText("AA:BB:CC:DD:EE:90")).toBeVisible();
    await page.getByRole("columnheader", { name: "Threat Score" }).click();

    const firstDataRow = page.locator(".ant-table-tbody .ant-table-row").first();
    await expect(firstDataRow).toContainText("AA:BB:CC:DD:EE:30");
  });

  test("delete confirmation can be cancelled without a write", async ({ page }) => {
    await authenticate(page);
    let deleteRequests = 0;
    await page.route("**/api/v1/assessment/42**", async (route) => {
      if (route.request().method() === "DELETE") {
        deleteRequests += 1;
        await route.fulfill({ status: 204 });
        return;
      }
      await route.fallback();
    });

    await gotoReady(page, "/investigate/threats");
    await page.locator(".ant-segmented-item").filter({ hasText: "Detail View" }).click();
    await page.getByRole("button", { name: "Delete threat record #42" }).click();
    await page.getByRole("button", { name: "Cancel", exact: true }).click();

    await expect(page.locator(".ant-modal-confirm")).toBeHidden();
    expect(deleteRequests).toBe(0);
  });

  test("pipeline refresh and ten-second polling both request fresh status", async ({ page }) => {
    test.setTimeout(30_000);
    await authenticate(page);
    let healthRequests = 0;
    await page.route("**/api/v1/system/health**", async (route) => {
      healthRequests += 1;
      await fulfillJson(route, pipelineHealth);
    });

    await gotoReady(page, "/operate/pipeline");
    await expect(page.getByText("Healthy", { exact: true })).toBeVisible();
    await expect.poll(() => healthRequests).toBeGreaterThanOrEqual(1);

    const beforeManualRefresh = healthRequests;
    await page.getByRole("button", { name: "Refresh pipeline health" }).click();
    await expect.poll(() => healthRequests).toBeGreaterThan(beforeManualRefresh);
    const afterManualRefresh = healthRequests;
    await page.waitForTimeout(10_300);
    await expect.poll(() => healthRequests).toBeGreaterThan(afterManualRefresh);
  });

  test("empty overview data renders explicit empty states", async ({ page }) => {
    await authenticate(page);
    await page.route("**/api/v1/assessment/overview-snapshot**", (route) =>
      fulfillJson(route, emptyOverview),
    );

    await gotoReady(page, "/overview");
    await waitForOverviewReady(page);
    await expect(page.getByText("No trend data available")).toBeVisible();
    await expect(page.getByText("Total Threats")).toBeVisible();
    await expect(page.getByText("0", { exact: true }).first()).toBeVisible();
  });

  test("HTTP failure is surfaced to the user", async ({ page }) => {
    await authenticate(page);
    await page.route("**/api/v1/assessment/overview-snapshot**", (route) =>
      fulfillJson(route, { message: "audit failure" }, 500),
    );

    await gotoReady(page, "/overview");
    // A failed load still leaves the skeleton and shows the error state.
    await waitForOverviewReady(page);
    await expect(page.getByText("Failed to load overview data")).toBeVisible();
  });

  test("network timeout is retried once and then surfaced", async ({ page }) => {
    await authenticate(page);
    let attempts = 0;
    await page.route("**/api/v1/assessment/overview-snapshot**", async (route) => {
      attempts += 1;
      await route.abort("timedout");
    });

    await gotoReady(page, "/overview");
    await waitForOverviewReady(page);
    await expect(page.getByText("Failed to load overview data")).toBeVisible();
    expect(attempts).toBe(2);
  });
});
