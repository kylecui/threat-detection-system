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

-- Production installations intentionally start with no customer/device data.
-- Bind real devices through the authenticated management API after bootstrap.

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
