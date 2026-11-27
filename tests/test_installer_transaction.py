"""Real rsync transaction regressions in private, non-system installation trees.

Only installer function definitions are loaded. Docker/systemd are recording
stubs, and every shell filesystem writer rejects paths outside the fixture.
"""
from __future__ import annotations

import hashlib
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "install.sh"
HELPER = ROOT / "scripts/verify_release_state.py"
BASH = shutil.which("bash")
RSYNC = shutil.which("rsync")
POSIX_RUNTIME = os.name == "posix" and BASH is not None and RSYNC is not None


def installer_functions(source: str) -> str:
    """Use the last function terminator, not braces inside Python heredocs."""
    starts = list(re.finditer(r"^([a-z_][a-z0-9_]*)\(\) \{", source, re.M))
    functions = []
    omitted = {"info", "ok", "warn", "die", "compose_in", "capture_failure_diagnostics"}
    for index, match in enumerate(starts):
        if match.group(1) in omitted:
            continue
        boundary = starts[index + 1].start() if index + 1 < len(starts) else len(source)
        segment = source[match.start():boundary]
        end = segment.rfind("\n}\n")
        if end < 0:
            raise AssertionError(f"No complete function definition: {match.group(1)}")
        functions.append(segment[:end + 3])
    return "\n".join(functions)


def tree_state(root: Path) -> dict[str, object]:
    """Independent comparison to actual pre-state, never to its old MANIFEST."""
    if not root.exists() and not root.is_symlink():
        return {"exists": False}
    result: dict[str, object] = {}
    paths = [root]
    if root.is_dir() and not root.is_symlink():
        paths += sorted(root.rglob("*"))
    for item in paths:
        stat = item.lstat()
        row: dict[str, object] = {
            "mode": stat.st_mode & 0o7777,
            "uid": stat.st_uid,
            "gid": stat.st_gid,
            "mtime_ns": stat.st_mtime_ns,
        }
        if item.is_symlink():
            row["link"] = os.readlink(item)
        elif item.is_file():
            row["sha256"] = hashlib.sha256(item.read_bytes()).hexdigest()
        else:
            row["directory"] = True
        result["." if item == root else item.relative_to(root).as_posix()] = row
    return result


