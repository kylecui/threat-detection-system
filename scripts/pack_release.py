#!/usr/bin/env python3
"""Build the source-based production release archive.

The output intentionally contains only files required to validate, build,
install, run and diagnose the Docker Compose deployment.
"""

from __future__ import annotations

import gzip
import hashlib
import shutil
import tarfile
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
PACKAGE_NAME = f"threat-detection-system-{VERSION}-linux-x86_64"
OUTPUT_DIR = ROOT / "outputs" / f"v{VERSION}"

TOP_LEVEL_FILES = {
    ".dockerignore",
    ".env.example",
    "README.md",
    "VERSION",
    "install.sh",
    "maven-settings.xml",
    "pom.xml",
}
JAVA_SERVICES = {
    "alert-management",
    "api-gateway",
    "config-server",
    "customer-management",
    "data-ingestion",
    "stream-processing",
    "threat-assessment",
    "threat-intelligence",
}
FRONTEND_FILES = {
    "frontend/.dockerignore",
    "frontend/.env.production",
    "frontend/Dockerfile",
    "frontend/default.conf",
    "frontend/index.html",
    "frontend/nginx.conf",
    "frontend/package-lock.json",
    "frontend/package.json",
    "frontend/tsconfig.json",
    "frontend/tsconfig.node.json",
    "frontend/vite.config.ts",
}
LOGSTASH_FILES = {
    "infrastructure/logging/logstash/Dockerfile",
    "infrastructure/logging/logstash/logstash.yml",
}
INSTALL_FILES = {
    "deploy/systemd/threat-detection-compose.service",
    "deploy/systemd/threat-detection-runtime-watchdog.service",
    "deploy/systemd/threat-detection-runtime-watchdog.timer",
    "scripts/prepare_release_env.py",
    "scripts/verify_release_state.py",
    "scripts/runtime-watchdog.sh",
    "scripts/tdsdiag.sh",
}
EXECUTABLE_FILES = {
    "install.sh",
    "scripts/prepare_release_env.py",
    "scripts/verify_release_state.py",
    "scripts/runtime-watchdog.sh",
    "scripts/tdsdiag.sh",
}

SKIP_DIRS = {
    ".git",
    ".idea",
    ".pytest_cache",
    ".vscode",
    "__pycache__",
    "__tests__",
    "coverage",
    "dist",
    "docs",
    "e2e",
    "node_modules",
    "playwright-report",
    "target",
    "test-results",
    "test",
    "tests",
}
SKIP_SUFFIXES = (
    ".bak", ".backup", ".NOUSE", ".old", ".old.old", ".log", ".tmp", ".db",
    ".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx",
)


def should_include(relative: Path) -> bool:
    parts = relative.parts
    if any(part in SKIP_DIRS for part in parts):
        return False
    if relative.name == ".env" or relative.name.startswith(".env.local"):
        return False
    if relative.name.endswith(SKIP_SUFFIXES):
        return False
    return True


def iter_tree(relative_root: str):
    source_root = ROOT / relative_root
    if not source_root.is_dir():
        raise FileNotFoundError(f"required release directory is missing: {relative_root}")
    for source in sorted(source_root.rglob("*")):
        if source.is_file():
            relative = source.relative_to(ROOT)
            if should_include(relative):
                yield relative


def compose_runtime_files() -> set[Path]:
    compose = ROOT / "docker" / "docker-compose.yml"
    required = {Path("docker/docker-compose.yml")}
    for line in compose.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped.startswith("- ./") or ":/docker-entrypoint-initdb.d/" not in stripped:
            continue
        relative_name = stripped[4:].split(":", 1)[0]
        required.add(Path("docker") / relative_name)
    return required


def release_files() -> list[Path]:
    selected = {
        Path(item)
        for item in TOP_LEVEL_FILES | INSTALL_FILES | FRONTEND_FILES | LOGSTASH_FILES
    }
    selected.update(compose_runtime_files())
    for tree in (
        "config-repo",
        "frontend/public",
        "frontend/src",
        "infrastructure/logging/logstash/pipeline",
        "services/ml-detection/app",
        "services/ml-detection/models",
        "services/tire",
    ):
        selected.update(iter_tree(tree))
    selected.update(
        {
            Path("services/ml-detection/Dockerfile"),
            Path("services/ml-detection/requirements-runtime.txt"),
        }
    )
    for service in JAVA_SERVICES:
        selected.add(Path("services") / service / "Dockerfile")
        selected.add(Path("services") / service / "pom.xml")
        selected.update(iter_tree(f"services/{service}/src/main"))
    missing = sorted(str(path) for path in selected if not (ROOT / path).is_file())
    if missing:
        raise FileNotFoundError("required release files are missing: " + ", ".join(missing))
    return sorted(selected, key=lambda path: path.as_posix())


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_mode(path: Path, relative: Path) -> None:
    executable = relative.as_posix() in EXECUTABLE_FILES
    path.chmod(0o755 if executable else 0o644)


