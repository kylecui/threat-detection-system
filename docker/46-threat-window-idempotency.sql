BEGIN;

ALTER TABLE threat_assessments
    ADD COLUMN IF NOT EXISTS window_start TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS window_end TIMESTAMP WITH TIME ZONE;

-- Old rows have no stable window identity and remain valid history. New rows
-- are protected against Kafka redelivery and duplicate Flink graph output.
CREATE UNIQUE INDEX IF NOT EXISTS uq_threat_assessment_window
    ON threat_assessments(customer_id, UPPER(attack_mac), detection_tier, window_start)
    WHERE window_start IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_threat_assessment_window_end
    ON threat_assessments(customer_id, window_end DESC)
    WHERE window_end IS NOT NULL;

INSERT INTO tds_schema_migrations(migration_name)
VALUES ('46-threat-window-idempotency.sql')
ON CONFLICT (migration_name) DO NOTHING;

COMMIT;
