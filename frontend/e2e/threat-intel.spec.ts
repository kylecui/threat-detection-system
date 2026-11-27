import { expect, test } from "@playwright/test";
import { setAuthState } from "./helpers/auth";
import { gotoReady } from "./helpers/ready";

const feeds = [
  {
    id: 1,
    feedName: "internal",
    feedUrl: null,
    feedType: "INTERNAL",
    enabled: true,
    pollIntervalHours: 6,
    apiKeyEnvVar: null,
    sourceWeight: 1,
    lastPollAt: null,
    lastPollStatus: null,
    lastPollError: null,
    indicatorCount: 0,
    pollable: false,
    capability: "LOCAL_WRITE",
    nonPollableReason: "INTERNAL_SOURCE",
    pollBatchLimit: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: 2,
    feedName: "abuseipdb",
    feedUrl: "https://api.abuseipdb.com/api/v2/blacklist",
    feedType: "REST_API",
    enabled: true,
    pollIntervalHours: 6,
    apiKeyEnvVar: "ABUSEIPDB_API_KEY",
    sourceWeight: 0.85,
    lastPollAt: "2026-10-21T06:30:00Z",
    lastPollStatus: "SUCCESS",
    lastPollError: null,
    indicatorCount: 23,
    pollable: true,
    capability: "SCHEDULED_AND_MANUAL_POLL",
    nonPollableReason: null,
    pollBatchLimit: 500,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-10-21T06:30:00Z",
  },
];

test.describe("Threat intelligence contracts", () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, "SUPER_ADMIN");
    await page.route("**/api/v1/threat-intel/statistics**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalIndicators: 23,
          activeIndicators: 21,
          totalFeeds: 2,
          activeFeeds: 1,
          bySource: { abuseipdb: 23 },
          bySeverity: { HIGH: 23 },
        }),
      }),
    );
    await page.route("**/api/v1/threat-intel/feeds**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(feeds),
      }),
    );
    await page.route("**/api/v1/threat-intel/indicators**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 20,
          number: 0,
        }),
      }),
    );
    await page.route("**/api/v1/system-config/category/tire_api_keys**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([{ configKey: "ABUSEIPDB_API_KEY", hasValue: true }]),
      }),
    );
  });

  test("renders real feed fields and backend statistics", async ({ page }) => {
    await page.setViewportSize({ width: 1800, height: 1000 });
    await gotoReady(page, "/investigate/intel");

    await expect(page.getByText("23", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
    await page.getByRole("tab", { name: "Feed Management" }).click();

    const abuseRow = page.getByRole("row").filter({ hasText: "abuseipdb" });
    await expect(abuseRow).toContainText("REST_API");
    await expect(abuseRow).toContainText("ABUSEIPDB_API_KEY");
    await expect(abuseRow).toContainText("Success");
    await expect(abuseRow).toContainText("23");

    const internalRow = page.getByRole("row").filter({ hasText: "internal" });
    await expect(internalRow).toContainText("Internal source");
    await expect(internalRow).toContainText("No poll needed");
    await expect(internalRow.getByRole("button", { name: "Poll" })).toHaveCount(0);
  });

  test("renders the actual IP lookup response contract", async ({ page }) => {
    await page.route("**/api/v1/threat-intel/lookup**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ip: "203.0.113.10",
          found: true,
          confidence: 92,
          severity: "HIGH",
          intelWeight: 3.4,
          sources: ["abuseipdb"],
          indicatorCount: 1,
          lastSeenAt: "2026-10-21T06:30:00Z",
          tireScore: 88,
          tireLevel: "High",
          tireVerdict: "Malicious activity reported",
        }),
      }),
    );
    await gotoReady(page, "/investigate/intel");

    await page.getByPlaceholder("Enter IP address").fill("203.0.113.10");
    await page.getByRole("button", { name: "Search" }).click();

    await expect(page.getByText("92%", { exact: true })).toBeVisible();
    await expect(page.getByText("abuseipdb", { exact: true })).toBeVisible();
    await expect(page.getByText("Malicious activity reported")).toBeVisible();
  });
});
