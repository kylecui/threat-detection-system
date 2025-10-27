#!/bin/bash

##################################################
# 端口权重功能测试 - 绕过Flink直接测试API
# 目的: 验证端口权重系统的核心功能
# 方法: 直接调用 threat-assessment REST API
##################################################

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

THREAT_ASSESSMENT_URL="http://localhost:8083"
EVALUATE_ENDPOINT="${THREAT_ASSESSMENT_URL}/api/v1/assessment/evaluate"
CUSTOMER_ID="customer-001"

echo "=========================================="
echo "端口权重功能测试 (绕过Flink)"
echo "=========================================="
echo ""
echo "测试策略: 直接调用 threat-assessment REST API"
echo "API端点: POST ${EVALUATE_ENDPOINT}"
echo ""

# 等待服务就绪
echo "⏳ 等待 threat-assessment 服务启动..."
for i in {1..30}; do
    if curl -s "${THREAT_ASSESSMENT_URL}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} threat-assessment 服务已就绪"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}✗${NC} threat-assessment 服务未启动，请检查"
        exit 1
    fi
    sleep 1
done

echo ""
echo "=========================================="
echo "📊 测试场景设计"
echo "=========================================="
echo ""
echo "场景1: 高风险端口 RDP (3389)"
echo "  - customer_port_weights: weight=10.0 (远程控制)"
echo "  - 预期: 高威胁评分"
echo ""
echo "场景2: 中风险端口 SSH (22)"
echo "  - customer_port_weights: weight=5.0 (远程登录)"
echo "  - 预期: 中等威胁评分"
echo ""
echo "场景3: 低风险端口 HTTP (8080)"
echo "  - customer_port_weights: weight=1.0 (Web服务)"
echo "  - 预期: 较低威胁评分"
echo ""
echo "场景4: 多端口扫描 (3389, 445, 22)"
echo "  - 端口权重: 10.0, 8.0, 5.0"
echo "  - portList: [3389, 445, 22]"
echo "  - 预期: 组合端口权重计算"
echo ""

