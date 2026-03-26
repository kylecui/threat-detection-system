# Threat Detection Platform — Top-Down Overhaul Plan

## Context

Cloud-native honeypot/deception defense threat detection platform. 7 Java/Spring Boot microservices + React frontend. Must support old sentinel (V1 syslog KV) and new sentinel (V1 compat + V2 JSON via MQTT). User requires strictly top-down work: Design → Framework → Module.

## Critical Pre-Planning Corrections (from Metis Analysis)

### Listed P0 Bugs — ALREADY FIXED (Verified in Code)

| Listed P0 | Actual Status | Evidence |
|-----------|---------------|----------|
| Kafka topic mismatch (`threat-events` vs `threat-alerts`) | ✅ FIXED | `alert-management/application-docker.properties` line 48: `app.kafka.topics.threat-events=threat-alerts` — property indirection resolves correctly |
| Missing `alerts`/`notifications` table SQL | ✅ FIXED | `docker/04-alert-management-tables.sql` exists with `CREATE TABLE IF NOT EXISTS` for all 4 tables |
| JPA DDL `create-drop` strategy | ✅ FIXED | All services use `validate` (docker) or `update` (dev). No `create-drop` found anywhere |
| Threat Assessment hardcodes `threat-events` | ✅ FIXED | `NewThreatAlertConsumer.java` uses `${kafka.topics.threat-alerts:threat-alerts}` — correctly configured |

**Action**: Archive `docs/fixes/P0_FIX_CHECKLIST.md` as resolved. Do NOT spend time "fixing" these.

### NEW Real P0 Bugs Discovered

| New P0 | Severity | Details |
|--------|----------|---------|
| **P0-A: SQL initialization ordering** | HIGH | 21 SQL files in `docker/` but unclear which execute on fresh deploy. If `docker-entrypoint-initdb.d` only runs `init-db.sql`, numbered migrations (02-17) may not execute → missing tables |
| **P0-B: H2 dialect default** | MEDIUM | `alert-management/application.properties` line 14 defaults to `H2Dialect` instead of `PostgreSQLDialect`. Any non-Docker deployment uses wrong SQL dialect |
| **P0-C: Dead Kafka topic** | LOW | `minute-aggregations` topic produced by stream-processing but never consumed. Dead data accumulating |

---

## Phase 1 — Design Revision

### 1.1 Fix README.md Contradictions & Duplication
- **Problem**: README.md is ~776 lines with 2 contradictory copies of content. API Gateway listed as both ✅ complete and 🟡 planned. Config Server listed as both ✅ complete and 🟡 planned. Port 8082 listed for both alert-management and API gateway.
- **Action**: Deduplicate README.md to single authoritative version. Set accurate statuses: API Gateway = partial (code exists, no tests/K8s), Config Server = not started (empty directory). Fix port mapping to match docker-compose truth (8082=alert-management, 8888=api-gateway). Remove duplicate tech stack, quick start, data flow, features, and roadmap sections.
- **QA Scenario**:
  1. `bash`: `wc -l README.md` → expect < 500 lines
  2. `grep`: Search `README.md` for `✅.*配置服务器` → expect 0 matches (config-server should NOT be marked complete)
  3. `grep`: Search `README.md` for `✅.*API网关` → expect 0 matches (api-gateway should NOT be marked complete)
  4. `grep`: Search `README.md` for duplicate section headers (e.g., `## 🛠️ 技术栈` appearing twice) → expect 0 duplicates
  5. `read`: Verify port table has exactly: 8080=data-ingestion, 8081=Flink, 8082=alert-management, 8083=threat-assessment, 8084=customer-management, 8888=api-gateway
- **Effort**: 30 min

