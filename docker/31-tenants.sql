-- =============================================================
-- Multi-Tenant Hierarchy Tables
-- =============================================================
-- Hierarchy: SuperAdmin → Tenants → Customers → Users
-- SuperAdmin can create tenants
-- TenantAdmin can manage users within their tenant
-- CustomerUser can only access their own customer data
-- =============================================================

-- Tenants table
CREATE TABLE IF NOT EXISTS tenants (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL UNIQUE,       -- slug identifier (e.g. "acme-corp")
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    contact_email   VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, SUSPENDED
    max_customers   INTEGER      NOT NULL DEFAULT 100,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenants_tenant_id ON tenants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);

-- Add tenant_id column to auth_users (nullable for backward compatibility)
ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_auth_users_tenant ON auth_users(tenant_id);

-- Seed default tenant for existing demo-customer users
INSERT INTO tenants (tenant_id, name, description, contact_email, status)
VALUES ('default-tenant', 'Default Tenant', 'Default tenant for existing users', 'admin@threat-detection.local', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

-- Link existing demo_admin user to default tenant
UPDATE auth_users
SET tenant_id = (SELECT id FROM tenants WHERE tenant_id = 'default-tenant')
WHERE username = 'demo_admin' AND tenant_id IS NULL;

-- Note: admin (SUPER_ADMIN) keeps tenant_id = NULL (can access all tenants)
