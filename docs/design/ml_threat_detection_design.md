# ML Threat Detection — Design Document

**Version**: 2.0  
**Date**: 2026-03-28  
**Status**: Phase 3 — BiGRU Temporal Model + Ensemble Scoring  
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
                          ┌──────────────────────────────────────────────────────────┐
                          │  ML Detection Service (port 8086)                         │
                          │                                                           │
 Kafka (threat-alerts) ──▶│  ┌──────────────┐   ┌────────────────────┐              │
                          │  │ Kafka Consumer │──▶│ Feature Extractor  │              │
                          │  │ (aiokafka)    │   │ (tier-stratified)  │              │
                          │  └──────────────┘   └─────────┬──────────┘              │
                          │                                │                         │
                          │              ┌─────────────────┼──────────────────┐      │
                          │              ▼                  ▼                  │      │
                          │  ┌─────────────────────┐  ┌──────────────────┐   │      │
                          │  │ ONNX Autoencoder     │  │ Sequence Buffer  │   │      │
                          │  │ (FP16, per-tier)     │  │ (per attacker,   │   │      │
                          │  │ → recon error        │  │  LRU+TTL, 10K)  │   │      │
                          │  └─────────┬───────────┘  └────────┬─────────┘   │      │
                          │            │ anomaly_score          │ sequence     │      │
                          │            │                        ▼             │      │
                          │            │              ┌──────────────────┐   │      │
                          │            │              │ ONNX BiGRU       │   │      │
                          │            │              │ (FP16, attention) │   │      │
                          │            │              │ → temporal_score  │   │      │
                          │            │              └────────┬─────────┘   │      │
                          │            │                       │             │      │
                          │            └───────────┬───────────┘             │      │
                          │                        ▼                         │      │
                          │              ┌──────────────────────┐           │      │
                          │              │ Ensemble Scorer       │           │      │
                          │              │ ae^0.6 × bigru^0.4   │           │      │
                          │              │ (cold-start: ae only) │           │      │
                          │              └──────────┬───────────┘           │      │
                          │                         │                       │      │
                          │  ┌──────────────┐  ┌────▼──────────────────┐   │      │
                          │  │ Kafka Producer │◀─│ Score Calculator      │   │      │
                          │  │ (aiokafka)    │  │ (mlWeight 0.5-3.0)   │   │      │
                          │  └──────┬───────┘  └───────────────────────┘   │      │
                          │         │                                       │      │
                          │  ┌──────▼──────────────────────────────────┐   │      │
                          │  │ REST API: POST /api/v1/ml/detect         │   │      │
                          │  │           GET  /health                    │   │      │
                          │  │           GET  /api/v1/ml/buffer/stats    │   │      │
                          │  └─────────────────────────────────────────┘   │      │
                          └──────────────────────────────────────────────────────────┘
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
4. ONNX Runtime runs **autoencoder** inference → reconstruction error → anomaly score
5. **(Phase 3)** Feature vector is appended to the per-attacker **Sequence Buffer** (keyed by `customerId:attackMac:tier`)
6. **(Phase 3)** If sequence length ≥ 4 and BiGRU model is loaded, ONNX Runtime runs **BiGRU** inference → temporal score (predicted next-window anomaly)
7. **(Phase 3)** **Ensemble scorer** combines autoencoder anomaly score and BiGRU temporal score via weighted geometric mean: `combined = ae^0.6 × bigru^0.4`. Cold start (seq < 4): autoencoder score only.
8. Score calculator maps final anomaly score → `mlWeight` (0.5–3.0) + `mlConfidence` (0–1.0) + `anomalyType`
9. Result published to `ml-threat-detections` topic (enriched with `sequenceLength`, `temporalScore`, `ensembleMethod`, `ensembleAlpha`)
10. (Phase 2) Threat-assessment service consumes `ml-threat-detections` and applies `mlWeight` as advisory multiplier

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
  "sequenceLength": 12,
  "temporalScore": 0.78,
  "ensembleMethod": "ensemble",
  "ensembleAlpha": 0.6,
  "timestamp": "2026-03-27T10:05:01Z"
}
```

**New fields (Phase 3)**:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `sequenceLength` | int | `0` | Number of windows in the attacker's sequence buffer |
| `temporalScore` | float | `0.0` | BiGRU predicted next-window anomaly score (0–1) |
| `ensembleMethod` | string | `"autoencoder_only"` | `"autoencoder_only"` (cold start) or `"ensemble"` (BiGRU active) |
| `ensembleAlpha` | float | `0.6` | Autoencoder weight in the ensemble formula |

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

### 4.4 Sequence Buffer Stats Endpoint (Phase 3)

```
GET /api/v1/ml/buffer/stats
```

**Response**:
```json
{
  "enabled": true,
  "totalEntries": 1234,
  "maxEntries": 10000,
  "utilizationPercent": 12.34,
  "tierBreakdown": {
    "tier1": 456,
    "tier2": 567,
    "tier3": 211
  }
}
```

Returns `{"enabled": false}` when BiGRU feature flag is off.

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

### 5.2 Phase 2: BiGRU Temporal Detector (Complete ✅)

Detects attack progressions over time (port scan → service enumeration → exploitation). Operates on sequences of aggregated windows for the same `customerId:attackMac:tier` key.

**Why BiGRU for honeypots**: While the autoencoder detects per-window anomalies, many real attacks exhibit temporal patterns — gradual escalation, periodic reconnaissance, slow-and-low lateral movement. The BiGRU sees the sequence of anomaly scores over time and predicts what comes next. A rising trend (predicted high next-window score) amplifies the alert; a falling trend dampens it.

**Training Target**: Self-supervised next-window anomaly score regression. The BiGRU predicts the autoencoder's reconstruction error on the next time window. Labels are generated automatically from the autoencoder's own output — no manual labeling required.

#### Architecture

```
Input (seq of 12-dim features) → Input Projection → BiGRU → Additive Attention → Output Head → Prediction

