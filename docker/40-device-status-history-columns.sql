-- Issue #42: Add missing columns to device_status_history table
-- These columns exist in the DeviceStatusHistory JPA entity but were never migrated.
-- Without them, DeviceHealthAlertConsumer crashes on startup:
--   ERROR: column d1_0.is_expired does not exist

ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS is_healthy BOOLEAN DEFAULT true;
ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS is_expiring_soon BOOLEAN DEFAULT false;
ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS is_expired BOOLEAN DEFAULT false;
ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS sentry_count_changed BOOLEAN DEFAULT false;
ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS real_host_count_changed BOOLEAN DEFAULT false;
ALTER TABLE device_status_history ADD COLUMN IF NOT EXISTS raw_log TEXT;
