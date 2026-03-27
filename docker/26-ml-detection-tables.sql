-- ============================================================
-- Migration 26: ML Detection Tables
-- Purpose: Store ML prediction results and per-customer
--          normalization statistics for the ml-detection service
-- ============================================================

-- ml_predictions: Stores every ML inference result
CREATE TABLE IF NOT EXISTS ml_predictions (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45),
    tier SMALLINT NOT NULL CHECK (tier IN (1, 2, 3)),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    ml_score DOUBLE PRECISION NOT NULL,
    ml_weight DOUBLE PRECISION NOT NULL,
    ml_confidence DOUBLE PRECISION NOT NULL,
    anomaly_type VARCHAR(50) NOT NULL,
    reconstruction_error DOUBLE PRECISION,
    threshold DOUBLE PRECISION,
    model_version VARCHAR(100) NOT NULL,
    pre_ml_threat_score DOUBLE PRECISION,
    pre_ml_threat_level VARCHAR(20),
    feature_vector DOUBLE PRECISION[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT idx_ml_pred_unique UNIQUE (customer_id, attack_mac, tier, window_start)
);

-- Indexes for common query patterns
CREATE INDEX idx_ml_pred_customer_time ON ml_predictions (customer_id, created_at DESC);
CREATE INDEX idx_ml_pred_anomaly ON ml_predictions (anomaly_type, ml_score DESC) WHERE anomaly_type != 'normal';
CREATE INDEX idx_ml_pred_tier ON ml_predictions (tier, created_at DESC);

-- ml_customer_stats: Per-customer rolling normalization stats (Welford's algorithm)
CREATE TABLE IF NOT EXISTS ml_customer_stats (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    tier SMALLINT NOT NULL CHECK (tier IN (1, 2, 3)),
    feature_name VARCHAR(50) NOT NULL,
    rolling_mean DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rolling_std DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sample_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_tier_feature UNIQUE (customer_id, tier, feature_name)
);

CREATE INDEX idx_ml_stats_customer ON ml_customer_stats (customer_id, tier);
