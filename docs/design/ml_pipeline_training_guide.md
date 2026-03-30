# ML Pipeline & Training Guide (机器学习流水线与训练指南)

## 1. Pipeline Overview (流水线概览)
Flink (threat-alerts topic) → ML Detection Service (Kafka consumer) → Feature extraction → ONNX inference → mlWeight score → Kafka (ml-threat-detections) → DB (ml_predictions table)

## 2. 3-Tier Model Architecture (三层模型架构)
| Tier | Window | Purpose | Model | Input Features |
|------|--------|---------|-------|---------------|
| Tier 1 | 30s | Ransomware detection | Autoencoder | attackCount, uniqueIps, uniquePorts, uniqueDevices, threatScore, timeWeight, ipWeight, portWeight |
| Tier 2 | 5min | Main threat detection | Autoencoder | Same features, 5-min aggregation |
| Tier 3 | 15min | APT detection | Autoencoder | Same features, 15-min aggregation |

- **BiGRU sequence model**: Activates after 4+ windows of data per attacker, captures temporal patterns.
- **Alpha blending**: `finalScore = alpha * autoencoder_score + (1 - alpha) * bigru_score`, alpha=0.6 (autoencoder-dominant).
- **mlWeight**: Advisory multiplier mapped from anomaly score → range [0.5, 3.0].
  - score < 0.3 → mlWeight ≈ 0.5 (likely benign, reduce score)
  - score > 0.8 → mlWeight ≈ 3.0 (highly anomalous, amplify score)

## 3. Training Process (训练流程)
1. **Training data**: Uses data from `attack_events` table in PostgreSQL.
2. **Feature extraction**: Window-based aggregation matching Flink output format.
3. **Normalization**: Global + per-customer normalization.
4. **Training command**:
```bash
# Enter the ml-detection pod
sudo kubectl exec -n threat-detection -it $(sudo kubectl get pod -n threat-detection -l app=ml-detection -o name) -- /bin/bash

# Inside the pod - train all tiers
python -m app.training.train_tier1
python -m app.training.train_tier2
python -m app.training.train_tier3

# Export to ONNX
python -m app.training.export_onnx
```
5. **Or train from host with DB access**:
```bash
# SSH into server, exec into ml-detection pod
ssh kylecui@10.174.1.229
sudo kubectl exec -n threat-detection -it <ml-detection-pod> -- python -m app.training.train_all
```

## 4. Model Files (模型文件)
- **Location inside container**: `/app/models/`
- **Files**: `tier1_autoencoder.onnx`, `tier2_autoencoder.onnx`, `tier3_autoencoder.onnx`, `bigru_model.onnx`
- **Normalization stats**: `tier1_stats.json`, `tier2_stats.json`, `tier3_stats.json`
- **Deployment**: Models are baked into Docker image during build — rebuild image after retraining.

## 5. Kafka Topics (Kafka 主题)
- **Input**: `threat-alerts` (Flink output, contains windowStart/windowEnd as float epoch seconds).
- **Output**: `ml-threat-detections` (ML predictions with anomaly score and mlWeight).
- **Consumer group**: `ml-detection-group-v2` (changed from v1 to force re-consumption).

## 6. Database Table: `ml_predictions` (数据库表：ml_predictions)
```sql
CREATE TABLE ml_predictions (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50),
    attack_mac VARCHAR(17),
    detection_tier VARCHAR(20),
    anomaly_score DECIMAL(10,4),
    ml_weight DECIMAL(10,4),
    model_version VARCHAR(50),
    features JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);
```

## 7. Key Gotchas (关键注意事项)
1. **Timestamp Parsing**: Flink sends timestamps as float epoch seconds, not ISO strings — ML consumer must parse floats.
2. **ONNX Precision**: ONNX models export as float32 — inference must NOT cast to float16.
3. **Offset Persistence**: If consumer crashes after Kafka commits offset but before DB write, messages are lost. Changed group name forces re-consumption from earliest.
4. **BiGRU Activation**: BiGRU requires 4+ windows before activating — initial predictions are autoencoder-only.
5. **Concept Drift**: Models need retraining when data distribution changes significantly.

## 8. Hot-Reload & Shadow Scoring (热重载与影子评分)
- **Hot-reload**: Models can be swapped at runtime by replacing files in `/app/models/` and sending SIGHUP.
- **Shadow scoring**: New models can run in parallel (shadow mode) without affecting production scores.

## 9. Key Implementation Files (关键实现文件)
- `services/ml-detection/app/kafka/consumer.py` — Kafka consumer
- `services/ml-detection/app/inference/engine.py` — ONNX inference engine
- `services/ml-detection/app/persistence/db_writer.py` — DB persistence
- `services/ml-detection/app/training/` — Training scripts
- `services/ml-detection/app/models/` — Trained ONNX models
