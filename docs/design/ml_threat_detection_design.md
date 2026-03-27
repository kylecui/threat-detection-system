# ML Threat Detection — Design Document

**Version**: 1.0  
**Date**: 2026-03-27  
**Status**: Phase 1 — Tabular Anomaly Detection (Autoencoder)  
**Service Port**: 8086  
**Language**: Python 3.11 + FastAPI + PyTorch + ONNX Runtime

---

## 1. Overview

The ML Threat Detection service adds machine-learning-based anomaly detection to the existing rule-based scoring pipeline. It consumes aggregated threat data from the Flink stream-processing output, runs inference via ONNX Runtime, and publishes ML-derived weight factors that the threat-assessment service uses as an advisory multiplier.

### Design Principles

- **Advisory, not authoritative** — ML provides a weight multiplier (0.5–3.0); rule-based scoring remains the primary authority
- **Async enrichment** — ML scores arrive via Kafka, not inline with the scoring pipeline. No latency coupling.
- **Fail-open** — If ML service is down, `mlWeight = 1.0` (neutral). Existing scoring is unaffected.
- **Tier-stratified** — Models are trained and inferred per tier (1/2/3). Features are NOT comparable across tiers.
- **Global + per-customer normalization** — One global model, but features are normalized per-customer to avoid cross-tenant contamination.
- **Pre-ML features only** — Training uses raw aggregation features, NEVER features derived from the rule-based `threatScore` (prevents feedback loops).
- **Cloud-native** — Containerized Python service, same Docker-compose patterns as Java services.

### Key Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Framework | PyTorch (training) + ONNX Runtime (serving) | User specified PyTorch; ONNX Runtime ~3x faster for CPU inference |
| Quantization | FP16 minimum | INT8 quantization noise destroys autoencoder reconstruction error precision |
| Integration | Async via Kafka | Avoids coupling ML latency to scoring pipeline; fail-open by design |
| ML Authority | Advisory multiplier (0.5–3.0) | Rule-based scoring remains authoritative; ML adjusts, never overrides |
| Data Source | `threat-alerts` Kafka topic | Pre-aggregated by Flink with 12+ features; `minute-aggregations` topic is dead |
| Model Scope | Global with per-customer normalization | Balances data volume (global) with tenant isolation (normalization) |
| Phase 3 | Ensemble methods (autoencoder + BiGRU) | GNN replaced — bipartite honeypot topology doesn't benefit from graph neural networks |

---

## 2. Architecture

```
                                    ┌─────────────────────────────────────────────┐
                                    │  ML Detection Service (port 8086)            │
                                    │                                              │
 Kafka (threat-alerts) ──────────▶ │  ┌──────────────┐   ┌────────────────────┐  │
                                    │  │ Kafka Consumer │──▶│ Feature Extractor  │  │
                                    │  │ (aiokafka)    │   │ (tier-stratified)  │  │
                                    │  └──────────────┘   └─────────┬──────────┘  │
                                    │                                │              │
                                    │                    ┌───────────▼──────────┐  │
                                    │                    │ ONNX Runtime Engine   │  │
                                    │                    │ (FP16, per-tier model)│  │
                                    │                    └───────────┬──────────┘  │
                                    │                                │              │
                                    │  ┌──────────────┐   ┌────────▼───────────┐  │
                                    │  │ Kafka Producer │◀──│ Score Calculator    │  │
                                    │  │ (aiokafka)    │   │ (mlWeight 0.5-3.0) │  │
                                    │  └──────┬───────┘   └────────────────────┘  │
                                    │         │                                    │
                                    │  ┌──────▼────────────────────────────────┐  │
                                    │  │ REST API: POST /api/v1/ml/detect       │  │
                                    │  │           GET  /health                  │  │
                                    │  └───────────────────────────────────────┘  │
                                    └─────────────────────────────────────────────┘
                                              │
                                              ▼
                              Kafka (ml-threat-detections)
                                              │
                                              ▼
                              ┌────────────────────────────────┐
                              │  Threat Assessment Service      │
                              │  ThreatScoreCalculator          │
                              │  finalScore = ruleScore × mlW   │
                              │  (Phase 2 integration)          │
                              └────────────────────────────────┘
```

