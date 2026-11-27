#!/usr/bin/env python3
"""INTERACTION-001: static contract for the global table layout rules.

Guards the global table contract (centred, single-line cells, unified density,
in-table horizontal scrolling) against regressions. Unlike
``test_release_contract.py`` this test only reads frontend sources, so it runs
without building the release archive:

    python -m unittest tests.test_table_layout_contract
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "frontend" / "src"
STYLES = FRONTEND / "index.css"
APP = FRONTEND / "App.tsx"
# INTERACTION-004: antd theme extracted from App.tsx so harnesses share the tokens.
APP_THEME = FRONTEND / "theme" / "appTheme.ts"

TABLE_TAG = re.compile(r"<(?:Pro)?Table(?:<[^>]*>)?[\s>]")
# scroll.x is a number, or a variable computed by useResponsiveScrollX (tables
# that hide columns with `responsive`, INTERACTION-004 finding).
NUMERIC_SCROLL_X = re.compile(r"scroll=\{\{\s*x:\s*(?:\d+|\w+ScrollX)\s*\}\}")
RESPONSIVE_SCROLL_HOOK = re.compile(r"const (\w+ScrollX) = useResponsiveScrollX\((\w+)\);")
SURFACES = ROOT / "frontend" / "e2e" / "helpers" / "tableSurfaces.ts"
LEFT_RIGHT_ALIGN = re.compile(r"align:\s*['\"](?:left|right)['\"]")
ACTION_TITLE = re.compile(r"title:\s*t\(\s*['\"]common\.actions['\"]\s*\)")

# INTERACTION-002: action column width budget for 40px TableActionButtons
# (mirrors components/tableActionWidth.ts): >= 40n + 4(n-1) + 24, min 72.
ACTION_WIDTH_BUDGET = {1: 72, 2: 108, 3: 152, 4: 200}
WIDTH_VALUE = re.compile(r"width:\s*(?:(\d+)|TABLE_ACTION_COLUMN_WIDTH\[(\d)\])")

# INTERACTION-005: action columns that legitimately are NOT 40px icon groups.
# Each entry is reviewed: (file, width) -> reason.
ACTION_COLUMN_EXCEPTIONS = {
    ("frontend/src/pages/ThreatList/index.tsx", 120): "single text destructive Delete button (stops row-click propagation)",
    ("frontend/src/pages/ThreatIntel/index.tsx", 120): "non-interactive TableCellText note or a single poll button",
}

# INTERACTION-005: action columns rendering two or more interactive controls
# must use <TableActions>. (file, number of such columns in the file)
MULTI_ACTION_COLUMNS = {
    "frontend/src/pages/AlertCenter/index.tsx": 1,
    "frontend/src/pages/Configuration/AIConfig.tsx": 2,
    "frontend/src/pages/Configuration/ConfigCascading.tsx": 2,
    "frontend/src/pages/Configuration/PluginConfig.tsx": 1,
    "frontend/src/pages/CustomersAndDevices/index.tsx": 2,
    "frontend/src/pages/TenantMgmt/index.tsx": 1,
    "frontend/src/pages/ThreatIntel/index.tsx": 1,
    "frontend/src/pages/UserMgmt/index.tsx": 1,
}


def table_sources() -> list[Path]:
    return sorted(
        path
        for path in FRONTEND.rglob("*.tsx")
        if "__tests__" not in path.parts and TABLE_TAG.search(path.read_text(encoding="utf-8"))
    )


def css_block(styles: str, marker: str) -> str:
    start = styles.index(marker)
    return styles[start : styles.index("}", start) + 1]


def _bracket_block(text: str, start: int) -> str:
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char in "[{(":
            depth += 1
        elif char in "]})":
            depth -= 1
            if depth == 0:
                return text[start : index + 1]
    return text[start:]


def _column_width_sum(array_source: str) -> int:
    """Sum of ``width`` values declared directly on the column objects."""
    depth = 0
    total = 0
    for match in re.finditer(r"[\[\]{}()]|" + WIDTH_VALUE.pattern, array_source):
        token = match.group(0)
        if token in "[{(":
            depth += 1
        elif token in "]})":
            depth -= 1
        elif depth == 2:
            total += int(match.group(1)) if match.group(1) else ACTION_WIDTH_BUDGET[int(match.group(2))]
    return total


def table_scroll_budgets(text: str) -> list[tuple[int, int, int | str]]:
    """(line, column width sum, scroll.x) for every Table/ProTable in a file.

    scroll.x is an int, or the name of the columns array passed to
    useResponsiveScrollX (the budget is then computed at runtime)."""
    definitions = [
        (match.start(), match.group(1), _column_width_sum(_bracket_block(text, match.end() - 1)))
        for match in re.finditer(
            r"const (\w+)\s*(?::[^=]+)?=\s*(?:useMemo\(\s*\(\)\s*=>\s*)?\[", text
        )
    ]
    budgets = []
    for match in TABLE_TAG.finditer(text):
        tag = text[match.start() : match.start() + 4000]
        named = re.search(r"columns=\{(\w+)\}", tag[:1500])
        scroll = re.search(r"scroll=\{\{\s*x:\s*(\d+|\w+ScrollX)", tag)
        if named:
            candidates = [d for d in definitions if d[1] == named.group(1) and d[0] < match.start()]
            total = candidates[-1][2] if candidates else 0
        else:
            inline = re.search(r"columns=\{\[", tag)
            total = _column_width_sum(_bracket_block(tag, inline.end() - 1)) if inline else 0
        line = text.count("\n", 0, match.start()) + 1
        value: int | str = 0
        if scroll and scroll.group(1).isdigit():
            value = int(scroll.group(1))
        elif scroll:
            hooks = dict(RESPONSIVE_SCROLL_HOOK.findall(text))
            value = hooks.get(scroll.group(1), "")
        budgets.append((line, total, value))
    return budgets


class TableLayoutContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.styles = STYLES.read_text(encoding="utf-8")
        cls.sources = {path: path.read_text(encoding="utf-8") for path in table_sources()}

    def test_inventory_covers_every_table_instance(self) -> None:
        count = sum(len(TABLE_TAG.findall(text)) for text in self.sources.values())
        self.assertEqual(count, 24, "update the INTERACTION-001 inventory when tables change")
        self.assertEqual(len(self.sources), 12)

    def test_global_rule_centres_and_keeps_cells_single_line(self) -> None:
        block = css_block(
            self.styles,
            ":is(.tds-content, .ant-modal, .ant-drawer) .ant-table-thead > tr > th,\n",
        )
        self.assertIn(".ant-table-selection-column", block)
        self.assertIn(".ant-table-row-expand-icon-cell", block)
        self.assertIn("text-align: center !important", block)
        self.assertIn("vertical-align: middle !important", block)
        self.assertIn("white-space: nowrap", block)

    def test_rules_reach_modal_and_drawer_tables(self) -> None:
        # Modals and drawers are portals outside .tds-content.
        self.assertIn(":is(.tds-content, .ant-modal, .ant-drawer) .ant-table-column-sorters", self.styles)
        self.assertIn(":is(.tds-content, .ant-modal, .ant-drawer) .ant-table-filter-column", self.styles)

    def test_sorter_and_filter_icons_stay_with_centred_title(self) -> None:
        self.assertRegex(
            self.styles,
            r"\.ant-table-column-sorters,\n:is\([^)]*\) \.ant-table-filter-column \{\n"
            r"  justify-content: center;",
        )
        self.assertIn("> .ant-table-column-title {\n  flex: 0 1 auto;", self.styles)

    def test_density_baseline_is_global(self) -> None:
        self.assertIn("height: 40px;", css_block(self.styles, ":is(.tds-content, .ant-modal, .ant-drawer) .ant-table-thead > tr > th {"))
        self.assertRegex(self.styles, r"> td \{\n  height: 48px;")
        self.assertRegex(self.styles, r"\.ant-table-expanded-row\n  \.ant-table-tbody[\s\S]*?> td \{\n  height: 44px;")
        app = APP_THEME.read_text(encoding="utf-8")
        self.assertIn("createAppTheme({ isDarkMode, accent: activeWorkspace.accent })", APP.read_text(encoding="utf-8"))
        for token in (
            "cellPaddingBlock:",
            "cellPaddingBlockMD:",
            "cellPaddingBlockSM:",
            "cellPaddingInlineMD:",
            "cellPaddingInlineSM:",
        ):
            self.assertIn(token, app)
        for page_css in (
            "pages/Overview/overview.css",
            "pages/operations.css",
            "pages/Configuration/configuration.css",
            "pages/investigate.css",
            "pages/governance.css",
        ):
            text = (FRONTEND / page_css).read_text(encoding="utf-8")
            for match in re.finditer(r"\.ant-table-(?:thead|tbody)[^{]*\{([^}]*)\}", text):
                body = match.group(1)
                for prop in ("height", "padding-block", "font-size", "font-weight", "min-height"):
                    self.assertNotRegex(body, rf"(^|\s){prop}:", f"{page_css} overrides table {prop}")

    def test_fixed_layout_overflow_is_clipped_inside_its_cell(self) -> None:
        self.assertIn(
            "td:not(.ant-table-cell-fix-left):not(.ant-table-cell-fix-right) {\n  overflow: hidden;\n  text-overflow: ellipsis;",
            self.styles,
        )

    def test_shared_cell_helpers_exist(self) -> None:
        for marker in (".tds-table-cell-text {", ".tds-table-actions {", ".tds-table-tags {"):
            block = css_block(self.styles, marker)
            self.assertIn("white-space: nowrap", block, marker)
        self.assertIn("flex-wrap: nowrap", css_block(self.styles, ".tds-table-actions {"))
        status = css_block(self.styles, ".tds-status-tag.ant-tag {")
        self.assertIn("display: inline-flex", status)
        self.assertIn("justify-content: center", status)
        self.assertTrue((FRONTEND / "components/TableCellText.tsx").is_file())
        self.assertTrue((FRONTEND / "components/TableActions.tsx").is_file())

    def test_page_specific_threat_list_centering_was_folded_into_global_rule(self) -> None:
        self.assertNotIn(".tds-threat-list-table .ant-table-column-sorters", self.styles)

    def test_no_column_is_left_or_right_aligned(self) -> None:
        for path, text in self.sources.items():
            self.assertIsNone(LEFT_RIGHT_ALIGN.search(text), path.relative_to(ROOT).as_posix())

    def test_every_table_has_a_stable_numeric_scroll_width(self) -> None:
        for path, text in self.sources.items():
            relative = path.relative_to(ROOT).as_posix()
            self.assertNotIn("x: 'max-content'", text, relative)
            self.assertNotIn('x: "max-content"', text, relative)
            self.assertEqual(
                len(TABLE_TAG.findall(text)),
                len(NUMERIC_SCROLL_X.findall(text)),
                f"{relative}: every Table/ProTable needs scroll={{{{ x: <width> }}}}",
            )

    def test_scroll_width_covers_declared_column_widths(self) -> None:
        for path, text in self.sources.items():
            relative = path.relative_to(ROOT).as_posix()
            for line, total, scroll_x in table_scroll_budgets(text):
                self.assertGreater(total, 0, f"{relative}:{line} columns need widths")
                if isinstance(scroll_x, str):
                    # Runtime budget: must be computed from this table's own columns.
                    tag_start = sum(len(row) + 1 for row in text.split("\n")[: line - 1])
                    columns = re.search(r"columns=\{(\w+)\}", text[tag_start : tag_start + 1500])
                    self.assertTrue(scroll_x, f"{relative}:{line} scroll.x variable must come from useResponsiveScrollX")
                    self.assertIsNotNone(columns, f"{relative}:{line} responsive table needs a named columns array")
                    self.assertEqual(columns.group(1), scroll_x, f"{relative}:{line} scroll.x computed from other columns")
                else:
                    self.assertLessEqual(total, scroll_x, f"{relative}:{line} scroll.x < column widths")

    def test_tables_with_responsive_columns_use_responsive_scroll_budget(self) -> None:
        """INTERACTION-004 finding: a constant scroll.x stretches the columns left at narrow widths."""
        for path, text in self.sources.items():
            relative = path.relative_to(ROOT).as_posix()
            hooked = {columns for _, columns in RESPONSIVE_SCROLL_HOOK.findall(text)}
            for match in re.finditer(r"const (\w+)\s*(?::[^=]+)?=\s*(?:useMemo\(\s*\(\)\s*=>\s*)?\[", text):
                block = _bracket_block(text, match.end() - 1)
                if re.search(r"\bresponsive:\s*\[", block) and re.search(r"\bwidth:", block):
                    self.assertIn(match.group(1), hooked, f"{relative}: {match.group(1)} hides columns responsively; use useResponsiveScrollX")
            self.assertNotRegex(text, r"columns=\{\[[^\]]*responsive:", f"{relative}: hoist responsive inline columns")

    def test_table_surfaces_cover_all_24_instances_once(self) -> None:
        """INTERACTION-004: every table instance is reachable by exactly one E2E surface."""
        surfaces = SURFACES.read_text(encoding="utf-8")
        covered = re.findall(r'\{\s*file:\s*"([^"]+)",\s*index:\s*(\d+)\s*\}', surfaces)
        self.assertEqual(len(covered), len(set(covered)), "an instance is listed by two surfaces")
        expected = {
            (path.relative_to(FRONTEND).as_posix(), str(index))
            for path, text in self.sources.items()
            for index in range(1, len(TABLE_TAG.findall(text)) + 1)
        }
        self.assertEqual(set(covered), expected)
        self.assertNotIn("test.skip(", (ROOT / "frontend/e2e/table-layout.spec.ts").read_text(encoding="utf-8"))

    def action_columns(self):
        """Yield (relative path, column source) for every `common.actions` column."""
        for path, text in self.sources.items():
            relative = path.relative_to(ROOT).as_posix()
            for match in ACTION_TITLE.finditer(text):
                following = text[match.end() :]
                # The column ends at the next column title or at the end of
                # the columns array, whichever comes first.
                ends = [m.start() for m in (re.search(r"\n\s*title:", following), re.search(r"\n\s*\][;,)]*\n", following)) if m]
                yield relative, following[: min(ends) if ends else 2500]

    def test_action_columns_never_wrap(self) -> None:
        for relative, column in self.action_columns():
            self.assertNotIn("<Space", column, f"{relative}: action columns must not use <Space>/<Space wrap>")
            self.assertRegex(column, WIDTH_VALUE, f"{relative}: action column needs a fixed width")

    def test_action_column_width_stays_within_budget(self) -> None:
        """INTERACTION-002: no oversized action columns (e.g. the old 220/560px)."""
        for relative, column in self.action_columns():
            match = WIDTH_VALUE.search(column)
            width = int(match.group(1)) if match.group(1) else ACTION_WIDTH_BUDGET[int(match.group(2))]
            if (relative, width) in ACTION_COLUMN_EXCEPTIONS:
                continue
            self.assertIn(
                width,
                ACTION_WIDTH_BUDGET.values(),
                f"{relative}: action column width {width} is not a 1-4 button budget "
                f"{sorted(ACTION_WIDTH_BUDGET.values())}; register a reviewed exception if it must differ",
            )

    def test_config_cascading_uses_compact_icon_actions(self) -> None:
        page = (FRONTEND / "pages/Configuration/ConfigCascading.tsx").read_text(encoding="utf-8")
        for scroll_x in (728, 1156, 492):
            self.assertIn(f"scroll={{{{ x: {scroll_x} }}}}", page)
        for budget in ("TABLE_ACTION_COLUMN_WIDTH[1]", "TABLE_ACTION_COLUMN_WIDTH[2]", "TABLE_ACTION_COLUMN_WIDTH[4]"):
            self.assertIn(f"width: {budget}", page)
        columns = [column for relative, column in self.action_columns() if relative.endswith("ConfigCascading.tsx")]
        self.assertEqual(len(columns), 3)
        for column in columns:
            self.assertNotRegex(column, r"<Button\b", "use TableActionButton icon actions")
            self.assertIn("<TableActionButton", column)

    def test_action_width_helper_matches_contract_budget(self) -> None:
        helper = (FRONTEND / "components/tableActionWidth.ts").read_text(encoding="utf-8")
        self.assertIn("TABLE_ACTION_BUTTON_SIZE = 40", helper)
        self.assertIn("TABLE_ACTION_GAP = 4", helper)
        self.assertIn("TABLE_CELL_INLINE_PADDING = 24", helper)
        for buttons, width in ACTION_WIDTH_BUDGET.items():
            self.assertIn(f"{buttons}: {width},", helper)
            # Never narrower than the buttons actually need.
            self.assertGreaterEqual(width, 40 * buttons + 4 * (buttons - 1) + 24)

    def test_multi_action_columns_use_shared_group(self) -> None:
        """INTERACTION-005: two or more actions -> <TableActions>; single actions may render directly."""
        counts: dict[str, int] = {}
        for relative, column in self.action_columns():
            if "<TableActions" in column:
                counts[relative] = counts.get(relative, 0) + 1
        self.assertEqual(counts, MULTI_ACTION_COLUMNS)

    def test_legacy_device_action_container_is_gone(self) -> None:
        for path in FRONTEND.rglob("*"):
            if path.suffix in {".tsx", ".ts", ".css"} and "__tests__" not in path.parts:
                self.assertNotIn("tds-device-action", path.read_text(encoding="utf-8"), path.as_posix())


if __name__ == "__main__":
    unittest.main()
