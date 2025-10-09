#!/usr/bin/env bash
set -euo pipefail

# Simple integration smoke test for the stream-processing pipeline.
# Prerequisites:
#   - docker compose stack is up (from the docker/ directory)
#   - kafka, topic-init, data-ingestion, and stream-processing services are healthy

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/docker"

cd "$COMPOSE_DIR"

echo "[*] Verifying docker compose stack..."
docker compose ps --services --filter "status=running" > /tmp/tds-running-services.txt

required_services=("kafka" "stream-processing" "data-ingestion-service" "zookeeper")
for svc in "${required_services[@]}"; do
  if ! grep -q "^${svc}$" /tmp/tds-running-services.txt; then
    echo "[!] Service '${svc}' is not running. Please start the stack with 'docker compose up -d' before running this script." >&2
    exit 1
  fi
done

rm -f /tmp/tds-running-services.txt

BOOTSTRAP="kafka:9092"
ATTACK_TOPIC="${INPUT_TOPIC:-attack-events}"
STATUS_TOPIC="${STATUS_TOPIC:-status-events}"
AGG_TOPIC="${AGGREGATION_TOPIC:-minute-aggregations}"
ALERT_TOPIC="${OUTPUT_TOPIC:-threat-alerts}"

produce() {
  local topic=$1
  local payload=$2
  docker compose exec -T kafka bash -lc "printf '%s\n' '${payload}' | kafka-console-producer --bootstrap-server ${BOOTSTRAP} --topic ${topic}" >/dev/null
}

consume() {
  local topic=$1
  docker compose exec -T kafka kafka-console-consumer \
    --bootstrap-server "${BOOTSTRAP}" \
    --topic "${topic}" \
    --from-beginning \
    --timeout-ms 5000 \
    --max-messages 10
}

echo "[*] Producing sample attack events to '${ATTACK_TOPIC}'..."
current_epoch="$(date +%s)"
produce "${ATTACK_TOPIC}" "{\"devSerial\":\"dev-001\",\"attackIp\":\"10.0.0.11\",\"responseIp\":\"192.168.1.10\",\"responsePort\":22,\"logTime\":${current_epoch},\"subType\":1}"
produce "${ATTACK_TOPIC}" "{\"devSerial\":\"dev-001\",\"attackIp\":\"10.0.0.12\",\"responseIp\":\"192.168.1.10\",\"responsePort\":22,\"logTime\":${current_epoch},\"subType\":1}"
produce "${ATTACK_TOPIC}" "{\"devSerial\":\"dev-001\",\"attackIp\":\"10.0.0.13\",\"responseIp\":\"192.168.1.10\",\"responsePort\":22,\"logTime\":${current_epoch},\"subType\":1}"

echo "[*] Producing a status heartbeat to '${STATUS_TOPIC}'..."
produce "${STATUS_TOPIC}" "{\"devSerial\":\"dev-001\",\"devStartTime\":${current_epoch},\"devEndTime\":${current_epoch},\"sentryCount\":5,\"realHostCount\":200}"

echo "[*] Waiting briefly for Flink windows to close..."
sleep 15

echo "[*] Consuming minute aggregation output from '${AGG_TOPIC}'..."
consume "${AGG_TOPIC}"

echo "[*] Consuming threat alert output from '${ALERT_TOPIC}'..."
consume "${ALERT_TOPIC}"

echo "[✓] Test run complete. Review the output above to confirm expected aggregations and threat scores."