Input Projection: Linear(12, 64) + LayerNorm + ReLU + Dropout(0.3)
BiGRU: GRU(64, 32, bidirectional=True, num_layers=2, dropout=0.3) → output 64-dim
Additive Attention: energy = Linear(64, 64), v = Linear(64, 1)
  - Mask: scores + (1.0 - mask) * (-1e4)  [FP16-safe, NOT float('-inf')]
  - Softmax → weighted sum → context vector [B, 64]
Output Head: Linear(64, 32) + ReLU + Dropout(0.3) + Linear(32, 1) + Sigmoid
  - nan_to_num guard on output (FP16 safety)
```

#### Sequence Buffer

Per-attacker rolling window buffer, keyed by `(customerId, attackMac, tier)`:

| Parameter | Value | Description |
|-----------|-------|-------------|
| Max entries | 10,000 | Global cap across all attacker keys (LRU eviction) |
| Max seq len (Tier 1) | 32 | 30s windows × 32 = ~16 min history |
| Max seq len (Tier 2) | 32 | 5min windows × 32 = ~2.5 hr history |
| Max seq len (Tier 3) | 48 | 15min windows × 48 = ~12 hr history |
| TTL (Tier 1) | 1,800s (30 min) | Short-lived burst detection |
| TTL (Tier 2) | 10,800s (3 hr) | Medium-term campaign tracking |
| TTL (Tier 3) | 86,400s (24 hr) | Long-term APT tracking |

**Eviction**: LRU ordering via `OrderedDict`. TTL-based expiry checked every 60 seconds. State is NOT persisted across restarts — advisory system auto-recovers as new windows flow in.

#### ONNX Export

```python
torch.onnx.export(model, (dummy_features, dummy_mask),
    f"bigru_v1_tier{tier}.onnx",
    input_names=["features_seq", "mask"],
    output_names=["prediction", "attention_weights"],
    dynamic_axes={
        "features_seq": {0: "batch", 1: "seq_len"},
        "mask": {0: "batch", 1: "seq_len"},
        "prediction": {0: "batch"},
        "attention_weights": {0: "batch", 1: "seq_len"}
    },
    opset_version=17)
```

**Critical ONNX constraints**:
- NO `pack_padded_sequence` — does not export to ONNX. Use mask-based attention only.
- Attention mask fill uses `-1e4` not `float('-inf')` for FP16 safety.
- FP16 input: `np.float16` for both features and mask tensors.

#### Training Pipeline (`bigru_trainer.py`)

```bash
python -m app.training.bigru_trainer \
    --tier 2 \
    --npz training_data_tier2.npz \
    --epochs 100 \
    --hidden-size 64 \
    --num-layers 2 \
    --dropout 0.3 \
    --model-dir models/
