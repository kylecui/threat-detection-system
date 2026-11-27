import { chromium, type FullConfig } from "@playwright/test";
import { readFileSync, statfsSync } from "fs";
import { cpus } from "os";
import { setAuthState } from "./helpers/auth";
import { APP_READY_TIMEOUT, waitForAppReady } from "./helpers/ready";

/**
 * INTERACTION-011: controlled warm-up before any test starts.
 *
 * `npm run dev` answers on :3000 long before the lazy route modules are
 * compiled; the first visit of each route used to happen inside a test and
 * counted against its business timeouts (and could trigger Vite's dependency
 * re-optimisation reload mid-test). This setup visits every lazy route once in
 * an isolated browser context (nothing is shared with the tests' contexts),
 * waits for the real app-ready contract on each, then re-checks all routes
 * with the normal APP_READY_TIMEOUT to prove they are warm.
 *
 * It does not replace the readiness waits in the specs. Set
 * E2E_SKIP_WARMUP=1 to skip it (e.g. when iterating on a single spec).
 */
export const WARMUP_ROUTES = [
  "/login",
  "/overview",
  "/investigate/alerts",
  "/investigate/threats",
  "/investigate/intel",
  "/operate/pipeline",
  "/operate/ml",
  "/admin/customers",
  "/admin/users",
  "/admin/tenants",
  "/config/general",
  "/config/notifications",
  "/config/integrations",
  "/config/ai",
  "/config/plugins",
  "/__e2e-warmup-not-found__",
  "/e2e/harness/config-cascading.html",
];

/** Cold compilation of one route on a loaded 4-worker host. */
const COLD_ROUTE_TIMEOUT = 120_000;

function log(message: string) {
  console.log(`[e2e warm-up] ${message}`);
}

function environmentSummary(): string {
  const parts = [`cpus=${cpus().length}`];
  try {
    const meminfo = readFileSync("/proc/meminfo", "utf8");
    const available = /^MemAvailable:\s+(\d+)/m.exec(meminfo);
    if (available) parts.push(`MemAvailable=${(Number(available[1]) / 1048576).toFixed(2)}GiB`);
  } catch {
    // not Linux
  }
  try {
    const shm = statfsSync("/dev/shm");
    parts.push(`/dev/shm=${((shm.blocks * shm.bsize) / 1073741824).toFixed(2)}GiB`);
  } catch {
    // no /dev/shm
  }
  return parts.join(" ");
}

export default async function globalSetup(config: FullConfig): Promise<void> {
  if (process.env.E2E_SKIP_WARMUP === "1") {
    log("skipped (E2E_SKIP_WARMUP=1)");
    return;
  }
  const use = config.projects[0]?.use ?? {};
  const baseURL = use.baseURL ?? "http://localhost:3000";
  log(`environment: ${environmentSummary()}`);

  const browser = await chromium.launch(use.launchOptions ?? {});
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  const started = Date.now();
  try {
    await setAuthState(page, "SUPER_ADMIN", undefined, "en-US");
    for (const pass of [1, 2] as const) {
      const timeout = pass === 1 ? COLD_ROUTE_TIMEOUT : APP_READY_TIMEOUT;
      for (const route of WARMUP_ROUTES) {
        const routeStarted = Date.now();
        await page.goto(route);
        try {
          await waitForAppReady(page, { timeout });
        } catch (error) {
          throw new Error(`warm-up pass ${pass}: ${route} did not become ready: ${(error as Error).message}`);
        }
        if (pass === 1) log(`${route} ready in ${((Date.now() - routeStarted) / 1000).toFixed(1)}s`);
      }
    }
    log(`all ${WARMUP_ROUTES.length} routes warm in ${((Date.now() - started) / 1000).toFixed(1)}s`);
  } finally {
    await context.close();
    await browser.close();
  }
}
