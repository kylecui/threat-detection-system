-- Add temporal fields to device_customer_mapping table for device circulation support
-- This migration adds time-windowed device-customer relationships

-- Add temporal fields
ALTER TABLE device_customer_mapping
ADD COLUMN bind_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN unbind_time TIMESTAMP WITH TIME ZONE,
ADD COLUMN bind_reason TEXT;

-- Remove the unique constraint on dev_serial to allow device circulation
ALTER TABLE device_customer_mapping
DROP CONSTRAINT device_customer_mapping_dev_serial_key;

-- Create indexes for temporal queries
CREATE INDEX idx_device_customer_mapping_bind_time ON device_customer_mapping(bind_time);
CREATE INDEX idx_device_customer_mapping_unbind_time ON device_customer_mapping(unbind_time);
CREATE INDEX idx_device_customer_mapping_temporal_range ON device_customer_mapping(dev_serial, bind_time, unbind_time);

-- Update existing records to have bind_time set to created_at
UPDATE device_customer_mapping
SET bind_time = created_at
WHERE bind_time IS NULL;

-- Add comments
COMMENT ON COLUMN device_customer_mapping.bind_time IS 'When the device was bound to this customer';
COMMENT ON COLUMN device_customer_mapping.unbind_time IS 'When the device was unbound from this customer (null means currently active)';
COMMENT ON COLUMN device_customer_mapping.bind_reason IS 'Reason for binding/unbinding (e.g., POC, rental, transfer)';