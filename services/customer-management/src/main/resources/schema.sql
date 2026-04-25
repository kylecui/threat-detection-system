-- Customer Management Service Database Schema

-- 客户表
CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    subscription_tier VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    max_devices INTEGER NOT NULL DEFAULT 10,
    current_devices INTEGER NOT NULL DEFAULT 0,
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system',
    subscription_start_date TIMESTAMP WITH TIME ZONE,
    subscription_end_date TIMESTAMP WITH TIME ZONE,
    alert_enabled BOOLEAN DEFAULT TRUE,
    tenant_id BIGINT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_customer_id ON customers(customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customer_status ON customers(status);
CREATE INDEX IF NOT EXISTS idx_customer_tenant_id ON customers(tenant_id);

-- 插入测试数据
INSERT INTO customers (customer_id, name, email, phone, address, status, subscription_tier, max_devices, description)
VALUES
('customer_a', 'Acme Corporation', 'admin@acme.com', '+1-555-0001', '123 Main St, New York, NY', 'ACTIVE', 'PROFESSIONAL', 100, 'Enterprise customer - IT Security Division'),
('customer_b', 'Beta Industries', 'contact@beta.com', '+1-555-0002', '456 Oak Ave, San Francisco, CA', 'ACTIVE', 'BASIC', 20, 'SMB customer - Manufacturing'),
('customer_c', 'Gamma Tech', 'info@gamma.tech', '+1-555-0003', '789 Pine Rd, Austin, TX', 'ACTIVE', 'ENTERPRISE', 10000, 'Large enterprise - Tech company'),
('customer_d', 'Delta Solutions', 'support@delta.com', '+1-555-0004', '321 Elm St, Seattle, WA', 'SUSPENDED', 'BASIC', 20, 'Customer suspended for payment issues'),
('customer_test', 'Test Customer', 'test@example.com', '+1-555-9999', 'Test Address', 'ACTIVE', 'FREE', 5, 'Test customer for development')
ON CONFLICT (customer_id) DO NOTHING;

-- 更新现有device_customer_mapping表的客户信息
COMMENT ON TABLE customers IS '客户/租户信息表';
COMMENT ON COLUMN customers.customer_id IS '客户唯一标识符';
COMMENT ON COLUMN customers.status IS '客户状态: ACTIVE, SUSPENDED, INACTIVE';
COMMENT ON COLUMN customers.subscription_tier IS '订阅套餐: FREE, BASIC, PROFESSIONAL, ENTERPRISE';
COMMENT ON COLUMN customers.max_devices IS '最大设备数量限制';
COMMENT ON COLUMN customers.current_devices IS '当前绑定设备数量';
