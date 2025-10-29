#!/bin/bash

# ===================================================================
# 时间分布评分功能验证测试
# 发送爆发式攻击数据，验证时间分布权重
# ===================================================================

set -e

API_URL="http://localhost:8080/api/v1/logs/ingest"
BASE_TIME=$(date +%s)
CUSTOMER_ID="customer_c"  # 使用现有客户
ATTACK_MAC="AA:BB:CC:DD:EE:01"
ATTACK_IP="192.168.88.88"
DEV_SERIAL="TEST_BURST_DEVICE"

echo "========================================================="
echo "时间分布评分功能验证"
echo "========================================================="
echo ""
echo "测试目标: 验证爆发式攻击的时间分布权重计算"
echo "基准时间: $(date -d @$BASE_TIME '+%Y-%m-%d %H:%M:%S')"
echo ""

# 场景1: 爆发式攻击 - 100个事件在3秒内
echo "📤 场景1: 爆发式攻击 (100个事件在3秒内)"
echo "   预期: 高爆发强度系数 (BIC ≈ 0.93), 高时间分布权重 (≈ 2.86)"
echo ""

echo -n "   发送进度: "
for i in {1..100}; do
    # 时间偏移: 0, 1, 2, 0, 1, 2, ... (所有事件集中在3秒内)
    TIME_OFFSET=$((i % 3))
    LOG_TIME=$((BASE_TIME + TIME_OFFSET))
    
    # IP和端口多样化 (50个IP, 10个端口)
    IP_SUFFIX=$(( (i - 1) % 50 + 1 ))
    PORT_INDEX=$(( (i - 1) % 10 ))
    PORTS=(22 80 443 3306 3389 445 8080 5432 1433 6379)
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
echo "   ✅ 100个事件发送完成"
echo ""

# 显示时间分布信息
echo "📊 事件时间分布:"
echo "   首个事件时间: $(date -d @$BASE_TIME '+%H:%M:%S')"
echo "   最后事件时间: $(date -d @$((BASE_TIME + 2)) '+%H:%M:%S')"
echo "   实际时间跨度: 2秒"
echo ""

echo "📐 预期计算 (基于30秒窗口):"
echo "   爆发强度系数 BIC = 1 - (2秒 / 30秒) = 0.933"
echo "   时间分布权重 = 1.0 + (0.933 × 2.0) = 2.87"
echo "   威胁评分提升: 约 2.87倍 🔴"
echo ""

# 检查数据持久化
echo "🔍 检查PostgreSQL持久化..."
sleep 3
PG_COUNT=$(docker exec -e PGPASSWORD=threat_detection_pass postgres psql -U threat_user -d threat_detection -t -c "SELECT COUNT(*) FROM attack_events WHERE attack_mac = '$ATTACK_MAC' AND created_at > NOW() - INTERVAL '1 minute';" 2>/dev/null | tr -d ' ')
echo "   最近1分钟插入的记录数: $PG_COUNT"

if [ "$PG_COUNT" -gt 0 ]; then
    echo "   ✅ 数据已成功持久化到PostgreSQL"
    
    # 查询实际时间跨度
    ACTUAL_SPAN=$(docker exec -e PGPASSWORD=threat_detection_pass postgres psql -U threat_user -d threat_detection -t -c "SELECT ROUND(EXTRACT(EPOCH FROM (MAX(event_timestamp) - MIN(event_timestamp)))) FROM attack_events WHERE attack_mac = '$ATTACK_MAC' AND created_at > NOW() - INTERVAL '1 minute';" 2>/dev/null | tr -d ' ')
    echo "   数据库中实际时间跨度: ${ACTUAL_SPAN}秒"
else
    echo "   ⚠️  数据尚未到达PostgreSQL"
fi
echo ""

# 等待Flink处理
echo "⏳ 等待Flink处理窗口 (35秒 = 30秒窗口 + 5秒缓冲)..."
for i in {35..1}; do
    echo -ne "\r   倒计时: $i 秒  "
    sleep 1
done
echo -e "\n"

# 查询Flink日志
echo "📋 查询Flink日志中的时间分布信息:"
echo "========================================================="

# 查找包含该攻击者的窗口处理日志
FLINK_LOG=$(docker logs stream-processing 2>&1 | grep -E "Tier.*$ATTACK_MAC" | tail -5)

if [ -n "$FLINK_LOG" ]; then
    echo "$FLINK_LOG"
    echo ""
    
    # 提取时间分布权重信息（如果日志中有的话）
    DIST_WEIGHT=$(docker logs stream-processing 2>&1 | grep "$ATTACK_MAC" | grep -oP 'timeDistWeight=\K[0-9.]+' | tail -1)
    BURST_INT=$(docker logs stream-processing 2>&1 | grep "$ATTACK_MAC" | grep -oP 'burstIntensity=\K[0-9.]+' | tail -1)
    
    if [ -n "$DIST_WEIGHT" ] && [ -n "$BURST_INT" ]; then
        echo "🎯 检测到时间分布权重:"
        echo "   爆发强度系数: $BURST_INT"
        echo "   时间分布权重: $DIST_WEIGHT"
        echo ""
        
        # 验证权重是否符合预期
        if (( $(echo "$DIST_WEIGHT > 2.5" | bc -l) )); then
            echo "   ✅ 时间分布权重 > 2.5, 爆发式攻击特征已识别!"
        else
            echo "   ⚠️  时间分布权重较低, 可能需要检查时间戳"
        fi
    else
        echo "   ℹ️  日志中未找到时间分布权重字段"
        echo "   提示: 检查日志格式是否包含 timeDistWeight 和 burstIntensity"
    fi
else
    echo "   ⚠️  未找到Flink窗口处理日志"
    echo "   可能原因:"
    echo "   1. 窗口还未触发 (需要等待更长时间)"
    echo "   2. 数据未到达Kafka"
    echo "   3. Flink作业未运行"
fi

echo ""
echo "========================================================="
echo "测试完成"
echo "========================================================="
echo ""
echo "💡 手动验证方法:"
echo ""
echo "1. 查看完整Flink日志:"
echo "   docker logs stream-processing 2>&1 | grep -E '$ATTACK_MAC|timeDistWeight|burstIntensity'"
echo ""
echo "2. 查看数据库中的事件分布:"
echo "   docker exec -e PGPASSWORD=threat_detection_pass postgres psql -U threat_user -d threat_detection -c \\"
echo "     \"SELECT MIN(event_timestamp) as first, MAX(event_timestamp) as last,"
echo "      EXTRACT(EPOCH FROM (MAX(event_timestamp) - MIN(event_timestamp))) as span,"
echo "      COUNT(*) FROM attack_events WHERE attack_mac = '$ATTACK_MAC';\""
echo ""
echo "3. 查看告警:"
echo "   curl -s http://localhost:8083/api/alerts?severity=CRITICAL | jq '.[] | select(.description | contains(\"$ATTACK_MAC\"))'"
echo ""
