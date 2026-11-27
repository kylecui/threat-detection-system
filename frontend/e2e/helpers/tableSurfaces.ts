/**
 * INTERACTION-004: table display-surface inventory.
 *
 * A "surface" is one concrete state in which tables are on screen (a route,
 * a tab, a view mode, an expanded row, an open modal or drawer). Every one of
 * the 24 production <Table>/<ProTable> instances is covered by exactly one
 * surface (`instances`), which `tests/test_table_layout_contract.py` checks
 * against the source code. Specs never "find any table and skip otherwise":
 * each surface declares how many tables it must show, or which mobile card
 * view replaces them at a given viewport.
 */
import { expect, type Page } from "@playwright/test";
import type { E2ERole } from "./auth";
import { tr, type E2ELanguage } from "./locale";
import { waitForOverviewReady, waitForTableReady } from "./ready";

export type SurfaceScope = "content" | "modal" | "drawer";
export type SurfaceMode = "table" | "cards";

export const SCOPE_SELECTOR: Record<SurfaceScope, string> = {
  content: ".tds-content",
  modal: ".ant-modal-root .ant-modal",
  drawer: ".ant-drawer-open .ant-drawer-content",
};

export interface SurfaceContext {
  page: Page;
  language: E2ELanguage;
}

export interface TableInstanceRef {
  /** Path under frontend/src. */
  file: string;
  /** 1-based order of the <Table>/<ProTable> tag inside the file. */
  index: number;
}

export interface TableSurface {
  id: string;
  /** Production route, or the test-only harness page for unrouted components. */
  route: string;
  role: E2ERole;
  scope: SurfaceScope;
  /** Exact number of visible tables (including nested ones) in the scope. */
  expectedTables: number;
  instances: TableInstanceRef[];
  /** Viewport width -> mode; widths not listed use "table". */
  modes?: Partial<Record<number, SurfaceMode>>;
  /** Visible replacement when the mode is "cards". */
  cardsSelector?: string;
  /**
   * Rendered by @ant-design/pro-components. Only relevant for the local
   * UMD build (see E2E_LOCAL_UMD_BUILD in the spec), where ProTable carries
   * its own antd copy and therefore its own theme tokens.
   */
  proTable?: boolean;
  /** Steps after navigation that reveal the tables. */
  enter?: (context: SurfaceContext) => Promise<void>;
  /**
   * INTERACTION-011: page-level data readiness after navigation, used in
   * every mode (e.g. the Overview skeleton has no tables *and* no cards).
   */
  ready?: (page: Page) => Promise<void>;
}

/** INTERACTION-011: rows present, no spinner, stable for 1s (shared helper). */
async function waitForRows(page: Page, scope: string, minimum = 1) {
  await waitForTableReady(page.locator(scope), { minRows: minimum });
}

async function selectViewMode(page: Page, index: number) {
  await page.locator(".tds-content .ant-segmented-item").nth(index).click();
}

async function expandFirstRow(page: Page) {
  await waitForRows(page, ".tds-content");
  await page.locator(".tds-content .ant-table-row-expand-icon").first().click();
  await expect(page.locator(".tds-content .ant-table-expanded-row .ant-table")).toBeVisible();
  await waitForRows(page, ".tds-content .ant-table-expanded-row");
}

async function openTab(page: Page, index: number) {
  await page.locator(".tds-content .ant-tabs-tab").nth(index).click();
}

async function openCustomerDeviceDrawer({ page, language }: SurfaceContext) {
  await waitForRows(page, ".tds-content");
  await page.locator(".tds-content .ant-table-tbody").getByRole("button", { name: tr(language, "customersDevices.devices"), exact: true }).first().click();
  await expect(page.locator(SCOPE_SELECTOR.drawer)).toBeVisible();
}

async function loadConfigLocks({ page, language }: SurfaceContext) {
  const form = page.locator(".tds-content form").first();
  await form.locator(".ant-select").first().click();
  await page.locator(".ant-select-dropdown:visible .ant-select-item-option", { hasText: tr(language, "configCascading.customer") }).click();
  await form.locator("input[type=number]").fill("101");
  await form.getByRole("button", { name: tr(language, "configCascading.loadLocks") }).click();
}

