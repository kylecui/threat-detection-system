#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class PerformanceAndBootstrapContractTest(unittest.TestCase):
    def test_fresh_install_has_no_demo_business_rows_or_default_login(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        mounted = re.findall(
            r"^\s*- \./([^:]+):/docker-entrypoint-initdb\.d/[^\s]+",
            compose,
            flags=re.MULTILINE,
        )
        sql = "\n".join((ROOT / "docker" / name).read_text(encoding="utf-8") for name in mounted)
        for forbidden in (
            "admin123",
            "demo_admin",
            "demo-customer",
            "'customer_a'",
            "'customer_b'",
            "'customer_test'",
            "kylecui@outlook.com",
        ):
            self.assertNotIn(forbidden, sql)

        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.assertNotIn("admin / admin123", readme)
        self.assertNotIn("admin / admin123", installer)
        self.assertIn("/api/v1/auth/bootstrap/status", installer)

    def test_ingestion_and_stream_processing_are_bounded_and_parallel(self) -> None:
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        ingestion = (ROOT / "services/data-ingestion/src/main/resources/application-docker.yml").read_text(encoding="utf-8")
        stream = (ROOT / "services/stream-processing/src/main/java/com/threatdetection/stream/StreamProcessingJob.java").read_text(encoding="utf-8")
        attack_sink = (ROOT / "services/stream-processing/src/main/java/com/threatdetection/stream/sink/AttackEventJdbcSink.java").read_text(encoding="utf-8")
        apt_sink = (ROOT / "services/stream-processing/src/main/java/com/threatdetection/stream/service/APTTemporalAccumulator.java").read_text(encoding="utf-8")

        self.assertIn("KAFKA_TOPIC_PARTITIONS:-12", compose)
        self.assertIn("KAFKA_CONSUMER_CONCURRENCY", compose)
        self.assertIn("INGEST_EXECUTOR_QUEUE_CAPACITY", compose)
        self.assertIn("flink_checkpoints", compose)
        self.assertIn("flink-checkpoint-init:", compose)
        self.assertIn("chown -R 9999:9999 /opt/flink/checkpoints", compose)
        self.assertIn("condition: service_completed_successfully", compose)
        self.assertIn("compression-type: zstd", ingestion)
        self.assertIn("enable.idempotence: true", ingestion)
        self.assertIn("JDBC_SINK_PARALLELISM", stream)
        self.assertIn('System.getenv().getOrDefault(\n            "DB_PASSWORD"', attack_sink)
        self.assertIn('System.getenv().getOrDefault(\n            "DB_PASSWORD"', apt_sink)

    def test_overview_queries_and_storage_have_large_table_indexes(self) -> None:
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")
        query_service = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/service/ThreatQueryService.java").read_text(encoding="utf-8")
        schema = (ROOT / "docker/42-performance-foundation.sql").read_text(encoding="utf-8")

        self.assertIn("COUNT(*) FILTER", repository)
        self.assertIn("getStatisticsAggregate(customerId)", query_service)
        self.assertIn("USING BRIN(received_at)", schema)
        self.assertIn("customer_id, event_timestamp DESC, id DESC", schema)

    def test_3_1_30_overview_is_bucketed_cached_and_deduplicated(self) -> None:
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")
        query_service = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/service/ThreatQueryService.java").read_text(encoding="utf-8")
        cache_config = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/config/CacheConfig.java").read_text(encoding="utf-8")
        frontend_service = (ROOT / "frontend/src/services/threat.ts").read_text(encoding="utf-8")
        overview = (ROOT / "frontend/src/pages/Overview/index.tsx").read_text(encoding="utf-8")
        migration = (ROOT / "docker/47-overview-query-performance.sql").read_text(encoding="utf-8")

        self.assertIn("DATE_TRUNC('day', assessment_time)", repository)
        self.assertIn("findDistinctRecentByCustomer", repository)
        self.assertIn("response_port BETWEEN 1 AND 65535", repository)
        self.assertIn("getNonPortProtocolRows", repository)
        self.assertIn("safeHours > 48", query_service)
        self.assertIn("fillTrendBuckets", query_service)
        self.assertIn('@Cacheable(', query_service)
        self.assertIn('registerCustomCache("overviewSnapshots"', cache_config)
        self.assertIn("overviewInFlight", frontend_service)
        self.assertIn("overviewCache", frontend_service)
        self.assertIn("smooth: false", overview)
        self.assertNotIn("areaStyle", overview)
        self.assertIn("idx_attack_events_customer_port_time", migration)

    def test_overview_non_port_protocol_sql_is_hibernate_safe_and_null_aware(self) -> None:
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")

        self.assertNotIn("::int", repository)
        self.assertIn("AS integer", repository)
        self.assertIn("COALESCE(ae.response_port, 0) NOT BETWEEN 1 AND 65535", repository)
        self.assertIn("getNonPortProtocolRowsForTenant", repository)

    def test_3_1_32_threat_intelligence_contract_and_feed_upgrade(self) -> None:
        frontend = (ROOT / "frontend/src/pages/ThreatIntel/index.tsx").read_text(encoding="utf-8")
        types = (ROOT / "frontend/src/types/index.ts").read_text(encoding="utf-8")
        statistics = (ROOT / "services/threat-intelligence/src/main/java/com/threatdetection/intelligence/dto/StatisticsResponse.java").read_text(encoding="utf-8")
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        migration = (ROOT / "docker/48-threat-intel-feed-contract.sql").read_text(encoding="utf-8")

        for field in ("feedName", "feedUrl", "lastPollAt", "lastPollStatus"):
            self.assertIn(field, types)
            self.assertIn(field, frontend)
        self.assertIn("totalFeeds", statistics)
        self.assertIn("activeFeeds", statistics)
        self.assertIn("VT_API_KEY", migration)
        self.assertIn("48-threat-intel-feed-contract.sql", compose)
        self.assertIn("48-threat-intel-feed-contract.sql", installer)

    def test_3_1_32_threat_delete_endpoints_are_scoped_and_atomic(self) -> None:
        controller = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/controller/AssessmentController.java").read_text(encoding="utf-8")
        service = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/service/ThreatQueryService.java").read_text(encoding="utf-8")
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")

        self.assertIn('@DeleteMapping("/{assessmentId}")', controller)
        self.assertIn('@PostMapping("/batch-delete")', controller)
        self.assertIn("verifyDeleteScope", service)
        self.assertIn("deleteAllInBatch", service)
        self.assertIn("existsByIdAndTenantId", repository)

    def test_3_1_30_detection_windows_are_persisted_and_idempotent(self) -> None:
        entity = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/model/ThreatAssessment.java").read_text(encoding="utf-8")
        service = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/service/ThreatAssessmentService.java").read_text(encoding="utf-8")
        migration = (ROOT / "docker/46-threat-window-idempotency.sql").read_text(encoding="utf-8")
        threat_list = (ROOT / "frontend/src/pages/ThreatList/index.tsx").read_text(encoding="utf-8")

        self.assertIn("windowStart", entity)
        self.assertIn("windowEnd", entity)
        self.assertIn("findFirstByCustomerIdAndAttackMacIgnoreCaseAndDetectionTierAndWindowStart", service)
        self.assertIn("uq_threat_assessment_window", migration)
        self.assertIn("detection_tier", threat_list)
        self.assertIn("window30s", threat_list)

    def test_3_1_23_overview_and_device_flow_are_bounded(self) -> None:
        controller = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/controller/AssessmentController.java").read_text(encoding="utf-8")
        repository = (ROOT / "services/threat-assessment/src/main/java/com/threatdetection/assessment/repository/ThreatAssessmentRepository.java").read_text(encoding="utf-8")
        device_controller = (ROOT / "services/customer-management/src/main/java/com/threatdetection/customer/device/controller/DeviceManagementController.java").read_text(encoding="utf-8")
        device_service = (ROOT / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceManagementService.java").read_text(encoding="utf-8")
        scope_selector = (ROOT / "frontend/src/components/ScopeSelector.tsx").read_text(encoding="utf-8")
        overview = (ROOT / "frontend/src/pages/Overview/index.tsx").read_text(encoding="utf-8")
        gateway = (ROOT / "services/api-gateway/src/main/resources/application.yml").read_text(encoding="utf-8")
        rbac_filter = (ROOT / "services/api-gateway/src/main/java/com/threatdetection/gateway/auth/filter/RbacAuthorizationFilter.java").read_text(encoding="utf-8")

        self.assertIn('@GetMapping("/overview-snapshot")', controller)
        self.assertIn("getStatisticsAggregateForTenant", repository)
        self.assertIn("findRecentByTenant", repository)
        self.assertIn("getOverviewSnapshot", overview)
        self.assertNotIn("Promise.all([", overview)
        self.assertIn('@GetMapping("/{devSerial}/events")', device_controller)
        self.assertIn("LIMIT ? OFFSET ?", device_service)
        self.assertIn("COUNT(*) FILTER (WHERE received_at >= NOW() - INTERVAL '24 hours')", device_service)
        self.assertIn("searchTenants", scope_selector)
        self.assertIn("searchCustomers", scope_selector)
        self.assertIn("threatAssessmentCB:", gateway)
        self.assertIn("alertManagementCB:", gateway)
        self.assertIn('replaceQueryParam("tenant_id", tenantId)', rbac_filter)

    def test_sparse_kafka_partitions_cannot_hold_watermarks_forever(self) -> None:
        stream = (ROOT / "services/stream-processing/src/main/java/com/threatdetection/stream/StreamProcessingJob.java").read_text(encoding="utf-8")
        self.assertIn("withIdleness(java.time.Duration.ofSeconds(45))", stream)
        self.assertIn("forBoundedOutOfOrderness(java.time.Duration.ofSeconds(30))", stream)

    def test_v1_syslog_heartbeat_policy_matches_client_cadence(self) -> None:
        policy = (ROOT / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceTelemetryPolicy.java").read_text(encoding="utf-8")
        device_service = (ROOT / "services/customer-management/src/main/java/com/threatdetection/customer/device/service/DeviceManagementService.java").read_text(encoding="utf-8")
        device_type = (ROOT / "frontend/src/types/index.ts").read_text(encoding="utf-8")
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")

        self.assertIn("expected-heartbeat-interval-seconds:1800", policy)
        self.assertIn("heartbeat-delayed-after-seconds:2100", policy)
        self.assertIn("heartbeat-offline-after-seconds:3900", policy)
        self.assertIn("dataActivityState", device_service)
        self.assertIn("heartbeatState", device_type)
        self.assertIn("DEVICE_HEARTBEAT_OFFLINE_AFTER_SECONDS:-3900", compose)
        self.assertNotIn("now.minus(Duration.ofMinutes(5))", device_service)

    def test_installer_requires_business_readiness_and_stability(self) -> None:
        installer = (ROOT / "install.sh").read_text(encoding="utf-8")
        compose = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("wait_for_platform_readiness", installer)
        self.assertIn("verify_restart_stability", installer)
        self.assertIn("verify_fresh_database_is_empty", installer)
        self.assertIn("verify_kafka_topics", installer)
        self.assertIn("three consecutive successful checks", installer)
        start_block = installer[
            installer.index('info "Starting the Docker Compose stack through systemd..."'):
        ]
        self.assertNotIn('compose_in "$TARGET_DIR" up -d --remove-orphans', start_block)
        self.assertIn('systemctl start "$UNIT_NAME"', start_block)
        frontend_start = compose.index("\n  frontend:")
        frontend_block = compose[
            frontend_start:compose.index("\n  topic-init:", frontend_start)
        ]
        self.assertIn("condition: service_started", frontend_block)
        self.assertNotIn("condition: service_healthy", frontend_block)

    def test_alert_management_uses_jdbc4_validation_before_pool_start(self) -> None:
        properties = (
            ROOT
            / "services/alert-management/src/main/resources/application-docker.properties"
        ).read_text(encoding="utf-8")
        self.assertNotIn("spring.datasource.hikari.connection-test-query", properties)


if __name__ == "__main__":
    unittest.main()
