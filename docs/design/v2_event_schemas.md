# V2 Sentinel Event Schemas

**Version**: 1.0
**Date**: 2026-03-26
**Status**: Design — defines the JSON event formats emitted by the new sentinel (jz_sniff_rn) over MQTT/HTTPS.

---

## Overview

The new sentinel (jz_sniff_rn) supports two log formats:

| Format | Transport | Use Case |
|--------|-----------|----------|
| **V1** (KV pairs) | rsyslog UDP | Backward compatibility with the original JZZN platform |
| **V2** (JSON) | MQTT QoS 1 / HTTPS batch | New cloud-native platform, richer event data |

This document defines V2. The platform's **data-ingestion** service must parse both V1 and V2, normalize them into the internal `attack-events` Kafka topic format, and route downstream.

## Common Envelope

Every V2 event shares this envelope structure:

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 123456,
  "ts": "2026-03-23T10:15:32.123456789+08:00",
  "type": "attack",
  "data": { ... }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `v` | int | ✅ | Format version. Always `2` for V2 events. |
| `device_id` | string | ✅ | Sentinel device identifier (maps to `deviceSerial` in platform). |
| `seq` | uint64 | ✅ | Monotonically increasing sequence number (device-scoped). Used for dedup and gap detection. |
| `ts` | string | ✅ | ISO 8601 timestamp with timezone and nanosecond precision. |
| `type` | string | ✅ | Event type. One of: `attack`, `sniffer`, `threat`, `bg`, `heartbeat`, `audit`, `policy`. |
| `data` | object | ✅ | Type-specific payload (see below). |

### MQTT Topic Structure

Events are published to: `jz/{device_id}/logs/{type}`

- Subscription wildcard: `jz/+/logs/#`
- LWT (Last Will and Testament): `jz/{device_id}/status` with payload `{"online": false}`
- On connect: retained message on `jz/{device_id}/status` with `{"online": true, ...}`

---

## Event Type 1: Attack (`type="attack"`)

Emitted when a device probes a honeypot/guard IP. This is the primary event type consumed by the platform's threat scoring pipeline.

### V1 Mapping

