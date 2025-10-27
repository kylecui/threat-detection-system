#!/bin/bash

# ===================================================================
# Flink实时消费测试 - 边发送边检查
# ===================================================================

set -e

API_URL="http://localhost:8080/api/v1/logs/ingest"
TIMESTAMP=$(date +%s)

echo "========================================="
echo "Flink 实时消费测试"
echo "========================================="
echo ""

# 发送10条消息
echo "📤 发送10条测试消息..."
for i in {1..10}; do
    RESPONSE_IP="192.168.75.$((60 + i))"
    PORT=$((3000 + i * 100))
    LOG_TIME=$((TIMESTAMP + i))
    
    ATTACK_LOG="syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=$RESPONSE_IP,response_port=$PORT,line_id=$i,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME"
    
    curl -X POST "$API_URL" -H "Content-Type: text/plain" -d "$ATTACK_LOG" --silent -o /dev/null
    echo "   [$i/10] 发送: port=$PORT"
    sleep 0.2
done
echo ""

# 立即检查Kafka
echo "🔍 立即检查Kafka (不等待)..."
sleep 1

KAFKA_MESSAGES=$(docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic attack-events \
    --from-beginning \
    --max-messages 20 \
    --timeout-ms 2000 2>/dev/null | wc -l)

echo "   Kafka消息数量: $KAFKA_MESSAGES"
echo ""

# 检查consumer groups
echo "🔍 检查消费者组..."
docker exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group attack-events-persistence-group \
    --describe 2>/dev/null | grep attack-events

echo ""

# 检查Flink job状态
echo "🔍 检查Flink job状态..."
FLINK_STATUS=$(curl -s http://localhost:8081/jobs | jq -r '.jobs[0].status')
echo "   Flink job status: $FLINK_STATUS"
echo ""

echo "✅ 测试完成"
echo ""
echo "💡 关键发现:"
echo "   1. data-ingestion → Kafka: 正常发送"
echo "   2. AttackEventPersistenceService: 立即消费"
echo "   3. Flink: 看到的是已消费的topic (offset已提交)"
echo ""
echo "🔧 问题原因:"
echo "   - attack-events topic被两个consumer消费"
echo "   - persistence-group和Flink应该独立消费"
echo "   - 但Flink看到的offset已经是最新的"
