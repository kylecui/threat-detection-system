from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class DatabaseMigrationLedgerTest(unittest.TestCase):
    def test_fresh_database_creates_ledger_before_other_init_scripts(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn(
            "./00-schema-migrations.sql:/docker-entrypoint-initdb.d/00-schema-migrations.sql",
            compose,
        )
        ledger = (ROOT / "docker/00-schema-migrations.sql").read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE tds_schema_migrations", ledger)
        self.assertIn("migration_name VARCHAR(255) PRIMARY KEY", ledger)

    def test_installer_skips_recorded_migrations_instead_of_blind_replay(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        function = installer.split("apply_database_upgrades() {", 1)[1].split("\n}", 1)[0]
        self.assertIn("SELECT migration_name FROM tds_schema_migrations", function)
        self.assertIn('grep -Fxq "$migration_name"', function)
        self.assertIn("Database upgrade already recorded; skipping", function)
        self.assertIn('applied=$applied_count, already_present=$skipped_count', function)

    def test_legacy_upgrade_bootstraps_ledger_and_keeps_sql_errors_fatal(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        function = installer.split("apply_database_upgrades() {", 1)[1].split("\n}", 1)[0]
        self.assertIn("CREATE TABLE IF NOT EXISTS tds_schema_migrations", function)
        self.assertIn("client_min_messages=warning", function)
        self.assertIn("ON_ERROR_STOP=1", function)
        self.assertIn("Database upgrade did not record completion", function)

    def test_installer_managed_migrations_record_completion(self) -> None:
        for name in (
            "43-customer-admin-role.sql",
            "44-device-flow-observability.sql",
            "45-device-binding-integrity.sql",
            "46-threat-window-idempotency.sql",
            "47-overview-query-performance.sql",
            "48-threat-intel-feed-contract.sql",
            "49-notification-runtime-reliability.sql",
        ):
            sql = (ROOT / "docker" / name).read_text(encoding="utf-8")
            self.assertIn("INSERT INTO tds_schema_migrations", sql, name)
            self.assertIn(f"VALUES ('{name}')", sql, name)
            self.assertIn("ON CONFLICT (migration_name) DO NOTHING", sql, name)

    def test_notification_runtime_migration_is_part_of_fresh_install_and_upgrade(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        migration = (ROOT / "docker/49-notification-runtime-reliability.sql").read_text(
            encoding="utf-8"
        )
        self.assertIn("49-notification-runtime-reliability.sql", compose)
        self.assertIn("49-notification-runtime-reliability.sql", installer)
        self.assertIn("ADD COLUMN IF NOT EXISTS customer_id", migration)
        self.assertIn("ADD COLUMN IF NOT EXISTS next_retry_at", migration)
        self.assertIn("idx_notifications_retry_due", migration)


if __name__ == "__main__":
    unittest.main()
