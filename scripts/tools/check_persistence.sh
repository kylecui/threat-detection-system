#!/bin/bash
################################################################################
# 数据持久化检查脚本
# 快速验证攻击事件和威胁告警是否正确保存到数据库
#
# 用法: ./scripts/tools/check_persistence.sh
################################################################################

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}         数据持久化状态检查                                ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}\n"

# 检查1: 攻击事件表
echo -e "${YELLOW}📊 1. 攻击事件 (attack_events):${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
SELECT 
    COUNT(*) as total_events,
    COUNT(DISTINCT customer_id) as customers,
    COUNT(DISTINCT attack_mac) as unique_attackers,
    COUNT(DISTINCT response_port) as unique_ports,
    MIN(created_at) as first_event,
    MAX(created_at) as last_event
FROM attack_events 
WHERE created_at > NOW() - INTERVAL '1 hour';
"
echo ""

# 检查2: 威胁告警表
echo -e "${YELLOW}📊 2. 威胁告警 (threat_alerts):${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
SELECT 
    COUNT(*) as total_alerts,
    COUNT(DISTINCT customer_id) as customers,
    COUNT(DISTINCT attack_mac) as unique_attackers,
    ROUND(AVG(threat_score::numeric), 2) as avg_score,
    ROUND(MAX(threat_score::numeric), 2) as max_score,
    MIN(created_at) as first_alert,
    MAX(created_at) as last_alert
FROM threat_alerts 
WHERE created_at > NOW() - INTERVAL '1 hour';
"
echo ""

# 检查3: 威胁等级分布
echo -e "${YELLOW}📊 3. 威胁等级分布 (最近1小时):${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
SELECT 
    threat_level,
    tier,
    COUNT(*) as count,
    ROUND(AVG(threat_score::numeric), 2) as avg_score
FROM threat_alerts 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY threat_level, tier
ORDER BY tier, 
    CASE threat_level 
        WHEN 'CRITICAL' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
        ELSE 5
    END;
"
echo ""

# 检查4: 最近的JSONB数据样本
echo -e "${YELLOW}📊 4. JSONB数据样本 (最近3条):${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
SELECT 
    attack_mac,
    response_ip,
    response_port,
    LEFT(raw_log_data::text, 100) as raw_data_sample
FROM attack_events 
ORDER BY created_at DESC 
LIMIT 3;
"
echo ""

# 检查5: Kafka消费者状态
echo -e "${YELLOW}📊 5. Kafka消费者状态:${NC}"
echo -e "${BLUE}   attack-events-persistence-group:${NC}"
docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe --group attack-events-persistence-group 2>/dev/null || echo "   ❌ 消费者组未找到"

echo -e "\n${BLUE}   threat-alerts-persistence-group:${NC}"
docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe --group threat-alerts-persistence-group 2>/dev/null || echo "   ❌ 消费者组未找到"

echo -e "\n${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}         检查完成                                          ${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}\n"

# 提供下一步建议
echo -e "${BLUE}💡 如果数据为空:${NC}"
echo -e "   1. 检查Kafka消费者日志: ${YELLOW}docker logs data-ingestion-service | grep -i 'persisted\\|error'${NC}"
echo -e "   2. 发送测试数据:        ${YELLOW}python3 scripts/test/e2e_mvp_test.py${NC}"
echo -e "   3. 重启服务:            ${YELLOW}./scripts/tools/full_restart.sh data-ingestion${NC}"
echo ""
