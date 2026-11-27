#!/usr/bin/env bash
# Read-only one-command diagnostics for threat-detection-system.

set -uo pipefail

TARGET_DIR="/opt/threat-detection-system"
OUTPUT=""
SELF_TEST=false

usage() {
  cat <<'EOF'
Usage: sudo tdsdiag [--self-test] [--output FILE]

Collects service, container, resource, port and public health status, plus
release-version metadata. From .env only RELEASE_VERSION is read; credentials,
database rows, private keys and application payload logs are not collected.
Exit codes: 0=collected/no critical failure, 2=collected/critical failure,
1=invalid arguments or collection failure.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --self-test) SELF_TEST=true; shift ;;
    --output) [[ $# -ge 2 ]] || { echo "[ERR] --output requires a file" >&2; exit 1; }; OUTPUT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[ERR] Unknown option: $1" >&2; exit 1 ;;
  esac
done

[[ $(id -u) -eq 0 ]] || { echo "[ERR] Root privileges are required" >&2; exit 1; }
for command in docker curl tar sha256sum ss python3 systemctl; do
  command -v "$command" >/dev/null 2>&1 || { echo "[ERR] Missing command: $command" >&2; exit 1; }
done
[[ -f "$TARGET_DIR/.env" && -f "$TARGET_DIR/docker/docker-compose.yml" ]] \
  || { echo "[ERR] Threat Detection System is not installed in $TARGET_DIR" >&2; exit 1; }

compose() {
  (cd "$TARGET_DIR" && docker compose --env-file .env -f docker/docker-compose.yml "$@")
}

critical=0

# Collect and validate release metadata before the self-test can return. Only
# allowlisted, validated version strings leave this boundary, including when the
# helper is missing, fails, or returns an invalid document.
version_metadata="$(python3 - "$TARGET_DIR" <<'PY'
import json
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])
keys = (
    "file_version", "installed_version", "environment_version",
    "template_version", "frontend_build_version",
)
version_pattern = re.compile(r"[0-9]+(?:\.[0-9]+)*(?:[+~-][0-9A-Za-z.-]+)?")
values = {key: None for key in keys}
consistency = "checker_missing"
check_rc = 1
helper = root / "scripts/verify_release_state.py"
if helper.is_file() and not helper.is_symlink():
    consistency = "checker_failed"
    try:
        result = subprocess.run(
            [sys.executable, str(helper), "versions", str(root)],
            capture_output=True, text=True, timeout=15, check=False,
        )
        check_rc = result.returncode
        document = json.loads(result.stdout)
        if not isinstance(document, dict):
            raise ValueError("invalid document")
        status = document.get("version_consistency")
        if status not in {"consistent", "legacy", "unknown", "invalid", "mismatch"}:
            raise ValueError("invalid status")
        candidate = {key: document.get(key) for key in keys}
        if any(
            value is not None
            and (not isinstance(value, str) or not version_pattern.fullmatch(value))
            for value in candidate.values()
        ):
            raise ValueError("invalid version")
        if status in {"consistent", "legacy"}:
            if not candidate["file_version"] or not candidate["environment_version"]:
                raise ValueError("incomplete version metadata")
            present = [value for value in candidate.values() if value is not None]
            if len(set(present)) != 1:
                raise ValueError("contradictory version metadata")
            if status == "consistent" and not candidate["installed_version"]:
                raise ValueError("installation marker missing")
        values = candidate
        consistency = status
    except (OSError, ValueError, subprocess.SubprocessError):
        check_rc = 1

if values["file_version"] is None:
    marker = root / "VERSION"
    try:
        if marker.is_file() and not marker.is_symlink():
            candidate = marker.read_text(encoding="utf-8").strip()
            if version_pattern.fullmatch(candidate):
                values["file_version"] = candidate
    except (OSError, UnicodeError):
        pass

for key in keys:
    print(f"{key}={values[key] or 'unknown'}")
print(f"version_consistency={consistency}")
print(f"version_check_rc={check_rc}")
PY
)"
file_version="unknown"
installed_version="unknown"
environment_version="unknown"
template_version="unknown"
frontend_build_version="unknown"
version_consistency="checker_failed"
version_check_rc=1
while IFS='=' read -r key value; do
  case "$key" in
    file_version) file_version="$value" ;;
    installed_version) installed_version="$value" ;;
    environment_version) environment_version="$value" ;;
    template_version) template_version="$value" ;;
    frontend_build_version) frontend_build_version="$value" ;;
    version_consistency) version_consistency="$value" ;;
    version_check_rc) version_check_rc="$value" ;;
  esac
