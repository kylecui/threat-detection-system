ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_auth_users_tenant ON auth_users(tenant_id);
