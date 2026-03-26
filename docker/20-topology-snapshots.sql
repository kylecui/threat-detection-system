-- ============================================================================
-- 20-topology-snapshots.sql
-- Time-series network topology snapshots produced by V2 sentinel heartbeats.
-- One row per heartbeat event per device.
-- See docs/design/network_topology_data_model.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS topology_snapshots (
    snapshot_id          BIGSERIAL    PRIMARY KEY,
    device_id            VARCHAR(64)  NOT NULL REFERENCES device_inventory(device_id),
    customer_id          VARCHAR(64)  NOT NULL,
    snapshot_time        TIMESTAMP WITH TIME ZONE NOT NULL,
    total_ips_monitored  INTEGER      NOT NULL,
    active_decoy_count   INTEGER      NOT NULL,
    network_interfaces   JSONB        NOT NULL,      -- NIC details array
    raw_topology         JSONB        NOT NULL        -- full heartbeat payload
);

CREATE INDEX IF NOT EXISTS idx_topology_snapshots_device_time
    ON topology_snapshots(device_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_topology_snapshots_customer_time
    ON topology_snapshots(customer_id, snapshot_time DESC);
