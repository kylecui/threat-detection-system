#!/bin/bash
################################################################################
# 数据库初始化安全性检查脚本
# 
# 功能:
#   1. 分析SQL初始化脚本,识别潜在的数据丢失风险
#   2. 检查持久化表是否使用了危险的DROP TABLE语句
#   3. 生成安全评估报告
#
# 用法: ./scripts/tools/check_db_init_safety.sh
################################################################################

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

PROJECT_ROOT="/home/kylecui/threat-detection-system"
SQL_DIR="${PROJECT_ROOT}/docker"

echo -e "${BLUE}${BOLD}"
cat << "EOF"
╔═══════════════════════════════════════════════════════════════════════╗
║                                                                       ║
║        数据库初始化安全性检查                                         ║
║        Database Initialization Safety Checker                         ║
║                                                                       ║
╚═══════════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}\n"

# 定义持久化表列表 (运行时产生的业务数据,不能DROP)
PERSISTENT_TABLES=(
    "attack_events"
    "threat_alerts"
    "threat_assessments"
    "device_status_history"
)

# 定义配置表列表 (静态配置数据,可以安全DROP和重建)
CONFIG_TABLES=(
    "device_customer_mapping"
    "port_risk_configs"
)

# 风险计数器
CRITICAL_ISSUES=0
WARNING_ISSUES=0
SAFE_PATTERNS=0

echo -e "${YELLOW}📂 检查目录: ${SQL_DIR}${NC}\n"

# 函数: 检查SQL文件
check_sql_file() {
    local file="$1"
    local filename=$(basename "$file")
    
    echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}📄 检查文件: ${filename}${NC}"
    echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    
    # 检查持久化表的DROP TABLE
    for table in "${PERSISTENT_TABLES[@]}"; do
        if grep -q "DROP TABLE.*${table}" "$file"; then
            echo -e "${RED}✗ 危险: 发现持久化表 '${table}' 的 DROP TABLE 语句${NC}"
            echo -e "${RED}  文件: ${filename}${NC}"
            echo -e "${RED}  风险: 历史业务数据将被永久删除！${NC}"
            
            # 显示具体行
            grep -n "DROP TABLE.*${table}" "$file" | while read line; do
                echo -e "${RED}  → 第 $(echo $line | cut -d: -f1) 行: $(echo $line | cut -d: -f2-)${NC}"
            done
            
            # 检查是否有对应的CREATE IF NOT EXISTS
            if grep -q "CREATE TABLE IF NOT EXISTS ${table}" "$file"; then
                echo -e "${YELLOW}  ⚠️  但同时发现了 CREATE TABLE IF NOT EXISTS (矛盾的设计)${NC}"
            else
                echo -e "${RED}  ✗ 未使用 CREATE TABLE IF NOT EXISTS (风险加倍!)${NC}"
            fi
            
            ((CRITICAL_ISSUES++))
            echo ""
        elif grep -q "CREATE TABLE IF NOT EXISTS ${table}" "$file"; then
            echo -e "${GREEN}✓ 安全: 持久化表 '${table}' 使用 CREATE IF NOT EXISTS${NC}"
            ((SAFE_PATTERNS++))
        fi
    done
    
    # 检查配置表的处理
    for table in "${CONFIG_TABLES[@]}"; do
        if grep -q "DROP TABLE.*${table}" "$file"; then
            if grep -q "INSERT.*${table}.*ON CONFLICT.*DO NOTHING" "$file" || \
               grep -q "INSERT.*${table}.*ON CONFLICT.*DO UPDATE" "$file"; then
                echo -e "${GREEN}✓ 安全: 配置表 '${table}' 使用 DROP + CREATE + ON CONFLICT (幂等)${NC}"
                ((SAFE_PATTERNS++))
            else
                echo -e "${YELLOW}⚠️  警告: 配置表 '${table}' 使用 DROP 但缺少 ON CONFLICT 幂等性保证${NC}"
                ((WARNING_ISSUES++))
            fi
        fi
    done
    
    # 检查视图的处理
    if grep -q "CREATE VIEW" "$file" && ! grep -q "CREATE OR REPLACE VIEW" "$file"; then
        echo -e "${YELLOW}⚠️  警告: 发现 CREATE VIEW 但未使用 CREATE OR REPLACE VIEW${NC}"
        echo -e "${YELLOW}  建议: 使用 CREATE OR REPLACE VIEW 确保幂等性${NC}"
        ((WARNING_ISSUES++))
    fi
    
    # 检查触发器的处理
    if grep -q "CREATE TRIGGER" "$file" && ! grep -q "DROP TRIGGER IF EXISTS" "$file"; then
        echo -e "${YELLOW}⚠️  警告: 发现 CREATE TRIGGER 但未先执行 DROP TRIGGER IF EXISTS${NC}"
        echo -e "${YELLOW}  建议: 触发器需要先删除再创建以确保幂等性${NC}"
        ((WARNING_ISSUES++))
    fi
    
    # 检查ALTER TABLE的幂等性
    if grep -q "ALTER TABLE.*ADD COLUMN" "$file" && ! grep -q "IF NOT EXISTS" "$file"; then
        echo -e "${YELLOW}⚠️  警告: 发现 ALTER TABLE ADD COLUMN 但未检查列是否存在${NC}"
        echo -e "${YELLOW}  建议: 使用 DO \$\$ 块检查列是否存在${NC}"
        ((WARNING_ISSUES++))
    fi
    
    echo ""
}

