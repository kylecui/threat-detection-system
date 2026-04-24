-- Seed demo-customer and device mapping for V1/V2 pipeline testing
-- This matches the k8s seed in k8s/base/postgres.yaml (36-demo-customer-seed.sql)

INSERT INTO customers (customer_id, name, email, phone, address, status, subscription_tier, max_devices, description)
VALUES ('demo-customer', 'Demo Customer', 'demo@threat-detection.local', '+86-000-0000', 'Demo Lab', 'ACTIVE', 'PROFESSIONAL', 50, 'Demo customer for V1/V2 pipeline testing')
ON CONFLICT (customer_id) DO NOTHING;

INSERT INTO device_customer_mapping (dev_serial, customer_id)
VALUES ('9d262111f2476d34', 'demo-customer')
ON CONFLICT DO NOTHING;