export const TABLE_SURFACES: TableSurface[] = [
  {
    id: "overview",
    route: "/overview",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 2,
    instances: [
      { file: "pages/Overview/index.tsx", index: 1 },
      { file: "pages/Overview/index.tsx", index: 2 },
    ],
    modes: { 768: "cards", 390: "cards" },
    cardsSelector: ".tds-overview__mobile-list",
    ready: waitForOverviewReady,
  },
  {
    id: "alerts-grouped-expanded",
    route: "/investigate/alerts",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 2,
    instances: [
      { file: "pages/AlertCenter/index.tsx", index: 2 },
      { file: "pages/AlertCenter/index.tsx", index: 3 },
    ],
    enter: async ({ page }) => {
      await selectViewMode(page, 1);
      await expandFirstRow(page);
    },
  },
  {
    id: "alerts-detail",
    route: "/investigate/alerts",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    proTable: true,
    instances: [{ file: "pages/AlertCenter/index.tsx", index: 1 }],
    enter: async ({ page }) => selectViewMode(page, 0),
  },
  {
    id: "threats-grouped-expanded",
    route: "/investigate/threats",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 2,
    instances: [
      { file: "pages/ThreatList/index.tsx", index: 2 },
      { file: "pages/ThreatList/index.tsx", index: 3 },
    ],
    enter: async ({ page }) => {
      await selectViewMode(page, 1);
      await expandFirstRow(page);
    },
  },
  {
    id: "threats-detail",
    route: "/investigate/threats",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/ThreatList/index.tsx", index: 1 }],
    enter: async ({ page }) => selectViewMode(page, 0),
  },
  {
    id: "intel-indicators",
    route: "/investigate/intel",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    proTable: true,
    instances: [{ file: "pages/ThreatIntel/index.tsx", index: 1 }],
  },
  {
    id: "intel-feeds",
    route: "/investigate/intel",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/ThreatIntel/index.tsx", index: 2 }],
    enter: async ({ page }) => openTab(page, 1),
  },
  {
    id: "pipeline",
    route: "/operate/pipeline",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/PipelineHealth/index.tsx", index: 1 }],
    modes: { 390: "cards" },
    cardsSelector: ".tds-operate-mobile-records",
  },
  {
    id: "ml",
    route: "/operate/ml",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/MlDetection/index.tsx", index: 1 }],
    modes: { 390: "cards" },
    cardsSelector: ".tds-operate-mobile-records",
  },
  {
    id: "customers",
    route: "/admin/customers",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    proTable: true,
    instances: [{ file: "pages/CustomersAndDevices/index.tsx", index: 1 }],
  },
  {
    id: "customers-device-drawer",
    route: "/admin/customers",
    role: "SUPER_ADMIN",
    scope: "drawer",
    expectedTables: 1,
    instances: [{ file: "pages/CustomersAndDevices/index.tsx", index: 2 }],
    enter: openCustomerDeviceDrawer,
  },
  {
    id: "customers-device-events-modal",
    route: "/admin/customers",
    role: "SUPER_ADMIN",
    scope: "modal",
    expectedTables: 1,
    instances: [{ file: "pages/CustomersAndDevices/index.tsx", index: 3 }],
    enter: async (context) => {
      await openCustomerDeviceDrawer(context);
      await waitForRows(context.page, SCOPE_SELECTOR.drawer);
      await context.page
        .locator(SCOPE_SELECTOR.drawer)
        .getByRole("button", { name: tr(context.language, "customersDevices.dataFlow"), exact: true })
        .first()
        .click();
      await expect(context.page.locator(SCOPE_SELECTOR.modal)).toBeVisible();
      await waitForRows(context.page, SCOPE_SELECTOR.modal);
    },
  },
  {
    id: "customers-device-list",
    route: "/admin/customers",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    proTable: true,
    instances: [{ file: "pages/CustomersAndDevices/index.tsx", index: 4 }],
    enter: async ({ page }) => openTab(page, 1),
  },
  {
    id: "users",
    route: "/admin/users",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/UserMgmt/index.tsx", index: 1 }],
  },
  {
    id: "tenants",
    route: "/admin/tenants",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/TenantMgmt/index.tsx", index: 1 }],
  },
  {
    id: "ai-config",
    route: "/config/ai",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 2,
    instances: [
      { file: "pages/Configuration/AIConfig.tsx", index: 1 },
      { file: "pages/Configuration/AIConfig.tsx", index: 2 },
    ],
  },
  {
    id: "plugins",
    route: "/config/plugins",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 1,
    instances: [{ file: "pages/Configuration/PluginConfig.tsx", index: 1 }],
  },
  {
    // ConfigCascading has no production route; rendered by the test-only
    // harness (e2e/harness/config-cascading.html) with production providers.
    id: "config-cascading",
    route: "/e2e/harness/config-cascading.html",
    role: "SUPER_ADMIN",
    scope: "content",
    expectedTables: 2,
    instances: [
      { file: "pages/Configuration/ConfigCascading.tsx", index: 1 },
      { file: "pages/Configuration/ConfigCascading.tsx", index: 2 },
    ],
    enter: loadConfigLocks,
  },
  {
    id: "config-cascading-members-modal",
    route: "/e2e/harness/config-cascading.html",
    role: "SUPER_ADMIN",
    scope: "modal",
    expectedTables: 1,
    instances: [{ file: "pages/Configuration/ConfigCascading.tsx", index: 3 }],
    enter: async ({ page, language }) => {
      await waitForRows(page, ".tds-content");
      await page.locator(".tds-content .ant-table-tbody").getByRole("button", { name: tr(language, "configCascading.manageMembers"), exact: true }).first().click();
      await expect(page.locator(SCOPE_SELECTOR.modal)).toBeVisible();
    },
  },
];

export interface Viewport {
  width: number;
  height: number;
}

/** Full acceptance matrix (INTERACTION-004 §5.3). */
export const FULL_VIEWPORTS: Viewport[] = [
  { width: 1920, height: 1080 },
  { width: 1440, height: 900 },
  { width: 1280, height: 800 },
  { width: 1024, height: 768 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
];

export function surfaceMode(surface: TableSurface, width: number): SurfaceMode {
  return surface.modes?.[width] ?? "table";
}
