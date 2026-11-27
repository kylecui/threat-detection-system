#!/usr/bin/env bash
# Detect and optionally repair Docker/containerd state desynchronization for
# the Threat Detection System Compose project. Named volumes are never removed.

set -uo pipefail

TARGET_DIR="/opt/threat-detection-system"
MODE="check"
INSTALL_LOCK="/run/lock/threat-detection-install.lock"
WATCHDOG_LOCK="/run/lock/threat-detection-runtime-watchdog.lock"

usage() {
  cat <<'EOF'
Usage: runtime-watchdog.sh [--check|--recover]

  --check    Report containers that Docker marks running although their host
             PID or containerd task is no longer running. No changes are made.
  --recover  Recreate only inconsistent Compose services. Named volumes and
             application data are preserved.

Exit codes: 0=no inconsistency (or recovery succeeded), 2=inconsistency found,
1=invalid environment or recovery failed.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) MODE="check"; shift ;;
    --recover) MODE="recover"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[ERR] Unknown option: $1" >&2; exit 1 ;;
  esac
done

[[ $(id -u) -eq 0 ]] || { echo "[ERR] Root privileges are required" >&2; exit 1; }
for command in docker flock; do
  command -v "$command" >/dev/null 2>&1 \
    || { echo "[ERR] Missing command: $command" >&2; exit 1; }
done
[[ -f "$TARGET_DIR/.env" && -f "$TARGET_DIR/docker/docker-compose.yml" ]] \
  || { echo "[ERR] Threat Detection System is not installed in $TARGET_DIR" >&2; exit 1; }

compose() {
  (cd "$TARGET_DIR" && docker compose --env-file .env -f docker/docker-compose.yml "$@")
}

containerd_task_state() {
  local id="$1"
  command -v ctr >/dev/null 2>&1 || return 0
  # Consume the complete task list. Exiting awk on the first match closes the
  # pipe while ctr may still be writing and produces SIGPIPE (rc=141) under
  # pipefail on hosts with multiple containers.
  ctr -n moby tasks list 2>/dev/null \
    | awk -v wanted="$id" 'NR > 1 && $1 == wanted {state=$3} END {if (state != "") print state}'
}

runtime_reason() {
  local id="$1" state pid task_state
  state="$(docker inspect -f '{{.State.Status}}' "$id" 2>/dev/null || true)"
  [[ "$state" == "running" ]] || return 1

  pid="$(docker inspect -f '{{.State.Pid}}' "$id" 2>/dev/null || true)"
  if [[ ! "$pid" =~ ^[1-9][0-9]*$ || ! -d "/proc/$pid" ]]; then
    printf 'docker=running pid=%s proc=missing' "${pid:-unknown}"
    return 0
  fi

  if command -v ctr >/dev/null 2>&1; then
    task_state="$(containerd_task_state "$id")"
    if [[ -z "$task_state" ]]; then
      printf 'docker=running pid=%s containerd=MISSING' "$pid"
      return 0
    fi
    if [[ "$task_state" != "RUNNING" ]]; then
      printf 'docker=running pid=%s containerd=%s' "$pid" "$task_state"
      return 0
    fi
  fi
  return 1
}

recover_service() {
  local service="$1" deadline id reason
  echo "[WARN] Recreating inconsistent service: $service" >&2
  if ! (
    cd "$TARGET_DIR"
    timeout 300 docker compose --env-file .env -f docker/docker-compose.yml \
      up -d --no-deps --force-recreate "$service"
  ); then
    echo "[ERR] Failed to recreate service: $service" >&2
    return 1
  fi

  deadline=$((SECONDS + 90))
  while (( SECONDS < deadline )); do
    id="$(compose ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$id" ]] && ! reason="$(runtime_reason "$id")"; then
      echo "[ OK ] Runtime state restored: $service"
      return 0
    fi
    sleep 2
  done
  echo "[ERR] Recreated service did not obtain a real running task: $service" >&2
  return 1
}

exec 8>"$WATCHDOG_LOCK"
if ! flock -n 8; then
  echo "[INFO] Runtime watchdog is already running; skipping"
  exit 0
fi

if [[ "$MODE" == "recover" ]]; then
  exec 9>"$INSTALL_LOCK"
  if ! flock -n 9; then
    echo "[INFO] Installation or upgrade is active; recovery skipped"
    exit 0
  fi
fi

declare -a inconsistent_services=()
while IFS= read -r service; do
  [[ -n "$service" ]] || continue
  id="$(compose ps -aq "$service" 2>/dev/null | head -n 1)"
  [[ -n "$id" ]] || continue
  if reason="$(runtime_reason "$id")"; then
    echo "[FAIL] $service: $reason" >&2
    inconsistent_services+=("$service")
  fi
done < <(compose config --services 2>/dev/null)

if (( ${#inconsistent_services[@]} == 0 )); then
  echo "[ OK ] Docker/containerd runtime state is consistent"
  exit 0
fi

if [[ "$MODE" == "check" ]]; then
  exit 2
fi

failed=0
for service in "${inconsistent_services[@]}"; do
  recover_service "$service" || failed=1
done

(( failed == 0 )) || exit 1
exit 0
