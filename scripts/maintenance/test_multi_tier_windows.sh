#!/bin/bash

echo "=========================================="
echo "3层时间窗口测试"
echo "=========================================="
echo ""
echo "📊 窗口设计:"
echo "  - Tier 1: 30秒窗口 (勒索软件检测)"
echo "  - Tier 2: 5分钟窗口 (主要威胁检测)"
echo "  - Tier 3: 15分钟窗口 (APT慢速扫描)"
echo ""
echo "🔬 测试策略:"
echo "  1. 持续发送攻击事件 (每10秒发送一批)"
echo "  2. 观察3层窗口的触发时间"
echo "  3. 验证不同窗口的告警生成"
echo ""

KAFKA_CONTAINER="kafka"
TOPIC="attack-events"

# 测试场景：高分数场景确保触发CRITICAL告警
ATTACK_MAC="AA:BB:CC:DD:EE:FF"
ATTACK_IP="192.168.10.50"
HONEYPOT_IP="192.168.1.100"
RESPONSE_PORT=3306
DEV_SERIAL="DEV001"
CUSTOMER_ID="default"

echo "🎯 测试场景: 数据库服务器攻击办公蜜罐"
echo "  - 攻击源: $ATTACK_IP ($ATTACK_MAC)"
echo "  - 目标: $HONEYPOT_IP:$RESPONSE_PORT"
echo "  - 预期: attackSourceWeight=3.0 × honeypotSensitivityWeight=1.3 = 3.9"
echo ""

# 发送函数
send_events() {
    local count=$1
    local batch_num=$2
    
    echo "📤 批次 #$batch_num: 发送 $count 个事件..."
    
    for i in $(seq 1 $count); do
        TIMESTAMP=$(date +%s)
        MESSAGE=$(cat <<-END
{
  "id": "evt-$(uuidgen)",
  "attackMac": "$ATTACK_MAC",
  "attackIp": "$ATTACK_IP",
  "responseIp": "$HONEYPOT_IP",
  "responsePort": $RESPONSE_PORT,
  "devSerial": "$DEV_SERIAL",
  "customerId": "$CUSTOMER_ID",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "logTime": $TIMESTAMP
}
END
        )
        
        echo "$MESSAGE" | docker exec -i $KAFKA_CONTAINER \
            kafka-console-producer \
            --bootstrap-server localhost:9092 \
            --topic $TOPIC \
            --property parse.key=true \
            --property key.separator=: \
            <<< "$CUSTOMER_ID:$MESSAGE" 2>/dev/null
    done
    
    echo "✅ 批次 #$batch_num 完成"
}

# 查询告警函数
check_alerts() {
    echo ""
    echo "📊 当前告警统计:"
    docker exec -i postgres psql -U threat_user -d threat_detection -c "
        SELECT 
            substring(metadata from 'tier\":([0-9]+)') as tier,
            substring(metadata from 'windowType\":\"([^\"]+)') as window_type,
            COUNT(*) as alert_count,
            MAX(created_at) as latest_alert
        FROM alerts 
        WHERE attack_mac = '$ATTACK_MAC' 
          AND created_at > NOW() - INTERVAL '20 minutes'
        GROUP BY tier, window_type
        ORDER BY tier;
    " 2>/dev/null | head -20
}

echo "🚀 开始持续发送事件..."
echo ""

# 第1批: 初始事件 (0分钟)
send_events 50 1
check_alerts

echo ""
echo "⏳ 等待30秒，观察Tier 1窗口触发..."
sleep 30

# 第2批: 30秒后 (触发Tier 1)
send_events 50 2
check_alerts

echo ""
echo "⏳ 等待90秒，继续发送保持活跃..."
sleep 90

# 第3批: 2分钟后
send_events 50 3
check_alerts

echo ""
echo "⏳ 等待120秒，继续发送保持活跃..."
sleep 120

# 第4批: 4分钟后
send_events 50 4
check_alerts

echo ""
echo "⏳ 等待60秒，观察Tier 2窗口触发 (5分钟)..."
sleep 60

# 第5批: 5分钟后 (触发Tier 2)
send_events 50 5

echo ""
echo "⏳ 等待30秒让窗口处理..."
sleep 30

check_alerts

echo ""
echo "⏳ 继续发送以触发Tier 3 (15分钟)..."
echo "   (需要继续运行约10分钟)"

# 继续发送直到15分钟
for batch in {6..15}; do
    sleep 60
    send_events 30 $batch
    
    if [ $batch -eq 15 ]; then
        echo ""
        echo "⏳ 等待30秒，观察Tier 3窗口触发 (15分钟)..."
        sleep 30
        check_alerts
    fi
done

echo ""
echo "=========================================="
echo "✅ 测试完成"
echo "=========================================="
echo ""
echo "📋 最终结果:"
check_alerts

echo ""
echo "📌 验证要点:"
echo "  1. Tier 1 (30秒): 应该有多个告警"
echo "  2. Tier 2 (5分钟): 应该在5分钟标记触发"
echo "  3. Tier 3 (15分钟): 应该在15分钟标记触发"
echo ""
