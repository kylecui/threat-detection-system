#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
PREFIX = f"threat-detection-system-{VERSION}-linux-x86_64"
BASH = shutil.which("bash")
if os.name == "nt":
    git_bash = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
    if git_bash.is_file():
        BASH = str(git_bash)


class ReleaseArchiveContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        # Exercise the real packer without replacing an already delivered release.
        cls.temporary = tempfile.TemporaryDirectory(prefix="tds-release-contract-")
        cls.addClassCleanup(cls.temporary.cleanup)
        specification = importlib.util.spec_from_file_location("release_contract_packer", ROOT / "scripts/pack_release.py")
        assert specification is not None and specification.loader is not None
        packer = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(packer)
        cls.output = Path(cls.temporary.name)
        packer.OUTPUT_DIR = cls.output
        cls.packer = packer
        cls.archive = packer.build()

    def test_repeated_archive_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory(prefix="tds-release-repeat-") as temporary:
            previous = self.packer.OUTPUT_DIR
            try:
                self.packer.OUTPUT_DIR = Path(temporary)
                repeated = self.packer.build()
                self.assertEqual(self.archive.read_bytes(), repeated.read_bytes())
            finally:
                self.packer.OUTPUT_DIR = previous

    def test_external_checksum(self) -> None:
        expected, name = (self.output / "SHA256SUMS").read_text(encoding="utf-8").split()
        self.assertEqual(name, self.archive.name)
        self.assertEqual(expected, hashlib.sha256(self.archive.read_bytes()).hexdigest())
        self.assertTrue((self.output / "PACKAGE_CONTENTS.txt").is_file())
        self.assertTrue((self.output / "RELEASE_NOTES.md").is_file())

    def test_archive_shape_and_internal_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp, tarfile.open(self.archive, "r:gz") as archive:
            names = archive.getnames()
            self.assertTrue(all(member.mtime == 0 for member in archive.getmembers()))
            self.assertTrue(all(name == PREFIX or name.startswith(PREFIX + "/") for name in names))
            self.assertEqual(
                [name for name in names if Path(name).name == "install.sh"],
                [f"{PREFIX}/install.sh"],
            )
            archive.extractall(temp, filter="data")
            root = Path(temp) / PREFIX
            required = {
                "install.sh",
                "VERSION",
                ".env.example",
                "MANIFEST.sha256",
                "docker/docker-compose.yml",
                "docker/docker-compose.control.yml",
                "scripts/compose-runtime.sh",
                "scripts/runtime-watchdog.sh",
                "scripts/tdsdiag.sh",
                "scripts/verify_release_state.py",
                "deploy/systemd/threat-detection-runtime-watchdog.service",
                "deploy/systemd/threat-detection-runtime-watchdog.timer",
            }
            self.assertTrue(required.issubset({path.relative_to(root).as_posix() for path in root.rglob("*")}))
            manifest_paths = set()
            for line in (root / "MANIFEST.sha256").read_text(encoding="utf-8").splitlines():
                expected, relative = line.split("  ", 1)
                manifest_paths.add(relative)
                self.assertEqual(expected, hashlib.sha256((root / relative).read_bytes()).hexdigest(), relative)
            self.assertIn("docker/docker-compose.control.yml", manifest_paths)
            self.assertIn("scripts/compose-runtime.sh", manifest_paths)

            for relative in (
                "install.sh",
                "scripts/prepare_release_env.py",
                "scripts/compose-runtime.sh",
                "scripts/runtime-watchdog.sh",
                "scripts/tdsdiag.sh",
                "scripts/verify_release_state.py",
            ):
                member = archive.getmember(f"{PREFIX}/{relative}")
                self.assertEqual(member.mode & 0o777, 0o755, relative)
                self.assertTrue(os.access(root / relative, os.X_OK), relative)

            readme = archive.getmember(f"{PREFIX}/README.md")
            self.assertEqual(readme.mode & 0o777, 0o644)

    def test_no_development_or_secret_material(self) -> None:
        with tarfile.open(self.archive, "r:gz") as archive:
            names = archive.getnames()
        forbidden_parts = {"AI_PROJECT", "docs", "k8s", "node_modules", "target", "tests", "__tests__", "e2e"}
        for name in names:
            relative_parts = Path(name).parts[1:]
            self.assertFalse(forbidden_parts.intersection(relative_parts), name)
            self.assertFalse(name.lower().endswith((".env", ".pem", ".key", ".p12", ".pfx", ".pkcs12", ".bak", ".backup")), name)
            self.assertFalse(name.endswith((".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx")), name)

    def test_secret_filenames_are_excluded_from_recursive_release_sources(self) -> None:
        for name in (".env", ".env.local", "control.env", "client.p12", "trust.P12", "private.key", "server.pem"):
            with self.subTest(name=name):
                self.assertFalse(self.packer.should_include(Path("config-repo") / "tls" / name))


