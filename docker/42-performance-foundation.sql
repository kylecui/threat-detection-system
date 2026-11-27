-- Query and retention foundation for high-volume production data.
-- These indexes are safe on an empty database and remain compatible with
-- upgrades from earlier releases.

CREATE INDEX IF NOT EXISTS idx_attack_events_customer_time_id
    ON attack_events(customer_id, event_timestamp DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_attack_events_device_time
    ON attack_events(dev_serial, event_timestamp DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_attack_events_mac_time
    ON attack_events(attack_mac, event_timestamp DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_attack_events_received_brin
    ON attack_events USING BRIN(received_at) WITH (pages_per_range = 64);

CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_time_id
    ON threat_assessments(customer_id, assessment_time DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_level_time
    ON threat_assessments(customer_id, threat_level, assessment_time DESC);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_mac_time
    ON threat_assessments(customer_id, attack_mac, assessment_time DESC);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_tier
    ON threat_assessments(customer_id, detection_tier);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_time_brin
    ON threat_assessments USING BRIN(assessment_time) WITH (pages_per_range = 64);

CREATE INDEX IF NOT EXISTS idx_alerts_status_created_id
    ON alerts(status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_severity_created_id
    ON alerts(severity, created_at DESC, id DESC);

ALTER TABLE attack_events SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.01,
    autovacuum_vacuum_threshold = 5000,
    autovacuum_analyze_threshold = 2500
);
ALTER TABLE threat_assessments SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);

COMMENT ON INDEX idx_attack_events_received_brin IS
    'Low-overhead time index for high-volume retention and range scans';
