-- ============================================================
-- 37-config-cascading.sql
-- Config cascading: inheritance lock modes + entity groups
-- ============================================================

-- ============================================================
-- 1. Config inheritance lock modes per entity
-- ============================================================
CREATE TABLE IF NOT EXISTS config_inheritance (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(20)  NOT NULL CHECK (entity_type IN ('tenant', 'customer')),
    entity_id       BIGINT       NOT NULL,
    config_type     VARCHAR(50)  NOT NULL,  -- e.g. 'llm', 'tire', 'detection_rules', 'alert_thresholds', 'notification'
    lock_mode       VARCHAR(20)  NOT NULL DEFAULT 'default' CHECK (lock_mode IN ('default', 'inherit_only', 'independent_only')),
    locked_by       BIGINT,                 -- auth_users.id of admin who set the lock
    locked_at       TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_type, entity_id, config_type)
);

CREATE INDEX IF NOT EXISTS idx_config_inheritance_entity ON config_inheritance(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_config_inheritance_type   ON config_inheritance(config_type);

COMMENT ON TABLE  config_inheritance IS 'Per-entity lock mode controlling whether config is inherited, independent, or flexible';
COMMENT ON COLUMN config_inheritance.lock_mode IS 'default=flexible, inherit_only=must inherit parent config, independent_only=must have own config';

-- ============================================================
-- 2. Entity groups (for batch config assignment)
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_groups (
    id              BIGSERIAL PRIMARY KEY,
    group_name      VARCHAR(100) NOT NULL,
    group_type      VARCHAR(20)  NOT NULL CHECK (group_type IN ('tenant_group', 'customer_group')),
    description     TEXT,
    tenant_id       BIGINT,                 -- NULL for superadmin-owned groups, tenant.id for tenant-owned groups
    created_by      BIGINT       NOT NULL,  -- auth_users.id
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entity_groups_type      ON entity_groups(group_type);
CREATE INDEX IF NOT EXISTS idx_entity_groups_tenant    ON entity_groups(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_groups_created   ON entity_groups(created_by);

COMMENT ON TABLE entity_groups IS 'Groups of tenants or customers for batch config operations';

-- ============================================================
-- 3. Entity group membership
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_group_members (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT       NOT NULL REFERENCES entity_groups(id) ON DELETE CASCADE,
    entity_id       BIGINT       NOT NULL,
    entity_type     VARCHAR(20)  NOT NULL CHECK (entity_type IN ('tenant', 'customer')),
    added_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, entity_id, entity_type)
);

CREATE INDEX IF NOT EXISTS idx_entity_group_members_group  ON entity_group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_entity_group_members_entity ON entity_group_members(entity_id, entity_type);

COMMENT ON TABLE entity_group_members IS 'Maps entities (tenants/customers) to their groups';
