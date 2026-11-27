#!/usr/bin/env python3
"""Verify release contents, version metadata and actual rollback snapshots.

Only release-version keys are read from environment files. Configuration is
never evaluated, and errors never include configuration values or file data.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
from pathlib import Path, PurePosixPath


VERSION_RE = re.compile(r"[0-9]+(?:\.[0-9]+)*(?:[+~-][0-9A-Za-z.-]+)?\Z")
MANIFEST_RE = re.compile(r"([0-9a-fA-F]{64})  (.+)\Z")
GENERATED_FILES = {".env", ".installed_version", "MANIFEST.sha256"}


class VerificationError(Exception):
    """An error whose message is safe to include in installer output."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def regular_file(path: Path) -> bool:
    try:
        return stat.S_ISREG(path.lstat().st_mode)
    except FileNotFoundError:
        return False


def version_value(path: Path, key: str | None = None) -> tuple[str | None, str]:
    """Return only a validated version and a valid/missing/invalid state."""
    try:
        if not regular_file(path):
            return None, "invalid" if path.is_symlink() or path.exists() else "missing"
        if key is None:
            value = path.read_text(encoding="utf-8").strip()
        else:
            values: list[str] = []
            pattern = re.compile(rf"(?:export\s+)?{re.escape(key)}\s*=\s*(.*)\Z")
            with path.open(encoding="utf-8") as stream:
                for raw in stream:
                    match = pattern.fullmatch(raw.strip())
                    if not match:
                        continue
                    candidate = match[1].strip()
                    if candidate.startswith(("'", '"')):
                        quote = candidate[0]
                        end = candidate.find(quote, 1)
                        if end < 0:
                            return None, "invalid"
                        remainder = candidate[end + 1 :].strip()
                        if remainder and not remainder.startswith("#"):
                            return None, "invalid"
                        candidate = candidate[1:end]
                    else:
                        candidate = re.split(r"\s+#", candidate, maxsplit=1)[0].strip()
                    values.append(candidate)
            if not values:
                return None, "missing"
            if len(values) != 1:
                return None, "invalid"
            value = values[0]
        if not VERSION_RE.fullmatch(value):
            return None, "invalid"
        return value, "valid"
    except (OSError, UnicodeError):
        return None, "invalid"


def versions(root: Path) -> dict[str, object]:
    sources = {
        "file_version": (root / "VERSION", None),
        "installed_version": (root / ".installed_version", None),
        "environment_version": (root / ".env", "RELEASE_VERSION"),
        "template_version": (root / ".env.example", "RELEASE_VERSION"),
        "frontend_build_version": (root / "frontend/.env.production", "VITE_APP_VERSION"),
    }
    values: dict[str, str | None] = {}
    states: dict[str, str] = {}
    for name, (path, key) in sources.items():
        values[name], states[name] = version_value(path, key)
    if "invalid" in states.values():
        consistency = "invalid"
    elif len({value for value in values.values() if value is not None}) > 1:
        consistency = "mismatch"
    elif states["file_version"] != "valid" or states["environment_version"] != "valid":
        consistency = "unknown"
    elif states["installed_version"] == "missing":
        consistency = "legacy"
    else:
        consistency = "consistent"
    return {**values, "version_consistency": consistency, "version_states": states}


def managed_path(root: Path, relative: str) -> Path:
    path = PurePosixPath(relative)
    if (
        not relative
        or path.is_absolute()
        or path.as_posix() != relative
        or any(part in {".", ".."} for part in path.parts)
        or "\\" in relative
        or ":" in relative
        or any(ord(character) < 32 for character in relative)
        or relative in GENERATED_FILES
    ):
        raise VerificationError("Manifest contains an unsafe or generated-file path")
    current = root
    if current.is_symlink() or not current.is_dir():
        raise VerificationError("Release root is not a regular directory")
    for part in path.parts:
        current = current / part
        if current.is_symlink():
            raise VerificationError(f"Managed release path is a symlink: {relative}")
    if not regular_file(current):
        raise VerificationError(f"Managed release file is missing or not regular: {relative}")
    return current


def verify_release(
    root: Path,
    manifest: Path,
    expected: str,
    metadata: bool = False,
    runtime_env: Path | None = None,
) -> None:
    if not VERSION_RE.fullmatch(expected):
        raise VerificationError("Expected release version is invalid")
    if not regular_file(manifest):
        raise VerificationError("Trusted release manifest is missing or not regular")
    target_manifest = root / "MANIFEST.sha256"
    if not regular_file(target_manifest):
        raise VerificationError("Target manifest is missing or not regular")
    trusted_bytes = manifest.read_bytes()
    if target_manifest.read_bytes() != trusted_bytes:
        raise VerificationError("Target manifest differs from the trusted release manifest")
    entries = trusted_bytes.decode("utf-8").splitlines()
    if not entries:
        raise VerificationError("Trusted release manifest is empty")
    names: set[str] = set()
    for line in entries:
        match = MANIFEST_RE.fullmatch(line)
        if not match:
            raise VerificationError("Trusted release manifest contains an invalid entry")
        checksum, relative = match.groups()
        if relative in names:
            raise VerificationError("Trusted release manifest contains duplicate paths")
        names.add(relative)
        if sha256(managed_path(root, relative)) != checksum.lower():
            raise VerificationError(f"Managed release checksum mismatch: {relative}")
    for relative in ("VERSION", ".env.example"):
        if relative not in names:
            raise VerificationError("Trusted manifest omits mandatory version metadata")
    sources = [(root / "VERSION", None), (root / ".env.example", "RELEASE_VERSION")]
    frontend = root / "frontend/.env.production"
    if frontend.exists() or frontend.is_symlink():
        if "frontend/.env.production" not in names:
            raise VerificationError("Trusted manifest omits frontend version metadata")
        sources.append((frontend, "VITE_APP_VERSION"))
    if metadata:
        sources.extend([(root / ".installed_version", None), (root / ".env", "RELEASE_VERSION")])
    if runtime_env is not None:
        sources.append((runtime_env, "RELEASE_VERSION"))
    for path, key in sources:
        value, state = version_value(path, key)
        if state != "valid" or value != expected:
            label = key or path.name
            raise VerificationError(f"Release version metadata is missing, invalid or inconsistent: {label}")


