#!/bin/bash

# 职责分离架构端到端集成测试
# 验证Customer-Management和Alert-Management服务的正确交互

set -e

# 服务端点
CUSTOMER_MGMT_URL="http://localhost:8084/api/v1"
ALERT_MGMT_URL="http://localhost:8082/api"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo "======================================================================="
echo "职责分离架构 - 端到端集成测试"
echo "======================================================================="
echo ""
echo "测试目标:"
echo "  1. Customer-Management: 提供客户/设备/通知配置的写操作（CRUD）"
echo "  2. Alert-Management: 只读访问通知配置（从数据库）"
echo "  3. 验证职责分离: Alert-Management不能直接修改配置（返回403）"
echo "  4. 数据一致性: Customer-Management写入 → Alert-Management读取正确"
echo ""
echo "======================================================================="
echo ""

# 测试客户ID
TEST_CUSTOMER_ID="integration-test-customer-$(date +%s)"
TEST_DEVICE_SERIAL="INT-TEST-DEV-001"

# 辅助函数：测试API
test_api() {
    local test_name=$1
    local method=$2
    local url=$3
    local data=$4
    local expected_status=$5
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "测试 $TOTAL_TESTS: $test_name ... "
    
    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method "$url")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$url" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "$expected_status" ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $http_code)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        echo -e "${RED}✗ FAIL${NC} (期望 $expected_status, 实际 $http_code)"
        echo "  响应: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

echo "======================================================================="
echo "阶段1: 验证服务健康状态"
echo "======================================================================="
echo ""

# 0. 清理可能存在的旧测试数据
echo "清理旧测试数据..."
docker exec -i postgres psql -U threat_user -d threat_detection -c "DELETE FROM device_customer_mapping WHERE dev_serial LIKE 'INT-TEST%';" > /dev/null 2>&1
docker exec -i postgres psql -U threat_user -d threat_detection -c "DELETE FROM customer_notification_configs WHERE customer_id LIKE 'integration-test%';" > /dev/null 2>&1
docker exec -i postgres psql -U threat_user -d threat_detection -c "DELETE FROM customers WHERE customer_id LIKE 'integration-test%';" > /dev/null 2>&1
echo "✓ 清理完成"
echo ""

# 1. Customer-Management健康检查
test_api "Customer-Management健康检查" "GET" "http://localhost:8084/actuator/health" "" "200"

# 2. Alert-Management健康检查  
test_api "Alert-Management健康检查" "GET" "http://localhost:8082/actuator/health" "" "200"

echo ""
echo "======================================================================="
echo "阶段2: Customer-Management写操作 (数据准备)"
echo "======================================================================="
echo ""

# 3. 在Customer-Management创建客户 (使用snake_case字段名)
test_api "CM创建客户" "POST" "$CUSTOMER_MGMT_URL/customers" \
    '{
        "customer_id": "'$TEST_CUSTOMER_ID'",
        "name": "集成测试客户",
        "email": "integration-test@example.com",
        "phone": "13800138000"
    }' "201"

# 4. 在Customer-Management绑定设备 (使用snake_case字段名)
test_api "CM绑定设备" "POST" "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID/devices" \
    '{
        "dev_serial": "'$TEST_DEVICE_SERIAL'",
        "description": "集成测试设备"
    }' "201"

# 5. 在Customer-Management创建通知配置 (使用snake_case字段名)
test_api "CM创建通知配置" "PUT" "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID/notification-config" \
    '{
        "email_enabled": true,
        "email_recipients": ["test@example.com"],
        "sms_enabled": false,
        "slack_enabled": false,
        "webhook_enabled": false,
        "min_severity_level": "MEDIUM"
    }' "200"

echo ""
echo "======================================================================="
echo "阶段3: Alert-Management只读访问验证（通过notification-config API）"
echo "======================================================================="
echo ""

# 6. Alert-Management读取所有通知配置（验证只读访问）
test_api "AM读取所有通知配置" "GET" "$ALERT_MGMT_URL/notification-config/customer" "" "200"

# 7. Alert-Management读取特定客户的通知配置
test_api "AM读取特定客户配置" "GET" "$ALERT_MGMT_URL/notification-config/customer/$TEST_CUSTOMER_ID" "" "200"

echo ""
echo "======================================================================="
echo "阶段4: 职责分离验证 (Alert-Management不能写操作通知配置)"
echo "======================================================================="
echo ""

# 8. Alert-Management尝试创建通知配置 (应该返回403 Forbidden)
test_api "AM尝试创建配置(应返回403)" "POST" "$ALERT_MGMT_URL/notification-config/customer" \
    '{
        "customerId": "test-forbidden",
        "emailEnabled": true,
        "emailRecipients": ["test@test.com"]
    }' "403"

# 9. Alert-Management尝试更新通知配置 (应该返回403)
test_api "AM尝试更新配置(应返回403)" "PUT" "$ALERT_MGMT_URL/notification-config/customer/$TEST_CUSTOMER_ID" \
    '{
        "emailEnabled": false
    }' "403"