### 1.2 Archive Resolved P0 Checklist + Document Real P0s
- **Problem**: `docs/fixes/P0_FIX_CHECKLIST.md` lists 4 bugs that are already fixed. 3 real P0s undocumented.
- **Action**: Mark listed P0s as resolved with evidence (property indirection, SQL files exist, DDL strategy correct). Add 3 new real P0s (SQL init ordering, H2 dialect default, dead topic).
- **QA Scenario**:
  1. `read`: Open `docs/fixes/P0_FIX_CHECKLIST.md` → verify all 4 original items marked `[x]` with resolution notes
  2. `grep`: Search for `P0-A`, `P0-B`, `P0-C` in the file → expect 3 matches (new P0s documented)
  3. `read`: Verify each new P0 has: description, severity, affected files, and fix steps
- **Effort**: 20 min

### 1.3 Design V2 JSON Event Schemas
- **Problem**: New sentinel sends 7 event types (attack, sniffer, threat, bg, heartbeat, audit, policy) in V2 JSON format. No schema definitions in the platform.
- **Action**: Create `docs/design/v2_event_schemas.md` with JSON Schema or equivalent for all 7 types. Map V2 fields to existing V1 fields where applicable. Define new fields for network topology, device fingerprinting.
- **Source**: `/mnt/d/MyWorkSpaces/jz_sniff_rn/design.md` (primary design spec), `/mnt/d/MyWorkSpaces/jz_sniff_rn/phase2_plan.md` (format details), `/mnt/d/MyWorkSpaces/jz_sniff_rn/config/base.yaml` (transport config). Note: The C source files (`log_format.c`, `mqtt.c`) are in the external sentinel repo and were analyzed during exploration — their findings are captured in this plan's context section above.
- **QA Scenario**:
  1. `read`: Open `docs/design/v2_event_schemas.md` → verify file exists and has content
  2. `grep`: Search for each event type name (`"attack"`, `"sniffer"`, `"threat"`, `"bg"`, `"heartbeat"`, `"audit"`, `"policy"`) → expect 7 matches (all types defined)
  3. `read`: Verify each event type has: JSON example, field table (name/type/required/description), V1 mapping column where applicable
  4. `grep`: Search for `v1_mapping` or `V1 Mapping` → expect ≥1 match per attack/heartbeat type (the two types with V1 equivalents)
- **Effort**: 2 hours

### 1.4 Design MQTT Ingestion Architecture
- **Problem**: New sentinel uses MQTT (QoS 1) for V2 events. Platform has zero MQTT support.
- **Action**: Create `docs/design/mqtt_ingestion_architecture.md` covering:
  - Broker selection: EMQX (K8s native, built-in Kafka bridge) vs Mosquitto (simpler but no clustering)
  - Topic structure: `jz/{device_id}/logs/{type}` subscription via `jz/+/logs/#`
  - Client library: HiveMQ MQTT Client (Netty async, manual ack for QoS 1 → Kafka guarantee)
  - Integration point: MQTT → data-ingestion service (new listener) → normalize → Kafka
  - Dual protocol coexistence: syslog (V1) + MQTT (V2) both feeding same Kafka topics
  - LWT monitoring: `jz/{device_id}/status` for device online/offline tracking
  - Docker Compose addition: EMQX broker service
  - K8s addition: EMQX StatefulSet
- **MUST NOT**: Implement any code — design doc only.
- **QA Scenario**:
  1. `read`: Open `docs/design/mqtt_ingestion_architecture.md` → verify file exists
  2. `grep`: Search for `EMQX` or `Mosquitto` → expect ≥1 match (broker choice documented)
  3. `grep`: Search for `jz/+/logs/#` or `jz/{device_id}/logs` → expect ≥1 match (topic structure)
  4. `grep`: Search for `QoS` → expect ≥1 match (delivery guarantee documented)
  5. `read`: Verify doc contains: architecture diagram (ASCII/Mermaid), component list, topic→Kafka mapping table, sequence diagram
  6. `grep`: Search for any Java/Python/code import statements → expect 0 matches (no code, design only)
- **Effort**: 2 hours

