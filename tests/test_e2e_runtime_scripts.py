#!/usr/bin/env python3
"""INTERACTION-010: behaviour of the Ubuntu Playwright runtime scripts.

Runs scripts/testing/*.sh against stand-in `docker`, `uname`, `npm` and `npx`
commands, so it needs bash, python3 and node but no Docker daemon, network or
browser:

    python -m unittest tests.test_e2e_runtime_scripts
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RUN = "scripts/testing/run_frontend_e2e_ubuntu.sh"
PREPARE = "scripts/testing/prepare_playwright_image.sh"
LOCKED = "1.59.1"
IMAGE = f"mcr.microsoft.com/playwright:v{LOCKED}-noble"

FAKE_DOCKER = r"""#!/usr/bin/env bash
# Stand-in docker: state lives in $FAKE_STATE.
echo "$*" >> "$FAKE_STATE/docker.log"
case "$1" in
  info)
    [ "${FAKE_DOCKER_DOWN:-0}" = 1 ] && exit 1
    if [ "${2:-}" = "--format" ]; then echo "$FAKE_STATE/docker-root"; fi
    exit 0 ;;
  image)
    [ -f "$FAKE_STATE/image-present" ] || exit 1
    case "$*" in
      *RepoDigests*) echo "mcr.microsoft.com/playwright@sha256:feedface" ;;
      *.Id*) cat "$FAKE_STATE/image-id" 2>/dev/null || echo "sha256:1111" ;;
    esac
    exit 0 ;;
  pull) touch "$FAKE_STATE/image-present"; exit 0 ;;
  save) printf 'fake-image-layers'; exit 0 ;;
  load) cat > "$FAKE_STATE/loaded-bytes"; touch "$FAKE_STATE/image-present"; echo "Loaded image: $IMAGE"; exit 0 ;;
  stats)
    [ -f "$FAKE_STATE/running" ] && echo "${FAKE_MEM:-1.5GiB} / 7.6GiB"
    exit 0 ;;
  run)
    shift
    printf '%s\n' "$@" > "$FAKE_STATE/run-args"
    touch "$FAKE_STATE/running"
    echo "fake playwright output"
    [ -n "${FAKE_RUN_SLEEP:-}" ] && sleep "$FAKE_RUN_SLEEP"
    rm -f "$FAKE_STATE/running"
    exit "${FAKE_RUN_STATUS:-0}" ;;
esac
exit 0
"""

FAKE_UNAME = r"""#!/usr/bin/env bash
case "${1:-}" in
  -m) echo "${FAKE_ARCH:-x86_64}" ;;
  *) echo "${FAKE_OS:-Linux}" ;;
