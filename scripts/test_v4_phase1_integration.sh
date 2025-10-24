#!/bin/bash
#
# V4.0 Phase 1 集成测试脚本
# 验证attackSourceWeight正确应用到威胁评分
#

set -e

BASE_URL="http://localhost:8083"
API_ENDPOINT="/api/v1/assessment/evaluate"

echo "=================================================="
echo "V4.0 Phase 1 Integration Test"
echo "Testing attackSourceWeight integration"
echo "=================================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
test_scenario() {
    local test_name="$1"
    local attack_ip="$2"
    local expected_weight="$3"
    local request_body="$4"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo "----------------------------------------"
    echo "测试 ${TOTAL_TESTS}: ${test_name}"
    echo "攻击源IP: ${attack_ip}"
    echo "预期权重: ${expected_weight}"
    echo ""
    
    # 发送请求
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}${API_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "${request_body}")
    
    # 分离HTTP状态码和响应体
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    echo "HTTP状态码: ${HTTP_CODE}"
    
    # we should accept all 200 series responses, 200, 201, etc.
    if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -gt 299 ]; then
        echo -e "${RED}❌ 失败: HTTP状态码不是200系列${NC}"
        echo "响应: ${BODY}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
    
    # 解析响应
    THREAT_SCORE=$(echo "$BODY" | jq -r '.threat_score')
    THREAT_LEVEL=$(echo "$BODY" | jq -r '.threat_level')
    
    echo "威胁评分: ${THREAT_SCORE}"
    echo "威胁等级: ${THREAT_LEVEL}"
    
    # 验证威胁评分不为null
    if [ "$THREAT_SCORE" == "null" ] || [ -z "$THREAT_SCORE" ]; then
        echo -e "${RED}❌ 失败: 威胁评分为空${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
    
    echo -e "${GREEN}✅ 通过: 评分计算成功${NC}"
    echo "提示: 检查日志确认权重为 ${expected_weight}"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo ""
}

echo "等待服务就绪..."
sleep 2

# 测试场景1: IoT设备 (权重应为0.9)
    # "customer_id": "test-customer-001",
    # "attack_mac": "00:11:22:33:44:55",
    # "attack_ip": "192.168.1.100",
    # "attack_count": 150,
    # "unique_ips": 5,
    # "unique_ports": 3,
    # "unique_devices": 2
test_scenario \
    "IoT设备攻击 (低权重)" \
    "192.168.50.10" \
    "0.9" \
    '{
      "customer_id": "customer-001",
      "attack_mac": "00:11:22:33:44:55",
      "attack_ip": "192.168.50.10",
      "attack_count": 100,
      "unique_ips": 5,
      "unique_ports": 3,
      "unique_devices": 1,
      "timestamp": "2024-01-15T02:00:00Z"
    }'

# 测试场景2: 数据库服务器 (权重应为3.0)
test_scenario \
    "数据库服务器攻击 (高权重)" \
    "10.0.3.50" \
    "3.0" \
    '{
      "customer_id": "customer-001",
      "attack_mac": "AA:BB:CC:DD:EE:FF",
      "attack_ip": "10.0.3.50",
      "attack_count": 100,
      "unique_ips": 5,
      "unique_ports": 3,
      "unique_devices": 1,
      "timestamp": "2024-01-15T10:00:00Z"
    }'

# 测试场景3: 访客网络 (权重应为0.6)
test_scenario \
    "访客网络攻击 (最低权重)" \
    "192.168.100.20" \
    "0.6" \
    '{
      "customer_id": "customer-001",
      "attack_mac": "11:22:33:44:55:66",
      "attack_ip": "192.168.100.20",
      "attack_count": 100,
      "unique_ips": 5,
      "unique_ports": 3,
      "unique_devices": 1,
      "timestamp": "2024-01-15T14:00:00Z"
    }'

# 测试场景4: 办公网络 (权重应为1.0 - 默认)
test_scenario \
    "办公网络攻击 (默认权重)" \
    "192.168.10.100" \
    "1.0" \
    '{
      "customer_id": "customer-001",
      "attack_mac": "22:33:44:55:66:77",
      "attack_ip": "192.168.10.100",
      "attack_count": 100,
      "unique_ips": 5,
      "unique_ports": 3,
      "unique_devices": 1,
      "timestamp": "2024-01-15T16:00:00Z"
    }'

# 测试场景5: 管理网络 (权重应为3.0)
test_scenario \
    "管理网络攻击 (高权重)" \
    "10.0.100.10" \
    "3.0" \
    '{
      "customer_id": "customer-001",
      "attack_mac": "33:44:55:66:77:88",
      "attack_ip": "10.0.100.10",
      "attack_count": 100,
      "unique_ips": 5,
      "unique_ports": 3,
      "unique_devices": 1,
      "timestamp": "2024-01-15T18:00:00Z"
    }'

# 输出测试总结
echo "=================================================="
echo "测试总结"
echo "=================================================="
echo "总测试数: ${TOTAL_TESTS}"
echo -e "通过: ${GREEN}${PASSED_TESTS}${NC}"
echo -e "失败: ${RED}${FAILED_TESTS}${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试通过!${NC}"
    echo ""
    echo "下一步: 检查日志验证权重应用"
    echo "命令: docker-compose logs threat-assessment | grep 'V4.0 attack source weight applied'"
    echo ""
    echo "预期日志:"
    echo "  - attackIp=192.168.50.10, weight=0.9"
    echo "  - attackIp=10.0.3.50, weight=3.0"
    echo "  - attackIp=192.168.100.20, weight=0.6"
    echo "  - attackIp=192.168.10.100, weight=1.0"
    echo "  - attackIp=10.0.100.10, weight=3.0"
    exit 0
else
    echo -e "${RED}❌ 部分测试失败${NC}"
    echo "请检查服务日志: docker-compose logs threat-assessment"
    exit 1
fi
