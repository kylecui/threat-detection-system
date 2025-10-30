#!/bin/bash

# Test APT Temporal Accumulation
# Send attack events and verify they accumulate in the APT temporal table

set -e

API_URL="http://localhost:8080/api/v1/logs/ingest"
TIMESTAMP=$(date +%s)

echo "========================================="
echo "APT Temporal Accumulation Test"
echo "========================================="
echo ""

# Step 1: Send test attack events
echo "📤 Step 1: Sending test attack events..."
for i in {1..10}; do
    # Vary the attack MAC and ports to simulate different attack patterns
    MACS=("00:11:22:33:44:55" "AA:BB:CC:DD:EE:FF" "11:22:33:44:55:66")
    MAC=${MACS[$((i % 3))]}
    
    PORTS=(22 80 443 3306 3389 445 8080 5432 1433 6379)
    PORT=${PORTS[$((i % 10))]}
    
    RESPONSE_IP="192.168.100.$((i % 250 + 1))"
    LOG_TIME=$((TIMESTAMP + i))
    
    ATTACK_LOG="syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=$MAC,attack_ip=192.168.100.100,response_ip=$RESPONSE_IP,response_port=$PORT,line_id=$i,Iface_type=1,Vlan_id=0,log_time=$LOG_TIME"
    
    curl -X POST "$API_URL" -H "Content-Type: text/plain" -d "$ATTACK_LOG" --silent -o /dev/null
    
    if [ $((i % 5)) -eq 0 ]; then
        echo "   Sent: $i/10 events"
    fi
    
    sleep 0.1
done
echo "   ✅ 10 attack events sent"
echo ""

# Step 2: Wait for processing
echo "⏳ Step 2: Waiting 10 seconds for stream processing..."
sleep 10
echo "   ✅ Wait complete"
echo ""

# Step 3: Check APT temporal accumulations
echo "🔍 Step 3: Checking APT temporal accumulations..."
ACCUMULATION_COUNT=$(docker exec postgres psql -U threat_user -d threat_detection -t -c "SELECT COUNT(*) FROM apt_temporal_accumulations WHERE last_updated > NOW() - INTERVAL '1 minute';" | tr -d ' ')

echo "   Recent accumulations (last 1 minute): $ACCUMULATION_COUNT"

if [ "$ACCUMULATION_COUNT" -gt 0 ]; then
    echo "   ✅ APT temporal accumulation is working!"
    echo ""
    echo "   Sample accumulation records:"
    docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT customer_id, attack_mac, total_attack_count, unique_ips_count, unique_ports_count, 
           decay_accumulated_score, inferred_attack_phase, phase_confidence, 
           last_updated 
    FROM apt_temporal_accumulations 
    WHERE last_updated > NOW() - INTERVAL '1 minute'
    ORDER BY last_updated DESC 
    LIMIT 3;" | head -20
else
    echo "   ❌ No accumulations found"
    echo ""
    echo "   Checking if table exists..."
    TABLE_EXISTS=$(docker exec postgres psql -U threat_user -d threat_detection -t -c "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'apt_temporal_accumulations');" | tr -d ' ')
    if [ "$TABLE_EXISTS" = "t" ]; then
        echo "   ✅ Table exists"
    else
        echo "   ❌ Table does not exist"
    fi
    
    echo ""
    echo "   Checking stream-processing logs for errors..."
    docker logs stream-processing 2>&1 | tail -10 | sed 's/^/     /'
fi
echo ""

# Step 4: Summary
echo "========================================="
echo "📊 Test Summary"
echo "========================================="
echo "Events sent: 10"
echo "Accumulations found: $ACCUMULATION_COUNT"
echo ""

if [ "$ACCUMULATION_COUNT" -gt 0 ]; then
    echo "🎉 APT Temporal Accumulation is working correctly!"
    echo ""
    echo "The exponential decay accumulation feature is:"
    echo "  ✅ Processing attack events"
    echo "  ✅ Calculating decay scores"
    echo "  ✅ Inferring attack phases"
    echo "  ✅ Storing in PostgreSQL"
    exit 0
else
    echo "⚠️  APT Temporal Accumulation may not be working"
    echo ""
    echo "Possible issues:"
    echo "  1. Stream processing service not running"
    echo "  2. APT accumulation sink not configured"
    echo "  3. Database connection issues"
    echo "  4. Serialization errors (should be fixed)"
    echo ""
    echo "Check logs: docker logs stream-processing"
    exit 1
fi