# 遍历所有SQL文件
SQL_FILES=$(find "$SQL_DIR" -name "*.sql" -type f | sort)

if [ -z "$SQL_FILES" ]; then
    echo -e "${YELLOW}⚠️  未找到SQL文件${NC}"
    exit 1
fi

for sql_file in $SQL_FILES; do
    check_sql_file "$sql_file"
done

# 生成总结报告
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}${BOLD}📊 安全性评估总结${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"

# 统计
echo -e "${RED}🔴 严重问题 (Critical): ${CRITICAL_ISSUES}${NC}"
echo -e "${YELLOW}⚠️  警告 (Warning):     ${WARNING_ISSUES}${NC}"
echo -e "${GREEN}✓ 安全模式 (Safe):      ${SAFE_PATTERNS}${NC}"
echo ""

# 安全等级评估
if [ $CRITICAL_ISSUES -gt 0 ]; then
    echo -e "${RED}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}${BOLD}❌ 安全等级: 危险 (DANGEROUS)${NC}"
    echo -e "${RED}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    echo -e "${RED}⚠️  发现 ${CRITICAL_ISSUES} 个严重问题!${NC}"
    echo -e "${RED}   持久化表使用了 DROP TABLE,可能导致历史数据丢失${NC}"
    echo -e "${RED}   建议立即修复:${NC}"
    echo -e "${YELLOW}   1. 将持久化表改为 CREATE TABLE IF NOT EXISTS${NC}"
    echo -e "${YELLOW}   2. 移除所有 DROP TABLE IF EXISTS attack_events/threat_alerts/...${NC}"
    echo -e "${YELLOW}   3. 参考: docs/database_initialization_guide.md${NC}\n"
    EXIT_CODE=1
    
elif [ $WARNING_ISSUES -gt 0 ]; then
    echo -e "${YELLOW}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}${BOLD}⚠️  安全等级: 需要改进 (NEEDS IMPROVEMENT)${NC}"
    echo -e "${YELLOW}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    echo -e "${YELLOW}发现 ${WARNING_ISSUES} 个警告项${NC}"
    echo -e "${YELLOW}建议优化幂等性设计${NC}\n"
    EXIT_CODE=0
    
else
    echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}${BOLD}✅ 安全等级: 优秀 (EXCELLENT)${NC}"
    echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    echo -e "${GREEN}所有检查通过,初始化脚本设计良好${NC}\n"
    EXIT_CODE=0
fi

# 推荐阅读
echo -e "${BLUE}📖 相关文档:${NC}"
echo -e "   • 数据库初始化指南: ${YELLOW}docs/database_initialization_guide.md${NC}"
echo -e "   • 启动稳定性改进: ${YELLOW}docs/startup_stability_improvements.md${NC}"
echo -e "   • 持久化表示例:   ${YELLOW}docker/01-schema-persistent.sql.example${NC}"
echo ""

exit $EXIT_CODE