done <<<"$version_metadata"
if [[ "$version_check_rc" != 0 || ( "$version_consistency" != consistent && "$version_consistency" != legacy ) ]]; then
  critical=1
elif [[ "$version_consistency" == legacy ]]; then
  echo "[WARN] Legacy installation has no installation marker; available release metadata agrees" >&2
fi

unit_state="$(systemctl is-active threat-detection-compose.service 2>/dev/null || true)"
unit_enabled="$(systemctl is-enabled threat-detection-compose.service 2>/dev/null || true)"
[[ "$unit_state" == active ]] || critical=1
[[ "$unit_enabled" == enabled ]] || critical=1

running_count="$(compose ps --status running -q 2>/dev/null | sed '/^$/d' | wc -l | tr -d ' ')"
[[ "$running_count" =~ ^[0-9]+$ && "$running_count" -ge 18 ]] || critical=1

runtime_consistency="unknown"
runtime_check_rc=0
runtime_check_output=""
if [[ -x "$TARGET_DIR/scripts/runtime-watchdog.sh" ]]; then
  runtime_check_output="$("$TARGET_DIR/scripts/runtime-watchdog.sh" --check 2>&1)" \
    || runtime_check_rc=$?
  if [[ $runtime_check_rc -eq 0 ]]; then
    runtime_consistency="ok"
  else
    runtime_consistency="failed"
    critical=1
  fi
else
  runtime_consistency="checker_missing"
  runtime_check_rc=1
  runtime_check_output="[FAIL] Runtime consistency checker is missing"
  critical=1
fi

topic_id="$(compose ps -aq topic-init 2>/dev/null || true)"
topic_state="missing"
topic_exit="unknown"
if [[ -n "$topic_id" ]]; then
  topic_state="$(docker inspect -f '{{.State.Status}}' "$topic_id" 2>/dev/null || echo unknown)"
  topic_exit="$(docker inspect -f '{{.State.ExitCode}}' "$topic_id" 2>/dev/null || echo unknown)"
fi
[[ "$topic_state" == exited && "$topic_exit" == 0 ]] || critical=1

ingestion_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 8 http://127.0.0.1:8080/actuator/health 2>/dev/null || true)"
gateway_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 8 http://127.0.0.1:8888/actuator/health 2>/dev/null || true)"
frontend_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 8 http://127.0.0.1:3000/ 2>/dev/null || true)"
readiness_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 12 http://127.0.0.1:8888/api/v1/system/readiness 2>/dev/null || true)"
bootstrap_http="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 8 http://127.0.0.1:8888/api/v1/auth/bootstrap/status 2>/dev/null || true)"
v1_syslog_tcp="failed"
logstash_id="$(compose ps -q logstash 2>/dev/null || true)"
logstash_ip=""
if [[ -n "$logstash_id" ]]; then
  logstash_ip="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' \
    "$logstash_id" 2>/dev/null | sed -n '/./{p;q}')"
fi
if [[ -n "$logstash_ip" ]] \
  && timeout 5 bash -c 'exec 3<>"/dev/tcp/$1/9080"' _ "$logstash_ip" 2>/dev/null; then
  v1_syslog_tcp="ok"
fi
[[ "$ingestion_http" == 200 ]] || critical=1
[[ "$gateway_http" == 200 ]] || critical=1
[[ "$frontend_http" == 200 ]] || critical=1
[[ "$readiness_http" == 200 ]] || critical=1
[[ "$bootstrap_http" == 200 ]] || critical=1
[[ "$v1_syslog_tcp" == ok ]] || critical=1

kafka_partitions="$(compose exec -T kafka kafka-topics --bootstrap-server kafka:29092 \
  --describe --topic attack-events 2>/dev/null \
  | sed -n 's/.*PartitionCount: \([0-9][0-9]*\).*/\1/p' | head -n1 || true)"
[[ "$kafka_partitions" =~ ^[0-9]+$ && "$kafka_partitions" -ge 1 ]] || critical=1

restart_summary="$(
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    docker inspect -f '{{.Name}}={{.RestartCount}}' "$id" 2>/dev/null || true
  done < <(compose ps -aq 2>/dev/null | sort)
  )"

