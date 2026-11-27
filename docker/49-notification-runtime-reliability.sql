-- Notification delivery reliability fields for existing and fresh installations.
BEGIN;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS customer_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_notifications_retry_due
    ON notifications(status, next_retry_at, id)
    WHERE status = 'RETRYING';

CREATE INDEX IF NOT EXISTS idx_notifications_customer_created
    ON notifications(customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_rate_limits_cleanup
    ON notification_rate_limits(notification_hour);

INSERT INTO tds_schema_migrations (migration_name)
VALUES ('49-notification-runtime-reliability.sql')
ON CONFLICT (migration_name) DO NOTHING;

COMMIT;
