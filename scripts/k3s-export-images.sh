#!/usr/bin/env bash
# =============================================================================
# 导出威胁检测系统所有容器镜像 (运行时 + 构建时)
# Export ALL images required by the threat-detection system from K3s containerd
# into a single tar archive for offline deployment.
#
# 用法 / Usage:
#   sudo bash scripts/k3s-export-images.sh                    # 默认路径
#   sudo bash scripts/k3s-export-images.sh /path/to/output    # 自定义路径
#   sudo bash scripts/k3s-export-images.sh --runtime-only      # 仅运行时镜像
#
# 输出 / Output: <output_dir>/threat-detection-images.tar.gz  (~4-5 GB)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNTIME_ONLY=false
OUTPUT_DIR="$PROJECT_ROOT"

for arg in "$@"; do
  case "$arg" in
    --runtime-only) RUNTIME_ONLY=true ;;
    *) OUTPUT_DIR="$arg" ;;
  esac
done

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

# ---------------------------------------------------------------------------
# 运行时镜像: K8s 部署直接使用的镜像
# ---------------------------------------------------------------------------
INFRA_IMAGES=(
  "postgres:15-alpine"
  "redis:7-alpine"
  "busybox:1.35"
  "confluentinc/cp-zookeeper:7.4.0"
  "confluentinc/cp-kafka:7.4.0"
  "docker.elastic.co/logstash/logstash:8.11.0"
  "emqx/emqx:5.5.1"
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

# ---------------------------------------------------------------------------
# 构建时基础镜像: Dockerfile FROM 引用的镜像 (用于从源码重新构建)
# ---------------------------------------------------------------------------
BUILD_IMAGES=(
  "maven:3.9-eclipse-temurin-21"
  "maven:3.9-eclipse-temurin-17"
  "eclipse-temurin:21-jre-alpine"
  "eclipse-temurin:21-jre"
  "flink:1.18.1-scala_2.12-java17"
  "python:3.11-slim"
  "python:3.12-slim"
  "node:20-alpine"
  "nginx:1.25-alpine"
)

if $RUNTIME_ONLY; then
  ALL_IMAGES=("${INFRA_IMAGES[@]}" "${APP_IMAGES[@]}")
  log "模式: 仅运行时镜像 (--runtime-only)"
else
  ALL_IMAGES=("${INFRA_IMAGES[@]}" "${APP_IMAGES[@]}" "${BUILD_IMAGES[@]}")
  log "模式: 运行时 + 构建时镜像 (完整离线包)"
fi

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

export_single_image() {
  local img="$1"
  local safe_name
  safe_name=$(echo "$img" | tr '/:' '__')
  local tar_file="${TMP_DIR}/${safe_name}.tar"

  # Try k3s ctr first (containerd native), then docker
  if command -v k3s &>/dev/null; then
    if k3s ctr images export "$tar_file" "docker.io/library/${img}" 2>/dev/null || \
       k3s ctr images export "$tar_file" "docker.io/${img}" 2>/dev/null || \
       k3s ctr images export "$tar_file" "${img}" 2>/dev/null; then
      ok "  ${img} ($(du -sh "$tar_file" | awk '{print $1}'))"
      return 0
    fi
  fi

  if command -v docker &>/dev/null; then
    if docker save "$img" -o "$tar_file" 2>/dev/null || \
       docker save "docker.io/library/$img" -o "$tar_file" 2>/dev/null; then
      ok "  ${img} ($(du -sh "$tar_file" | awk '{print $1}'))"
      return 0
    fi
  fi

  return 1
}

main() {
  mkdir -p "$TMP_DIR" "$OUTPUT_DIR"

  log "正在导出 ${#ALL_IMAGES[@]} 个镜像..."
  log "输出路径: ${OUTPUT_DIR}/${ARCHIVE_NAME}"
  echo ""

  local exported=0
  local failed=0
  local skipped_list=()

  for img in "${ALL_IMAGES[@]}"; do
    if export_single_image "$img"; then
      exported=$((exported + 1))
    else
      warn "  跳过: $img (本地不存在)"
      skipped_list+=("$img")
      failed=$((failed + 1))
    fi
  done

  echo ""
  log "正在压缩 ${exported} 个镜像..."
  tar czf "${OUTPUT_DIR}/${ARCHIVE_NAME}" -C "$TMP_DIR" .
  local size
  size=$(du -sh "${OUTPUT_DIR}/${ARCHIVE_NAME}" | awk '{print $1}')

  echo ""
  log "=========================================="
  ok "成功导出: ${exported}/${#ALL_IMAGES[@]} 个镜像"
  if [ $failed -gt 0 ]; then
    warn "跳过: ${failed} 个镜像 (本地不存在)"
    for img in "${skipped_list[@]}"; do
      warn "  - $img"
    done
    echo ""
    warn "跳过的镜像可在有网络的机器上拉取后重新导出:"
    warn "  docker pull <镜像名> && 重新运行本脚本"
  fi
  ok "归档文件: ${OUTPUT_DIR}/${ARCHIVE_NAME} (${size})"
  log "=========================================="
  echo ""
  log "部署步骤:"
  log "  1. 将 ${ARCHIVE_NAME} 和项目代码复制到目标机器"
  log "  2. 运行: sudo bash scripts/k3s-offline-deploy.sh"
  log "  (或分步执行: import → build → deploy)"
}

main
