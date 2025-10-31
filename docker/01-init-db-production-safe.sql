-- Database initialization script for threat detection system (PRODUCTION SAFE)
-- This script creates tables without dropping existing data
-- Safe for production deployments - preserves existing device mappings

-- Create device_customer_mapping table (PRODUCTION SAFE)
-- ✅ 使用 CREATE TABLE IF NOT EXISTS 保护现有数据
CREATE TABLE IF NOT EXISTS device_customer_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_serial VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    bind_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    unbind_time TIMESTAMP WITH TIME ZONE,
    bind_reason VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    description TEXT
);

-- Create indexes for better performance (safe to run multiple times)
CREATE INDEX IF NOT EXISTS idx_dev_serial ON device_customer_mapping(dev_serial);
CREATE INDEX IF NOT EXISTS idx_customer_id ON device_customer_mapping(customer_id);
CREATE INDEX IF NOT EXISTS idx_active_mapping ON device_customer_mapping(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_bind_time ON device_customer_mapping(bind_time);
CREATE INDEX IF NOT EXISTS idx_unbind_time ON device_customer_mapping(unbind_time);

-- Insert test data for E2E testing (based on real log files)
-- ✅ 使用 ON CONFLICT DO NOTHING 避免覆盖现有数据
-- ============================================================================
-- 真实测试数据映射表 (仅插入不存在的记录)
-- ============================================================================
INSERT INTO device_customer_mapping (dev_serial, customer_id, description, bind_time) VALUES
-- Customer A
('10221e5a3be0cf2d', 'customer_a', 'Customer A - Device 1', CURRENT_TIMESTAMP),
('eebe4c42df504ea5', 'customer_a', 'Customer A - Device 2', CURRENT_TIMESTAMP),

-- Customer B
('44056bfd85030e0e', 'customer_b', 'Customer B - Device 1', CURRENT_TIMESTAMP),
('5355ac453fe4e74d', 'customer_b', 'Customer B - Device 2', CURRENT_TIMESTAMP),

-- Customer C
('578b8eed4856244d', 'customer_c', 'Customer C - Device 1', CURRENT_TIMESTAMP),
('6360b776893dc0cc', 'customer_c', 'Customer C - Device 2', CURRENT_TIMESTAMP),
('GSFB2204200410007425', 'customer_c', 'Customer C - Device 3', CURRENT_TIMESTAMP),

-- Customer D
('a1fce03baf456aba', 'customer_d', 'Customer D - Device 1', CURRENT_TIMESTAMP),
('a458749d86a13bde', 'customer_d', 'Customer D - Device 2', CURRENT_TIMESTAMP),
('bce9288a4caa2c61', 'customer_d', 'Customer D - Device 3', CURRENT_TIMESTAMP),
('c606765df087c8a6', 'customer_d', 'Customer D - Device 4', CURRENT_TIMESTAMP),

-- Customer E
('caa0beea29676c6d', 'customer_e', 'Customer E - Primary Device', CURRENT_TIMESTAMP),

-- Customer F
('df01185343413132381002b2aaf96900', 'customer_f', 'Customer F - Primary Device', CURRENT_TIMESTAMP)
ON CONFLICT (dev_serial) DO NOTHING;

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at
DROP TRIGGER IF EXISTS update_device_customer_mapping_updated_at ON device_customer_mapping;
CREATE TRIGGER update_device_customer_mapping_updated_at
    BEFORE UPDATE ON device_customer_mapping
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust as needed for your application)
GRANT SELECT, INSERT, UPDATE, DELETE ON device_customer_mapping TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE device_customer_mapping_id_seq TO threat_user;