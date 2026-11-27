import { expect, test } from "@playwright/test";
import { MOCK_ROUTES, matchShape, resolveMock } from "./helpers/mockApi";

/**
 * INTERACTION-003: contract of the E2E API mocks themselves (no browser).
 * Field-level shapes are enforced by `npm run typecheck:e2e` (fixtures use
 * `satisfies <production type>`); this spec guards top-level shapes and the
 * "no fake success for unknown endpoints" rule.
 */
const url = (path: string) => new URL(`http://localhost:3000${path}`);

test.describe("E2E API mock contract", () => {
  test("every registered route returns the declared top-level shape", () => {
    for (const route of MOCK_ROUTES) {
      const body = route.body(url("/api/v1/placeholder"));
      expect(matchShape(route.shape, body), `${route.name} must return a ${route.shape}`).toBe(true);
    }
  });

  test("objects, arrays and pages are never mixed up", () => {
    const cases: Array<[string, string]> = [
      ["/api/v1/system/health", "object"],
      ["/api/v1/ml/models", "array"],
      ["/api/v1/ml/health", "object"],
      ["/api/v1/ml/buffer/stats", "object"],
      ["/api/v1/ml/drift/status", "object"],
      ["/api/v1/ml/shadow/stats", "object"],
      ["/api/v1/ml/train/status", "object"],
      ["/api/v1/ml/train/data-readiness", "object"],
      ["/api/v1/assessment/assessments", "page"],
      ["/api/v1/alerts", "page"],
      ["/api/v1/users", "array"],
      ["/api/v1/tenants/search", "page"],
      ["/api/v1/tenants/1", "object"],
      ["/api/v1/customers/e2e-customer/devices", "array"],
      ["/api/v1/customers/e2e-customer/devices/quota", "object"],
      ["/api/v1/customers/e2e-customer/devices/E2E-DEVICE-0001/events", "page"],
    ];
    for (const [path, shape] of cases) {
      const resolution = resolveMock(url(path));
      expect(resolution.kind, path).toBe("mocked");
      if (resolution.kind === "mocked") expect(resolution.route.shape, path).toBe(shape);
    }
    const pipeline = resolveMock(url("/api/v1/system/health"));
    expect(pipeline.kind === "mocked" && "services" in (pipeline.body as object)).toBe(true);
    const models = resolveMock(url("/api/v1/ml/models"));
    expect(models.kind === "mocked" && Array.isArray(models.body) && (models.body as unknown[]).length > 0).toBe(true);
  });

  test("trend mocks carry exactly the backend TrendDataPoint fields (INTERACTION-008)", () => {
    // Mirrors services/threat-assessment .../dto/TrendDataPoint.java. Chart
    // fields (time/value/category) must never be added to API fixtures again.
    const DTO_FIELDS = ["averageScore", "count", "criticalCount", "highCount", "maxScore", "mediumCount", "timestamp"];
    const sources: Array<[string, unknown]> = [];
    const snapshot = resolveMock(url("/api/v1/assessment/overview-snapshot"));
    if (snapshot.kind === "mocked") sources.push(["overview-snapshot trend", (snapshot.body as { trend: unknown }).trend]);
    for (const path of ["/api/v1/assessment/trend", "/api/v1/assessment/trend/tenant"]) {
      const resolution = resolveMock(url(path));
      expect(resolution.kind, path).toBe("mocked");
      if (resolution.kind === "mocked") sources.push([path, resolution.body]);
    }
    expect(sources).toHaveLength(3);
    for (const [name, points] of sources) {
      expect(Array.isArray(points) && points.length > 0, `${name} has points`).toBe(true);
      for (const point of points as Array<Record<string, unknown>>) {
        expect(Object.keys(point).sort(), name).toEqual(DTO_FIELDS);
        expect(Number.isNaN(Date.parse(String(point.timestamp))), `${name}: ISO timestamp`).toBe(false);
        expect(typeof point.count, `${name}: numeric count`).toBe("number");
      }
    }
  });

  test("an unregistered GET is reported, never answered with a fake page", () => {
    const resolution = resolveMock(url("/api/v1/not-a-real-endpoint"));
    expect(resolution).toEqual({ kind: "unexpected", path: "/api/v1/not-a-real-endpoint" });
  });

  test("the doubled /api/api prefix is normalised before matching", () => {
    const resolution = resolveMock(url("/api/api/v1/ml/models"));
    expect(resolution.kind).toBe("mocked");
  });

  test("a mock returning the wrong shape fails loudly", () => {
    expect(() =>
      resolveMock(url("/api/v1/x"), [{ name: "broken", path: "/api/v1/x", shape: "array", body: () => ({ content: [] }) }]),
    ).toThrow(/not a array/);
  });
});
