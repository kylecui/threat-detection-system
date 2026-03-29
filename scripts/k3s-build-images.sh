#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JAVA_SERVICES=(
  data-ingestion
  stream-processing
  threat-assessment
  alert-management
  customer-management
  threat-intelligence
  api-gateway
  config-server
)

PYTHON_SERVICES=(
  ml-detection
)

STREAM_PROCESSING_TAG="1.0"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

BUILD_CMD=""
IMPORT_CMD=""

detect_runtime() {
  if command -v nerdctl &>/dev/null; then
    BUILD_CMD="nerdctl"
    IMPORT_CMD=""
    info "Using nerdctl (K3s containerd native)"
  elif command -v docker &>/dev/null; then
    BUILD_CMD="docker"
    if command -v k3s &>/dev/null; then
      IMPORT_CMD="k3s-import"
      info "Using docker build + k3s ctr import"
    else
      IMPORT_CMD=""
      info "Using docker (no K3s detected, images stay in docker)"
    fi
  else
    error "Neither nerdctl nor docker found. Install one of them."
    exit 1
  fi
}

build_image() {
  local service="$1"
  local context="$2"
  local dockerfile="$3"
  local tag="$4"
  local full_tag="threat-detection/${service}:${tag}"

  info "Building ${full_tag} ..."

  if [[ "$BUILD_CMD" == "nerdctl" ]]; then
    sudo nerdctl --namespace k8s.io build \
      --no-cache \
      -t "$full_tag" \
      -f "$dockerfile" \
      "$context"
  else
    docker build \
      --no-cache \
      -t "$full_tag" \
      -f "$dockerfile" \
      "$context"

    if [[ "$IMPORT_CMD" == "k3s-import" ]]; then
      info "  Importing ${full_tag} into K3s containerd ..."
      docker save "$full_tag" | sudo k3s ctr images import -
    fi
  fi

  info "  ✓ ${full_tag}"
}

main() {
  detect_runtime
  cd "$PROJECT_ROOT"

  info "=== Building Java services (context: project root) ==="
  for svc in "${JAVA_SERVICES[@]}"; do
    local tag="latest"
    if [[ "$svc" == "stream-processing" ]]; then
      tag="$STREAM_PROCESSING_TAG"
    fi
    build_image "$svc" "$PROJECT_ROOT" "services/${svc}/Dockerfile" "$tag"
  done

  info "=== Building Python services ==="
  for svc in "${PYTHON_SERVICES[@]}"; do
    build_image "$svc" "services/${svc}" "services/${svc}/Dockerfile" "latest"
  done

  echo ""
  info "=== All images built ==="

  if [[ "$BUILD_CMD" == "nerdctl" ]]; then
    info "Listing images in k8s.io namespace:"
    sudo nerdctl --namespace k8s.io images | grep "threat-detection/" || true
  elif [[ "$IMPORT_CMD" == "k3s-import" ]]; then
    info "Listing images in K3s containerd:"
    sudo k3s crictl images | grep "threat-detection/" || true
  else
    info "Listing images:"
    docker images | grep "threat-detection/" || true
  fi

  echo ""
  info "Next steps:"
  info "  kubectl delete -k k8s/base  (if previously applied)"
  info "  kubectl apply -k k8s/base"
  info "  kubectl get pods -n threat-detection -w"
}

main "$@"