This event has a direct V1 equivalent (log_type=1, sub_type=1).

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 100,
  "ts": "2026-03-23T10:15:32.123456789+08:00",
  "type": "attack",
  "data": {
    "src_ip": "10.0.1.100",
    "src_mac": "aa:bb:cc:11:22:33",
    "guard_ip": "10.0.1.50",
    "guard_mac": "aa:bb:cc:dd:ee:01",
    "guard_type": "static",
    "protocol": "arp",
    "dst_port": 0,
    "interface": "eth1",
    "ifindex": 3,
    "vlan_id": 100,
    "threat_level": 2,
    "ethertype": 2054,
    "ip_proto": 0
  }
}
```

### Field Table

| Field | Type | Required | Description | V1 Mapping |
|-------|------|----------|-------------|------------|
| `src_ip` | string | ✅ | Attacker IP (compromised internal host). | `attack_ip` |
| `src_mac` | string | ✅ | Attacker MAC address. | `attack_mac` |
| `guard_ip` | string | ✅ | Honeypot/decoy IP that was targeted. | `response_ip` |
| `guard_mac` | string | ✅ | Fake MAC used in the honeypot response. | *(new in V2)* |
| `guard_type` | string | ✅ | Guard classification: `"static"` or `"dynamic"`. | *(new in V2)* |
| `protocol` | string | ✅ | Protocol that triggered the event: `"arp"`, `"icmp"`, `"tcp"`, `"udp"`. | Derived from `eth_type` + `ip_type` |
| `dst_port` | int | ✅ | Destination port (0 for ARP/ICMP). | `response_port` |
| `interface` | string | ✅ | Network interface name. | *(new in V2)* |
| `ifindex` | int | ✅ | Network interface index. | `line_id` |
| `vlan_id` | int | ✅ | VLAN ID (0 = untagged). | `Vlan_id` |
| `threat_level` | int | ✅ | BPF-classified threat level (0=none, 1=low, 2=med, 3=high, 4=crit). | *(new in V2)* |
| `ethertype` | int | ✅ | Ethernet type (0x0806=ARP, 0x0800=IPv4). | `eth_type` |
| `ip_proto` | int | ✅ | IP protocol number (1=ICMP, 6=TCP, 17=UDP; 0 for non-IP). | `ip_type` |

### V1 ↔ V2 Mapping Table

| V1 Field | V2 Field | Notes |
|----------|----------|-------|
| `syslog_version` | `v` | V1: `"1.10.0"`, V2: `2` |
| `dev_serial` | `device_id` | Direct mapping |
| `log_type=1` | `type="attack"` | — |
| `attack_mac` | `data.src_mac` | — |
| `attack_ip` | `data.src_ip` | — |
| `response_ip` | `data.guard_ip` | Renamed for clarity (it's a honeypot IP) |
| `response_port` | `data.dst_port` | — |
| `line_id` | `data.ifindex` | — |
| `Vlan_id` | `data.vlan_id` | — |
| `log_time` | `ts` | V1: epoch seconds, V2: ISO 8601 nanosecond |
| `eth_type` | `data.ethertype` | — |
| `ip_type` | `data.ip_proto` | — |
| *(none)* | `data.guard_mac` | New in V2 |
| *(none)* | `data.guard_type` | New in V2 |
| *(none)* | `data.protocol` | New in V2 (human-readable protocol name) |
| *(none)* | `data.threat_level` | New in V2 (BPF-layer classification) |

---

## Event Type 2: Sniffer Detection (`type="sniffer"`)

Emitted when a network device is detected responding to ARP probes for non-existent IPs, indicating promiscuous mode (potential sniffer).

### V1 Mapping

No V1 equivalent. This is a V2-only event type.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 300,
  "ts": "2026-03-23T10:20:00.000000000+08:00",
  "type": "sniffer",
  "data": {
    "suspect_mac": "00:11:22:33:44:55",
    "suspect_ip": "10.0.1.200",
    "probe_ip": "10.0.1.253",
    "interface": "eth1",
    "ifindex": 3,
    "response_count": 3,
    "first_seen": "2026-03-23T09:00:00+08:00",
    "last_seen": "2026-03-23T10:20:00+08:00"
  }
}
```

### Field Table

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `suspect_mac` | string | ✅ | MAC address of the suspected sniffer device. |
| `suspect_ip` | string | ✅ | IP address of the suspected sniffer device. |
| `probe_ip` | string | ✅ | Non-existent IP used in the ARP probe (only promiscuous-mode devices respond). |
| `interface` | string | ✅ | Network interface where the sniffer was detected. |
| `ifindex` | int | ✅ | Interface index. |
| `response_count` | int | ✅ | Number of probe responses from this device. |
| `first_seen` | string | ✅ | ISO 8601 timestamp of first probe response. |
| `last_seen` | string | ✅ | ISO 8601 timestamp of most recent probe response. |

---

## Event Type 3: Threat Detection (`type="threat"`)

Emitted by the BPF `threat_detect` module when a packet matches a known threat pattern (header/payload signatures).

### V1 Mapping

No V1 equivalent. This is a V2-only event type.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 400,
  "ts": "2026-03-23T10:25:00.000000000+08:00",
  "type": "threat",
  "data": {
    "pattern_id": 42,
    "threat_level": 3,
    "action_taken": "log_drop",
    "description": "SMB exploit attempt",
    "src_ip": "10.0.1.100",
    "dst_ip": "10.0.1.50",
    "dst_port": 445,
    "protocol": "tcp",
    "interface": "eth1",
    "ifindex": 3,
    "vlan_id": 100
  }
}
```

### Field Table

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pattern_id` | int | ✅ | ID of the matched threat signature pattern. |
| `threat_level` | int | ✅ | Threat severity (1=low, 2=med, 3=high, 4=crit). |
| `action_taken` | string | ✅ | Action applied: `"log"`, `"log_drop"`, `"log_redirect"`. |
| `description` | string | ✅ | Human-readable description of the matched pattern. |
| `src_ip` | string | ✅ | Source IP of the threat traffic. |
| `dst_ip` | string | ✅ | Destination IP. |
| `dst_port` | int | ✅ | Destination port. |
| `protocol` | string | ✅ | Transport protocol: `"tcp"`, `"udp"`, `"icmp"`. |
| `interface` | string | ✅ | Network interface. |
| `ifindex` | int | ✅ | Interface index. |
| `vlan_id` | int | ✅ | VLAN ID (0 = untagged). |

