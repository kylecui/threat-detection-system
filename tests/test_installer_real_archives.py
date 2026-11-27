"""Upgrade and roll back real delivered archives in the guarded rsync fixture.

Set TDS_TEST_RELEASE_ARCHIVES_DIR to a directory containing the historical
tarballs. Missing historical artifacts are explicit skips, never downloads.
No installer main, Docker, systemd or delivered output is executed or changed.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tarfile
import time
import unittest
from pathlib import Path

from tests import test_installer_transaction as transaction


ROOT = transaction.ROOT
ARCHIVES = Path(os.environ.get("TDS_TEST_RELEASE_ARCHIVES_DIR", ROOT / "outputs"))


@unittest.skipUnless(transaction.POSIX_RUNTIME, "requires isolated POSIX bash and real rsync")
class HistoricalArchiveTransactionTests(unittest.TestCase):
    def prepare_case(self, old: str) -> tuple[transaction.InstallerTransactionTests, Path]:
        name = f"threat-detection-system-{old}-linux-x86_64.tar.gz"
        archive = ARCHIVES / name
        if not archive.is_file():
            archive = ARCHIVES / f"v{old}" / name
        if not archive.is_file():
            self.skipTest(f"historical archive unavailable: {name}")
        case = transaction.InstallerTransactionTests("test_source_equals_target_avoids_self_copy_but_still_checks_content")
        case.setUp()
        self.addCleanup(case.doCleanups)
        specification = importlib.util.spec_from_file_location("historical_test_packer", ROOT / "scripts/pack_release.py")
        assert specification is not None and specification.loader is not None
        packer = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(packer)
        case.source.mkdir()
        files = packer.release_files()
        packer.copy_release_tree(case.source, files)
        packer.write_manifest(case.source, files)
        case.stamp(case.source, 0)
        extraction = case.root / "historical"
        extraction.mkdir()
        prefix = name.removesuffix(".tar.gz")
        with tarfile.open(archive, "r:gz") as stream:
            members = stream.getmembers()
            self.assertTrue(all(member.name == prefix or member.name.startswith(prefix + "/") for member in members))
            self.assertTrue(all(member.mtime == 0 for member in members))
            self.assertTrue(all(member.isfile() or member.isdir() for member in members))
            stream.extractall(extraction, filter="data")
        shutil.move(extraction / prefix, case.target)
        control_home = case.root / "host-control"
        (control_home / "tls").mkdir(parents=True)
        (control_home / "control.env").write_text("JZ_CONTROL_TLS_DIR=/fixture/tls\n", encoding="utf-8")
        (control_home / "tls/client.p12").write_bytes(b"synthetic fixture; never used by Docker")
        # Generate only synthetic credentials using the actual historical helper.
        subprocess.run(
            [sys.executable, str(case.target / "scripts/prepare_release_env.py"),
             str(case.target / ".env.example"), str(case.target / ".env")],
            check=True, capture_output=True, text=True,
        )
        with (case.target / ".env").open("a", encoding="utf-8") as stream:
            stream.write("\nCUSTOM_OPERATOR_SETTING=historical-archive-test\n")
        (case.target / ".installed_version").write_text(old + "\n", encoding="utf-8")
        for external in case.paths:
            original = case.target / ("scripts/tdsdiag.sh" if external.name == "tdsdiag" else f"deploy/systemd/{external.name}")
            if original.is_file():
                shutil.copy2(original, external)
        return case, archive

    @staticmethod
    def environment(path: Path) -> dict[str, str]:
        return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                    if "=" in line and not line.startswith("#"))

    def record(self, old: str, action: str, archive: Path, case: transaction.InstallerTransactionTests,
               elapsed: float, files: int) -> None:
        output = os.environ.get("TDS_TEST_EVIDENCE_DIR")
        if output:
            directory = Path(output)
            directory.mkdir(parents=True, exist_ok=True)
            report = {
                "old_version": old, "candidate_version": (case.source / "VERSION").read_text().strip(),
                "archive_name": archive.name, "archive_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                "action": action, "managed_file_count": files, "result": "PASS", "elapsed_seconds": round(elapsed, 3),
                "scope": "private temporary tree; real bash/rsync; recording Docker/systemd stubs; synthetic credentials",
            }
            (directory / f"historical-{old}-{action}.json").write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
            )

    def upgrade(self, old: str) -> None:
        started = time.monotonic()
        case, archive = self.prepare_case(old)
        before = self.environment(case.target / ".env")
        control_home = case.root / "host-control"
        control_before = transaction.tree_state(control_home)
        result = case.run_shell("detect_existing_install\nprepare_runtime_env\nprepare_rollback\nsync_release_tree\ncommit_install\ncleanup_temp\n")
        case.assert_ok(result)
        expected = (case.source / "VERSION").read_text().strip()
        self.assertEqual((case.target / "VERSION").read_text().strip(), expected)
        self.assertEqual((case.target / ".installed_version").read_text().strip(), expected)
        after = self.environment(case.target / ".env")
        self.assertEqual(after["RELEASE_VERSION"], expected)
        for key in ("POSTGRES_PASSWORD", "JWT_SECRET", "CONFIG_ENCRYPTION_KEY", "TIRE_FERNET_KEY",
                    "TIRE_SESSION_SECRET", "TIRE_INTERNAL_SERVICE_TOKEN", "ALERT_INTERNAL_SERVICE_TOKEN",
                    "CUSTOM_OPERATOR_SETTING"):
            if key in before:
                self.assertTrue(after.get(key) == before[key], f"operator value was not retained: {key}")
        entries = (case.source / "MANIFEST.sha256").read_text().splitlines()
        self.assertEqual((case.target / "MANIFEST.sha256").read_bytes(), (case.source / "MANIFEST.sha256").read_bytes())
        for line in entries:
            expected_hash, relative = line.split("  ", 1)
            self.assertEqual(hashlib.sha256((case.target / relative).read_bytes()).hexdigest(), expected_hash, relative)
        self.assertIn("docker/docker-compose.control.yml", {
            line.split("  ", 1)[1] for line in entries
        })
        self.assertTrue((case.target / "scripts/compose-runtime.sh").is_file())
        self.assertEqual(transaction.tree_state(control_home), control_before)
        for external in case.paths:
            relative = "scripts/tdsdiag.sh" if external.name == "tdsdiag" else f"deploy/systemd/{external.name}"
            self.assertEqual(external.read_bytes(), (case.source / relative).read_bytes())
        self.assertFalse(list(case.root.glob("rollback.*")))
        self.record(old, "upgrade", archive, case, time.monotonic() - started, len(entries))

    def rollback(self, old: str) -> None:
        started = time.monotonic()
        case, archive = self.prepare_case(old)
        # Deliberately diverge from its historical manifest. The actual tree must win.
        (case.target / "local-only.txt").write_text("operator local state\n", encoding="utf-8")
        before = transaction.tree_state(case.target)
        external = [transaction.tree_state(path) for path in case.paths]
        control_home = case.root / "host-control"
        control_before = transaction.tree_state(control_home)
        result = case.run_shell("prepare_runtime_env\nprepare_rollback\nsync_release_tree\nrollback_install 42\ncleanup_temp\n")
        case.assert_ok(result)
        self.assertEqual(transaction.tree_state(case.target), before)
        self.assertEqual([transaction.tree_state(path) for path in case.paths], external)
        self.assertEqual(transaction.tree_state(control_home), control_before)
        self.assertFalse(list(case.root.glob("rollback.*")))
        self.assertNotRegex((case.root / "commands.log").read_text(), r"compose .* down -v")
        entries = (case.source / "MANIFEST.sha256").read_text().splitlines()
        self.record(old, "rollback", archive, case, time.monotonic() - started, len(entries))

    def test_real_3_1_32_upgrade(self) -> None:
        self.upgrade("3.1.32")

    def test_real_3_1_38_upgrade(self) -> None:
        self.upgrade("3.1.38")

    def test_real_3_1_32_rollback(self) -> None:
        self.rollback("3.1.32")

    def test_real_3_1_38_rollback(self) -> None:
        self.rollback("3.1.38")


if __name__ == "__main__":
    unittest.main()
