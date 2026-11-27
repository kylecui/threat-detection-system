#!/usr/bin/env bash
# INTERACTION-010: run the frontend Playwright E2E suite on the Ubuntu build
# host inside the version-locked official Playwright image (prepared offline
# with prepare_playwright_image.sh). Test infrastructure only — never part of
# the release archive.
#
# Usage:
#   scripts/testing/run_frontend_e2e_ubuntu.sh [playwright test args...]
#
# Examples:
#   scripts/testing/run_frontend_e2e_ubuntu.sh                      # full suite, 4 workers
#   scripts/testing/run_frontend_e2e_ubuntu.sh e2e/overview-trend.spec.ts --workers=4 --repeat-each=3
#
# Without --workers/-j the run uses --workers=4 (the project default); the
# full regression must not be replaced by a single-worker run. CI is unset in
# the container, so the config's CI-only single worker and retries never apply.
#
# Environment:
#   TDS_PLAYWRIGHT_IMAGE     image (default mcr.microsoft.com/playwright:v<locked>-noble)
#   TDS_E2E_NPM_REGISTRY     npm registry reachable from the build host (optional)
#   TDS_E2E_NPM_CACHE        host npm cache dir (default ~/.cache/tds-e2e-npm)
#   TDS_E2E_REPORT_DIR       report dir (default outputs/e2e-reports/<UTC time>)
#   TDS_E2E_MIN_FREE_GB      minimum free disk for reports and Docker root (default 4)
#   TDS_E2E_MIN_MEM_GB       minimum available memory (default 4; 1 GiB is /dev/shm)
#
# The exit code is the exit code of `playwright test` (0 only if every test
# passed); preflight failures exit non-zero before any test runs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND="$REPO_ROOT/frontend"
LOCK_FILE="$FRONTEND/package-lock.json"
MIN_FREE_GB="${TDS_E2E_MIN_FREE_GB:-4}"
MIN_MEM_GB="${TDS_E2E_MIN_MEM_GB:-4}"
SHM_SIZE="1g"

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

case "${1:-}" in
  -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

# ------------------------------------------------------------------ preflight
[ "$(uname -s)" = "Linux" ] || die "Linux host required (got $(uname -s))"
[ "$(uname -m)" = "x86_64" ] || die "x86_64 host required (got $(uname -m))"
command -v docker >/dev/null || die "docker is not installed"
command -v python3 >/dev/null || die "python3 is required to read package-lock.json"
[ -f "$LOCK_FILE" ] || die "missing $LOCK_FILE"
docker info >/dev/null 2>&1 || die "cannot reach the Docker daemon (is the user in the docker group?)"

VERSION="$(python3 - "$LOCK_FILE" <<'PY'
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
)" || die "cannot determine the locked Playwright version"

IMAGE="${TDS_PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v${VERSION}-noble}"
case "$IMAGE" in
  *":v${VERSION}-"*) ;;
  *) die "image $IMAGE does not match the locked Playwright version v${VERSION}" ;;
esac
docker image inspect "$IMAGE" >/dev/null 2>&1 \
  || die "image $IMAGE is not loaded. Run: scripts/testing/prepare_playwright_image.sh load <dir> (no online download is attempted)"
IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$IMAGE")"

free_gb() { df -Pk "$1" 2>/dev/null | awk 'NR == 2 { printf "%d", $4 / 1048576 }'; }
check_free() {
  local path="$1" label="$2" free
  free="$(free_gb "$path")"
  if [ -z "$free" ]; then
    echo "WARNING: cannot read free space of $label ($path); check it with df -h" >&2
    return 0
  fi
  [ "$free" -ge "$MIN_FREE_GB" ] \
    || die "$label has ${free} GiB free, need ${MIN_FREE_GB} GiB ($path). Free space manually; nothing is pruned automatically."
  info "$label: ${free} GiB free"
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_DIR="${TDS_E2E_REPORT_DIR:-$REPO_ROOT/outputs/e2e-reports/$STAMP}"
case "$REPORT_DIR/" in
  "$REPO_ROOT"/outputs/v*) die "do not write E2E reports into outputs/v<version>/ (release artefacts only)" ;;
