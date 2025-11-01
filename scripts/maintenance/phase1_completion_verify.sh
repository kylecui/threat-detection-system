#!/bin/bash

###############################################################################
# Phase 1 完成验证脚本
# 功能: 容器重建 + 快乐路径测试
# 用法: bash scripts/phase1_completion_verify.sh
###############################################################################

set -e  # 任何错误立即退出

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}Phase 1 完成验证${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""

# 1. 清理系统
echo -e "${YELLOW}[1/5] 清理Docker缓存...${NC}"
cd docker
docker system prune -f --volumes > /dev/null 2>&1
echo -e "${GREEN}✓ Docker缓存已清理${NC}"

# 2. 构建镜像 (无缓存)
echo ""
echo -e "${YELLOW}[2/5] 重建Alert Management容器 (无缓存)...${NC}"
timeout 300 docker-compose build --no-cache alert-management || {
    echo -e "${RED}✗ 构建失败${NC}"
    exit 1
}
echo -e "${GREEN}✓ 容器已成功构建${NC}"

# 3. 启动服务
echo ""
echo -e "${YELLOW}[3/5] 启动Alert Management服务...${NC}"
docker-compose up -d alert-management
echo -e "${GREEN}✓ 服务已启动${NC}"

# 4. 等待服务就绪
echo ""
echo -e "${YELLOW}[4/5] 等待服务就绪 (最多30秒)...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 服务已就绪${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}✗ 服务启动超时${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done

# 5. 运行快乐路径测试
echo ""
echo -e "${YELLOW}[5/5] 运行快乐路径测试...${NC}"
cd /home/kylecui/threat-detection-system
timeout 120 bash scripts/test_backend_api_happy_path.sh

echo ""
echo -e "${GREEN}=================================${NC}"
echo -e "${GREEN}✓ Phase 1 完成验证成功！${NC}"
echo -e "${GREEN}=================================${NC}"
echo ""
echo "结果摘要:"
echo "  • Docker缓存: 已清理"
echo "  • 容器构建: 成功"
echo "  • 服务启动: 成功"  
echo "  • 快乐路径: 已执行"
echo ""
echo "下一步:"
echo "  1. 审查快乐路径测试结果"
echo "  2. 确认Alert Management部分通过"
echo "  3. 准备Phase 2: DTO模式实现"