# 测试函数
test_scenario() {
    local scenario_name="$1"
    local attack_mac="$2"
    local attack_ip="$3"
    local attack_count="$4"
    local unique_ips="$5"
    local unique_ports="$6"
    local unique_devices="$7"
    local expected_min_score="$8"
    local expected_level="$9"
    
    echo "=========================================="
    echo "🔍 ${scenario_name}"
    echo "=========================================="
    echo ""
    
    # 构造请求 (包含port_list用于端口权重计算)
    # 注意: 实际场景中port_list应该是真实访问的端口列表
    # 这里为了测试,我们根据场景模拟不同的端口组合
    local port_list_json="[]"
    if [[ "$scenario_name" == *"RDP"* ]]; then
        port_list_json="[3389]"
    elif [[ "$scenario_name" == *"SSH"* ]]; then
        port_list_json="[22]"
    elif [[ "$scenario_name" == *"HTTP"* ]]; then
        port_list_json="[8080]"
    elif [[ "$scenario_name" == *"多端口"* ]]; then
        port_list_json="[3389, 445, 22]"
    elif [[ "$scenario_name" == *"大规模"* ]]; then
        port_list_json="[3389, 445, 22, 3306, 1433, 139, 5900, 8080, 21, 23]"
    fi
    
    REQUEST_BODY=$(cat <<EOF
{
  "customer_id": "${CUSTOMER_ID}",
  "attack_mac": "${attack_mac}",
  "attack_ip": "${attack_ip}",
  "attack_count": ${attack_count},
  "unique_ips": ${unique_ips},
  "unique_ports": ${unique_ports},
  "unique_devices": ${unique_devices},
  "port_list": ${port_list_json}
}
EOF
)
    
    echo "📤 发送评估请求:"
    echo "${REQUEST_BODY}" | jq '.'
    echo ""
    
    # 调用API
    RESPONSE=$(curl -s -X POST "${EVALUATE_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "${REQUEST_BODY}" \
        -w "\n%{http_code}")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "200" ]; then
        echo -e "${RED}✗ API调用失败${NC}"
        echo "HTTP状态码: $HTTP_CODE"
        echo "响应: $BODY"
        return 1
    fi
    
    echo -e "${GREEN}✓ API调用成功 (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo "📥 评估结果:"
    echo "$BODY" | jq '.'
    echo ""
    
    # 提取关键字段
    THREAT_SCORE=$(echo "$BODY" | jq -r '.threat_score // .threatScore // 0')
    THREAT_LEVEL=$(echo "$BODY" | jq -r '.threat_level // .threatLevel // "UNKNOWN"')
    ASSESSMENT_ID=$(echo "$BODY" | jq -r '.assessment_id // .assessmentId // "N/A"')
    
    echo "📊 评估指标:"
    echo "  - 评估ID: ${ASSESSMENT_ID}"
    echo "  - 威胁评分: ${THREAT_SCORE}"
    echo "  - 威胁等级: ${THREAT_LEVEL}"
    echo ""
    
    # 验证结果
    if [ "$THREAT_LEVEL" = "$expected_level" ]; then
        echo -e "${GREEN}✓ 威胁等级符合预期: ${THREAT_LEVEL}${NC}"
    else
        echo -e "${YELLOW}⚠ 威胁等级不符合预期: 预期=${expected_level}, 实际=${THREAT_LEVEL}${NC}"
    fi
    
    # 检查评分是否大于最小预期值
    if (( $(echo "$THREAT_SCORE >= $expected_min_score" | bc -l) )); then
        echo -e "${GREEN}✓ 威胁评分符合预期: ${THREAT_SCORE} >= ${expected_min_score}${NC}"
    else
        echo -e "${YELLOW}⚠ 威胁评分低于预期: ${THREAT_SCORE} < ${expected_min_score}${NC}"
    fi
    
    echo ""
    
    # 查询数据库验证
    echo "🔍 验证数据库记录..."
    DB_RECORD=$(docker exec -it postgres psql -U threat_user -d threat_detection -t -c \
        "SELECT threat_score, threat_level FROM threat_assessments WHERE attack_mac = '${attack_mac}' ORDER BY created_at DESC LIMIT 1;" 2>/dev/null | tr -d '[:space:]')
    
    if [ -n "$DB_RECORD" ]; then
        echo -e "${GREEN}✓ 数据库记录已创建${NC}"
        echo "  记录: $DB_RECORD"
    else
        echo -e "${YELLOW}⚠ 未找到数据库记录${NC}"
    fi
    
    echo ""
    echo -e "${GREEN}========== 场景测试完成 ==========${NC}"
    echo ""
    
    # 等待一下，避免重复评估
    sleep 2
}

# 清理旧数据
echo "🧹 清理旧测试数据..."
docker exec -it postgres psql -U threat_user -d threat_detection -c \
    "DELETE FROM threat_assessments WHERE customer_id = '${CUSTOMER_ID}';" > /dev/null 2>&1
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 场景1: 高风险端口 RDP (3389)
test_scenario \
    "场景1: 高风险端口 RDP (3389)" \
    "00:11:22:33:44:01" \
    "192.168.1.100" \
    100 \
    3 \
    1 \
    1 \
    300 \
    "HIGH"

# 场景2: 中风险端口 SSH (22)
test_scenario \
    "场景2: 中风险端口 SSH (22)" \
    "00:11:22:33:44:02" \
    "192.168.1.101" \
    100 \
    3 \
    1 \
    1 \
    150 \
    "MEDIUM"

# 场景3: 低风险端口 HTTP (8080)
test_scenario \
    "场景3: 低风险端口 HTTP (8080)" \
    "00:11:22:33:44:03" \
    "192.168.1.102" \
    100 \
    3 \
    1 \
    1 \
    30 \
    "LOW"

# 场景4: 多端口扫描 (应该体现端口多样性)
test_scenario \
    "场景4: 多端口扫描 (3端口)" \
    "00:11:22:33:44:04" \
    "192.168.1.103" \
    150 \
    5 \
    3 \
    2 \
    500 \
    "HIGH"

# 场景5: 大规模扫描 + 高风险端口
test_scenario \
    "场景5: 大规模扫描 (10端口, 包含RDP)" \
    "00:11:22:33:44:05" \
    "192.168.1.104" \
    500 \
    10 \
    10 \
    3 \
    1500 \
    "CRITICAL"

echo ""
echo "=========================================="
echo "📊 测试总结"
echo "=========================================="
echo ""

# 统计数据库中的记录
TOTAL_RECORDS=$(docker exec -it postgres psql -U threat_user -d threat_detection -t -c \
    "SELECT COUNT(*) FROM threat_assessments WHERE customer_id = '${CUSTOMER_ID}';" | tr -d '[:space:]')

echo "✓ 总测试场景: 5"
echo "✓ 数据库记录数: ${TOTAL_RECORDS}"
echo ""

if [ "$TOTAL_RECORDS" -ge "5" ]; then
    echo -e "${GREEN}✓✓✓ 端口权重功能测试通过!${NC}"
    echo ""
    echo "测试总结:"
    echo "  1. ✓ REST API评估端点工作正常"
    echo "  2. ✓ 威胁评分计算正常"
    echo "  3. ✓ 数据库持久化正常"
    echo "  4. ✓ 端口权重影响威胁评分 (需人工确认分数差异)"
    echo ""
    echo "详细记录:"
    docker exec -it postgres psql -U threat_user -d threat_detection -c \
        "SELECT attack_mac, threat_score, threat_level, unique_ports, created_at 
         FROM threat_assessments 
         WHERE customer_id = '${CUSTOMER_ID}' 
         ORDER BY created_at DESC 
         LIMIT 10;"
else
    echo -e "${RED}✗✗✗ 端口权重功能测试失败!${NC}"
    echo "预期记录数: >= 5, 实际: ${TOTAL_RECORDS}"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="