esac
mkdir -p "$REPORT_DIR"
REPORT_DIR="$(cd "$REPORT_DIR" && pwd)"
NPM_CACHE="${TDS_E2E_NPM_CACHE:-$HOME/.cache/tds-e2e-npm}"
mkdir -p "$NPM_CACHE"

check_free "$REPORT_DIR" "report directory"
DOCKER_ROOT="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || true)"
# node_modules is installed inside the container, i.e. on the Docker root filesystem.
[ -z "$DOCKER_ROOT" ] || check_free "$DOCKER_ROOT" "Docker root"

mem_kb="$(awk '/^MemAvailable:/ { print $2 }' /proc/meminfo 2>/dev/null || true)"
if [ -n "$mem_kb" ]; then
  mem_gb=$((mem_kb / 1048576))
  [ "$mem_gb" -ge "$MIN_MEM_GB" ] \
    || die "only ${mem_gb} GiB memory available, need ${MIN_MEM_GB} GiB (4 browsers + ${SHM_SIZE} /dev/shm)"
  info "memory: ${mem_gb} GiB available, /dev/shm in container: ${SHM_SIZE}"
fi

# ------------------------------------------------------------------ arguments
ARGS=("$@")
has_workers=0
for arg in "${ARGS[@]+"${ARGS[@]}"}"; do
  case "$arg" in --workers|--workers=*|-j|-j*) has_workers=1 ;; esac
done
[ "$has_workers" -eq 1 ] || ARGS+=("--workers=4")

# ------------------------------------------------------------------ container
# Runs as the calling user (no root-owned files on the host), copies the
# source into the container (host node_modules is never read or written),
# installs from the lock file and checks that the image ships the browser
# revisions the installed Playwright expects before running any test.
INNER='set -euo pipefail
SRC="${TDS_E2E_SRC:-/src}"; WORK="${TDS_E2E_WORK:-/tmp/frontend}"; OUT="${TDS_E2E_OUT:-/report}"
mkdir -p "$HOME" "$WORK"
tar -C "$SRC" --exclude=./node_modules --exclude=./dist --exclude=./playwright-report --exclude=./test-results -cf - . | tar -C "$WORK" -xf -
cd "$WORK"
echo "==> npm ci (registry: ${TDS_E2E_NPM_REGISTRY:-npm default})"
npm ci --no-audit --no-fund ${TDS_E2E_NPM_REGISTRY:+--registry="$TDS_E2E_NPM_REGISTRY"}
node - "$EXPECTED_PLAYWRIGHT" <<'"'"'JS'"'"'
const fs = require("fs");
const path = require("path");
const expected = process.argv[2];
const version = JSON.parse(fs.readFileSync("node_modules/@playwright/test/package.json", "utf8")).version;
if (version !== expected) throw new Error(`installed @playwright/test ${version} != locked ${expected}`);
const browsersPath = process.env.PLAYWRIGHT_BROWSERS_PATH || "/ms-playwright";
const { browsers } = JSON.parse(fs.readFileSync("node_modules/playwright-core/browsers.json", "utf8"));
const missing = [];
for (const name of ["chromium", "chromium-headless-shell"]) {
  const browser = browsers.find((item) => item.name === name);
  if (!browser) continue;
  const dir = path.join(browsersPath, `${name.replace(/-/g, "_")}-${browser.revision}`);
  if (!fs.existsSync(dir)) missing.push(dir);
  else console.log(`==> ${name} revision ${browser.revision} (${browser.browserVersion || "?"}) at ${dir}`);
}
if (missing.length) throw new Error(`image lacks browsers expected by Playwright ${version}: ${missing.join(", ")}`);
JS
npx playwright --version
# INTERACTION-011: resources actually seen inside the container.
echo "==> container resources: cpus=$(nproc) $(grep -E "^MemAvailable" /proc/meminfo | tr -s " ") shm=$(df -h /dev/shm | awk "NR==2 {print \$2}")"
exec npx playwright test --forbid-only --reporter=list,html,json --output="$OUT/test-results" "$@"'

