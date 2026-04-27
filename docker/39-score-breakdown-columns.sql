-- Issue #40: Add score breakdown columns to threat_assessments
-- These columns store the individual scoring factors for audit/debug purposes

ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS base_score DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS attack_rate_weight DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS attack_source_weight DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS honeypot_sensitivity_weight DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS combined_segment_weight DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS raw_score DOUBLE PRECISION;
ALTER TABLE threat_assessments ADD COLUMN IF NOT EXISTS attack_rate DOUBLE PRECISION;
