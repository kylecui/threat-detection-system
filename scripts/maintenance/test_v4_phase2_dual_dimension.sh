#!/bin/bash

##################################################
# V4.0 Phase 2 双维度权重集成测试
# 测试: attackSourceWeight × honeypotSensitivityWeight
##################################################

set -e

DATA_INGESTION_URL="http://localhost:8080/api/v1/logs/ingest"
CUSTOMER_ID="customer-001"

echo "=================================================="
echo "V4.0 Phase 2 双维度权重测试"
echo "=================================================="

echo ""
echo "📊 测试场景设计:"
echo "  场景1: IoT设备(0.9) × 管理蜜罐(3.5) = 3.15 → 预期CRITICAL"
echo "  场景2: 数据库服务器(3.0) × 办公蜜罐(1.3) = 3.9 → 预期CRITICAL"  
echo "  场景3: 办公设备(1.0) × 办公蜜罐(1.3) = 1.3 → 预期MEDIUM"
echo ""

# 等待服务就绪
echo "⏳ 等待服务启动..."
sleep 3

# 测试场景1: IoT设备攻击DMZ管理蜜罐
echo ""
echo "🔍 场景1: 生产设备(192.168.50.10) 攻击 DMZ蜜罐(10.10.20.100)"
echo "  - attackSourceWeight: 0.9 (生产-192.168.50.0/24)"
echo "  - honeypotSensitivityWeight: 3.5 (DMZ-10.10.20.0/24)"
echo "  - 组合权重: 0.9 × 3.5 = 3.15"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..150}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV001,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.50.10,response_ip=10.10.20.100,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME" > /dev/null
done

echo "✅ 已发送 150 个事件 (IoT → 管理蜜罐)"

TIMESTAMP=$(date +%s)
for i in {1..150}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV-001,log_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.50.10,response_ip=10.10.20.100,response_port=3389,log_time=$LOG_TIME" > /dev/null
done

echo "✅ 已发送 150 个事件 (IoT → 管理蜜罐)"

# 测试场景2: 数据库服务器攻击办公蜜罐
echo ""
echo "🔍 场景2: 数据库服务器(192.168.10.50) 攻击 办公蜜罐(192.168.1.100)"
echo "  - attackSourceWeight: 3.0 (数据库-192.168.10.0/24)"
echo "  - honeypotSensitivityWeight: 1.3 (办公网段-192.168.1.0/24)"
echo "  - 组合权重: 3.0 × 1.3 = 3.9"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..120}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV002,log_type=1,sub_type=1,attack_mac=AA:BB:CC:DD:EE:FF,attack_ip=192.168.10.50,response_ip=192.168.1.100,response_port=445,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME" > /dev/null
done

echo "✅ 已发送 120 个事件 (数据库 → 办公蜜罐)"

# 测试场景3: 办公设备攻击办公蜜罐
echo ""
echo "🔍 场景3: 办公设备(192.168.1.50) 攻击 办公蜜罐(192.168.1.200)"
echo "  - attackSourceWeight: 1.0 (办公-192.168.1.0/24)"
echo "  - honeypotSensitivityWeight: 1.3 (办公网段-192.168.1.0/24)"
echo "  - 组合权重: 1.0 × 1.3 = 1.3"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..80}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV003,log_type=1,sub_type=1,attack_mac=11:22:33:44:55:66,attack_ip=192.168.1.50,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME" > /dev/null
done

echo "✅ 已发送 80 个事件 (办公 → 办公蜜罐)"

# 等待处理
echo ""
echo "⏳ 等待流处理完成（4分钟窗口 + 评分窗口）..."
sleep 270

# 检查日志
echo ""
echo "=================================================="
echo "📊 检查威胁评分日志"
echo "=================================================="

echo ""
echo "🔍 场景1结果 (IoT → 管理蜜罐):"
docker logs threat-assessment-service 2>&1 | grep -A 2 "00:11:22:33:44:55" | grep -E "(attackSource|honeypot|combinedSegmentWeight|threatScore|threatLevel)" | tail -10

echo ""
echo "🔍 场景2结果 (数据库 → 办公蜜罐):"
docker logs threat-assessment-service 2>&1 | grep -A 2 "AA:BB:CC:DD:EE:FF" | grep -E "(attackSource|honeypot|combinedSegmentWeight|threatScore|threatLevel)" | tail -10

echo ""
echo "🔍 场景3结果 (办公 → 办公蜜罐):"
docker logs threat-assessment-service 2>&1 | grep -A 2 "11:22:33:44:55:66" | grep -E "(attackSource|honeypot|combinedSegmentWeight|threatScore|threatLevel)" | tail -10

echo ""
echo "=================================================="
echo "✅ 测试完成"
echo "=================================================="

echo ""
echo "📋 验证要点:"
echo "  1. 日志应显示 attackSourceWeight 和 honeypotSensitivityWeight"
echo "  2. combinedSegmentWeight = attackSourceWeight × honeypotSensitivityWeight"
echo "  3. 场景1: 组合权重应为 3.15 (0.9 × 3.5)"
echo "  4. 场景2: 组合权重应为 3.9 (3.0 × 1.3)"
echo "  5. 场景3: 组合权重应为 1.3 (1.0 × 1.3)"
echo ""
