ALTER TABLE customers
ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_id ON customers(tenant_id);

UPDATE customers
SET tenant_id = 1
WHERE customer_id = 'demo-customer';

UPDATE customers
SET tenant_id = 2
WHERE customer_id = 'acme-customer';
