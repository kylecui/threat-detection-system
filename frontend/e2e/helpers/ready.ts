import { expect, type Locator, type Page } from "@playwright/test";

/**
 * INTERACTION-011: shared readiness contract for E2E.
 *
 * Startup chain of every page:
 *   server reachable -> lazy route chunk -> auth restore -> Scope init
 *   -> page request -> React state commit -> table / cards rendered
 *
 * The app exposes the real state on its root element (App.tsx `.tds-shell`,
 * Login `.tds-login`, test harnesses):
 *   data-app-ready="true|false"  Scope initialised (login: bootstrap check settled)
 *   data-route-path="/path"       route that is actually *committed*
 * The root Suspense fallback is `.tds-app-loading`.
 *
 * Rules: navigate with `gotoReady()` (or `page.goto()` + `waitForAppReady()`),
 * wait for page data with the page-level helpers below, never with fixed
 * sleeps. `setAuthState()` only injects state; it never navigates or waits.
 */
export const APP_READY_TIMEOUT = 20_000;
export const DATA_READY_TIMEOUT = 15_000;
export const STABLE_MS = 1_000;

export interface AppState {
  url: string;
  path: string;
  globalLoading: boolean;
  roots: Array<{ ready: string | null; route: string | null }>;
  spinning: number;
  busy: number;
}

async function readAppState(page: Page): Promise<AppState | undefined> {
  try {
    return await page.evaluate(() => ({
      url: window.location.href,
      path: window.location.pathname,
      globalLoading: !!document.querySelector(".tds-app-loading"),
      roots: Array.from(document.querySelectorAll("[data-app-ready]")).map((element) => ({
        ready: element.getAttribute("data-app-ready"),
        route: element.getAttribute("data-route-path"),
      })),
      spinning: document.querySelectorAll(".ant-spin-spinning").length,
      busy: document.querySelectorAll('[aria-busy="true"]').length,
    }));
  } catch {
    return undefined; // navigation in progress: execution context replaced
  }
}

function appIsReady(state: AppState | undefined): boolean {
  return (
    !!state &&
    !state.globalLoading &&
    state.roots.some((root) => root.ready === "true" && root.route === state.path)
  );
}

/** Human-readable "which layer is not ready" for failure messages. */
export function describeAppState(state: AppState | undefined): string {
  if (!state) return "page is navigating (no execution context)";
  let layer: string;
  if (state.globalLoading) layer = "global Suspense fallback .tds-app-loading still shown (lazy route chunk / bootstrap)";
  else if (state.roots.length === 0) layer = "no app shell, login page or harness root mounted";
  else if (!state.roots.some((root) => root.ready === "true"))
    layer = "Scope / login bootstrap not initialised (data-app-ready=false)";
  else if (!state.roots.some((root) => root.route === state.path))
    layer = `route not committed: URL path ${state.path}, rendered ${state.roots.map((root) => root.route).join(", ")}`;
  else layer = "ready";
  return `${layer}; url=${state.url}; spinning=${state.spinning}; aria-busy=${state.busy}`;
}

/**
 * Generic "condition holds continuously for `stableMs`" wait (the pattern
 * first used by table-layout.spec.ts). `describe` builds the failure detail.
 */
export async function waitForStable<T>(
  probe: () => Promise<T>,
  isReady: (value: T) => boolean,
  options: { what: string; timeout?: number; stableMs?: number; describe?: (value: T | undefined) => string },
): Promise<T> {
  const { what, timeout = DATA_READY_TIMEOUT, stableMs = STABLE_MS } = options;
  let last: T | undefined;
  let readySince = 0;
  try {
    await expect
      .poll(
        async () => {
          last = await probe();
          if (!isReady(last)) {
            readySince = 0;
            return false;
          }
          readySince ||= Date.now();
          return Date.now() - readySince >= stableMs;
        },
        { timeout, intervals: [100, 250] },
      )
      .toBe(true);
  } catch {
    const detail = options.describe ? options.describe(last) : JSON.stringify(last);
    throw new Error(`${what} not ready within ${timeout}ms: ${detail}`);
  }
  return last as T;
}

