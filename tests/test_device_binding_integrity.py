#!/usr/bin/env python3
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parent.parent


class DeviceBindingIntegrityContractTest(unittest.TestCase):
    def test_database_enforces_one_current_binding_per_serial(self) -> None:
        migration = (ROOT / "docker/45-device-binding-integrity.sql").read_text(encoding="utf-8")
        self.assertIn("PARTITION BY UPPER(BTRIM(dev_serial))", migration)
        self.assertIn("CREATE UNIQUE INDEX IF NOT EXISTS uq_device_current_serial_ci", migration)
        self.assertIn("WHERE is_active IS TRUE AND unbind_time IS NULL", migration)

    def test_migration_runs_for_fresh_install_and_upgrade(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        mount = "./45-device-binding-integrity.sql:/docker-entrypoint-initdb.d/45-device-binding-integrity.sql"
        self.assertIn(mount, compose)
        self.assertIn("45-device-binding-integrity.sql", installer)

    def test_customer_management_serializes_binding_and_keeps_history(self) -> None:
        service = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceManagementService.java"
        ).read_text(encoding="utf-8")
        repository = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/device/repository/DeviceMappingRepository.java"
        ).read_text(encoding="utf-8")
        self.assertIn('acquireBindingLock("device:" + devSerial)', service)
        self.assertIn("pg_advisory_xact_lock", service)
        self.assertIn("setUnbindTime(Instant.now())", service)
        self.assertIn("setIsActive(false)", service)
        self.assertIn("IsActiveTrueAndUnbindTimeIsNull", repository)

    def test_backfill_is_invoked_through_a_separate_async_bean(self) -> None:
        service = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceManagementService.java"
        ).read_text(encoding="utf-8")
        backfill = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceBackfillService.java"
        ).read_text(encoding="utf-8")
        self.assertIn("deviceBackfillService.backfillCustomerId", service)
        self.assertNotIn("public void backfillCustomerIdForDevice", service)
        self.assertIn("@Async", backfill)

    def test_frontend_uses_synchronous_submit_guards(self) -> None:
        hook = (ROOT / "frontend/src/hooks/useSubmitGuard.ts").read_text(encoding="utf-8")
        customer_page = (
            ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx"
        ).read_text(encoding="utf-8")
        self.assertIn("lockedRef.current", hook)
        self.assertIn("if (!deviceMutation.begin()) return", customer_page)
        self.assertIn("if (!createMutation.begin()) return", customer_page)
        self.assertIn("confirmLoading={deviceMutation.submitting}", customer_page)

    def test_batch_api_contract_is_checked_before_success_feedback(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(encoding="utf-8")
        types = (ROOT / "frontend/src/types/index.ts").read_text(encoding="utf-8")
        self.assertIn("DeviceBatchOperationResult", types)
        self.assertIn("result.succeeded !== 1 || result.failed !== 0", page)
        self.assertIn("result.failures[0]?.reason", page)


if __name__ == "__main__":
    unittest.main()
