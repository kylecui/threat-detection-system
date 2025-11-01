#!/bin/bash

# ===================================================================
# Flink完整流程测试 - 等待Flink处理完整个窗口
# ===================================================================

set -e

API_URL="http://localhost:8080/api/v1/logs/ingest"
TIMESTAMP=$(date +%s)

echo "========================================="
echo "Flink 完整流程测试"
echo "========================================="
echo ""

# 第1步: 发送大量测试数据
echo "📤 步骤1: 发送50条测试数据 (模拟真实流量)..."
for i in {1..50}; do
    RESPONSE_IP="192.168.100.$((i % 250 + 1))"
    PORTS=(22 80 443 3306 3389 445 8080 5432 1433 6379)
    PORT=${PORTS[$((i % 10))]}
    LOG_TIME=$((TIMESTAMP + i))
    
    ATTACK_LOG="syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.100.100,response_ip=$RESPONSE_IP,response_port=$PORT,line_id=$i,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME"
    
    curl -X POST "$API_URL" -H "Content-Type: text/plain" -d "$ATTACK_LOG" --silent -o /dev/null
    
    if [ $((i % 10)) -eq 0 ]; then
        echo "   已发送: $i/50"
    fi
    
    sleep 0.1
done
echo "   ✅ 50条消息发送完成"
echo ""

# 第2步: 检查PostgreSQL中的记录
echo "🔍 步骤2: 检查PostgreSQL持久化..."
sleep 2
PG_COUNT=$(docker exec postgres psql -U threat_user -d threat_detection -t -c "SELECT COUNT(*) FROM attack_events WHERE created_at > NOW() - INTERVAL '1 minute';" | tr -d ' ')
echo "   最近1分钟插入的记录数: $PG_COUNT"
echo ""

# 第3步: 检查Kafka offset
echo "🔍 步骤3: 检查Kafka consumer groups offset..."
echo ""
echo "   attack-events-persistence-group (PostgreSQL持久化):"
docker exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group attack-events-persistence-group \
    --describe 2>/dev/null | grep attack-events | awk '{print "     CURRENT-OFFSET: " $4 ", LOG-END-OFFSET: " $5 ", LAG: " $6}'
echo ""

echo "   threat-detection-stream (Flink流处理):"
FLINK_GROUP_EXISTS=$(docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -c "threat-detection-stream" || echo "0")

if [ "$FLINK_GROUP_EXISTS" -gt 0 ]; then
    echo "     ✅ Flink consumer group已注册!"
    docker exec kafka kafka-consumer-groups \
        --bootstrap-server localhost:9092 \
        --group threat-detection-stream \
        --describe 2>/dev/null | grep attack-events | awk '{print "     CURRENT-OFFSET: " $4 ", LOG-END-OFFSET: " $5 ", LAG: " $6}'
else
    echo "     ❌ Flink consumer group未注册"
    echo "     说明: Flink还没有开始消费 (可能正在等待窗口时间)"
fi
echo ""

# 第4步: 等待Flink 30秒窗口
echo "⏳ 步骤4: 等待35秒 (30秒窗口 + 5秒处理缓冲)..."
echo "   窗口配置: TIER1=30s, TIER2=300s, TIER3=900s"
echo "   等待第一个窗口聚合完成..."

for i in {35..1}; do
    echo -ne "\r   倒计时: $i 秒  "
    sleep 1
done
echo ""
echo ""

# 第5步: 再次检查Flink consumer group
echo "🔍 步骤5: 窗口后再次检查Flink consumer group..."
FLINK_GROUP_EXISTS_AFTER=$(docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -c "threat-detection-stream" || echo "0")

if [ "$FLINK_GROUP_EXISTS_AFTER" -gt 0 ]; then
    echo "   ✅ Flink consumer group已注册!"
    docker exec kafka kafka-consumer-groups \
        --bootstrap-server localhost:9092 \
        --group threat-detection-stream \
        --describe 2>/dev/null | grep attack-events
else
    echo "   ❌ Flink consumer group仍未注册"
fi
echo ""

# 第6步: 检查minute-aggregations topic
echo "🔍 步骤6: 检查minute-aggregations topic (Flink输出)..."
AGGREGATIONS=$(docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic minute-aggregations \
    --from-beginning \
    --max-messages 10 \
    --timeout-ms 3000 2>/dev/null | wc -l)

echo "   聚合消息数量: $AGGREGATIONS"

if [ "$AGGREGATIONS" -gt 0 ]; then
    echo "   ✅ Flink已生成聚合!"
    echo ""
    echo "   聚合样例 (第1条):"
    docker exec kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic minute-aggregations \
        --from-beginning \
        --max-messages 1 \
        --timeout-ms 2000 2>/dev/null | jq '.'
else
    echo "   ❌ 没有聚合数据"
    echo ""
    echo "   Flink日志 (最后20行):"
    docker logs stream-processing 2>&1 | tail -20 | sed 's/^/     /'
fi
echo ""

# 第7步: 总结
echo "========================================="
echo "📊 测试总结"
echo "========================================="
echo "发送消息数: 50条"
echo "PostgreSQL持久化: $PG_COUNT 条 (persistence consumer)"
echo "Flink consumer group: $([[ "$FLINK_GROUP_EXISTS_AFTER" -gt 0 ]] && echo "✅ 已注册" || echo "❌ 未注册")"
echo "Flink聚合输出: $([[ "$AGGREGATIONS" -gt 0 ]] && echo "✅ $AGGREGATIONS 条" || echo "❌ 0条")"
echo ""

if [ "$AGGREGATIONS" -gt 0 ]; then
    echo "🎉 Flink流处理管道完全正常!"
    echo ""
    echo "数据流:"
    echo "  syslog → data-ingestion → Kafka (attack-events)"
    echo "                              ├→ PostgreSQL (persistence consumer)"
    echo "                              └→ Flink → minute-aggregations"
    exit 0
else
    echo "⚠️  Flink未生成聚合,需要进一步调试"
    echo ""
    echo "可能原因:"
    echo "  1. Flink配置的offset策略问题"
    echo "  2. Flink等待更长的窗口时间"
    echo "  3. Flink代码中的问题"
    exit 1
fi
