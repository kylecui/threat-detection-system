#!/usr/bin/env bash
# Build all threat-detection service images and import into K3s containerd.
# Usage:
#   sudo bash scripts/k3s-build-images.sh            # build all
#   sudo bash scripts/k3s-build-images.sh data-ingestion ml-detection  # build specific
#
# NOTE: Run with sudo so k3s ctr / nerdctl / crictl work without pipe issues.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ALL_JAVA_SERVICES=(
  data-ingestion
  stream-processing
  threat-assessment
  alert-management
  customer-management
  threat-intelligence
  api-gateway
  config-server
)

ALL_PYTHON_SERVICES=(
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

FAILED_SERVICES=()
SUCCESS_SERVICES=()

# ── runtime detection ──────────────────────────────────────────────
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

# ── build + import one image ──────────────────────────────────────
build_image() {
  local service="$1"
  local context="$2"
  local dockerfile="$3"
  local tag="$4"
  local full_tag="threat-detection/${service}:${tag}"

  info "Building ${full_tag} ..."

  if [[ "$BUILD_CMD" == "nerdctl" ]]; then
    if ! nerdctl --namespace k8s.io build \
        --no-cache \
        -t "$full_tag" \
        -f "$dockerfile" \
        "$context"; then
      error "  ✗ BUILD FAILED: ${full_tag}"
      FAILED_SERVICES+=("$service")
      return 1
    fi
  else
    if ! docker build \
        --no-cache \
        -t "$full_tag" \
        -f "$dockerfile" \
        "$context"; then
      error "  ✗ BUILD FAILED: ${full_tag}"
      FAILED_SERVICES+=("$service")
      return 1
    fi

    if [[ "$IMPORT_CMD" == "k3s-import" ]]; then
      info "  Importing ${full_tag} into K3s containerd ..."
      # Save to temp file first to avoid pipe + sudo issues
      local tmptar="/tmp/${service}-image.tar"
      docker save "$full_tag" -o "$tmptar"
      if ! k3s ctr images import "$tmptar"; then
        error "  ✗ IMPORT FAILED: ${full_tag}"
        rm -f "$tmptar"
        FAILED_SERVICES+=("$service")
        return 1
      fi
      rm -f "$tmptar"
    fi
  fi

  info "  ✓ ${full_tag}"
  SUCCESS_SERVICES+=("$service")
}

# ── determine which services to build ─────────────────────────────
resolve_services() {
  local requested=("$@")

  # If no args, build everything
  if [[ ${#requested[@]} -eq 0 ]]; then
    JAVA_TO_BUILD=("${ALL_JAVA_SERVICES[@]}")
    PYTHON_TO_BUILD=("${ALL_PYTHON_SERVICES[@]}")
    return
  fi

  JAVA_TO_BUILD=()
  PYTHON_TO_BUILD=()

  for svc in "${requested[@]}"; do
    local found=false
    for j in "${ALL_JAVA_SERVICES[@]}"; do
      if [[ "$svc" == "$j" ]]; then
        JAVA_TO_BUILD+=("$svc")
        found=true
        break
      fi
    done
    if ! $found; then
      for p in "${ALL_PYTHON_SERVICES[@]}"; do
        if [[ "$svc" == "$p" ]]; then
          PYTHON_TO_BUILD+=("$svc")
          found=true
          break
        fi
      done
    fi
    if ! $found; then
      warn "Unknown service: $svc (skipped)"
    fi
  done
}

# ── main ──────────────────────────────────────────────────────────
main() {
  detect_runtime
  cd "$PROJECT_ROOT"

  JAVA_TO_BUILD=()
  PYTHON_TO_BUILD=()
  resolve_services "$@"

  local total=$(( ${#JAVA_TO_BUILD[@]} + ${#PYTHON_TO_BUILD[@]} ))
  info "=== Building ${total} service(s) ==="

  for svc in "${JAVA_TO_BUILD[@]}"; do
    local tag="latest"
    if [[ "$svc" == "stream-processing" ]]; then
      tag="$STREAM_PROCESSING_TAG"
    fi
    build_image "$svc" "$PROJECT_ROOT" "services/${svc}/Dockerfile" "$tag" || true
  done

  for svc in "${PYTHON_TO_BUILD[@]}"; do
    build_image "$svc" "services/${svc}" "services/${svc}/Dockerfile" "latest" || true
  done

  # ── summary ───────────────────────────────────────────────────
  echo ""
  info "=== Build Summary ==="
  if [[ ${#SUCCESS_SERVICES[@]} -gt 0 ]]; then
    info "  ✓ Succeeded (${#SUCCESS_SERVICES[@]}): ${SUCCESS_SERVICES[*]}"
  fi
  if [[ ${#FAILED_SERVICES[@]} -gt 0 ]]; then
    error "  ✗ Failed (${#FAILED_SERVICES[@]}): ${FAILED_SERVICES[*]}"
  fi

  echo ""
  info "Images in K3s containerd:"
  if command -v nerdctl &>/dev/null; then
    nerdctl --namespace k8s.io images | grep "threat-detection/" || true
  elif command -v k3s &>/dev/null; then
    k3s crictl images | grep "threat-detection/" || true
  else
    docker images | grep "threat-detection/" || true
  fi

  if [[ ${#FAILED_SERVICES[@]} -gt 0 ]]; then
    echo ""
    error "Some builds failed. Re-run with specific services:"
    error "  sudo bash scripts/k3s-build-images.sh ${FAILED_SERVICES[*]}"
    exit 1
  fi
}

main "$@"
