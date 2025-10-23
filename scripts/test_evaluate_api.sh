#!/bin/bash

# 测试 POST /api/v1/assessment/evaluate API
# 用于验证威胁评估API完整性修复

API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:8083}"
ASSESS_ENDPOINT="${API_GATEWAY_URL}/api/v1/assessment/evaluate"

echo "========================================="
echo "Testing POST /api/v1/assessment/evaluate"
echo "========================================="

# 测试案例1: 基本评估请求
echo -e "\n[Test 1] Basic threat assessment"
RESPONSE=$(curl -X POST "${ASSESS_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "test-customer-001",
    "attack_mac": "00:11:22:33:44:55",
    "attack_ip": "192.168.1.100",
    "attack_count": 150,
    "unique_ips": 5,
    "unique_ports": 3,
    "unique_devices": 2
  }' \
  -w "\n%{http_code}" \
  -s)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "$BODY" | jq '.'
echo "HTTP Status: $HTTP_CODE"

# 测试案例2: 高威胁评估 (大量攻击)
echo -e "\n[Test 2] High threat assessment"
RESPONSE=$(curl -X POST "${ASSESS_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "test-customer-001",
    "attack_mac": "AA:BB:CC:DD:EE:FF",
    "attack_ip": "10.0.0.50",
    "attack_count": 500,
    "unique_ips": 10,
    "unique_ports": 15,
    "unique_devices": 3
  }' \
  -w "\n%{http_code}" \
  -s)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "$BODY" | jq '.'
echo "HTTP Status: $HTTP_CODE"

# 测试案例3: 验证必填字段
echo -e "\n[Test 3] Validation - Missing customer_id (should fail)"
RESPONSE=$(curl -X POST "${ASSESS_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "attack_mac": "00:11:22:33:44:55",
    "attack_count": 100,
    "unique_ips": 3,
    "unique_ports": 2,
    "unique_devices": 1
  }' \
  -w "\n%{http_code}" \
  -s)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo "HTTP Status: $HTTP_CODE"

# 测试案例4: 验证数值范围
echo -e "\n[Test 4] Validation - Invalid attack_count (should fail)"
RESPONSE=$(curl -X POST "${ASSESS_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "test-customer-001",
    "attack_mac": "00:11:22:33:44:55",
    "attack_count": 0,
    "unique_ips": 3,
    "unique_ports": 2,
    "unique_devices": 1
  }' \
  -w "\n%{http_code}" \
  -s)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo "HTTP Status: $HTTP_CODE"

# 测试案例5: 带时间戳的评估
echo -e "\n[Test 5] Assessment with custom timestamp"
CURRENT_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
RESPONSE=$(curl -X POST "${ASSESS_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d "{
    \"customer_id\": \"test-customer-001\",
    \"attack_mac\": \"11:22:33:44:55:66\",
    \"attack_ip\": \"172.16.0.10\",
    \"attack_count\": 200,
    \"unique_ips\": 7,
    \"unique_ports\": 5,
    \"unique_devices\": 2,
    \"timestamp\": \"${CURRENT_TIME}\"
  }" \
  -w "\n%{http_code}" \
  -s)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "$BODY" | jq '.'
echo "HTTP Status: $HTTP_CODE"

echo -e "\n========================================="
echo "Testing Complete"
echo "========================================="
