BEGIN;

-- Long-range overview queries aggregate by time, port and normalized MAC.
-- Keep these access paths available on both fresh installs and upgrades.
CREATE INDEX IF NOT EXISTS idx_attack_events_customer_port_time
    ON attack_events(customer_id, response_port, event_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_upper_mac_time
    ON threat_assessments(customer_id, UPPER(attack_mac), assessment_time DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_time_tier
    ON threat_assessments(customer_id, assessment_time DESC, detection_tier);

INSERT INTO tds_schema_migrations(migration_name)
VALUES ('47-overview-query-performance.sql')
ON CONFLICT (migration_name) DO NOTHING;

COMMIT;