```

- **Data**: Sequences built from per-attacker windows; labels = autoencoder anomaly scores of the next window
- **Split**: `GroupShuffleSplit(groups=attacker_mac)` — prevents data leakage across train/val
- **Optimizer**: Adam (lr=1e-3, weight_decay=1e-5)
- **Early stopping**: Patience=10 on validation MSE
- **Gradient clipping**: max_norm=1.0
- **Batch size**: 64

### 5.3 Phase 3: Ensemble Scoring (Complete ✅)

Combines autoencoder per-window anomaly score with BiGRU temporal prediction into a single score.

#### Formula: Weighted Geometric Mean

```python
def ensemble_anomaly_score(ae_score, bigru_pred, seq_len, min_seq_len=4, alpha=0.6):
    """
    Combine autoencoder anomaly and BiGRU temporal scores.
    
    alpha = 0.6 → autoencoder-dominant (trusted, immediate signal)
    (1 - alpha) = 0.4 → BiGRU contribution (temporal context)
    """
    # Cold start: BiGRU not ready
    if bigru_pred is None or seq_len < min_seq_len:
        return ae_score, "autoencoder_only"
    
    # Clamp BiGRU prediction to [0.01, 1.0] for log safety
    bigru_clamped = max(0.01, min(1.0, bigru_pred))
    
    # Weighted geometric mean
    combined = (ae_score ** alpha) * (bigru_clamped ** (1.0 - alpha))
    
    return max(0.0, min(1.0, combined)), "ensemble"
