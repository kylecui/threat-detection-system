-- Migration 27: Add ML weight columns to threat_assessments table
-- Phase 2: ML Detection Integration - advisory multiplier storage for audit

ALTER TABLE threat_assessments
    ADD COLUMN IF NOT EXISTS ml_weight DOUBLE PRECISION DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS pre_ml_score DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_threat_assessments_ml_weight
    ON threat_assessments (ml_weight)
    WHERE ml_weight != 1.0;

COMMENT ON COLUMN threat_assessments.ml_weight IS 'ML advisory multiplier (0.5-3.0), 1.0=neutral/disabled';
COMMENT ON COLUMN threat_assessments.pre_ml_score IS 'Normalized threat score before ML weight applied (for audit/feedback loop prevention)';
