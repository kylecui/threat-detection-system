-- ============================================================================
-- 25-threat-indicators-phase2-upgrade.sql
-- Phase 2 schema upgrade: INET type for IP containment queries
-- ============================================================================

-- Upgrade ioc_inet from VARCHAR(50) to INET for GiST containment queries
ALTER TABLE threat_indicators ALTER COLUMN ioc_inet TYPE INET USING ioc_inet::INET;

-- Create GiST index for CIDR containment (enables << operator)
CREATE INDEX IF NOT EXISTS idx_indicator_inet_gist ON threat_indicators USING GIST (ioc_inet inet_ops)
    WHERE ioc_inet IS NOT NULL;