# 10. Alert-Management尝试删除通知配置 (应该返回403)
test_api "AM尝试删除配置(应返回403)" "DELETE" "$ALERT_MGMT_URL/notification-config/customer/$TEST_CUSTOMER_ID" "" "403"

echo ""
echo "======================================================================="
echo "阶段5: 数据一致性验证"
echo "======================================================================="
echo ""

# 11. Customer-Management更新通知配置 (使用snake_case字段名)
echo -n "测试 $((TOTAL_TESTS + 1)): CM更新通知配置 ... "
TOTAL_TESTS=$((TOTAL_TESTS + 1))
response=$(curl -s -w "\n%{http_code}" -X PATCH "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID/notification-config" \
    -H "Content-Type: application/json" \
    -d '{
        "email_enabled": false,
        "min_severity_level": "HIGH"
    }')
http_code=$(echo "$response" | tail -n1)
if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC} (HTTP $http_code)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    # 等待数据库同步
    sleep 1
else
    echo -e "${RED}✗ FAIL${NC} (期望 200, 实际 $http_code)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 12. Alert-Management读取更新后的配置并验证一致性
echo -n "测试 $((TOTAL_TESTS + 1)): AM读取更新后配置 ... "
TOTAL_TESTS=$((TOTAL_TESTS + 1))
response=$(curl -s -w "\n%{http_code}" -X GET "$ALERT_MGMT_URL/notification-config/customer/$TEST_CUSTOMER_ID")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" = "200" ]; then
    # 验证配置是否一致（使用snake_case字段名）
    email_enabled=$(echo "$body" | jq -r '.emailEnabled')
    min_severity=$(echo "$body" | jq -r '.minSeverityLevel')
    
    if [ "$email_enabled" = "false" ] && [ "$min_severity" = "HIGH" ]; then
        echo -e "${GREEN}✓ PASS${NC} (配置一致: emailEnabled=false, minSeverityLevel=HIGH)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ FAIL${NC} (配置不一致: emailEnabled=$email_enabled, minSeverityLevel=$min_severity)"
        echo "  响应内容: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $http_code)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 13. Customer-Management删除通知配置
test_api "CM删除通知配置" "DELETE" "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID/notification-config" "" "204"

# 14. Alert-Management验证配置已删除
test_api "AM验证配置已删除" "GET" "$ALERT_MGMT_URL/notification-config/customer/$TEST_CUSTOMER_ID" "" "404"

echo ""
echo "======================================================================="
echo "阶段6: 清理测试数据"
echo "======================================================================="
echo ""

# 15. 清理：删除客户（软删除，级联删除设备绑定）
test_api "CM删除客户(软删除)" "DELETE" "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID" "" "204"

# 16. 验证客户状态为INACTIVE
echo -n "测试 $((TOTAL_TESTS + 1)): 验证客户已软删除(状态=INACTIVE) ... "
TOTAL_TESTS=$((TOTAL_TESTS + 1))
response=$(curl -s "$CUSTOMER_MGMT_URL/customers/$TEST_CUSTOMER_ID")
status=$(echo "$response" | jq -r '.status')
if [ "$status" = "INACTIVE" ]; then
    echo -e "${GREEN}✓ PASS${NC} (客户状态: INACTIVE)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗ FAIL${NC} (期望 INACTIVE, 实际 $status)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ""
echo "======================================================================="
echo "测试总结"
echo "======================================================================="
echo ""
echo "总测试数: $TOTAL_TESTS"
echo -e "通过: ${GREEN}$PASSED_TESTS${NC}"
echo -e "失败: ${RED}$FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    echo ""
    echo "职责分离架构验证成功:"
    echo "  ✓ Customer-Management: 提供完整的CRUD操作（客户/设备/通知配置）"
    echo "  ✓ Alert-Management: 只读访问通知配置（从数据库）"
    echo "  ✓ 写操作隔离: Alert-Management无法修改配置（返回403）"
    echo "  ✓ 数据一致性: Customer-Management写入 → Alert-Management正确读取"
    echo ""
    echo "架构优势:"
    echo "  - 职责清晰: 配置管理与通知发送分离"
    echo "  - 数据安全: 告警服务不能误操作配置"
    echo "  - 独立扩展: 两个服务可独立部署和扩展"
    echo "  - 容错隔离: 一个服务故障不影响另一个"
    exit 0
else
    echo -e "${RED}✗ 有测试失败！${NC}"
    echo ""
    echo "请检查:"
    echo "  1. 服务是否正常运行 (docker ps)"
    echo "  2. 数据库连接是否正常"
    echo "  3. API端点实现是否正确"
    exit 1
fi
    exit 0
else
    echo -e "${RED}✗ 部分测试失败${NC}"
    echo "请检查服务日志"
    exit 1
fi