/**
 * INTERACTION-015: app root mounted for the current URL, *without* requiring
 * initialisation: Suspense fallback gone and the root committed for this path,
 * `data-app-ready` may still be "false". For negative scenarios that keep
 * Scope / bootstrap pending on purpose (`waitForAppReady` would never pass).
 */
export function isAppRootMounted(state: AppState | undefined): boolean {
  return !!state && !state.globalLoading && state.roots.some((root) => root.route === state.path);
}

/** Wait (same cold-start budget as `waitForAppReady`) until `isAppRootMounted`. */
export async function waitForAppRootMounted(page: Page, options: { timeout?: number } = {}): Promise<void> {
  const timeout = options.timeout ?? APP_READY_TIMEOUT;
  await waitForStable(() => readAppState(page), isAppRootMounted, {
    what: "App root mount",
    timeout,
    stableMs: 0, // one bounded confirmation; no stability window needed
    describe: (state) =>
      state && !state.globalLoading && state.roots.length > 0 && !isAppRootMounted(state)
        ? `route not committed: URL path ${state.path}, rendered ${state.roots.map((root) => root.route).join(", ")}; url=${state.url}`
        : describeAppState(state),
  });
}

/** App shell (or login / harness) mounted, Scope initialised, current URL committed. */
export async function waitForAppReady(page: Page, options: { timeout?: number } = {}): Promise<void> {
  const timeout = options.timeout ?? APP_READY_TIMEOUT;
  let last: AppState | undefined;
  try {
    await expect
      .poll(
        async () => {
          last = await readAppState(page);
          return appIsReady(last);
        },
        { timeout, intervals: [100, 250] },
      )
      .toBe(true);
  } catch {
    throw new Error(`App not ready within ${timeout}ms: ${describeAppState(last)}`);
  }
}

/** Navigate and wait for the app-level contract. */
export async function gotoReady(page: Page, url: string, options: { timeout?: number } = {}): Promise<void> {
  await page.goto(url);
  await waitForAppReady(page, options);
}

/** Overview: app ready and the loading skeleton (`aria-busy`) replaced by data. */
export async function waitForOverviewReady(page: Page): Promise<void> {
  await waitForAppReady(page);
  await waitForStable(
    () =>
      page.evaluate(() => ({
        skeleton: document.querySelectorAll(".tds-overview--loading, .tds-overview[aria-busy='true']").length,
        loaded: document.querySelectorAll(".tds-overview:not(.tds-overview--loading)").length,
      })),
    (state) => state.skeleton === 0 && state.loaded === 1,
    { what: "Overview data", describe: (state) => JSON.stringify(state) },
  );
}

export interface TableReadyOptions {
  /** Minimum number of visible `.ant-table` elements (default 1). */
  tables?: number;
  /** "data": at least `minRows` rows (default); "empty": an explicit empty state. */
  mode?: "data" | "empty";
  minRows?: number;
  timeout?: number;
}

/**
 * Tables inside `root` are loaded and stable: no spinner, the expected number
 * of tables and rows (or an explicit empty state), unchanged for 1 second.
 */
export async function waitForTableReady(root: Locator, options: TableReadyOptions = {}) {
  const { tables = 1, mode = "data", minRows = 1, timeout = DATA_READY_TIMEOUT } = options;
  return waitForStable(
    () =>
      root.evaluateAll((elements) => {
        const visible = (element: Element) => !!(element as HTMLElement).offsetParent;
        const within = (selector: string) =>
          elements.flatMap((element) => Array.from(element.querySelectorAll(selector))).filter(visible);
        return {
          roots: elements.length,
          tables: within(".ant-table").length,
          rows: within(".ant-table-tbody tr.ant-table-row").length,
          empty: within(".ant-table-placeholder .ant-empty, .ant-table-placeholder").length,
          spinning: elements.reduce((count, element) => count + element.querySelectorAll(".ant-spin-spinning").length, 0),
        };
      }),
    (state) =>
      state.roots > 0 &&
      state.spinning === 0 &&
      state.tables >= tables &&
      (mode === "data" ? state.rows >= minRows : state.rows === 0 && state.empty > 0),
    { what: `table (${mode})`, timeout, describe: (state) => JSON.stringify(state) },
  );
}
