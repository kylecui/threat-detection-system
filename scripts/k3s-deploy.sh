#!/bin/bash
# =============================================================================
# Threat Detection System — K3s Full Deploy Script
# Nukes the namespace and redeploys everything from scratch in correct order.
# Usage: sudo bash scripts/k3s-deploy.sh
# =============================================================================
set -euo pipefail

NAMESPACE="threat-detection"
K8S_BASE="k8s/base"
TIMEOUT_INFRA=300   # 5 min for infrastructure pods
TIMEOUT_APP=300     # 5 min for application pods

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }

# ---------------------------------------------------------------------------
# wait_for_pod: waits until a pod reaches Running+Ready state
# Usage: wait_for_pod <label-selector> <timeout-seconds>
# ---------------------------------------------------------------------------
wait_for_pod() {
  local selector="$1"
  local timeout="$2"
  local elapsed=0

  while [ $elapsed -lt $timeout ]; do
    # Get status of all matching pods
    local ready
    ready=$(kubectl get pods -n "$NAMESPACE" -l "$selector" \
      --no-headers 2>/dev/null | awk '{print $2, $3}' || true)

    if [ -z "$ready" ]; then
      sleep 5; elapsed=$((elapsed + 5)); continue
    fi

    # Check if ALL matching pods are 1/1 Running (or N/N Running)
    local all_ready=true
    while IFS= read -r line; do
      local ready_count status
      ready_count=$(echo "$line" | awk '{print $1}')
      status=$(echo "$line" | awk '{print $2}')
      local want got
      want=$(echo "$ready_count" | cut -d/ -f2)
      got=$(echo "$ready_count" | cut -d/ -f1)
      if [ "$status" != "Running" ] || [ "$got" != "$want" ]; then
        all_ready=false
        break
      fi
    done <<< "$ready"

    if $all_ready; then
      return 0
    fi

    sleep 5; elapsed=$((elapsed + 5))
  done

  err "Timeout waiting for pods with selector '$selector' (${timeout}s)"
  kubectl get pods -n "$NAMESPACE" -l "$selector" --no-headers 2>/dev/null || true
  return 1
}

# ---------------------------------------------------------------------------
# wait_for_job: waits until a Job completes
# ---------------------------------------------------------------------------
wait_for_job() {
  local job_name="$1"
  local timeout="$2"
  local elapsed=0

  while [ $elapsed -lt $timeout ]; do
    local status
    status=$(kubectl get job "$job_name" -n "$NAMESPACE" \
      -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || true)
    if [ "$status" = "True" ]; then
      return 0
    fi
    # Check for failure
    local failed
    failed=$(kubectl get job "$job_name" -n "$NAMESPACE" \
      -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || true)
    if [ "$failed" = "True" ]; then
      err "Job $job_name failed!"
      kubectl logs -n "$NAMESPACE" "job/$job_name" --tail=20 2>/dev/null || true
      return 1
    fi
    sleep 5; elapsed=$((elapsed + 5))
  done
  err "Timeout waiting for job '$job_name' (${timeout}s)"
  return 1
}

# =============================================================================
# PHASE 0a: Pre-flight image check
# =============================================================================
log "=========================================="
log "PHASE 0a: Image pre-flight check"
log "=========================================="

REQUIRED_IMAGES=(
  "postgres:15-alpine"
  "redis:7-alpine"
  "busybox:1.35"
  "confluentinc/cp-zookeeper:7.4.0"
  "confluentinc/cp-kafka:7.4.0"
  "docker.elastic.co/logstash/logstash:8.11.0"
  "emqx/emqx:5.5.1"
  "threat-detection/data-ingestion:latest"
  "threat-detection/stream-processing:1.0"
  "threat-detection/threat-assessment:latest"
  "threat-detection/alert-management:latest"
  "threat-detection/customer-management:latest"
  "threat-detection/threat-intelligence:latest"
  "threat-detection/api-gateway:latest"
  "threat-detection/config-server:latest"
  "threat-detection/ml-detection:latest"
  "threat-detection/tire:latest"
  "threat-detection/frontend:latest"
)

MISSING_IMAGES=()
for img in "${REQUIRED_IMAGES[@]}"; do
  if ! crictl images 2>/dev/null | grep -q "$(echo "$img" | cut -d: -f1)"; then
    MISSING_IMAGES+=("$img")
  fi