---

## Event Type 4: Background Traffic (`type="bg"`)

Periodic summary of broadcast/multicast protocol traffic captured by the `bg_collector` BPF module. Used for network baseline building and anomaly detection.

### V1 Mapping

No V1 equivalent. This is a V2-only event type.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 500,
  "ts": "2026-03-23T10:30:00.000000000+08:00",
  "type": "bg",
  "data": {
    "period_start": "2026-03-23T10:00:00+08:00",
    "period_end": "2026-03-23T10:30:00+08:00",
    "protocols": {
      "arp": { "count": 1234, "bytes": 56780, "unique_sources": 15 },
      "dhcp": { "count": 56, "bytes": 12340, "unique_sources": 8 },
      "mdns": { "count": 789, "bytes": 34560, "unique_sources": 12 },
      "lldp": { "count": 23, "bytes": 4560, "unique_sources": 3 },
      "stp": { "count": 456, "bytes": 9120, "unique_sources": 2 }
    }
  }
}
```

### Field Table

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `period_start` | string | ✅ | ISO 8601 start of aggregation period. |
| `period_end` | string | ✅ | ISO 8601 end of aggregation period. |
| `protocols` | object | ✅ | Per-protocol statistics (key = protocol name). |
| `protocols.{name}.count` | int | ✅ | Number of packets captured for this protocol. |
| `protocols.{name}.bytes` | int | ✅ | Total bytes captured. |
| `protocols.{name}.unique_sources` | int | ✅ | Number of unique source MACs. |

**Supported protocol keys**: `arp`, `dhcp`, `mdns`, `ssdp`, `lldp`, `stp`, `cdp`, `igmp`.

---

## Event Type 5: Heartbeat (`type="heartbeat"`)

Periodic device status report with rich network topology data from passive device fingerprinting (DHCP/mDNS/SSDP/LLDP/CDP).

### V1 Mapping

This event has a V1 equivalent (log_type=2), but V2 is vastly richer.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 200,
  "ts": "2026-03-23T10:30:00.000000000+08:00",
  "type": "heartbeat",
  "data": {
    "uptime_sec": 86400,
    "static_guards": 10,
    "dynamic_guards": 45,
    "total_guards": 55,
    "online_devices": 120,
    "frozen_ips": 5,
    "whitelist_count": 8,
    "interfaces": {
      "eth1": { "rx_pps": 15000, "tx_pps": 200, "bpf_modules": 8 },
      "eth2": { "rx_pps": 8000, "tx_pps": 100, "bpf_modules": 8 }
    },
    "modules": {
      "loaded": 8,
      "failed": 0
    },
    "db_size_mb": 128,
    "attack_count_total": 5432,
    "attack_count_last_period": 12,
    "network_topology": {
      "total_identified": 95,
      "total_unidentified": 25,
      "by_class": {
        "Computer": 42, "Phone": 18, "Printer": 5,
        "Switch": 3, "IoT": 12, "Unknown": 40
      },
      "by_os": {
        "Windows": 30, "Linux": 8, "iOS": 10,
        "Android": 8, "Cisco IOS": 3, "Unknown": 61
      },
      "by_vendor": {
        "Apple": 18, "Dell": 12, "HP": 8,
        "Cisco": 3, "VMware": 5, "Other": 74
      }
    },
    "devices": [
      {
        "ip": "10.0.1.100",
        "mac": "aa:bb:cc:11:22:33",
        "vlan": 100,
        "vendor": "Dell",
        "os_class": "Windows",
        "device_class": "Computer",
        "hostname": "desktop-01",
        "confidence": 85,
        "first_seen": "2026-03-20T08:00:00+08:00",
        "last_seen": "2026-03-23T10:29:55+08:00"
      }
    ]
  }
}
```