### Data Flow

1. Flink `TierWindowProcessor` publishes `AggregatedAttackData` JSON to `threat-alerts` topic
2. ML service consumes from `threat-alerts` via `ml-detection-consumer` consumer group
3. Feature extractor transforms JSON → numpy feature vector (tier-stratified, customer-normalized)
4. ONNX Runtime runs autoencoder inference → reconstruction error → anomaly score
5. Score calculator maps anomaly score → `mlWeight` (0.5–3.0) + `mlConfidence` (0–1.0) + `anomalyType`
6. Result published to `ml-threat-detections` topic
7. (Phase 2) Threat-assessment service consumes `ml-threat-detections` and applies `mlWeight` as advisory multiplier

---

## 3. Data Model

### 3.1 Input: AggregatedAttackData (from threat-alerts topic)

```json
{
  "customerId": "customer_a",
  "attackMac": "00:11:22:33:44:55",
  "attackIp": "192.168.1.100",
  "mostAccessedHoneypotIp": "10.0.0.5",
  "attackCount": 50,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "mixedPortWeight": 1.4,
  "netWeight": 2.0,
  "intelScore": 60,
  "intelWeight": 1.5,
  "eventTimeSpan": 28000,
  "burstIntensity": 0.7,
  "timeDistributionWeight": 1.8,
  "tier": 2,
  "windowType": "TUMBLING",
  "windowStart": "2026-03-27T10:00:00Z",
  "windowEnd": "2026-03-27T10:05:00Z",
  "timestamp": "2026-03-27T10:05:00Z",
  "threatScore": 125.5,
  "threatLevel": "HIGH"
}
```

### 3.2 ML Feature Vector (12 features, per tier)

| # | Feature | Source Field | Normalization |
|---|---------|-------------|---------------|
| 1 | `attack_count_log` | `log1p(attackCount)` | Per-customer rolling mean/std |
| 2 | `unique_ips_norm` | `uniqueIps` | Per-customer rolling mean/std |
| 3 | `unique_ports_norm` | `uniquePorts` | Per-customer rolling mean/std |
| 4 | `unique_devices_norm` | `uniqueDevices` | Min-max (global, 0-10 range) |
| 5 | `mixed_port_weight` | `mixedPortWeight` | Raw (already 1.0–2.0 range) |
| 6 | `net_weight_log` | `log1p(netWeight)` | Raw (already bounded) |
| 7 | `intel_score_norm` | `intelScore / 100.0` | Raw (already 0–1 range) |
| 8 | `event_time_span_log` | `log1p(eventTimeSpan / 1000.0)` | Per-customer rolling mean/std |
| 9 | `burst_intensity` | `burstIntensity` | Raw (already 0–1 range) |
| 10 | `time_dist_weight` | `timeDistributionWeight` | Raw (already 1.0–3.0 range) |
| 11 | `hour_sin` | `sin(2π × hour / 24)` | Cyclical encoding |
| 12 | `hour_cos` | `cos(2π × hour / 24)` | Cyclical encoding |

**Important**: Features 1, 2, 3, 8 use per-customer rolling statistics (24h window). This prevents one customer's traffic volume from contaminating another's baseline.

### 3.3 Output: ML Detection Result

```json
{
  "customerId": "customer_a",
  "attackMac": "00:11:22:33:44:55",
  "attackIp": "192.168.1.100",
  "tier": 2,
  "windowStart": "2026-03-27T10:00:00Z",
  "windowEnd": "2026-03-27T10:05:00Z",
  "mlScore": 0.85,
  "mlWeight": 2.5,
  "mlConfidence": 0.92,
  "anomalyType": "statistical_outlier",
  "reconstructionError": 0.0342,
  "threshold": 0.0150,
  "modelVersion": "autoencoder_v1_tier2",
  "timestamp": "2026-03-27T10:05:01Z"
}
```

