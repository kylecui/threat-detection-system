#!/bin/bash
# 发送burst攻击测试数据 - 测试Event Time窗口

KAFKA_CONTAINER="kafka"
TOPIC="attack-events"
CUSTOMER_ID="customer-001"
ATTACK_MAC="BB:CC:DD:EE:FF:01"

# 获取当前时间戳
BASE_TIMESTAMP=$(date +%s)

echo "开始发送100个burst攻击事件..."
echo "基础时间戳: $BASE_TIMESTAMP"

# 发送100个事件（2秒时间跨度）
for i in {1..100}; do
    # 计算时间戳：前50个事件使用base_timestamp，后50个使用base_timestamp+1
    if [ $i -le 50 ]; then
        LOG_TIME=$BASE_TIMESTAMP
    else
        LOG_TIME=$((BASE_TIMESTAMP + 1))
    fi
    
    # 计算IP和端口变化
    IP_SUFFIX=$((100 + (i % 10)))
    RESPONSE_IP_SUFFIX=$((1 + (i % 5)))
    PORT=$((3389 + (i % 3)))
    
    # 构造JSON消息
    MESSAGE=$(cat <<EOF
{
  "attackMac": "$ATTACK_MAC",
  "attackIp": "192.168.1.$IP_SUFFIX",
  "responseIp": "10.0.0.$RESPONSE_IP_SUFFIX",
  "responsePort": $PORT,
  "deviceSerial": "DEV-001",
  "customerId": "$CUSTOMER_ID",
  "logTime": $LOG_TIME
}
EOF
)
    
    # 发送到Kafka
    echo "$MESSAGE" | docker exec -i $KAFKA_CONTAINER kafka-console-producer.sh \
        --broker-list localhost:9092 \
        --topic $TOPIC \
        --property "parse.key=true" \
        --property "key.separator=:" \
        > /dev/null 2>&1 <<< "$CUSTOMER_ID:$MESSAGE"
    
    # 每10个事件显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo "已发送 $i/100 个事件"
    fi
    
    # 短暂延迟（模拟20ms间隔）
    sleep 0.02
done

echo "✅ 100个事件已发送完成"
echo "时间跨度: $BASE_TIMESTAMP - $((BASE_TIMESTAMP + 1)) (2秒)"
echo ""
echo "等待4分钟以查看聚合结果..."
echo "可以使用以下命令查看最新告警："
echo "  docker exec postgres psql -U postgres -d threat_detection -c \"SELECT id, attack_mac, tier, threat_score, attack_count, window_start, window_end FROM threat_alerts WHERE attack_mac = '$ATTACK_MAC' ORDER BY id DESC LIMIT 10;\""