@unittest.skipUnless(POSIX_RUNTIME, "requires POSIX bash and real rsync; run on isolated Linux")
class InstallerTransactionTests(unittest.TestCase):
    def setUp(self) -> None:
        if not HELPER.is_file():
            self.fail("The production release integrity helper is required")
        self.temp = tempfile.TemporaryDirectory(prefix="tds-transaction-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.target = self.root / "target"
        self.paths = [self.root / "system" / name for name in (
            "threat-detection-compose.service",
            "threat-detection-runtime-watchdog.service",
            "threat-detection-runtime-watchdog.timer",
            "tdsdiag",
        )]
        self.paths[0].parent.mkdir()
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        writers = {name: shutil.which(name) for name in ("install", "rm", "mkdir", "cp")}
        # Also guard subprocesses such as find -exec install, which do not see
        # the shell's writer functions. No fixture may write system paths.
        wrapper = "#!/usr/bin/env python3\n" + (
            "import os, sys\n"
            "from pathlib import Path\n"
            f"writers = {writers!r}\n"
            f"root = {str(self.root)!r}\n"
            "name = Path(sys.argv[0]).name\n"
            "args = sys.argv[1:]\n"
            "paths = args[-1:] if name in {'cp','install'} else [arg for arg in args if not arg.startswith('-')]\n"
            "for value in paths:\n"
            "    if os.path.commonpath([root, os.path.abspath(value)]) != root:\n"
            "        print('Refusing fixture write outside its private directory', file=sys.stderr)\n"
            "        raise SystemExit(98)\n"
            "os.execv(writers[name], [writers[name], *args])\n"
        )
        for name in writers:
            executable = self.bin_dir / name
            executable.write_text(wrapper, encoding="utf-8")
            executable.chmod(0o755)
        self.functions = installer_functions(INSTALLER.read_text(encoding="utf-8"))

    def release_tree(self, destination: Path, version: str, content: str) -> None:
        destination.mkdir(parents=True)
        template = (ROOT / ".env.example").read_text(encoding="utf-8")
        template = re.sub(r"(?m)^RELEASE_VERSION=.*$", f"RELEASE_VERSION={version}", template)
        files = {
            "VERSION": version + "\n",
            ".env.example": template,
            "pom.xml": f"<fixture>{content}</fixture>\n",
            "data/config.txt": content + "\n",
            "docker/docker-compose.yml": "services: {}\n",
            "frontend/.env.production": f"VITE_APP_VERSION={version}\n",
            "scripts/tdsdiag.sh": f"#!/bin/sh\n# {content}\nexit 0\n",
            "scripts/runtime-watchdog.sh": "#!/bin/sh\nexit 0\n",
        }
        for unit in self.paths[:3]:
            files[f"deploy/systemd/{unit.name}"] = f"# {content} {unit.name}\n"
        for relative, value in files.items():
            path = destination / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value, encoding="utf-8")
        for name in ("prepare_release_env.py", "verify_release_state.py"):
            shutil.copyfile(ROOT / "scripts" / name, destination / "scripts" / name)
        for script in (destination / "scripts").iterdir():
            script.chmod(0o755)
        self.write_manifest(destination)
        self.stamp(destination, 0)

    @staticmethod
    def write_manifest(root: Path) -> None:
        entries = []
        for path in sorted(root.rglob("*")):
            if path.is_file() and path.name not in {"MANIFEST.sha256", ".env", ".installed_version"}:
                entries.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(root).as_posix()}\n")
        (root / "MANIFEST.sha256").write_text("".join(entries), encoding="utf-8")

    @staticmethod
    def stamp(root: Path, seconds: int) -> None:
        for path in sorted(root.rglob("*"), reverse=True):
            if not path.is_symlink():
                os.utime(path, ns=(seconds * 10**9, seconds * 10**9))
        os.utime(root, ns=(seconds * 10**9, seconds * 10**9))

    def setup_upgrade(self, old_version: str = "3.1.32", new_version: str = "3.1.39") -> None:
        self.release_tree(self.source, new_version, "new-data")
        self.release_tree(self.target, old_version, "old-data")
        subprocess.run(
            [sys.executable, str(ROOT / "scripts/prepare_release_env.py"),
             str(self.target / ".env.example"), str(self.target / ".env")],
            check=True, capture_output=True, text=True,
        )
        with (self.target / ".env").open("a", encoding="utf-8") as stream:
            stream.write("CUSTOM_OPERATOR_SETTING=retained-setting\n")
        (self.target / ".installed_version").write_text(old_version + "\n", encoding="utf-8")
        for path in self.paths:
            path.write_text("old-external\n", encoding="utf-8")
            path.chmod(0o750 if path.name == "tdsdiag" else 0o640)
            os.utime(path, ns=(0, 0))
        self.stamp(self.target, 0)

    def run_shell(self, body: str, *, fault: str = "", source: Path | None = None) -> subprocess.CompletedProcess[str]:
        assignments = {
            "TEST_ROOT": self.root,
            "SRC_ROOT": source or self.source,
            "SCRIPT_DIR": (source or self.source) / "scripts",
            "TARGET_DIR": self.target,
            "BACKUP_ROOT": self.root / "backups",
            "UNIT_PATH": self.paths[0],
            "WATCHDOG_SERVICE_PATH": self.paths[1],
            "WATCHDOG_TIMER_PATH": self.paths[2],
            "DIAG_PATH": self.paths[3],
            "CONTROL_LOG": self.root / "commands.log",
            "REAL_RSYNC": RSYNC,
            "FAULT_MODE": fault,
            "PATH": str(self.bin_dir) + os.pathsep + os.environ.get("PATH", ""),
        }
        prelude = "\n".join(f"{key}={shlex.quote(str(value))}" for key, value in assignments.items())
        harness = "set -Eeuo pipefail\n" + prelude + r'''
UNIT_NAME=threat-detection-compose.service
WATCHDOG_SERVICE_NAME=threat-detection-runtime-watchdog.service
WATCHDOG_TIMER_NAME=threat-detection-runtime-watchdog.timer
INSTALL_MODE=upgrade
NO_START=true
PREPARED_ENV=''
ROLLBACK_DIR=''
TARGET_EXISTED=false
INSTALL_ACTIVE=false
TRUSTED_MANIFEST=''
INTEGRITY_HELPER="$SCRIPT_DIR/verify_release_state.py"
PRESERVE_ROLLBACK=false
ROLLBACK_RESULT=not-needed
RELEASE_VERSION="$(cat "$SRC_ROOT/VERSION")"
info() { printf 'INFO:%s\n' "$*"; }
ok() { printf 'OK:%s\n' "$*"; }
warn() { printf 'WARN:%s\n' "$*"; }
die() { printf 'ERROR:%s\n' "$*" >&2; exit 1; }
safe_path() { [[ "$1" == "$TEST_ROOT" || "$1" == "$TEST_ROOT/"* ]] || { printf 'Unsafe fixture write: %s\n' "$1" >&2; exit 98; }; }
mkdir() { for arg in "$@"; do [[ "$arg" == -* ]] || safe_path "$arg"; done; command mkdir "$@"; }
rm() { for arg in "$@"; do [[ "$arg" == -* ]] || safe_path "$arg"; done; command rm "$@"; }
cp() {
  local last="${!#}"
  safe_path "$last"
  command cp "$@" || return $?
  if [[ "$FAULT_MODE" == corrupt-external-restore && "$last" == "$DIAG_PATH" ]]; then
    printf 'bad external restore\n' >"$last"
  fi
}
install() { local last="${!#}"; safe_path "$last"; command install "$@"; }
mktemp() {
  if [[ "${1:-}" == -d ]]; then command mktemp -d "$TEST_ROOT/rollback.XXXXXX";
  else command mktemp "$TEST_ROOT/prepared.XXXXXX"; fi
}
systemctl() {
  printf 'systemctl %s\n' "$*" >>"$CONTROL_LOG"
  [[ "$FAULT_MODE" != fail-unit-reload || "$1" != daemon-reload ]]
}
compose_in() { printf 'compose %s\n' "$*" >>"$CONTROL_LOG"; }
capture_failure_diagnostics() { printf 'fixture diagnostics only\n' >"$TEST_ROOT/failure-diagnostics.txt"; }
rsync() {
  local last="${!#}" penultimate="${@: -2:1}"
  safe_path "$last"
  if [[ "$penultimate" == "$SRC_ROOT/" && "$last" == "$TARGET_DIR/" ]]; then
    if [[ "$FAULT_MODE" == omit-forward ]]; then return 0; fi
    command "$REAL_RSYNC" "$@" || return $?
    case "$FAULT_MODE" in
      corrupt-target) printf 'bad-data\n' >"$TARGET_DIR/data/config.txt" ;;
      missing-target) command rm -f "$TARGET_DIR/data/config.txt" ;;
      corrupt-manifest) printf 'forged manifest\n' >"$TARGET_DIR/MANIFEST.sha256" ;;
    esac
    return 0
  fi
  if [[ "$penultimate" == "$ROLLBACK_DIR/target/" && "$last" == "$TARGET_DIR/" ]]; then
    [[ "$FAULT_MODE" != fail-restore ]] || return 73
    command "$REAL_RSYNC" "$@" || return $?
    [[ "$FAULT_MODE" != corrupt-restore ]] || printf 'corruption\n' >"$TARGET_DIR/data/config.txt"
    return 0
  fi
  command "$REAL_RSYNC" "$@"
}
''' + "\n" + self.functions + "\n" + body + "\n"
        return subprocess.run([BASH, "-c", harness], cwd=ROOT, text=True, capture_output=True, timeout=90, check=False)

    def assert_ok(self, result: subprocess.CompletedProcess[str]) -> None:
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_fixture_reproduces_default_rsync_skip_at_zero_and_nonzero_mtime(self) -> None:
        for seconds in (0, 1234567890):
            with self.subTest(mtime=seconds):
                source = self.root / f"quick-source-{seconds}"
                target = self.root / f"quick-target-{seconds}"
                source.mkdir(); target.mkdir()
                for base, value in ((source, "new-data\n"), (target, "old-data\n")):
                    file = base / "ordinary.txt"
                    file.write_text(value, encoding="utf-8")
                    os.utime(file, ns=(seconds * 10**9, seconds * 10**9))
                subprocess.run([RSYNC, "-a", str(source) + "/", str(target) + "/"], check=True)
                self.assertEqual((target / "ordinary.txt").read_text(), "old-data\n")
                subprocess.run([RSYNC, "-a", "--checksum", str(source) + "/", str(target) + "/"], check=True)
                self.assertEqual((target / "ordinary.txt").read_text(), "new-data\n")

    def test_upgrade_reinstall_and_downgrade_converge_managed_content_and_preserve_env(self) -> None:
        for old in ("3.1.32", "3.1.38", "3.1.39", "3.1.99"):
            with self.subTest(old=old):
                if self.source.exists(): shutil.rmtree(self.source)
                if self.target.exists(): shutil.rmtree(self.target)
                self.setup_upgrade(old)
                before = dict(line.split("=", 1) for line in (self.target / ".env").read_text().splitlines() if "=" in line and not line.startswith("#"))
                result = self.run_shell("detect_existing_install\nprepare_runtime_env\nprepare_rollback\nsync_release_tree\nverify_installed_release metadata\nprintf 'MODE=%s\\n' \"$INSTALL_MODE\"\n")
                self.assert_ok(result)
                self.assertEqual((self.target / "VERSION").read_text().strip(), "3.1.39")
                self.assertEqual((self.target / ".installed_version").read_text().strip(), "3.1.39")
                self.assertEqual((self.target / "data/config.txt").read_text(), "new-data\n")
                self.assertEqual((self.target / ".env.example").read_bytes(), (self.source / ".env.example").read_bytes())
                after = dict(line.split("=", 1) for line in (self.target / ".env").read_text().splitlines() if "=" in line and not line.startswith("#"))
                self.assertEqual(after["RELEASE_VERSION"], "3.1.39")
                for key in ("POSTGRES_PASSWORD", "JWT_SECRET", "CONFIG_ENCRYPTION_KEY", "CUSTOM_OPERATOR_SETTING"):
                    self.assertTrue(after[key] == before[key], f"retention failed for {key}")

    def test_source_equals_target_avoids_self_copy_but_still_checks_content(self) -> None:
        self.setup_upgrade()
        shutil.rmtree(self.target)
        self.target = self.source
        result = self.run_shell("detect_existing_install\nprepare_runtime_env\nprepare_rollback\nsync_release_tree\nverify_installed_release metadata\n", source=self.target)
        self.assert_ok(result)
        self.assertIn("file synchronization is not required", result.stdout)

    def test_bad_target_content_blocks_metadata_and_external_installs(self) -> None:
        for fault in ("omit-forward", "corrupt-target", "missing-target", "corrupt-manifest"):
            with self.subTest(fault=fault):
                if self.source.exists(): shutil.rmtree(self.source)
                if self.target.exists(): shutil.rmtree(self.target)
                self.setup_upgrade()
                old_env = (self.target / ".env").read_bytes()
                result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\n", fault=fault)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual((self.target / ".env").read_bytes(), old_env)
                marker = self.target / ".installed_version"
                self.assertFalse(marker.is_file() and marker.read_text().strip() == "3.1.39")
                self.assertEqual(self.paths[3].read_text(), "old-external\n")
                self.assertNotIn("Release files installed", result.stdout)

    def test_rollback_restores_actual_mixed_prestate_units_and_external_diag(self) -> None:
        self.setup_upgrade()
        # The old package manifest intentionally no longer describes actual state.
        (self.target / "data/config.txt").write_text("local-old\n", encoding="utf-8")
        (self.target / "local-only.txt").write_text("operator file\n", encoding="utf-8")
        self.stamp(self.target, 0)
        before = tree_state(self.target)
        external = [tree_state(path) for path in self.paths]
        result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\nrollback_install 42\ncleanup_temp\n")
        self.assert_ok(result)
        self.assertEqual(tree_state(self.target), before)
        self.assertEqual([tree_state(path) for path in self.paths], external)
        self.assertIn("restored", result.stdout.lower())
        self.assertEqual(list(self.root.glob("rollback.*")), [])
        self.assertNotRegex((self.root / "commands.log").read_text(), r"compose .* down -v")

    def test_bad_prepared_environment_blocks_marker_and_enters_verified_rollback(self) -> None:
        self.setup_upgrade()
        before = tree_state(self.target)
        external = [tree_state(path) for path in self.paths]
        result = self.run_shell(
            "prepare_runtime_env\nprepare_rollback\ntrap on_exit EXIT\n"
            "printf 'RELEASE_VERSION=0.0.0\\n' >\"$PREPARED_ENV\"\nsync_release_tree\n"
        )
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Release files installed", result.stdout)
        self.assertEqual(tree_state(self.target), before)
        self.assertEqual([tree_state(path) for path in self.paths], external)
        self.assertFalse(list(self.root.glob("rollback.*")))

    def test_fresh_failure_removes_only_created_target_and_external_paths(self) -> None:
        self.release_tree(self.source, "3.1.39", "new-data")
        result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\nrollback_install 42\ncleanup_temp\n")
        self.assert_ok(result)
        self.assertFalse(self.target.exists())
        self.assertTrue(self.source.is_dir())
        self.assertTrue(all(not path.exists() for path in self.paths))
        self.assertEqual(list(self.root.glob("rollback.*")), [])

    def test_fresh_files_only_install_commits_verified_content_and_environment(self) -> None:
        self.release_tree(self.source, "3.1.39", "new-data")
        result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\ncommit_install\ncleanup_temp\n")
        self.assert_ok(result)
        self.assertEqual((self.target / "VERSION").read_text().strip(), "3.1.39")
        self.assertEqual((self.target / ".installed_version").read_text().strip(), "3.1.39")
        self.assertEqual((self.target / ".env").stat().st_mode & 0o777, 0o600)
        self.assertTrue(all(path.is_file() for path in self.paths))
        self.assertFalse(list(self.root.glob("rollback.*")))
        self.assertFalse((self.root / "commands.log").exists(), "files-only transaction must not start services")

    def test_restore_command_and_verification_failures_keep_snapshot_after_exit_cleanup(self) -> None:
        for fault in ("fail-restore", "corrupt-restore", "corrupt-external-restore", "fail-unit-reload"):
            with self.subTest(fault=fault):
                if self.source.exists(): shutil.rmtree(self.source)
                if self.target.exists(): shutil.rmtree(self.target)
                self.setup_upgrade()
                before = tree_state(self.target)
                result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\ntrap on_exit EXIT\nexit 42\n", fault=fault)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                retained = list(self.root.glob("rollback.*"))
                self.assertTrue(retained, result.stdout + result.stderr)
                self.assertTrue(any((directory / "target").is_dir() for directory in retained))
                for directory in retained:
                    self.assertEqual(directory.stat().st_mode & 0o777, 0o700)
                    self.assertEqual(tree_state(directory / "target"), before)
                    self.assertTrue((directory / "release-manifest.sha256").is_file())
                    self.assertTrue((directory / "verify_release_state.py").is_file())
                    self.assertTrue((directory / "rollback-failure.txt").is_file())
                self.assertNotIn("previous installation was restored", result.stdout.lower())
                self.assertNotIn("Release installation committed", result.stdout)
                for directory in retained: shutil.rmtree(directory)

    def test_commit_refuses_corruption_and_no_start_uses_the_same_validation(self) -> None:
        self.setup_upgrade()
        result = self.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\nprintf 'bad-data\\n' >\"$TARGET_DIR/data/config.txt\"\ncommit_install\n")
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(list(self.root.glob("rollback.*")), "validation must precede snapshot deletion")
        source = INSTALLER.read_text(encoding="utf-8")
        execution = source.rsplit("\nvalidate_target\n", 1)[1]
        no_start = execution.split("if $NO_START; then", 1)[1].split("\nfi", 1)[0]
        self.assertIn("commit_install", no_start)
        commit = installer_functions(source)
        definition = commit[commit.index("commit_install() {"):]
        self.assertLess(definition.index("verify_installed_release"), definition.index("INSTALL_ACTIVE=false"))


if __name__ == "__main__":
    unittest.main()