done

if [ ${#MISSING_IMAGES[@]} -gt 0 ]; then
  warn "Missing ${#MISSING_IMAGES[@]} images in K3s containerd:"
  for img in "${MISSING_IMAGES[@]}"; do
    err "  - $img"
  done
  echo ""
  log "To fix:"
  log "  Option A: Import from archive:"
  log "    sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz"
  log "  Option B: Build app images + pull infra images:"
  log "    sudo bash scripts/k3s-build-images.sh"
  log "    sudo docker pull <missing-infra-image> && docker save ... | k3s ctr images import -"
  echo ""
  read -p "Continue anyway? (y/N) " -r REPLY
  if [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
    err "Aborted. Import missing images first."
    exit 1
  fi
fi
ok "Image pre-flight check complete"

# =============================================================================
# PHASE 0b: Nuke existing namespace
# =============================================================================
log "=========================================="
log "PHASE 0b: Clean slate"
log "=========================================="

if kubectl get namespace "$NAMESPACE" &>/dev/null; then
  log "Deleting namespace '$NAMESPACE'..."
  kubectl delete namespace "$NAMESPACE" --timeout=120s 2>/dev/null || {
    warn "Namespace delete timed out, force-cleaning..."
    # Force delete any stuck pods
    kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | \
      awk '{print $1}' | xargs -r kubectl delete pod -n "$NAMESPACE" --force --grace-period=0 2>/dev/null || true
    sleep 10
    kubectl delete namespace "$NAMESPACE" --force --grace-period=0 2>/dev/null || true
  }
  # Wait for namespace to actually disappear
  count=0
  while kubectl get namespace "$NAMESPACE" &>/dev/null && [ $count -lt 60 ]; do
    sleep 2; count=$((count + 1))
  done
fi
ok "Namespace cleaned"

# =============================================================================
# PHASE 1: Create namespace + secrets + configmaps
# =============================================================================
log "=========================================="
log "PHASE 1: Namespace, secrets, configmaps"
log "=========================================="

kubectl apply -f "$K8S_BASE/namespace.yaml"
ok "Namespace created"

# Apply postgres.yaml first — it contains the Secret, ConfigMap (init-scripts), and StatefulSet
kubectl apply -f "$K8S_BASE/postgres.yaml"
ok "PostgreSQL Secret + ConfigMap + StatefulSet applied"

# =============================================================================
# PHASE 2: Infrastructure — postgres, redis, zookeeper, emqx
# =============================================================================
log "=========================================="
log "PHASE 2: Infrastructure (PG, Redis, ZK, EMQX)"
log "=========================================="

kubectl apply -f "$K8S_BASE/redis.yaml"
kubectl apply -f "$K8S_BASE/zookeeper.yaml"
kubectl apply -f "$K8S_BASE/emqx.yaml"

log "Waiting for PostgreSQL..."
wait_for_pod "app=postgres" "$TIMEOUT_INFRA"
ok "PostgreSQL ready"

log "Waiting for Redis..."
wait_for_pod "app=redis" "$TIMEOUT_INFRA"
ok "Redis ready"

log "Waiting for Zookeeper..."
wait_for_pod "app=zookeeper" "$TIMEOUT_INFRA"
ok "Zookeeper ready"

log "Waiting for EMQX..."
wait_for_pod "app=emqx" "$TIMEOUT_INFRA"
ok "EMQX ready"

# =============================================================================
# PHASE 3: Kafka (needs Zookeeper)
# =============================================================================
log "=========================================="
log "PHASE 3: Kafka"
log "=========================================="

kubectl apply -f "$K8S_BASE/kafka.yaml"

log "Waiting for Kafka..."
wait_for_pod "app=kafka" "$TIMEOUT_INFRA"
ok "Kafka ready"

# =============================================================================
# PHASE 4: Init jobs (need Kafka + PG)
# =============================================================================
log "=========================================="
log "PHASE 4: Kafka topic init (CronJob) + PG schema"
log "=========================================="

# Delete old resources if they exist (Jobs/CronJobs are immutable)
kubectl delete job kafka-topic-init -n "$NAMESPACE" --ignore-not-found=true
kubectl delete cronjob kafka-topic-init -n "$NAMESPACE" --ignore-not-found=true
kubectl delete job kafka-topic-init-manual -n "$NAMESPACE" --ignore-not-found=true
kubectl delete job postgres-schema-apply -n "$NAMESPACE" --ignore-not-found=true

kubectl apply -f "$K8S_BASE/kafka-topic-init.yaml"
kubectl apply -f "$K8S_BASE/postgres-schema-apply.yaml"

log "Triggering immediate Kafka topic init..."
kubectl create job --from=cronjob/kafka-topic-init kafka-topic-init-manual -n "$NAMESPACE"
wait_for_job "kafka-topic-init-manual" 120
ok "Kafka topics created"

log "Waiting for PG schema apply..."
wait_for_job "postgres-schema-apply" 120
ok "PG schema applied"

# =============================================================================
# PHASE 5: Core services (config-server, logstash)
# =============================================================================
log "=========================================="
log "PHASE 5: Core services"
log "=========================================="

kubectl apply -f "$K8S_BASE/config-server.yaml"
kubectl apply -f "$K8S_BASE/logstash.yaml"

log "Waiting for Config Server..."
wait_for_pod "app=config-server" "$TIMEOUT_APP"
ok "Config Server ready"

log "Waiting for Logstash..."
wait_for_pod "app=logstash" "$TIMEOUT_APP"
ok "Logstash ready"

# =============================================================================
# PHASE 6: Application services (all in parallel — they have initContainers)
# =============================================================================
log "=========================================="
log "PHASE 6: Application services"
log "=========================================="

kubectl apply -f "$K8S_BASE/data-ingestion.yaml"
kubectl apply -f "$K8S_BASE/customer-management.yaml"
kubectl apply -f "$K8S_BASE/threat-assessment.yaml"
kubectl apply -f "$K8S_BASE/alert-management.yaml"
kubectl apply -f "$K8S_BASE/threat-intelligence.yaml"
kubectl apply -f "$K8S_BASE/ml-detection.yaml"
kubectl apply -f "$K8S_BASE/tire-secret.yaml"
kubectl apply -f "$K8S_BASE/tire.yaml"
kubectl apply -f "$K8S_BASE/frontend.yaml"
kubectl apply -f "$K8S_BASE/api-gateway.yaml"

log "Waiting for application services..."
for svc in data-ingestion customer-management threat-assessment alert-management threat-intelligence ml-detection tire frontend api-gateway; do
  wait_for_pod "app=$svc" "$TIMEOUT_APP" && ok "$svc ready" || warn "$svc NOT ready (will check later)"
done

# =============================================================================
# PHASE 7: Flink (needs Kafka topics to exist)
# =============================================================================
log "=========================================="
log "PHASE 7: Flink (JobManager + TaskManager)"
log "=========================================="

kubectl apply -f "$K8S_BASE/stream-processing.yaml"

log "Waiting for Flink JobManager..."
wait_for_pod "app=stream-processing,component=jobmanager" 360
ok "Flink JobManager ready"

log "Waiting for Flink TaskManager..."
wait_for_pod "app=stream-processing,component=taskmanager" 360
ok "Flink TaskManager ready"

# =============================================================================
# PHASE 8: Final status
# =============================================================================
log "=========================================="
log "PHASE 8: Final status"
log "=========================================="

echo ""
kubectl get pods -n "$NAMESPACE" -o wide
echo ""

# Count ready pods
TOTAL=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep -v Completed | wc -l)
READY=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep -v Completed | grep "1/1.*Running" | wc -l)
COMPLETED=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep Completed | wc -l)

echo ""
log "=========================================="
if [ "$READY" -eq "$TOTAL" ]; then
  ok "ALL $READY/$TOTAL pods Running + $COMPLETED jobs Completed"
  ok "Deployment SUCCESSFUL!"
else
  FAILING=$((TOTAL - READY))
  warn "$READY/$TOTAL pods Running, $FAILING not ready, $COMPLETED jobs Completed"
  echo ""
  warn "Pods not ready:"
  kubectl get pods -n "$NAMESPACE" --no-headers | grep -v "1/1.*Running" | grep -v Completed
fi
log "=========================================="
