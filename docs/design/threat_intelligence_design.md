# Threat Intelligence Integration — Design Document

**Version**: 1.0  
**Date**: 2026-03-27  
**Status**: Phase 1 — Internal Curated Database  
**Service Port**: 8085

---

## 1. Overview

The Threat Intelligence service enriches the existing honeypot-based threat detection pipeline with external and internal threat intelligence data. When the stream-processing scoring pipeline evaluates an attack IP, it queries this service for known indicators of compromise (IOCs). The returned confidence score becomes an `intelWeight` multiplier in the existing threat scoring formula.

### Design Principles

- **STIX 2.1 aligned** — Data model follows STIX Indicator SDO field conventions for future TAXII interop
- **Honeypot-native** — Internal honeypot sightings are first-class indicators with highest trust weight
- **Scoring-integrated** — Directly plugs into `TierWindowProcessor.calculateThreatScore()` as a new weight factor
- **Cloud-native** — Containerized Spring Boot service, same patterns as customer-management
- **Phased rollout** — Phase 1 = internal curated DB + REST API; Phase 2 = external feed pollers (AbuseIPDB, OTX, etc.)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Scoring Pipeline                                 │
│                                                                          │
│  TierWindowProcessor                                                     │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ threatScore = baseScore × timeWeight × ipWeight × deviceWeight   │    │
│  │            × timeDistWeight × netWeight × intelWeight ◀── NEW    │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│         ▲                                                                │
│         │ HTTP GET /api/v1/threat-intel/lookup?ip={attackIp}             │
│         │ Response: { "confidence": 85, "severity": "HIGH", ... }       │
│         │                                                                │
│  ThreatIntelServiceClient (LRU cache, 5min TTL, fallback=1.0)          │
└──────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Threat Intelligence Service (port 8085)                                 │
│                                                                          │
│  ┌─────────────┐   ┌──────────────────┐   ┌──────────────────────┐     │
│  │ REST API    │──▶│ IndicatorService │──▶│ PostgreSQL           │     │
│  │ /lookup     │   │ (exact + CIDR)   │   │ threat_indicators    │     │
│  │ /indicators │   │                  │   │ threat_intel_feeds   │     │
│  └─────────────┘   └──────────────────┘   └──────────────────────┘     │
│                                                                          │
│  Phase 2 (future):                                                       │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ @Scheduled Feed Pollers                                          │   │
│  │ AbuseIPDB │ AlienVault OTX │ VirusTotal │ Internal Honeypot     │   │
│  │ (Bucket4j rate limit + Resilience4j circuit breaker)             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Model

### 3.1 threat_indicators Table

STIX 2.1 aligned, with operational extensions for our honeypot context.

```sql
CREATE TABLE threat_indicators (
    id                  BIGSERIAL PRIMARY KEY,
    
    -- IOC identification
    ioc_value           TEXT NOT NULL,                    -- "192.168.1.100", "evil.com", "d41d8cd98f..."
    ioc_type            VARCHAR(20) NOT NULL,             -- IP_V4, IP_V6, CIDR, DOMAIN, FILE_HASH
    ioc_inet            INET,                             -- Populated for IP/CIDR types (enables GiST)
    
    -- STIX 2.1 Indicator fields
    indicator_type      VARCHAR(50) DEFAULT 'malicious-activity',
    pattern             TEXT,                             -- STIX pattern: [ipv4-addr:value = '1.2.3.4']
    pattern_type        VARCHAR(20) DEFAULT 'stix',
    confidence          SMALLINT CHECK (confidence BETWEEN 0 AND 100),
    valid_from          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until         TIMESTAMPTZ,                     -- NULL = no expiry
    
    -- Operational extensions
    severity            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',  -- CRITICAL, HIGH, MEDIUM, LOW, INFO
    source_name         VARCHAR(100) NOT NULL,                   -- 'internal', 'abuseipdb', 'otx', etc.
    description         TEXT,
    tags                TEXT[],
    
    -- Sighting tracking
    sighting_count      INTEGER DEFAULT 1,
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_ioc_type CHECK (ioc_type IN ('IP_V4', 'IP_V6', 'CIDR', 'DOMAIN', 'FILE_HASH')),
    CONSTRAINT chk_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_inet_for_ip CHECK (
        ioc_type NOT IN ('IP_V4', 'IP_V6', 'CIDR') OR ioc_inet IS NOT NULL
    ),
    CONSTRAINT uq_ioc_source UNIQUE (ioc_value, source_name)
);
```

### 3.2 Indexes

