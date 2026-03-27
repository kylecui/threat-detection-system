-- ============================================================================
-- 23-threat-intel-feeds.sql
-- Feed configuration table for threat intelligence source management
-- ============================================================================

CREATE TABLE IF NOT EXISTS threat_intel_feeds (
    id                      BIGSERIAL PRIMARY KEY,
    feed_name               VARCHAR(100) NOT NULL UNIQUE,
    feed_url                TEXT,
    feed_type               VARCHAR(50) NOT NULL,
    enabled                 BOOLEAN DEFAULT true,
    poll_interval_hours     INTEGER DEFAULT 6,
    api_key_env_var         VARCHAR(100),
    source_weight           DOUBLE PRECISION DEFAULT 0.5,
    last_poll_at            TIMESTAMPTZ,
    last_poll_status        VARCHAR(20),
    last_poll_error         TEXT,
    indicator_count         INTEGER DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_feed_type CHECK (feed_type IN ('REST_API', 'TAXII', 'CSV', 'INTERNAL')),
    CONSTRAINT chk_poll_status CHECK (last_poll_status IS NULL OR last_poll_status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'RATE_LIMITED'))
);

-- Seed the internal honeypot feed (always enabled, highest trust)
INSERT INTO threat_intel_feeds (feed_name, feed_type, enabled, poll_interval_hours, source_weight)
VALUES ('internal', 'INTERNAL', true, 0, 0.95)
ON CONFLICT (feed_name) DO NOTHING;

COMMENT ON TABLE threat_intel_feeds IS 'Configuration for threat intelligence feed sources and their polling status';
COMMENT ON COLUMN threat_intel_feeds.api_key_env_var IS 'Environment variable name containing the API key (never store keys directly)';
COMMENT ON COLUMN threat_intel_feeds.source_weight IS 'Trust weight for this source (0.0-1.0) used in multi-source confidence scoring';
