-- ============================================================
-- 33-plugin-configs.sql
-- TIRE Plugin Configuration + Additional API Keys
-- Adds per-plugin enabled/priority/timeout settings and
-- missing API keys (Threatbook, TianjiYoumeng)
-- ============================================================

-- ============================================================
-- Additional API Keys (missing from 30-system-config.sql)
-- ============================================================
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('THREATBOOK_API_KEY',      '', 'tire_api_keys', 'Threatbook (微步在线) API Key', true),
    ('TIANJIYOUMENG_API_KEY',   '', 'tire_api_keys', 'TianjiYoumeng (天际友盟) API Key', true)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Plugin-Level Configuration: Enabled / Priority / Timeout
-- One row per setting per plugin (11 plugins × 3 settings = 33 rows)
-- Category: tire_plugins
-- ============================================================

-- abuseipdb
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_ABUSEIPDB_ENABLED',   'true',  'tire_plugins', 'Enable AbuseIPDB plugin', false),
    ('PLUGIN_ABUSEIPDB_PRIORITY',  '10',    'tire_plugins', 'AbuseIPDB priority (lower = higher)', false),
    ('PLUGIN_ABUSEIPDB_TIMEOUT',   '30',    'tire_plugins', 'AbuseIPDB timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- virustotal
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_VIRUSTOTAL_ENABLED',  'true',  'tire_plugins', 'Enable VirusTotal plugin', false),
    ('PLUGIN_VIRUSTOTAL_PRIORITY', '10',    'tire_plugins', 'VirusTotal priority (lower = higher)', false),
    ('PLUGIN_VIRUSTOTAL_TIMEOUT',  '60',    'tire_plugins', 'VirusTotal timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- otx (AlienVault OTX)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_OTX_ENABLED',   'true',  'tire_plugins', 'Enable AlienVault OTX plugin', false),
    ('PLUGIN_OTX_PRIORITY',  '20',    'tire_plugins', 'OTX priority (lower = higher)', false),
    ('PLUGIN_OTX_TIMEOUT',   '30',    'tire_plugins', 'OTX timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- greynoise
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_GREYNOISE_ENABLED',   'true',  'tire_plugins', 'Enable GreyNoise plugin', false),
    ('PLUGIN_GREYNOISE_PRIORITY',  '20',    'tire_plugins', 'GreyNoise priority (lower = higher)', false),
    ('PLUGIN_GREYNOISE_TIMEOUT',   '30',    'tire_plugins', 'GreyNoise timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- shodan
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_SHODAN_ENABLED',   'true',  'tire_plugins', 'Enable Shodan plugin', false),
    ('PLUGIN_SHODAN_PRIORITY',  '30',    'tire_plugins', 'Shodan priority (lower = higher)', false),
    ('PLUGIN_SHODAN_TIMEOUT',   '30',    'tire_plugins', 'Shodan timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- rdap (no API key needed)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_RDAP_ENABLED',   'true',  'tire_plugins', 'Enable RDAP (IP registration) plugin', false),
    ('PLUGIN_RDAP_PRIORITY',  '5',     'tire_plugins', 'RDAP priority (lower = higher)', false),
    ('PLUGIN_RDAP_TIMEOUT',   '30',    'tire_plugins', 'RDAP timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- reverse_dns (no API key needed)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_REVERSE_DNS_ENABLED',   'true',  'tire_plugins', 'Enable Reverse DNS plugin', false),
    ('PLUGIN_REVERSE_DNS_PRIORITY',  '5',     'tire_plugins', 'Reverse DNS priority (lower = higher)', false),
    ('PLUGIN_REVERSE_DNS_TIMEOUT',   '30',    'tire_plugins', 'Reverse DNS timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- honeynet (no API key needed — uses local data)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_HONEYNET_ENABLED',   'true',  'tire_plugins', 'Enable Honeynet plugin', false),
    ('PLUGIN_HONEYNET_PRIORITY',  '40',    'tire_plugins', 'Honeynet priority (lower = higher)', false),
    ('PLUGIN_HONEYNET_TIMEOUT',   '30',    'tire_plugins', 'Honeynet timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- internal_flow (no API key needed — uses local data)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_INTERNAL_FLOW_ENABLED',   'true',  'tire_plugins', 'Enable Internal Flow plugin', false),
    ('PLUGIN_INTERNAL_FLOW_PRIORITY',  '40',    'tire_plugins', 'Internal Flow priority (lower = higher)', false),
    ('PLUGIN_INTERNAL_FLOW_TIMEOUT',   '30',    'tire_plugins', 'Internal Flow timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- threatbook (微步在线)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_THREATBOOK_ENABLED',   'true',  'tire_plugins', 'Enable Threatbook (微步在线) plugin', false),
    ('PLUGIN_THREATBOOK_PRIORITY',  '15',    'tire_plugins', 'Threatbook priority (lower = higher)', false),
    ('PLUGIN_THREATBOOK_TIMEOUT',   '30',    'tire_plugins', 'Threatbook timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;

-- tianjiyoumeng (天际友盟)
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('PLUGIN_TIANJIYOUMENG_ENABLED',   'true',  'tire_plugins', 'Enable TianjiYoumeng (天际友盟) plugin', false),
    ('PLUGIN_TIANJIYOUMENG_PRIORITY',  '15',    'tire_plugins', 'TianjiYoumeng priority (lower = higher)', false),
    ('PLUGIN_TIANJIYOUMENG_TIMEOUT',   '20',    'tire_plugins', 'TianjiYoumeng timeout (seconds)', false)
ON CONFLICT (config_key) DO NOTHING;
