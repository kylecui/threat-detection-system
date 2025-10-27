#!/bin/bash

##################################################
# 端口权重系统端到端集成测试
# 测试端口权重在威胁评分中的作用
##################################################

set -e

DATA_INGESTION_URL="http://localhost:8080/api/v1/logs/ingest"
CUSTOMER_ID="test-customer"

echo "=================================================="
echo "端口权重系统 E2E 测试"
echo "=================================================="

echo ""
echo "📊 测试场景设计:"
echo "  场景1: 高危端口攻击 (端口3389, 权重10.0) → 预期高威胁分"
echo "  场景2: 中危端口攻击 (端口22, 权重5.0) → 预期中威胁分"
echo "  场景3: 低危端口攻击 (端口8080, 权重1.0) → 预期低威胁分"
echo ""

# 等待服务就绪
echo "⏳ 等待服务启动..."
sleep 3

# 测试场景1: 高危端口 RDP (3389)
echo ""
echo "🔍 场景1: 攻击高危端口 RDP (3389)"
echo "  - 端口: 3389 (Remote Desktop)"
echo "  - 端口权重: 10.0 (高危)"
echo "  - 预期: 高威胁评分"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..100}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV001,log_type=1,sub_type=1,attack_mac=AA:BB:CC:DD:EE:01,attack_ip=192.168.1.100,response_ip=10.0.0.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME,eth_type=2048,ip_type=6" > /dev/null
done

echo "✅ 已发送 100 个事件 (端口 3389)"

# 测试场景2: 中危端口 SSH (22)
echo ""
echo "🔍 场景2: 攻击中危端口 SSH (22)"
echo "  - 端口: 22 (SSH)"
echo "  - 端口权重: 5.0 (中危)"
echo "  - 预期: 中等威胁评分"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..100}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV002,log_type=1,sub_type=1,attack_mac=AA:BB:CC:DD:EE:02,attack_ip=192.168.1.101,response_ip=10.0.0.2,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME,eth_type=2048,ip_type=6" > /dev/null
done

echo "✅ 已发送 100 个事件 (端口 22)"

# 测试场景3: 低危端口 (8080)
echo ""
echo "🔍 场景3: 攻击低危端口 (8080)"
echo "  - 端口: 8080 (HTTP Alternate)"
echo "  - 端口权重: 1.0 (低危/默认)"
echo "  - 预期: 低威胁评分"
echo ""

TIMESTAMP=$(date +%s)
for i in {1..100}; do
  LOG_TIME=$((TIMESTAMP + i))
  curl -s -X POST "$DATA_INGESTION_URL" \
    -H "Content-Type: text/plain" \
    -H "X-Customer-Id: $CUSTOMER_ID" \
    -d "syslog_version=1.10.0,dev_serial=DEV003,log_type=1,sub_type=1,attack_mac=AA:BB:CC:DD:EE:03,attack_ip=192.168.1.102,response_ip=10.0.0.3,response_port=8080,line_id=1,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME,eth_type=2048,ip_type=6" > /dev/null
done

echo "✅ 已发送 100 个事件 (端口 8080)"

# 等待处理
echo ""
echo "⏳ 等待流处理完成（30秒窗口 + 2分钟评分窗口 + 缓冲）..."
echo "  - Tier 1 窗口: 30秒"
echo "  - 评分窗口: 2分钟"
echo "  - 总等待: 180秒"
sleep 180

# 检查结果
echo ""
echo "=================================================="
echo "📊 检查威胁评分结果"
echo "=================================================="

echo ""
echo "🔍 场景1结果 (端口 3389 - 高危):"
docker logs threat-assessment-service 2>&1 | grep -A 5 "AA:BB:CC:DD:EE:01" | grep -E "(portWeight|threatScore|threatLevel)" | tail -10

echo ""
echo "🔍 场景2结果 (端口 22 - 中危):"
docker logs threat-assessment-service 2>&1 | grep -A 5 "AA:BB:CC:DD:EE:02" | grep -E "(portWeight|threatScore|threatLevel)" | tail -10

echo ""
echo "🔍 场景3结果 (端口 8080 - 低危):"
docker logs threat-assessment-service 2>&1 | grep -A 5 "AA:BB:CC:DD:EE:03" | grep -E "(portWeight|threatScore|threatLevel)" | tail -10

# 检查数据库
echo ""
echo "=================================================="
echo "📊 检查数据库中的威胁评估记录"
echo "=================================================="

echo ""
echo "查询最近的威胁评估记录:"
docker exec -it postgres psql -U threat_user -d threat_detection -c "
SELECT 
    customer_id,
    attack_mac,
    threat_score,
    threat_level,
    attack_count,
    unique_ports,
    assessment_time
FROM threat_assessments
WHERE customer_id = '$CUSTOMER_ID'
  AND assessment_time > NOW() - INTERVAL '10 minutes'
ORDER BY threat_score DESC
LIMIT 10;
"

echo ""
echo "=================================================="
echo "✅ 测试完成"
echo "=================================================="

echo ""
echo "📋 验证要点:"
echo "  1. 日志应显示 portWeight 字段"
echo "  2. 高危端口 (3389) 的威胁分应该最高"
echo "  3. 中危端口 (22) 的威胁分应该居中"
echo "  4. 低危端口 (8080) 的威胁分应该最低"
echo "  5. 端口权重应该影响最终的 threatScore"
echo ""