{
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "host=$(hostname)"
  echo "image=$IMAGE"
  echo "image_id=$IMAGE_ID"
  echo "playwright_locked=$VERSION"
  echo "args=${ARGS[*]}"
  echo "host_cpus=$(nproc 2>/dev/null || echo unknown)"
  echo "host_mem_available_kib=${mem_kb:-unknown}"
  echo "shm_size=$SHM_SIZE"
} > "$REPORT_DIR/run-info.txt"
printf '%s\n' "$INNER" > "$REPORT_DIR/container-script.sh"

# INTERACTION-011: sample the container's memory every 5s so the minimum
# memory gate can be set from measured peaks (memory-samples.txt, peak in run-info.txt).
CONTAINER_NAME="tds-frontend-e2e-$STAMP-$$"
(
  while sleep 5; do
    sample="$(docker stats --no-stream --format '{{.MemUsage}}' "$CONTAINER_NAME" 2>/dev/null)" || continue
    [ -n "$sample" ] && echo "$(date -u +%H:%M:%S) $sample" >> "$REPORT_DIR/memory-samples.txt"
  done
) >/dev/null 2>&1 &
SAMPLER_PID=$!
trap 'kill "$SAMPLER_PID" 2>/dev/null || true' EXIT

info "Running Playwright $VERSION in $IMAGE"
info "Reports: $REPORT_DIR"
set +e
docker run --rm --init \
  --name "$CONTAINER_NAME" \
  --shm-size="$SHM_SIZE" \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp/home \
  -e CI= \
  -e npm_config_cache=/npm-cache \
  -e TDS_E2E_NPM_REGISTRY="${TDS_E2E_NPM_REGISTRY:-}" \
  -e EXPECTED_PLAYWRIGHT="$VERSION" \
  -e PLAYWRIGHT_HTML_OPEN=never \
  -e PLAYWRIGHT_HTML_OUTPUT_DIR=/report/html \
  -e PLAYWRIGHT_JSON_OUTPUT_NAME=/report/results.json \
  -v "$FRONTEND:/src:ro" \
  -v "$REPORT_DIR:/report" \
  -v "$NPM_CACHE:/npm-cache" \
  -w /tmp \
  "$IMAGE" \
  bash -c "$INNER" playwright-e2e "${ARGS[@]}" 2>&1 | tee "$REPORT_DIR/console.log"
status=${PIPESTATUS[0]}
set -e
kill "$SAMPLER_PID" 2>/dev/null || true
wait "$SAMPLER_PID" 2>/dev/null || true

# Peak "used" value of `docker stats` (e.g. "1.234GiB / 7.6GiB"), in MiB.
peak_mib="$(python3 - "$REPORT_DIR/memory-samples.txt" <<'PY' 2>/dev/null || true
import re, sys
units = {"B": 1 / 1048576, "KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "kB": 1 / 1024, "MB": 1, "GB": 1024}
peak = 0.0
try:
    for line in open(sys.argv[1], encoding="utf-8"):
        match = re.search(r"([\d.]+)\s*([KMG]i?B|B|kB)\s*/", line)
        if match:
            peak = max(peak, float(match.group(1)) * units.get(match.group(2), 1))
except FileNotFoundError:
    pass
print(f"{peak:.0f}" if peak else "")
PY
)"

{
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "exit_status=$status"
  echo "container_memory_peak_mib=${peak_mib:-unknown}"
} >> "$REPORT_DIR/run-info.txt"
if [ "$status" -eq 0 ]; then
  info "E2E passed (reports: $REPORT_DIR)"
else
  echo "E2E FAILED with exit status $status (reports: $REPORT_DIR)" >&2
fi
exit "$status"