### 3.4 mlWeight Calculation

```python
def calculate_ml_weight(anomaly_score: float, confidence: float) -> float:
    """
    Map anomaly score (0-1) to mlWeight multiplier (0.5-3.0).
    
    - anomaly_score < 0.3: Normal activity → mlWeight 0.8-1.0 (slightly reduce score)
    - anomaly_score 0.3-0.5: Borderline → mlWeight 1.0 (neutral)
    - anomaly_score 0.5-0.7: Suspicious → mlWeight 1.2-1.5
    - anomaly_score 0.7-0.9: Anomalous → mlWeight 1.5-2.5
    - anomaly_score > 0.9: Highly anomalous → mlWeight 2.5-3.0
    
    Low confidence (<0.5) dampens the weight toward 1.0 (neutral).
    """
    if confidence < 0.3:
        return 1.0  # Too uncertain, stay neutral
    
    # Base weight from anomaly score
    if anomaly_score < 0.3:
        base_weight = 0.8 + (anomaly_score / 0.3) * 0.2  # 0.8 - 1.0
    elif anomaly_score < 0.5:
        base_weight = 1.0  # neutral zone
    elif anomaly_score < 0.7:
        base_weight = 1.0 + ((anomaly_score - 0.5) / 0.2) * 0.5  # 1.0 - 1.5
    elif anomaly_score < 0.9:
        base_weight = 1.5 + ((anomaly_score - 0.7) / 0.2) * 1.0  # 1.5 - 2.5
    else:
        base_weight = 2.5 + ((anomaly_score - 0.9) / 0.1) * 0.5  # 2.5 - 3.0
    
    # Dampen by confidence: blend toward 1.0 when confidence is low
    weight = 1.0 + (base_weight - 1.0) * confidence
    return max(0.5, min(3.0, weight))
```

---

## 4. REST API

### 4.1 Health Endpoint

```
GET /health
```

**Response**:
```json
{
  "status": "healthy",
  "modelLoaded": true,
  "modelVersion": "autoencoder_v1_tier2",
  "kafkaConnected": true,
  "uptime": 3600,
  "modelsAvailable": {
    "tier1": "autoencoder_v1_tier1",
    "tier2": "autoencoder_v1_tier2",
    "tier3": "autoencoder_v1_tier3"
  }
}
```

### 4.2 On-Demand Detection Endpoint

```
POST /api/v1/ml/detect
```

**Request** (same shape as `AggregatedAttackData`):
```json
{
  "customerId": "customer_a",
  "attackMac": "00:11:22:33:44:55",
  "attackCount": 50,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "burstIntensity": 0.7,
  "tier": 2
}
```

**Response**:
```json
{
  "mlScore": 0.85,
  "mlWeight": 2.5,
  "mlConfidence": 0.92,
  "anomalyType": "statistical_outlier",
  "modelVersion": "autoencoder_v1_tier2"
}
```

### 4.3 Model Status Endpoint

```
GET /api/v1/ml/models
```

**Response**:
```json
{
  "models": [
    {
      "name": "autoencoder_v1_tier1",
      "tier": 1,
      "status": "active",
      "trainedAt": "2026-03-27T00:00:00Z",
      "sampleCount": 52000,
      "threshold": 0.0120,
      "metrics": {
        "validationLoss": 0.0089,
        "anomalyRate": 0.05
      }
    }
  ]
}
```

---

## 5. Model Architecture

### 5.1 Phase 1: Tabular Autoencoder

The autoencoder learns the distribution of **typical malicious probing activity** (automated commodity scanners, worm propagation). Reconstruction error identifies statistical outliers — targeted attackers with low-and-slow lateral movement look DIFFERENT from automated port sweeps.

**Why this works for honeypots**: All honeypot traffic is malicious. But 90%+ is automated commodity scanning (Shodan-like, mass probes). The autoencoder learns this "typical malicious" distribution. High reconstruction error = unusual attack pattern worth investigating.

