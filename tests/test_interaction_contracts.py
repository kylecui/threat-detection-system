#!/usr/bin/env python3
"""INTERACTION-006/007/008/009/010: static contracts for the follow-up fixes.

Reads frontend sources only (no build), like test_table_layout_contract.py:

    python -m unittest tests.test_interaction_contracts
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "frontend"
SRC = FRONTEND / "src"
E2E = FRONTEND / "e2e"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def code_only(text: str) -> str:
    """Source without /* */ and // comments (good enough for these TS/TSX files)."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"(?m)^\s*//.*$|\{/\*.*?\*/\}", "", text)


def block(text: str, start: str, end: str) -> str:
    """Return the source between the first `start` marker and the next `end`."""
    begin = text.index(start)
    return text[begin : text.index(end, begin)]


class DeviceFlowSummaryContract(unittest.TestCase):
    """INTERACTION-006: responsive device data-flow summary."""

    page = read(SRC / "pages" / "CustomersAndDevices" / "index.tsx")
    css = read(SRC / "index.css")

    def summary(self) -> str:
        return block(self.page, 'className="tds-detail-descriptions tds-device-flow-descriptions"', "</Descriptions>")

    def test_column_count_is_responsive(self) -> None:
        summary = self.summary()
        self.assertIn("column={DEVICE_FLOW_COLUMNS}", summary)
        self.assertNotRegex(summary, r"column=\{\d+\}")
        columns = re.search(r"const DEVICE_FLOW_COLUMNS = \{([^}]*)\}", self.page)
        self.assertIsNotNone(columns)
        values = dict(re.findall(r"(\w+):\s*(\d)", columns.group(1)))
        self.assertEqual(values, {"xs": "1", "sm": "1", "md": "2", "lg": "3", "xl": "3", "xxl": "3"})

    def test_no_span_assumes_three_columns(self) -> None:
        summary = self.summary()
        self.assertNotRegex(summary, r"span=\{[2-9]\}")
        self.assertEqual(summary.count('span="filled"'), 2)
        self.assertIn("span={DEVICE_FLOW_DESCRIPTION_SPAN}", summary)

    def test_scoped_small_screen_rule_only(self) -> None:
        self.assertIn(".tds-device-flow-descriptions .ant-descriptions-item-label", self.css)
        rule = block(self.css, "INTERACTION-006: device data-flow summary", "\n}\n")
        self.assertIn("@media (max-width: 575px)", rule)
        # The shared detail rule keeps labels on one line everywhere else.
        shared = block(self.css, ".tds-detail-descriptions .ant-descriptions-item-label {", "}")
        self.assertIn("white-space: nowrap", shared)
        # Never hide the overflow instead of fixing it.
        self.assertNotIn("overflow", rule.replace("overflow-wrap", ""))

    def test_modal_width_unchanged(self) -> None:
        modal = block(self.page, 't("customersDevices.dataFlowTitle"', "<Steps")
        self.assertIn("width={960}", modal)