### Field Table — Top-Level Status

| Field | Type | Required | Description | V1 Mapping |
|-------|------|----------|-------------|------------|
| `uptime_sec` | int | ✅ | Device uptime in seconds. | Derived from `dev_start_time` / `dev_end_time` |
| `static_guards` | int | ✅ | Number of static guard IPs. | *(new in V2)* |
| `dynamic_guards` | int | ✅ | Number of dynamic guard IPs. | *(new in V2)* |
| `total_guards` | int | ✅ | Total guard count. | `sentry_count` |
| `online_devices` | int | ✅ | Number of discovered online devices. | `real_host_count` |
| `frozen_ips` | int | ✅ | Number of frozen IPs. | *(new in V2)* |
| `whitelist_count` | int | ✅ | Number of whitelisted entries. | *(new in V2)* |
| `interfaces` | object | ✅ | Per-interface stats (key = interface name). | *(new in V2)* |
| `modules` | object | ✅ | BPF module status. | *(new in V2)* |
| `db_size_mb` | int | ✅ | SQLite database size in MB. | *(new in V2)* |
| `attack_count_total` | int | ✅ | Lifetime attack event count. | *(new in V2)* |
| `attack_count_last_period` | int | ✅ | Attack count since last heartbeat. | *(new in V2)* |

### Field Table — network_topology

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `total_identified` | int | ✅ | Devices with confidence > 0. |
| `total_unidentified` | int | ✅ | Devices with confidence = 0. |
| `by_class` | object | ✅ | Device counts by class (Computer, Phone, Printer, Switch, IoT, Unknown). |
| `by_os` | object | ✅ | Device counts by OS family (Windows, Linux, iOS, Android, etc.). |
| `by_vendor` | object | ✅ | Device counts by manufacturer. |

### Field Table — devices[] Array

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ip` | string | ✅ | Device IP address. |
| `mac` | string | ✅ | Device MAC address. |
| `vlan` | int | ✅ | VLAN ID. |
| `vendor` | string | ✅ | Manufacturer (from OUI lookup or DHCP/LLDP). |
| `os_class` | string | ✅ | Operating system family. |
| `device_class` | string | ✅ | Device category (Computer, Phone, Printer, etc.). |
| `hostname` | string | ✅ | Device hostname (from DHCP Option 12 or LLDP). |
| `confidence` | int | ✅ | Fingerprint confidence score (0–100). |
| `first_seen` | string | ✅ | ISO 8601 timestamp of first detection. |
| `last_seen` | string | ✅ | ISO 8601 timestamp of last activity. |

> **Size control**: When online_devices > 200, only the top-200 devices are included (sorted by confidence DESC, then last_seen DESC). Full device list available via the sentinel's REST API `GET /discovery/devices`.

---

## Event Type 6: Audit (`type="audit"`)

Administrative action log — records configuration changes, guard table modifications, API operations, etc.

### V1 Mapping

No V1 equivalent. This is a V2-only event type.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 700,
  "ts": "2026-03-23T10:40:00.000000000+08:00",
  "type": "audit",
  "data": {
    "action": "guard_add",
    "actor": "api:token:admin",
    "target": "static_guard:10.0.1.50",
    "result": "success",
    "details": {
      "ip": "10.0.1.50",
      "mac": "aa:bb:cc:dd:ee:01",
      "vlan": 100
    }
  }
}
```

