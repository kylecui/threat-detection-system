#!/bin/bash

echo "🧪 V4.0 Phase 2 快速测试"
echo "================================"

# 发送单个测试事件
echo ""
echo "📤 发送测试事件..."
curl -s -X POST "http://localhost:8080/api/v1/logs/ingest" \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: customer-001" \
  -d '{
    "dev_serial": "TEST-001",
    "attack_mac": "00:11:22:33:44:55",
    "attack_ip": "192.168.50.10",
    "response_ip": "10.10.20.100",
    "response_port": 3389,
    "timestamp": '$(date +%s)'
  }' && echo "✅ 事件已发送"

echo ""
echo "⏳ 等待5秒..."
sleep 5

echo ""
echo "📊 检查 data-ingestion 日志:"
docker logs data-ingestion-service 2>&1 | grep "192.168.50.10" | tail -3

echo ""
echo "📊 检查 stream-processing 日志:"
docker logs stream-processing 2>&1 | grep -i "process\|event" | tail -5

echo ""
echo "================================"
echo "如果看到日志输出，说明数据流正常工作"