```
Input (12 features) → Encoder → Latent (6 dims) → Decoder → Output (12 features)

Encoder: 12 → 10 → 8 → 6 (ReLU, BatchNorm, Dropout=0.1)
Decoder: 6 → 8 → 10 → 12 (ReLU, BatchNorm)

Loss: MSE(input, output)
Anomaly Score: normalized reconstruction error (0-1)
```

**Training**:
- Data source: Historical `threat_alerts` from PostgreSQL, stratified by tier
- Train/validation split: 80/20 (time-based, NOT random)
- Epochs: 100 with early stopping (patience=10)
- Optimizer: Adam (lr=1e-3, weight_decay=1e-5)
- Batch size: 256
- Threshold: Rolling 24h window P95 reconstruction error

**ONNX Export**:
```python
torch.onnx.export(model, dummy_input, "autoencoder_v1_tier{tier}.onnx",
                  input_names=["features"], output_names=["reconstruction"],
                  dynamic_axes={"features": {0: "batch"}, "reconstruction": {0: "batch"}})
```

### 5.2 Phase 2: BiGRU Temporal Detector (Future)

Detects attack progressions over time (port scan → service enumeration → exploitation). Operates on sequences of aggregated windows for the same `customerId:attackMac`.

### 5.3 Phase 3: Ensemble (Future)

Combines autoencoder anomaly score with BiGRU temporal score. Weighted ensemble with learned combination weights.

---

## 6. Service Architecture

### 6.1 Directory Structure

```
services/ml-detection/
├── Dockerfile
├── pyproject.toml
├── requirements.txt
├── app/
│   ├── __init__.py
│   ├── main.py                  # FastAPI app + lifespan
│   ├── config.py                # Settings via pydantic-settings
│   ├── models/
│   │   ├── __init__.py
│   │   ├── autoencoder.py       # PyTorch model definition
│   │   └── schemas.py           # Pydantic request/response models
│   ├── features/
│   │   ├── __init__.py
│   │   └── extractor.py         # Feature engineering + tier stratification
│   ├── serving/
│   │   ├── __init__.py
│   │   ├── engine.py            # ONNX Runtime inference singleton
│   │   └── scorer.py            # Anomaly score → mlWeight mapping
│   ├── kafka/
│   │   ├── __init__.py
│   │   ├── consumer.py          # aiokafka consumer for threat-alerts
│   │   └── producer.py          # aiokafka producer for ml-threat-detections
│   └── training/
│       ├── __init__.py
│       └── trainer.py           # Offline batch training pipeline
├── models/                      # Saved model artifacts (.onnx files)
│   └── .gitkeep
└── tests/
    ├── __init__.py
    ├── conftest.py
    ├── test_features.py         # Feature extraction unit tests
    ├── test_serving.py          # Inference + fallback tests
    └── test_schemas.py          # Schema validation tests
```

### 6.2 Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Language | Python 3.11 | ML ecosystem (PyTorch, ONNX) |
| Web Framework | FastAPI | Async, fast, auto-docs |
| ML Training | PyTorch 2.x | User specified |
| ML Serving | ONNX Runtime 1.17+ | ~3x faster than PyTorch eager on CPU |
| Kafka Client | aiokafka | Async, integrates with FastAPI event loop |
| Config | pydantic-settings | Type-safe env var parsing |
| Testing | pytest + pytest-asyncio | Standard Python testing |
| Port | 8086 | Next available after threat-intelligence (8085) |

### 6.3 Configuration (Environment Variables)