```sql
-- Exact IOC lookup (most common query from scoring pipeline)
CREATE INDEX idx_indicator_ioc_value ON threat_indicators USING HASH (ioc_value);

-- CIDR containment: "Is 1.2.3.4 inside any known-bad CIDR range?"
CREATE INDEX idx_indicator_inet_gist ON threat_indicators USING GIST (ioc_inet inet_ops)
    WHERE ioc_inet IS NOT NULL;

-- Active indicators only (partial index eliminates expired)
CREATE INDEX idx_active_indicators ON threat_indicators (ioc_value, severity, confidence)
    WHERE (valid_until IS NULL OR valid_until > NOW());

-- Source filtering
CREATE INDEX idx_indicator_source ON threat_indicators (source_name, ioc_type, created_at DESC);

-- Tags search
CREATE INDEX idx_indicator_tags ON threat_indicators USING GIN (tags);
```

### 3.3 threat_intel_feeds Table (Phase 2 prep)

```sql
CREATE TABLE threat_intel_feeds (
    id                  BIGSERIAL PRIMARY KEY,
    feed_name           VARCHAR(100) NOT NULL UNIQUE,
    feed_url            TEXT,
    feed_type           VARCHAR(50) NOT NULL,      -- 'REST_API', 'TAXII', 'CSV', 'INTERNAL'
    enabled             BOOLEAN DEFAULT true,
    poll_interval_hours INTEGER DEFAULT 6,
    api_key_env_var     VARCHAR(100),              -- env var name, NOT the key itself
    source_weight       DOUBLE PRECISION DEFAULT 0.5,  -- trust weight for scoring
    last_poll_at        TIMESTAMPTZ,
    last_poll_status    VARCHAR(20),               -- 'SUCCESS', 'FAILED', 'TIMEOUT'
    indicator_count     INTEGER DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 4. REST API

### 4.1 Lookup Endpoint (called by scoring pipeline)

```
GET /api/v1/threat-intel/lookup?ip={ip}
```

**Response** (fastest path — single IP):
```json
{
  "ip": "192.168.1.100",
  "found": true,
  "confidence": 85,
  "severity": "HIGH",
  "intelWeight": 2.5,
  "sources": ["internal", "abuseipdb"],
  "indicatorCount": 3,
  "lastSeenAt": "2026-03-27T10:30:00Z"
}
```

**When not found** (`intelWeight` defaults to 1.0 — no amplification):
```json
{
  "ip": "10.0.0.50",
  "found": false,
  "confidence": 0,
  "severity": "INFO",
  "intelWeight": 1.0,
  "sources": [],
  "indicatorCount": 0,
  "lastSeenAt": null
}
```

### 4.2 CRUD Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/threat-intel/lookup?ip={ip}` | Fast IP lookup for scoring pipeline |
| `GET` | `/api/v1/threat-intel/indicators` | List indicators (paginated) |
| `GET` | `/api/v1/threat-intel/indicators/{id}` | Get indicator by ID |
| `POST` | `/api/v1/threat-intel/indicators` | Create indicator |
| `PUT` | `/api/v1/threat-intel/indicators/{id}` | Update indicator |
| `DELETE` | `/api/v1/threat-intel/indicators/{id}` | Delete indicator |
| `POST` | `/api/v1/threat-intel/indicators/bulk` | Bulk upsert indicators |
| `POST` | `/api/v1/threat-intel/indicators/{id}/sighting` | Increment sighting count |
| `GET` | `/api/v1/threat-intel/feeds` | List configured feeds |
| `GET` | `/api/v1/threat-intel/statistics` | Service statistics |

### 4.3 IntelWeight Calculation

The `intelWeight` returned to the scoring pipeline is a multiplier (1.0 = neutral, >1.0 = amplifies threat score):

```java
double intelWeight = calculateIntelWeight(confidence, severity);

// Mapping:
// confidence 0-20,  severity INFO     → 1.0  (no effect)
// confidence 20-40, severity LOW      → 1.2
// confidence 40-60, severity MEDIUM   → 1.5
// confidence 60-80, severity HIGH     → 2.0
// confidence 80+,   severity CRITICAL → 3.0

// When multiple indicators match (exact + CIDR), use the highest confidence.
// If multiple sources report the same IOC, confidence is boosted:
//   multiSourceBoost = min(100, maxConfidence + (sourceCount - 1) * 10)
```

---

## 5. Scoring Integration

### 5.1 Updated Formula

```
threatScore = (attackCount × uniqueIps × uniquePorts × portWeight) 
            × timeWeight × ipWeight × deviceWeight × timeDistWeight 
            × netWeight × intelWeight
```

### 5.2 ThreatIntelServiceClient

Follows the proven `NetWeightServiceClient` pattern:

