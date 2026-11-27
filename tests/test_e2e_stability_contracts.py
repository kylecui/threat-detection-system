#!/usr/bin/env python3
"""INTERACTION-011/012/013/014: static contracts for E2E stability fixes.

Reads sources only (no build, no browser):

    python -m unittest tests.test_e2e_stability_contracts
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


def block(text: str, start: str, end: str) -> str:
    begin = text.index(start)
    return text[begin : text.index(end, begin)]


PRO_TABLE_PAGES = {
    "pages/AlertCenter/index.tsx": 1,
    "pages/ThreatIntel/index.tsx": 1,
    "pages/CustomersAndDevices/index.tsx": 2,
}


class ReadinessContract(unittest.TestCase):
    """INTERACTION-011: one app/page readiness contract for all specs."""

    app = read(SRC / "App.tsx")
    login = read(SRC / "pages" / "Login" / "index.tsx")
    ready = read(E2E / "helpers" / "ready.ts")
    config = read(FRONTEND / "playwright.config.ts")
    setup = read(E2E / "global-setup.ts")

    def test_app_shell_exposes_real_state(self) -> None:
        self.assertIn('import { ScopeProvider, useScope } from "./contexts/ScopeContext";', self.app)
        self.assertIn("const { initialized: scopeInitialized } = useScope();", self.app)
        shell = block(self.app, '<Layout\n        className="tds-shell"', ">")
        self.assertIn('data-app-ready={scopeInitialized ? "true" : "false"}', shell)
        self.assertIn("data-route-path={location.pathname}", shell)

    def test_login_exposes_bootstrap_state(self) -> None:
        root = block(self.login, '<main\n      className="tds-login"', ">")
        self.assertIn('data-app-ready={checkingBootstrap ? "false" : "true"}', root)
        self.assertIn('data-route-path="/login"', root)

    def test_helpers_exist_with_bounded_timeouts_and_layered_errors(self) -> None:
        for name in ("waitForAppReady", "gotoReady", "waitForOverviewReady", "waitForTableReady", "waitForStable", "describeAppState"):
            self.assertRegex(self.ready, rf"export (async )?function {name}\b")
        self.assertIn("export const APP_READY_TIMEOUT = 20_000;", self.ready)
        self.assertIn("export const STABLE_MS = 1_000;", self.ready)
        for layer in ("global Suspense fallback", "Scope / login bootstrap not initialised", "route not committed"):
            self.assertIn(layer, self.ready)
        self.assertIn('mode?: "data" | "empty";', self.ready)
        self.assertNotIn("waitForTimeout", self.ready)

    def test_warm_up_runs_before_tests_and_covers_every_route(self) -> None:
        self.assertIn("globalSetup: './e2e/global-setup.ts',", self.config)
        self.assertNotRegex(self.config, r"^\s*timeout\s*:", "no global test timeout")
        routes = set(re.findall(r'^  "(/[^"]*)",$', block(self.setup, "export const WARMUP_ROUTES = [", "];"), re.MULTILINE))
        navigation = read(E2E / "navigation.spec.ts")
        main_routes = set(re.findall(r'^    "(/[^"]*)",$', block(navigation, "for (const route of [", "]) {"), re.MULTILINE))
        self.assertEqual(len(main_routes), 14)
        self.assertLessEqual(main_routes, routes)
        self.assertLessEqual({"/login", "/e2e/harness/config-cascading.html"}, routes)
        self.assertIn("E2E_SKIP_WARMUP", self.setup)
        self.assertIn("await browser.newContext({ baseURL })", self.setup)  # isolated context

    def test_specs_navigate_through_the_contract(self) -> None:
        allowed = {"overview-trend.spec.ts", "readiness.spec.ts"}
        for spec in sorted(E2E.glob("*.spec.ts")):
            text = read(spec)
            if spec.name not in allowed:
                self.assertNotIn("page.goto(", text, f"{spec.name}: use gotoReady()")
            for match in re.finditer(r"await page\.reload\(\);\n(.*)", text):
                self.assertIn("waitForAppReady(page)", match.group(1), f"{spec.name}: reload must wait for app ready")
        trend = read(E2E / "overview-trend.spec.ts")
        self.assertIn('await page.goto("/overview");\n  await waitForAppReady(page);', trend)

    def test_fixed_specs_use_no_sleeps(self) -> None:
        for name in ("overview.spec.ts", "rbac.spec.ts", "i18n.spec.ts", "table-interactions.spec.ts", "overview-trend.spec.ts",
                     "readiness.spec.ts", "query-filter.spec.ts", "protable-toolbar.spec.ts", "responsive-layout.spec.ts"):
            self.assertNotIn("waitForTimeout", read(E2E / name), name)

    def test_root_mount_helper_does_not_require_initialisation(self) -> None:
        """INTERACTION-015: shell mounted != app ready; same cold-start budget."""
        predicate = block(self.ready, "export function isAppRootMounted(", "\n}\n")
        self.assertIn("!state.globalLoading", predicate)
        self.assertIn("root.route === state.path", predicate)
        self.assertNotIn('"true"', predicate, "must accept data-app-ready=false")
        helper = block(self.ready, "export async function waitForAppRootMounted(", "\n}\n")
        self.assertIn("options.timeout ?? APP_READY_TIMEOUT", helper)
        self.assertIn("isAppRootMounted", helper)
        self.assertIn("describeAppState(state)", helper)
        self.assertIn("stableMs: 0", helper)
        self.assertNotIn('"true"', helper)

    def test_negative_scope_test_waits_for_the_mount_first(self) -> None:
        """INTERACTION-015: goto -> root-mount wait -> attributes -> diagnosis; cleanup in finally."""
        spec = read(E2E / "readiness.spec.ts")
        case = block(spec, 'test("a Scope that never initialises', "\n  });\n")
        order = [
            'page.route("**/api/v1/tenants/search**"',
            "try {",
            'await page.goto("/overview");',
            "await waitForAppRootMounted(page);",
            'toHaveAttribute("data-route-path", "/overview", { timeout: ATTRIBUTE_TIMEOUT })',
            'toHaveAttribute("data-app-ready", "false", { timeout: ATTRIBUTE_TIMEOUT })',
            "await waitForAppReady(page, { timeout: 4_000 })",
            "} finally {",
            "route.abort()",
        ]
        positions = [case.index(step) for step in order]
        self.assertEqual(positions, sorted(positions), "steps out of order")
        self.assertNotIn("route.abort()", case[: case.index("} finally {")], "abort only in finally")
        # Every locator assertion in the case has an explicit timeout (no default 5s).
        for match in re.finditer(r"\.(toHaveAttribute|toBeVisible|toBeAttached|toHaveCount)\(([^;]*)\);", case):
            self.assertIn("timeout", match.group(2), match.group(0))
        self.assertIn("const ATTRIBUTE_TIMEOUT = 1_000;", spec)
        # Every raw navigation in readiness.spec is followed by a bounded readiness/mount wait.
        for match in re.finditer(r"await page\.goto\([^)]*\);\n(?:\s*//.*\n)*(.*)", spec):
            self.assertRegex(match.group(1), r"await (waitForAppRootMounted|waitForAppReady)\(page", match.group(0))
        for forbidden in ("waitForTimeout", "test.slow(", "retries"):
            self.assertNotIn(forbidden, spec)
        self.assertNotRegex(self.config, r"expect\s*:\s*\{", "no global expect timeout")
        self.assertNotRegex(self.config, r"retries\s*:\s*[1-9]")

    def test_runner_records_resources(self) -> None:
        run = read(ROOT / "scripts" / "testing" / "run_frontend_e2e_ubuntu.sh")
        self.assertIn("container_memory_peak_mib=", run)
        self.assertIn("memory-samples.txt", run)
        self.assertIn("==> container resources:", run)

    def test_runner_default_memory_gate_is_four_gib(self) -> None:
        """INTERACTION-015: keep enough headroom above the measured 2996 MiB peak."""
        run = read(ROOT / "scripts" / "testing" / "run_frontend_e2e_ubuntu.sh")
        self.assertIn(
            "TDS_E2E_MIN_MEM_GB       minimum available memory (default 4; 1 GiB is /dev/shm)",
            run,
        )
        self.assertIn('MIN_MEM_GB="${TDS_E2E_MIN_MEM_GB:-4}"', run)
        self.assertNotIn('MIN_MEM_GB="${TDS_E2E_MIN_MEM_GB:-3}"', run)


class QueryFilterContract(unittest.TestCase):
    """INTERACTION-013: query forms use an app-owned hook, not Pro internals."""

    def test_stale_internal_class_is_gone(self) -> None:
        for path in list(SRC.rglob("*")) + list(E2E.rglob("*")):
            if path.is_file() and path.suffix in {".ts", ".tsx", ".css"}:
                self.assertNotIn("ant-pro-table-search", read(path), str(path))

    def test_every_pro_table_uses_the_shared_search_config(self) -> None:
        config = read(SRC / "components" / "proTableConfig.ts")
        self.assertIn('export const TABLE_QUERY_CLASS = "tds-table-query";', config)
        self.assertIn('labelWidth: "auto",', config)
        for relative, count in PRO_TABLE_PAGES.items():
            page = read(SRC / relative)
            self.assertEqual(page.count("search={PRO_TABLE_SEARCH}"), count, relative)
            self.assertNotIn("search={{", page, relative)

    def test_global_css_targets_the_hook(self) -> None:
        css = read(SRC / "index.css")
        # :where(div): box on the wrapper only (search.className also lands on the
        # inner <form>), same specificity as the previous selector.
        self.assertIn(".tds-content .tds-table-query:where(div) {", css)
        self.assertNotRegex(css, r"\.tds-content \.tds-table-query \{")
        self.assertIn(".tds-content .tds-table-query .ant-form-item-label > label {", css)

    def test_specs_use_the_shared_helper(self) -> None:
        helper = read(E2E / "helpers" / "queryFilter.ts")
        self.assertIn('export const TABLE_QUERY_SELECTOR = ".tds-content .tds-table-query";', helper)
        self.assertNotIn(".ant-pro-", helper)
        self.assertIn("tableQueryField(page, /Customer ID/)", read(E2E / "navigation.spec.ts"))
        matrix = read(E2E / "query-filter.spec.ts")
        self.assertIn('const SURFACES = ["customers", "customers-device-list", "alerts-detail", "intel-indicators"];', matrix)
        self.assertIn("const WIDTHS = [1440, 390];", matrix)
        self.assertIn('const LANGUAGES: E2ELanguage[] = ["en-US", "zh-CN"];', matrix)
        self.assertIn('checkGeometry(await queryGeometry(query), width, "expanded");', matrix)


class WorkspaceMotionContract(unittest.TestCase):
    """INTERACTION-014: one independent test per workspace motion scenario."""

    spec = read(E2E / "navigation.spec.ts")

    def test_four_independent_tests(self) -> None:
        scenarios = block(self.spec, "const WORKSPACE_SUBMENU_SCENARIOS = [", "] as const;")
        self.assertEqual(len(re.findall(r"\{ workspace: ", scenarios)), 4)
        self.assertNotIn("all workspace submenu parents share the same motion behavior", self.spec)
        loop = block(self.spec, "for (const scenario of WORKSPACE_SUBMENU_SCENARIOS) {", "\n  }\n")
        self.assertIn('await gotoReady(page, "/overview");', loop)
        self.assertIn("await switchWorkspaceAndWait(page, scenario.workspace);", loop)
        self.assertIn("await assertWorkspaceSubmenuMotion(page, scenario);", loop)

    def test_switch_completion_and_motion_checks(self) -> None:
        switch = block(self.spec, "async function switchWorkspaceAndWait(", "\n}\n")
        self.assertIn("await expect(switcher).toContainText(workspace);", switch)
        self.assertIn("await waitForAppReady(page);", switch)
        motion = block(self.spec, "async function assertWorkspaceSubmenuMotion(", "\n}\n")
        for needed in ("toHaveCount(1)", 'toHaveAttribute("aria-expanded", "true")', "tds-submenu-motion-leave-(?:start|active)",
                       'toHaveAttribute("aria-expanded", "false")', "ant-menu-hidden/, { timeout: 600 }",
                       "tds-submenu-motion-enter-active", "timeout: 700"):
            self.assertIn(needed, motion)
        self.assertNotIn("waitForTimeout", motion + switch)

    def test_motion_recorder_samples_atomically_and_cleans_up(self) -> None:
        """INTERACTION-016: one frame = class + geometry + transition, recorded before the click."""
        recorder = block(self.spec, "async function recordSubmenuMotionFrames(", "\n}\n")
        for needed in ("new MutationObserver", 'attributeFilter: ["class", "style"]', "requestAnimationFrame(tick)",
                       "className: host.className", "rectHeight: host.getBoundingClientRect().height",
                       "computedHeight: style.height", "inlineHeight: host.style.height", "scrollHeight: host.scrollHeight",
                       "transitionDuration: style.transitionDuration", "observer.disconnect();", "cancelAnimationFrame(rafId);",
                       "delete host.__tdsMotionRecorder;", "frames.length >= limit"):
            self.assertIn(needed, recorder)
        self.assertNotIn("recordClassHistory", self.spec, "single recorder with cleanup")
        self.assertNotRegex(self.spec, r"\bany\b(?=[\s;,)>\]])", "typed recorder")

    def test_measured_height_test_uses_same_frame_samples(self) -> None:
        case = block(self.spec, 'test("submenu uses measured height and synchronized enter and leave motion"', "\n  });\n")
        self.assertNotIn("parseFloat(style.height)", case)
        self.assertNotIn("parseFloat(enterFrame", case)
        self.assertIn("parseFloat(style.height)", block(self.spec, 'test("sidebar runtime status', "\n  });\n"),
                      "fixed-height sidebar may parse its own CSS height; only the motion test may not")
        self.assertNotRegex(case, r"toHaveClass\(/tds-submenu-motion-(?:leave|enter)-active/\);\n\s*const \w+ = await submenu\.evaluate",
                            "no class wait followed by a separate style read")
        # Recorder installed before each click, stopped in finally.
        for phase in case.split("recorder = await recordSubmenuMotionFrames(submenu);")[1:]:
            self.assertLess(phase.index("await parent.click();"), phase.index("} finally {"))
            self.assertIn("await recorder.stop();", phase[phase.index("} finally {"):])
        self.assertEqual(case.count("recordSubmenuMotionFrames(submenu)"), 2)
        for needed in ("validLeaveFrame(frame, naturalHeight)", "validEnterFrame(frame, naturalHeight)",
                       "Number.isFinite(frame.rectHeight)", "describeFrames(frames)",
                       'toHaveClass(/ant-menu-hidden/, { timeout: 600 })', "timeout: 700",
                       'toHaveAttribute("aria-expanded", "false")', 'toHaveAttribute("aria-expanded", "true")',
                       'expect(await submenu.getAttribute("style")).not.toContain("height");'):
            self.assertIn(needed, case)
        leave = block(self.spec, "function validLeaveFrame(", "\n}\n")
        for needed in ("LEAVE_ACTIVE.test(frame.className)", "Number.isFinite(frame.rectHeight)", "frame.rectHeight >= 0",
                       "frame.rectHeight <= naturalHeight", 'transitionDuration.includes("0.21s")', 'frame.maxHeight === "none"',
                       "Number.isFinite(opacity)", "ty <= 0"):
            self.assertIn(needed, leave)
        enter = block(self.spec, "function validEnterFrame(", "\n}\n")
        for needed in ("ENTER_ACTIVE.test(frame.className)", "Number(target[1]) === frame.scrollHeight",
                       "Number(target[1]) === naturalHeight", 'transitionDuration.includes("0.26s")'):
            self.assertIn(needed, enter)
        self.assertNotIn("waitForTimeout", case)

    def test_related_motion_tests_still_run(self) -> None:
        for title in ("submenu uses measured height and synchronized enter and leave motion", "reduced motion removes spatial route animation"):
            self.assertIn(title, self.spec)


class StrictModeConsoleContract(unittest.TestCase):
    """INTERACTION-012: warning removed at the source; StrictMode and the console gate stay."""

    def test_strict_mode_is_kept(self) -> None:
        self.assertIn("<React.StrictMode>", read(SRC / "main.tsx"))

    def test_every_pro_table_uses_the_app_density_control(self) -> None:
        for relative, count in PRO_TABLE_PAGES.items():
            page = read(SRC / relative)
            self.assertEqual(len(re.findall(r"= useProTableToolbar\(\);", page)), count, relative)
            self.assertEqual(len(re.findall(r"\{\.\.\.\w+TableToolbar\}", page)), count, relative)
        hook = read(SRC / "components" / "useProTableToolbar.tsx")
        self.assertIn("options: { density: false, reload: true, setting: true },", hook)
        self.assertIn('return [reload, <TableDensityControl key="density" value={size} onChange={setSize} />, ...rest];', hook)

    def test_density_control_wraps_a_native_button(self) -> None:
        control = read(SRC / "components" / "TableDensityControl.tsx")
        self.assertRegex(control, r'<Tooltip title=\{label\}>\s*<button type="button" className="tds-table-density-control" aria-label=\{label\}>')
        for key in ("tableToolBar.density", "tableToolBar.densityLarger", "tableToolBar.densityMiddle", "tableToolBar.densitySmall"):
            self.assertIn(key, control)

    def test_console_gate_is_strict(self) -> None:
        gate = read(E2E / "helpers" / "consoleGate.ts")
        patterns = re.findall(r"pattern: (/.+/),", gate)
        self.assertEqual(patterns, [r"/^Warning: \[antd: [^\]]+\]/"])
        self.assertNotRegex(block(gate, "export const CONSOLE_ALLOWLIST", "];"), r"findDOMNode|StrictMode\.|DomWrapper")
        layout = read(E2E / "table-layout.spec.ts")
        self.assertIn('import { collectErrors } from "./helpers/consoleGate";', layout)
        self.assertIn("expect(errors.consoleErrors).toEqual([]);", layout)
        self.assertIn("expect(errors.pageErrors).toEqual([]);", layout)
        for path in E2E.rglob("*.ts"):
            self.assertNotRegex(read(path), r"console\.error\s*=", str(path))


if __name__ == "__main__":
    unittest.main()
