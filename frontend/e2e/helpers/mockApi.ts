/**
 * INTERACTION-003: path-exact E2E API mocks.
 *
 * - Every GET the app issues is registered with its response SHAPE
 *   (page / array / object / scalar) and a typed fixture.
 * - An unregistered GET is never answered with a fake success: it is
 *   recorded in `unexpectedRequests` and answered with HTTP 501, so the page
 *   takes its real error path and table specs fail with the path in the
 *   report.
 * - `EMPTY_PAGE`-style catch-alls are gone: a paged wrapper is only returned
 *   for endpoints declared as `page`.
 */
import { appendFileSync } from "node:fs";
import type { Page, Route } from "@playwright/test";
import * as F from "./apiFixtures";

export type MockShape = "page" | "array" | "object" | "scalar";

export interface MockRoute {
  /** Human readable name used in reports. */
  name: string;
  /** Exact path (after the `/api` prefix normalisation) or a full-path regex. */
  path: string | RegExp;
  shape: MockShape;
  body: (url: URL) => unknown;
}

export interface ApiMockReport {
  /** `GET <path>` for every request that had no registered mock. */
  unexpectedRequests: string[];
  /** Every GET path that was answered (useful for debugging specs). */
  servedRequests: string[];
}

const C = "[^/]+";
const re = (source: string) => new RegExp(`^${source}$`);