### 1.5 Design Network Topology & Device Fingerprint Data Model
- **Problem**: V2 heartbeats include rich network topology data (devices, OS, vendor, class, confidence scores). No data model exists.
- **Action**: Create `docs/design/network_topology_data_model.md` with:
  - PostgreSQL table designs for device inventory, topology snapshots, interface stats
  - Relationship to existing `device_customer_mapping` table
  - Data retention policy (topology snapshots are time-series)
  - Query patterns for frontend dashboard
- **Depends on**: 1.3 (V2 event schemas)
- **QA Scenario**:
  1. `read`: Open `docs/design/network_topology_data_model.md` → verify file exists
  2. `grep`: Search for `CREATE TABLE` or table name patterns (`device_inventory`, `topology_snapshot`, `interface_stats`) → expect ≥2 matches
  3. `grep`: Search for `device_customer_mapping` → expect ≥1 match (relationship to existing table documented)
  4. `grep`: Search for `retention` or `TTL` → expect ≥1 match (data retention policy defined)
  5. `grep`: Search for `INDEX` or `index` → expect ≥1 match (index strategy present)
- **Effort**: 1.5 hours

### 1.6 Document ClickHouse → PostgreSQL Decision
- **Problem**: Original cloud design (jzzn_Cloud) planned ClickHouse for time-series analytics. Current implementation uses PostgreSQL. Decision undocumented.
- **Action**: Create `docs/design/adr/001-postgresql-over-clickhouse.md` (Architecture Decision Record) explaining: why PostgreSQL was chosen (operational simplicity, single-digit customer scale), when ClickHouse becomes necessary (>10M events/day), migration path.
- **QA Scenario**:
  1. `read`: Open `docs/design/adr/001-postgresql-over-clickhouse.md` → verify file exists
  2. `grep`: Search for `Context`, `Decision`, `Consequences` → expect 3 matches (standard ADR sections)
  3. `grep`: Search for `ClickHouse` → expect ≥2 matches (comparison discussed)
  4. `grep`: Search for `migration` or `scale` → expect ≥1 match (future path documented)
- **Effort**: 30 min

### 1.7 Address Net Weighting Gap
- **Problem**: Original system had 186 net segment weight configs for IP-based threat scoring. New system has zero net weighting — uses simplified diversity algorithm.
- **Action**: Create `docs/design/net_weighting_strategy.md` documenting:
  - What the original system did (186 hardcoded net segments with weights)
  - Why simplified approach was chosen (dynamic vs static, customer-specific nets)
  - Hybrid strategy: configurable net segment weights per customer via customer-management service
  - Data model: `net_segment_weights` table (customer_id, cidr, weight, description)
