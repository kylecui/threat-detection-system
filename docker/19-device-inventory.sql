-- ============================================================================
-- 19-device-inventory.sql
-- V2 sentinel device registry.  Extends the concept of device_customer_mapping
-- with richer metadata (firmware, uptime, etc.).
-- See docs/design/network_topology_data_model.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS device_inventory (
    device_id         VARCHAR(64)   PRIMARY KEY,
    customer_id       VARCHAR(64)   NOT NULL,
    firmware_version  VARCHAR(32),
    uptime            BIGINT,                        -- seconds since boot
    last_seen         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_inventory_customer_id
    ON device_inventory(customer_id);
