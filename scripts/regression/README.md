# Regression Replay Harness

Offline replay utility for threat-detection pipeline regression checks.

## Files

- `replay_harness.py` — replay + validation script
- `fixtures/v1_sample_events.json` — V1 syslog KV events
- `fixtures/v2_sample_events.json` — V2 MQTT JSON events

## Usage

```bash
# Replay V1 sample data
python3 scripts/regression/replay_harness.py --dataset v1 --target http://localhost:8080

# Replay V2 sample data
python3 scripts/regression/replay_harness.py --dataset v2 --target http://localhost:8080

# Replay both datasets
python3 scripts/regression/replay_harness.py --dataset all --target http://localhost:8080

# Dry run (fixture validation only)
python3 scripts/regression/replay_harness.py --dataset all --dry-run

# Custom wait window for Flink processing
python3 scripts/regression/replay_harness.py --dataset all --target http://localhost:8080 --wait 300
```

## What it does

1. Loads fixtures from `scripts/regression/fixtures/`
2. Replays events to data-ingestion:
   - V1 → `POST /api/v1/logs/ingest` (`text/plain`)
   - V2 → `POST /api/v1/logs/batch` (`application/json`)
3. Waits for stream processing (`--wait`, default 240s)
4. Validates outputs:
   - Kafka `threat-alerts` topic (best-effort)
   - PostgreSQL `threat_assessments` (required for PASS)

## Environment Variables

- `KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`)
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `threat_detection`)
- `DB_USER` (default: `threat_user`)
- `DB_PASSWORD` (default: `threat_password`)

Notes:
- Harness generates a unique `customerId` per run (`regression-test-...`).
- Exit code: `0` on pass, `1` on fail.
- Kafka/DB connection failures are reported as warnings/skips, not crashes.
