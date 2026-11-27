#!/usr/bin/env bash
# INTERACTION-010: offline, version-locked Playwright browser runtime for the
# frontend E2E suite. Test infrastructure only — never part of the release
# archive (scripts/pack_release.py uses an explicit file list).
#
#   save [DIR]  On an Internet-connected x86_64 Linux host: pull the official
#               image matching frontend/package-lock.json, write
#               playwright-v<ver>-noble.tar.gz + .sha256 + .meta into DIR
#               (default: current directory).
#   load [DIR]  On the build host (192.168.100.9): check free space, verify the
#               checksum, docker load the archive and verify the image ID.
#               DIR defaults to /home/jzzn/e2e-images.
#
# Environment:
#   TDS_PLAYWRIGHT_IMAGE      override the image reference (must keep v<ver>)
#   TDS_E2E_MIN_FREE_GB       minimum free space for `load` (default 4)
#
# The script never prunes or removes other images, volumes or files.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCK_FILE="$REPO_ROOT/frontend/package-lock.json"
MIN_FREE_GB="${TDS_E2E_MIN_FREE_GB:-4}"

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

usage() {
  sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 2
}

# Version of @playwright/test locked in package-lock.json; playwright-core
# must be locked to the same version (they ship together).
locked_playwright_version() {
  [ -f "$LOCK_FILE" ] || die "missing $LOCK_FILE"
  python3 - "$LOCK_FILE" <<'PY'
import json, sys
packages = json.load(open(sys.argv[1], encoding="utf-8")).get("packages", {})
test = packages.get("node_modules/@playwright/test", {}).get("version")
core = packages.get("node_modules/playwright-core", {}).get("version")
if not test or not core:
    sys.exit("package-lock.json does not lock @playwright/test and playwright-core")
if test != core:
    sys.exit(f"@playwright/test {test} != playwright-core {core} in package-lock.json")
print(test)
PY
}

free_gb() {
  # Free space (GiB, rounded down) of the filesystem holding $1; empty if unknown.
  df -Pk "$1" 2>/dev/null | awk 'NR == 2 { printf "%d", $4 / 1048576 }'
}

require_free_space() {
  local path="$1" label="$2" free
  free="$(free_gb "$path")"
  if [ -z "$free" ]; then
    echo "WARNING: cannot read free space of $label ($path); check it manually with df -h" >&2
    return 0
  fi
  [ "$free" -ge "$MIN_FREE_GB" ] \
    || die "$label has ${free} GiB free, need at least ${MIN_FREE_GB} GiB ($path). Free space manually; this script never prunes."
  info "$label: ${free} GiB free (>= ${MIN_FREE_GB} GiB)"
}

[ "$(uname -s)" = "Linux" ] || die "Linux host required (got $(uname -s))"
[ "$(uname -m)" = "x86_64" ] || die "x86_64 host required (got $(uname -m))"
command -v docker >/dev/null || die "docker is not installed"
command -v python3 >/dev/null || die "python3 is required to read package-lock.json"

VERSION="$(locked_playwright_version)" || die "cannot determine the locked Playwright version"
IMAGE="${TDS_PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v${VERSION}-noble}"
case "$IMAGE" in
  *":v${VERSION}-"*) ;;
  *) die "image $IMAGE does not match the locked Playwright version v${VERSION}" ;;
esac
BASENAME="playwright-v${VERSION}-noble"

command="${1:-}"
case "$command" in
  save)
    out_dir="${2:-$PWD}"
    mkdir -p "$out_dir"
    out_dir="$(cd "$out_dir" && pwd)"
    case "$out_dir/" in
      "$REPO_ROOT"/outputs/v*) die "do not store the test image under outputs/v<version>/ (release artefacts only)" ;;
    esac
    info "Pulling $IMAGE (Playwright $VERSION from package-lock.json)"
    docker pull "$IMAGE"
    image_id="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
    info "Saving to $out_dir/$BASENAME.tar.gz"
    docker save "$IMAGE" | gzip -1 > "$out_dir/$BASENAME.tar.gz.partial"
    mv "$out_dir/$BASENAME.tar.gz.partial" "$out_dir/$BASENAME.tar.gz"
    (cd "$out_dir" && sha256sum "$BASENAME.tar.gz" > "$BASENAME.tar.gz.sha256")
    {
      echo "image=$IMAGE"
      echo "image_id=$image_id"
      echo "repo_digests=$(docker image inspect --format '{{join .RepoDigests " "}}' "$IMAGE")"
      echo "playwright=$VERSION"
      echo "saved_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$out_dir/$BASENAME.meta"
    info "Done: $(cat "$out_dir/$BASENAME.tar.gz.sha256")"
    ;;
  load)
    in_dir="${2:-/home/jzzn/e2e-images}"
    [ -d "$in_dir" ] || die "directory not found: $in_dir"
    cd "$in_dir"
    for file in "$BASENAME.tar.gz" "$BASENAME.tar.gz.sha256"; do
      [ -f "$file" ] || die "missing $in_dir/$file"
    done
    docker info >/dev/null 2>&1 || die "cannot reach the Docker daemon"
    require_free_space "$in_dir" "archive directory"
    docker_root="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || true)"
    [ -n "$docker_root" ] && require_free_space "$docker_root" "Docker root"
    info "Verifying checksum"
    sha256sum -c "$BASENAME.tar.gz.sha256"
    info "Loading $BASENAME.tar.gz"
    gzip -dc "$BASENAME.tar.gz" | docker load
    docker image inspect "$IMAGE" >/dev/null 2>&1 || die "image $IMAGE not present after docker load"
    loaded_id="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
    if [ -f "$BASENAME.meta" ]; then
      expected_id="$(sed -n 's/^image_id=//p' "$BASENAME.meta")"
      [ -z "$expected_id" ] || [ "$expected_id" = "$loaded_id" ] \
        || die "loaded image ID $loaded_id differs from the saved ID $expected_id"
    fi
    info "Loaded $IMAGE ($loaded_id)"
    ;;
  -h|--help|help|"") usage ;;
  *) die "unknown command: $command (expected save or load)" ;;
esac
