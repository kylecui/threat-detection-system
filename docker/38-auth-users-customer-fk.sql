-- =============================================================
-- Fix auth_users.customer_id: type alignment + FK constraint
-- Fix orphaned customers: assign tenant_id
-- Addresses GitHub issues #28 and #29
-- =============================================================

-- 1. Widen auth_users.customer_id from VARCHAR(50) to VARCHAR(100)
--    to match customers.customer_id VARCHAR(100)
ALTER TABLE auth_users
ALTER COLUMN customer_id TYPE VARCHAR(100);

-- 2. Add FK constraint (customer_id is nullable for SUPER_ADMIN users)
--    Only non-NULL values must reference a valid customer
ALTER TABLE auth_users
ADD CONSTRAINT fk_auth_users_customer
FOREIGN KEY (customer_id) REFERENCES customers(customer_id);

-- 3. Assign orphaned test customers to default-tenant (id=1)
--    These were seeded in 17-customers-init.sql without tenant_id
UPDATE customers
SET tenant_id = (SELECT id FROM tenants WHERE tenant_id = 'default-tenant')
WHERE tenant_id IS NULL
  AND customer_id IN ('customer_a', 'customer_b', 'customer_c', 'customer_d', 'customer_test');