esac
"""

FAKE_NPM = r"""#!/usr/bin/env bash
# Stand-in `npm ci`: installs only the two files the container script reads.
echo "npm $*" >> "$FAKE_STATE/inner.log"
mkdir -p node_modules/@playwright/test node_modules/playwright-core
printf '{"version": "%s"}' "${FAKE_INSTALLED:-1.59.1}" > node_modules/@playwright/test/package.json
cat > node_modules/playwright-core/browsers.json <<'JSON'
{"browsers": [
  {"name": "chromium", "revision": "1217", "browserVersion": "147.0.7727.15"},
  {"name": "chromium-headless-shell", "revision": "1217", "browserVersion": "147.0.7727.15"},
  {"name": "firefox", "revision": "1500"}
]}
JSON
"""

FAKE_NPX = r"""#!/usr/bin/env bash
echo "npx $*" >> "$FAKE_STATE/inner.log"
[ "${2:-}" = "--version" ] && { echo "Version 1.59.1"; exit 0; }
exit "${FAKE_TEST_STATUS:-0}"
"""


def lock(test: str = LOCKED, core: str = LOCKED) -> str:
    return json.dumps(
        {
            "packages": {
                "node_modules/@playwright/test": {"version": test},
                "node_modules/playwright-core": {"version": core},
            }
        }
    )


@unittest.skipUnless(os.name == "posix" and shutil.which("bash") and shutil.which("node"), "needs bash and node")
class E2ERuntimeScriptsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="tds-e2e-scripts-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.repo = self.tmp / "repo"
        (self.repo / "scripts" / "testing").mkdir(parents=True)
        for script in (RUN, PREPARE):
            shutil.copy2(ROOT / script, self.repo / script)
        (self.repo / "frontend" / "e2e").mkdir(parents=True)
        (self.repo / "frontend" / "package-lock.json").write_text(lock(), encoding="utf-8")
        (self.repo / "frontend" / "package.json").write_text("{}", encoding="utf-8")
        (self.repo / "frontend" / "e2e" / "a.spec.ts").write_text("// spec", encoding="utf-8")
        (self.repo / "frontend" / "node_modules" / "host-only").mkdir(parents=True)
        self.state = self.tmp / "state"
        (self.state / "docker-root").mkdir(parents=True)
        (self.state / "image-present").touch()
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        for name, body in (("docker", FAKE_DOCKER), ("uname", FAKE_UNAME), ("npm", FAKE_NPM), ("npx", FAKE_NPX)):
            path = self.bin / name
            path.write_text(body, encoding="utf-8")
            path.chmod(0o755)
        self.env = {
            **os.environ,
            "PATH": f"{self.bin}:{os.environ['PATH']}",
            "HOME": str(self.tmp / "home"),
            "FAKE_STATE": str(self.state),
            "IMAGE": IMAGE,
            "TDS_E2E_MIN_FREE_GB": "0",
            "TDS_E2E_MIN_MEM_GB": "0",
        }
        self.env.pop("TDS_PLAYWRIGHT_IMAGE", None)
        self.env.pop("TDS_E2E_REPORT_DIR", None)

    def run_script(self, script: str, *args: str, **env: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["bash", str(self.repo / script), *args],
            cwd=self.tmp,
            env={**self.env, **env},
            capture_output=True,
            text=True,
            timeout=60,
        )

    def run_args(self) -> list[str]:
        return (self.state / "run-args").read_text(encoding="utf-8").splitlines()

    def report_dirs(self) -> list[Path]:
        root = self.repo / "outputs" / "e2e-reports"
        return sorted(root.iterdir()) if root.is_dir() else []

    # ---------------------------------------------------------------- runner
    def test_default_run_uses_locked_image_four_workers_and_isolated_mounts(self) -> None:
        result = self.run_script(RUN)
        self.assertEqual(result.returncode, 0, result.stderr)
        args = self.run_args()
        self.assertIn(IMAGE, args)
        self.assertIn("--shm-size=1g", args)
        self.assertIn("--init", args)
        self.assertIn("CI=", args)  # config's CI-only single worker / retries never apply
        self.assertIn("PLAYWRIGHT_HTML_OPEN=never", args)
        self.assertIn(f"{self.repo / 'frontend'}:/src:ro", args)
        self.assertEqual(args[-1], "--workers=4")
        self.assertIn("--user", args)
        self.assertEqual(args[args.index("--user") + 1], f"{os.getuid()}:{os.getgid()}")
        [report] = self.report_dirs()
        info = (report / "run-info.txt").read_text(encoding="utf-8")
        self.assertIn(f"image={IMAGE}", info)
        self.assertIn("image_id=sha256:1111", info)
        self.assertIn(f"playwright_locked={LOCKED}", info)
        self.assertIn("exit_status=0", info)
        self.assertIn("shm_size=1g", info)
        self.assertRegex(info, r"host_cpus=\d+")
        self.assertIn("container_memory_peak_mib=", info)
        name = args[args.index("--name") + 1]
        self.assertRegex(name, r"^tds-frontend-e2e-\d{8}T\d{6}Z-\d+$")
        self.assertIn("fake playwright output", (report / "console.log").read_text(encoding="utf-8"))
        self.assertTrue((report / "container-script.sh").is_file())

    def test_container_memory_is_sampled_and_peak_recorded(self) -> None:
        result = self.run_script(RUN, FAKE_RUN_SLEEP="7", FAKE_MEM="1.5GiB")
        self.assertEqual(result.returncode, 0, result.stderr)
        [report] = self.report_dirs()
        samples = (report / "memory-samples.txt").read_text(encoding="utf-8")
        self.assertIn("1.5GiB / 7.6GiB", samples)
        self.assertIn("container_memory_peak_mib=1536", (report / "run-info.txt").read_text(encoding="utf-8"))

    def test_explicit_workers_and_spec_arguments_are_passed_through(self) -> None:
        result = self.run_script(RUN, "e2e/overview-trend.spec.ts", "--workers=4", "--repeat-each=3")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.run_args()[-3:], ["e2e/overview-trend.spec.ts", "--workers=4", "--repeat-each=3"])
        result = self.run_script(RUN, "-j", "2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.run_args()[-2:], ["-j", "2"])

    def test_failing_tests_keep_their_exit_status(self) -> None:
        result = self.run_script(RUN, FAKE_RUN_STATUS="3")
        self.assertEqual(result.returncode, 3)
        self.assertIn("E2E FAILED with exit status 3", result.stderr)
        [report] = self.report_dirs()
        self.assertIn("exit_status=3", (report / "run-info.txt").read_text(encoding="utf-8"))

    def test_missing_image_fails_before_running_and_never_downloads(self) -> None:
        (self.state / "image-present").unlink()
        result = self.run_script(RUN)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("is not loaded", result.stderr)
        log = (self.state / "docker.log").read_text(encoding="utf-8")
        self.assertNotIn("run", log.split())
        self.assertNotIn("pull", log.split())

    def test_version_mismatches_are_rejected(self) -> None:
        result = self.run_script(RUN, TDS_PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.60.0-noble")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match the locked Playwright version v1.59.1", result.stderr)
        (self.repo / "frontend" / "package-lock.json").write_text(lock(core="1.58.0"), encoding="utf-8")
        result = self.run_script(RUN)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("!= playwright-core 1.58.0", result.stderr)
        self.assertFalse((self.state / "run-args").exists())

    def test_host_preflight(self) -> None:
        for env, message in (
            ({"FAKE_ARCH": "aarch64"}, "x86_64 host required"),
            ({"FAKE_OS": "Darwin"}, "Linux host required"),
            ({"FAKE_DOCKER_DOWN": "1"}, "cannot reach the Docker daemon"),
            ({"TDS_E2E_MIN_FREE_GB": "100000000"}, "GiB free, need 100000000 GiB"),
            ({"TDS_E2E_MIN_MEM_GB": "100000000"}, "memory available"),
            ({"TDS_E2E_REPORT_DIR": str(self.repo / "outputs" / "v3.1.38" / "e2e")}, "outputs/v<version>"),
        ):
            with self.subTest(env=env):
                result = self.run_script(RUN, **env)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)
                self.assertFalse((self.state / "run-args").exists())

    # ------------------------------------------------- in-container script
    def container_script(self) -> str:
        self.assertEqual(self.run_script(RUN).returncode, 0)
        [report] = self.report_dirs()
        return (report / "container-script.sh").read_text(encoding="utf-8")

    def run_inner(self, **env: str) -> tuple[subprocess.CompletedProcess[str], Path]:
        script = self.container_script()
        browsers = self.tmp / "ms-playwright"
        for name in ("chromium-1217", "chromium_headless_shell-1217"):
            (browsers / name).mkdir(parents=True, exist_ok=True)
        work = self.tmp / "work"
        result = subprocess.run(
            ["bash", "-c", script, "playwright-e2e", "e2e/a.spec.ts", "--workers=4"],
            cwd=self.tmp,
            env={
                **self.env,
                "TDS_E2E_SRC": str(self.repo / "frontend"),
                "TDS_E2E_WORK": str(work),
                "TDS_E2E_OUT": str(self.tmp / "report"),
                "EXPECTED_PLAYWRIGHT": LOCKED,
                "PLAYWRIGHT_BROWSERS_PATH": str(browsers),
                **env,
            },
            capture_output=True,
            text=True,
            timeout=60,
        )
        return result, work

    def test_container_script_installs_from_lock_checks_browsers_and_runs(self) -> None:
        result, work = self.run_inner()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("chromium revision 1217 (147.0.7727.15)", result.stdout)
        self.assertRegex(result.stdout, r"==> container resources: cpus=\d+ MemAvailable: \d+ kB shm=")
        self.assertTrue((work / "e2e" / "a.spec.ts").is_file())
        self.assertFalse((work / "node_modules" / "host-only").exists(), "host node_modules must not be copied")
        log = (self.state / "inner.log").read_text(encoding="utf-8")
        self.assertIn("npm ci --no-audit --no-fund", log)
        self.assertIn(
            f"npx playwright test --forbid-only --reporter=list,html,json --output={self.tmp / 'report'}/test-results e2e/a.spec.ts --workers=4",
            log,
        )

    def test_container_script_uses_the_given_registry(self) -> None:
        result, _ = self.run_inner(TDS_E2E_NPM_REGISTRY="https://registry.example.test/")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--registry=https://registry.example.test/", (self.state / "inner.log").read_text(encoding="utf-8"))

    def test_container_script_propagates_test_failures(self) -> None:
        result, _ = self.run_inner(FAKE_TEST_STATUS="1")
        self.assertEqual(result.returncode, 1)

    def test_container_script_rejects_missing_browser_revision(self) -> None:
        script = self.container_script()
        browsers = self.tmp / "ms-playwright-old"
        (browsers / "chromium-1200").mkdir(parents=True)
        result = subprocess.run(
            ["bash", "-c", script, "playwright-e2e"],
            cwd=self.tmp,
            env={**self.env, "TDS_E2E_SRC": str(self.repo / "frontend"), "TDS_E2E_WORK": str(self.tmp / "w2"),
                 "TDS_E2E_OUT": str(self.tmp / "r2"), "EXPECTED_PLAYWRIGHT": LOCKED, "PLAYWRIGHT_BROWSERS_PATH": str(browsers)},
            capture_output=True, text=True, timeout=60,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("image lacks browsers expected by Playwright 1.59.1", result.stderr)
        self.assertNotIn("playwright test", (self.state / "inner.log").read_text(encoding="utf-8"))

    def test_container_script_rejects_installed_version_drift(self) -> None:
        result, _ = self.run_inner(FAKE_INSTALLED="1.60.0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("installed @playwright/test 1.60.0 != locked 1.59.1", result.stderr)

    # --------------------------------------------------------- image prep
    def test_save_then_load_round_trip_with_checksum_and_image_id(self) -> None:
        (self.state / "image-present").unlink()
        archive_dir = self.tmp / "e2e-images"
        result = self.run_script(PREPARE, "save", str(archive_dir))
        self.assertEqual(result.returncode, 0, result.stderr)
        base = f"playwright-v{LOCKED}-noble"
        for suffix in (".tar.gz", ".tar.gz.sha256", ".meta"):
            self.assertTrue((archive_dir / f"{base}{suffix}").is_file(), suffix)
        self.assertIn(f"pull {IMAGE}", (self.state / "docker.log").read_text(encoding="utf-8"))
        meta = (archive_dir / f"{base}.meta").read_text(encoding="utf-8")
        self.assertIn("image_id=sha256:1111", meta)
        self.assertIn("repo_digests=mcr.microsoft.com/playwright@sha256:feedface", meta)

        # Build host: image not present yet.
        (self.state / "image-present").unlink()
        result = self.run_script(PREPARE, "load", str(archive_dir))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.state / "loaded-bytes").read_text(), "fake-image-layers")
        self.assertIn("Loaded", result.stdout)
        log = (self.state / "docker.log").read_text(encoding="utf-8")
        self.assertNotIn("prune", log)
        self.assertNotIn("rmi", log)

    def test_load_rejects_corrupt_archive_and_wrong_image_id(self) -> None:
        archive_dir = self.tmp / "e2e-images"
        self.assertEqual(self.run_script(PREPARE, "save", str(archive_dir)).returncode, 0)
        base = archive_dir / f"playwright-v{LOCKED}-noble"
        (self.state / "image-id").write_text("sha256:2222\n", encoding="utf-8")
        result = self.run_script(PREPARE, "load", str(archive_dir))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("differs from the saved ID sha256:1111", result.stderr)
        (self.state / "image-id").unlink()
        Path(f"{base}.tar.gz").write_bytes(b"tampered")
        (self.state / "loaded-bytes").unlink(missing_ok=True)
        result = self.run_script(PREPARE, "load", str(archive_dir))
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.state / "loaded-bytes").exists(), "nothing is loaded when the checksum fails")

    def test_save_refuses_release_output_directory_and_load_checks_space(self) -> None:
        result = self.run_script(PREPARE, "save", str(self.repo / "outputs" / "v3.1.38"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("outputs/v<version>", result.stderr)
        archive_dir = self.tmp / "e2e-images"
        self.assertEqual(self.run_script(PREPARE, "save", str(archive_dir)).returncode, 0)
        result = self.run_script(PREPARE, "load", str(archive_dir), TDS_E2E_MIN_FREE_GB="100000000")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("never prunes", result.stderr)


class E2ERuntimeScriptsStaticTest(unittest.TestCase):
    def test_scripts_are_test_infrastructure_only(self) -> None:
        pack = (ROOT / "scripts" / "pack_release.py").read_text(encoding="utf-8")
        self.assertNotIn("scripts/testing", pack)
        self.assertNotIn('"scripts"', pack)  # never packs the whole scripts tree
        for script in (RUN, PREPARE):
            text = (ROOT / script).read_text(encoding="utf-8")
            self.assertTrue(text.startswith("#!/usr/bin/env bash\n"), script)
            self.assertIn("set -euo pipefail", text)
            self.assertNotIn("\r", text, f"{script} must keep LF line endings")
            self.assertNotRegex(text, r"docker (system |image |volume |container )?prune|docker rmi")
        run = (ROOT / RUN).read_text(encoding="utf-8")
        self.assertNotIn("playwright install", run)
        self.assertNotIn("--retries", run)


if __name__ == "__main__":
    unittest.main()
