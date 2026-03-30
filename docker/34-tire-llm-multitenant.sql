-- ============================================================
-- 34-tire-llm-multitenant.sql
-- Multi-tenant TIRE Custom Plugins, LLM Providers, and
-- Config Assignments for per-tenant / per-user configuration
-- ============================================================

-- ============================================================
-- 1. Custom TIRE Plugins (user-defined, beyond the 11 built-in)
-- ============================================================
CREATE TABLE IF NOT EXISTS tire_custom_plugins (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,                         -- display name e.g. "My Shodan Pro"
    slug         VARCHAR(50)  NOT NULL,                         -- unique key e.g. "my_shodan_pro"
    description  VARCHAR(512),
    plugin_url   VARCHAR(512) NOT NULL DEFAULT '',              -- API endpoint URL
    api_key      TEXT         NOT NULL DEFAULT '',              -- API key (stored plain; masked in responses)
    auth_type    VARCHAR(30)  NOT NULL DEFAULT 'bearer',       -- bearer, header, query, none
    auth_header  VARCHAR(100) NOT NULL DEFAULT 'Authorization', -- header name for API key
    parser_type  VARCHAR(50)  NOT NULL DEFAULT 'json',         -- json, xml, csv
    request_method VARCHAR(10) NOT NULL DEFAULT 'GET',         -- GET, POST
    request_body TEXT,                                          -- optional POST body template ({ip} placeholder)
    response_path VARCHAR(255),                                -- JSONPath to extract score/data
    enabled      BOOLEAN NOT NULL DEFAULT true,
    priority     INTEGER NOT NULL DEFAULT 50,                  -- lower = higher priority
    timeout      INTEGER NOT NULL DEFAULT 30,                  -- seconds
    owner_type   VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',       -- SYSTEM, TENANT, USER
    owner_id     BIGINT,                                       -- NULL for SYSTEM, tenant.id or auth_users.id
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_custom_plugins_owner ON tire_custom_plugins(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_custom_plugins_slug  ON tire_custom_plugins(slug);
CREATE INDEX IF NOT EXISTS idx_custom_plugins_enabled ON tire_custom_plugins(enabled);

-- Unique: same slug per ownership scope
CREATE UNIQUE INDEX IF NOT EXISTS uq_custom_plugins_slug_owner
    ON tire_custom_plugins(slug, owner_type, COALESCE(owner_id, -1));

-- ============================================================
-- 2. LLM Providers (multiple provider configs, multi-tenant)
-- ============================================================
CREATE TABLE IF NOT EXISTS llm_providers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,                          -- e.g. "OpenAI GPT-4o", "DeepSeek Chat"
    api_key     TEXT         NOT NULL DEFAULT '',               -- API key (masked in responses)
    model       VARCHAR(100) NOT NULL DEFAULT '',               -- model name
    base_url    VARCHAR(512) NOT NULL DEFAULT '',               -- API base URL
    is_default  BOOLEAN NOT NULL DEFAULT false,                 -- default provider for this scope
    enabled     BOOLEAN NOT NULL DEFAULT true,
    owner_type  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',        -- SYSTEM, TENANT, USER
    owner_id    BIGINT,                                        -- NULL for SYSTEM
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_providers_owner ON llm_providers(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_llm_providers_default ON llm_providers(is_default);

-- ============================================================
-- 3. Config Assignments (link customer to specific plugin/LLM)
-- ============================================================
CREATE TABLE IF NOT EXISTS config_assignments (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(50)  NOT NULL UNIQUE,               -- target customer
    llm_provider_id BIGINT REFERENCES llm_providers(id) ON DELETE SET NULL,
    assigned_by     BIGINT NOT NULL,                            -- user_id who made the assignment
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_assignments_customer ON config_assignments(customer_id);

-- ============================================================
-- 4. Migrate existing global LLM config to llm_providers table
-- ============================================================
INSERT INTO llm_providers (name, api_key, model, base_url, is_default, owner_type, owner_id)
SELECT
    'Default LLM Provider',
    COALESCE((SELECT config_value FROM system_config WHERE config_key = 'LLM_API_KEY'), ''),
    COALESCE((SELECT config_value FROM system_config WHERE config_key = 'LLM_MODEL'), ''),
    COALESCE((SELECT config_value FROM system_config WHERE config_key = 'LLM_BASE_URL'), ''),
    true,
    'SYSTEM',
    NULL
WHERE EXISTS (SELECT 1 FROM system_config WHERE config_key = 'LLM_API_KEY' AND config_value <> '');
