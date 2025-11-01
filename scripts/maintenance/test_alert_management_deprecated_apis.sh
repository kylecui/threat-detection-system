#!/bin/bash

# Alert-Management 废弃API测试脚本
# 验证客户通知配置管理API已正确返回403 Forbidden

BASE_URL="http://localhost:8082"
CUSTOMER_ID="test-customer-001"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo "================================================"
echo "Alert-Management 废弃API测试"
echo "测试目标: 验证写权限API返回403 Forbidden"
echo "================================================"
echo ""

# 测试函数
test_deprecated_api() {
    local test_name=$1
    local method=$2
    local endpoint=$3
    local data=$4
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo "测试 $TOTAL_TESTS: $test_name"
    
    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "403" ]; then
        echo -e "${GREEN}✓ PASS${NC} - HTTP $http_code (Forbidden)"
        
        # 检查响应体是否包含迁移提示
        if echo "$body" | grep -q "Customer-Management"; then
            echo -e "${GREEN}  ✓ 包含迁移提示${NC}"
        else
            echo -e "${YELLOW}  ! 警告: 缺少迁移提示${NC}"
        fi
        
        # 显示新端点信息
        new_endpoint=$(echo "$body" | grep -o '"newEndpoint":"[^"]*"' | cut -d'"' -f4)
        if [ -n "$new_endpoint" ]; then
            echo -e "  新端点: ${YELLOW}$new_endpoint${NC}"
        fi
        
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ FAIL${NC} - 期望HTTP 403，实际得到 $http_code"
        echo "  响应体: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    echo ""
}

# 测试只读API仍然可用
test_readonly_api() {
    local test_name=$1
    local method=$2
    local endpoint=$3
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo "测试 $TOTAL_TESTS: $test_name"
    
    response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    # 只读API应该返回200或404（如果数据不存在）
    if [ "$http_code" = "200" ] || [ "$http_code" = "404" ]; then
        echo -e "${GREEN}✓ PASS${NC} - HTTP $http_code (只读API正常)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ FAIL${NC} - 期望HTTP 200/404，实际得到 $http_code"
        echo "  响应体: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    echo ""
}

echo "==============================================="
echo "第1组: 测试废弃的写权限API (应返回403)"
echo "==============================================="
echo ""

# 测试1: POST /api/notification-config/customer
test_deprecated_api \
    "创建客户通知配置 (已废弃)" \
    "POST" \
    "/api/notification-config/customer" \
    '{
        "customerId": "'$CUSTOMER_ID'",
        "emailEnabled": true,
        "emailRecipients": ["test@example.com"]
    }'

# 测试2: PUT /api/notification-config/customer/{customerId}
test_deprecated_api \
    "更新客户通知配置 (已废弃)" \
    "PUT" \
    "/api/notification-config/customer/$CUSTOMER_ID" \
    '{
        "emailEnabled": false
    }'

# 测试3: DELETE /api/notification-config/customer/{customerId}
test_deprecated_api \
    "删除客户通知配置 (已废弃)" \
    "DELETE" \
    "/api/notification-config/customer/$CUSTOMER_ID"

echo "==============================================="
echo "第2组: 测试只读API (应正常工作)"
echo "==============================================="
echo ""

# 测试4: GET /api/notification-config/customer
test_readonly_api \
    "获取所有客户通知配置 (只读)" \
    "GET" \
    "/api/notification-config/customer"

# 测试5: GET /api/notification-config/customer/{customerId}
test_readonly_api \
    "获取单个客户通知配置 (只读)" \
    "GET" \
    "/api/notification-config/customer/$CUSTOMER_ID"

echo "==============================================="
echo "第3组: 验证SMTP配置API仍正常 (未受影响)"
echo "==============================================="
echo ""

# 测试6: GET /api/notification-config/smtp
test_readonly_api \
    "获取所有SMTP配置" \
    "GET" \
    "/api/notification-config/smtp"

echo "==============================================="
echo "测试总结"
echo "==============================================="
echo ""
echo "总测试数: $TOTAL_TESTS"
echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
echo -e "${RED}失败: $FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    echo ""
    echo "职责分离实施成功:"
    echo "  - Alert-Management: 写权限API已废弃 (返回403)"
    echo "  - Alert-Management: 只读API正常工作"
    echo "  - Alert-Management: SMTP配置管理未受影响"
    echo "  - Customer-Management: 负责客户通知配置管理 (端口8084)"
    exit 0
else
    echo -e "${RED}✗ 部分测试失败${NC}"
    echo "请检查Alert-Management服务状态和代码修改"
    exit 1
fi
