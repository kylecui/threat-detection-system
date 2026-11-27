import { expect, test, type Page } from "@playwright/test";
import { getApiMockReport, setAuthState, setTheme, type E2ETheme } from "./helpers/auth";
import { collectErrors } from "./helpers/consoleGate";
import type { E2ELanguage } from "./helpers/locale";
import { inspectTables } from "./helpers/tableAssertions";
import {
  FULL_VIEWPORTS,
  SCOPE_SELECTOR,
  TABLE_SURFACES,
  surfaceMode,
  type TableSurface,
  type Viewport,
} from "./helpers/tableSurfaces";
import { gotoReady } from "./helpers/ready";

/**
 * INTERACTION-001/003/004: global table layout regression, driven by the
 * TABLE_SURFACES inventory (all 24 tables, incl. tabs, expanded rows,
 * modals and drawers).
 *
 * - Default (commit) tier: every surface at 1440 + 390 (en-US, light),
 *   1440 zh-CN and 1440 dark.
 * - Full tier: TABLE_MATRIX=full -> 6 viewports x 2 languages x 2 themes.
 *
 * There is no runtime skip: a missing table fails, and mobile card views are
 * asserted as the expected replacement. Browser zoom (80-200%) is NOT covered
 * here; see the INTERACTION-004 result document for the dedicated manual run.
 */
interface Combo {
  viewport: Viewport;
  language: E2ELanguage;
  theme: E2ETheme;
}

const QUICK_COMBOS: Combo[] = [
  { viewport: { width: 1440, height: 900 }, language: "en-US", theme: "light" },
  { viewport: { width: 390, height: 844 }, language: "en-US", theme: "light" },
  { viewport: { width: 1440, height: 900 }, language: "zh-CN", theme: "light" },
  { viewport: { width: 1440, height: 900 }, language: "en-US", theme: "dark" },
];

const FULL_COMBOS: Combo[] = FULL_VIEWPORTS.flatMap((viewport) =>
  (["en-US", "zh-CN"] as const).flatMap((language) =>
    (["light", "dark"] as const).map((theme) => ({ viewport, language, theme })),
  ),
);

const COMBOS = process.env.TABLE_MATRIX === "full" ? FULL_COMBOS : QUICK_COMBOS;

// Console / page error gate: helpers/consoleGate.ts (strict; no StrictMode allowlist).

/**
 * Problems found by this matrix that are NOT table defects and are tracked
 * as separate issues (INTERACTION-004 §6: never hide them by loosening the
 * checks). A registration only exempts the exact problem text on the listed
 * surface/viewports, is reported as an annotation, and FAILS once the
 * problem no longer reproduces so the entry gets removed.
 */
interface KnownIssue {
  id: string;
  surface: string;
  widths: number[];
  /** Languages in which it reproduces (labels differ in length). */
  languages: E2ELanguage[];
  problem: RegExp;
  reason: string;
}

// NEW-A and NEW-B were fixed by INTERACTION-006 and INTERACTION-007 and
// removed. Keep the list (and its "no longer reproduces" check) for future
// findings; an entry here must never be added just to make a run pass.
const KNOWN_ISSUES: KnownIssue[] = [];

/**
 * The local verification build (no npm registry) loads ProTable from the
 * pro-components UMD bundle, which embeds its own antd copy and therefore
 * ignores the app theme; its cell contrast is only meaningful in the real
 * Vite build. Never set this when running against `npm run dev`.
 */
const LOCAL_UMD_BUILD = process.env.E2E_LOCAL_UMD_BUILD === "1";

async function checkOverflowTooltips(page: Page, scope: string) {
  const states = await page.locator(`${scope} .tds-table-cell-text[data-full-text]`).evaluateAll((elements) =>
    elements.map((element) => ({
      overflowing: element.scrollWidth > element.clientWidth + 1,
      visible: !!(element as HTMLElement).offsetParent,
    })),
  );
  const cells = page.locator(`${scope} .tds-table-cell-text[data-full-text]`);
  const overflowIndex = states.findIndex((state) => state.visible && state.overflowing);
  if (overflowIndex >= 0) {
    const cell = cells.nth(overflowIndex);
    const fullText = (await cell.getAttribute("data-full-text")) ?? "";
    await cell.hover();
    await expect(page.locator(".ant-tooltip:visible").last(), "truncated text shows its full value").toContainText(fullText);
    await page.mouse.move(0, 0);
    await expect(page.locator(".ant-tooltip:visible")).toHaveCount(0);
  }
  const fittingIndex = states.findIndex((state) => state.visible && !state.overflowing);
  if (fittingIndex >= 0) {
    await cells.nth(fittingIndex).hover();
    await page.waitForTimeout(400);
    await expect(page.locator(".ant-tooltip:visible"), "text that fits has no tooltip").toHaveCount(0);
    await page.mouse.move(0, 0);
  }
}

