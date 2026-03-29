-- ============================================================================
-- 21-discovered-hosts.sql
-- Hosts discovered by V2 sentinels, populated from the devices[] array
-- inside heartbeat events.
-- See docs/design/network_topology_data_model.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS discovered_hosts (
    host_id     BIGSERIAL         PRIMARY KEY,
    device_id   VARCHAR(64)       NOT NULL REFERENCES device_inventory(device_id),
    customer_id VARCHAR(64)       NOT NULL,
    mac_address VARCHAR(17)       NOT NULL,
    ip_address  VARCHAR(45)       NOT NULL,
    vlan_id     INTEGER           DEFAULT 0,
    is_decoy    BOOLEAN           DEFAULT FALSE,
    first_seen  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen   TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (device_id, mac_address)
);

CREATE INDEX IF NOT EXISTS idx_discovered_hosts_customer_id
    ON discovered_hosts(customer_id);

CREATE INDEX IF NOT EXISTS idx_discovered_hosts_customer_decoy
    ON discovered_hosts(customer_id, is_decoy);
