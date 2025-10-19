#!/bin/bash

# API Gateway 测试脚本
# 测试所有路由和功能

set -e

GATEWAY_URL="http://localhost:8888"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "API Gateway 功能测试"
echo "Gateway URL: $GATEWAY_URL"
echo "=========================================="
echo ""

# 1. 测试健康检查
echo -e "${YELLOW}[1/8] 测试健康检查${NC}"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/actuator/health")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ 健康检查通过${NC}"
    echo "Response: $body"
else
    echo -e "${RED}❌ 健康检查失败 (HTTP $http_code)${NC}"
    exit 1
fi
echo ""

# 2. 测试Customer Management路由
echo -e "${YELLOW}[2/8] 测试Customer Management路由${NC}"
echo "创建测试客户..."
response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/api/v1/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "test-customer-001",
    "companyName": "测试公司",
    "contactName": "张三",
    "contactEmail": "test@example.com",
    "subscriptionTier": "PROFESSIONAL"
  }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "201" ] || [ "$http_code" = "409" ]; then
    echo -e "${GREEN}✅ Customer Management路由正常${NC}"
    echo "Response: $body" | head -c 200
else
    echo -e "${RED}❌ Customer Management路由失败 (HTTP $http_code)${NC}"
    echo "Response: $body"
fi
echo ""

# 3. 测试查询客户
echo -e "${YELLOW}[3/8] 测试查询客户${NC}"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/customers/test-customer-001")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ 查询客户成功${NC}"
    echo "Response: $body" | head -c 200
else
    echo -e "${RED}❌ 查询客户失败 (HTTP $http_code)${NC}"
fi
echo ""

# 4. 测试Data Ingestion路由
echo -e "${YELLOW}[4/8] 测试Data Ingestion路由${NC}"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/logs/stats")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ Data Ingestion路由正常${NC}"
    echo "Response: $body" | head -c 200
else
    echo -e "${YELLOW}⚠️  Data Ingestion可能未启动 (HTTP $http_code)${NC}"
fi
echo ""

# 5. 测试Threat Assessment路由
echo -e "${YELLOW}[5/8] 测试Threat Assessment路由${NC}"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/assessment/health")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ Threat Assessment路由正常${NC}"
    echo "Response: $body" | head -c 200
else
    echo -e "${YELLOW}⚠️  Threat Assessment可能未启动 (HTTP $http_code)${NC}"
fi
echo ""

# 6. 测试Alert Management路由
echo -e "${YELLOW}[6/8] 测试Alert Management路由${NC}"
response=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/v1/alerts?page=0&size=10")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ Alert Management路由正常${NC}"
    echo "Response: $body" | head -c 200
else
    echo -e "${YELLOW}⚠️  Alert Management可能未启动 (HTTP $http_code)${NC}"
fi
echo ""

# 7. 测试CORS
echo -e "${YELLOW}[7/8] 测试CORS配置${NC}"
response=$(curl -s -w "\n%{http_code}" -X OPTIONS "$GATEWAY_URL/api/v1/customers" \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -I)
http_code=$(echo "$response" | tail -n1)

if echo "$response" | grep -q "access-control-allow-origin"; then
    echo -e "${GREEN}✅ CORS配置正常${NC}"
else
    echo -e "${YELLOW}⚠️  CORS可能未正确配置${NC}"
fi
echo ""

# 8. 测试限流（快速发送20个请求）
echo -e "${YELLOW}[8/8] 测试限流功能${NC}"
echo "快速发送20个请求..."
success_count=0
rate_limited_count=0

for i in {1..20}; do
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/customers")
    if [ "$http_code" = "200" ]; then
        ((success_count++))
    elif [ "$http_code" = "429" ]; then
        ((rate_limited_count++))
    fi
done

echo "成功: $success_count, 限流: $rate_limited_count"
if [ $rate_limited_count -gt 0 ]; then
    echo -e "${GREEN}✅ 限流功能正常工作${NC}"
else
    echo -e "${YELLOW}⚠️  未触发限流（可能需要发送更多请求）${NC}"
fi
echo ""

# 总结
echo "=========================================="
echo -e "${GREEN}测试完成！${NC}"
echo "=========================================="
echo ""
echo "Gateway监控地址:"
echo "  - Health: $GATEWAY_URL/actuator/health"
echo "  - Metrics: $GATEWAY_URL/actuator/metrics"
echo "  - Prometheus: $GATEWAY_URL/actuator/prometheus"
echo "  - Gateway Routes: $GATEWAY_URL/actuator/gateway/routes"
echo ""
echo "服务端点:"
echo "  - Customer Management: $GATEWAY_URL/api/v1/customers"
echo "  - Device Management: $GATEWAY_URL/api/v1/devices"
echo "  - Data Ingestion: $GATEWAY_URL/api/v1/logs"
echo "  - Threat Assessment: $GATEWAY_URL/api/v1/assessment"
echo "  - Alert Management: $GATEWAY_URL/api/v1/alerts"