async function assertCards(page: Page, surface: TableSurface) {
  // One card list replaces each desktop table.
  const cards = page.locator(surface.cardsSelector ?? "__missing_cards_selector__");
  await expect(cards, `${surface.id}: mobile card views`).toHaveCount(surface.expectedTables);
  for (let index = 0; index < surface.expectedTables; index += 1) {
    const list = cards.nth(index);
    await expect(list, `${surface.id}: mobile card view ${index + 1}`).toBeVisible();
    await expect.poll(async () => list.locator(":scope > *").count()).toBeGreaterThan(0);
    expect((await list.innerText()).trim().length, "cards render data").toBeGreaterThan(0);
  }
  const visibleTables = await page.locator(`${SCOPE_SELECTOR[surface.scope]} .ant-table`).evaluateAll(
    (tables) => tables.filter((table) => !!(table as HTMLElement).offsetParent).length,
  );
  expect(visibleTables, "desktop table is replaced, not duplicated").toBe(0);
}

for (const combo of COMBOS) {
  const { viewport, language, theme } = combo;
  test.describe(`table layout @${viewport.width} ${language} ${theme}`, () => {
    test.use({ viewport });

    for (const surface of TABLE_SURFACES) {
      test(`${surface.id}`, async ({ page }) => {
        const errors = collectErrors(page);
        await setTheme(page, theme);
        await setAuthState(page, surface.role, undefined, language);
        await gotoReady(page, surface.route);
        await surface.ready?.(page);
        await expect(page.locator("html")).toHaveAttribute("data-theme", theme);

        if (surfaceMode(surface, viewport.width) === "cards") {
          await assertCards(page, surface);
        } else {
          await surface.enter?.({ page, language });
          const scope = SCOPE_SELECTOR[surface.scope];
          const checkContrast = !(LOCAL_UMD_BUILD && surface.proTable);
          if (!checkContrast) {
            test.info().annotations.push({ type: "local-umd-build", description: "ProTable contrast not checked (pro-components UMD has its own antd theme)" });
          }
          const inspect = () => page.evaluate(inspectTables, { scope, tolerance: 3, checkContrast });

          // Wait for the declared tables *with data* instead of skipping.
          // "Ready" must hold continuously for 1s: ProTable may re-request once
          // the tenant/customer scope resolves. Only readiness is polled; the
          // layout assertions below run once and are never retried.
          let readySince = 0;
          await expect
            .poll(async () => {
              const report = await inspect();
              const ready =
                report.tables === surface.expectedTables &&
                report.rows >= surface.expectedTables &&
                report.spinning === 0 &&
                !report.animating;
              if (!ready) {
                readySince = 0;
                return false;
              }
              readySince ||= Date.now();
              return Date.now() - readySince >= 1_000;
            }, { message: `${surface.id}: expected ${surface.expectedTables} loaded table(s) with rows`, timeout: 30_000, intervals: [250] })
            .toBe(true);

          const report = await inspect();
          expect(report.tables).toBe(surface.expectedTables);
          expect(report.cells).toBeGreaterThan(0);
          const knownIssues = KNOWN_ISSUES.filter(
            (issue) =>
              issue.surface === surface.id &&
              issue.widths.includes(viewport.width) &&
              issue.languages.includes(language),
          );
          const unexpected = report.problems.filter(
            (problem) => !knownIssues.some((issue) => issue.problem.test(problem)),
          );
          expect(unexpected).toEqual([]);
          for (const issue of knownIssues) {
            test.info().annotations.push({ type: "known-issue", description: `${issue.id}: ${issue.reason}` });
            expect(
              report.problems.some((problem) => issue.problem.test(problem)),
              `${issue.id} no longer reproduces — remove it from KNOWN_ISSUES`,
            ).toBe(true);
          }
          await checkOverflowTooltips(page, scope);
        }

        expect(
          await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth),
          "page never scrolls horizontally",
        ).toBeLessThanOrEqual(0);
        expect(errors.pageErrors).toEqual([]);
        expect(errors.consoleErrors).toEqual([]);
        expect(getApiMockReport(page).unexpectedRequests).toEqual([]);
      });
    }
  });
}