class ConfigCascadingQueryContract(unittest.TestCase):
    """INTERACTION-007: responsive lock query, still no production entry."""

    component = read(SRC / "pages" / "Configuration" / "ConfigCascading.tsx")
    css = read(SRC / "pages" / "Configuration" / "configuration.css")
    shell_css = read(SRC / "index.css")

    def query(self) -> str:
        return block(self.component, '<Form form={lockQueryForm}', "</Form>")

    def test_query_form_has_no_fixed_widths_or_unwrapped_space(self) -> None:
        query = self.query()
        self.assertIn('className="tds-config-cascading__lock-query"', query)
        self.assertNotIn("width: 180", query)
        self.assertNotIn("style=", query)
        self.assertNotIn("<Space", query)
        self.assertEqual(query.count('className="tds-config-cascading__field-control"'), 2)
        self.assertIn('className="tds-config-cascading__lock-actions"', query)

    def test_actions_keep_their_handlers(self) -> None:
        query = self.query()
        for handler in ("handleLoadLocks", "openAddLockModal", "handleRemoveAllLocks"):
            self.assertIn(f"onClick={{{handler}}}", query)
        self.assertIn("Modal.confirm", block(self.component, "const handleRemoveAllLocks", "const openCreateGroupModal"))

    def test_admin_gate_is_kept(self) -> None:
        self.assertIn("if (!isAdmin) {", self.component)
        self.assertIn("t('apiErrors.forbidden')", self.component)

    def test_rules_are_scoped_and_shell_still_hides_overflow(self) -> None:
        rules = self.css[self.css.index("INTERACTION-007") :]
        selectors = [line.strip() for line in rules.splitlines() if line.strip().endswith("{") and not line.strip().startswith("@media")]
        self.assertTrue(selectors)
        for selector in selectors:
            for part in selector.rstrip("{").split(","):
                if part.strip():
                    self.assertTrue(part.strip().startswith(".tds-config-cascading"), part)
        self.assertIn("@media (max-width: 575px)", rules)
        content = block(self.shell_css, ".tds-content {", "}")
        self.assertIn("overflow-x: hidden", content)

    def test_component_imports_its_styles(self) -> None:
        self.assertIn("import './configuration.css';", self.component)

    def test_no_production_entry(self) -> None:
        app = read(SRC / "App.tsx")
        self.assertNotRegex(app, r"ConfigCascading|config-cascading|configCascading")
        # Configuration/index.tsx (the only tab container) is not imported.
        for path in SRC.rglob("*.ts*"):
            text = read(path)
            self.assertNotRegex(text, r"pages/Configuration['\"]|pages/Configuration/index|from ['\"]\./Configuration['\"]", str(path))
        vite = read(FRONTEND / "vite.config.ts")
        self.assertNotIn("harness", vite)


class OverviewTrendContract(unittest.TestCase):
    """INTERACTION-008: API trend DTO separated from the chart model."""

    types = read(SRC / "types" / "index.ts")
    service = read(SRC / "services" / "threat.ts")
    overview = read(SRC / "pages" / "Overview" / "index.tsx")
    mapper = read(SRC / "pages" / "Overview" / "trend.ts")
    fixtures = read(E2E / "helpers" / "apiFixtures.ts")

    DTO_FIELDS = ["timestamp", "count", "averageScore", "maxScore", "criticalCount", "highCount", "mediumCount"]

    def test_dto_matches_backend_trend_data_point(self) -> None:
        dto = block(self.types, "export interface ThreatTrendPoint {", "}")
        fields = re.findall(r"^\s*(\w+)\??:", dto, re.MULTILINE)
        self.assertEqual(fields, self.DTO_FIELDS)
        self.assertNotIn("?:", dto)  # backend zero-fills every field
        self.assertIn("trend: ThreatTrendPoint[];", block(self.types, "export interface OverviewSnapshot {", "}"))
        chart = block(self.types, "export interface ChartDataPoint {", "}")
        self.assertEqual(re.findall(r"^\s*(\w+)\??:", chart, re.MULTILINE), ["time", "value", "category"])

    def test_services_return_the_dto(self) -> None:
        self.assertNotRegex(self.service, r"ChartDataPoint")
        self.assertEqual(len(re.findall(r"Promise<ThreatTrendPoint\[\]>", self.service)), 2)
        self.assertEqual(len(re.findall(r"get<ThreatTrendPoint\[\]>", self.service)), 2)

    def test_overview_maps_in_one_typed_place(self) -> None:
        for legacy in (r"item:\s*any", r"item\.value", r"\.timestamp\b", r"count \?\? item"):
            self.assertIsNone(re.search(legacy, self.overview), legacy)
        self.assertIn("toTrendChartData(trend, hours, t('overview.threatCount'))", self.overview)
        self.assertIn("value: point.count", self.mapper)
        self.assertIn("time: formatLabel(point.timestamp)", self.mapper)
        self.assertNotRegex(self.mapper, r"\?\?\s*0|\|\|\s*0|\.value|any\b")

    def test_fixtures_use_only_dto_fields(self) -> None:
        self.assertNotIn("OverviewTrendPoint", self.fixtures)
        self.assertNotIn("ChartDataPoint", self.fixtures)
        trend = block(self.fixtures, "export const THREAT_TREND = [", "] satisfies ThreatTrendPoint[];")
        for row in re.findall(r"\{([^}]*)\}", trend):
            self.assertEqual(re.findall(r"(\w+):", row), self.DTO_FIELDS)
        self.assertIn("trend: THREAT_TREND,", self.fixtures)
        self.assertIn("} satisfies OverviewSnapshot;", self.fixtures)
        mock = read(E2E / "helpers" / "mockApi.ts")
        self.assertIn("body: () => F.THREAT_TREND }", mock)


