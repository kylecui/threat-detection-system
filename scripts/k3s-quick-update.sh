#!/usr/bin/env bash
# Incremental K3s service update — build, import, and restart specific services.
# Unlike k3s-deploy.sh (full nuke+redeploy), this does a safe rolling update.
#
# Usage:
#   sudo bash scripts/k3s-quick-update.sh <service> [service2 ...]
#   sudo bash scripts/k3s-quick-update.sh --changed     # auto-detect from git diff
#   sudo bash scripts/k3s-quick-update.sh --all          # rebuild + restart everything
#
# Examples:
#   sudo bash scripts/k3s-quick-update.sh threat-assessment frontend
#   sudo bash scripts/k3s-quick-update.sh --changed
#
# NOTE: Run with sudo so k3s ctr / docker work without issues.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
NAMESPACE="threat-detection"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
log()   { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }

SERVICE_DIR_MAP=(
  "data-ingestion:services/data-ingestion"
  "stream-processing:services/stream-processing"
  "threat-assessment:services/threat-assessment"
  "alert-management:services/alert-management"
  "customer-management:services/customer-management"
  "threat-intelligence:services/threat-intelligence"
  "api-gateway:services/api-gateway"
  "config-server:services/config-server"
  "ml-detection:services/ml-detection"
  "tire:services/tire"
  "frontend:frontend"
)

JAVA_SERVICES="data-ingestion stream-processing threat-assessment alert-management customer-management threat-intelligence api-gateway config-server"
PYTHON_SERVICES="ml-detection tire"
SPECIAL_SERVICES="frontend"

STREAM_PROCESSING_TAG="1.0"

get_source_dir() {
  local svc="$1"
  for mapping in "${SERVICE_DIR_MAP[@]}"; do
    local key="${mapping%%:*}"
    local val="${mapping#*:}"
    if [[ "$key" == "$svc" ]]; then
      echo "$val"
      return
    fi
  done
  echo ""
}

is_java_service() {
  [[ " $JAVA_SERVICES " == *" $1 "* ]]
}

is_python_service() {
  [[ " $PYTHON_SERVICES " == *" $1 "* ]]
}

is_special_service() {
  [[ " $SPECIAL_SERVICES " == *" $1 "* ]]
}

