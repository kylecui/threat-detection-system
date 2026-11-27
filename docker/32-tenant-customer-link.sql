ALTER TABLE customers
ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_id ON customers(tenant_id);

-- No production customer is assigned implicitly. Tenant/customer links are
-- created through the authenticated management workflow.
