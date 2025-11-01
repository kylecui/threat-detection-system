#!/bin/bash
################################################################################
# 完整重启脚本
# 用于代码修改后完全重新构建和部署系统
#
# 用法: ./scripts/tools/full_restart.sh [service_name]
#   不带参数: 重启所有服务
#   带参数: 只重启指定服务 (例如: data-ingestion)
################################################################################

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="/home/kylecui/threat-detection-system"
SERVICE_NAME=${1:-"all"}

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}         完整重启流程 - Cloud-Native Threat Detection      ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}\n"

# 步骤1: Maven编译
echo -e "${YELLOW}🔨 步骤 1/6: Maven编译最新代码...${NC}"
cd "$PROJECT_ROOT"
mvn clean package -DskipTests
echo -e "${GREEN}✅ Maven编译完成${NC}\n"

# 步骤2: 重新构建Docker镜像
echo -e "${YELLOW}🐳 步骤 2/6: 重新构建Docker镜像...${NC}"
cd "$PROJECT_ROOT/docker"

if [ "$SERVICE_NAME" == "all" ]; then
    echo "   构建所有服务..."
    docker compose build --no-cache
else
    echo "   构建服务: $SERVICE_NAME"
    docker compose build "$SERVICE_NAME" --no-cache
fi
echo -e "${GREEN}✅ Docker镜像构建完成${NC}\n"

# 步骤3: 停止现有容器
echo -e "${YELLOW}🛑 步骤 3/6: 停止现有容器...${NC}"
if [ "$SERVICE_NAME" == "all" ]; then
    docker compose down
else
    docker compose stop "$SERVICE_NAME"
fi
echo -e "${GREEN}✅ 容器已停止${NC}\n"

# 步骤4: 启动服务
echo -e "${YELLOW}🚀 步骤 4/6: 启动服务...${NC}"
docker compose up -d
echo -e "${GREEN}✅ 服务已启动${NC}\n"

# 步骤5: 等待服务就绪
echo -e "${YELLOW}⏳ 步骤 5/6: 等待服务就绪 (15秒)...${NC}"
sleep 15
echo -e "${GREEN}✅ 等待完成${NC}\n"

# 步骤6: 验证服务状态
echo -e "${YELLOW}✅ 步骤 6/6: 验证服务状态...${NC}"
docker compose ps
echo ""

# 显示关键服务日志
if [ "$SERVICE_NAME" == "all" ] || [ "$SERVICE_NAME" == "data-ingestion" ]; then
    echo -e "${BLUE}━━━ Data Ingestion 日志 (最近20行) ━━━${NC}"
    docker logs data-ingestion-service --tail 20
    echo ""
fi

if [ "$SERVICE_NAME" == "all" ] || [ "$SERVICE_NAME" == "stream-processing" ]; then
    echo -e "${BLUE}━━━ Stream Processing 日志 (最近20行) ━━━${NC}"
    docker logs stream-processing --tail 20
    echo ""
fi

# 完成
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}         ✅ 重启完成! 系统已就绪                           ${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}\n"

echo -e "${BLUE}💡 后续操作建议:${NC}"
echo -e "   1. 查看实时日志: ${YELLOW}docker logs -f data-ingestion-service${NC}"
echo -e "   2. 运行E2E测试: ${YELLOW}python3 scripts/test/e2e_mvp_test.py${NC}"
echo -e "   3. 检查数据库:   ${YELLOW}docker exec postgres psql -U threat_user -d threat_detection${NC}"
echo ""