| Variable | Description | Default |
|----------|-------------|---------|
| `ML_SERVICE_PORT` | Service port | `8086` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker | `kafka:29092` |
| `KAFKA_INPUT_TOPIC` | Input topic | `threat-alerts` |
| `KAFKA_OUTPUT_TOPIC` | Output topic | `ml-threat-detections` |
| `KAFKA_CONSUMER_GROUP` | Consumer group ID | `ml-detection-consumer` |
| `MODEL_DIR` | Directory for ONNX models | `/app/models` |
| `ML_CONFIDENCE_THRESHOLD` | Min confidence for non-neutral weight | `0.3` |
| `ML_DEFAULT_WEIGHT` | Fallback mlWeight when no model | `1.0` |
| `LOG_LEVEL` | Logging level | `INFO` |

---

## 7. Kafka Integration

### 7.1 Topics

| Topic | Partitions | Direction | Content |
|-------|-----------|-----------|---------|
| `threat-alerts` | 1 | Input (consume) | AggregatedAttackData from Flink |
| `ml-threat-detections` | 1 | Output (produce) | ML detection results |

### 7.2 Consumer Group

- Group ID: `ml-detection-consumer`
- Auto offset reset: `earliest`
- Deserialization: JSON → Python dict → Pydantic model validation

### 7.3 Failure Handling

- JSON parse error → log warning, skip message, continue
- Model not loaded → publish `mlWeight=1.0` (neutral fallback)
- Kafka disconnection → automatic reconnect with backoff
- ONNX inference error → log error, publish `mlWeight=1.0`

---

## 8. Database Schema

### 8.1 ml_predictions Table (Migration 26)

Stores ML inference results for audit, training feedback, and dashboard visualization.

```sql
CREATE TABLE ml_predictions (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         VARCHAR(50) NOT NULL,
    attack_mac          VARCHAR(17) NOT NULL,
    attack_ip           VARCHAR(45),
    tier                SMALLINT NOT NULL CHECK (tier IN (1, 2, 3)),
    window_start        TIMESTAMPTZ NOT NULL,
    window_end          TIMESTAMPTZ NOT NULL,
    
    -- ML results
    ml_score            DOUBLE PRECISION NOT NULL,   -- Anomaly score (0-1)
    ml_weight           DOUBLE PRECISION NOT NULL,   -- Advisory multiplier (0.5-3.0)
    ml_confidence       DOUBLE PRECISION NOT NULL,   -- Model confidence (0-1)
    anomaly_type        VARCHAR(50) NOT NULL,         -- 'normal', 'statistical_outlier', 'behavioral_anomaly'
    reconstruction_error DOUBLE PRECISION,            -- Raw autoencoder error
    threshold           DOUBLE PRECISION,             -- Threshold used for this tier
    
    -- Model metadata
    model_version       VARCHAR(100) NOT NULL,
    
    -- Pre-ML rule-based score (for feedback loop prevention)
    pre_ml_threat_score DOUBLE PRECISION,
    pre_ml_threat_level VARCHAR(20),
    
    -- Feature vector (for retraining)
    feature_vector      DOUBLE PRECISION[],
    
    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Indexes
    CONSTRAINT idx_ml_pred_unique UNIQUE (customer_id, attack_mac, tier, window_start)
);

-- Query by customer + time range
CREATE INDEX idx_ml_pred_customer_time ON ml_predictions (customer_id, created_at DESC);

-- Query anomalies
CREATE INDEX idx_ml_pred_anomaly ON ml_predictions (anomaly_type, ml_score DESC)
    WHERE anomaly_type != 'normal';

-- Query by tier for model retraining
CREATE INDEX idx_ml_pred_tier ON ml_predictions (tier, created_at DESC);
```

### 8.2 ml_customer_stats Table (per-customer normalization state)

```sql
CREATE TABLE ml_customer_stats (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(50) NOT NULL,
    tier            SMALLINT NOT NULL CHECK (tier IN (1, 2, 3)),
    feature_name    VARCHAR(50) NOT NULL,
    rolling_mean    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rolling_std     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sample_count    BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_customer_tier_feature UNIQUE (customer_id, tier, feature_name)
);

CREATE INDEX idx_ml_stats_customer ON ml_customer_stats (customer_id, tier);
```

---

## 9. Docker / Infrastructure

