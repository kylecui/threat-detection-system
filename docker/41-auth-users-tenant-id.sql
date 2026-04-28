ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE auth_users SET tenant_id = (SELECT id FROM tenants WHERE tenant_id = 'default-tenant') WHERE username = 'demo_admin' AND tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_auth_users_tenant ON auth_users(tenant_id);
