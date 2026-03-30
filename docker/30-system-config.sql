-- ============================================================
-- 30-system-config.sql
-- System Configuration Key-Value Store
-- Stores TIRE API keys, LLM config, and other system settings
-- ============================================================

CREATE TABLE IF NOT EXISTS system_config (
    id          SERIAL PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL DEFAULT '',
    category    VARCHAR(50) NOT NULL DEFAULT 'general',
    description VARCHAR(255),
    is_secret   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_config_key ON system_config(config_key);
CREATE INDEX IF NOT EXISTS idx_system_config_category ON system_config(category);

-- ============================================================
-- Seed: TIRE Threat Intelligence API Keys
-- ============================================================
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('ABUSEIPDB_API_KEY',  '', 'tire_api_keys', 'AbuseIPDB API Key for IP reputation lookup', true),
    ('OTX_API_KEY',        '', 'tire_api_keys', 'AlienVault OTX API Key for threat intelligence', true),
    ('GREYNOISE_API_KEY',  '', 'tire_api_keys', 'GreyNoise API Key for IP noise detection', true),
    ('VT_API_KEY',         '', 'tire_api_keys', 'VirusTotal API Key for file/URL/IP scanning', true),
    ('SHODAN_API_KEY',     '', 'tire_api_keys', 'Shodan API Key for internet-connected device search', true)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed: LLM Configuration
-- ============================================================
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('LLM_API_KEY',   '', 'llm', 'LLM API Key (e.g., OpenAI, DeepSeek)', true),
    ('LLM_MODEL',     '', 'llm', 'LLM Model name (e.g., gpt-4o, deepseek-chat)', false),
    ('LLM_BASE_URL',  '', 'llm', 'LLM API Base URL (e.g., https://api.openai.com/v1)', false)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed: TIRE General Settings
-- ============================================================
INSERT INTO system_config (config_key, config_value, category, description, is_secret) VALUES
    ('TIRE_ADMIN_PASSWORD', 'tire-admin-2026', 'tire_general', 'TIRE admin panel password', true),
    ('TIRE_CACHE_TTL_HOURS', '24', 'tire_general', 'TIRE cache TTL in hours', false)
ON CONFLICT (config_key) DO NOTHING;
