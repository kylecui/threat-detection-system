#!/usr/bin/env bash
# =============================================================================
# 威胁检测系统 — 一键离线部署脚本
# Threat Detection System — One-shot Offline Deploy
#
# 将镜像包 + 源码部署到一台干净的 K3s 机器上，全程无需外网访问。
#
# 前置条件:
#   1. K3s 已安装 (curl -sfL https://rancher.cn/k3s/k3s-install.sh | sh -)
#   2. Docker 已安装 (用于从源码构建镜像)
#   3. 项目代码已 clone 或拷贝到当前目录
#   4. 镜像包 threat-detection-images.tar.gz 已放在项目根目录或指定路径
#
# 用法:
#   sudo bash scripts/k3s-offline-deploy.sh                                # 使用预构建镜像直接部署
#   sudo bash scripts/k3s-offline-deploy.sh --rebuild                      # 从源码重新构建应用镜像后部署
#   sudo bash scripts/k3s-offline-deploy.sh --archive /path/to/images.tar.gz  # 指定镜像包路径
#   sudo bash scripts/k3s-offline-deploy.sh --skip-import                  # 跳过镜像导入 (已有镜像)
#
# 流程:
#   Step 1: 导入镜像到 K3s containerd
#   Step 2: (可选) 从源码重新构建应用镜像
#   Step 3: 执行 K8s 部署
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
ARCHIVE_PATH="${PROJECT_ROOT}/threat-detection-images.tar.gz"
REBUILD=false
SKIP_IMPORT=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rebuild)      REBUILD=true; shift ;;
    --skip-import)  SKIP_IMPORT=true; shift ;;
    --archive)      ARCHIVE_PATH="$2"; shift 2 ;;
    -h|--help)
      echo "用法: sudo bash $0 [选项]"
      echo ""
      echo "选项:"
      echo "  --archive <路径>   指定镜像包路径 (默认: 项目根目录/threat-detection-images.tar.gz)"
      echo "  --rebuild          从源码重新构建应用镜像 (需要 Docker)"
      echo "  --skip-import      跳过镜像导入 (镜像已存在于 K3s 中)"
      echo "  -h, --help         显示帮助信息"
      exit 0
      ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log()     { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()      { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn()    { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()     { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }
header()  { echo -e "\n${BOLD}${CYAN}$*${NC}"; }

# =============================================================================
# 前置检查
# =============================================================================
header "============================================"
header "  威胁检测系统 — 离线一键部署"
header "============================================"
echo ""

# Check K3s
if ! command -v k3s &>/dev/null; then
  err "K3s 未安装!"
  log "安装命令 (国内源):"
  log "  curl -sfL https://rancher.cn/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn sh -"
  exit 1
fi
ok "K3s 已安装"

# Check kubectl works
if ! kubectl get nodes &>/dev/null; then
  err "kubectl 无法连接到集群，请确认 K3s 正在运行"
  log "  systemctl status k3s"
  exit 1
fi
ok "K3s 集群可用"

# Check Docker (needed for rebuild)
if $REBUILD; then
  if ! command -v docker &>/dev/null; then
    err "--rebuild 需要 Docker，但未检测到 Docker"
    exit 1
  fi
  ok "Docker 已安装 (用于源码构建)"
fi

# Check archive exists (unless skipping import)
if ! $SKIP_IMPORT; then
  if [ ! -f "$ARCHIVE_PATH" ]; then
    err "镜像包不存在: $ARCHIVE_PATH"
    log ""
    log "请确认以下步骤之一:"
    log "  1. 将 threat-detection-images.tar.gz 拷贝到项目根目录"
    log "  2. 使用 --archive <路径> 指定镜像包位置"
    log "  3. 使用 --skip-import 跳过导入 (如果镜像已在 K3s 中)"
    exit 1
  fi
  local_size=$(du -sh "$ARCHIVE_PATH" | awk '{print $1}')
  ok "镜像包: $ARCHIVE_PATH ($local_size)"
fi

# Check disk space
AVAIL_GB=$(df -BG "$PROJECT_ROOT" | awk 'NR==2{gsub(/G/,"",$4); print $4}')
if [ "$AVAIL_GB" -lt 10 ]; then
  warn "磁盘剩余空间不足: ${AVAIL_GB}G (建议 > 10G)"
  warn "镜像导入和构建需要大量临时空间"
fi

echo ""
log "部署配置:"
log "  镜像包: $(basename "$ARCHIVE_PATH")"
log "  源码构建: $($REBUILD && echo '是' || echo '否')"
log "  跳过导入: $($SKIP_IMPORT && echo '是' || echo '否')"
echo ""

# =============================================================================
# Step 1: 导入镜像
# =============================================================================
if ! $SKIP_IMPORT; then
  header "Step 1/$(($REBUILD ? 3 : 2)): 导入镜像到 K3s containerd"
  log "执行: bash scripts/k3s-import-images.sh $ARCHIVE_PATH"
  echo ""

  if bash "${SCRIPT_DIR}/k3s-import-images.sh" "$ARCHIVE_PATH"; then
    ok "镜像导入完成"
  else
    err "镜像导入失败!"
    exit 1
  fi
else
  log "跳过镜像导入 (--skip-import)"
fi

# =============================================================================
# Step 2: (可选) 从源码重新构建应用镜像
# =============================================================================
if $REBUILD; then
  header "Step 2/3: 从源码构建应用镜像"
  log "执行: bash scripts/k3s-build-images.sh"
  log "(构建时基础镜像已从镜像包导入，无需联网)"
  echo ""

  if bash "${SCRIPT_DIR}/k3s-build-images.sh"; then
    ok "应用镜像构建完成"
  else
    err "部分镜像构建失败，请检查上方错误信息"
    warn "继续尝试部署..."
  fi
fi

# =============================================================================
# Step 3: K8s 部署
# =============================================================================
if $REBUILD; then
  header "Step 3/3: 部署到 K8s"
else
  header "Step $($SKIP_IMPORT && echo '1/1' || echo '2/2'): 部署到 K8s"
fi

log "执行: bash scripts/k3s-deploy.sh"
echo ""

if bash "${SCRIPT_DIR}/k3s-deploy.sh"; then
  ok "K8s 部署完成"
else
  err "K8s 部署过程中有错误，请检查上方输出"
  exit 1
fi

# =============================================================================
# 完成
# =============================================================================
echo ""
header "============================================"
ok "部署完成!"
header "============================================"
echo ""
log "验证命令:"
log "  sudo kubectl get pods -n threat-detection"
echo ""
log "访问地址:"
log "  Web 界面: http://<本机IP>:30080"
log "  API 网关: http://<本机IP>:30080/api/"
echo ""
log "登录账号:"
log "  管理员: admin / admin123"
log "  演示用户: demo_admin / admin123"
echo ""
log "测试命令 (V1 syslog):"
log '  TIMESTAMP=$(date +%s) && echo "syslog_version=1.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=4,attack_mac=aa:bb:cc:dd:ee:10,attack_ip=192.168.1.50,response_ip=10.0.1.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=${TIMESTAMP}" | nc -q1 localhost 32318'
echo ""
log "如需重新构建单个服务:"
log "  sudo bash scripts/k3s-build-images.sh <服务名>"
log "  sudo kubectl rollout restart deployment/<服务名> -n threat-detection"
