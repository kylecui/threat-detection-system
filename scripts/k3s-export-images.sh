#!/usr/bin/env bash
# =============================================================================
# Export ALL images required by the threat-detection system from K3s containerd
# into a single tar archive for offline deployment to other machines.
#
# Usage:
#   sudo bash scripts/k3s-export-images.sh                    # export to default path
#   sudo bash scripts/k3s-export-images.sh /path/to/output    # export to custom dir
#
# Output: <output_dir>/threat-detection-images.tar.gz  (~2-3 GB)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="${1:-$PROJECT_ROOT}"
ARCHIVE_NAME="threat-detection-images.tar.gz"
TMP_DIR="/tmp/td-image-export-$$"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }

# All images referenced in k8s/base/ manifests
INFRA_IMAGES=(
  "postgres:15-alpine"
  "redis:7-alpine"
  "busybox:1.35"
  "confluentinc/cp-zookeeper:7.4.0"
  "confluentinc/cp-kafka:7.4.0"
  "docker.elastic.co/logstash/logstash:8.11.0"
  "emqx/emqx:5.5.1"
  "curlimages/curl:latest"
)

APP_IMAGES=(
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

ALL_IMAGES=("${INFRA_IMAGES[@]}" "${APP_IMAGES[@]}")

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

main() {
  mkdir -p "$TMP_DIR" "$OUTPUT_DIR"

  log "Exporting ${#ALL_IMAGES[@]} images for offline deployment"
  log "Output: ${OUTPUT_DIR}/${ARCHIVE_NAME}"
  echo ""

  local exported=0
  local failed=0

  for img in "${ALL_IMAGES[@]}"; do
    local safe_name
    safe_name=$(echo "$img" | tr '/:' '__')
    local tar_file="${TMP_DIR}/${safe_name}.tar"

    log "  Exporting: $img"

    # Try k3s ctr first (containerd native), then docker
    if command -v k3s &>/dev/null; then
      if k3s ctr images export "$tar_file" "docker.io/${img}" 2>/dev/null || \
         k3s ctr images export "$tar_file" "${img}" 2>/dev/null; then
        ok "    Exported: $img ($(du -sh "$tar_file" | awk '{print $1}'))"
        exported=$((exported + 1))
        continue
      fi
    fi

    if command -v docker &>/dev/null; then
      if docker save "$img" -o "$tar_file" 2>/dev/null || \
         docker save "docker.io/library/$img" -o "$tar_file" 2>/dev/null; then
        ok "    Exported: $img ($(du -sh "$tar_file" | awk '{print $1}'))"
        exported=$((exported + 1))
        continue
      fi
    fi

    if command -v crictl &>/dev/null; then
      # crictl doesn't have save, but ctr does
      warn "    Skipped: $img (not found in local registry)"
      failed=$((failed + 1))
    else
      warn "    Skipped: $img (not found)"
      failed=$((failed + 1))
    fi
  done

  echo ""
  log "Compressing ${exported} images into archive..."
  tar czf "${OUTPUT_DIR}/${ARCHIVE_NAME}" -C "$TMP_DIR" .
  local size
  size=$(du -sh "${OUTPUT_DIR}/${ARCHIVE_NAME}" | awk '{print $1}')

  echo ""
  log "=========================================="
  ok "Exported: ${exported}/${#ALL_IMAGES[@]} images"
  if [ $failed -gt 0 ]; then
    warn "Skipped: ${failed} images (not available locally)"
  fi
  ok "Archive: ${OUTPUT_DIR}/${ARCHIVE_NAME} (${size})"
  log "=========================================="
  echo ""
  log "To deploy on a new machine:"
  log "  1. Copy ${ARCHIVE_NAME} to target machine"
  log "  2. Run: sudo bash scripts/k3s-import-images.sh ${ARCHIVE_NAME}"
  log "  3. Run: sudo bash scripts/k3s-build-images.sh  (to build app images from source)"
  log "  4. Run: sudo bash scripts/k3s-deploy.sh"
}

main
