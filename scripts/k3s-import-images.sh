#!/usr/bin/env bash
# =============================================================================
# Import threat-detection images into K3s containerd from an archive.
# Use with the archive produced by k3s-export-images.sh.
#
# Usage:
#   sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz
#   sudo bash scripts/k3s-import-images.sh /path/to/threat-detection-images.tar.gz
# =============================================================================
set -euo pipefail

ARCHIVE="${1:?Usage: sudo bash $0 <threat-detection-images.tar.gz>}"
TMP_DIR="/tmp/td-image-import-$$"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [ ! -f "$ARCHIVE" ]; then
  err "Archive not found: $ARCHIVE"
  exit 1
fi

if ! command -v k3s &>/dev/null; then
  err "K3s not installed. Install K3s first: curl -sfL https://get.k3s.io | sh -"
  exit 1
fi

mkdir -p "$TMP_DIR"

log "Extracting archive: $ARCHIVE"
tar xzf "$ARCHIVE" -C "$TMP_DIR"

imported=0
failed=0
total=$(find "$TMP_DIR" -name '*.tar' | wc -l)

log "Found ${total} image archives to import"
echo ""

for tar_file in "$TMP_DIR"/*.tar; do
  [ -f "$tar_file" ] || continue
  local_name=$(basename "$tar_file" .tar | tr '__' '/:')
  log "  Importing: $local_name"
  if k3s ctr images import "$tar_file" 2>/dev/null; then
    ok "    Imported successfully"
    imported=$((imported + 1))
  else
    warn "    Import failed (may already exist or format mismatch)"
    failed=$((failed + 1))
  fi
done

echo ""
log "=========================================="
ok "Imported: ${imported}/${total} images"
if [ $failed -gt 0 ]; then
  warn "Failed: ${failed} images"
fi
log "=========================================="

echo ""
log "Verify with: sudo crictl images | grep -v rancher"
echo ""
log "Next steps:"
log "  1. Build app images from source: sudo bash scripts/k3s-build-images.sh"
log "  2. Deploy: sudo bash scripts/k3s-deploy.sh"
