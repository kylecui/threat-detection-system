#!/bin/bash

# 时间分布评分 - 爆发式攻击验证测试
# 测试目标: 100个事件在3秒内，预期BIC≈0.93, timeDistWeight≈2.87

echo "========================================================="
echo "爆发式攻击测试 - 时间分布权重验证"
echo "========================================================="
echo ""

API_URL="http://localhost:8080/api/v1/logs/ingest"
DEV_SERIAL="TESTDEV001"  # 符合规范: 只包含字母数字
ATTACK_MAC="BB:CC:DD:EE:FF:01"
ATTACK_IP="192.168.200.100"

# 基准时间
BASE_TIME=$(date +%s)
echo "测试开始时间: $(date -d @$BASE_TIME '+%Y-%m-%d %H:%M:%S')"
echo ""

echo "📤 发送100个攻击事件 (3秒内完成)..."
echo "   设备序列号: $DEV_SERIAL"
echo "   攻击MAC: $ATTACK_MAC"
echo "   预期BIC: 0.93"
echo "   预期timeDistWeight: 2.87"
echo ""

# 发送事件
for i in $(seq 1 100); do
    # 时间偏移: 0, 1, 2秒 (循环)
    TIME_OFFSET=$((i % 3))
    LOG_TIME=$((BASE_TIME + TIME_OFFSET))
    
    # 生成变化的IP和端口
    IP_SUFFIX=$((i % 50 + 1))
    PORT_INDEX=$((i % 10))
    
    PORTS=(3389 445 22 3306 8080 1433 135 443 21 25)
    PORT=${PORTS[$PORT_INDEX]}
    
    RESPONSE_IP="10.88.0.$IP_SUFFIX"
    
    # 构造syslog消息
    ATTACK_LOG="syslog_version=1.10.0,dev_serial=$DEV_SERIAL,log_type=1,sub_type=1,attack_mac=$ATTACK_MAC,attack_ip=$ATTACK_IP,response_ip=$RESPONSE_IP,response_port=$PORT,line_id=$i,Iface_type=1,Vlan_id=30,log_time=$LOG_TIME"
    
    # 发送到data-ingestion
    curl -X POST "$API_URL" \
        -H "Content-Type: text/plain" \
        -d "$ATTACK_LOG" \
        --silent -o /dev/null
    
    # 显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo -n "$i "
    fi
done

echo ""
echo "✅ 100个事件发送完成"
echo ""

# 显示时间分布
echo "📊 事件时间分布:"
echo "   首个事件: $(date -d @$BASE_TIME '+%H:%M:%S')"
echo "   最后事件: $(date -d @$((BASE_TIME + 2)) '+%H:%M:%S')"
echo "   实际时间跨度: 2秒"
echo ""

echo "📐 预期计算 (30秒窗口):"
echo "   爆发强度系数 BIC = 1 - (2秒 / 30秒) = 0.933"
echo "   时间分布权重 = 1.0 + (0.933 × 2.0) = 2.87"
echo "   威胁评分提升: 约 2.87倍 🔴"
echo ""

echo "⏳ 等待Flink处理 (40秒)..."
sleep 40

echo ""
echo "🔍 验证结果:"
echo "========================================================="

# 检查PostgreSQL
echo ""
echo "1️⃣  数据库时间分布:"
docker exec -e PGPASSWORD=threat_detection_pass postgres psql -U threat_user -d threat_detection -c \
    "SELECT 
        MIN(event_timestamp) as first_event,
        MAX(event_timestamp) as last_event,
        EXTRACT(EPOCH FROM (MAX(event_timestamp) - MIN(event_timestamp))) as span_seconds,
        COUNT(*) as total_events
     FROM attack_events 
     WHERE attack_mac = '$ATTACK_MAC';"

echo ""
echo "2️⃣  Flink日志 - 时间分布权重:"
docker logs stream-processing 2>&1 | grep -E "$ATTACK_MAC" | grep -E "timeDistWeight|burstIntensity" | tail -5

echo ""
echo "========================================================="
echo "测试完成"
echo "========================================================="
echo ""
echo "💡 如果timeDistWeight接近2.87，说明时间分布评分功能正常工作！"
echo ""
