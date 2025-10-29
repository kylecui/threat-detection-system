#!/bin/bash

# V4.0 Phase 3: 时间分布评分功能测试
# 测试爆发强度系数对威胁评分的影响

set -e

echo "=================================================="
echo "V4.0 Phase 3: 时间分布评分功能测试"
echo "测试爆发式攻击 vs 分散式攻击的评分差异"
echo "=================================================="
echo ""

# 配置
KAFKA_CONTAINER="kafka"
TOPIC="attack-events"
CUSTOMER_ID="customer_test_burst"
ATTACK_MAC="AA:BB:CC:DD:EE:FF"
ATTACK_IP="192.168.1.100"
DEV_SERIAL="TEST_BURST_001"

# 清理函数
cleanup() {
    echo "清理测试数据..."
    docker exec postgres psql -U postgres -d threat_detection -c \
        "DELETE FROM attack_events WHERE customer_id = '$CUSTOMER_ID';" 2>/dev/null || true
}

# 等待Flink处理完成
wait_for_processing() {
    local wait_time=$1
    echo "等待 $wait_time 秒让Flink处理数据..."
    sleep $wait_time
}

# 发送事件到Kafka
send_event() {
    local timestamp=$1
    local response_ip=$2
    local response_port=$3
    
    local event=$(cat <<EOF
{
  "id": "${DEV_SERIAL}_${timestamp}_1",
  "devSerial": "$DEV_SERIAL",
  "logType": 1,
  "subType": 1,
  "attackMac": "$ATTACK_MAC",
  "attackIp": "$ATTACK_IP",
  "responseIp": "$response_ip",
  "responsePort": $response_port,
  "lineId": 1,
  "ifaceType": 1,
  "vlanId": 30,
  "logTime": $timestamp,
  "ethType": 2048,
  "ipType": 6,
  "severity": "INFO",
  "description": "Test burst scoring",
  "rawLog": "test",
  "customerId": "$CUSTOMER_ID",
  "timestamp": "$(date -u -d @$timestamp +%Y-%m-%dT%H:%M:%S)Z"
}
EOF
)
    
    echo "$event" | docker exec -i $KAFKA_CONTAINER kafka-console-producer.sh \
        --bootstrap-server localhost:9092 \
        --topic $TOPIC \
        --property "parse.key=true" \
        --property "key.separator=:" \
        > /dev/null 2>&1
}

echo "----------------------------------------"
echo "测试场景 1: 爆发式攻击 (3秒内100个事件)"
echo "预期: 高爆发强度系数 (BIC ≈ 0.99)"
echo "预期: 高时间分布权重 (≈ 2.98)"
echo "----------------------------------------"

cleanup

# 当前时间戳
BASE_TIME=$(date +%s)

echo "发送100个事件在3秒内..."
for i in $(seq 1 100); do
    # 所有事件集中在3秒内
    OFFSET=$((i % 3))
    TIMESTAMP=$((BASE_TIME + OFFSET))
    
    # 多样化的IP和端口
    IP_SUFFIX=$((i % 50 + 1))
    PORT=$((3000 + (i % 10)))
    
    send_event $TIMESTAMP "10.0.0.$IP_SUFFIX" $PORT
    
    # 每10个事件显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo "  已发送 $i/100 个事件"
    fi
done

echo "✅ 爆发式攻击事件发送完成"
echo ""

# 等待Tier 1窗口(30秒)触发
wait_for_processing 35

echo "查询Tier 1 (30秒窗口) 评分结果..."
docker logs stream-processing 2>&1 | grep -A 5 "Tier 1 window.*$CUSTOMER_ID.*$ATTACK_MAC" | tail -20

echo ""
echo "----------------------------------------"
echo "测试场景 2: 分散式攻击 (300秒内100个事件)"
echo "预期: 低爆发强度系数 (BIC ≈ 0.01)"
echo "预期: 低时间分布权重 (≈ 1.02)"
echo "----------------------------------------"

cleanup

# 新的客户ID避免冲突
CUSTOMER_ID="customer_test_distributed"

# 新的起始时间
BASE_TIME=$(($(date +%s) + 10))

echo "发送100个事件均匀分布在300秒内..."
for i in $(seq 1 100); do
    # 事件均匀分布在300秒
    OFFSET=$((i * 3))  # 每3秒一个事件
    TIMESTAMP=$((BASE_TIME + OFFSET))
    
    # 多样化的IP和端口
    IP_SUFFIX=$((i % 50 + 1))
    PORT=$((3000 + (i % 10)))
    
    send_event $TIMESTAMP "10.0.0.$IP_SUFFIX" $PORT
    
    # 每10个事件显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo "  已发送 $i/100 个事件"
    fi
done

echo "✅ 分散式攻击事件发送完成"
echo ""

# 由于事件跨度300秒，我们需要等待Tier 2窗口(5分钟)触发
wait_for_processing 320

echo "查询Tier 2 (5分钟窗口) 评分结果..."
docker logs stream-processing 2>&1 | grep -A 5 "Tier 2 window.*$CUSTOMER_ID.*$ATTACK_MAC" | tail -20

echo ""
echo "=================================================="
echo "测试总结"
echo "=================================================="
echo ""
echo "场景 1 (爆发式): 100个事件在3秒内"
echo "  - 预期爆发强度: 0.99 (99%集中)"
echo "  - 预期时间分布权重: 2.98"
echo "  - 预期威胁评分: 基础分 × 2.98 (约3倍提升)"
echo ""
echo "场景 2 (分散式): 100个事件在300秒内"
echo "  - 预期爆发强度: 0.01 (1%集中)"
echo "  - 预期时间分布权重: 1.02"
echo "  - 预期威胁评分: 基础分 × 1.02 (几乎无提升)"
echo ""
echo "评分差异: 场景1应约为场景2的 2.92 倍"
echo ""
echo "查看完整日志:"
echo "  docker logs stream-processing 2>&1 | grep -E 'timeDistWeight|burstIntensity|timeSpan'"
echo ""
echo "清理测试数据:"
echo "  docker exec postgres psql -U postgres -d threat_detection -c \"DELETE FROM attack_events WHERE customer_id LIKE 'customer_test_%';\""
echo ""
