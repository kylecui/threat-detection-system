-- 45-device-binding-integrity.sql
-- Keep device binding history while guaranteeing exactly one current binding
-- for a serial number (case-insensitive).

ALTER TABLE device_customer_mapping
    ADD COLUMN IF NOT EXISTS bind_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS unbind_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS bind_reason VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

UPDATE device_customer_mapping
SET bind_time = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE bind_time IS NULL;

UPDATE device_customer_mapping
SET is_active = FALSE
WHERE unbind_time IS NOT NULL
  AND is_active IS DISTINCT FROM FALSE;

UPDATE device_customer_mapping
SET unbind_time = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE is_active IS FALSE
  AND unbind_time IS NULL;

-- Older releases could create more than one current row when two bind requests
-- raced. Preserve the newest row and close older rows as history.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY UPPER(BTRIM(dev_serial))
               ORDER BY COALESCE(bind_time, created_at, CURRENT_TIMESTAMP) DESC,
                        id DESC
           ) AS row_rank
    FROM device_customer_mapping
    WHERE is_active IS TRUE
      AND unbind_time IS NULL
), duplicates AS (
    SELECT id FROM ranked WHERE row_rank > 1
)
UPDATE device_customer_mapping AS mapping
SET is_active = FALSE,
    unbind_time = CURRENT_TIMESTAMP,
    bind_reason = CASE
        WHEN mapping.bind_reason IS NULL OR BTRIM(mapping.bind_reason) = ''
            THEN 'deduplicated_by_3.1.25_upgrade'
        ELSE mapping.bind_reason
    END,
    updated_at = CURRENT_TIMESTAMP
FROM duplicates
WHERE mapping.id = duplicates.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_current_serial_ci
    ON device_customer_mapping (UPPER(BTRIM(dev_serial)))
    WHERE is_active IS TRUE AND unbind_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_device_customer_current
    ON device_customer_mapping (customer_id, is_active, unbind_time);

INSERT INTO tds_schema_migrations (migration_name)
VALUES ('45-device-binding-integrity.sql')
ON CONFLICT (migration_name) DO NOTHING;