- **QA Scenario**:
  1. `read`: Open `docs/design/net_weighting_strategy.md` → verify file exists
  2. `grep`: Search for `186` → expect ≥1 match (original system's config count referenced)
  3. `grep`: Search for `net_segment_weights` or `cidr` → expect ≥1 match (data model defined)
  4. `grep`: Search for `customer` → expect ≥1 match (per-customer configurability discussed)
- **Effort**: 1 hour

### 1.8 Fix docs/README.md Navigation
- **Problem**: `docs/README.md` (documentation center) doesn't include customer-management service in navigation. Some links may be stale.
- **Action**: Add customer-management section. Verify all navigation links resolve to existing files.
- **QA Scenario**:
  1. `grep`: Search `docs/README.md` for `customer-management` → expect ≥1 match
  2. `grep`: Search `docs/README.md` for all 7 service names → expect 7 matches
  3. Manual: Extract all `](...)` markdown links from `docs/README.md`, verify each target file exists via `glob`
- **Effort**: 20 min

---

## Phase 2 — Framework Gaps

### 2.0 Fix Real P0 Bugs (Prerequisite for All Framework Work)
- **2.0a: SQL initialization ordering** — Verify that `docker/docker-compose.yml` mounts all numbered SQL files to `/docker-entrypoint-initdb.d/` in correct order. If not, fix the volume mount or consolidate into ordered execution.
  - **QA Scenario**:
    1. `read`: Open `docker/docker-compose.yml` → find postgres service `volumes:` section → verify all SQL files (01-17) are mounted to `/docker-entrypoint-initdb.d/`
    2. `bash`: `ls -la docker/*.sql | wc -l` → note count of SQL files
    3. `grep`: Search `docker/docker-compose.yml` for `docker-entrypoint-initdb.d` → verify mount count matches SQL file count
    4. `bash` (if Docker available): `docker compose -f docker/docker-compose.yml up -d postgres && sleep 5 && docker compose -f docker/docker-compose.yml exec postgres psql -U threat_user -d threat_detection -c "\dt" | grep -c "public"` → expect ≥10 tables
  - **Effort**: 1 hour
- **2.0b: H2 dialect default** — Change `services/alert-management/src/main/resources/application.properties` line 14 default from `H2Dialect` to `PostgreSQLDialect`. Keep the env var override mechanism intact.
  - **QA Scenario**:
    1. `grep`: Search `services/alert-management/src/main/resources/application.properties` for `H2Dialect` → expect 0 matches
    2. `grep`: Search `services/alert-management/src/main/resources/application.properties` for `PostgreSQLDialect` → expect 1 match
    3. `grep`: Search entire `services/alert-management/` for `H2Dialect` → expect 0 matches in production code (test files may still reference H2 for unit tests — that's acceptable)
  - **Effort**: 5 min
- **2.0c: Dead topic documentation** — Document `minute-aggregations` topic purpose in architecture docs, or add a consumer, or remove the producer.
  - **QA Scenario**:
    1. `grep`: Search `docs/` for `minute-aggregations` → expect ≥1 match (topic documented)
    2. If topic kept: `grep` for `minute-aggregations` across `services/` → verify both a producer and consumer exist, OR documentation explains why it's produced without consumer
  - **Effort**: 30 min

### 2.1 Implement Config Server
- **Problem**: `services/config-server/` has only a README (no Java source, no pom.xml, no Dockerfile).
- **Action**: Implement Spring Cloud Config Server:
  - `pom.xml` with `spring-cloud-config-server` dependency
  - `ConfigServerApplication.java` with `@EnableConfigServer`
  - `application.yml` with native filesystem backend (for simplicity; Git backend documented as upgrade path)
  - Seed config files for at least `data-ingestion.properties` in a `config-repo/` directory
  - Use `optional:configserver:` in all client services to prevent cascade failure
  - `Dockerfile` following existing service patterns (multi-stage build)
  - Integration into `docker/docker-compose.yml`
- **Pattern**: Follow `services/data-ingestion/` for project structure
- **MUST**: Use `optional:configserver:` — never `fail-fast: true`
- **QA Scenario**:
  1. `glob`: `services/config-server/src/main/java/**/*.java` → expect ≥1 file (application class)
  2. `read`: Open `services/config-server/pom.xml` → verify `spring-cloud-config-server` dependency exists
  3. `grep`: Search `services/config-server/` for `@EnableConfigServer` → expect 1 match
  4. `read`: Open `services/config-server/src/main/resources/application.yml` → verify config backend defined
  5. `glob`: `services/config-server/Dockerfile` → expect 1 match
  6. `grep`: Search `docker/docker-compose.yml` for `config-server` → expect ≥1 match (service defined)
  7. `bash` (if Docker available): `docker compose -f docker/docker-compose.yml up -d config-server && sleep 10 && curl -s http://localhost:8888/actuator/health | grep -c UP` → expect 1
- **Effort**: 4 hours

### 2.2 Add K8s Manifests for Missing Services
- **Problem**: K8s manifests missing for api-gateway and config-server.
- **Action**: Create `k8s/base/api-gateway.yaml` and `k8s/base/config-server.yaml` following pattern of existing manifests (e.g., `data-ingestion.yaml`). Update `k8s/base/kustomization.yaml` to include them.
- **Pattern**: Follow `k8s/base/data-ingestion.yaml` for structure
- **QA Scenario**:
  1. `glob`: `k8s/base/api-gateway.yaml` → expect 1 match
  2. `glob`: `k8s/base/config-server.yaml` → expect 1 match
  3. `grep`: Search `k8s/base/kustomization.yaml` for `api-gateway.yaml` → expect 1 match
  4. `grep`: Search `k8s/base/kustomization.yaml` for `config-server.yaml` → expect 1 match
  5. `bash`: `kubectl apply -k k8s/base/ --dry-run=client 2>&1 | grep -c "configured\|created"` → expect no errors (exit code 0)
- **Effort**: 1.5 hours

### 2.3 Add Stream Processing Tests
- **Problem**: Flink stream processing has zero test coverage. Most dangerous untested service — windowing bugs cause incorrect threat scores.
- **Action**: Create test suite:
  - Window aggregation tests (30s/5min/15min) with Flink MiniCluster + TestHarness
  - Threat scoring logic unit tests
  - Event parsing/deserialization tests
  - Use event-time processing with controlled watermarks (NOT processing time — flaky)
- **MUST**: Use `MiniClusterExtension` or `TestHarness` for deterministic assertions
- **QA Scenario**:
  1. `glob`: `services/stream-processing/src/test/java/**/*Test.java` → expect ≥3 files
  2. `bash`: `mvn test -pl services/stream-processing -f services/stream-processing/pom.xml 2>&1 | tail -20` → expect `BUILD SUCCESS` and `Tests run: N` where N ≥ 3
  3. `grep`: Search test files for `MiniCluster` or `TestHarness` → expect ≥1 match (proper Flink test infrastructure)
  4. `grep`: Search test files for `ProcessingTime` → expect 0 matches (must use event-time only)
- **Effort**: 4 hours

### 2.4 Add API Gateway Tests
- **Problem**: API Gateway has no test coverage. Misconfiguration affects all services.
- **Action**: Create test suite:
  - Route resolution tests (verify each route maps to correct downstream service)
  - Health endpoint test
  - Error handling tests (downstream service unavailable)
  - Use `WebTestClient` + `WireMock` for mocked downstream services
- **QA Scenario**:
  1. `glob`: `services/api-gateway/src/test/java/**/*Test.java` → expect ≥2 files
  2. `bash`: `mvn test -pl services/api-gateway -f services/api-gateway/pom.xml 2>&1 | tail -20` → expect `BUILD SUCCESS` and `Tests run: N` where N ≥ 2
  3. `grep`: Search test files for `WebTestClient` or `MockServer` or `WireMock` → expect ≥1 match
- **Effort**: 3 hours

---

## Phase 3 — Module & Detail Fixes

### 3.1 Standardize Field Naming
- **Problem**: `docs/api/FIELD_NAMING_INCONSISTENCY.md` documents camelCase vs snake_case inconsistencies.
- **Action**: Audit all API DTOs and database entities. Ensure all use snake_case per project standard. Add `@JsonProperty("snake_case")` annotations where needed. Start from the inconsistency doc's list of affected files.
- **QA Scenario**:
  1. `read`: Open `docs/api/FIELD_NAMING_INCONSISTENCY.md` → extract list of affected files
  2. `grep`: Search `services/*/src/main/java/**/*Dto.java` and `*Response.java` for camelCase field names without `@JsonProperty` → expect 0 matches
  3. `lsp_diagnostics`: Run on each modified file → expect 0 errors
- **Effort**: 2 hours

### 3.2 Fix Port Documentation Conflicts
- **Problem**: README lists port 8082 for both API Gateway and alert-management in different sections.
- **Action**: Correct to match docker-compose truth: 8080=data-ingestion, 8081=Flink UI, 8082=alert-management, 8083=threat-assessment, 8084=customer-management, 8888=api-gateway
- **Note**: May already be handled in 1.1 (README dedup). Verify after 1.1 completes.
- **QA Scenario**:
  1. `grep`: Search `README.md` for `8082` → every occurrence should reference `alert-management`, never `api-gateway`
  2. `grep`: Search `README.md` for `8888` → every occurrence should reference `api-gateway` or `config-server`
- **Effort**: 10 min

### 3.3 Security: Remove Exposed Credentials
- **Problem**: Hardcoded SMTP credentials exist in multiple committed files:
  - `docker/docker-compose.yml` (lines 317-322): SMTP host, auth settings
  - `services/alert-management/src/main/resources/application.properties` (lines 54-63): SMTP host, username, **password** (`TTXWjJiuxmE2HCRE`)
  - `services/alert-management/src/main/resources/application-docker.properties` (lines 61-69): SMTP host, username, **password** (`TTXWjJiuxmE2HCRE`)
- **Action**:
  1. Create `docker/.env.example` with placeholder SMTP values (e.g., `SPRING_MAIL_PASSWORD=your-smtp-password-here`)
  2. Create or update `docker/.env` with actual values (ensure `.env` is in `.gitignore`)
  3. Replace hardcoded SMTP values in `docker/docker-compose.yml` with `${SPRING_MAIL_PASSWORD}` env var references
  4. Replace hardcoded SMTP password in `application.properties` with `${SPRING_MAIL_PASSWORD:}` (empty default)
  5. Replace hardcoded SMTP password in `application-docker.properties` with `${SPRING_MAIL_PASSWORD}` env var
  6. Verify `.gitignore` contains `docker/.env` and `*.env` patterns (not `.env.example`)
- **QA Scenario**:
  1. `grep`: Search entire repo for `TTXWjJiuxmE2HCRE` → expect 0 matches (password removed from all committed files)
  2. `grep`: Search `docker/.env.example` for `SPRING_MAIL_PASSWORD` → expect 1 match (template exists)
  3. `grep`: Search `.gitignore` for `.env` → expect ≥1 match (env files git-ignored)
  4. `grep`: Search `services/alert-management/src/main/resources/application.properties` for `password=` with a literal value (not `${...}`) → expect 0 matches
  5. `grep`: Search `services/alert-management/src/main/resources/application-docker.properties` for `password=` with a literal value → expect 0 matches
- **Effort**: 45 min

---

## Execution Order & Dependencies

```
Phase 1 (Design — can parallelize internally):
  1.1 README dedup ──────────────────→ unblocks everything
  1.2 Archive P0 checklist ──────────→ independent
  1.3 V2 event schemas ─────────────→ blocks 1.4, 1.5
  1.4 MQTT architecture ─────────────→ depends on 1.3
  1.5 Topology data model ───────────→ depends on 1.3
  1.6 ClickHouse ADR ────────────────→ independent
  1.7 Net weighting strategy ────────→ independent
  1.8 Fix docs navigation ──────────→ independent

Phase 2 (Framework — some can parallel with late Phase 1):
  2.0 P0 fixes (SQL, H2, dead topic) → prerequisite for all Phase 2
  2.1 Config Server ─────────────────→ depends on 2.0
  2.2 K8s manifests ─────────────────→ depends on 2.1 (needs config-server image)
  2.3 Stream Processing tests ───────→ independent (can parallel with 2.1)
  2.4 API Gateway tests ─────────────→ independent (can parallel with 2.1)

Phase 3 (Module — after Phase 2):
  3.1 Field naming ──────────────────→ after Phase 2 tests pass
  3.2 Port docs ─────────────────────→ after 1.1
  3.3 SMTP credentials ─────────────→ independent
```

## Estimated Total Effort

| Phase | Items | Estimated Hours |
|-------|-------|----------------|
| Phase 1 | 8 items | ~8 hours |
| Phase 2 | 5 items | ~13 hours |
| Phase 3 | 3 items | ~3 hours |
| **Total** | **16 items** | **~24 hours** |

## Out of Scope (Deferred)

- MQTT implementation (code) — design only in this plan
- APT state machine implementation
- ML pipeline implementation
- Istio/service mesh deployment
- ClickHouse migration
- Frontend changes
- New sentinel V2 event parsing code (blocked on MQTT architecture design)