def copy_release_tree(destination: Path, files: list[Path]) -> None:
    for relative in files:
        source = ROOT / relative
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        normalize_mode(target, relative)


def write_manifest(package_root: Path, files: list[Path]) -> None:
    entries = [f"{sha256(package_root / relative)}  {relative.as_posix()}" for relative in files]
    manifest = package_root / "MANIFEST.sha256"
    manifest.write_text("\n".join(entries) + "\n", encoding="utf-8", newline="\n")
    manifest.chmod(0o644)


def add_to_tar(archive: tarfile.TarFile, package_root: Path) -> None:
    for source in sorted(package_root.rglob("*"), key=lambda path: path.relative_to(package_root).as_posix()):
        package_relative = source.relative_to(package_root)
        relative = Path(PACKAGE_NAME) / package_relative
        info = archive.gettarinfo(str(source), arcname=relative.as_posix())
        info.uid = info.gid = 0
        info.uname = info.gname = "root"
        info.mtime = 0
        if source.is_dir():
            info.mode = 0o755
        elif package_relative.as_posix() in EXECUTABLE_FILES:
            info.mode = 0o755
        else:
            info.mode = 0o644
        if source.is_file():
            with source.open("rb") as stream:
                archive.addfile(info, stream)
        else:
            archive.addfile(info)