### Field Table

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | ✅ | Action performed (e.g., `guard_add`, `guard_delete`, `config_push`, `policy_create`, `system_restart`). |
| `actor` | string | ✅ | Who performed the action (e.g., `api:token:admin`, `cli:root`, `system:auto`). |
| `target` | string | ✅ | Object of the action (e.g., `static_guard:10.0.1.50`, `config:guards`). |
| `result` | string | ✅ | Outcome: `"success"` or `"failure"`. |
| `details` | object | ❌ | Additional context (action-specific, schema varies). |

---

## Event Type 7: Policy Match (`type="policy"`)

Emitted when a traffic flow matches a flow policy rule in the `traffic_weaver` BPF module, triggering a redirect or mirror action.

### V1 Mapping

No V1 equivalent. This is a V2-only event type.

### JSON Example

```json
{
  "v": 2,
  "device_id": "jz-sniff-001",
  "seq": 600,
  "ts": "2026-03-23T10:35:00.000000000+08:00",
  "type": "policy",
  "data": {
    "policy_id": 7,
    "action": "redirect_mirror",
    "src_ip": "10.0.1.100",
    "dst_ip": "10.0.1.50",
    "src_port": 54321,
    "dst_port": 80,
    "protocol": "tcp",
    "redirect_to": "eth3",
    "mirror_to": "eth4",
    "trigger": "auto",
    "reason": "repeated_attack_threshold"
  }
}
```

### Field Table

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `policy_id` | int | ✅ | ID of the matched flow policy. |
| `action` | string | ✅ | Action taken: `"redirect"`, `"mirror"`, `"redirect_mirror"`, `"drop"`. |
| `src_ip` | string | ✅ | Source IP of the matched flow. |
| `dst_ip` | string | ✅ | Destination IP. |
| `src_port` | int | ✅ | Source port. |
| `dst_port` | int | ✅ | Destination port. |
| `protocol` | string | ✅ | Transport protocol: `"tcp"`, `"udp"`. |
| `redirect_to` | string | ❌ | Redirect target interface (present if action includes redirect). |
| `mirror_to` | string | ❌ | Mirror target interface (present if action includes mirror). |
| `trigger` | string | ✅ | How the policy was created: `"manual"` or `"auto"`. |
| `reason` | string | ❌ | For auto-created policies, the trigger reason. |

---

## Platform Integration Notes

### Ingestion Strategy

The **data-ingestion** service should:

1. Listen on MQTT topic `jz/+/logs/#` for V2 events.
2. Continue listening on rsyslog:9080 for V1 events.
3. For **attack** events (both V1 and V2): normalize to the internal `attack-events` Kafka format (existing `AttackEvent` DTO).
4. For **heartbeat** events: extract `network_topology` and `devices[]` for the future topology service; forward device status to `status-events` Kafka topic.
5. For **sniffer**, **threat**, **bg**, **audit**, **policy** events: route to new Kafka topics as they become needed, or store directly.

### V2 → Internal Kafka Mapping (Attack Events)

| V2 Field | Kafka `attack-events` Field |
|----------|----------------------------|
| `device_id` | `deviceSerial` |
| `data.src_mac` | `attackMac` |
| `data.src_ip` | `attackIp` |
| `data.guard_ip` | `responseIp` |
| `data.dst_port` | `responsePort` |
| `ts` | `timestamp` (parse to Instant) |
| `data.vlan_id` | *(new field needed)* |
| `data.guard_type` | *(new field needed)* |
| `data.protocol` | *(new field needed)* |

### Sequence Number Usage

- Use `seq` for deduplication: if `(device_id, seq)` was already processed, skip.
- Use `seq` for gap detection: if seq jumps by >1, request replay from device or log a warning.
- Seq resets to 0 on device restart (detect via `device_id` + `uptime_sec` in heartbeat).

### Size Considerations

- Attack/sniffer/threat/audit/policy events: typically < 1 KB each.
- Background events: < 2 KB each.
- Heartbeat events: variable — up to ~100 KB with 200 devices in the `devices[]` array. MQTT broker must be configured for messages up to 256 KB.
