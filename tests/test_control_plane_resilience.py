#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class ControlPlaneResilienceContractTest(unittest.TestCase):
    def test_multitenant_scope_and_customer_admin_contract(self) -> None:
        scope = (ROOT / "frontend/src/contexts/ScopeContext.tsx").read_text(encoding="utf-8")
        overview = (ROOT / "frontend/src/pages/Overview/index.tsx").read_text(encoding="utf-8")
        devices = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(encoding="utf-8")
        users = (ROOT / "frontend/src/pages/UserMgmt/index.tsx").read_text(encoding="utf-8")
        gateway = (
            ROOT / "services/api-gateway/src/main/java/com/threatdetection/gateway/auth/filter/RbacAuthorizationFilter.java"
        ).read_text(encoding="utf-8")
        customer = (
            ROOT / "services/customer-management/src/main/java/com/threatdetection/customer/service/CustomerService.java"
        ).read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")

        self.assertIn("refreshTenants", scope)
        self.assertIn("refreshCustomers", scope)
        self.assertIn("effectiveCustomerId: customerId", overview)
        self.assertIn("tenantId", overview)
        self.assertNotIn("getCustomersByTenant", overview)
        self.assertNotIn("localStorage.setItem('customer_id'", devices)
        self.assertIn("UserRole.CUSTOMER_ADMIN", users)
        self.assertIn('roles.contains("CUSTOMER_ADMIN")', gateway)
        self.assertIn("validateTenantCapacity", customer)
        self.assertIn("CUSTOMER_ADMIN role is unavailable", customer)
        self.assertIn("apply_database_upgrades", installer)
        self.assertIn("43-customer-admin-role.sql", compose)

    def test_customer_creation_requires_tenant_and_optional_account_is_atomic(self) -> None:
        page = (ROOT / "frontend/src/pages/CustomersAndDevices/index.tsx").read_text(encoding="utf-8")
        request = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/dto/CreateCustomerRequest.java"
        ).read_text(encoding="utf-8")
        service = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/service/CustomerService.java"
        ).read_text(encoding="utf-8")
        handler = (
            ROOT
            / "services/customer-management/src/main/java/com/threatdetection/customer/config/GlobalExceptionHandler.java"
        ).read_text(encoding="utf-8")

        self.assertIn("disabled={!tenantId}", page)
        self.assertIn("window.crypto.getRandomValues", page)
        self.assertIn("`${normalized}-admin`", page)
        self.assertIn('regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$"', request)
        self.assertIn("@Transactional\n    public CustomerResponse createCustomer", service)
        self.assertIn("CUSTOMER_ADMIN", service)
        self.assertIn("passwordEncoder.encode(password)", service)
        self.assertIn("@ExceptionHandler(ResponseStatusException.class)", handler)

        source_without_tests = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "frontend/src").rglob("*")
            if path.is_file() and path.suffix in {".ts", ".tsx", ".json"}
        )
        self.assertNotIn("changeme123", source_without_tests)

    def test_threat_list_requires_scope_and_uses_backend_pagination_contract(self) -> None:
        page = (ROOT / "frontend/src/pages/ThreatList/index.tsx").read_text(encoding="utf-8")
        service = (ROOT / "frontend/src/services/threat.ts").read_text(encoding="utf-8")
        types = (ROOT / "frontend/src/types/index.ts").read_text(encoding="utf-8")

        self.assertRegex(
            page,
            r"!effectiveCustomerId\s*\|\|\s*viewMode\s*!==\s*[\"']detail[\"']",
        )
        self.assertIn("size: pageSize", page)
        self.assertIn("size: ps", page)
        self.assertNotIn("page_size", service)
        self.assertNotIn("page_size", types)

    def test_smtp_empty_state_and_secret_safe_response_contract(self) -> None:
        controller = (
            ROOT
            / "services/alert-management/src/main/java/com/threatdetection/alert/controller/NotificationConfigController.java"
        ).read_text(encoding="utf-8")
        response = (
            ROOT
            / "services/alert-management/src/main/java/com/threatdetection/alert/dto/SmtpConfigResponse.java"
        ).read_text(encoding="utf-8")
        page = (ROOT / "frontend/src/pages/Configuration/NotificationConfig.tsx").read_text(encoding="utf-8")

        self.assertIn("SmtpConfigResponse.unconfigured()", controller)
        self.assertIn('@PutMapping("/smtp/default")', controller)
        self.assertIn("passwordConfigured", response)
        self.assertNotIn("private String password;", response)
        self.assertIn("/api/notification-config/smtp/default', values", page)
        self.assertIn("name=\"active\"", page)

    def test_customer_gateway_has_bounded_non_default_timeout_and_warmup(self) -> None:
        gateway = (ROOT / "services/api-gateway/src/main/resources/application.yml").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        fallback = (
            ROOT
            / "services/api-gateway/src/main/java/com/threatdetection/gateway/controller/FallbackController.java"
        ).read_text(encoding="utf-8")

        self.assertIn("customerManagementCB:", gateway)
        self.assertIn("timeout-duration: 5s", gateway)
        self.assertIn("warm_customer_management_query", installer)
        self.assertIn("/api/v1/customers?page=0&size=1", installer)
        self.assertIn("CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR", fallback)

    def test_ml_readiness_does_not_materialize_training_features(self) -> None:
        main = (ROOT / "services/ml-detection/app/main.py").read_text(encoding="utf-8")
        trainer = (ROOT / "services/ml-detection/app/training/trainer.py").read_text(encoding="utf-8")
        page = (ROOT / "frontend/src/pages/MlDetection/index.tsx").read_text(encoding="utf-8")

        readiness = main[main.index('@app.get("/api/v1/ml/train/data-readiness")'):]
        self.assertIn("count_training_samples", readiness)
        self.assertNotIn("load_from_postgres", readiness)
        self.assertIn("asyncio.to_thread", readiness)
        self.assertIn("GROUP BY detection_tier", trainer)
        self.assertIn("refreshInFlightRef", page)
        self.assertIn("document.visibilityState", page)
        self.assertIn("loadPrimary(showPageLoading)", page)
        self.assertIn("loadSecondary()", page)


if __name__ == "__main__":
    unittest.main()
