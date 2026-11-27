#!/usr/bin/env bash
# Use the same Compose model for installation, systemd and runtime recovery.
set -euo pipefail

if (( $# < 3 )); then
  echo "Usage: compose-runtime.sh RELEASE_ROOT PRIMARY_ENV COMPOSE_ARGS..." >&2
  exit 2
fi

root="$1"
primary_env="$2"
shift 2
control_env="${JZ_CONTROL_ENV_FILE:-/etc/jz-control/control.env}"
if [[ "$control_env" != /* ]]; then
  control_env="$PWD/$control_env"
fi

cd -- "$root"
env_files=(--env-file "$primary_env")
compose_files=(-f docker/docker-compose.yml)
if [[ -f "$control_env" && -r "$control_env" && -f docker/docker-compose.control.yml ]]; then
  env_files+=(--env-file "$control_env")
  compose_files+=(-f docker/docker-compose.control.yml)
fi

exec docker compose "${env_files[@]}" "${compose_files[@]}" "$@"
