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
    os.environ.get("TDS_RUN_POSTGRES_INIT_TEST") == "1",
    "set TDS_RUN_POSTGRES_INIT_TEST=1 on a Docker build host",
)
class FreshPostgresInitializationTest(unittest.TestCase):
    def test_every_mounted_sql_script_executes_on_an_empty_volume(self) -> None:
        subprocess.run([sys.executable, str(ROOT / "scripts/pack_release.py")], check=True)
        project = f"tds-pg-init-{os.getpid()}"
        container_id = ""

        with tempfile.TemporaryDirectory(prefix="tds-pg-init-") as temp:
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
            values = {}
            for line in env_file.read_text(encoding="utf-8").splitlines():
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    values[key] = value

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
                    compose + ["run", "-d", "--no-deps", "postgres"],
                    cwd=release,
                    env=environment,
                    check=True,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                )
                container_id = result.stdout.strip().splitlines()[-1]
                deadline = time.monotonic() + 240
                logs = ""
                while time.monotonic() < deadline:
                    logs = subprocess.check_output(
                        ["docker", "logs", container_id],
                        text=True,
                        stderr=subprocess.STDOUT,
                    )
                    if "ERROR:" in logs:
                        self.fail(logs)
                    ready = subprocess.run(
                        [
                            "docker",
                            "exec",
                            container_id,
                            "pg_isready",
                            "-U",
                            values["POSTGRES_USER"],
                            "-d",
                            values["POSTGRES_DB"],
                        ],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                    )
                    health = subprocess.check_output(
                        [
                            "docker",
                            "inspect",
                            "-f",
                            "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}",
                            container_id,
                        ],
                        text=True,
                    ).strip()
                    if (
                        ready.returncode == 0
                        and "PostgreSQL init process complete; ready for start up." in logs
                        and health == "healthy"
                    ):
                        break
                    status = subprocess.check_output(
                        ["docker", "inspect", "-f", "{{.State.Status}}", container_id],
                        text=True,
                    ).strip()
                    if status == "exited":
                        self.fail("PostgreSQL exited during fresh initialization")
                    time.sleep(2)
                else:
                    self.fail("PostgreSQL did not become ready within 240 seconds")

                logs = subprocess.check_output(
                    ["docker", "logs", container_id],
                    text=True,
                    stderr=subprocess.STDOUT,
                )
                restart_count = subprocess.check_output(
                    ["docker", "inspect", "-f", "{{.RestartCount}}", container_id],
                    text=True,
                ).strip()
                self.assertEqual(restart_count, "0", logs)
                self.assertNotIn("ERROR:", logs, logs)
                self.assertIn(
                    "running /docker-entrypoint-initdb.d/00-schema-migrations.sql",
                    logs,
                )
                self.assertIn(
                    "running /docker-entrypoint-initdb.d/42-performance-foundation.sql",
                    logs,
                )
                self.assertIn(
                    "running /docker-entrypoint-initdb.d/44-device-flow-observability.sql",
                    logs,
                )

                query = """
SELECT string_agg(name, ',' ORDER BY name)
FROM (VALUES
  (to_regclass('public.port_risk_configs')::text),
  (to_regclass('public.whitelist_config')::text),
  (to_regclass('public.auth_users')::text),
  (to_regclass('public.device_status_history')::text)
) AS required(name)
WHERE name IS NOT NULL;
"""
                tables = subprocess.check_output(
                    [
                        "docker",
                        "exec",
                        container_id,
                        "psql",
                        "-v",
                        "ON_ERROR_STOP=1",
                        "-U",
                        values["POSTGRES_USER"],
                        "-d",
                        values["POSTGRES_DB"],
                        "-Atc",
                        query,
                    ],
                    text=True,
                ).strip()
                self.assertEqual(
                    set(tables.split(",")),
                    {"auth_users", "device_status_history", "port_risk_configs", "whitelist_config"},
                )

                query_contract = r"""
SELECT count(*) FROM (
  SELECT CAST(port_value AS INTEGER) AS port, COUNT(*) AS port_count
  FROM threat_assessments ta
  CROSS JOIN LATERAL regexp_split_to_table(
    regexp_replace(COALESCE(ta.port_list, ''), '[\[\]\s]', '', 'g'), ','
  ) AS port_value
  WHERE ta.customer_id = 'contract-test'
    AND ta.assessment_time >= NOW() - INTERVAL '24 hours'
    AND port_value ~ '^[0-9]+$'
  GROUP BY port_value ORDER BY port_count DESC LIMIT 15
) overview_ports;

SELECT count(*) FROM (
  SELECT dcm.id, hb.report_time, att.last_attack_at, thr.last_threat_at
  FROM device_customer_mapping dcm
  LEFT JOIN LATERAL (
    SELECT report_time FROM device_status_history
    WHERE UPPER(dev_serial) = UPPER(dcm.dev_serial)
    ORDER BY report_time DESC LIMIT 1
  ) hb ON TRUE
  LEFT JOIN LATERAL (
    SELECT MAX(event_timestamp) AS last_attack_at FROM attack_events ae
    WHERE ae.customer_id = dcm.customer_id
      AND UPPER(ae.dev_serial) = UPPER(dcm.dev_serial)
  ) att ON TRUE
  LEFT JOIN LATERAL (
    SELECT MAX(ta.assessment_time) AS last_threat_at FROM threat_assessments ta
    WHERE ta.customer_id = dcm.customer_id AND EXISTS (
      SELECT 1 FROM attack_events ae2
      WHERE ae2.customer_id = dcm.customer_id
        AND UPPER(ae2.dev_serial) = UPPER(dcm.dev_serial)
        AND ae2.attack_mac = ta.attack_mac
    )
  ) thr ON TRUE
) device_flow;

SELECT count(*) FROM pg_indexes
WHERE indexname IN (
  'idx_device_status_history_serial_report',
  'idx_attack_events_device_mac_time',
  'idx_threat_assessments_customer_mac_time'
);
"""
                contract_result = subprocess.check_output(
                    [
                        "docker", "exec", container_id, "psql", "-v", "ON_ERROR_STOP=1",
                        "-U", values["POSTGRES_USER"], "-d", values["POSTGRES_DB"],
                        "-Atc", query_contract,
                    ],
                    text=True,
                ).strip().splitlines()
                self.assertEqual(contract_result, ["0", "0", "3"])

                migrations = subprocess.check_output(
                    [
                        "docker", "exec", container_id, "psql", "-v", "ON_ERROR_STOP=1",
                        "-U", values["POSTGRES_USER"], "-d", values["POSTGRES_DB"], "-Atc",
                        "SELECT migration_name FROM tds_schema_migrations ORDER BY migration_name;",
                    ],
                    text=True,
                ).strip().splitlines()
                self.assertEqual(
                    migrations,
                    [
                        "43-customer-admin-role.sql",
                        "44-device-flow-observability.sql",
                        "45-device-binding-integrity.sql",
                        "46-threat-window-idempotency.sql",
                        "47-overview-query-performance.sql",
                        "48-threat-intel-feed-contract.sql",
                        "49-notification-runtime-reliability.sql",
                    ],
                )

                business_rows = subprocess.check_output(
                    [
                        "docker", "exec", container_id, "psql", "-v", "ON_ERROR_STOP=1",
                        "-U", values["POSTGRES_USER"], "-d", values["POSTGRES_DB"], "-Atc",
                        "SELECT (SELECT count(*) FROM auth_users) || ':' || "
                        "(SELECT count(*) FROM customers) || ':' || "
                        "(SELECT count(*) FROM device_customer_mapping) || ':' || "
                        "(SELECT count(*) FROM attack_events) || ':' || "
                        "(SELECT count(*) FROM alerts) || ':' || "
                        "(SELECT count(*) FROM threat_assessments);",
                    ],
                    text=True,
                ).strip()
                self.assertEqual(business_rows, "0:0:0:0:0:0")
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