### 9.1 Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY app/ ./app/
COPY models/ ./models/

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
    CMD curl -fsS http://localhost:8086/health || exit 1

EXPOSE 8086

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8086"]
```

### 9.2 Docker Compose Entry

```yaml
ml-detection:
  build:
    context: ../services/ml-detection
    dockerfile: Dockerfile
  image: ml-detection:latest
  hostname: ml-detection
  container_name: ml-detection-service
  depends_on:
    - kafka
    - postgres
  ports: ["8086:8086"]
  environment:
    ML_SERVICE_PORT: 8086
    KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    KAFKA_INPUT_TOPIC: threat-alerts
    KAFKA_OUTPUT_TOPIC: ml-threat-detections
    KAFKA_CONSUMER_GROUP: ml-detection-consumer
    MODEL_DIR: /app/models
    ML_CONFIDENCE_THRESHOLD: 0.3
    ML_DEFAULT_WEIGHT: 1.0
    DATABASE_URL: postgresql://threat_user:threat_password@postgres:5432/threat_detection
    LOG_LEVEL: INFO
  networks:
    - threat-detection-network
  healthcheck:
    test: ["CMD", "curl", "-fsS", "http://localhost:8086/health"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 30s
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 2G
      reservations:
        cpus: '1.0'
        memory: 1G
  restart: unless-stopped
```

---

## 10. Phased Rollout

### Phase 1 (Current): Tabular Anomaly Detection
- FastAPI skeleton + health endpoint + Dockerfile
- Kafka consumer (`threat-alerts`) + producer (`ml-threat-detections`)
- Feature engineering with tier stratification and per-customer normalization
- PyTorch autoencoder + ONNX export
- ONNX Runtime FP16 inference engine with `mlWeight=1.0` fallback
- REST endpoint `POST /api/v1/ml/detect`
- Database tables for predictions + customer stats
- Unit tests

### Phase 2 (Future): Scoring Integration
- Add `mlWeight` to `ThreatScoreCalculator` in threat-assessment service
- Feature-flagged (disabled by default)
- Store pre-ML and post-ML scores separately
- Java REST client or Kafka consumer in threat-assessment

### Phase 3 (Future): BiGRU Temporal Model
- Sequence construction from historical windows per `customerId:attackMac`
- BiGRU with attention for temporal pattern detection
- Multi-model inference (autoencoder + BiGRU)

### Phase 4 (Future): Ensemble + Training Pipeline
- Weighted ensemble combining autoencoder + BiGRU scores
- Feature store (PostgreSQL-backed)
- Nightly batch retraining with PSI drift detection
- Champion/challenger model promotion

---

## 11. Risks and Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| `minute-aggregations` topic is dead | Critical | Use `threat-alerts` only |
| Kafka partitions=1 bottleneck | Critical | Monitor lag; increase partitions if needed |
| No "normal" traffic baseline | Critical | Train on "typical malicious" distribution; outliers = interesting attacks |
| Feature distribution differs by tier | High | Separate models per tier; never mix tier features |
| Scoring feedback loop | High | Train ONLY on pre-ML features; store both scores |
| INT8 quantization kills anomaly detection | High | Use FP16 minimum |
| Cold start (no model available) | Medium | `mlWeight=1.0` fallback; service is fully functional without model |
| Multi-tenant contamination | Medium | Per-customer normalization of volume-dependent features |
| Python service doesn't use Spring Cloud Config | Medium | Use env vars consistent with docker-compose pattern |

---

## 12. Migration Numbering

- `26-ml-detection-tables.sql` — `ml_predictions` + `ml_customer_stats` tables and indexes

---

## 13. References

- [Threat Intelligence Design](./threat_intelligence_design.md) — Analogous design pattern for service integration
- [V2 Event Schemas](./v2_event_schemas.md) — Upstream data format
- [Net Weighting Strategy](./net_weighting_strategy.md) — Similar weight factor integration pattern
- [ONNX Runtime Documentation](https://onnxruntime.ai/docs/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
