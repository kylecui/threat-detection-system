# Network Topology Data Model Design

**Version**: 1.0
**Date**: 2026-03-26
**Status**: Finalized

## Overview

This document defines the PostgreSQL data model for storing network topology data emitted by V2 sentinels via heartbeat events. The model supports multi-tenancy, time-series snapshots, and host discovery tracking.

## Data Model

### 1. `device_inventory`
Stores metadata for sentinel devices. This table extends the concept of `device_customer_mapping` and serves as the primary registry for V2 sentinels.

```sql
CREATE TABLE device_inventory (
    device_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    firmware_version VARCHAR(32),
    uptime BIGINT,
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_inventory_customer_id ON device_inventory(customer_id);
```

### 2. `topology_snapshots`
Time-series snapshots of network topology per device. One row is created for each heartbeat event.

```sql
CREATE TABLE topology_snapshots (
    snapshot_id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES device_inventory(device_id),
    customer_id VARCHAR(64) NOT NULL,
    snapshot_time TIMESTAMP WITH TIME ZONE NOT NULL,
    total_ips_monitored INTEGER NOT NULL,
    active_decoy_count INTEGER NOT NULL,
    network_interfaces JSONB NOT NULL,
    raw_topology JSONB NOT NULL
);

CREATE INDEX idx_topology_snapshots_device_time ON topology_snapshots(device_id, snapshot_time DESC);
CREATE INDEX idx_topology_snapshots_customer_time ON topology_snapshots(customer_id, snapshot_time DESC);
```

### 3. `discovered_hosts`
Tracks hosts discovered by sentinels. This table maps to the `devices[]` array in the heartbeat event.

```sql
CREATE TABLE discovered_hosts (
    host_id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES device_inventory(device_id),
    customer_id VARCHAR(64) NOT NULL,
    mac_address MACADDR NOT NULL,
    ip_address INET NOT NULL,
    vlan_id INTEGER DEFAULT 0,
    is_decoy BOOLEAN DEFAULT FALSE,
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (device_id, mac_address)
);

CREATE INDEX idx_discovered_hosts_customer_id ON discovered_hosts(customer_id);
CREATE INDEX idx_discovered_hosts_customer_decoy ON discovered_hosts(customer_id, is_decoy);
```

## Relationship to Existing Tables

The `device_inventory` table supersedes the existing `device_customer_mapping` table for V2 sentinels. 
- **V1 Sentinels**: Continue to use `device_customer_mapping` for legacy compatibility.
- **V2 Sentinels**: Use `device_inventory` for richer metadata (firmware, uptime, etc.).
- **Migration**: The data-ingestion service should check `device_inventory` first; if not found, it may fall back to `device_customer_mapping` for backward compatibility during the transition period.

## Retention Policy

To manage storage growth, the following retention policy is applied to `topology_snapshots`:
1. **Detailed Snapshots**: Retain all snapshots for **30 days**.
2. **Daily Summaries**: After 30 days, aggregate snapshots into daily summaries (min/max/avg metrics) and retain for **1 year**.
3. **Cleanup**: Delete snapshots older than 1 year.

### Cleanup Job Example (SQL)

```sql
-- Delete snapshots older than 30 days after ensuring aggregation is complete
DELETE FROM topology_snapshots 
WHERE snapshot_time < NOW() - INTERVAL '30 days';

-- Delete hosts not seen for more than 90 days (optional host cleanup)
DELETE FROM discovered_hosts 
WHERE last_seen < NOW() - INTERVAL '90 days';
```

## Query Patterns

### 1. Get current topology for a customer
Retrieves the latest snapshot for all devices belonging to a specific customer.

```sql
SELECT DISTINCT ON (device_id) *
FROM topology_snapshots
WHERE customer_id = 'cust-123'
ORDER BY device_id, snapshot_time DESC;
```

### 2. Get device online/offline status
Identifies devices that haven't sent a heartbeat in the last 5 minutes.

```sql
SELECT device_id, last_seen,
       CASE WHEN last_seen > NOW() - INTERVAL '5 minutes' THEN 'online' ELSE 'offline' END as status
FROM device_inventory
WHERE customer_id = 'cust-123';
```

### 3. Get decoy coverage statistics per customer
Calculates the total number of active decoys and monitored IPs across all devices for a customer.

```sql
SELECT SUM(active_decoy_count) as total_decoys,
       SUM(total_ips_monitored) as total_monitored_ips
FROM (
    SELECT DISTINCT ON (device_id) active_decoy_count, total_ips_monitored
    FROM topology_snapshots
    WHERE customer_id = 'cust-123'
    ORDER BY device_id, snapshot_time DESC
) latest_snapshots;
```

### 4. Historical topology changes for a device
Tracks the growth of monitored IPs over the last 7 days.

```sql
SELECT date_trunc('hour', snapshot_time) as hour,
       AVG(total_ips_monitored) as avg_ips
FROM topology_snapshots
WHERE device_id = 'jz-sniff-001' 
  AND snapshot_time > NOW() - INTERVAL '7 days'
GROUP BY hour
ORDER BY hour ASC;
```
