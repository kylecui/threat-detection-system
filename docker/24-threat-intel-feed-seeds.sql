-- ============================================================================
-- 24-threat-intel-feed-seeds.sql
-- Seed external threat intelligence feed configurations
-- All feeds start disabled — operators enable after providing API keys
-- ============================================================================

INSERT INTO threat_intel_feeds (feed_name, feed_url, feed_type, enabled, poll_interval_hours, api_key_env_var, source_weight)
VALUES
    ('abuseipdb', 'https://api.abuseipdb.com/api/v2/blacklist', 'REST_API', false, 6, 'ABUSEIPDB_API_KEY', 0.85),
    ('alienvault_otx', 'https://otx.alienvault.com/api/v1/indicators/export', 'REST_API', false, 4, 'OTX_API_KEY', 0.75),
    ('virustotal', 'https://www.virustotal.com/api/v3/ip_addresses', 'REST_API', false, 12, 'VIRUSTOTAL_API_KEY', 0.80),
    ('greynoise', 'https://api.greynoise.io/v3/community', 'REST_API', false, 8, 'GREYNOISE_API_KEY', 0.70)
ON CONFLICT (feed_name) DO NOTHING;
