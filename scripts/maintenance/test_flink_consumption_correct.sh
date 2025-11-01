#!/bin/bash

# ===================================================================
# Flink消费测试脚本 - 使用正确的日志格式
# 
# 目的: 验证Flink能够从Kafka消费attack-events并生成聚合
# 
# 根据文档: docs/api/data_ingestion_api.md
# 正确格式: syslog_version=1.10.0,dev_serial=DEV001,log_type=1,...
# ===================================================================

set -e

API_URL="http://localhost:8080/api/v1/logs/ingest"
TIMESTAMP=$(date +%s)

echo "========================================="
echo "Flink 消费测试 - 正确日志格式"
echo "========================================="
echo ""

echo "📝 测试数据准备:"
echo "  - 设备序列号: GSFB2204200410007425"
echo "  - 攻击MAC: 04:42:1a:8e:e3:65"
echo "  - 攻击IP: 192.168.75.188"
echo "  - 诱饵IP范围: 192.168.75.67-71"
echo "  - 端口: 3389 (RDP), 445 (SMB), 22 (SSH)"
echo ""

# 1. 先检查数据摄取服务健康状态
echo "🔍 步骤1: 检查数据摄取服务健康状态"
HEALTH_STATUS=$(curl -s http://localhost:8080/api/v1/logs/health | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
echo "   健康状态: $HEALTH_STATUS"

if [ "$HEALTH_STATUS" != "UP" ]; then
    echo "   ⚠️  警告: 服务状态不是UP,继续尝试..."
fi
echo ""

# 2. 发送测试攻击日志 (正确的键值对格式)
echo "📤 步骤2: 发送5条测试攻击日志"

for i in {1..5}; do
    # 计算动态字段
    RESPONSE_IP="192.168.75.$((67 + i - 1))"
    PORTS=(3389 445 22 3306 8080)
    PORT=${PORTS[$((i - 1))]}
    LOG_TIME=$((TIMESTAMP + i))
    
    # 构造正确格式的日志 (key=value,key=value)
    ATTACK_LOG="syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=$RESPONSE_IP,response_port=$PORT,line_id=$i,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME"
    
    echo "   [$i/5] 发送: response_ip=$RESPONSE_IP, port=$PORT"
    
    HTTP_CODE=$(curl -X POST "$API_URL" \
        -H "Content-Type: text/plain" \
        -d "$ATTACK_LOG" \
        --silent --write-out "%{http_code}" --output /dev/null)
    
    if [ "$HTTP_CODE" = "200" ]; then
        echo "        ✅ 成功 (HTTP $HTTP_CODE)"
    else
        echo "        ❌ 失败 (HTTP $HTTP_CODE)"
    fi
    
    sleep 0.3
done
echo ""

# 3. 等待数据写入Kafka
echo "⏳ 步骤3: 等待3秒,让数据写入Kafka..."
sleep 3
echo ""

# 4. 验证Kafka中的消息
echo "🔍 步骤4: 检查attack-events topic中的消息"
KAFKA_MESSAGES=$(docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic attack-events \
    --from-beginning \
    --max-messages 5 \
    --timeout-ms 3000 2>/dev/null | wc -l)

echo "   Kafka消息数量: $KAFKA_MESSAGES"

if [ "$KAFKA_MESSAGES" -gt 0 ]; then
    echo "   ✅ Kafka中有消息!"
    echo ""
    echo "   样例消息 (第1条):"
    docker exec kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic attack-events \
        --from-beginning \
        --max-messages 1 \
        --timeout-ms 2000 2>/dev/null | jq '.' || echo "   (无法解析JSON)"
else
    echo "   ❌ Kafka中没有消息!"
    echo ""
    echo "   🔍 检查data-ingestion日志中的错误:"
    docker logs data-ingestion-service 2>&1 | grep -i "error\|failed" | tail -5
fi
echo ""

# 5. 等待Flink处理
echo "⏳ 步骤5: 等待30秒,让Flink处理数据..."
echo "   (30秒窗口 + 处理时间)"
sleep 30
echo ""

# 6. 检查Flink是否开始消费
echo "🔍 步骤6: 检查Flink消费者组"
CONSUMER_GROUPS=$(docker exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --list 2>/dev/null)

echo "   当前消费者组列表:"
echo "$CONSUMER_GROUPS" | sed 's/^/     - /'
echo ""

if echo "$CONSUMER_GROUPS" | grep -q "threat-detection-stream"; then
    echo "   ✅ 找到Flink消费者组: threat-detection-stream"
    echo ""
    echo "   消费者组详情:"
    docker exec kafka kafka-consumer-groups \
        --bootstrap-server localhost:9092 \
        --group threat-detection-stream \
        --describe 2>/dev/null | head -10
else
    echo "   ❌ 未找到Flink消费者组: threat-detection-stream"
    echo "   这意味着Flink还没有开始消费消息"
fi
echo ""

# 7. 检查Flink日志中的反序列化活动
echo "🔍 步骤7: 检查Flink日志中的处理活动"
FLINK_LOGS=$(docker logs stream-processing 2>&1 | tail -50)

DESERIALIZE_COUNT=$(echo "$FLINK_LOGS" | grep -c "deserialize\|Attempting to" || echo "0")
AGGREGATION_COUNT=$(echo "$FLINK_LOGS" | grep -c "aggregation\|window" || echo "0")

echo "   反序列化日志数量: $DESERIALIZE_COUNT"
echo "   聚合日志数量: $AGGREGATION_COUNT"

if [ "$DESERIALIZE_COUNT" -gt 0 ]; then
    echo "   ✅ Flink正在反序列化消息!"
    echo ""
    echo "   最新Flink日志 (最后10行):"
    echo "$FLINK_LOGS" | tail -10 | sed 's/^/     /'
else
    echo "   ❌ Flink没有反序列化活动"
fi
echo ""

# 8. 检查minute-aggregations topic
echo "🔍 步骤8: 检查minute-aggregations topic"
AGGREGATION_MESSAGES=$(docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic minute-aggregations \
    --from-beginning \
    --max-messages 5 \
    --timeout-ms 3000 2>/dev/null | wc -l)

echo "   minute-aggregations消息数量: $AGGREGATION_MESSAGES"

if [ "$AGGREGATION_MESSAGES" -gt 0 ]; then
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
fi
echo ""

# 9. 总结
echo "========================================="
echo "📊 测试总结"
echo "========================================="
echo "数据摄取服务: $([[ "$HEALTH_STATUS" == "UP" ]] && echo "✅ 正常" || echo "⚠️  异常")"
echo "Kafka消息写入: $([[ "$KAFKA_MESSAGES" -gt 0 ]] && echo "✅ 成功 ($KAFKA_MESSAGES条)" || echo "❌ 失败")"
echo "Flink消费者组: $(echo "$CONSUMER_GROUPS" | grep -q "threat-detection-stream" && echo "✅ 已注册" || echo "❌ 未注册")"
echo "Flink反序列化: $([[ "$DESERIALIZE_COUNT" -gt 0 ]] && echo "✅ 活跃 ($DESERIALIZE_COUNT次)" || echo "❌ 无活动")"
echo "聚合数据生成: $([[ "$AGGREGATION_MESSAGES" -gt 0 ]] && echo "✅ 成功 ($AGGREGATION_MESSAGES条)" || echo "❌ 失败")"
echo ""

if [ "$KAFKA_MESSAGES" -gt 0 ] && [ "$AGGREGATION_MESSAGES" -gt 0 ]; then
    echo "🎉 Flink流处理管道测试通过!"
    exit 0
elif [ "$KAFKA_MESSAGES" -gt 0 ]; then
    echo "⚠️  数据写入成功,但Flink未生成聚合 - 需要进一步调试"
    exit 1
else
    echo "❌ 数据写入失败 - 检查data-ingestion服务"
    exit 1
fi
