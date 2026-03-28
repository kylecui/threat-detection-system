# Multi-Region Deployment Architecture

**Version**: 1.0
**Date**: 2026-03-28
**Status**: Implementation Ready

---

## Overview

Multi-region deployment enables the Threat Detection System to operate across geographically distributed clusters while maintaining data consistency, low-latency access, and resilience against regional failures.

### Regions

| Region ID | Name | Namespace | Primary Use Case |
|-----------|------|-----------|------------------|
| `east` | US East | `threat-detection-east` | Primary region, US East Coast customers |
| `west` | US West | `threat-detection-west` | US West Coast customers, DR failover |
| `cn` | China | `threat-detection-cn` | China mainland customers, data sovereignty |

---

## Architecture Components

### 1. Kubernetes Region Overlays

Each region is deployed as a separate Kustomize overlay extending the shared `base/` configuration.

```
k8s/
  base/                          # Shared base (all services)
  overlays/
    region-east/                 # US East production overlay
      kustomization.yaml         # References ../../base, sets namespace + REGION
      region-config.yaml         # Region-specific ConfigMap (endpoints, Kafka bootstrap)
      replica-patch.yaml         # Region-specific replica counts
      resource-patch.yaml        # Region-specific resource limits
    region-west/                 # US West production overlay
    region-cn/                   # China production overlay
```

**Key design decisions:**
- Each region overlay sets `REGION` env var on all pods via ConfigMap
- Region namespaces are fully isolated (`threat-detection-east`, etc.)
- Security patches from production overlay are inherited via base
- Region overlays include production-grade resource limits

### 2. Cross-Region Kafka Replication (MirrorMaker 2)

Kafka MirrorMaker 2 replicates critical topics across regions for:
- Cross-region threat correlation
- Centralized analytics aggregation
- Disaster recovery

**Replicated Topics:**
- `attack-events` — Raw attack data
- `threat-alerts` — Computed threat alerts
- `ml-threat-detections` — ML anomaly detections

**Topology:**
```
Region East (Primary)
  kafka:29092
       |
  MirrorMaker 2 ──→ Region West kafka
       |
  MirrorMaker 2 ──→ Region CN kafka
```

MirrorMaker 2 runs as a Deployment in each region, configured via ConfigMap. It uses `source->target` prefix convention (e.g., `east.attack-events` in west region).

### 3. PostgreSQL Cross-Region Replication

**Strategy**: Streaming replication with a primary (east) and standby replicas (west, cn).

```
Region East: postgres (Primary, read-write)
       |
       ├─── async streaming ──→ Region West: postgres-standby (read-only)
       |
       └─── async streaming ──→ Region CN: postgres-standby (read-only)
```

**Components:**
- **Primary**: Standard postgres StatefulSet with replication enabled (`wal_level=replica`, `max_wal_senders=10`)
- **Standby**: postgres-standby StatefulSet configured as hot standby (`hot_standby=on`)
- **PgBouncer**: Connection pooler sidecar for all regions (reduces connection overhead)
- **Failover Script**: `scripts/tools/pg_failover.sh` for manual primary promotion

**Data Sovereignty (CN):**
The CN region can optionally run as an independent primary if data sovereignty requires it. In that mode, MirrorMaker 2 handles all cross-region data sharing at the Kafka level only.

### 4. Region-Aware Services

All microservices receive a `REGION` environment variable injected via the region overlay's ConfigMap. Services use this to:

1. **Tag Kafka messages** with `region` header for cross-region deduplication
2. **Tag DB records** with `region` column for origin tracking
3. **Log region context** for distributed tracing
4. **Route requests** to region-local dependencies

**DB Schema Change (Migration 28):**
```sql
ALTER TABLE threat_assessments ADD COLUMN region VARCHAR(20) DEFAULT 'east';
ALTER TABLE attack_events_raw ADD COLUMN region VARCHAR(20) DEFAULT 'east';
-- Index for region-based queries
CREATE INDEX idx_threat_assessments_region ON threat_assessments(region);
CREATE INDEX idx_attack_events_raw_region ON attack_events_raw(region);
```

### 5. Global Ingress + DNS

**Architecture:**
```
                     ┌─ east.threat-detection.io ──→ Region East Ingress ──→ api-gateway
Global DNS (geo) ──→ ├─ west.threat-detection.io ──→ Region West Ingress ──→ api-gateway
                     └─ cn.threat-detection.io   ──→ Region CN Ingress   ──→ api-gateway
```

**Components:**
- **cert-manager**: Automated TLS certificates via Let's Encrypt for all region domains
- **external-dns**: Automatic DNS record management from Ingress annotations
- **Geo-routing**: DNS-level geographic routing (via external DNS provider or cloud LB)

### 6. Frontend Region Selector

The frontend dashboard includes a region selector in Settings that:
1. Stores selected region in `localStorage`
2. Switches the API base URL to the region-specific gateway endpoint
3. Displays current region in the settings environment info

**Region endpoint map:**
```typescript
const REGION_ENDPOINTS: Record<string, { label: string; apiBase: string }> = {
  east: { label: 'US East', apiBase: 'https://east.threat-detection.io' },
  west: { label: 'US West', apiBase: 'https://west.threat-detection.io' },
  cn:   { label: 'China',   apiBase: 'https://cn.threat-detection.io' },
  auto: { label: 'Auto',    apiBase: '' },  // Uses default VITE_API_BASE_URL
};
```

---

## Deployment Guide

### Deploy a Region

```bash
# Deploy east region
kubectl apply -k k8s/overlays/region-east
kubectl get pods -n threat-detection-east

# Deploy west region
kubectl apply -k k8s/overlays/region-west
kubectl get pods -n threat-detection-west

# Deploy cn region
kubectl apply -k k8s/overlays/region-cn
kubectl get pods -n threat-detection-cn
```

### Verify Cross-Region Replication

```bash
# Check MirrorMaker 2 status
kubectl logs -n threat-detection-east deployment/kafka-mirrormaker -f

# Verify replicated topics in west region
kubectl exec -n threat-detection-west kafka-0 -- kafka-topics --bootstrap-server localhost:9092 --list
# Should show: east.attack-events, east.threat-alerts, east.ml-threat-detections
```

### PostgreSQL Failover

```bash
# Promote standby to primary (emergency)
./scripts/tools/pg_failover.sh promote west

# Check replication status
./scripts/tools/pg_failover.sh status
```

---

## Monitoring

| Metric | Source | Alert Threshold |
|--------|--------|-----------------|
| MirrorMaker lag | MM2 JMX metrics | > 10,000 messages |
| PG replication lag | `pg_stat_replication` | > 60 seconds |
| Cross-region latency | Ingress controller | > 500ms p99 |
| Region pod health | kubelet | Any pod CrashLoopBackOff |

---

## Disaster Recovery

| Scenario | Recovery Action | RTO | RPO |
|----------|----------------|-----|-----|
| Region East down | Failover DNS to West; promote PG standby | 5-10 min | < 1 min |
| Kafka broker failure | MM2 continues from last offset on recovery | Auto | 0 (replicated) |
| PG primary failure | Promote nearest standby; update service endpoints | 2-5 min | < 1 min (async) |
| Network partition | Regions operate independently; reconcile on reconnect | Auto | Eventual |

---

*Last updated: 2026-03-28*