class HarnessModuleBoundaryContract(unittest.TestCase):
    """INTERACTION-009: harness component and bootstrap live in separate modules."""

    harness_dir = E2E / "harness"
    entry = read(harness_dir / "config-cascading.tsx")
    component = read(harness_dir / "ConfigCascadingHarness.tsx")
    html = read(harness_dir / "config-cascading.html")
    eslintrc = read(FRONTEND / ".eslintrc.cjs")
    package = read(FRONTEND / "package.json")

    COMPONENT_DEF = re.compile(r"^[ \t]*(?:export\s+)?(?:default\s+)?(?:function\s+[A-Z]\w*|const\s+[A-Z]\w*\s*=\s*(?:\([^)]*\)|\w+)\s*=>)", re.MULTILINE)

    def test_entry_only_bootstraps(self) -> None:
        self.assertIsNone(self.COMPONENT_DEF.search(self.entry), "the entry must not define React components")
        self.assertIsNone(re.search(r"^[ \t]*export\b", self.entry, re.MULTILINE))
        self.assertIn('import { HarnessShell } from "./ConfigCascadingHarness";', self.entry)
        self.assertIn('ReactDOM.createRoot(document.getElementById("root")!).render(', self.entry)
        tree = re.sub(r"\s+", "", self.entry[self.entry.index("<React.StrictMode>") :])
        self.assertIn("<ThemeProvider><AuthProvider><HarnessShell/></AuthProvider></ThemeProvider>", tree)

    def test_component_module_only_exports_the_component(self) -> None:
        exports = re.findall(r"^[ \t]*export\b.*$", self.component, re.MULTILINE)
        self.assertEqual(exports, ["export function HarnessShell() {"])
        code = code_only(self.component)
        self.assertNotIn("createRoot", code)
        self.assertNotIn("react-dom", code)
        for needed in (
            'import "@/i18n";',
            'import "@/index.css";',
            'import "@/pages/Configuration/configuration.css";',
            'const CONFIGURATION_ACCENT = "#9d7aea";',
            'className="tds-shell"',
            'data-testid="config-cascading-harness"',
            'className="tds-content"',
            'className="tds-content__inner"',
            'className="tds-route-stage"',
            "<ConfigCascading />",
            "createAppTheme({ isDarkMode, accent: CONFIGURATION_ACCENT })",
        ):
            self.assertIn(needed, self.component)

    def test_entry_url_and_lint_policy_unchanged(self) -> None:
        self.assertIn('<script type="module" src="./config-cascading.tsx"></script>', self.html)
        self.assertIn("--max-warnings 0", self.package)
        self.assertIn("--report-unused-disable-directives", self.package)
        self.assertRegex(self.eslintrc, r"'react-refresh/only-export-components':\s*\[\s*'warn',\s*\{\s*allowConstantExport:\s*true\s*\}")
        self.assertIn("ignorePatterns: ['dist', '.eslintrc.cjs']", self.eslintrc)
        for path in E2E.rglob("*.ts*"):
            self.assertNotIn("eslint-disable", read(path), str(path))


