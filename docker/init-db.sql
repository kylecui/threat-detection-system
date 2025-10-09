-- Database initialization script for threat detection system
-- This script creates the necessary tables and inserts sample data

-- Drop existing table if it exists to ensure clean recreation
DROP TABLE IF EXISTS device_customer_mapping CASCADE;

-- Create device_customer_mapping table
CREATE TABLE device_customer_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_serial VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    description TEXT
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_dev_serial ON device_customer_mapping(dev_serial);
CREATE INDEX IF NOT EXISTS idx_customer_id ON device_customer_mapping(customer_id);
CREATE INDEX IF NOT EXISTS idx_active_mapping ON device_customer_mapping(is_active) WHERE is_active = true;

-- Insert sample data for testing
-- Customer A devices
INSERT INTO device_customer_mapping (dev_serial, customer_id, description) VALUES
('ABC123', 'CUSTOMER_A', 'Customer A - Primary Device'),
('DEF456', 'CUSTOMER_A', 'Customer A - Secondary Device'),
('GHI789', 'CUSTOMER_A', 'Customer A - Tertiary Device')
ON CONFLICT (dev_serial) DO NOTHING;

-- Customer B devices
INSERT INTO device_customer_mapping (dev_serial, customer_id, description) VALUES
('XYZ001', 'CUSTOMER_B', 'Customer B - Primary Device')
ON CONFLICT (dev_serial) DO NOTHING;

-- Customer C devices
INSERT INTO device_customer_mapping (dev_serial, customer_id, description) VALUES
('MNO234', 'CUSTOMER_C', 'Customer C - Primary Device'),
('PQR567', 'CUSTOMER_C', 'Customer C - Secondary Device')
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
CREATE TRIGGER update_device_customer_mapping_updated_at
    BEFORE UPDATE ON device_customer_mapping
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust as needed for your application)
GRANT SELECT, INSERT, UPDATE, DELETE ON device_customer_mapping TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE device_customer_mapping_id_seq TO threat_user;