-- Keep existing installations aligned with the runtime TIRE key names and
-- restore any built-in feed definitions that may be missing. Operator choices
-- such as enabled state and polling intervals are intentionally preserved.
BEGIN;

INSERT INTO threat_intel_feeds
    (feed_name, feed_url, feed_type, enabled, poll_interval_hours, api_key_env_var, source_weight)
VALUES
    ('abuseipdb', 'https://api.abuseipdb.com/api/v2/blacklist', 'REST_API', false, 6, 'ABUSEIPDB_API_KEY', 0.85),
    ('alienvault_otx', 'https://otx.alienvault.com/api/v1/indicators/export', 'REST_API', false, 4, 'OTX_API_KEY', 0.75),
    ('virustotal', 'https://www.virustotal.com/api/v3/ip_addresses', 'REST_API', false, 12, 'VT_API_KEY', 0.80),
    ('greynoise', 'https://api.greynoise.io/v3/community', 'REST_API', false, 8, 'GREYNOISE_API_KEY', 0.70)
ON CONFLICT (feed_name) DO NOTHING;

UPDATE threat_intel_feeds
SET api_key_env_var = 'VT_API_KEY', updated_at = CURRENT_TIMESTAMP
WHERE feed_name = 'virustotal'
  AND api_key_env_var IS DISTINCT FROM 'VT_API_KEY';

INSERT INTO tds_schema_migrations (migration_name)
VALUES ('48-threat-intel-feed-contract.sql')
ON CONFLICT (migration_name) DO NOTHING;

COMMIT;