class OverviewTrendSyncContract(unittest.TestCase):
    """INTERACTION-010: Overview E2E waits for snapshot responses, not requests."""

    spec = read(E2E / "overview-trend.spec.ts")
    config = read(FRONTEND / "playwright.config.ts")

    def test_waits_for_the_successful_snapshot_response(self) -> None:
        # INTERACTION-011: responses are recorded by a tracker created before
        # the action; the business budget starts when waitFor() is called.
        tracker = block(self.spec, "function trackSnapshots(", "\n}\n")
        self.assertIn('page.on("response", (response) => {', tracker)
        self.assertIn("url.pathname.endsWith(SNAPSHOT_PATH) && response.ok()", tracker)
        self.assertIn('received.add(url.searchParams.get("hours") ?? "")', tracker)
        self.assertIn("timeout: RESPONSE_TIMEOUT", tracker)
        self.assertIn('const SNAPSHOT_PATH = "/api/v1/assessment/overview-snapshot";', self.spec)

    def test_listener_is_created_before_navigation_and_clicks(self) -> None:
        self.assertEqual(self.spec.count('page.goto("/overview")'), 1)
        open_overview = block(self.spec, "async function openOverview(", "\n}\n")
        order = [
            open_overview.index("trackSnapshots(page)"),
            open_overview.index('page.goto("/overview")'),
            open_overview.index("waitForAppReady(page)"),
            open_overview.index('snapshots.waitFor("24")'),
            open_overview.index("waitForOverviewReady(page)"),
        ]
        self.assertEqual(order, sorted(order), "tracker -> goto -> app ready -> snapshot -> overview ready")
        range_loop = block(self.spec, "for (const [label, hours] of", "expect(errors)")
        self.assertLess(range_loop.index("await item.click();"), range_loop.index("await snapshots.waitFor(hours);"))
        self.assertEqual(len(re.findall(r"await openOverview\(page\);", self.spec)), 3)  # all three tests
        self.assertIn("const snapshots = await openOverview(page);", self.spec)
        self.assertIn('[["overview.range7d", "168"], ["overview.range30d", "720"]]', self.spec)

    def test_no_request_polling_serial_mode_retries_or_global_timeouts(self) -> None:
        for forbidden in ('page.on("request"', "hours.includes", "describe.serial", "configure({", "test.setTimeout", "test.slow(", "waitForTimeout"):
            self.assertNotIn(forbidden, self.spec, forbidden)
        self.assertIsNone(re.search(r"\bretries\s*:", self.spec))
        self.assertIn("workers: process.env.CI ? 1 : 4,", self.config)
        self.assertIn("retries: process.env.CI ? 2 : 0,", self.config)
        self.assertNotRegex(self.config, r"expect\s*:\s*\{")
        self.assertNotRegex(self.config, r"^\s*timeout\s*:", "no global test timeout")

    def test_rendering_assertions_keep_canvas_and_empty_state_checks(self) -> None:
        chart = block(self.spec, "async function expectChart(", "\n}\n")
        self.assertIn('locator("canvas")).toHaveCount(1, { timeout: RENDER_TIMEOUT })', chart)
        self.assertIn("expect(emptyTrend(page)).toHaveCount(0)", chart)
        self.assertIn("await expect(chart(page)).toHaveCount(0);", self.spec)
        self.assertIn('toHaveClass(/ant-segmented-item-selected/)', self.spec)
        self.assertIn("await expect(refreshing(page)).toHaveCount(0, { timeout: RENDER_TIMEOUT });", self.spec)
        self.assertEqual(self.spec.count("expect(errors).toEqual([]);"), 3)
        self.assertIn("expect(getApiMockReport(page).unexpectedRequests).toEqual([]);", self.spec)


if __name__ == "__main__":
    unittest.main()
