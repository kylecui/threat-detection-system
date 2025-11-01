#!/bin/bash

##################################################
# 数据流诊断脚本 - V4.0 Phase 2
# 追踪事件从 data-ingestion → Kafka → Flink → Threat Assessment 的完整流程
##################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

CUSTOMER_ID="customer-001"
TEST_MAC="FF:EE:DD:CC:BB:AA"
TEST_ATTACK_IP="192.168.50.99"
TEST_HONEYPOT_IP="10.10.20.99"
TIMESTAMP=$(date +%s)

echo -e "${BLUE}=================================================="
echo "数据流诊断 - V4.0 Phase 2"
echo -e "==================================================${NC}"
echo ""

# 步骤 1: 检查所有服务状态
echo -e "${YELLOW}[1/7] 检查服务健康状态${NC}"
echo "-----------------------------------"

services=("kafka" "stream-processing" "data-ingestion-service" "threat-assessment-service")
all_healthy=true

for service in "${services[@]}"; do
    if docker ps --filter "name=$service" --filter "status=running" | grep -q "$service"; then
        echo -e "  ${GREEN}✓${NC} $service: Running"
    else
        echo -e "  ${RED}✗${NC} $service: Not Running"
        all_healthy=false
    fi
done

if [ "$all_healthy" = false ]; then
    echo -e "${RED}错误: 部分服务未运行，请先启动所有服务${NC}"
    exit 1
fi

echo ""

# 步骤 2: 检查 Kafka Topics
echo -e "${YELLOW}[2/7] 检查 Kafka Topics${NC}"
echo "-----------------------------------"

echo "  检查必需的 topics..."
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E "(attack-events|minute-aggregations|threat-alerts)" | while read topic; do
    echo -e "  ${GREEN}✓${NC} $topic"
done

echo ""

# 步骤 3: 发送测试事件
echo -e "${YELLOW}[3/7] 发送诊断测试事件${NC}"
echo "-----------------------------------"

echo "  发送单个测试事件..."
echo "    攻击源: $TEST_ATTACK_IP (IoT设备 - 权重 0.9)"
echo "    蜜罐IP: $TEST_HONEYPOT_IP (管理网段 - 权重 3.5)"
echo "    预期组合权重: 3.15"

response=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:8080/api/v1/logs/ingest" \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: $CUSTOMER_ID" \
  -d "{
    \"dev_serial\": \"DIAG-001\",
    \"attack_mac\": \"$TEST_MAC\",
    \"attack_ip\": \"$TEST_ATTACK_IP\",
    \"response_ip\": \"$TEST_HONEYPOT_IP\",
    \"response_port\": 3389,
    \"timestamp\": $TIMESTAMP
  }")

http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "200" ] || [ "$http_code" = "202" ]; then
    echo -e "  ${GREEN}✓${NC} 事件发送成功 (HTTP $http_code)"
else
    echo -e "  ${RED}✗${NC} 事件发送失败 (HTTP $http_code)"
    exit 1
fi

echo ""

# 步骤 4: 检查 Data Ingestion 日志
echo -e "${YELLOW}[4/7] 检查 Data Ingestion 处理${NC}"
echo "-----------------------------------"

sleep 2
echo "  查找测试事件接收日志..."

if docker logs data-ingestion-service 2>&1 | grep -q "$TEST_ATTACK_IP"; then
    echo -e "  ${GREEN}✓${NC} Data Ingestion 接收到事件"
    docker logs data-ingestion-service 2>&1 | grep "$TEST_ATTACK_IP" | tail -1 | sed 's/^/    /'
else
    echo -e "  ${YELLOW}⚠${NC} 未在日志中找到测试事件"
fi

echo ""

# 步骤 5: 检查 Kafka 中的原始事件
echo -e "${YELLOW}[5/7] 检查 Kafka attack-events topic${NC}"
echo "-----------------------------------"

echo "  读取最近的消息..."
sleep 1

kafka_output=$(docker exec kafka timeout 5 kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic attack-events \
    --from-beginning \
    --max-messages 10 2>/dev/null || true)

if echo "$kafka_output" | grep -q "$TEST_ATTACK_IP"; then
    echo -e "  ${GREEN}✓${NC} 在 Kafka 中找到测试事件"
    echo "$kafka_output" | grep "$TEST_ATTACK_IP" | head -1 | python3 -m json.tool 2>/dev/null | sed 's/^/    /' || echo "$kafka_output" | grep "$TEST_ATTACK_IP" | head -1 | sed 's/^/    /'
else
    echo -e "  ${YELLOW}⚠${NC} 未在 attack-events topic 中找到测试事件"
    echo "    可能原因: Kafka Producer 未成功发送"
fi

echo ""

# 步骤 6: 检查 Flink 处理状态
echo -e "${YELLOW}[6/7] 检查 Flink Stream Processing${NC}"
echo "-----------------------------------"

echo "  检查 Flink Job 状态..."
flink_status=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null)

