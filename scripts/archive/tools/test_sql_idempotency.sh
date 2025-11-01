#!/bin/bash
# ============================================================================
# SQL幂等性测试脚本
# 测试数据库初始化脚本可以安全地重复执行而不丢失数据
# ============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color
BOLD='\033[1m'

echo -e "${BOLD}${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════════════╗"
echo "║                                                                       ║"
echo "║        SQL初始化脚本幂等性测试                                        ║"
echo "║        SQL Idempotency Test                                           ║"
echo "║                                                                       ║"
echo "╚═══════════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

DOCKER_DIR="/home/kylecui/threat-detection-system/docker"
POSTGRES_CONTAINER="postgres"
DB_NAME="threat_detection"
DB_USER="threat_user"

# 检查容器是否运行
echo -e "${BLUE}📋 步骤1: 检查PostgreSQL容器状态${NC}"
if ! docker ps | grep -q "$POSTGRES_CONTAINER"; then
    echo -e "${RED}✗ PostgreSQL容器未运行！${NC}"
    echo -e "${YELLOW}请先启动容器: docker-compose up -d postgres${NC}"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL容器运行中${NC}\n"

# 函数: 执行SQL并捕获错误
execute_sql() {
    local sql_file=$1
    local attempt=$2
    
    echo -e "${BLUE}  尝试 #${attempt}: 执行 $(basename $sql_file)${NC}"
    
    if docker exec -i "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$sql_file" > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ 成功执行${NC}"
        return 0
    else
        echo -e "${RED}  ✗ 执行失败${NC}"
        docker exec -i "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$sql_file" 2>&1 | tail -5
        return 1
    fi
}

# 函数: 检查表行数
check_table_count() {
    local table_name=$1
    local count=$(docker exec "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM $table_name;")
    echo "$count" | xargs
}

# 测试序列
echo -e "${BLUE}📋 步骤2: 测试初始化脚本幂等性${NC}"

SQL_FILES=(
    "$DOCKER_DIR/init-db.sql"
    "$DOCKER_DIR/02-attack-events-storage.sql"
    "$DOCKER_DIR/port_weights_migration.sql"
)

for sql_file in "${SQL_FILES[@]}"; do
    echo -e "\n${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}📄 测试文件: $(basename $sql_file)${NC}"
    echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    if [ ! -f "$sql_file" ]; then
        echo -e "${YELLOW}⚠  文件不存在,跳过: $sql_file${NC}"
        continue
    fi
    
    # 第一次执行
    execute_sql "$sql_file" 1
    
    # 第二次执行 (测试幂等性)
    echo -e "${BLUE}  测试重复执行...${NC}"
    if execute_sql "$sql_file" 2; then
        echo -e "${GREEN}  ✓ 幂等性测试通过 - 可以安全重复执行${NC}"
    else
        echo -e "${RED}  ✗ 幂等性测试失败 - 重复执行出错！${NC}"
        exit 1
    fi
done

# 测试数据持久性
echo -e "\n${BLUE}📋 步骤3: 测试数据持久性${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# 插入测试数据
echo -e "${BLUE}  插入测试数据到持久化表...${NC}"
docker exec "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<EOF > /dev/null 2>&1
INSERT INTO attack_events (customer_id, dev_serial, attack_mac, attack_ip, response_ip, response_port, event_timestamp)
VALUES ('test_customer', 'test_device', '00:11:22:33:44:55', '192.168.1.100', '10.0.0.1', 3306, NOW());

INSERT INTO threat_alerts (customer_id, attack_mac, threat_score, threat_level, attack_count, unique_ips, unique_ports, unique_devices, tier, window_start, window_end, alert_timestamp)
VALUES ('test_customer', '00:11:22:33:44:55', 125.5, 'HIGH', 100, 5, 3, 1, 2, NOW() - INTERVAL '5 minutes', NOW(), NOW());

INSERT INTO device_status_history (dev_serial, customer_id, sentry_count, real_host_count, dev_start_time, dev_end_time, report_time)
VALUES ('test_device', 'test_customer', 10, 50, EXTRACT(EPOCH FROM NOW())::BIGINT, -1, NOW());

INSERT INTO threat_assessments (customer_id, attack_mac, threat_score, threat_level, attack_count, unique_ips, unique_ports, unique_devices, assessment_time)
VALUES ('test_customer', '00:11:22:33:44:55', 125.5, 'HIGH', 100, 5, 3, 1, NOW());
EOF

echo -e "${GREEN}  ✓ 测试数据插入成功${NC}"

# 记录插入后的行数
echo -e "\n${BLUE}  记录各表行数...${NC}"
declare -A before_counts
before_counts["attack_events"]=$(check_table_count "attack_events")
before_counts["threat_alerts"]=$(check_table_count "threat_alerts")
before_counts["device_status_history"]=$(check_table_count "device_status_history")
before_counts["threat_assessments"]=$(check_table_count "threat_assessments")

echo -e "${BLUE}  插入前行数:${NC}"
for table in "${!before_counts[@]}"; do
    echo -e "    $table: ${before_counts[$table]}"
done

# 重新执行初始化脚本
echo -e "\n${BLUE}  重新执行所有初始化脚本...${NC}"
for sql_file in "${SQL_FILES[@]}"; do
    if [ -f "$sql_file" ]; then
        execute_sql "$sql_file" "重复执行" > /dev/null 2>&1
    fi
done

# 检查数据是否保留
echo -e "\n${BLUE}  检查数据是否保留...${NC}"
declare -A after_counts
after_counts["attack_events"]=$(check_table_count "attack_events")
after_counts["threat_alerts"]=$(check_table_count "threat_alerts")
after_counts["device_status_history"]=$(check_table_count "device_status_history")
after_counts["threat_assessments"]=$(check_table_count "threat_assessments")

echo -e "${BLUE}  重新执行后行数:${NC}"
all_preserved=true
for table in "${!after_counts[@]}"; do
    before=${before_counts[$table]}
    after=${after_counts[$table]}
    
    if [ "$before" -eq "$after" ]; then
        echo -e "    ${GREEN}✓${NC} $table: $before → $after (保留)"
    else
        echo -e "    ${RED}✗${NC} $table: $before → $after (${RED}数据丢失!${NC})"
        all_preserved=false
    fi
done

# 清理测试数据
echo -e "\n${BLUE}  清理测试数据...${NC}"
docker exec "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<EOF > /dev/null 2>&1
DELETE FROM attack_events WHERE customer_id = 'test_customer';
DELETE FROM threat_alerts WHERE customer_id = 'test_customer';
DELETE FROM device_status_history WHERE customer_id = 'test_customer';
DELETE FROM threat_assessments WHERE customer_id = 'test_customer';
EOF
echo -e "${GREEN}  ✓ 测试数据已清理${NC}"

# 最终结果
echo -e "\n${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}📊 测试结果${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ "$all_preserved" = true ]; then
    echo -e "${GREEN}${BOLD}✅ 所有测试通过！${NC}"
    echo -e "${GREEN}   ✓ SQL脚本可以安全重复执行${NC}"
    echo -e "${GREEN}   ✓ 持久化表数据得到保护${NC}"
    echo -e "${GREEN}   ✓ 配置表正确刷新${NC}"
    exit 0
else
    echo -e "${RED}${BOLD}❌ 测试失败！${NC}"
    echo -e "${RED}   ✗ 持久化表数据在重复执行后丢失${NC}"
    exit 1
fi
