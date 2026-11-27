#!/usr/bin/env bash
# Official transactional release-package installer for threat-detection-system.

set -Eeuo pipefail

RED='\033[0;31m'; GRN='\033[0;32m'; CYN='\033[0;36m'; YLW='\033[0;33m'; RST='\033[0m'
info() { printf "${CYN}[INFO]${RST}  %s\n" "$*"; }
ok()   { printf "${GRN}[ OK ]${RST}  %s\n" "$*"; }
warn() { printf "${YLW}[WARN]${RST}  %s\n" "$*"; }
die()  { printf "${RED}[ERR]${RST}   %s\n" "$*" >&2; exit 1; }

SRC_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_DIR="$SRC_ROOT/scripts"
INTEGRITY_HELPER="$SCRIPT_DIR/verify_release_state.py"
TRUSTED_MANIFEST=""
TARGET_DIR="/opt/threat-detection-system"
BACKUP_ROOT="/opt/threat-detection-system-backups"
UNIT_NAME="threat-detection-compose.service"
UNIT_PATH="/etc/systemd/system/$UNIT_NAME"
WATCHDOG_SERVICE_NAME="threat-detection-runtime-watchdog.service"
WATCHDOG_TIMER_NAME="threat-detection-runtime-watchdog.timer"
WATCHDOG_SERVICE_PATH="/etc/systemd/system/$WATCHDOG_SERVICE_NAME"
WATCHDOG_TIMER_PATH="/etc/systemd/system/$WATCHDOG_TIMER_NAME"
DIAG_PATH="/usr/local/sbin/tdsdiag"
NO_START=false
UNINSTALL=false
PURGE=false
DRY_RUN=false
YES=false
DEBUG=false
PREPARED_ENV=""
ROLLBACK_DIR=""
TARGET_EXISTED=false
INSTALL_ACTIVE=false
PRESERVE_ROLLBACK=false
ROLLBACK_RESULT="not-needed"
RELEASE_VERSION=""
INSTALLED_VERSION=""
INSTALL_MODE="fresh"
APT_LOCK_TIMEOUT_SECONDS="${APT_LOCK_TIMEOUT_SECONDS:-600}"
DOCKER_PULL_RETRIES="${DOCKER_PULL_RETRIES:-4}"
DOCKER_PULL_TIMEOUT_SECONDS="${DOCKER_PULL_TIMEOUT_SECONDS:-600}"
DOCKER_BUILD_RETRIES="${DOCKER_BUILD_RETRIES:-3}"
READINESS_TIMEOUT_SECONDS="${READINESS_TIMEOUT_SECONDS:-360}"
STABILITY_WINDOW_SECONDS="${STABILITY_WINDOW_SECONDS:-30}"
DOCKER_PRESENT_AT_START=false
command -v docker >/dev/null 2>&1 && DOCKER_PRESENT_AT_START=true

DOCKER_REGISTRY_MIRRORS=(
  "https://docker.xuanyuan.me"
  "https://docker.1ms.run"
  "https://docker.m.daocloud.io"
)
FLINK_RUNTIME_IMAGE="flink:1.18.1-scala_2.12-java17"
FLINK_FALLBACK_IMAGE="${FLINK_FALLBACK_IMAGE:-docker.m.daocloud.io/library/flink:1.18.1-scala_2.12-java17}"
FLINK_EXPECTED_DIGEST="sha256:44af270538aa97418be63d0f42b2f4400d12c5e41e58813f8b4b85d52fe27f5d"

usage() {
  cat <<EOF
Usage: sudo ./install.sh [options]

Options:
  --no-start         Install files without building or starting containers
  --uninstall        Remove containers and systemd unit; preserve data and configuration
  --purge            Remove containers, images, volumes, configuration, credentials and backups
  --dry-run          Show purge scope without changing the system (requires --purge)
  --yes              Confirm destructive operations non-interactively
  --debug            Write detailed installer output to /var/log/threat-detection-install.log
  -h, --help         Show this help

Production installation uses only the extracted release package:
  tar -xzf threat-detection-system-<version>-linux-x86_64.tar.gz
  cd threat-detection-system-<version>-linux-x86_64
  sudo ./install.sh

When an older installation is present, the same command automatically
overwrites and upgrades it in place. Do not run --purge before an upgrade;
--purge is only for permanently removing configuration, credentials and data.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-start) NO_START=true; shift ;;
    --uninstall) UNINSTALL=true; shift ;;
    --purge) PURGE=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    --yes|-y) YES=true; shift ;;
    --debug) DEBUG=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1" ;;
  esac
done

$DRY_RUN && ! $PURGE && die "--dry-run can only be used with --purge"
$UNINSTALL && $PURGE && die "--uninstall and --purge are mutually exclusive"
[[ $(id -u) -eq 0 ]] || die "Root privileges are required (use sudo)"

if $DEBUG; then
  mkdir -p /var/log
  exec > >(tee -a /var/log/threat-detection-install.log) 2>&1
  set -x
fi

