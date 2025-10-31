-- Add temporal fields to device_customer_mapping table for device circulation support
-- This migration adds time-windowed device-customer relationships

-- Add temporal fields (only if they don't exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'device_customer_mapping' AND column_name = 'bind_time') THEN
        ALTER TABLE device_customer_mapping
        ADD COLUMN bind_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
        RAISE NOTICE 'Added bind_time column to device_customer_mapping';
    ELSE
        RAISE NOTICE 'bind_time column already exists in device_customer_mapping';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'device_customer_mapping' AND column_name = 'unbind_time') THEN
        ALTER TABLE device_customer_mapping
        ADD COLUMN unbind_time TIMESTAMP WITH TIME ZONE;
        RAISE NOTICE 'Added unbind_time column to device_customer_mapping';
    ELSE
        RAISE NOTICE 'unbind_time column already exists in device_customer_mapping';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'device_customer_mapping' AND column_name = 'bind_reason') THEN
        ALTER TABLE device_customer_mapping
        ADD COLUMN bind_reason TEXT;
        RAISE NOTICE 'Added bind_reason column to device_customer_mapping';
    ELSE
        RAISE NOTICE 'bind_reason column already exists in device_customer_mapping';
    END IF;
END $$;

-- Remove the unique constraint on dev_serial to allow device circulation (only if it exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
               WHERE table_name = 'device_customer_mapping'
               AND constraint_name = 'device_customer_mapping_dev_serial_key') THEN
        ALTER TABLE device_customer_mapping
        DROP CONSTRAINT device_customer_mapping_dev_serial_key;
        RAISE NOTICE 'Dropped unique constraint on dev_serial';
    ELSE
        RAISE NOTICE 'Unique constraint on dev_serial does not exist or already dropped';
    END IF;
END $$;

-- Create indexes for temporal queries (only if they don't exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_device_customer_mapping_bind_time') THEN
        CREATE INDEX idx_device_customer_mapping_bind_time ON device_customer_mapping(bind_time);
        RAISE NOTICE 'Created index idx_device_customer_mapping_bind_time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_device_customer_mapping_unbind_time') THEN
        CREATE INDEX idx_device_customer_mapping_unbind_time ON device_customer_mapping(unbind_time);
        RAISE NOTICE 'Created index idx_device_customer_mapping_unbind_time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_device_customer_mapping_temporal_range') THEN
        CREATE INDEX idx_device_customer_mapping_temporal_range ON device_customer_mapping(dev_serial, bind_time, unbind_time);
        RAISE NOTICE 'Created index idx_device_customer_mapping_temporal_range';
    END IF;
END $$;

-- Update existing records to have bind_time set to created_at (only for records where bind_time is still null)
UPDATE device_customer_mapping
SET bind_time = created_at
WHERE bind_time IS NULL AND created_at IS NOT NULL;

-- Add comments (only if they don't exist)
DO $$
BEGIN
    -- Check and add comments for each column
    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'device_customer_mapping'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'device_customer_mapping'::regclass AND attname = 'bind_time'
                   )) THEN
        COMMENT ON COLUMN device_customer_mapping.bind_time IS 'When the device was bound to this customer';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'device_customer_mapping'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'device_customer_mapping'::regclass AND attname = 'unbind_time'
                   )) THEN
        COMMENT ON COLUMN device_customer_mapping.unbind_time IS 'When the device was unbound from this customer (null means currently active)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'device_customer_mapping'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'device_customer_mapping'::regclass AND attname = 'bind_reason'
                   )) THEN
        COMMENT ON COLUMN device_customer_mapping.bind_reason IS 'Reason for binding/unbinding (e.g., POC, rental, transfer)';
    END IF;
END $$;

-- Migration completed successfully
DO $$
BEGIN
    RAISE NOTICE 'Temporal device mapping migration completed successfully';
END $$;