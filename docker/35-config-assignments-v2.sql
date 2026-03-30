-- ============================================================
-- 35-config-assignments-v2.sql
-- Extend config_assignments with TI API keys + lock flags
-- Add user_configs table for end-user self-service
-- ============================================================

-- ============================================================
-- 1. Extend config_assignments with TI API keys + lock flags
-- ============================================================
ALTER TABLE config_assignments
    ADD COLUMN IF NOT EXISTS tire_api_keys JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS lock_llm     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS lock_tire    BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN config_assignments.tire_api_keys IS 'Admin-assigned TI API keys: {"ABUSEIPDB_API_KEY":"xxx","VT_API_KEY":"yyy",...}';
COMMENT ON COLUMN config_assignments.lock_llm IS 'When true, customer cannot use own LLM provider';
COMMENT ON COLUMN config_assignments.lock_tire IS 'When true, customer cannot use own TI API keys';

-- ============================================================
-- 2. User configs (end-user self-service)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_configs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT  NOT NULL UNIQUE,                      -- auth_users.id
    llm_provider_id BIGINT  REFERENCES llm_providers(id) ON DELETE SET NULL,
    tire_api_keys   JSONB   NOT NULL DEFAULT '{}'::jsonb,         -- user's own TI API keys
    use_own_llm     BOOLEAN NOT NULL DEFAULT false,               -- prefer own LLM over admin assignment
    use_own_tire    BOOLEAN NOT NULL DEFAULT false,               -- prefer own TI keys over admin assignment
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_configs_user ON user_configs(user_id);

-- ============================================================
-- Resolution order (documented, enforced in backend):
--   1. If admin locked (lock_llm/lock_tire=true)  → admin assignment only
--   2. If user has use_own_*=true AND has keys     → user's own config
--   3. If admin assigned (config_assignments)      → admin assignment
--   4. Fallback                                    → global system_config
-- ============================================================
