#!/bin/bash

# V4.0 Phase 3: 简化版时间分布评分测试
# 通过data-ingestion API发送事件

set -e

echo "=================================================="
echo "时间分布评分功能快速验证"
echo "=================================================="
echo ""

API_URL="http://localhost:8080/api/events"
CUSTOMER_ID="customer_burst_test"
ATTACK_MAC="BB:BB:BB:BB:BB:BB"
ATTACK_IP="192.168.99.99"
DEV_SERIAL="BURST_TEST_DEV"

# 获取当前Unix时间戳
BASE_TIME=$(date +%s)

echo "场景1: 爆发式攻击 (100个事件在3秒内)"
echo "基准时间: $(date -d @$BASE_TIME)"
echo ""

BURST_COUNT=0
echo -n "发送事件: "

# 发送100个事件，时间戳集中在3秒内
for i in $(seq 1 100); do
    # 时间偏移：0, 1, 2, 0, 1, 2, ... (循环)
    TIME_OFFSET=$((i % 3))
    EVENT_TIME=$((BASE_TIME + TIME_OFFSET))
    
    # IP和端口多样化
    IP_SUFFIX=$((i % 50 + 1))
    PORT=$((3000 + (i % 10)))
    
    # 构造事件JSON
    EVENT_JSON=$(cat <<EOF
{
  "id": "${DEV_SERIAL}_${EVENT_TIME}_${i}",
  "devSerial": "$DEV_SERIAL",
  "logType": 1,
  "subType": 1,
  "attackMac": "$ATTACK_MAC",
  "attackIp": "$ATTACK_IP",
  "responseIp": "10.99.0.$IP_SUFFIX",
  "responsePort": $PORT,
  "lineId": $i,
  "ifaceType": 1,
  "vlanId": 30,
  "logTime": $EVENT_TIME,
  "ethType": 2048,
  "ipType": 6,
  "severity": "INFO",
  "description": "Burst attack test",
  "rawLog": "test_burst",
  "customerId": "$CUSTOMER_ID",
  "timestamp": "$(date -u -d @$EVENT_TIME +%Y-%m-%dT%H:%M:%S)Z"
}
EOF
)
    
    # 发送到API（静默模式）
    curl -s -X POST "$API_URL" \
        -H "Content-Type: application/json" \
        -d "$EVENT_JSON" > /dev/null
    
    BURST_COUNT=$((BURST_COUNT + 1))
    
    # 每10个显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo -n "$i "
    fi
done

echo ""
echo "✅ 爆发式攻击: 已发送 $BURST_COUNT 个事件"
echo ""

# 时间跨度
FIRST_EVENT=$BASE_TIME
LAST_EVENT=$((BASE_TIME + 2))
TIME_SPAN=$((LAST_EVENT - FIRST_EVENT))

echo "事件时间分布:"
echo "  首个事件: $(date -d @$FIRST_EVENT +%H:%M:%S)"
echo "  最后事件: $(date -d @$LAST_EVENT +%H:%M:%S)"
echo "  时间跨度: ${TIME_SPAN}秒"
echo ""

echo "预期计算:"
echo "  爆发强度系数 BIC = 1 - (2 / 30) = 0.93 (30秒窗口)"
echo "  时间分布权重 = 1.0 + (0.93 × 2.0) = 2.86"
echo "  威胁评分提升: 约2.86倍"
echo ""

echo "等待35秒让Tier 1窗口触发..."
sleep 35

echo ""
echo "查询Flink日志中的时间分布信息:"
echo "----------------------------------------"
docker logs stream-processing 2>&1 | \
    grep -E "Tier 1.*$CUSTOMER_ID.*$ATTACK_MAC" | \
    tail -5

echo ""
echo "查询详细的时间分布权重:"
echo "----------------------------------------"
docker logs stream-processing 2>&1 | \
    grep -E "timeDistWeight|burstIntensity|timeSpan" | \
    grep "$CUSTOMER_ID" | \
    tail -5

echo ""
echo "=================================================="
echo "测试完成！"
echo "=================================================="
echo ""
echo "如需查看数据库中的事件分布:"
echo "  docker exec -e PGPASSWORD=threat_detection_pass postgres psql -U threat_user -d threat_detection -c \\"
echo "    \"SELECT MIN(event_timestamp), MAX(event_timestamp), "
echo "     EXTRACT(EPOCH FROM (MAX(event_timestamp) - MIN(event_timestamp))) as span_sec, COUNT(*) "
echo "     FROM attack_events WHERE customer_id = '$CUSTOMER_ID';\""
echo ""
