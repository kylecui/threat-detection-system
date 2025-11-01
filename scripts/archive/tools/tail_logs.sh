#!/bin/bash
################################################################################
# 多服务日志查看脚本
# 同时查看多个服务的实时日志
#
# 用法: ./scripts/tools/tail_logs.sh [service1] [service2] ...
#   不带参数: 查看所有核心服务日志
#   带参数: 查看指定服务日志
################################################################################

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认服务列表
DEFAULT_SERVICES=("data-ingestion-service" "stream-processing" "kafka" "postgres")

# 获取要查看的服务
if [ $# -eq 0 ]; then
    SERVICES=("${DEFAULT_SERVICES[@]}")
else
    SERVICES=("$@")
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}         实时日志查看 - ${#SERVICES[@]} 个服务                    ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}\n"

echo -e "${YELLOW}正在查看以下服务的日志:${NC}"
for service in "${SERVICES[@]}"; do
    echo -e "   • ${GREEN}$service${NC}"
done
echo ""

echo -e "${YELLOW}提示: 按 Ctrl+C 停止查看${NC}\n"

# 等待2秒让用户看清提示
sleep 2

# 使用docker-compose logs查看多个服务
cd /home/kylecui/threat-detection-system/docker

# 构建服务名列表
service_args=""
for service in "${SERVICES[@]}"; do
    service_args="$service_args $service"
done

# 实时查看日志
docker compose logs -f --tail=50 $service_args