def shell_function(source: str, name: str) -> str:
    body = source.split(f"{name}() {{\n", 1)[1].split("\n}\n", 1)[0]
    return f"{name}() {{\n{body}\n}}\n"


@unittest.skipUnless(BASH, "requires Bash or Git for Windows Bash")
class ComposeRuntimeContractTest(unittest.TestCase):
    """Probe real shell composition and installed entry points with recording Docker."""

    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="tds-compose-contract-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.source = self.root / "source release"
        self.target = self.root / "target release"
        self.control_env = self.root / "host config/control.env"
        self.control_env.parent.mkdir()
        self.log = self.root / "compose-calls"
        self.wrapper = (ROOT / "scripts/compose-runtime.sh").read_text(encoding="utf-8")
        self.installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.watchdog = (ROOT / "scripts/runtime-watchdog.sh").read_text(encoding="utf-8")
        for release in (self.source, self.target):
            (release / "docker").mkdir(parents=True)
            (release / "scripts").mkdir()
            for name in ("docker-compose.yml", "docker-compose.control.yml"):
                (release / "docker" / name).write_text("services: {}\n", encoding="utf-8")
            (release / ".env").write_text("# base\n", encoding="utf-8")
            wrapper = release / "scripts/compose-runtime.sh"
            wrapper.write_text(self.wrapper, encoding="utf-8", newline="\n")
            wrapper.chmod(0o755)
        (self.root / "prepared.env").write_text("# prospective base\n", encoding="utf-8")
        (self.root / "bin").mkdir()
        docker = self.root / "bin/docker"
        docker.write_text(
            '#!/usr/bin/env bash\n'
            '{ printf \'%s\\0\' "$PWD" "$@"; printf \'\\n\'; } >>"$COMPOSE_TEST_LOG"\n'
            'if [[ "${*: -3}" == "ps -q api-gateway" ]]; then echo fixture; fi\n',
            encoding="utf-8", newline="\n",
        )
        docker.chmod(0o755)

    def run_calls(self, body: str) -> list[list[str]]:
        self.log.unlink(missing_ok=True)
        # PWD is in the active shell's path format on both Linux and Windows.
        prelude = r'''
fixture="$PWD"
export PATH="$fixture/bin:$PATH" COMPOSE_TEST_LOG="$fixture/compose-calls"
export JZ_CONTROL_ENV_FILE="$fixture/host config/control.env"
CONTROL_ENV_FILE="$JZ_CONTROL_ENV_FILE"
SRC_ROOT="$fixture/source release"
SCRIPT_DIR="$SRC_ROOT/scripts"
TARGET_DIR="$fixture/target release"
PREPARED_ENV="$fixture/prepared.env"
timeout() { shift; "$@"; }
runtime_reason() { return 1; }
warn() { :; }
'''
        result = subprocess.run(
            [BASH, "-ec", prelude + body], cwd=self.root, text=True,
            capture_output=True, check=False, timeout=35,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return [line.rstrip("\0").split("\0") for line in self.log.read_text(encoding="utf-8").splitlines()]

    def assert_compose(self, call: list[str], root: str, primary: str, overlay: bool, *command: str) -> None:
        cwd = call[0]
        self.assertTrue(cwd.endswith(f"/{root}"), call)
        fixture = cwd.rsplit("/", 1)[0]
        args = ["compose", "--env-file", primary.replace("{fixture}", fixture)]
        if overlay:
            args.extend(["--env-file", f"{fixture}/host config/control.env"])
        args.extend(["-f", "docker/docker-compose.yml"])
        if overlay:
            args.extend(["-f", "docker/docker-compose.control.yml"])
        self.assertEqual(call[1:], args + list(command))

    def test_installer_preflights_source_then_uses_target_overlay_only_when_available(self) -> None:
        main = self.installer.rsplit("\nvalidate_target\n", 1)[1]
        quiet = next(line.rstrip(" \\") for line in main.splitlines() if "config --quiet" in line)
        ports = shell_function(self.installer, "validate_compose_ports")
        rendering = next(line.strip().removeprefix("if ! ").split(" >", 1)[0]
                         for line in ports.splitlines() if "config --format json" in line)
        functions = shell_function(self.installer, "compose_release") + shell_function(self.installer, "compose_in")
        for present in (False, True):
            with self.subTest(control_env=present):
                if present:
                    self.control_env.write_text("JZ_CONTROL_TLS_DIR=/fixture/tls\n", encoding="utf-8")
                calls = self.run_calls(functions + quiet + "\n" + rendering + '\ncompose_in "$TARGET_DIR" down --remove-orphans\n')
                self.assertEqual(len(calls), 3)
                self.assert_compose(calls[0], "source release", "{fixture}/prepared.env", present, "config", "--quiet")
                self.assert_compose(calls[1], "source release", "{fixture}/prepared.env", present, "config", "--format", "json")
                self.assert_compose(calls[2], "target release", ".env", present, "down", "--remove-orphans")
        (self.target / "docker/docker-compose.control.yml").unlink()
        call, = self.run_calls(functions + 'compose_in "$TARGET_DIR" down --remove-orphans\n')
        self.assert_compose(call, "target release", ".env", False, "down", "--remove-orphans")

    def test_systemd_start_stop_and_watchdog_recreation_share_the_same_overlay(self) -> None:
        unit = (ROOT / "deploy/systemd/threat-detection-compose.service").read_text(encoding="utf-8")
        commands = [
            line.split("=", 1)[1].replace("/opt/threat-detection-system", '"$TARGET_DIR"')
            for line in unit.splitlines() if line.startswith(("ExecStart=", "ExecStop="))
        ]
        watchdog = shell_function(self.watchdog, "compose") + shell_function(self.watchdog, "recover_service")
        for kind in ("missing", "directory", "file"):
            with self.subTest(control_env=kind):
                if kind == "directory":
                    self.control_env.mkdir()
                if kind == "file":
                    self.control_env.rmdir()
                    self.control_env.write_text("JZ_CONTROL_TLS_DIR=/fixture/tls\n", encoding="utf-8")
                calls = self.run_calls(watchdog + "\n".join(commands) + "\ncompose config --services\nrecover_service api-gateway\n")
                expected = [
                    ("up", "-d", "--remove-orphans"), ("stop",), ("config", "--services"),
                    ("up", "-d", "--no-deps", "--force-recreate", "api-gateway"), ("ps", "-q", "api-gateway"),
                ]
                self.assertEqual(len(calls), len(expected))
                for call, command in zip(calls, expected):
                    self.assert_compose(call, "target release", ".env", kind == "file", *command)

    def test_uninstall_and_purge_do_not_target_host_control_material(self) -> None:
        self.control_env.write_text("JZ_CONTROL_TLS_DIR=/fixture/tls\n", encoding="utf-8")
        tls = self.control_env.parent / "tls"
        tls.mkdir()
        certificate = tls / "client.p12"
        certificate.write_bytes(b"synthetic fixture")
        functions = "".join(shell_function(self.installer, name) for name in (
            "installed_project_name", "show_remove_plan", "remove_installation",
        ))
        # Record the deletion arguments without giving the installer a real
        # filesystem writer or access to a running systemd/Docker daemon.
        calls = self.run_calls(functions + r'''
BACKUP_ROOT="$fixture/backups"
TARGET_DIR="$fixture/absent-installation"
UNIT_NAME=threat-detection-compose.service
WATCHDOG_SERVICE_NAME=threat-detection-runtime-watchdog.service
WATCHDOG_TIMER_NAME=threat-detection-runtime-watchdog.timer
UNIT_PATH="$fixture/$UNIT_NAME"
WATCHDOG_SERVICE_PATH="$fixture/$WATCHDOG_SERVICE_NAME"
WATCHDOG_TIMER_PATH="$fixture/$WATCHDOG_TIMER_NAME"
DRY_RUN=false
systemctl() { :; }
docker() { :; }
ok() { :; }
die() { echo "$*" >&2; exit 1; }
confirm() { return 0; }
rm() { { printf '%s\0' rm "$@"; printf '\n'; } >>"$COMPOSE_TEST_LOG"; }
remove_installation false
remove_installation true
''')
        self.assertEqual(len(calls), 4)
        self.assertTrue(any(call[1:3] == ["-rf", "--"] for call in calls))
        self.assertFalse(any("host config" in arg or "/etc/jz-control" in arg for call in calls for arg in call))
        self.assertTrue(self.control_env.is_file())
        self.assertEqual(certificate.read_bytes(), b"synthetic fixture")


class ReleaseContractTest(unittest.TestCase):
    """Read-only contracts can run without regenerating a delivered archive."""

    def test_installer_waits_for_apt_lock_without_deleting_it(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.assertIn('DPkg::Lock::Timeout=$native_wait', installer)
        self.assertIn("APT/dpkg is busy; waiting 5 seconds before retrying", installer)
        self.assertIn("Do not delete files under /var/lib/dpkg", installer)
        self.assertIn("docker-buildx-plugin", installer)
        self.assertIn("export COMPOSE_BAKE=true", installer)
        self.assertNotIn("export COMPOSE_BAKE=false", installer)
        self.assertNotRegex(installer, r"rm\s+-[^\n]*\s/var/lib/(?:dpkg|apt)/.*lock")

    def test_image_downloads_precede_transaction_and_builds_are_sequential(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        execution = installer.rsplit("\nvalidate_target\n", 1)[1]
        self.assertLess(execution.index("prefetch_container_images"), execution.index("prepare_rollback"))
        self.assertIn('compose_in "$TARGET_DIR" build "$service"', installer)
        self.assertIn("DOCKER_PULL_RETRIES", installer)
        self.assertIn("DOCKER_BUILD_RETRIES", installer)
        for image in (
            "postgres:15-alpine",
            "confluentinc/cp-kafka:7.4.0",
            "maven:3.9-eclipse-temurin-21",
            "flink:1.18.1-scala_2.12-java17",
            "python:3.11-slim",
            "python:3.12-slim",
            "node:20-alpine",
            "nginx:1.25-alpine",
        ):
            self.assertIn(image, installer)

        self.assertIn("threat-intelligence tire api-gateway", installer)
        self.assertIn('running_count" -ge 18', (ROOT / "scripts/tdsdiag.sh").read_text(encoding="utf-8"))

    def test_flink_pull_uses_verified_direct_registry_fallback(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        pull = installer.split("pull_container_image() {", 1)[1].split("\n}", 1)[0]

        self.assertIn(
            "docker.m.daocloud.io/library/flink:1.18.1-scala_2.12-java17",
            installer,
        )
        self.assertIn(
            "sha256:44af270538aa97418be63d0f42b2f4400d12c5e41e58813f8b4b85d52fe27f5d",
            installer,
        )
        self.assertIn('grep -Fq "@$expected_digest"', installer)
        self.assertIn('docker image tag "$fallback" "$image"', installer)
        self.assertIn('pull_image_candidate "$image" "$expected_digest"', pull)
        self.assertIn('pull_image_candidate "$fallback" "$expected_digest"', pull)
        self.assertLess(
            pull.index('pull_image_candidate "$image" "$expected_digest"'),
            pull.index('pull_image_candidate "$fallback" "$expected_digest"'),
        )
        self.assertIn("Verified fallback image ready as", pull)

    def test_installer_automatically_overwrites_older_releases(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        detection = installer.split("detect_existing_install() {", 1)[1].split("\n}", 1)[0]
        sync = installer.split("sync_release_tree() {", 1)[1].split("\n}", 1)[0]
        execution = installer.rsplit("\nvalidate_target\n", 1)[1]

        self.assertIn('$TARGET_DIR/.installed_version', detection)
        self.assertIn('$TARGET_DIR/VERSION', detection)
        self.assertIn('INSTALL_MODE="upgrade"', detection)
        self.assertIn("Older installed version detected", detection)
        self.assertIn("no uninstall or confirmation is required", detection)
        self.assertIn(".env and Docker data volumes will be preserved", detection)
        self.assertNotIn("confirm ", detection)
        self.assertIn("Do not run --purge before an upgrade", installer)
        self.assertIn("rsync -a --checksum --delete --exclude='.env'", sync)
        self.assertLess(sync.index("verify_installed_release content"), sync.index('install -m 600 "$PREPARED_ENV"'))
        self.assertLess(sync.index("verify_installed_release environment"), sync.index('"$TARGET_DIR/.installed_version"'))
        self.assertIn("verify_installed_release metadata", sync)
        self.assertLess(execution.index("detect_existing_install"), execution.index("prepare_runtime_env"))
        self.assertLess(execution.index("detect_existing_install"), execution.index("prepare_rollback"))

    def test_docker_mirror_policy_distinguishes_new_and_existing_docker(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.assertIn("DOCKER_PRESENT_AT_START=false", installer)
        self.assertIn("configure_new_docker_registry_mirrors", installer)
        self.assertIn("print_existing_docker_mirror_guidance", installer)
        self.assertIn("the original file was preserved", installer)
        self.assertIn("the previous daemon configuration was restored", installer)
        dependency_check = installer.split("ensure_dependencies() {", 1)[1].split("\n}", 1)[0]
        self.assertNotIn("print_existing_docker_mirror_guidance", dependency_check)
        prefetch_failure = installer.split("if ! prefetch_container_images; then", 1)[1].split("\nfi", 1)[0]
        self.assertIn("print_existing_docker_mirror_guidance", prefetch_failure)
        for mirror in (
            "https://docker.xuanyuan.me",
            "https://docker.1ms.run",
            "https://docker.m.daocloud.io",
        ):
            self.assertIn(mirror, installer)

    def test_compose_ports_are_unique_and_preflighted(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        template = (ROOT / ".env.example").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")

        self.assertIn('${EMQX_WS_HOST_PORT:-18084}:8083', compose)
        self.assertEqual(compose.count('"8083:8083"'), 1)
        self.assertIn("EMQX_WS_HOST_PORT=18084", template)
        self.assertIn("validate_compose_ports()", installer)
        self.assertIn("Docker Compose contains duplicate published host ports", installer)
        self.assertIn("Required host ports are unavailable", installer)

        execution = installer.rsplit("\nvalidate_target\n", 1)[1]
        self.assertLess(execution.index("validate_compose_ports"), execution.index("prefetch_container_images"))
        self.assertLess(execution.index("validate_compose_ports"), execution.index("prepare_rollback"))

    def test_failure_diagnostics_include_container_logs_and_ports(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        diagnostics = installer.split("capture_failure_diagnostics() {", 1)[1].split("\n}", 1)[0]
        self.assertIn("compose logs (last 150 lines per service)", diagnostics)
        self.assertIn("logs --no-color --tail=150", diagnostics)
        self.assertIn("ss -ltnup", diagnostics)

    def test_threat_detail_response_preserves_persisted_attacker_ip(self) -> None:
        query_service = (
            ROOT
            / "services/threat-assessment/src/main/java/com/threatdetection/assessment/service/ThreatQueryService.java"
        ).read_text(encoding="utf-8")
        self.assertIn("response.setAttackIp(assessment.getAttackIp());", query_service)
        self.assertNotIn('response.setAttackIp("N/A");', query_service)

    def test_runtime_watchdog_detects_docker_containerd_desynchronization(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        diagnostic = (ROOT / "scripts/tdsdiag.sh").read_text(encoding="utf-8")
        watchdog = (ROOT / "scripts/runtime-watchdog.sh").read_text(encoding="utf-8")
        compose_unit = (ROOT / "deploy/systemd/threat-detection-compose.service").read_text(
            encoding="utf-8"
        )
        timer = (ROOT / "deploy/systemd/threat-detection-runtime-watchdog.timer").read_text(
            encoding="utf-8"
        )

        self.assertIn('! -d "/proc/$pid"', watchdog)
        self.assertIn("ctr -n moby tasks list", watchdog)
        self.assertIn('task_state" != "RUNNING"', watchdog)
        self.assertIn("--force-recreate", watchdog)
        self.assertIn("--no-deps", watchdog)
        self.assertNotIn(" down -v", watchdog)
        self.assertIn("runtime-watchdog.sh\" --check", installer)
        self.assertIn("verify_v1_syslog_listener", installer)
        self.assertIn("/dev/tcp/$1/9080", installer)
        self.assertIn("NetworkSettings.Networks", installer)
        self.assertIn("runtime-watchdog.sh\" --check", diagnostic)
        self.assertIn("v1_syslog_tcp", diagnostic)
        self.assertIn("logstash_container_ip", diagnostic)
        self.assertIn("ExecStartPost=/opt/threat-detection-system/scripts/runtime-watchdog.sh --recover", compose_unit)
        self.assertIn("OnUnitActiveSec=1min", timer)

    def test_containerd_task_lookup_does_not_trigger_sigpipe(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        watchdog = (ROOT / "scripts/runtime-watchdog.sh").read_text(encoding="utf-8")
        safe_lookup = (
            'NR > 1 && $1 == wanted {state=$3} '
            'END {if (state != "") print state}'
        )

        for source in (installer, watchdog):
            self.assertIn(safe_lookup, source)
            self.assertNotIn(
                'NR > 1 && $1 == wanted {print $3; exit}',
                source,
            )

        if os.name != "nt":
            probe = subprocess.run(
                [
                    "bash",
                    "-o",
                    "pipefail",
                    "-c",
                    "{ printf 'TASK PID STATUS\\n'; "
                    "for i in $(seq 1 4096); do "
                    "printf 'task-%s %s RUNNING\\n' \"$i\" \"$i\"; done; } "
                    "| awk -v wanted=task-2 "
                    "'NR > 1 && $1 == wanted {state=$3} "
                    "END {if (state != \"\") print state}'",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(probe.returncode, 0, probe.stderr)
            self.assertEqual(probe.stdout.strip(), "RUNNING")

    def test_customer_form_modal_uses_readable_responsive_layout(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(
            encoding="utf-8"
        )
        styles = (ROOT / "frontend/src/index.css").read_text(encoding="utf-8")
        self.assertIn('rootClassName="tds-form-modal"', page)
        self.assertIn("width={920}", page)
        self.assertIn("<Row gutter={[20, 0]}>", page)
        self.assertIn("xs={24} md={12}", page)
        self.assertRegex(
            page,
            r"maxHeight:\s*[\"']calc\(100vh - 180px\)[\"']",
        )
        self.assertIn(".tds-customer-form .ant-input", styles)

    def test_device_management_table_fits_drawer_without_horizontal_scroll(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(
            encoding="utf-8"
        )
        styles = (ROOT / "frontend/src/index.css").read_text(encoding="utf-8")
        action_button = (
            ROOT / "frontend/src/components/TableActionButton.tsx"
        ).read_text(encoding="utf-8")
        self.assertIn('className="tds-device-management-table"', page)
        self.assertIn('width="min(1480px, 96vw)"', page)
        self.assertIn('tableLayout="fixed"', page)
        self.assertNotIn("scroll={{ x: 1495 }}", page)
        self.assertIn('responsive: ["xl"]', page)
        # INTERACTION-005: the device drawer uses the shared action group.
        self.assertIn('<TableActions label={t("common.actions")}>', page)
        self.assertNotIn("tds-device-actions", page)
        self.assertIn("<TableActionButton", page)
        self.assertIn("tds-table-action-button", action_button)
        self.assertIn("aria-label={label}", action_button)
        self.assertIn('label={t("customersDevices.discoveredHosts")}', page)
        self.assertIn('label={t("common.createdAt")}', page)
        self.assertIn(".tds-device-management-table .ant-table-thead", styles)
        self.assertIn("white-space: nowrap", styles)

    def test_device_drawer_dialogs_stay_above_the_drawer(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(
            encoding="utf-8"
        )
        self.assertIn("const DEVICE_MODAL_Z_INDEX = 1300;", page)
        self.assertEqual(page.count("zIndex={DEVICE_MODAL_Z_INDEX}"), 3)
        self.assertIn("setDeviceFlowOpen(false);", page)
        self.assertIn("setBindDeviceModalOpen(false);", page)
        self.assertIn("setEditDeviceModalOpen(false);", page)

    def test_entity_lifecycle_statuses_share_accessible_semantic_tags(self) -> None:
        component = (ROOT / "frontend/src/components/SemanticStatusTag.tsx").read_text(
            encoding="utf-8"
        )
        styles = (ROOT / "frontend/src/index.css").read_text(encoding="utf-8")
        customer_page = (
            ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx"
        ).read_text(encoding="utf-8")
        tenant_page = (ROOT / "frontend/src/pages/TenantMgmt/index.tsx").read_text(
            encoding="utf-8"
        )
        user_page = (ROOT / "frontend/src/pages/UserMgmt/index.tsx").read_text(
            encoding="utf-8"
        )
        self.assertIn("tds-status-tag--${tone}", component)
        self.assertIn(".tds-status-tag--success.ant-tag", styles)
        self.assertIn(".tds-status-tag--neutral.ant-tag", styles)
        self.assertIn('html[data-theme="dark"] .tds-status-tag--success.ant-tag', styles)
        self.assertIn("<SemanticStatusTag", customer_page)
        self.assertIn("<SemanticStatusTag", tenant_page)
        self.assertIn("<SemanticStatusTag", user_page)

    def test_device_flow_refreshes_summary_and_events_as_one_view(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(
            encoding="utf-8"
        )
        self.assertIn("Promise.allSettled([", page)
        self.assertIn("customerService.getDevice(customerId, device.devSerial)", page)
        self.assertRegex(
            page,
            r"customerService\.getDeviceEvents\(\s*customerId,\s*device\.devSerial",
        )
        self.assertIn("reconcileDeviceTelemetry(refreshedDevice, events)", page)
        self.assertRegex(page, r"pipelineState\s*=\s*[\"']RECEIVING[\"']")
        self.assertIn("setCurrentDevice(refreshedDevice)", page)
        self.assertRegex(page, r"setDevices\(\(previous\)\s*=>\s*previous\.map")
        self.assertIn("}, 15_000);", page)
        self.assertIn("deviceFlowRequestId.current += 1", page)
        self.assertIn("const deviceListRequestId = useRef(0)", page)
        self.assertIn("loadDevices(currentCustomer.customerId, true)", page)
        self.assertIn("if (!silent) setDevicesLoading(true)", page)
        self.assertIn("onClose={closeDeviceDrawer}", page)

    def test_overview_port_distribution_uses_raw_events_and_horizontal_bars(self) -> None:
        overview = (ROOT / "frontend/src/pages/Overview/index.tsx").read_text(encoding="utf-8")
        chart = (ROOT / "frontend/src/components/ui/PortDistributionChart.tsx").read_text(encoding="utf-8")
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")
        styles = (ROOT / "frontend/src/index.css").read_text(encoding="utf-8")
        package = json.loads((ROOT / "frontend/package.json").read_text(encoding="utf-8"))
        self.assertIn("import PortDistributionChart", overview)
        self.assertNotIn("AreaChart", overview)
        self.assertIn("<ResponsiveContainer", chart)
        self.assertIn('layout="vertical"', chart)
        self.assertIn("<Bar dataKey=\"count\"", chart)
        self.assertIn("tds-port-chart__tooltip", chart)
        self.assertIn("FROM attack_events ae", repository)
        self.assertIn("response_port BETWEEN 1 AND 65535", repository)
        self.assertIn("SUM(grouped.port_count) OVER ()", repository)
        self.assertIn("--chart-tooltip-bg", styles)
        self.assertIn("recharts", package["dependencies"])

    def test_network_identifiers_use_one_readable_component(self) -> None:
        component = (ROOT / "frontend/src/components/NetworkIdentifier.tsx").read_text(encoding="utf-8")
        styles = (ROOT / "frontend/src/index.css").read_text(encoding="utf-8")
        pages = [
            ROOT / "frontend/src/pages/Overview/index.tsx",
            ROOT / "frontend/src/pages/ThreatList/index.tsx",
            ROOT / "frontend/src/pages/AlertCenter/index.tsx",
            ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx",
        ]
        combined = "\n".join(page.read_text(encoding="utf-8") for page in pages)
        self.assertIn("MAC_PATTERN", component)
        self.assertIn("toUpperCase()", component)
        self.assertIn("tds-network-identifier", styles)
        self.assertIn(".tds-network-identifier--mac", styles)
        self.assertIn("padding: 7px 12px", styles)
        self.assertIn("background: #f1f7fc", styles)
        self.assertIn("color: #0b4f8a", styles)
        self.assertIn("font: inherit", styles)
        self.assertGreaterEqual(combined.count("<NetworkIdentifier"), 10)
        self.assertNotRegex(combined, r"<code>\s*\{[^}]*[Mm]ac")

    def test_postgres_init_mounts_are_real_sql_files_and_preflighted(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        mounts = re.findall(
            r"^\s*- \./([^:]+):/docker-entrypoint-initdb\.d/([^\s]+)",
            compose,
            flags=re.MULTILINE,
        )

        self.assertGreater(len(mounts), 0)
        self.assertEqual(len({target for _, target in mounts}), len(mounts))
        self.assertIn(("port_weights_migration.sql", "03-port-weights.sql"), mounts)
        self.assertNotIn(("03-port-weights.sql", "03-port-weights.sql"), mounts)
        for table in (
            "port_risk_configs",
            "whitelist_config",
            "auth_users",
            "device_status_history",
        ):
            self.assertIn(f"to_regclass('public.{table}') IS NOT NULL", compose)

        for source, target in mounts:
            path = ROOT / "docker" / source
            self.assertTrue(path.is_file(), source)
            nonblank = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
            self.assertTrue(nonblank, source)
            self.assertNotRegex(nonblank[0], r"^[A-Za-z0-9._-]+\.sql$", source)
            self.assertRegex(target, r"^[A-Za-z0-9._-]+\.sql$")

        self.assertIn("validate_postgres_init_scripts()", installer)
        execution = installer.rsplit("\nvalidate_target\n", 1)[1]
        self.assertLess(
            execution.index("validate_postgres_init_scripts"),
            execution.index("prepare_rollback"),
        )

    def test_runtime_secrets_and_notification_provider_settings_are_packaged(self) -> None:
        template = (ROOT / ".env.example").read_text(encoding="utf-8")
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        generator = (ROOT / "scripts/prepare_release_env.py").read_text(encoding="utf-8")

        for name in (
            "CONFIG_ENCRYPTION_KEY",
            "TIRE_INTERNAL_SERVICE_TOKEN",
            "ALERT_INTERNAL_SERVICE_TOKEN",
        ):
            self.assertIn(f"{name}=auto", template)
            self.assertIn(name, generator)
            self.assertIn(f"${{{name}:?{name} is required}}", compose)

        alert_service = compose.split("\n  alert-management:\n", 1)[1].split(
            "\n  customer-management:\n", 1
        )[0]
        customer_service = compose.split("\n  customer-management:\n", 1)[1].split(
            "\n  threat-intelligence:\n", 1
        )[0]
        self.assertIn('expose: ["8082"]', alert_service)
        self.assertNotIn('ports: ["8082:8082"]', alert_service)
        self.assertIn('expose: ["8084"]', customer_service)
        self.assertNotIn('ports: ["8084:8084"]', customer_service)

        gateway_source = (
            ROOT
            / "services/api-gateway/src/main/java/com/threatdetection/gateway/ApiGatewayApplication.java"
        ).read_text(encoding="utf-8")
        alert_filter_source = (
            ROOT
            / "services/alert-management/src/main/java/com/threatdetection/alert/config/NotificationConfigInternalAuthFilter.java"
        ).read_text(encoding="utf-8")
        customer_client_source = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/notification/service/NotificationDeliveryClient.java"
        ).read_text(encoding="utf-8")
        rbac_source = (
            ROOT
            / "services/api-gateway/src/main/java/com/threatdetection/gateway/auth/filter/RbacAuthorizationFilter.java"
        ).read_text(encoding="utf-8")
        self.assertIn('.setRequestHeader("X-Internal-Service-Token", alertServiceToken)', gateway_source)
        self.assertIn('PROTECTED_PREFIX = "/api/notification-config"', alert_filter_source)
        self.assertIn('.defaultHeader("X-Internal-Service-Token", alertServiceToken)', customer_client_source)
        self.assertIn('"/api/notification-config/**"', rbac_source)

        for name in (
            "SMS_PROVIDER",
            "TWILIO_ACCOUNT_SID",
            "TWILIO_AUTH_TOKEN",
            "TWILIO_PHONE_NUMBER",
            "ALIYUN_ACCESS_KEY_ID",
            "ALIYUN_ACCESS_KEY_SECRET",
            "ALIYUN_SMS_SIGN_NAME",
            "ALIYUN_SMS_TEMPLATE_CODE",
        ):
            self.assertIn(f"{name}=", template)
            self.assertIn(f"{name}:", compose)

    def test_kafka_healthcheck_keeps_a_bounded_slow_start_budget(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        kafka = compose.split("\n  kafka:\n", 1)[1].split("\n  data-ingestion:\n", 1)[0]
        self.assertIn(
            'test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]',
            kafka,
        )
        self.assertIn("retries: 10", kafka)
        self.assertIn("start_period: 180s", kafka)

    def test_emqx_single_node_name_is_not_forced(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        emqx = compose.split("\n  emqx:\n", 1)[1].split("\n  kafka:\n", 1)[0]
        self.assertNotIn("EMQX_NAME:", emqx)
        self.assertNotIn("EMQX_HOST:", emqx)
        self.assertNotIn("EMQX_NODE__NAME:", emqx)
        self.assertIn('test: ["CMD", "emqx", "ctl", "status"]', emqx)


if __name__ == "__main__":
    unittest.main()
