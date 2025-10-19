#!/bin/bash

# API Gateway 一键部署脚本

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=========================================="
echo "API Gateway 一键部署"
echo "=========================================="
echo ""

# 1. 构建Maven项目
echo -e "${YELLOW}[1/5] 构建Maven项目...${NC}"
cd /home/kylecui/threat-detection-system/services/api-gateway
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Maven构建成功${NC}"
else
    echo -e "${RED}❌ Maven构建失败${NC}"
    exit 1
fi
echo ""

# 2. 构建Docker镜像
echo -e "${YELLOW}[2/5] 构建Docker镜像...${NC}"
docker build -t threat-detection/api-gateway:latest .
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Docker镜像构建成功${NC}"
else
    echo -e "${RED}❌ Docker镜像构建失败${NC}"
    exit 1
fi
echo ""

# 3. 启动依赖服务
echo -e "${YELLOW}[3/5] 启动依赖服务...${NC}"
cd /home/kylecui/threat-detection-system/docker
docker-compose up -d redis customer-management data-ingestion threat-assessment alert-management
echo "等待服务启动（30秒）..."
sleep 30
echo -e "${GREEN}✅ 依赖服务已启动${NC}"
echo ""

# 4. 启动API Gateway
echo -e "${YELLOW}[4/5] 启动API Gateway...${NC}"
docker-compose up -d api-gateway
echo "等待Gateway启动（20秒）..."
sleep 20
echo ""

# 5. 健康检查
echo -e "${YELLOW}[5/5] 健康检查...${NC}"
response=$(curl -s -w "\n%{http_code}" http://localhost:8888/actuator/health)
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ API Gateway健康检查通过${NC}"
    echo "Response: $body"
else
    echo -e "${RED}❌ API Gateway健康检查失败 (HTTP $http_code)${NC}"
    echo "查看日志: docker-compose logs api-gateway"
    exit 1
fi
echo ""

# 总结
echo "=========================================="
echo -e "${GREEN}🎉 部署完成！${NC}"
echo "=========================================="
echo ""
echo "Gateway地址: http://localhost:8888"
echo ""
echo "快速测试:"
echo "  curl http://localhost:8888/actuator/health"
echo "  curl http://localhost:8888/api/v1/customers"
echo ""
echo "查看日志:"
echo "  docker-compose logs -f api-gateway"
echo ""
echo "运行完整测试:"
echo "  ./scripts/test_api_gateway.sh"
echo ""