detect_changed_services() {
  local changed=()
  local base_ref="${1:-HEAD~1}"

  local diff_files
  diff_files=$(cd "$PROJECT_ROOT" && git diff --name-only "$base_ref" HEAD 2>/dev/null || true)

  if [[ -z "$diff_files" ]]; then
    diff_files=$(cd "$PROJECT_ROOT" && git diff --name-only HEAD 2>/dev/null || true)
  fi

  if [[ -z "$diff_files" ]]; then
    warn "No git changes detected. Nothing to update."
    exit 0
  fi

  for mapping in "${SERVICE_DIR_MAP[@]}"; do
    local svc="${mapping%%:*}"
    local dir="${mapping#*:}"
    if echo "$diff_files" | grep -q "^${dir}/"; then
      changed+=("$svc")
    fi
  done

  if [[ ${#changed[@]} -eq 0 ]]; then
    info "No service source changes detected in git diff."
    exit 0
  fi

  echo "${changed[@]}"
}

build_service() {
  local svc="$1"
  local src_dir
  src_dir=$(get_source_dir "$svc")

  if [[ -z "$src_dir" ]]; then
    error "Unknown service: $svc"
    return 1
  fi

  local tag="latest"
  local context=""
  local dockerfile=""

  if is_java_service "$svc"; then
    context="$PROJECT_ROOT"
    dockerfile="services/${svc}/Dockerfile"
    if [[ "$svc" == "stream-processing" ]]; then
      tag="$STREAM_PROCESSING_TAG"
    fi
  elif is_python_service "$svc"; then
    context="services/${svc}"
    dockerfile="services/${svc}/Dockerfile"
  elif is_special_service "$svc"; then
    case "$svc" in
      frontend)
        context="frontend"
        dockerfile="frontend/Dockerfile"
        ;;
    esac
  fi

  local full_tag="threat-detection/${svc}:${tag}"

  log "Building ${full_tag} ..."
  if ! docker build --no-cache -t "$full_tag" -f "$dockerfile" "$context"; then
    error "BUILD FAILED: ${full_tag}"
    return 1
  fi

  log "Importing ${full_tag} into K3s ..."
  local tmptar="/tmp/${svc}-image.tar"
  docker save "$full_tag" -o "$tmptar"
  if ! k3s ctr images import "$tmptar"; then
    error "IMPORT FAILED: ${full_tag}"
    rm -f "$tmptar"
    return 1
  fi
  rm -f "$tmptar"

  if [[ "$svc" == "stream-processing" ]]; then
    local extra_tag="threat-detection/${svc}:latest"
    docker tag "$full_tag" "$extra_tag"
    docker save "$extra_tag" -o "$tmptar"
    k3s ctr images import "$tmptar" || true
    rm -f "$tmptar"
    info "  Also imported ${extra_tag}"
  fi

  info "  ✓ ${full_tag} built + imported"
}

restart_service() {
  local svc="$1"

  if [[ "$svc" == "stream-processing" ]]; then
    log "Restarting Flink pods (jobmanager + taskmanager) ..."
    kubectl delete pod -n "$NAMESPACE" -l "app=stream-processing,component=jobmanager" --force --grace-period=0 2>/dev/null || true
    kubectl delete pod -n "$NAMESPACE" -l "app=stream-processing,component=taskmanager" --force --grace-period=0 2>/dev/null || true
  else
    log "Restarting ${svc} ..."
    kubectl rollout restart deployment/"$svc" -n "$NAMESPACE" 2>/dev/null || \
      kubectl delete pod -n "$NAMESPACE" -l "app=$svc" --force --grace-period=0 2>/dev/null || true
  fi
}

wait_for_ready() {
  local svc="$1"
  local timeout=180
  local selector="app=$svc"
  local elapsed=0

  if [[ "$svc" == "stream-processing" ]]; then
    selector="app=stream-processing,component=taskmanager"
  fi

  while [ $elapsed -lt $timeout ]; do
    local ready
    ready=$(kubectl get pods -n "$NAMESPACE" -l "$selector" --no-headers 2>/dev/null | awk '{print $2, $3}' || true)

    if [[ -n "$ready" ]]; then
      local all_ready=true
      while IFS= read -r line; do
        local rc st
        rc=$(echo "$line" | awk '{print $1}')
        st=$(echo "$line" | awk '{print $2}')
        local want got
        want=$(echo "$rc" | cut -d/ -f2)
        got=$(echo "$rc" | cut -d/ -f1)
        if [ "$st" != "Running" ] || [ "$got" != "$want" ]; then
          all_ready=false
          break
        fi
      done <<< "$ready"

      if $all_ready; then
        info "  ✓ ${svc} ready"
        return 0
      fi
    fi

    sleep 5; elapsed=$((elapsed + 5))
  done

  warn "  ⚠ ${svc} not ready after ${timeout}s"
  return 1
}

usage() {
  echo "Usage: sudo bash scripts/k3s-quick-update.sh <service> [service2 ...]"
  echo "       sudo bash scripts/k3s-quick-update.sh --changed"
  echo "       sudo bash scripts/k3s-quick-update.sh --all"
  echo ""
  echo "Available services:"
  for mapping in "${SERVICE_DIR_MAP[@]}"; do
    local svc="${mapping%%:*}"
    echo "  $svc"
  done
  exit 1
}

main() {
  cd "$PROJECT_ROOT"

  if [[ $# -eq 0 ]]; then
    usage
  fi

  local services=()

  case "$1" in
    --changed)
      local detected
      detected=$(detect_changed_services)
      read -ra services <<< "$detected"
      info "Auto-detected changed services: ${services[*]}"
      ;;
    --all)
      for mapping in "${SERVICE_DIR_MAP[@]}"; do
        services+=("${mapping%%:*}")
      done
      ;;
    --help|-h)
      usage
      ;;
    *)
      services=("$@")
      ;;
  esac

  local total=${#services[@]}
  log "=========================================="
  log "Quick Update: ${total} service(s)"
  log "Services: ${services[*]}"
  log "=========================================="

  local failed=()
  local succeeded=()

  for svc in "${services[@]}"; do
    echo ""
    log "── ${svc} ──────────────────────────────"
    if build_service "$svc"; then
      restart_service "$svc"
      succeeded+=("$svc")
    else
      failed+=("$svc")
    fi
  done

  echo ""
  log "Waiting for pods to come up ..."
  for svc in "${succeeded[@]}"; do
    wait_for_ready "$svc" || true
  done

  echo ""
  log "=========================================="
  log "Update Summary"
  log "=========================================="
  if [[ ${#succeeded[@]} -gt 0 ]]; then
    info "  ✓ Updated (${#succeeded[@]}): ${succeeded[*]}"
  fi
  if [[ ${#failed[@]} -gt 0 ]]; then
    error "  ✗ Failed (${#failed[@]}): ${failed[*]}"
  fi

  echo ""
  kubectl get pods -n "$NAMESPACE" --no-headers | grep -v Completed

  if [[ ${#failed[@]} -gt 0 ]]; then
    exit 1
  fi
}

main "$@"
