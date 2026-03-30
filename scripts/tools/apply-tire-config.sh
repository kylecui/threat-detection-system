#!/usr/bin/env bash
set -euo pipefail

# Sync system_config DB values → K8s tire-secret → restart TIRE pod
# Usage: ./apply-tire-config.sh [NAMESPACE]
#   NAMESPACE defaults to "threat-detection"

NS="${1:-threat-detection}"
PG_POD="postgres-0"
DB="threat_detection"
DB_USER="threat_user"
SECRET_NAME="tire-secret"

echo "=== Syncing TIRE config from PostgreSQL → K8s secret ==="

read_config() {
  local key="$1"
  kubectl exec -n "$NS" "$PG_POD" -- \
    psql -U "$DB_USER" -d "$DB" -tA \
    -c "SELECT config_value FROM system_config WHERE config_key='$key'" 2>/dev/null | tr -d '[:space:]'
}

ABUSEIPDB_API_KEY=$(read_config "ABUSEIPDB_API_KEY")
OTX_API_KEY=$(read_config "OTX_API_KEY")
GREYNOISE_API_KEY=$(read_config "GREYNOISE_API_KEY")
VT_API_KEY=$(read_config "VT_API_KEY")
SHODAN_API_KEY=$(read_config "SHODAN_API_KEY")
LLM_API_KEY=$(read_config "LLM_API_KEY")
LLM_MODEL=$(read_config "LLM_MODEL")
LLM_BASE_URL=$(read_config "LLM_BASE_URL")
ADMIN_PASSWORD=$(read_config "TIRE_ADMIN_PASSWORD")
TIRE_LOG_LEVEL=$(read_config "TIRE_LOG_LEVEL")

: "${LLM_MODEL:=gpt-4}"
: "${LLM_BASE_URL:=https://api.openai.com/v1}"
: "${ADMIN_PASSWORD:=tire-admin-2026}"
: "${TIRE_LOG_LEVEL:=INFO}"

echo "  ABUSEIPDB_API_KEY: ${ABUSEIPDB_API_KEY:+SET}"
echo "  OTX_API_KEY:       ${OTX_API_KEY:+SET}"
echo "  GREYNOISE_API_KEY: ${GREYNOISE_API_KEY:+SET}"
echo "  VT_API_KEY:        ${VT_API_KEY:+SET}"
echo "  SHODAN_API_KEY:    ${SHODAN_API_KEY:+SET}"
echo "  LLM_API_KEY:       ${LLM_API_KEY:+SET}"
echo "  LLM_MODEL:         $LLM_MODEL"
echo "  LLM_BASE_URL:      $LLM_BASE_URL"

kubectl create secret generic "$SECRET_NAME" \
  --namespace="$NS" \
  --from-literal="ABUSEIPDB_API_KEY=${ABUSEIPDB_API_KEY}" \
  --from-literal="OTX_API_KEY=${OTX_API_KEY}" \
  --from-literal="GREYNOISE_API_KEY=${GREYNOISE_API_KEY}" \
  --from-literal="VT_API_KEY=${VT_API_KEY}" \
  --from-literal="SHODAN_API_KEY=${SHODAN_API_KEY}" \
  --from-literal="LLM_API_KEY=${LLM_API_KEY}" \
  --from-literal="LLM_MODEL=${LLM_MODEL}" \
  --from-literal="LLM_BASE_URL=${LLM_BASE_URL}" \
  --from-literal="ADMIN_PASSWORD=${ADMIN_PASSWORD}" \
  --from-literal="TIRE_LOG_LEVEL=${TIRE_LOG_LEVEL}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "=== Secret updated. Restarting TIRE pod ==="
kubectl delete pod -n "$NS" -l app=tire --force --grace-period=0 2>/dev/null || true
kubectl rollout status deployment/tire -n "$NS" --timeout=120s 2>/dev/null || echo "Waiting for pod to come up..."

echo "=== Done. TIRE pod will restart with new config ==="
