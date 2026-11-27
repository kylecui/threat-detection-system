#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
ARCHIVE = (
    ROOT
    / "outputs"
    / f"v{VERSION}"
    / f"threat-detection-system-{VERSION}-linux-x86_64.tar.gz"
)
PREFIX = f"threat-detection-system-{VERSION}-linux-x86_64"


@unittest.skipUnless(
    os.environ.get("TDS_RUN_EMQX_HEALTH_TEST") == "1",
    "set TDS_RUN_EMQX_HEALTH_TEST=1 on a Docker build host",
)
class EmqxHealthTest(unittest.TestCase):
    def test_packaged_single_node_emqx_becomes_healthy(self) -> None:
        subprocess.run([sys.executable, str(ROOT / "scripts/pack_release.py")], check=True)
        project = f"tds-emqx-health-{os.getpid()}"
        container_id = ""

        with tempfile.TemporaryDirectory(prefix="tds-emqx-health-") as temp:
            with tarfile.open(ARCHIVE, "r:gz") as archive:
                archive.extractall(temp, filter="data")
            release = Path(temp) / PREFIX
            env_file = release / ".env"
            subprocess.run(
                [
                    sys.executable,
                    str(release / "scripts/prepare_release_env.py"),
                    str(release / ".env.example"),
                    str(env_file),
                ],
                check=True,
            )
            env_file.chmod(0o600)
            environment = os.environ.copy()
            environment["COMPOSE_PROJECT_NAME"] = project
            compose = [
                "docker",
                "compose",
                "--project-name",
                project,
                "--env-file",
                str(env_file),
                "-f",
                str(release / "docker/docker-compose.yml"),
            ]

            try:
                result = subprocess.run(
                    compose + ["run", "-d", "--no-deps", "emqx"],
                    cwd=release,
                    env=environment,
                    check=True,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                )
                container_id = result.stdout.strip().splitlines()[-1]
                deadline = time.monotonic() + 120
                health = "starting"
                status = "created"
                while time.monotonic() < deadline:
                    status, health = subprocess.check_output(
                        [
                            "docker",
                            "inspect",
                            "-f",
                            "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}",
                            container_id,
                        ],
                        text=True,
                    ).strip().split()
                    if status == "running" and health == "healthy":
                        break
                    if status == "exited" or health == "unhealthy":
                        break
                    time.sleep(2)

                logs = subprocess.check_output(
                    ["docker", "logs", container_id],
                    text=True,
                    stderr=subprocess.STDOUT,
                )
                self.assertEqual((status, health), ("running", "healthy"), logs)
                self.assertIn("Listener tcp:default on 0.0.0.0:1883 started.", logs)
                self.assertIn("Listener ssl:default on 0.0.0.0:8883 started.", logs)
                self.assertNotIn("@emqx not responding to pings", logs)
                restart_count = subprocess.check_output(
                    ["docker", "inspect", "-f", "{{.RestartCount}}", container_id],
                    text=True,
                ).strip()
                self.assertEqual(restart_count, "0", logs)

                ctl = subprocess.run(
                    ["docker", "exec", container_id, "emqx", "ctl", "status"],
                    check=False,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                )
                self.assertEqual(ctl.returncode, 0, ctl.stdout)
                self.assertIn("is started", ctl.stdout)
            finally:
                if container_id:
                    subprocess.run(
                        ["docker", "rm", "-f", container_id],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                    )
                subprocess.run(
                    compose + ["down", "--volumes", "--remove-orphans"],
                    cwd=release,
                    env=environment,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )


if __name__ == "__main__":
    unittest.main()
