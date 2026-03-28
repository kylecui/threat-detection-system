-- Migration 28: Multi-Region Support
-- Adds region column to key tables for cross-region data tracking

-- Add region to threat_assessments
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS region VARCHAR(20) DEFAULT 'east';
CREATE INDEX IF NOT EXISTS idx_threat_assessments_region ON threat_assessments(region);

-- Add region to attack_events_raw (if table exists)
DO $$
BEGIN
  IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'attack_events_raw') THEN
    ALTER TABLE attack_events_raw ADD COLUMN IF NOT EXISTS region VARCHAR(20) DEFAULT 'east';
    CREATE INDEX IF NOT EXISTS idx_attack_events_raw_region ON attack_events_raw(region);
  END IF;
END $$;

-- Add region to alerts
DO $$
BEGIN
  IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'alerts') THEN
    ALTER TABLE alerts ADD COLUMN IF NOT EXISTS region VARCHAR(20) DEFAULT 'east';
    CREATE INDEX IF NOT EXISTS idx_alerts_region ON alerts(region);
  END IF;
END $$;

-- Add region to ml_detections
DO $$
BEGIN
  IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'ml_detections') THEN
    ALTER TABLE ml_detections ADD COLUMN IF NOT EXISTS region VARCHAR(20) DEFAULT 'east';
    CREATE INDEX IF NOT EXISTS idx_ml_detections_region ON ml_detections(region);
  END IF;
END $$;

-- Add region to threat_indicators
DO $$
BEGIN
  IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'threat_indicators') THEN
    ALTER TABLE threat_indicators ADD COLUMN IF NOT EXISTS region VARCHAR(20) DEFAULT 'east';
  END IF;
END $$;

-- Region metadata table for tracking active regions
CREATE TABLE IF NOT EXISTS regions (
    region_id VARCHAR(20) PRIMARY KEY,
    region_label VARCHAR(100) NOT NULL,
    api_endpoint VARCHAR(255),
    kafka_bootstrap VARCHAR(255),
    postgres_role VARCHAR(20) DEFAULT 'standby',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed default regions
INSERT INTO regions (region_id, region_label, api_endpoint, kafka_bootstrap, postgres_role) VALUES
  ('east', 'US East', 'https://east.threat-detection.io', 'kafka.threat-detection-east:29092', 'primary'),
  ('west', 'US West', 'https://west.threat-detection.io', 'kafka.threat-detection-west:29092', 'standby'),
  ('cn', 'China', 'https://cn.threat-detection.io', 'kafka.threat-detection-cn:29092', 'primary')
ON CONFLICT (region_id) DO NOTHING;

COMMENT ON TABLE regions IS 'Multi-region deployment configuration and metadata';
