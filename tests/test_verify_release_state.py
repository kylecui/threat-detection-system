#!/usr/bin/env python3
"""Isolated verification tests; never package or operate on a live install."""

from __future__ import annotations

import hashlib
import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
HELPER = ROOT / "scripts/verify_release_state.py"
VERSION = "3.1.39"


class ReleaseStateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="tds-release-state-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / "release"
        self.root.mkdir()
        (self.root / "frontend").mkdir()
        (self.root / "VERSION").write_text(VERSION + "\n", encoding="utf-8")
        (self.root / ".env.example").write_text(
            f"RELEASE_VERSION={VERSION}\nJWT_SECRET=fixture-secret-not-for-output\n",
            encoding="utf-8",
        )
        (self.root / "frontend/.env.production").write_text(
            f"VITE_APP_VERSION={VERSION}\n", encoding="utf-8"
        )
        (self.root / "README.md").write_text("old content\n", encoding="utf-8")
        self.managed = ["VERSION", ".env.example", "frontend/.env.production", "README.md"]
        self.trusted = self.base / "trusted.sha256"
        self.write_manifest()

    def run_helper(self, *arguments: object) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(HELPER), *(str(value) for value in arguments)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=False,
        )

    def write_manifest(self) -> None:
        data = "".join(
            f"{hashlib.sha256((self.root / relative).read_bytes()).hexdigest()}  {relative}\n"
            for relative in self.managed
        )
        (self.root / "MANIFEST.sha256").write_text(data, encoding="utf-8")
        self.trusted.write_text(data, encoding="utf-8")

    def metadata(self, version: str = VERSION) -> None:
        (self.root / ".installed_version").write_text(version + "\n", encoding="utf-8")
        (self.root / ".env").write_text(
            f"RELEASE_VERSION={version}\nJWT_SECRET=fixture-secret-not-for-output\n",
            encoding="utf-8",
        )

    def verify(self, *extra: str) -> subprocess.CompletedProcess[str]:
        return self.run_helper(
            "verify-release", self.root, "--manifest", self.trusted, "--expected", VERSION, *extra
        )

    def assert_rejected(self, result: subprocess.CompletedProcess[str]) -> None:
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("fixture-secret-not-for-output", result.stdout + result.stderr)

    def test_source_contents_and_all_metadata_pass(self) -> None:
        self.assertEqual(self.verify().returncode, 0)
        self.metadata()
        result = self.verify("--metadata")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_equal_size_equal_time_changed_content_is_rejected(self) -> None:
        source = self.root / "README.md"
        os.utime(source, ns=(0, 0))
        source.write_text("new content\n", encoding="utf-8")
        os.utime(source, ns=(0, 0))
        result = self.verify()
        self.assert_rejected(result)
        self.assertIn("checksum mismatch: README.md", result.stderr)

    def test_missing_managed_file_is_rejected(self) -> None:
        (self.root / "README.md").unlink()
        self.assert_rejected(self.verify())

    def test_target_manifest_cannot_substitute_its_own_new_checksums(self) -> None:
        (self.root / "README.md").write_text("new content\n", encoding="utf-8")
        trusted_data = self.trusted.read_bytes()
        self.write_manifest()
        self.trusted.write_bytes(trusted_data)
        result = self.verify()
        self.assert_rejected(result)
        self.assertIn("differs from the trusted", result.stderr)

    def test_runtime_metadata_is_checked_only_after_installation(self) -> None:
        self.assertEqual(self.verify().returncode, 0)
        self.assert_rejected(self.verify("--metadata"))
        self.metadata("3.1.32")
        self.assert_rejected(self.verify("--metadata"))

    def test_prepared_runtime_env_is_checked_before_installation_marker(self) -> None:
        prepared = self.base / "prepared.env"
        prepared.write_text(
            f"export RELEASE_VERSION='{VERSION}'\nJWT_SECRET=fixture-secret-not-for-output\n",
            encoding="utf-8",
        )
        result = self.verify("--runtime-env", str(prepared))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / ".installed_version").exists())
        self.assertFalse((self.root / ".env").exists())
        prepared.write_text("RELEASE_VERSION=3.1.32\n", encoding="utf-8")
        self.assert_rejected(self.verify("--runtime-env", str(prepared)))
        prepared.write_text(
            "RELEASE_VERSION=$(fixture-secret-not-for-output)\n", encoding="utf-8"
        )
        self.assert_rejected(self.verify("--runtime-env", str(prepared)))
        self.assertEqual(prepared.read_text(encoding="utf-8"), "RELEASE_VERSION=$(fixture-secret-not-for-output)\n")

    def test_template_and_frontend_metadata_must_match_expected(self) -> None:
        for name, key in ((".env.example", "RELEASE_VERSION"), ("frontend/.env.production", "VITE_APP_VERSION")):
            with self.subTest(name=name):
                target = self.root / name
                old = target.read_bytes()
                target.write_text(f"{key}=3.1.32\n", encoding="utf-8")
                self.write_manifest()
                self.assert_rejected(self.verify())
                target.write_bytes(old)
                self.write_manifest()

    def test_unsafe_and_generated_manifest_paths_are_rejected(self) -> None:
        for relative in ("../outside", "/etc/passwd", "folder/../VERSION", "folder//VERSION", "C:/Windows", "folder\\VERSION", ".env", ".installed_version", "MANIFEST.sha256"):
            with self.subTest(relative=relative):
                data = "0" * 64 + "  " + relative + "\n"
                self.trusted.write_text(data, encoding="utf-8")
                (self.root / "MANIFEST.sha256").write_text(data, encoding="utf-8")
                self.assert_rejected(self.verify())

    def test_duplicate_and_missing_required_manifest_entries_are_rejected(self) -> None:
        data = self.trusted.read_text(encoding="utf-8")
        self.trusted.write_text(data + data.splitlines()[0] + "\n", encoding="utf-8")
        (self.root / "MANIFEST.sha256").write_bytes(self.trusted.read_bytes())
        self.assert_rejected(self.verify())
        self.managed.remove("VERSION")
        self.write_manifest()
        self.assert_rejected(self.verify())

    def make_symlink(self, destination: Path, target: Path, *, directory: bool = False) -> None:
        try:
            destination.symlink_to(target, target_is_directory=directory)
        except (OSError, NotImplementedError) as exc:
            self.skipTest(f"Symlinks unavailable on this host: {type(exc).__name__}")

    def test_managed_symlink_and_symlink_parent_are_rejected(self) -> None:
        original = self.root / "README.md"
        outside = self.base / "outside"
        outside.write_bytes(original.read_bytes())
        original.unlink()
        self.make_symlink(original, outside)
        self.assert_rejected(self.verify())
        original.unlink()
        original.write_bytes(outside.read_bytes())
        frontend = self.root / "frontend"
        moved = self.base / "frontend-real"
        frontend.rename(moved)
        self.make_symlink(frontend, moved, directory=True)
        self.assert_rejected(self.verify())

    def test_version_report_consistent_and_legacy(self) -> None:
        self.metadata()
        result = self.run_helper("versions", self.root)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["version_consistency"], "consistent")
        (self.root / ".installed_version").unlink()
        legacy = self.run_helper("versions", self.root)
        self.assertEqual(legacy.returncode, 0, legacy.stderr)
        data = json.loads(legacy.stdout)
        self.assertEqual(data["version_consistency"], "legacy")
        self.assertIsNone(data["installed_version"])
        self.assertEqual(data["version_states"]["installed_version"], "missing")
        self.assertNotEqual(self.run_helper("versions", self.root, "--strict").returncode, 0)

    def test_version_report_unknown_and_mismatch_are_nonzero_json(self) -> None:
        unknown = self.run_helper("versions", self.root)
        self.assertEqual(unknown.returncode, 2)
        self.assertEqual(json.loads(unknown.stdout)["version_consistency"], "unknown")
        self.metadata("3.1.32")
        mismatch = self.run_helper("versions", self.root)
        self.assertEqual(mismatch.returncode, 2)
        self.assertEqual(json.loads(mismatch.stdout)["version_consistency"], "mismatch")

    def test_version_parser_supports_export_quotes_comments_and_crlf(self) -> None:
        self.metadata()
        (self.root / ".env").write_bytes(
            b'# unrelated values are not parsed\r\nJWT_SECRET=$(never-execute)\r\nexport RELEASE_VERSION = "3.1.39" # version only\r\n'
        )
        result = self.run_helper("versions", self.root)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["environment_version"], VERSION)
        self.assertNotIn("never-execute", result.stdout + result.stderr)

    def test_invalid_version_is_redacted_and_never_executed(self) -> None:
        self.metadata()
        marker = self.base / "must-not-exist"
        payload = f"$(touch {marker})-fixture-secret-not-for-output"
        (self.root / ".env").write_text(f"RELEASE_VERSION={payload}\n", encoding="utf-8")
        result = self.run_helper("versions", self.root)
        self.assertEqual(result.returncode, 2)
        data = json.loads(result.stdout)
        self.assertEqual(data["version_consistency"], "invalid")
        self.assertIsNone(data["environment_version"])
        self.assertNotIn(payload, result.stdout + result.stderr)
        self.assertFalse(marker.exists())

    def test_duplicate_version_keys_and_multiline_markers_are_invalid(self) -> None:
        self.metadata()
        (self.root / ".env").write_text(
            f"RELEASE_VERSION={VERSION}\nRELEASE_VERSION={VERSION}\n", encoding="utf-8"
        )
        result = self.run_helper("versions", self.root)
        self.assertEqual(json.loads(result.stdout)["version_consistency"], "invalid")
        self.metadata()
        (self.root / "VERSION").write_text("3.1.\n39\n", encoding="utf-8")
        self.assertEqual(json.loads(self.run_helper("versions", self.root).stdout)["file_version"], None)

    def test_snapshot_records_actual_tree_including_local_files_and_empty_directory(self) -> None:
        self.metadata()
        (self.root / "local-custom-file").write_text("operator setting\n", encoding="utf-8")
        (self.root / "empty-directory").mkdir()
        # A historically wrong package manifest must not become the restore baseline.
        (self.root / "MANIFEST.sha256").write_text("wrong old manifest\n", encoding="utf-8")
        snapshot = self.base / "snapshot.json"
        result = self.run_helper("snapshot", self.root, snapshot)
        self.assertEqual(result.returncode, 0, result.stderr)
        data = json.loads(snapshot.read_text(encoding="utf-8"))
        self.assertIn("local-custom-file", data["entries"])
        self.assertIn("empty-directory", data["entries"])
        self.assertEqual(self.run_helper("verify-snapshot", self.root, snapshot).returncode, 0)
        if os.name == "posix":
            self.assertEqual(stat.S_IMODE(snapshot.stat().st_mode), 0o600)

    def test_snapshot_detects_equal_metadata_content_change(self) -> None:
        snapshot = self.base / "snapshot.json"
        self.assertEqual(self.run_helper("snapshot", self.root, snapshot).returncode, 0)
        target = self.root / "README.md"
        before = target.stat()
        target.write_text("new content\n", encoding="utf-8")
        os.utime(target, ns=(before.st_atime_ns, before.st_mtime_ns))
        self.assert_rejected(self.run_helper("verify-snapshot", self.root, snapshot))

    def test_snapshot_detects_missing_or_extra_paths(self) -> None:
        snapshot = self.base / "snapshot.json"
        self.assertEqual(self.run_helper("snapshot", self.root, snapshot).returncode, 0)
        (self.root / "extra").write_text("extra", encoding="utf-8")
        self.assert_rejected(self.run_helper("verify-snapshot", self.root, snapshot))
        (self.root / "extra").unlink()
        (self.root / "README.md").unlink()
        self.assert_rejected(self.run_helper("verify-snapshot", self.root, snapshot))

    @unittest.skipUnless(os.name == "posix", "POSIX modes are deployment attributes")
    def test_snapshot_detects_mode_change(self) -> None:
        snapshot = self.base / "snapshot.json"
        self.assertEqual(self.run_helper("snapshot", self.root, snapshot).returncode, 0)
        target = self.root / "README.md"
        target.chmod(stat.S_IMODE(target.stat().st_mode) ^ stat.S_IXUSR)
        self.assert_rejected(self.run_helper("verify-snapshot", self.root, snapshot))

    def test_snapshot_records_symlink_without_following_it(self) -> None:
        outside = self.base / "external-secret"
        outside.write_text("fixture-secret-not-for-output", encoding="utf-8")
        link = self.root / "external-link"
        self.make_symlink(link, outside)
        snapshot = self.base / "snapshot.json"
        self.assertEqual(self.run_helper("snapshot", self.root, snapshot).returncode, 0)
        data = json.loads(snapshot.read_text(encoding="utf-8"))
        self.assertEqual(data["entries"]["external-link"]["type"], "symlink")
        self.assertNotIn("sha256", data["entries"]["external-link"])
        self.assertNotIn("fixture-secret-not-for-output", snapshot.read_text(encoding="utf-8"))

    def test_snapshot_represents_absent_external_path(self) -> None:
        missing = self.base / "not-installed"
        snapshot = self.base / "snapshot.json"
        self.assertEqual(self.run_helper("snapshot", missing, snapshot).returncode, 0)
        self.assertEqual(self.run_helper("verify-snapshot", missing, snapshot).returncode, 0)
        missing.write_text("new external diagnostic", encoding="utf-8")
        self.assert_rejected(self.run_helper("verify-snapshot", missing, snapshot))

    def test_snapshot_rejects_metadata_inside_its_own_tree_and_invalid_metadata(self) -> None:
        self.assert_rejected(self.run_helper("snapshot", self.root, self.root / "snapshot.json"))
        snapshot = self.base / "invalid.json"
        snapshot.write_text('{"secret":"fixture-secret-not-for-output"}', encoding="utf-8")
        self.assert_rejected(self.run_helper("verify-snapshot", self.root, snapshot))

    @unittest.skipUnless(os.name == "posix" and hasattr(os, "mkfifo"), "POSIX special-file fixture")
    def test_snapshot_rejects_unsupported_special_file(self) -> None:
        os.mkfifo(self.root / "named-pipe")
        self.assert_rejected(self.run_helper("snapshot", self.root, self.base / "snapshot.json"))


if __name__ == "__main__":
    unittest.main()