if $SELF_TEST; then
  if [[ $critical -eq 0 ]]; then
    echo "[ OK ] tdsdiag self-test passed"
    exit 0
  fi
  [[ "$runtime_consistency" == ok ]] || printf '%s\n' "$runtime_check_output" >&2
  [[ "$v1_syslog_tcp" == ok ]] \
    || echo "[ERR] V1 Syslog TCP listener is unavailable inside the Logstash container" >&2
  [[ "$version_check_rc" == 0 && ( "$version_consistency" == consistent || "$version_consistency" == legacy ) ]] \
    || printf '[ERR] Release version metadata is inconsistent or unavailable (status=%s, rc=%s)\n' \
      "$version_consistency" "$version_check_rc" >&2
  echo "[ERR] tdsdiag self-test found a critical failure" >&2
  exit 2
fi

work_dir="$(mktemp -d /tmp/tdsdiag.XXXXXX)" || exit 1
trap 'rm -rf -- "$work_dir"' EXIT
chmod 700 "$work_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
[[ -n "$OUTPUT" ]] || OUTPUT="/tmp/tdsdiag-${timestamp}.tar.gz"

version="$file_version"
cat >"$work_dir/diag-summary.txt" <<EOF
captured_at=${timestamp}
version=${version}
file_version=${file_version}
installed_version=${installed_version}
environment_version=${environment_version}
template_version=${template_version}
frontend_build_version=${frontend_build_version}
version_consistency=${version_consistency}
version_check_rc=${version_check_rc}
unit_active=${unit_state}
unit_enabled=${unit_enabled}
running_containers=${running_count}
runtime_consistency=${runtime_consistency}
runtime_check_rc=${runtime_check_rc}
topic_init_state=${topic_state}
topic_init_exit=${topic_exit}
data_ingestion_http=${ingestion_http:-000}
api_gateway_http=${gateway_http:-000}
frontend_http=${frontend_http:-000}
business_readiness_http=${readiness_http:-000}
bootstrap_status_http=${bootstrap_http:-000}
v1_syslog_tcp=${v1_syslog_tcp}
logstash_container_ip=${logstash_ip:-unknown}
attack_events_partitions=${kafka_partitions:-0}
critical_failure=${critical}
EOF

printf '%s\n' "$restart_summary" >"$work_dir/container-restarts.txt"
printf '%s\n' "$runtime_check_output" >"$work_dir/container-runtime-consistency.txt"
{
  printf '===== docker-reported runtime =====\n'
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    docker inspect -f '{{.Name}} id={{.Id}} status={{.State.Status}} pid={{.State.Pid}} restart={{.RestartCount}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null || true
  done < <(compose ps -aq 2>/dev/null | sort)
  printf '\n===== containerd tasks =====\n'
  if command -v ctr >/dev/null 2>&1; then
    ctr -n moby tasks list 2>&1 || true
  else
    printf 'ctr is not installed; PID and /proc checks were used\n'
  fi
} >"$work_dir/container-runtime.txt"

python3 - "$work_dir/diag-summary.txt" "$work_dir/diag-summary.json" <<'PY'
import json, sys
src, dst = sys.argv[1:]
data = {}
for line in open(src, encoding="utf-8"):
    key, value = line.rstrip("\n").split("=", 1)
    data[key] = value
with open(dst, "w", encoding="utf-8") as handle:
    json.dump(data, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
PY

compose ps -a --no-trunc >"$work_dir/compose-ps.txt" 2>&1 || true
systemctl status threat-detection-compose.service --no-pager >"$work_dir/systemd.txt" 2>&1 || true
{
  uname -a
  uptime
  free -h
  df -h
  df -ih
} >"$work_dir/system.txt" 2>&1
ss -lntup >"$work_dir/listening-ports.txt" 2>&1 || true
(cd "$work_dir" && sha256sum diag-summary.txt diag-summary.json container-restarts.txt \
  container-runtime-consistency.txt container-runtime.txt compose-ps.txt systemd.txt \
  system.txt listening-ports.txt >SHA256SUMS)

mkdir -p "$(dirname "$OUTPUT")"
tar -C "$work_dir" -czf "$OUTPUT" . || exit 1
chmod 600 "$OUTPUT"
echo "[ OK ] Diagnostic package: $OUTPUT (0600)"
[[ $critical -eq 0 ]] && exit 0
exit 2