/** Ordered: the first matching route wins, so specific paths come first. */
export const MOCK_ROUTES: MockRoute[] = [
  // ---- auth
  { name: "bootstrap status", path: "/api/v1/auth/bootstrap/status", shape: "object", body: () => ({ bootstrapRequired: false }) },

  // ---- threat assessment
  { name: "overview snapshot", path: "/api/v1/assessment/overview-snapshot", shape: "object", body: () => F.OVERVIEW_SNAPSHOT },
  { name: "assessments", path: re("/api/v1/assessment/assessments(?:/tenant)?"), shape: "page", body: () => F.page([F.E2E_THREAT]) },
  { name: "grouped assessments", path: "/api/v1/assessment/grouped", shape: "page", body: () => F.page([F.GROUPED_THREAT]) },
  { name: "assessment statistics", path: re("/api/v1/assessment/statistics(?:/tenant)?"), shape: "object", body: () => F.STATISTICS },
  { name: "assessment trend", path: re("/api/v1/assessment/trend(?:/tenant)?"), shape: "array", body: () => F.THREAT_TREND },
  { name: "port distribution", path: re("/api/v1/assessment/port-distribution(?:/tenant)?"), shape: "array", body: () => F.PORT_DISTRIBUTION },
  { name: "top attackers", path: "/api/v1/assessment/top-attackers", shape: "array", body: () => F.TOP_ATTACKERS },
  { name: "assessment detail", path: re("/api/v1/assessment/\\d+"), shape: "object", body: () => F.E2E_THREAT },

  // ---- alerts
  { name: "alert analytics", path: "/api/v1/alerts/analytics", shape: "object", body: () => F.ALERT_ANALYTICS },
  { name: "notification analytics", path: "/api/v1/alerts/notifications/analytics", shape: "object", body: () => F.NOTIFICATION_ANALYTICS },
  { name: "escalation analytics", path: "/api/v1/alerts/escalations/analytics", shape: "object", body: () => F.ESCALATION_ANALYTICS },
  { name: "grouped alerts", path: "/api/v1/alerts/grouped", shape: "page", body: () => F.page([F.GROUPED_ALERT]) },
  { name: "alerts", path: "/api/v1/alerts", shape: "page", body: () => F.page([F.E2E_ALERT]) },
  { name: "alert detail", path: re("/api/v1/alerts/\\d+"), shape: "object", body: () => F.E2E_ALERT },

  // ---- tenants / customers / devices / users
  { name: "tenant search", path: "/api/v1/tenants/search", shape: "page", body: () => F.page([F.E2E_TENANT]) },
  { name: "tenants", path: "/api/v1/tenants", shape: "array", body: () => [F.E2E_TENANT] },
  { name: "tenant detail", path: re(`/api/v1/tenants/${C}`), shape: "object", body: () => F.E2E_TENANT },
  { name: "customer search", path: re("/api/v1/customers(?:/search)?"), shape: "page", body: () => F.page([F.E2E_CUSTOMER]) },
  { name: "customers by status", path: re(`/api/v1/customers/status/${C}`), shape: "page", body: () => F.page([F.E2E_CUSTOMER]) },
  { name: "customers by tenant", path: re(`/api/v1/customers/by-tenant/${C}`), shape: "array", body: () => [F.E2E_CUSTOMER] },
  { name: "device quota", path: re(`/api/v1/customers/${C}/devices/quota`), shape: "object", body: () => F.DEVICE_QUOTA },
  { name: "device events", path: re(`/api/v1/customers/${C}/devices/${C}/events`), shape: "page", body: () => F.page([F.DEVICE_EVENT]) },
  { name: "device detail", path: re(`/api/v1/customers/${C}/devices/${C}`), shape: "object", body: () => F.E2E_DEVICE },
  { name: "devices", path: re(`/api/v1/customers/${C}/devices`), shape: "array", body: () => [F.E2E_DEVICE] },
  { name: "customer notification config", path: re(`/api/v1/customers/${C}/notification-config`), shape: "object", body: () => F.NOTIFICATION_CONFIG },
  { name: "net weights", path: re(`/api/v1/customers/${C}/net-weights`), shape: "array", body: () => F.NET_WEIGHTS },
  { name: "customer stats", path: re(`/api/v1/customers/${C}/stats`), shape: "object", body: () => ({ deviceCount: 1 }) },
  { name: "customer exists", path: re(`/api/v1/customers/${C}/exists`), shape: "scalar", body: () => true },
  { name: "customer detail", path: re(`/api/v1/customers/${C}`), shape: "object", body: () => F.E2E_CUSTOMER },
  { name: "users", path: "/api/v1/users", shape: "array", body: () => F.MANAGED_USERS },
  { name: "user detail", path: re("/api/v1/users/\\d+"), shape: "object", body: () => F.MANAGED_USERS[0] },

  // ---- threat intel
  { name: "intel indicators", path: "/api/v1/threat-intel/indicators", shape: "page", body: () => F.page([F.THREAT_INDICATOR]) },
  { name: "intel indicator", path: re("/api/v1/threat-intel/indicators/\\d+"), shape: "object", body: () => F.THREAT_INDICATOR },
  { name: "intel feeds", path: "/api/v1/threat-intel/feeds", shape: "array", body: () => F.THREAT_FEEDS },
  { name: "intel statistics", path: "/api/v1/threat-intel/statistics", shape: "object", body: () => F.INTEL_STATISTICS },
  { name: "intel lookup", path: "/api/v1/threat-intel/lookup", shape: "object", body: () => F.IP_LOOKUP },

  // ---- operate: pipeline + ML
  { name: "pipeline health", path: "/api/v1/system/health", shape: "object", body: () => F.PIPELINE_HEALTH },
  { name: "ml health", path: "/api/v1/ml/health", shape: "object", body: () => F.ML_HEALTH },
  { name: "ml models", path: "/api/v1/ml/models", shape: "array", body: () => F.ML_MODELS },
  { name: "ml buffer", path: "/api/v1/ml/buffer/stats", shape: "object", body: () => F.ML_BUFFER },
  { name: "ml drift", path: "/api/v1/ml/drift/status", shape: "object", body: () => F.ML_DRIFT },
  { name: "ml shadow", path: "/api/v1/ml/shadow/stats", shape: "object", body: () => F.ML_SHADOW },
  { name: "ml training", path: "/api/v1/ml/train/status", shape: "object", body: () => F.ML_TRAINING },
  { name: "ml readiness", path: "/api/v1/ml/train/data-readiness", shape: "object", body: () => F.ML_READINESS },

  // ---- configuration
  { name: "system config", path: re(`/api/v1/system-config/category/${C}`), shape: "array", body: () => F.SYSTEM_CONFIGS },
  { name: "llm providers", path: "/api/v1/llm-providers", shape: "array", body: () => F.LLM_PROVIDERS },
  { name: "config assignments", path: "/api/v1/config-assignments", shape: "array", body: () => F.CONFIG_ASSIGNMENTS },
  { name: "config assignment", path: re(`/api/v1/config-assignments/${C}`), shape: "object", body: () => F.CONFIG_ASSIGNMENTS[0] },
  { name: "tire plugins", path: "/api/v1/tire-plugins", shape: "array", body: () => F.TIRE_PLUGINS },
  { name: "resolved user config", path: "/api/v1/user-config/resolved", shape: "object", body: () => F.RESOLVED_CONFIG },
  { name: "user config", path: "/api/v1/user-config", shape: "object", body: () => F.USER_CONFIG },
  { name: "smtp config", path: "/api/notification-config/smtp/default", shape: "object", body: () => F.SMTP_CONFIG },
  { name: "entity group members", path: re("/api/v1/entity-groups/\\d+/members"), shape: "array", body: () => F.ENTITY_GROUP_MEMBERS },
  { name: "entity groups", path: "/api/v1/entity-groups", shape: "array", body: () => F.ENTITY_GROUPS },
  { name: "config locks", path: re(`/api/v1/config-inheritance/${C}/${C}`), shape: "array", body: () => F.CONFIG_LOCKS },
  { name: "config lock", path: re(`/api/v1/config-inheritance/${C}/${C}/${C}`), shape: "object", body: () => F.CONFIG_LOCKS[0] },
];