```java
public class ThreatIntelServiceClient {
    // HTTP client with 3s timeout
    // LRU cache: 1000 entries, 5min TTL (threat intel changes less frequently)
    // Fallback: intelWeight = 1.0 (fail-open, don't block scoring)
    
    public double getIntelWeight(String attackIp) {
        // 1. Check LRU cache
        // 2. HTTP GET /api/v1/threat-intel/lookup?ip={attackIp}
        // 3. Parse response, extract intelWeight
        // 4. Cache result
        // 5. On failure: log warning, return 1.0
    }
}
```

### 5.3 AggregatedAttackData Changes

Add two new fields:
- `intelScore` (int, 0-100) — raw confidence from threat intel
- `intelWeight` (double) — multiplier used in scoring formula

---

## 6. Service Architecture

### 6.1 Package Structure

```
services/threat-intelligence/
├── src/main/java/com/threatdetection/intelligence/
│   ├── ThreatIntelligenceApplication.java
│   ├── controller/
│   │   └── ThreatIndicatorController.java
│   ├── service/
│   │   └── ThreatIndicatorService.java
│   ├── repository/
│   │   └── ThreatIndicatorRepository.java
│   ├── model/
│   │   ├── ThreatIndicator.java          (JPA Entity)
│   │   ├── ThreatIntelFeed.java          (JPA Entity)
│   │   ├── IocType.java                  (Enum)
│   │   └── Severity.java                 (Enum)
│   ├── dto/
│   │   ├── LookupResponse.java
│   │   ├── CreateIndicatorRequest.java
│   │   ├── UpdateIndicatorRequest.java
│   │   ├── IndicatorResponse.java
│   │   ├── BulkUpsertRequest.java
│   │   └── StatisticsResponse.java
│   ├── exception/
│   │   ├── IndicatorNotFoundException.java
│   │   └── GlobalExceptionHandler.java
│   └── config/
│       └── JpaConfig.java
├── src/main/resources/
│   ├── application.yml
│   └── application-docker.yml
├── Dockerfile
└── pom.xml
```

### 6.2 Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | Spring Boot 3.1.5 | Match existing services |
| ORM | Spring Data JPA | Match customer-management pattern |
| Database | PostgreSQL 15 (shared instance) | Shared DB, separate tables |
| Port | 8085 | Next available after customer-management (8084) |
| Build | Maven | Match parent POM |
| Java | 21 LTS | Match all services |
| Annotations | Lombok | Match customer-management |

---

## 7. Phase 2 — External Feed Connectors (Future)

### Planned Feeds

| Feed | Type | Rate Limit | Priority |
|------|------|-----------|----------|
| Internal Honeypot | Direct DB write | Unlimited | P0 |
| AbuseIPDB | REST API | 1000 req/day (free) | P1 |
| AlienVault OTX | REST API | Generous free tier | P1 |
| VirusTotal | REST API | 500 req/day (free) | P2 |
| GreyNoise | REST API | 5000 req/day (community) | P2 |

### Feed Poller Architecture

- `@Scheduled` with `fixedDelay` (not `fixedRate`) to prevent overlap
- Bucket4j token bucket per feed for rate limiting
- Resilience4j `@CircuitBreaker` per feed
- Caffeine cache for deduplication
- Bulk upsert on poll completion

### ipit/TIRE Reference Integration

The user's existing [TIRE project](https://github.com/kylecui/ipit/tree/v1.0.1) can be deployed as a sidecar container and called via HTTP for enriched analysis. The `POST /api/v1/analyze/ip` endpoint with `ContextProfile` maps directly to our attack event context (port, direction, protocol).

**Sidecar integration pattern** (Phase 2+):
```
stream-processing → ThreatIntelServiceClient → threat-intelligence (8085)
                                                        │
                                                        ├── Local DB lookup (fast, Phase 1)
                                                        └── TIRE sidecar call (enriched, Phase 2)
```

---

## 8. Migration Numbering

- `22-threat-indicators.sql` — Main table + indexes
- `23-threat-intel-feeds.sql` — Feed configuration table

---

## 9. Docker / K8s Integration

- Added to `docker-compose.yml` as `threat-intelligence` service
- Depends on `postgres`
- Exposed on port `8085:8085`
- Health check: `/actuator/health`
- Environment: same pattern as customer-management

---

## 10. Reference

- [STIX 2.1 Indicator SDO](https://docs.oasis-open.org/cti/stix/v2.1/os/stix-v2.1-os.html#_muftrcpnf89v)
- [PostgreSQL GiST inet indexing](https://www.postgresql.org/docs/15/gist-builtin-opclasses.html)
- [ipit/TIRE project](https://github.com/kylecui/ipit/tree/v1.0.1)
- [Net Weighting Strategy](./net_weighting_strategy.md) — analogous design pattern