if echo "$flink_status" | grep -q '"state":"RUNNING"'; then
    echo -e "  ${GREEN}✓${NC} Flink Job 运行中"
    
    # 获取 job ID
    job_id=$(echo "$flink_status" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data['jobs'][0]['jid'])" 2>/dev/null || echo "unknown")
    
    if [ "$job_id" != "unknown" ]; then
        echo "    Job ID: $job_id"
        
        # 检查指标
        metrics=$(curl -s "http://localhost:8081/jobs/$job_id/metrics?get=numRecordsIn,numRecordsOut" 2>/dev/null || echo "")
        if [ -n "$metrics" ]; then
            echo "$metrics" | python3 -m json.tool 2>/dev/null | grep -E "(id|value)" | sed 's/^/    /' || true
        fi
    fi
else
    echo -e "  ${RED}✗${NC} Flink Job 未运行"
fi

echo ""
echo "  检查 Flink 日志中的处理记录..."

# 查找窗口触发和聚合日志
if docker logs stream-processing 2>&1 | tail -100 | grep -q "Window fired\|Aggregat"; then
    echo -e "  ${GREEN}✓${NC} Flink 正在处理窗口聚合"
    docker logs stream-processing 2>&1 | tail -100 | grep -E "Window fired|Aggregat" | tail -3 | sed 's/^/    /'
else
    echo -e "  ${YELLOW}⚠${NC} 未看到窗口触发日志 (可能需要等待窗口时间)"
fi

echo ""

# 步骤 7: 检查 Threat Assessment
echo -e "${YELLOW}[7/7] 检查 Threat Assessment 威胁评分${NC}"
echo "-----------------------------------"

echo "  等待 5 秒让数据流转..."
sleep 5

echo "  查找双维度权重计算日志..."

threat_logs=$(docker logs threat-assessment-service 2>&1 | tail -200)

if echo "$threat_logs" | grep -q "attackSourceWeight\|honeypotSensitivityWeight\|combinedSegmentWeight"; then
    echo -e "  ${GREEN}✓${NC} 发现双维度权重计算日志"
    echo ""
    echo "  最近的威胁评分记录:"
    echo "$threat_logs" | grep -B2 -A2 "combinedSegmentWeight" | tail -20 | sed 's/^/    /'
else
    echo -e "  ${YELLOW}⚠${NC} 未发现双维度权重计算日志"
    echo "    可能原因:"
    echo "      1. 窗口尚未触发 (需要等待 30秒 + 2分钟)"
    echo "      2. 数据未流转到 threat-assessment"
    echo ""
    echo "  最近的 threat-assessment 日志:"
    docker logs threat-assessment-service 2>&1 | tail -10 | sed 's/^/    /'
fi

echo ""

# 检查数据库中是否有评估记录
echo "  检查数据库中的威胁评估记录..."

db_count=$(docker exec postgres psql -U threat_user -d threat_detection -t -c "SELECT COUNT(*) FROM threat_assessments WHERE customer_id='$CUSTOMER_ID';" 2>/dev/null | tr -d ' ' || echo "0")

if [ "$db_count" -gt 0 ]; then
    echo -e "  ${GREEN}✓${NC} 数据库中有 $db_count 条威胁评估记录"
    echo ""
    echo "  最新的评估记录:"
    docker exec postgres psql -U threat_user -d threat_detection -c \
        "SELECT attack_mac, threat_score, threat_level, attack_count, unique_ips, unique_ports, assessment_time 
         FROM threat_assessments 
         WHERE customer_id='$CUSTOMER_ID' 
         ORDER BY assessment_time DESC 
         LIMIT 3;" 2>/dev/null | sed 's/^/    /'
else
    echo -e "  ${YELLOW}⚠${NC} 数据库中暂无威胁评估记录"
fi

echo ""
echo -e "${BLUE}=================================================="
echo "诊断完成"
echo -e "==================================================${NC}"
echo ""

# 诊断建议
echo -e "${BLUE}📋 诊断建议:${NC}"
echo ""

if [ "$db_count" -gt 0 ]; then
    echo -e "  ${GREEN}✓ 系统工作正常！${NC}"
    echo "    - 数据流完整: Data Ingestion → Kafka → Flink → Threat Assessment"
    echo "    - 建议运行完整测试: bash test_v4_phase2_dual_dimension.sh"
else
    echo -e "  ${YELLOW}⚠ 系统可能需要更多时间处理${NC}"
    echo ""
    echo "  建议操作:"
    echo "    1. 等待 3-5 分钟让窗口完整触发"
    echo "    2. 重新运行此诊断脚本查看进展"
    echo "    3. 检查日志中的错误信息:"
    echo "       docker logs stream-processing 2>&1 | grep -i error"
    echo "       docker logs threat-assessment-service 2>&1 | grep -i error"
    echo ""
    echo "  快速测试数据流:"
    echo "    # 发送更多事件"
    echo "    for i in {1..50}; do"
    echo "      curl -s -X POST http://localhost:8080/api/v1/logs/ingest \\"
    echo "        -H 'Content-Type: application/json' \\"
    echo "        -H 'X-Customer-Id: customer-001' \\"
    echo "        -d '{\"dev_serial\":\"TEST\",\"attack_mac\":\"00:11:22:33:44:55\","
    echo "            \"attack_ip\":\"192.168.50.10\",\"response_ip\":\"10.10.20.100\","
    echo "            \"response_port\":3389,\"timestamp\":'$(date +%s)'}' > /dev/null"
    echo "    done"
fi

echo ""