validate_target() {
  local resolved
  resolved="$(readlink -m "$TARGET_DIR")"
  [[ "$resolved" == /opt/* && "$resolved" != /opt ]] \
    || die "Refusing to operate on unsafe target directory: $resolved"
  TARGET_DIR="$resolved"
}

read_version_marker() {
  local marker="$1" value=""
  [[ -f "$marker" && ! -L "$marker" ]] || return 1
  value="$(tr -d '\r\n' <"$marker")"
  [[ "$value" =~ ^[0-9]+([.][0-9]+)*([+~-][0-9A-Za-z.-]+)?$ ]] || return 1
  printf '%s\n' "$value"
}

version_is_older() {
  local installed="$1" release="$2" oldest
  [[ "$installed" != "$release" ]] || return 1
  oldest="$(printf '%s\n%s\n' "$installed" "$release" | sort -V | sed -n '1p')"
  [[ "$oldest" == "$installed" ]]
}

detect_existing_install() {
  RELEASE_VERSION="$(read_version_marker "$SRC_ROOT/VERSION")" \
    || die "Release VERSION is missing or invalid"

  if [[ ! -d "$TARGET_DIR" ]]; then
    INSTALL_MODE="fresh"
    info "No existing installation detected; installing release $RELEASE_VERSION"
    return 0
  fi

  TARGET_EXISTED=true
  if INSTALLED_VERSION="$(read_version_marker "$TARGET_DIR/.installed_version")"; then
    :
  elif INSTALLED_VERSION="$(read_version_marker "$TARGET_DIR/VERSION")"; then
    :
  else
    INSTALLED_VERSION="unknown"
  fi

  if [[ "$INSTALLED_VERSION" == "unknown" ]]; then
    INSTALL_MODE="legacy-overwrite"
    warn "Existing installation has no valid version marker; treating it as a legacy release"
    info "Legacy application files will be overwritten automatically; .env and Docker data volumes will be preserved"
  elif [[ "$INSTALLED_VERSION" == "$RELEASE_VERSION" ]]; then
    INSTALL_MODE="reinstall"
    info "Release $RELEASE_VERSION is already installed; application files will be overwritten automatically"
  elif version_is_older "$INSTALLED_VERSION" "$RELEASE_VERSION"; then
    INSTALL_MODE="upgrade"
    info "Older installed version detected: $INSTALLED_VERSION -> $RELEASE_VERSION"
    info "Starting automatic in-place overwrite; no uninstall or confirmation is required"
    info "Existing .env and Docker data volumes will be preserved"
  else
    INSTALL_MODE="downgrade-overwrite"
    warn "Installed version $INSTALLED_VERSION is newer than release $RELEASE_VERSION"
    warn "Application files will still be overwritten because this release was explicitly executed"
  fi
}

check_host_capacity() {
  local memory_mb cpu_count disk_gb
  memory_mb="$(awk '/MemTotal:/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 0)"
  cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 0)"
  disk_gb="$(df -Pk /opt 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')"
  [[ "$memory_mb" =~ ^[0-9]+$ ]] || memory_mb=0
  [[ "$cpu_count" =~ ^[0-9]+$ ]] || cpu_count=0
  [[ "$disk_gb" =~ ^[0-9]+$ ]] || disk_gb=0

  if (( memory_mb < 8192 || cpu_count < 4 || disk_gb < 30 )); then
    warn "Host capacity is below the recommended single-node baseline: ${cpu_count} CPU, ${memory_mb} MiB RAM, ${disk_gb} GiB free under /opt"
    warn "Recommended for stable startup and moderate traffic: at least 4 CPU, 8 GiB RAM and 30 GiB free; production high availability requires multiple hosts"
  else
    ok "Host capacity preflight: ${cpu_count} CPU, ${memory_mb} MiB RAM, ${disk_gb} GiB free under /opt"
  fi
}

verify_release_manifest() {
  local manifest="$SRC_ROOT/MANIFEST.sha256"
  [[ -f "$manifest" ]] || die "MANIFEST.sha256 is missing; build and install a formal release package"
  command -v sha256sum >/dev/null 2>&1 || die "sha256sum is required to verify the release"
  info "Verifying the internal MANIFEST.sha256..."
  (cd "$SRC_ROOT" && sha256sum -c --quiet MANIFEST.sha256) \
    || die "Release verification failed; the system was not modified"
  ok "Release manifest verified"
}

verify_release_source_state() {
  python3 "$INTEGRITY_HELPER" verify-release "$SRC_ROOT" \
    --manifest "$SRC_ROOT/MANIFEST.sha256" --expected "$RELEASE_VERSION" \
    || die "Source release contents or version metadata are inconsistent"
}

verify_installed_release() {
  local phase="${1:-content}" args=()
  [[ -n "$TRUSTED_MANIFEST" && -f "$TRUSTED_MANIFEST" ]] \
    || die "Trusted source manifest is unavailable; refusing to commit the installation"
  case "$phase" in
    content) ;;
    environment) args+=(--runtime-env "$TARGET_DIR/.env") ;;
    metadata) args+=(--metadata) ;;
    *) die "Unknown installation verification phase" ;;
  esac
  python3 "$INTEGRITY_HELPER" verify-release "$TARGET_DIR" \
    --manifest "$TRUSTED_MANIFEST" --expected "$RELEASE_VERSION" "${args[@]}" \
    || die "Installed release contents or version metadata verification failed"
}

validate_postgres_init_scripts() {
  local compose_file="$SRC_ROOT/docker/docker-compose.yml"
  local source_name target_name source_path first_line count=0
  declare -A mounted_targets=()

  [[ -f "$compose_file" ]] || die "Docker Compose file is missing: $compose_file"

  while IFS='|' read -r source_name target_name; do
    [[ -n "$source_name" && -n "$target_name" ]] || continue
    count=$((count + 1))

    [[ "$source_name" != *".."* && "$source_name" =~ ^[A-Za-z0-9._/-]+$ ]] \
      || die "Unsafe PostgreSQL initialization source path: $source_name"
    [[ "$target_name" =~ ^[A-Za-z0-9._-]+\.sql$ ]] \
      || die "Invalid PostgreSQL initialization target name: $target_name"
    [[ -z "${mounted_targets[$target_name]:-}" ]] \
      || die "PostgreSQL initialization target is mounted more than once: $target_name"
    mounted_targets[$target_name]="$source_name"

    source_path="$SRC_ROOT/docker/$source_name"
    [[ -f "$source_path" && -s "$source_path" ]] \
      || die "PostgreSQL initialization script is missing or empty: docker/$source_name"
    first_line="$(sed -n '/^[[:space:]]*$/d; p; q' "$source_path")"
    [[ ! "$first_line" =~ ^[A-Za-z0-9._-]+\.sql[[:space:]]*$ ]] \
      || die "PostgreSQL initialization script is a filename placeholder, not SQL: docker/$source_name"
  done < <(
    sed -n \
      's#^[[:space:]]*-[[:space:]]*\./\([^:]*\):/docker-entrypoint-initdb\.d/\([^[:space:]]*\).*$#\1|\2#p' \
      "$compose_file"
  )

  (( count > 0 )) || die "No PostgreSQL initialization scripts are mounted by Docker Compose"
  ok "PostgreSQL initialization scripts validated ($count files)"
}

confirm() {
  local message="$1" reply
  $YES && return 0
  [[ -t 0 ]] || die "Non-interactive purge requires --yes"
  read -r -p "$message [y/N]: " reply
  [[ "$reply" == y || "$reply" == Y ]]
}

compose_in() {
  local root="$1"; shift
  (cd "$root" && docker compose --env-file .env -f docker/docker-compose.yml "$@")
}

installed_project_name() {
  local value=""
  if [[ -f "$TARGET_DIR/.env" ]]; then
    value="$(sed -n 's/^COMPOSE_PROJECT_NAME=//p' "$TARGET_DIR/.env" | tail -n 1)"
  fi
  [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9_-]*$ ]] || value="threat-detection"
  printf '%s\n' "$value"
}

package_manager_status() {
  ps -eo pid=,etimes=,comm=,args= 2>/dev/null \
    | awk '$3 ~ /^(apt|apt-get|dpkg|unattended-upgr|packagekitd)$/ {print "  " $0}' \
    || true
}

apt_get() {
  # A short native dpkg-lock wait plus bounded retries also covers the APT
  # lists/archive locks used by apt-daily and unattended-upgrade.
  local started=$SECONDS remaining native_wait output rc
  while true; do
    remaining=$((APT_LOCK_TIMEOUT_SECONDS - (SECONDS - started)))
    (( remaining >= 0 )) || remaining=0
    native_wait=15
    (( remaining >= native_wait )) || native_wait=$remaining
    output="$(mktemp /tmp/threat-detection-apt.XXXXXX)"
    if apt-get -o "DPkg::Lock::Timeout=$native_wait" "$@" 2>&1 | tee "$output"; then
      rm -f -- "$output"
      return 0
    else
      rc=${PIPESTATUS[0]}
    fi
    if ! grep -qiE 'could not get lock|unable to acquire.*lock|held by process|another process using it|waiting for cache lock' "$output"; then
      rm -f -- "$output"
      return "$rc"
    fi
    rm -f -- "$output"
    if (( SECONDS - started >= APT_LOCK_TIMEOUT_SECONDS )); then
      return "$rc"
    fi
    warn "APT/dpkg is busy; waiting 5 seconds before retrying"
    package_manager_status >&2
    sleep 5
  done
}

ensure_apt_packages() {
  local missing=() package
  [[ "$APT_LOCK_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] \
    || die "APT_LOCK_TIMEOUT_SECONDS must be a non-negative integer"
  for package in "$@"; do dpkg -s "$package" >/dev/null 2>&1 || missing+=("$package"); done
  [[ ${#missing[@]} -gt 0 ]] || { ok "APT dependencies are present: $*"; return 0; }
  info "Installing missing APT dependencies: ${missing[*]}"
  info "APT/dpkg locks will be awaited for up to ${APT_LOCK_TIMEOUT_SECONDS} seconds"
  export DEBIAN_FRONTEND=noninteractive
  if ! apt_get update -y || ! apt_get install -y "${missing[@]}"; then
    local owners
    owners="$(package_manager_status)"
    [[ -z "$owners" ]] || printf '[INFO] Active package-manager processes:\n%s\n' "$owners" >&2
    cat >&2 <<EOF
[ERR] APT installation failed after waiting for package-manager locks when needed.
Do not delete files under /var/lib/dpkg or /var/lib/apt/lists/lock.
Allow unattended-upgrade/apt/dpkg to finish, then check and retry:
  sudo dpkg --audit
  sudo apt-get -o DPkg::Lock::Timeout=${APT_LOCK_TIMEOUT_SECONDS} update
  sudo apt-get -o DPkg::Lock::Timeout=${APT_LOCK_TIMEOUT_SECONDS} install -y ${missing[*]}
  sudo ./install.sh
Logs: /var/log/apt/term.log and /var/log/apt/history.log
EOF
    return 1
  fi
  ok "APT dependencies installed: ${missing[*]}"
}

ensure_buildx() {
  local package candidate
  if ! docker buildx version >/dev/null 2>&1; then
    for candidate in docker-buildx docker-buildx-plugin; do
      if apt-cache show "$candidate" >/dev/null 2>&1; then
        package="$candidate"
        break
      fi
    done
    if [[ -z "${package:-}" ]]; then
      info "Refreshing APT metadata to locate a Docker Buildx package"
      apt_get update -y || die "APT metadata refresh failed while locating Docker Buildx"
      for candidate in docker-buildx docker-buildx-plugin; do
        if apt-cache show "$candidate" >/dev/null 2>&1; then
          package="$candidate"
          break
        fi
      done
    fi
    [[ -n "${package:-}" ]] \
      || die "Docker Buildx is required, but neither docker-buildx nor docker-buildx-plugin is available"
    ensure_apt_packages "$package"
  fi
  docker buildx version >/dev/null 2>&1 || die "Docker Buildx installation did not provide the docker buildx command"
  export COMPOSE_BAKE=true
  ok "Docker Buildx and Compose Bake are ready"
}

print_existing_docker_mirror_guidance() {
  cat <<'EOF'
[WARN] Docker was already installed before this installer started.
The required image still could not be pulled after all retries. The installer
did not modify /etc/docker/daemon.json or restart Docker because that could
affect other containers on this host. Review the existing daemon.json, then run
the following commands to switch to the approved mainland China mirrors (the
existing file is backed up first):

  sudo mkdir -p /etc/docker
  if sudo test -f /etc/docker/daemon.json; then
    sudo cp -a /etc/docker/daemon.json "/etc/docker/daemon.json.backup.$(date +%Y%m%d%H%M%S)"
  fi
  sudo tee /etc/docker/daemon.json >/dev/null <<-'DOCKER_EOF'
  {
    "registry-mirrors": [
      "https://docker.xuanyuan.me",
      "https://docker.1ms.run",
      "https://docker.m.daocloud.io"
    ]
  }
DOCKER_EOF
  sudo dockerd --validate --config-file=/etc/docker/daemon.json
  sudo systemctl daemon-reload
  sudo systemctl restart docker
  sudo docker info | sed -n '/Registry Mirrors/,+5p'
  sudo docker pull postgres:15-alpine

Note: replacing daemon.json also replaces any existing Docker daemon options.
Restore the timestamped backup or merge those options before restarting Docker
when this host uses custom proxy, data-root, insecure registries or other settings.
EOF
}

configure_new_docker_registry_mirrors() {
  local config_dir=/etc/docker
  local config="$config_dir/daemon.json"
  local backup=""
  local temporary

  install -d -m 0755 "$config_dir"
  temporary="$(mktemp "$config_dir/daemon.json.threat-detection.XXXXXX")"
  if [[ -f "$config" ]]; then
    backup="$config.backup.$(date +%Y%m%d%H%M%S)"
    cp -a -- "$config" "$backup"
    warn "Existing Docker daemon configuration backed up to $backup"
  fi

  if ! python3 - "$config" "$temporary" "${DOCKER_REGISTRY_MIRRORS[@]}" <<'PY'
import json
import os
import sys

source, destination, *mirrors = sys.argv[1:]
configuration = {}
if os.path.isfile(source) and os.path.getsize(source):
    with open(source, "r", encoding="utf-8") as stream:
        configuration = json.load(stream)
    if not isinstance(configuration, dict):
        raise SystemExit("existing daemon.json must contain a JSON object")
configuration["registry-mirrors"] = mirrors
with open(destination, "w", encoding="utf-8") as stream:
    json.dump(configuration, stream, ensure_ascii=False, indent=2)
    stream.write("\n")
PY
  then
    rm -f -- "$temporary"
    die "Unable to merge mainland China registry mirrors into $config; the original file was preserved"
  fi

  chmod 0644 "$temporary"
  if ! dockerd --validate --config-file="$temporary" >/dev/null 2>&1; then
    rm -f -- "$temporary"
    die "Generated Docker daemon configuration failed validation; the original file was preserved"
  fi
  mv -f -- "$temporary" "$config"
  systemctl daemon-reload
  if ! systemctl restart docker; then
    if [[ -n "$backup" ]]; then
      cp -a -- "$backup" "$config"
    else
      rm -f -- "$config"
    fi
    systemctl restart docker >/dev/null 2>&1 || true
    die "Docker failed to restart with registry mirrors; the previous daemon configuration was restored"
  fi
  ok "Docker installed by this run and configured with mainland China registry mirrors"
}

ensure_dependencies() {
  ensure_apt_packages ca-certificates curl rsync python3 tar iproute2 openssl util-linux
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    ensure_apt_packages docker.io docker-compose-v2
  fi
  if ! $DOCKER_PRESENT_AT_START; then
    configure_new_docker_registry_mirrors
  fi
  systemctl enable --now docker >/dev/null
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
  ensure_buildx
  ok "Docker and Compose are ready"
}

require_positive_integer() {
  local name="$1" value="$2"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || die "$name must be a positive integer"
}

image_pull_fallback() {
  case "$1" in
    "$FLINK_RUNTIME_IMAGE") printf '%s\n' "$FLINK_FALLBACK_IMAGE" ;;
    *) printf '\n' ;;
  esac
}

image_expected_digest() {
  case "$1" in
    "$FLINK_RUNTIME_IMAGE") printf '%s\n' "$FLINK_EXPECTED_DIGEST" ;;
    *) printf '\n' ;;
  esac
}

image_matches_expected_digest() {
  local image="$1" expected_digest="$2" repo_digests
  [[ -n "$expected_digest" ]] || return 0
  repo_digests="$(
    docker image inspect \
      --format '{{range .RepoDigests}}{{println .}}{{end}}' \
      "$image" 2>/dev/null
  )" || return 1
  grep -Fq "@$expected_digest" <<<"$repo_digests"
}

pull_image_candidate() {
  local candidate="$1" expected_digest="$2"
  timeout --signal=TERM --kill-after=15s "${DOCKER_PULL_TIMEOUT_SECONDS}s" \
    docker pull "$candidate" || return 1
  if ! image_matches_expected_digest "$candidate" "$expected_digest"; then
    warn "Pulled image digest did not match the approved digest: $candidate"
    return 1
  fi
}

tag_verified_fallback_image() {
  local fallback="$1" image="$2" expected_digest="$3"
  image_matches_expected_digest "$fallback" "$expected_digest" || return 1
  docker image tag "$fallback" "$image" || return 1
  image_matches_expected_digest "$image" "$expected_digest"
}

pull_container_image() {
  local image="$1" fallback expected_digest attempt delay
  fallback="$(image_pull_fallback "$image")"
  expected_digest="$(image_expected_digest "$image")"

  if docker image inspect "$image" >/dev/null 2>&1; then
    if image_matches_expected_digest "$image" "$expected_digest"; then
      ok "Container image already available: $image"
      return 0
    fi
    warn "Existing image does not match the approved digest and will be refreshed: $image"
  fi

  if [[ -n "$fallback" ]] && docker image inspect "$fallback" >/dev/null 2>&1; then
    if tag_verified_fallback_image "$fallback" "$image" "$expected_digest"; then
      ok "Reused verified fallback image as $image"
      return 0
    fi
    warn "Existing fallback image does not match the approved digest and will be refreshed: $fallback"
  fi

  for ((attempt = 1; attempt <= DOCKER_PULL_RETRIES; attempt++)); do
    info "Pulling container image ($attempt/$DOCKER_PULL_RETRIES): $image"
    if pull_image_candidate "$image" "$expected_digest"; then
      ok "Container image ready: $image"
      return 0
    fi

    if [[ -n "$fallback" ]]; then
      warn "Primary image source failed; trying the verified fallback: $fallback"
      info "Pulling fallback image ($attempt/$DOCKER_PULL_RETRIES): $fallback"
      if pull_image_candidate "$fallback" "$expected_digest" \
        && tag_verified_fallback_image "$fallback" "$image" "$expected_digest"; then
        ok "Verified fallback image ready as $image"
        return 0
      fi
    fi

    (( attempt < DOCKER_PULL_RETRIES )) || break
    delay=$((attempt * 5))
    warn "Image pull failed for $image; retrying in ${delay}s"
    sleep "$delay"
  done
  warn "Unable to pull required container image after $DOCKER_PULL_RETRIES attempts: $image"
  return 1
}

prefetch_container_images() {
  local image
  local images=(
    postgres:15-alpine
    confluentinc/cp-zookeeper:7.4.0
    redis:7-alpine
    docker.elastic.co/logstash/logstash:8.11.0
    emqx/emqx:5.5.1
    confluentinc/cp-kafka:7.4.0
    maven:3.9-eclipse-temurin-21
    eclipse-temurin:21-jre-alpine
    eclipse-temurin:21-jre
    maven:3.9-eclipse-temurin-17
    "$FLINK_RUNTIME_IMAGE"
    python:3.11-slim
    python:3.12-slim
    node:20-alpine
    nginx:1.25-alpine
  )
  require_positive_integer DOCKER_PULL_RETRIES "$DOCKER_PULL_RETRIES"
  require_positive_integer DOCKER_PULL_TIMEOUT_SECONDS "$DOCKER_PULL_TIMEOUT_SECONDS"
  export DOCKER_CLIENT_TIMEOUT="${DOCKER_CLIENT_TIMEOUT:-$DOCKER_PULL_TIMEOUT_SECONDS}"
  info "Prefetching ${#images[@]} base/runtime images sequentially before changing the installation"
  for image in "${images[@]}"; do
    pull_container_image "$image" || return 1
  done
  ok "All base/runtime container images are available"
}

build_container_images() {
  local service attempt delay
  local services=(
    logstash data-ingestion stream-processing threat-assessment alert-management
    customer-management threat-intelligence tire api-gateway config-server ml-detection frontend
  )
  require_positive_integer DOCKER_BUILD_RETRIES "$DOCKER_BUILD_RETRIES"
  for service in "${services[@]}"; do
    for ((attempt = 1; attempt <= DOCKER_BUILD_RETRIES; attempt++)); do
      info "Building $service ($attempt/$DOCKER_BUILD_RETRIES)"
      if compose_in "$TARGET_DIR" build "$service"; then
        ok "Container image built: $service"
        break
      fi
      (( attempt < DOCKER_BUILD_RETRIES )) || {
        warn "Container image build failed after $DOCKER_BUILD_RETRIES attempts: $service"
        return 1
      }
      delay=$((attempt * 10))
      warn "Build failed for $service; retrying in ${delay}s"
      sleep "$delay"
    done
  done
}

prepare_runtime_env() {
  PREPARED_ENV="$(mktemp /tmp/threat-detection-env.XXXXXX)"
  chmod 600 "$PREPARED_ENV"
  local args=("$SRC_ROOT/.env.example" "$PREPARED_ENV")
  if [[ -f "$TARGET_DIR/.env" ]]; then
    [[ ! -L "$TARGET_DIR/.env" ]] || die "Refusing to read a symlinked .env"
    args+=(--existing "$TARGET_DIR/.env")
  fi
  python3 "$SRC_ROOT/scripts/prepare_release_env.py" "${args[@]}" \
    || die "Runtime environment preparation failed; no secret value was printed"
  chmod 600 "$PREPARED_ENV"
}

validate_compose_ports() {
  local config_json port_specs project_name conflict=0
  config_json="$(mktemp /tmp/threat-detection-compose.XXXXXX.json)"
  port_specs="$(mktemp /tmp/threat-detection-ports.XXXXXX)"
  chmod 600 "$config_json" "$port_specs"

  if ! docker compose --env-file "$PREPARED_ENV" \
      -f "$SRC_ROOT/docker/docker-compose.yml" config --format json >"$config_json"; then
    rm -f -- "$config_json" "$port_specs"
    die "Docker Compose JSON rendering failed"
  fi

  if ! python3 - "$config_json" "$port_specs" <<'PY'
import json
import sys
from collections import defaultdict

source, destination = sys.argv[1:]
with open(source, "r", encoding="utf-8") as stream:
    document = json.load(stream)

published = defaultdict(list)
rows = []
for service_name, service in (document.get("services") or {}).items():
    for item in service.get("ports") or []:
        if isinstance(item, dict):
            host_ip = str(item.get("host_ip") or "0.0.0.0")
            host_port = str(item.get("published") or "")
            protocol = str(item.get("protocol") or "tcp").lower()
            target = str(item.get("target") or "")
        else:
            text = str(item).rsplit("/", 1)
            protocol = text[1].lower() if len(text) == 2 else "tcp"
            fields = text[0].split(":")
            host_ip = "0.0.0.0" if len(fields) < 3 else ":".join(fields[:-2])
            host_port = fields[-2] if len(fields) >= 2 else ""
            target = fields[-1]
        if not host_port.isdigit():
            continue
        key = (host_port, protocol)
        published[key].append((service_name, host_ip, target))
        rows.append((service_name, host_ip, host_port, protocol, target))

duplicates = {key: owners for key, owners in published.items() if len(owners) > 1}
if duplicates:
    print("[ERR] Docker Compose publishes the same host port more than once:", file=sys.stderr)
    for (host_port, protocol), owners in sorted(duplicates.items()):
        rendered = ", ".join(
            f"{service}({host_ip}:{host_port}->{target}/{protocol})"
            for service, host_ip, target in owners
        )
        print(f"  {host_port}/{protocol}: {rendered}", file=sys.stderr)
    raise SystemExit(1)

with open(destination, "w", encoding="utf-8") as stream:
    for row in rows:
        stream.write("|".join(row) + "\n")
PY
  then
    rm -f -- "$config_json" "$port_specs"
    die "Docker Compose contains duplicate published host ports"
  fi
  rm -f -- "$config_json"

  project_name="$(sed -n 's/^COMPOSE_PROJECT_NAME=//p' "$PREPARED_ENV" | tail -n 1)"
  while IFS='|' read -r service host_ip host_port protocol target; do
    local container_id owner_project owner_name own_project_port=false socket_output=""
    while IFS= read -r container_id; do
      [[ -n "$container_id" ]] || continue
      owner_project="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container_id" 2>/dev/null || true)"
      owner_name="$(docker inspect -f '{{.Name}}' "$container_id" 2>/dev/null | sed 's#^/##')"
      if [[ "$owner_project" == "$project_name" ]]; then
        own_project_port=true
      else
        printf '[ERR] Host port %s/%s required by %s is already published by container %s\n' \
          "$host_port" "$protocol" "$service" "${owner_name:-$container_id}" >&2
        conflict=1
      fi
    done < <(docker ps -q --filter "publish=$host_port" 2>/dev/null || true)

    $own_project_port && continue
    if [[ "$protocol" == "udp" ]]; then
      socket_output="$(ss -H -lun "sport = :$host_port" 2>/dev/null || true)"
    else
      socket_output="$(ss -H -ltn "sport = :$host_port" 2>/dev/null || true)"
    fi
    if [[ -n "$socket_output" ]]; then
      printf '[ERR] Host port %s/%s required by %s is already in use on %s\n' \
        "$host_port" "$protocol" "$service" "$host_ip" >&2
      printf '%s\n' "$socket_output" | sed 's/^/  /' >&2
      conflict=1
    fi
  done <"$port_specs"
  rm -f -- "$port_specs"

  [[ $conflict -eq 0 ]] || die "Required host ports are unavailable; no installation state was changed"
  ok "Compose published ports are unique and available"
}

prepare_rollback() {
  ROLLBACK_DIR="$(mktemp -d /var/tmp/threat-detection-rollback.XXXXXX)"
  chmod 700 "$ROLLBACK_DIR"
  # Keep the verifier and the verified source manifest outside the tree being replaced.
  install -m 700 "$INTEGRITY_HELPER" "$ROLLBACK_DIR/verify_release_state.py"
  INTEGRITY_HELPER="$ROLLBACK_DIR/verify_release_state.py"
  install -m 600 "$SRC_ROOT/MANIFEST.sha256" "$ROLLBACK_DIR/release-manifest.sha256"
  TRUSTED_MANIFEST="$ROLLBACK_DIR/release-manifest.sha256"
  python3 "$INTEGRITY_HELPER" verify-release "$SRC_ROOT" \
    --manifest "$TRUSTED_MANIFEST" --expected "$RELEASE_VERSION" \
    || die "Source release changed before the transaction started"
  if [[ -d "$TARGET_DIR" ]]; then
    TARGET_EXISTED=true
    mkdir -p "$ROLLBACK_DIR/target"
    rsync -a "$TARGET_DIR/" "$ROLLBACK_DIR/target/"
    python3 "$INTEGRITY_HELPER" snapshot "$ROLLBACK_DIR/target" "$ROLLBACK_DIR/target.snapshot.json"
  else
    python3 "$INTEGRITY_HELPER" snapshot "$TARGET_DIR" "$ROLLBACK_DIR/target.snapshot.json"
  fi
  local managed_path index=0
  mkdir -p "$ROLLBACK_DIR/paths"
  for managed_path in "$UNIT_PATH" "$WATCHDOG_SERVICE_PATH" "$WATCHDOG_TIMER_PATH" "$DIAG_PATH"; do
    if [[ -e "$managed_path" || -L "$managed_path" ]]; then
      cp -a -- "$managed_path" "$ROLLBACK_DIR/paths/$index"
      python3 "$INTEGRITY_HELPER" snapshot "$ROLLBACK_DIR/paths/$index" "$ROLLBACK_DIR/path-$index.json"
    else
      python3 "$INTEGRITY_HELPER" snapshot "$managed_path" "$ROLLBACK_DIR/path-$index.json"
    fi
    index=$((index + 1))
  done
  PRESERVE_ROLLBACK=false
  ROLLBACK_RESULT="prepared"
  INSTALL_ACTIVE=true
  ok "Transactional rollback snapshot prepared"
}

capture_failure_diagnostics() {
  local output="/var/log/threat-detection-install-failure.log"
  mkdir -p /var/log
  {
    printf 'captured_at=%s\n' "$(date -Is)"
    printf '\n===== compose ps =====\n'
    [[ ! -f "$TARGET_DIR/.env" ]] || compose_in "$TARGET_DIR" ps -a --no-trunc || true
    printf '\n===== compose logs (last 150 lines per service) =====\n'
    [[ ! -f "$TARGET_DIR/.env" ]] || (
      cd "$TARGET_DIR"
      timeout 30 docker compose --env-file .env -f docker/docker-compose.yml \
        logs --no-color --tail=150
    ) || true
    printf '\n===== listening ports =====\n'
    ss -ltnup || true
    printf '\n===== systemd unit =====\n'
    systemctl status "$UNIT_NAME" --no-pager || true
    printf '\n===== Docker/containerd runtime consistency =====\n'
    [[ ! -x "$TARGET_DIR/scripts/runtime-watchdog.sh" ]] \
      || "$TARGET_DIR/scripts/runtime-watchdog.sh" --check || true
  } >"$output" 2>&1
  chmod 600 "$output"
  warn "Failure diagnostics saved to $output (0600)"
}

rollback_failed() {
  PRESERVE_ROLLBACK=true
  ROLLBACK_RESULT="failed"
  if [[ -n "$ROLLBACK_DIR" && -d "$ROLLBACK_DIR" ]]; then
    printf 'rollback_result=failed\nrecovery_directory=%s\n' "$ROLLBACK_DIR" \
      >"$ROLLBACK_DIR/rollback-failure.txt" || true
    chmod 600 "$ROLLBACK_DIR/rollback-failure.txt" 2>/dev/null || true
  fi
  warn "Rollback could not be verified. Recovery materials were retained at: $ROLLBACK_DIR"
  return 1
}

restore_managed_paths() {
  local managed_path index=0
  for managed_path in "$UNIT_PATH" "$WATCHDOG_SERVICE_PATH" "$WATCHDOG_TIMER_PATH" "$DIAG_PATH"; do
    if [[ -e "$ROLLBACK_DIR/paths/$index" || -L "$ROLLBACK_DIR/paths/$index" ]]; then
      python3 "$INTEGRITY_HELPER" verify-snapshot "$ROLLBACK_DIR/paths/$index" "$ROLLBACK_DIR/path-$index.json" \
        || return 1
    fi
    rm -f -- "$managed_path" || return 1
    if [[ -e "$ROLLBACK_DIR/paths/$index" || -L "$ROLLBACK_DIR/paths/$index" ]]; then
      cp -a -- "$ROLLBACK_DIR/paths/$index" "$managed_path" || return 1
    fi
    python3 "$INTEGRITY_HELPER" verify-snapshot "$managed_path" "$ROLLBACK_DIR/path-$index.json" \
      || return 1
    index=$((index + 1))
  done
}

rollback_install() {
  local rc="$1"
  [[ "$INSTALL_ACTIVE" == true ]] || return 0
  INSTALL_ACTIVE=false
  PRESERVE_ROLLBACK=true
  ROLLBACK_RESULT="restoring"
  warn "Installation failed (rc=$rc); restoring the pre-install state..."
  [[ ! -d "$TARGET_DIR" ]] || capture_failure_diagnostics || true
  systemctl disable --now "$WATCHDOG_TIMER_NAME" >/dev/null 2>&1 || true
  systemctl stop "$WATCHDOG_SERVICE_NAME" "$UNIT_NAME" >/dev/null 2>&1 || true
  if [[ -f "$TARGET_DIR/.env" && -f "$TARGET_DIR/docker/docker-compose.yml" ]]; then
    if $TARGET_EXISTED; then
      compose_in "$TARGET_DIR" down --remove-orphans >/dev/null 2>&1 || true
    else
      compose_in "$TARGET_DIR" down -v --rmi local --remove-orphans >/dev/null 2>&1 || true
    fi
  fi
  if $TARGET_EXISTED; then
    [[ -d "$ROLLBACK_DIR/target" ]] || { rollback_failed; return 1; }
    python3 "$INTEGRITY_HELPER" verify-snapshot "$ROLLBACK_DIR/target" "$ROLLBACK_DIR/target.snapshot.json" \
      || { rollback_failed; return 1; }
    mkdir -p "$TARGET_DIR" || { rollback_failed; return 1; }
    rsync -a --checksum --delete "$ROLLBACK_DIR/target/" "$TARGET_DIR/" \
      || { rollback_failed; return 1; }
    python3 "$INTEGRITY_HELPER" verify-snapshot "$TARGET_DIR" "$ROLLBACK_DIR/target.snapshot.json" \
      || { rollback_failed; return 1; }
  else
    rm -rf -- "$TARGET_DIR" || { rollback_failed; return 1; }
    python3 "$INTEGRITY_HELPER" verify-snapshot "$TARGET_DIR" "$ROLLBACK_DIR/target.snapshot.json" \
      || { rollback_failed; return 1; }
  fi
  restore_managed_paths || { rollback_failed; return 1; }
  systemctl daemon-reload >/dev/null 2>&1 || { rollback_failed; return 1; }
  if $TARGET_EXISTED; then
    [[ ! -f "$UNIT_PATH" ]] \
      || systemctl enable --now "$UNIT_NAME" >/dev/null 2>&1 || { rollback_failed; return 1; }
    [[ ! -f "$WATCHDOG_TIMER_PATH" ]] \
      || systemctl enable --now "$WATCHDOG_TIMER_NAME" >/dev/null 2>&1 || { rollback_failed; return 1; }
    warn "The previous installation was restored; verify its status"
  else
    warn "State created by the failed fresh installation was removed"
  fi
  ROLLBACK_RESULT="restored"
  PRESERVE_ROLLBACK=false
  [[ ! -d "$ROLLBACK_DIR" ]] || rm -rf -- "$ROLLBACK_DIR"
  ROLLBACK_DIR=""
  return 0
}

cleanup_temp() {
  [[ -z "$PREPARED_ENV" || ! -f "$PREPARED_ENV" ]] || rm -f -- "$PREPARED_ENV"
  [[ -z "$ROLLBACK_DIR" || ! -d "$ROLLBACK_DIR" || "$INSTALL_ACTIVE" == true || "$PRESERVE_ROLLBACK" == true ]] \
    || rm -rf -- "$ROLLBACK_DIR"
}

on_exit() {
  local rc=$?
  trap - EXIT
  if [[ $rc -ne 0 ]]; then
    rollback_install "$rc" || rc=1
  fi
  cleanup_temp || rc=1
  exit "$rc"
}
trap on_exit EXIT

show_remove_plan() {
  cat <<EOF
Removal plan (uploaded release archives and APT/Docker packages are preserved):
  units      : $UNIT_PATH, $WATCHDOG_SERVICE_PATH, $WATCHDOG_TIMER_PATH
  containers : Compose project from $TARGET_DIR/docker/docker-compose.yml
  install    : $TARGET_DIR
  backups    : $BACKUP_ROOT
  diagnostic : /usr/local/sbin/tdsdiag
  logs       : /var/log/threat-detection-install*.log
  purge only : project images, named volumes, generated .env and database data
EOF
}

remove_installation() {
  local purge_mode="$1" residual=0 project_name
  project_name="$(installed_project_name)"
  show_remove_plan
  if [[ "$purge_mode" == true ]]; then
    $DRY_RUN && { ok "Purge dry-run completed; no changes were made"; return 0; }
    confirm "This permanently removes all installed Threat Detection System state. Continue?" \
      || die "Purge cancelled"
  fi
  systemctl disable --now "$WATCHDOG_TIMER_NAME" >/dev/null 2>&1 || true
  systemctl stop "$WATCHDOG_SERVICE_NAME" >/dev/null 2>&1 || true
  systemctl disable --now "$UNIT_NAME" >/dev/null 2>&1 || true
  if [[ -f "$TARGET_DIR/docker/docker-compose.yml" ]]; then
    if [[ "$purge_mode" == true ]]; then
      if [[ -f "$TARGET_DIR/.env" ]]; then
        compose_in "$TARGET_DIR" down -v --rmi local --remove-orphans || warn "Compose purge reported an error"
      else
        warn "Installed .env is missing; removing containers and volumes by Compose project label"
        docker ps -aq --filter "label=com.docker.compose.project=$project_name" | xargs -r docker rm -f || true
        docker volume ls -q --filter "label=com.docker.compose.project=$project_name" | xargs -r docker volume rm || true
        docker network ls -q --filter "label=com.docker.compose.project=$project_name" | xargs -r docker network rm || true
      fi
    else
      if [[ -f "$TARGET_DIR/.env" ]]; then
        compose_in "$TARGET_DIR" down --remove-orphans || warn "Compose uninstall reported an error"
      else
        warn "Installed .env is missing; removing containers by Compose project label"
        docker ps -aq --filter "label=com.docker.compose.project=$project_name" | xargs -r docker rm -f || true
      fi
    fi
  fi
  rm -f -- "$UNIT_PATH" "$WATCHDOG_SERVICE_PATH" "$WATCHDOG_TIMER_PATH"
  systemctl daemon-reload
  systemctl reset-failed "$UNIT_NAME" "$WATCHDOG_SERVICE_NAME" >/dev/null 2>&1 || true
  if [[ "$purge_mode" == true ]]; then
    rm -rf -- "$TARGET_DIR" "$BACKUP_ROOT"
    rm -f -- /usr/local/sbin/tdsdiag /var/log/threat-detection-install.log \
      /var/log/threat-detection-install-failure.log
    [[ ! -e "$TARGET_DIR" ]] || { warn "Residual install directory: $TARGET_DIR"; residual=1; }
    for unit_path in "$UNIT_PATH" "$WATCHDOG_SERVICE_PATH" "$WATCHDOG_TIMER_PATH"; do
      [[ ! -e "$unit_path" ]] || { warn "Residual unit: $unit_path"; residual=1; }
    done
    if docker ps -aq --filter "label=com.docker.compose.project=$project_name" | grep -q .; then
      warn "Residual $project_name containers remain"; residual=1
    fi
    if docker volume ls -q --filter "label=com.docker.compose.project=$project_name" | grep -q .; then
      warn "Residual $project_name volumes remain"; residual=1
    fi
    [[ $residual -eq 0 ]] || die "Purge completed with residuals"
    ok "Purge completed; uploaded archives and APT/Docker packages were preserved"
  else
    ok "Uninstall completed; configuration, credentials and data volumes were preserved"
  fi
}

sync_release_tree() {
  mkdir -p "$TARGET_DIR"
  if [[ "$SRC_ROOT" != "$TARGET_DIR" ]]; then
    if [[ "$INSTALL_MODE" != "fresh" ]]; then
      info "Overwriting the existing application tree with release $RELEASE_VERSION"
    fi
    rsync -a --checksum --delete --exclude='.env' "$SRC_ROOT/" "$TARGET_DIR/"
  else
    info "Running from the installed release tree; file synchronization is not required"
  fi
  verify_installed_release content
  install -m 600 "$PREPARED_ENV" "$TARGET_DIR/.env"
  verify_installed_release environment
  printf '%s\n' "$RELEASE_VERSION" >"$ROLLBACK_DIR/installed-version"
  install -m 644 "$ROLLBACK_DIR/installed-version" "$TARGET_DIR/.installed_version"
  verify_installed_release metadata
  install -m 755 "$TARGET_DIR/scripts/tdsdiag.sh" "$DIAG_PATH"
  chmod 755 "$TARGET_DIR/scripts/runtime-watchdog.sh"
  install -m 644 "$TARGET_DIR/deploy/systemd/threat-detection-compose.service" "$UNIT_PATH"
  install -m 644 "$TARGET_DIR/deploy/systemd/threat-detection-runtime-watchdog.service" "$WATCHDOG_SERVICE_PATH"
  install -m 644 "$TARGET_DIR/deploy/systemd/threat-detection-runtime-watchdog.timer" "$WATCHDOG_TIMER_PATH"
  ok "Release files installed to $TARGET_DIR"
}

commit_install() {
  # Content and metadata must still match before either success branch drops recovery.
  verify_installed_release metadata
  INSTALL_ACTIVE=false
  ROLLBACK_RESULT="committed"
  rm -rf -- "$ROLLBACK_DIR"
  ROLLBACK_DIR=""
}

backup_existing_install() {
  $TARGET_EXISTED || return 0
  local destination="$BACKUP_ROOT/$(date +%Y%m%d%H%M%S)-pre-install"
  mkdir -p "$destination"; chmod 700 "$destination"
  rsync -a "$TARGET_DIR/" "$destination/"
  ok "Pre-install file backup written to $destination"
}

wait_for_service() {
  local service="$1" timeout="${2:-600}" deadline state health id pid task_state
  deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    id="$(compose_in "$TARGET_DIR" ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$id" ]]; then
      state="$(docker inspect -f '{{.State.Status}}' "$id" 2>/dev/null || true)"
      health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null || true)"
      pid="$(docker inspect -f '{{.State.Pid}}' "$id" 2>/dev/null || true)"
      task_state=""
      if command -v ctr >/dev/null 2>&1; then
        # Do not exit awk as soon as the task is found. With pipefail enabled,
        # closing the pipe early can make ctr receive SIGPIPE (rc=141) and turn
        # an otherwise successful installation into a rollback.
        task_state="$(ctr -n moby tasks list 2>/dev/null \
          | awk -v wanted="$id" 'NR > 1 && $1 == wanted {state=$3} END {if (state != "") print state}')"
      fi
      if [[ "$state" == running \
        && "$pid" =~ ^[1-9][0-9]*$ \
        && -d "/proc/$pid" \
        && ( -z "$task_state" || "$task_state" == RUNNING ) \
        && ( "$health" == healthy || "$health" == none ) ]]; then
        return 0
      fi
    fi
    sleep 3
  done
  warn "Service did not become ready within ${timeout}s: $service"
  return 1
}

apply_database_upgrades() {
  local migration migration_name applied_migrations applied_count=0 skipped_count=0
  info "Checking installer-managed database upgrades..."

  # Releases before 3.1.26 did not have a migration ledger. Create it once for
  # existing data volumes; fresh databases already create it as init script 00.
  compose_in "$TARGET_DIR" exec -T postgres sh -ec \
    'PGOPTIONS="-c client_min_messages=warning" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    <<'SQL' >/dev/null
CREATE TABLE IF NOT EXISTS tds_schema_migrations (
    migration_name VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
SQL

  applied_migrations="$(compose_in "$TARGET_DIR" exec -T postgres sh -ec \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT migration_name FROM tds_schema_migrations ORDER BY migration_name"')"

  for migration_name in \
    43-customer-admin-role.sql \
    44-device-flow-observability.sql \
    45-device-binding-integrity.sql \
    46-threat-window-idempotency.sql \
    47-overview-query-performance.sql \
    48-threat-intel-feed-contract.sql \
    49-notification-runtime-reliability.sql; do
    migration="$TARGET_DIR/docker/$migration_name"
    [[ -f "$migration" ]] || die "Database upgrade is missing: docker/$migration_name"

    if grep -Fxq "$migration_name" <<<"$applied_migrations"; then
      info "Database upgrade already recorded; skipping: $migration_name"
      skipped_count=$((skipped_count + 1))
      continue
    fi

    info "Applying database upgrade: $migration_name"
    compose_in "$TARGET_DIR" exec -T postgres sh -ec \
      'PGOPTIONS="-c client_min_messages=warning" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
      <"$migration" >/dev/null
    applied_migrations="$(compose_in "$TARGET_DIR" exec -T postgres sh -ec \
      'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT migration_name FROM tds_schema_migrations ORDER BY migration_name"')"
    grep -Fxq "$migration_name" <<<"$applied_migrations" \
      || die "Database upgrade did not record completion: $migration_name"
    applied_count=$((applied_count + 1))
  done
  ok "Database upgrades checked (applied=$applied_count, already_present=$skipped_count)"
}

wait_for_platform_readiness() {
  local deadline ready_http bootstrap_http consecutive=0
  deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS))
  info "Waiting for business readiness (three consecutive successful checks)..."
  while (( SECONDS < deadline )); do
    ready_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 12 \
      http://127.0.0.1:8888/api/v1/system/readiness 2>/dev/null || true)"
    bootstrap_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 12 \
      http://127.0.0.1:8888/api/v1/auth/bootstrap/status 2>/dev/null || true)"
    if [[ "$ready_http" == 200 && "$bootstrap_http" == 200 ]]; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= 3 )); then
        ok "Business readiness and first-administrator API are stable"
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 3
  done
  warn "Business readiness did not stabilize within ${READINESS_TIMEOUT_SECONDS}s (readiness=${ready_http:-000}, bootstrap=${bootstrap_http:-000})"
  return 1
}

warm_customer_management_query() {
  local container_id container_ip attempt http_code
  container_id="$(compose_in "$TARGET_DIR" ps -q customer-management 2>/dev/null || true)"
  [[ -n "$container_id" ]] || { warn "Customer Management container is missing"; return 1; }
  container_ip="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' \
    "$container_id" 2>/dev/null | sed -n '/./{p;q}')"
  [[ -n "$container_ip" ]] || { warn "Unable to resolve Customer Management container address"; return 1; }

  for attempt in 1 2 3; do
    http_code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 8 \
      "http://${container_ip}:8084/api/v1/customers?page=0&size=1" 2>/dev/null || true)"
    if [[ "$http_code" == 200 ]]; then
      ok "Customer Management business query is warm and ready"
      return 0
    fi
    sleep 2
  done
  warn "Customer Management business query warm-up failed (HTTP ${http_code:-000})"
  return 1
}

container_restart_snapshot() {
  local id
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    docker inspect -f '{{.Name}}={{.RestartCount}}' "$id" 2>/dev/null || true
  done < <(compose_in "$TARGET_DIR" ps -aq 2>/dev/null | sort)
}

verify_restart_stability() {
  local before after
  before="$(container_restart_snapshot | sort)"
  sleep "$STABILITY_WINDOW_SECONDS"
  after="$(container_restart_snapshot | sort)"
  if [[ "$before" != "$after" ]]; then
    warn "One or more containers restarted during the ${STABILITY_WINDOW_SECONDS}s stability window"
    diff -u <(printf '%s\n' "$before") <(printf '%s\n' "$after") >&2 || true
    return 1
  fi
  ok "Container restart counters remained stable for ${STABILITY_WINDOW_SECONDS}s"
}

verify_fresh_database_is_empty() {
  $TARGET_EXISTED && return 0
  local counts
  counts="$(compose_in "$TARGET_DIR" exec -T postgres sh -ec \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT (SELECT count(*) FROM auth_users) || chr(58) || (SELECT count(*) FROM customers) || chr(58) || (SELECT count(*) FROM device_customer_mapping) || chr(58) || (SELECT count(*) FROM attack_events) || chr(58) || (SELECT count(*) FROM alerts) || chr(58) || (SELECT count(*) FROM threat_assessments);"')"
  [[ "$counts" == "0:0:0:0:0:0" ]] \
    || { warn "Fresh database contains unexpected business rows (users:customers:devices:events:alerts:assessments=$counts)"; return 1; }
  ok "Fresh database contains no demo users, customers, devices, events, alerts or assessments"
}

verify_kafka_topics() {
  local expected topic partitions
  expected="$(awk -F= '$1 == "KAFKA_TOPIC_PARTITIONS" {print $2; exit}' "$TARGET_DIR/.env")"
  [[ "$expected" =~ ^[0-9]+$ ]] || expected=12
  for topic in attack-events status-events threat-alerts minute-aggregations; do
    partitions="$(compose_in "$TARGET_DIR" exec -T kafka kafka-topics \
      --bootstrap-server kafka:29092 --describe --topic "$topic" 2>/dev/null \
      | sed -n 's/.*PartitionCount: \([0-9][0-9]*\).*/\1/p' | head -n1)"
    [[ "$partitions" =~ ^[0-9]+$ && "$partitions" -ge "$expected" ]] \
      || { warn "Kafka topic $topic has ${partitions:-0} partitions; expected at least $expected"; return 1; }
  done
  ok "Kafka high-throughput topics have at least $expected partitions"
}

verify_v1_syslog_listener() {
  local container_id container_ip
  container_id="$(compose_in "$TARGET_DIR" ps -q logstash 2>/dev/null || true)"
  [[ -n "$container_id" ]] \
    || { warn "Logstash container is missing"; return 1; }
  container_ip="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' \
    "$container_id" 2>/dev/null | sed -n '/./{p;q}')"
  [[ -n "$container_ip" ]] \
    || { warn "Unable to resolve the Logstash container address"; return 1; }
  timeout 5 bash -c 'exec 3<>"/dev/tcp/$1/9080"' _ "$container_ip" \
    || { warn "V1 Syslog listener is not accepting TCP connections inside Logstash (${container_ip}:9080)"; return 1; }
  ok "V1 Syslog listener is accepting TCP connections inside Logstash"
}

verify_runtime() {
  local service
  for service in postgres zookeeper redis logstash emqx kafka data-ingestion stream-processing \
    taskmanager threat-assessment alert-management customer-management threat-intelligence tire \
    api-gateway config-server ml-detection frontend; do
    wait_for_service "$service" 600 || return 1
  done
  local topic_id topic_state topic_exit
  topic_id="$(compose_in "$TARGET_DIR" ps -aq topic-init 2>/dev/null || true)"
  [[ -n "$topic_id" ]] || { warn "topic-init container is missing"; return 1; }
  topic_state="$(docker inspect -f '{{.State.Status}}' "$topic_id")"
  topic_exit="$(docker inspect -f '{{.State.ExitCode}}' "$topic_id")"
  [[ "$topic_state" == exited && "$topic_exit" == 0 ]] \
    || { warn "topic-init did not finish successfully"; return 1; }
  local checkpoint_init_id checkpoint_init_state checkpoint_init_exit
  checkpoint_init_id="$(compose_in "$TARGET_DIR" ps -aq flink-checkpoint-init 2>/dev/null || true)"
  [[ -n "$checkpoint_init_id" ]] || { warn "flink-checkpoint-init container is missing"; return 1; }
  checkpoint_init_state="$(docker inspect -f '{{.State.Status}}' "$checkpoint_init_id")"
  checkpoint_init_exit="$(docker inspect -f '{{.State.ExitCode}}' "$checkpoint_init_id")"
  [[ "$checkpoint_init_state" == exited && "$checkpoint_init_exit" == 0 ]] \
    || { warn "Flink checkpoint volume initialization did not finish successfully"; return 1; }
  compose_in "$TARGET_DIR" exec -T --user 9999:9999 stream-processing \
    sh -ec 'test -w /opt/flink/checkpoints && test "$(stat -c %u:%g /opt/flink/checkpoints)" = 9999:9999' \
    || { warn "Flink checkpoint volume is not writable by the Flink runtime user"; return 1; }
  curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1:8080/actuator/health >/dev/null
  curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1:8888/actuator/health >/dev/null
  curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1:3000/ >/dev/null
  verify_v1_syslog_listener
  warm_customer_management_query
  verify_kafka_topics
  wait_for_platform_readiness
  verify_fresh_database_is_empty
  verify_restart_stability
  systemctl is-active --quiet "$UNIT_NAME"
  systemctl is-enabled --quiet "$UNIT_NAME"
  "$TARGET_DIR/scripts/runtime-watchdog.sh" --check
  /usr/local/sbin/tdsdiag --self-test
  ok "Runtime verification passed"
}

print_summary() {
  local version host_ip
  version="$(tr -d '\r\n' <"$TARGET_DIR/VERSION")"
  host_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')"
  [[ -n "$host_ip" ]] || host_ip="<server-ip>"
  cat <<EOF

[ OK ] Threat Detection System installation completed
  Version       : $version
  Install path  : $TARGET_DIR
  Web console   : http://$host_ip:3000
  API gateway   : http://$host_ip:8888
  Ingestion API : http://$host_ip:8080/api/v1/logs/ingest
  V1 syslog     : $host_ip:9080/tcp
  V2 MQTT       : tcp://$host_ip:1883
  Boot service  : systemctl status $UNIT_NAME
  Watchdog      : systemctl status $WATCHDOG_TIMER_NAME
  Diagnostics   : sudo tdsdiag
  Credentials   : sudo grep -E '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD|JWT_SECRET)=' $TARGET_DIR/.env
  Initial login : open the Web console and create the first administrator
  Purge preview : sudo $TARGET_DIR/install.sh --purge --dry-run
EOF
}

validate_target
if $PURGE; then remove_installation true; exit 0; fi
if $UNINSTALL; then remove_installation false; exit 0; fi

verify_release_manifest
validate_postgres_init_scripts
detect_existing_install

ensure_dependencies
verify_release_source_state
check_host_capacity
exec 9>/run/lock/threat-detection-install.lock
flock -n 9 || die "Another Threat Detection System install operation is running"
prepare_runtime_env

docker compose --env-file "$PREPARED_ENV" -f "$SRC_ROOT/docker/docker-compose.yml" config --quiet \
  || die "Docker Compose configuration validation failed"
ok "Docker Compose configuration validated"
validate_compose_ports

if ! $NO_START; then
  if ! prefetch_container_images; then
    if $DOCKER_PRESENT_AT_START; then
      print_existing_docker_mirror_guidance
    fi
    die "Container image prefetch failed; check Docker registry connectivity and rerun the installer"
  fi
fi

prepare_rollback
backup_existing_install
systemctl stop "$UNIT_NAME" >/dev/null 2>&1 || true
if [[ -f "$TARGET_DIR/.env" && -f "$TARGET_DIR/docker/docker-compose.yml" ]]; then
  compose_in "$TARGET_DIR" down --remove-orphans || warn "Previous Compose stack did not stop cleanly"
fi

sync_release_tree
systemctl daemon-reload
systemctl enable "$UNIT_NAME" >/dev/null
systemctl enable "$WATCHDOG_TIMER_NAME" >/dev/null

if $NO_START; then
  commit_install
  ok "Release $RELEASE_VERSION files verified and installed with --no-start; containers were not built or started"
  exit 0
fi

info "Building container images (the first installation can take a long time)..."
build_container_images
info "Starting the Docker Compose stack through systemd..."
systemctl start "$UNIT_NAME"
wait_for_service postgres 180
apply_database_upgrades
verify_runtime
systemctl start "$WATCHDOG_TIMER_NAME"

commit_install
print_summary
