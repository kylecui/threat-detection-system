"""Run the real diagnostic script against an isolated tree and healthy command stubs.

No Docker/systemd/network command reaches the host. The sole production path
assignment is rewritten in a temporary script; production accepts no test-only
target override. Run this module on Linux as part of installer verification.
"""

from __future__ import annotations

import json
import os
import shlex
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASH = shutil.which("bash")
SECRET = "test-only-secret-must-not-be-collected-020"


@unittest.skipUnless(os.name == "posix" and BASH, "requires POSIX Bash; run on Linux")
class DiagnosticVersionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="tdsdiag-versions-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.target = self.root / "installation"
        self.bin = self.root / "bin"
        self.bin.mkdir()
        (self.target / "scripts").mkdir(parents=True)
        (self.target / "docker").mkdir()
        (self.target / "frontend").mkdir()
        (self.target / "VERSION").write_text("3.1.40\n", encoding="utf-8")
        (self.target / ".installed_version").write_text("3.1.40\n", encoding="utf-8")
        (self.target / ".env.example").write_text("RELEASE_VERSION=3.1.40\n", encoding="utf-8")
        (self.target / ".env").write_text(
            f"RELEASE_VERSION=3.1.40\nPOSTGRES_PASSWORD={SECRET}\nJWT_SECRET={SECRET}\n",
            encoding="utf-8",
        )
        (self.target / "frontend/.env.production").write_text(
            "VITE_APP_VERSION=3.1.40\n", encoding="utf-8"
        )
        (self.target / "docker/docker-compose.yml").write_text("services: {}\n", encoding="utf-8")
        shutil.copyfile(
            ROOT / "scripts/verify_release_state.py",
            self.target / "scripts/verify_release_state.py",
        )
        self.write_executable(
            self.target / "scripts/runtime-watchdog.sh", "#!/bin/sh\nprintf '[OK] mock runtime\\n'\n"
        )
        self.write_executable(self.bin / "id", "#!/bin/sh\nprintf '0\\n'\n")
        self.write_executable(self.bin / "curl", "#!/bin/sh\nprintf '200'\n")
        self.write_executable(self.bin / "timeout", "#!/bin/sh\nexit 0\n")
        self.write_executable(self.bin / "ss", "#!/bin/sh\nexit 0\n")
        self.write_executable(self.bin / "ctr", "#!/bin/sh\nexit 0\n")
        self.write_executable(
            self.bin / "systemctl",
            "#!/bin/sh\ncase \"$1\" in is-active) echo active;; is-enabled) echo enabled;; *) echo mock-systemd;; esac\n",
        )
        self.write_executable(
            self.bin / "docker",
            r'''#!/usr/bin/env bash
set -eu
args="$*"
if [[ "$1" == compose ]]; then
  if [[ "$args" == *" ps --status running -q" ]]; then
    for index in {1..18}; do printf 'mock-%s\n' "$index"; done
  elif [[ "$args" == *" ps -aq topic-init" ]]; then echo mock-topic
  elif [[ "$args" == *" ps -q logstash" ]]; then echo mock-logstash
  elif [[ "$args" == *" ps -aq" ]]; then printf 'mock-topic\nmock-logstash\n'
  elif [[ "$args" == *" exec -T kafka "* ]]; then echo 'Topic: attack-events PartitionCount: 2'
  else echo mock-compose
  fi
elif [[ "$1" == inspect ]]; then
  if [[ "$args" == *'.State.Status'* ]]; then echo exited
  elif [[ "$args" == *'.State.ExitCode'* ]]; then echo 0
  elif [[ "$args" == *'.IPAddress'* ]]; then echo 127.0.0.1
  elif [[ "$args" == *'.RestartCount'* ]]; then echo /mock=0
  else echo mock-inspect
  fi
else
  echo 'unexpected docker operation' >&2
  exit 90
fi
''',
        )
        self.script = self.root / "tdsdiag.sh"
        source = (ROOT / "scripts/tdsdiag.sh").read_text(encoding="utf-8")
        original = 'TARGET_DIR="/opt/threat-detection-system"'
        self.assertEqual(source.count(original), 1)
        self.script.write_text(
            source.replace(original, f"TARGET_DIR={shlex.quote(str(self.target))}", 1),
            encoding="utf-8",
        )
        self.environment = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"]}

    @staticmethod
    def write_executable(path: Path, source: str) -> None:
        path.write_text(source, encoding="utf-8")
        path.chmod(0o755)

    def run_diagnostic(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [BASH, str(self.script), *arguments],
            cwd=self.root,
            env=self.environment,
            text=True,
            capture_output=True,
            timeout=25,
            check=False,
        )
        self.assertNotIn(SECRET, result.stdout + result.stderr)
        return result

    def collect_summary(self) -> tuple[subprocess.CompletedProcess[str], dict[str, str], str]:
        archive_path = self.root / "diagnostic.tar.gz"
        result = self.run_diagnostic("--output", str(archive_path))
        self.assertTrue(archive_path.is_file(), result.stdout + result.stderr)
        self.assertEqual(archive_path.stat().st_mode & 0o777, 0o600)
        with tarfile.open(archive_path, "r:gz") as archive:
            names = archive.getnames()
            self.assertNotIn(".env", names)
            text = archive.extractfile("./diag-summary.txt").read().decode("utf-8")
            summary = json.load(archive.extractfile("./diag-summary.json"))
            for member in archive.getmembers():
                if member.isfile():
                    self.assertNotIn(SECRET.encode(), archive.extractfile(member).read(), member.name)
        return result, summary, text

    def test_consistent_metadata_passes_self_test(self) -> None:
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("tdsdiag self-test passed", result.stdout)

    def test_mismatch_fails_even_when_every_health_probe_is_successful(self) -> None:
        (self.target / "VERSION").write_text("3.1.32\n", encoding="utf-8")
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("Release version metadata", result.stderr)
        self.assertIn("status=mismatch", result.stderr)
        self.assertNotIn("self-test passed", result.stdout)

    def test_missing_marker_is_explicit_legacy_compatibility(self) -> None:
        (self.target / ".installed_version").unlink()
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Legacy installation", result.stderr)
        result, summary, _ = self.collect_summary()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(summary["version_consistency"], "legacy")
        self.assertEqual(summary["installed_version"], "unknown")

    def test_invalid_env_version_is_not_executed_or_disclosed(self) -> None:
        sentinel = self.root / "must-not-exist"
        payload = f"$(touch {sentinel})"
        (self.target / ".env").write_text(
            f"RELEASE_VERSION={payload}\nJWT_SECRET={SECRET}\n", encoding="utf-8"
        )
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("status=invalid", result.stderr)
        self.assertNotIn(payload, result.stdout + result.stderr)
        self.assertFalse(sentinel.exists())
        result, summary, text = self.collect_summary()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(summary["environment_version"], "unknown")
        self.assertEqual(summary["version_consistency"], "invalid")
        self.assertNotIn(payload, text)
        self.assertFalse(sentinel.exists())

    def test_missing_runtime_release_version_is_unknown_and_critical(self) -> None:
        (self.target / ".env").write_text(f"JWT_SECRET={SECRET}\n", encoding="utf-8")
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("status=unknown", result.stderr)

    def test_missing_checker_cannot_pass_healthy_self_test(self) -> None:
        (self.target / "scripts/verify_release_state.py").unlink()
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("status=checker_missing", result.stderr)
        result, summary, _ = self.collect_summary()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(summary["version"], "3.1.40")
        self.assertEqual(summary["version_consistency"], "checker_missing")

    def test_export_preserves_version_field_and_adds_all_sources(self) -> None:
        result, summary, text = self.collect_summary()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(summary["version"], "3.1.40")
        for key in (
            "file_version", "installed_version", "environment_version",
            "template_version", "frontend_build_version",
        ):
            self.assertEqual(summary[key], "3.1.40", key)
            self.assertIn(f"{key}=3.1.40\n", text)
        self.assertEqual(summary["version_consistency"], "consistent")
        self.assertEqual(summary["version_check_rc"], "0")
        self.assertEqual(summary["critical_failure"], "0")

    def test_mismatch_export_does_not_overwrite_old_file_version(self) -> None:
        (self.target / "VERSION").write_text("3.1.32\n", encoding="utf-8")
        result, summary, _ = self.collect_summary()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(summary["version"], "3.1.32")
        self.assertEqual(summary["file_version"], "3.1.32")
        self.assertEqual(summary["installed_version"], "3.1.40")
        self.assertEqual(summary["environment_version"], "3.1.40")
        self.assertEqual(summary["version_consistency"], "mismatch")
        self.assertEqual(summary["critical_failure"], "1")

    def test_malformed_helper_output_is_not_echoed(self) -> None:
        (self.target / "scripts/verify_release_state.py").write_text(
            f"print({SECRET!r})\n", encoding="utf-8"
        )
        result = self.run_diagnostic("--self-test")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("status=checker_failed", result.stderr)


if __name__ == "__main__":
    unittest.main()