```

#### Design Rationale

- **Geometric mean** (not arithmetic): Penalizes disagreement between models. If one scores low and the other high, the result is pulled toward the lower value — conservative by design.
- **alpha=0.6**: Autoencoder is the trusted baseline (per-window, immediate signal). BiGRU adds temporal context but should not override.
- **Cold start**: Until 4+ windows accumulate, BiGRU has insufficient context. Return autoencoder score unchanged.
- **Feeds into existing `score_to_weight()`**: The ensemble score replaces the raw autoencoder score in the existing pipeline. The `score_to_weight()` function is NOT modified.

#### Feature Flag

BiGRU is disabled by default (`BIGRU_ENABLED=false`). When disabled:
- No sequence buffer is created
- No BiGRU models are loaded
- Consumer follows the original autoencoder-only path
- Output fields default: `sequenceLength=0, temporalScore=0.0, ensembleMethod="autoencoder_only"`

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
│   ├── main.py                  # FastAPI app + lifespan (+ sequence buffer wiring)
│   ├── config.py                # Settings via pydantic-settings (+ BiGRU config)
│   ├── models/
│   │   ├── __init__.py
│   │   ├── autoencoder.py       # PyTorch autoencoder model definition
│   │   ├── bigru.py             # PyTorch BiGRU + AdditiveAttention model (Phase 3)
│   │   └── schemas.py           # Pydantic request/response models (+ temporal fields)
│   ├── features/
│   │   ├── __init__.py
│   │   ├── extractor.py         # Feature engineering + tier stratification
│   │   └── sequence_builder.py  # Per-attacker sequence buffer (LRU+TTL) (Phase 3)
│   ├── serving/
│   │   ├── __init__.py
│   │   ├── engine.py            # ONNX Runtime inference (autoencoder + BiGRU)
│   │   ├── scorer.py            # Anomaly score → mlWeight mapping
│   │   └── ensemble.py          # Ensemble scoring (ae + bigru) (Phase 3)
│   ├── kafka/
│   │   ├── __init__.py
│   │   ├── consumer.py          # aiokafka consumer (+ BiGRU integration path)
│   │   └── producer.py          # aiokafka producer for ml-threat-detections
│   └── training/
│       ├── __init__.py
│       ├── trainer.py           # Autoencoder offline batch training pipeline
│       └── bigru_trainer.py     # BiGRU training pipeline (Phase 3)
├── models/                      # Saved model artifacts (.onnx files)
│   └── .gitkeep
└── tests/
    ├── __init__.py
    ├── conftest.py
    ├── test_features.py         # Feature extraction unit tests
    ├── test_serving.py          # Inference + fallback tests
    ├── test_schemas.py          # Schema validation tests
    ├── test_autoencoder_regression.py  # Autoencoder regression tests (Phase 3)
    ├── test_bigru.py            # BiGRU model unit tests (Phase 3)
    ├── test_sequence_builder.py # Sequence buffer unit tests (Phase 3)
    └── test_ensemble.py         # Ensemble scoring unit tests (Phase 3)
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
| `BIGRU_ENABLED` | Enable BiGRU temporal model (feature flag) | `false` |
| `BIGRU_HIDDEN_SIZE` | BiGRU hidden dimension | `64` |
| `BIGRU_NUM_LAYERS` | Number of BiGRU layers | `2` |
| `BIGRU_DROPOUT` | BiGRU dropout rate | `0.3` |
| `BIGRU_MAX_SEQ_LEN_TIER1` | Max sequence length for Tier 1 | `32` |
| `BIGRU_MAX_SEQ_LEN_TIER2` | Max sequence length for Tier 2 | `32` |
| `BIGRU_MAX_SEQ_LEN_TIER3` | Max sequence length for Tier 3 | `48` |
| `BIGRU_MIN_SEQ_LEN` | Min windows before BiGRU activates | `4` |
| `BIGRU_ENSEMBLE_ALPHA` | Autoencoder weight in ensemble (0-1) | `0.6` |
| `BIGRU_BUFFER_MAX_ENTRIES` | Max total sequence buffer entries | `10000` |
| `BIGRU_BUFFER_TTL_TIER1` | Buffer TTL for Tier 1 (seconds) | `1800` |
| `BIGRU_BUFFER_TTL_TIER2` | Buffer TTL for Tier 2 (seconds) | `10800` |
| `BIGRU_BUFFER_TTL_TIER3` | Buffer TTL for Tier 3 (seconds) | `86400` |

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

### Phase 1 (Complete ✅): Tabular Anomaly Detection
- FastAPI skeleton + health endpoint + Dockerfile
- Kafka consumer (`threat-alerts`) + producer (`ml-threat-detections`)
- Feature engineering with tier stratification and per-customer normalization
- PyTorch autoencoder + ONNX export
- ONNX Runtime FP16 inference engine with `mlWeight=1.0` fallback
- REST endpoint `POST /api/v1/ml/detect`
- Database tables for predictions + customer stats
- Unit tests

### Phase 2 (Complete ✅): Scoring Integration
- `mlWeight` advisory multiplier integrated into `ThreatScoreCalculator` (applied after rawScore, before log-normalization)
- Feature-flagged via `ml.weight.enabled` (default false in properties, true in Docker)
- `MlWeightService` Kafka consumer on `ml-threat-detections` with ConcurrentHashMap cache (5000 max, TTL-based eviction)
- `pre_ml_score` and `ml_weight` columns added to `threat_assessments` table (migration 27)
- `mlWeight=1.0` fallback when no cache entry, disabled, or confidence below threshold
- Prometheus counters for cache hits/misses
- 8 unit tests passing

### Phase 3 (Complete ✅): BiGRU Temporal Model + Ensemble Scoring
- BiGRU (Bidirectional GRU) with additive attention for temporal attack progression detection
- Per-attacker sequence buffer (keyed by `customerId:attackMac:tier`) with LRU + TTL eviction
- Self-supervised training: predict next-window autoencoder anomaly score (no manual labels needed)
- Ensemble scoring: weighted geometric mean `ae^0.6 × bigru^0.4` with cold-start fallback
- Feature-flagged via `BIGRU_ENABLED` (default false) — zero impact when disabled
- ONNX export with mask-based attention (no `pack_padded_sequence`), FP16-safe `-1e4` fill
- BiGRU training pipeline with `GroupShuffleSplit` to prevent data leakage
- REST endpoint `GET /api/v1/ml/buffer/stats` for sequence buffer monitoring
- 51 unit tests (7 BiGRU + 13 sequence builder + 14 ensemble + 4 autoencoder regression + existing)

### Phase 4 (Future): Advanced Ensemble + Training Automation
- Learned ensemble weights (train alpha from data instead of fixed 0.6)
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
| BiGRU sequence buffer memory (10K entries) | High | LRU eviction + per-tier TTL; max ~10MB memory footprint |
| BiGRU state loss on restart | Medium | Acceptable — advisory system, buffers refill from live Kafka traffic within TTL windows |
| FP16 NaN in attention masks | Medium | Use `-1e4` fill instead of `float('-inf')`; `nan_to_num` guard on output |
| BiGRU cold start (seq < 4) | Medium | Graceful degradation — return autoencoder score only until buffer fills |
| `pack_padded_sequence` ONNX incompatibility | Medium | Mask-based attention only; verified in ONNX export tests |
| Cold start (no model available) | Medium | `mlWeight=1.0` fallback; service is fully functional without model |
| Multi-tenant contamination | Medium | Per-customer normalization of volume-dependent features |
| Python service doesn't use Spring Cloud Config | Medium | Use env vars consistent with docker-compose pattern |
| Data leakage in BiGRU training | Medium | `GroupShuffleSplit(groups=attacker_mac)` prevents same attacker in train+val |

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