def build() -> Path:
    if not VERSION or any(char.isspace() for char in VERSION):
        raise ValueError("VERSION must be a non-empty single token")
    files = release_files()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    archive_path = OUTPUT_DIR / f"{PACKAGE_NAME}.tar.gz"
    checksum_path = OUTPUT_DIR / "SHA256SUMS"
    contents_path = OUTPUT_DIR / "PACKAGE_CONTENTS.txt"
    notes_path = OUTPUT_DIR / "RELEASE_NOTES.md"

    with tempfile.TemporaryDirectory(prefix="threat-detection-release-") as temp:
        package_root = Path(temp) / PACKAGE_NAME
        package_root.mkdir()
        copy_release_tree(package_root, files)
        write_manifest(package_root, files)
        with archive_path.open("wb") as raw:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as zipped:
                with tarfile.open(fileobj=zipped, mode="w", format=tarfile.PAX_FORMAT) as archive:
                    add_to_tar(archive, package_root)

    checksum_path.write_text(f"{sha256(archive_path)}  {archive_path.name}\n", encoding="utf-8", newline="\n")
    with tarfile.open(archive_path, "r:gz") as archive:
        contents = [
            f"{member.size:12d}  {member.name}"
            for member in archive.getmembers()
            if member.isfile()
        ]
    contents_path.write_text("\n".join(contents) + "\n", encoding="utf-8", newline="\n")
    notes_path.write_text(
        f"""# Threat Detection System {VERSION}

- The production frontend workspace has been merged across threat operations, platform operations, asset governance and system configuration, with consistent information hierarchy, responsive spacing, tables, empty states and feedback while preserving existing routes, permissions and API contracts.
- Login and first-administrator bootstrap follow the client reference with a light gray-blue background, a pale-blue brand column and a white form column in one centered card. Narrow screens stack the columns vertically, all authentication states retain their existing behavior, and the login's local light theme preserves the user's workspace theme preference.
- Login button hover text now meets the contrast requirement, and holding Space or Enter on a password-visibility button toggles once while keeping keyboard focus and the original password value.
- The production source archive excludes colocated frontend unit-test directories and TypeScript test/spec files, in addition to the existing development material and secret exclusions.
- Unknown protected routes now render a clear 404 state inside the existing application shell, and the release adds focused regression coverage for session exit, configuration saves, filters, pagination, sorting, cancellation, polling, empty states and request failures.
- Alert Center owner cells now render the stored username instead of a ProTable display node, so `[object Object]` can no longer appear. Detail labels stay on one line, long values remain readable, and counted text areas reserve space above modal actions.
- Threat List detail, grouped and expanded tables now center every business and auxiliary column consistently, including selection, expansion and sortable headers. The same non-wrapping detail-label treatment is shared by related customer/device, intelligence and configuration descriptions.
- Alert handling now uses a clear Pending / In Progress / Handled / Archived workflow. Owners are selected from enabled accounts, assignment moves an alert into progress, and the detail drawer exposes the full owner, handling level, elevation reason, actual handler, completion time and disposition without widening the main table.
- Ambiguous UI terms such as upgrade level and assigned to have been replaced with Handling Level, Elevate Handling and Owner. Elevation level/reason now persist correctly and no longer compete with the workflow status.
- Threat-intelligence recurrence recording keeps rows on a stable ID-ordered page, accepts the actual backend IOC filters and returns the updated sighting count and last-seen value.
- A saved encrypted LLM API key can be used by Connection Test without re-entering the secret. Configuration categories have deterministic backend and frontend ordering, so refresh no longer swaps field positions.
- Session expiry copy is concise and no longer exposes internal cleanup details; suspicious-source, observation-cycle, responsibility, handling and recurrence terms are consistent across the operator UI.
- The release contains exactly one installer at the package root; install, upgrade, rollback, uninstall and purge logic now lives directly in `install.sh`.
- Running `sudo ./install.sh` over an older installation now detects its version and performs an automatic in-place overwrite; `--purge --yes` is not required for upgrades, and existing environment settings and Docker data volumes are preserved.
- If the official Flink runtime tag stalls or fails through Docker Hub mirrors, the same installer run automatically pulls the pinned-digest DaoCloud image, verifies it and retags it for Compose; no manual pre-pull or second installer run is required.
- Administrator and user passwords use one 8–16 character policy across bootstrap, user management and customer-account provisioning, without character-class requirements.
- The application shell now keeps scrolling inside the main content region, so opening a modal or drawer while scrolled no longer moves the sidebar, header, online status or collapse control.
- Device-management dialogs now stay above their parent drawer, the device table fits without a forced horizontal scrollbar, and secondary device fields remain available in Data Status without changing the drawer size.
- Customer, tenant, user and device lifecycle states now share accessible semantic status tags with explicit light and dark theme colors.
- Threat-intelligence feeds now render the backend feed contract (name, URL, key setting, count, poll status and time); statistics, IP lookup and post-poll refresh use real response fields, and existing VirusTotal feeds migrate to `VT_API_KEY`.
- Threat-list single and batch deletion now have scoped backend endpoints; horizontal search labels are vertically aligned, trigger buttons no longer scale while pressed, and asynchronous row actions expose loading and duplicate-submit protection.
- Long-range overview queries now use daily buckets, indexed access paths, request coalescing and short-lived caching; 7-day/30-day views no longer time out or draw multiple values on the same date.
- Latest threats are de-duplicated by MAC, and 30-second/5-minute/15-minute detection windows are preserved and filterable.
- TCP/UDP ports are charted only when valid; ARP, ICMP, ICMPv6, GRE, ESP, AH and malformed port events are reported separately.
- MAC addresses use the dedicated readable badge style while IP addresses, ports, serials and service identifiers keep normal text styling.
- Threat-intelligence IP and CIDR values are now written through PostgreSQL's native `inet` cast, and feed rows keep a stable internal-first, ID-ordered position after enable or poll operations.
- TIRE resolves effective LLM settings at report time through a token-protected internal API, with a short cache and bounded stale fallback; when the configured bridge is unavailable without a valid cache it fails closed to template-only reports instead of reusing local or environment credentials. Persisted LLM API keys use AES-256-GCM encryption.
- Notification preferences now enforce configured severities, quiet hours and hourly limits, perform real channel tests, select the configured SMS provider, and retry failed deliveries; persisted SMTP passwords use AES-256-GCM encryption. Notification configuration is restricted to authenticated gateway access, protected by a separate internal service token, and Alert/Customer Management ports 8082/8084 are no longer published on the host.
- ML training handles single-group datasets and reports real tier progress.
- The release includes database migrations 46–49, generated encryption and TIRE service secrets, 18-service diagnostics, and the existing transactional installer/rollback checks.
""",
        encoding="utf-8",
        newline="\n",
    )
    print(f"Release: {archive_path}")
    print(f"SHA256: {checksum_path}")
    print(f"Contents: {contents_path}")
    print(f"Notes:    {notes_path}")
    print(f"Files:   {len(files) + 1}")
    return archive_path


if __name__ == "__main__":
    build()