/** `/api/api/v1/x` (doubled prefix from some base URLs) -> `/api/v1/x`. */
export function normalizeApiPath(url: URL): string {
  return url.pathname.replace(/^\/api\/api\//, "/api/");
}

export function matchShape(shape: MockShape, body: unknown): boolean {
  if (shape === "page") {
    return !!body && typeof body === "object" && Array.isArray((body as { content?: unknown }).content);
  }
  if (shape === "array") return Array.isArray(body);
  if (shape === "object") return !!body && typeof body === "object" && !Array.isArray(body);
  return body === null || ["string", "number", "boolean"].includes(typeof body);
}

export type MockResolution =
  | { kind: "mocked"; route: MockRoute; body: unknown }
  | { kind: "unexpected"; path: string };

/** Pure resolver (unit-tested in e2e/api-mocks.spec.ts). */
export function resolveMock(url: URL, routes: MockRoute[] = MOCK_ROUTES): MockResolution {
  const path = normalizeApiPath(url);
  const route = routes.find((candidate) =>
    typeof candidate.path === "string" ? candidate.path === path : candidate.path.test(path),
  );
  if (!route) return { kind: "unexpected", path };
  const body = route.body(url);
  if (!matchShape(route.shape, body)) {
    throw new Error(`E2E mock "${route.name}" for ${path} returned a value that is not a ${route.shape}`);
  }
  return { kind: "mocked", route, body };
}

const reports = new WeakMap<Page, ApiMockReport>();

/** Report for the given page (empty if mocks were not installed). */
export function getApiMockReport(page: Page): ApiMockReport {
  return reports.get(page) ?? { unexpectedRequests: [], servedRequests: [] };
}

async function fulfillJson(route: Route, status: number, body: unknown) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

/**
 * Install strict GET mocks for every `/api/**` request on the page.
 * Non-GET requests fall through unchanged (specs mock mutations themselves).
 * Set `E2E_MOCK_LOG=<file>` to append unexpected paths to a file.
 */
export async function installApiMocks(page: Page, routes: MockRoute[] = MOCK_ROUTES): Promise<ApiMockReport> {
  const report: ApiMockReport = { unexpectedRequests: [], servedRequests: [] };
  reports.set(page, report);
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    if (request.method() !== "GET") {
      await route.fallback();
      return;
    }
    const resolution = resolveMock(new URL(request.url()), routes);
    if (resolution.kind === "unexpected") {
      const entry = `GET ${resolution.path}`;
      report.unexpectedRequests.push(entry);
      if (process.env.E2E_MOCK_LOG) appendFileSync(process.env.E2E_MOCK_LOG, `${entry}\n`);
      await fulfillJson(route, 501, { message: `E2E: no mock registered for ${entry}` });
      return;
    }
    report.servedRequests.push(`GET ${normalizeApiPath(new URL(request.url()))}`);
    await fulfillJson(route, 200, resolution.body);
  });
  return report;
}
