-- Device data-flow observability indexes used by customer-management.
-- Idempotent and safe for both fresh installations and upgrades.
CREATE INDEX IF NOT EXISTS idx_device_status_history_serial_report
    ON device_status_history (UPPER(dev_serial), report_time DESC);

CREATE INDEX IF NOT EXISTS idx_attack_events_device_mac_time
    ON attack_events (UPPER(dev_serial), customer_id, attack_mac, event_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_mac_time
    ON threat_assessments (customer_id, attack_mac, assessment_time DESC);

INSERT INTO tds_schema_migrations (migration_name)
VALUES ('44-device-flow-observability.sql')
ON CONFLICT (migration_name) DO NOTHING;
