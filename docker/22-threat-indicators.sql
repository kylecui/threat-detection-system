-- ============================================================================
-- 22-threat-indicators.sql
-- Threat Intelligence indicators table (STIX 2.1 aligned)
-- ============================================================================

CREATE TABLE IF NOT EXISTS threat_indicators (
    id                  BIGSERIAL PRIMARY KEY,
    
    -- IOC identification
    ioc_value           TEXT NOT NULL,
    ioc_type            VARCHAR(20) NOT NULL,
    ioc_inet            VARCHAR(50),
    
    -- STIX 2.1 Indicator fields
    indicator_type      VARCHAR(50) DEFAULT 'malicious-activity',
    pattern             TEXT,
    pattern_type        VARCHAR(20) DEFAULT 'stix',
    confidence          INTEGER DEFAULT 50 CHECK (confidence BETWEEN 0 AND 100),
    valid_from          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until         TIMESTAMPTZ,
    
    -- Operational extensions
    severity            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    source_name         VARCHAR(100) NOT NULL,
    description         TEXT,
    tags                TEXT,
    
    -- Sighting tracking
    sighting_count      INTEGER DEFAULT 1,
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_indicator_ioc_type CHECK (ioc_type IN ('IP_V4', 'IP_V6', 'CIDR', 'DOMAIN', 'FILE_HASH')),
    CONSTRAINT chk_indicator_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_indicator_inet_for_ip CHECK (
        ioc_type NOT IN ('IP_V4', 'IP_V6', 'CIDR') OR ioc_inet IS NOT NULL
    ),
    CONSTRAINT uq_indicator_ioc_source UNIQUE (ioc_value, source_name)
);

-- Exact IOC lookup (most common query from scoring pipeline)
CREATE INDEX IF NOT EXISTS idx_indicator_ioc_value ON threat_indicators USING HASH (ioc_value);

-- CIDR containment: stored as VARCHAR for now; upgrade to INET + GiST in Phase 2 for << operator
-- CREATE INDEX IF NOT EXISTS idx_indicator_inet_gist ON threat_indicators USING GIST (ioc_inet inet_ops)
--     WHERE ioc_inet IS NOT NULL;

-- Active indicators only (partial index eliminates expired rows)
CREATE INDEX IF NOT EXISTS idx_active_indicators ON threat_indicators (ioc_value, severity, confidence)
    WHERE (valid_until IS NULL OR valid_until > NOW());

-- Source filtering + time ordering
CREATE INDEX IF NOT EXISTS idx_indicator_source ON threat_indicators (source_name, ioc_type, created_at DESC);

-- Tags search (B-tree on TEXT column; upgrade to GIN on text[] in Phase 2)
CREATE INDEX IF NOT EXISTS idx_indicator_tags ON threat_indicators (tags);

-- Time range queries
CREATE INDEX IF NOT EXISTS idx_indicator_valid_range ON threat_indicators (valid_from, valid_until);

COMMENT ON TABLE threat_indicators IS 'Threat intelligence indicators (STIX 2.1 aligned) for IP reputation and IOC matching';
COMMENT ON COLUMN threat_indicators.ioc_value IS 'The indicator value: IP address, CIDR range, domain, or file hash';
COMMENT ON COLUMN threat_indicators.ioc_inet IS 'PostgreSQL INET type for IP/CIDR indicators, enables GiST containment queries';
COMMENT ON COLUMN threat_indicators.confidence IS 'Confidence score 0-100 per STIX 2.1 spec section 3.2';
COMMENT ON COLUMN threat_indicators.source_name IS 'Source of the indicator: internal, abuseipdb, otx, virustotal, etc.';