def capture_snapshot(path: Path) -> dict[str, object]:
    entries: dict[str, dict[str, object]] = {}

    def visit(current: Path, relative: str) -> None:
        info = current.lstat()
        entry: dict[str, object] = {
            "mode": stat.S_IMODE(info.st_mode),
            "uid": info.st_uid,
            "gid": info.st_gid,
            "mtime_ns": info.st_mtime_ns,
        }
        if stat.S_ISLNK(info.st_mode):
            entry.update(type="symlink", target=os.readlink(current))
        elif getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0):
            raise VerificationError("Snapshot contains an unsupported filesystem reparse point")
        elif stat.S_ISREG(info.st_mode):
            entry.update(type="file", sha256=sha256(current))
        elif stat.S_ISDIR(info.st_mode):
            entry.update(type="directory")
        else:
            raise VerificationError("Snapshot contains an unsupported special file")
        entries[relative] = entry
        if entry["type"] == "directory":
            for child in sorted(current.iterdir(), key=lambda item: item.name):
                child_relative = child.name if relative == "." else f"{relative}/{child.name}"
                visit(child, child_relative)

    try:
        path.lstat()
    except FileNotFoundError:
        return {"format": 1, "exists": False, "entries": {}}
    visit(path, ".")
    return {"format": 1, "exists": True, "entries": entries}


def write_snapshot(path: Path, output: Path) -> None:
    # Never put recovery metadata inside the tree being recorded or overwrite it.
    if output.absolute() == path.absolute() or (
        path.is_dir() and not path.is_symlink() and output.resolve().is_relative_to(path.resolve())
    ):
        raise VerificationError("Snapshot metadata must be outside the recorded path")
    data = capture_snapshot(path)
    descriptor, temporary = tempfile.mkstemp(prefix=".release-state-", dir=output.parent)
    temporary_path = Path(temporary)
    try:
        os.chmod(temporary_path, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, output)
    finally:
        temporary_path.unlink(missing_ok=True)


def verify_snapshot(path: Path, source: Path) -> None:
    if not regular_file(source):
        raise VerificationError("Snapshot metadata is missing or not regular")
    expected = json.loads(source.read_text(encoding="utf-8"))
    if (
        not isinstance(expected, dict)
        or expected.get("format") != 1
        or not isinstance(expected.get("exists"), bool)
        or not isinstance(expected.get("entries"), dict)
    ):
        raise VerificationError("Snapshot metadata format is invalid")
    actual = capture_snapshot(path)
    if actual != expected:
        raise VerificationError("Restored path differs from the actual pre-install snapshot")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    release = commands.add_parser("verify-release")
    release.add_argument("root", type=Path)
    release.add_argument("--manifest", type=Path, required=True)
    release.add_argument("--expected", required=True)
    release.add_argument("--metadata", action="store_true")
    release.add_argument("--runtime-env", type=Path)
    version = commands.add_parser("versions")
    version.add_argument("root", type=Path)
    version.add_argument("--strict", action="store_true")
    snapshot = commands.add_parser("snapshot")
    snapshot.add_argument("path", type=Path)
    snapshot.add_argument("output", type=Path)
    restore = commands.add_parser("verify-snapshot")
    restore.add_argument("path", type=Path)
    restore.add_argument("source", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "versions":
            report = versions(args.root)
            print(json.dumps(report, ensure_ascii=False, sort_keys=True))
            allowed = {"consistent"} if args.strict else {"consistent", "legacy"}
            return 0 if report["version_consistency"] in allowed else 2
        if args.command == "verify-release":
            verify_release(args.root, args.manifest, args.expected, args.metadata, args.runtime_env)
            print("Release contents and version metadata verified")
        elif args.command == "snapshot":
            write_snapshot(args.path, args.output)
            print("Actual pre-install snapshot metadata recorded")
        else:
            verify_snapshot(args.path, args.source)
            print("Restored path matches the actual pre-install snapshot")
        return 0
    except VerificationError as exc:
        print(f"[ERR] {exc}", file=sys.stderr)
    except (OSError, UnicodeError, ValueError, KeyError, TypeError):
        print("[ERR] Release state verification failed; configuration values were not printed", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
