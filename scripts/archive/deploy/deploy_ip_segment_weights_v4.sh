#!/bin/bash
# V4.0 IP网段权重系统部署脚本
# 用途: 部署或更新V4.0双维度权重配置到PostgreSQL数据库
# 作者: ThreatDetection Team
# 日期: 2025-10-24

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}V4.0 IP网段权重系统部署脚本${NC}"
echo -e "${BLUE}=================================${NC}"
echo ""

# 检查是否在正确的目录
if [ ! -f "$DOCKER_DIR/12-ip-segment-weights-v4.sql" ]; then
    echo -e "${RED}错误: 找不到 12-ip-segment-weights-v4.sql${NC}"
    echo -e "${RED}请确保在项目根目录运行此脚本${NC}"
    exit 1
fi

cd "$DOCKER_DIR"

# 选项1: 全新部署（删除数据卷）
echo -e "${YELLOW}部署选项:${NC}"
echo -e "  ${GREEN}1)${NC} 全新部署 (删除现有数据，重新初始化) ${RED}⚠️  数据会丢失!${NC}"
echo -e "  ${GREEN}2)${NC} 增量部署 (保留现有数据，手动执行SQL)"
echo -e "  ${GREEN}3)${NC} 仅验证部署"
echo ""
read -p "请选择 [1/2/3]: " DEPLOY_OPTION

case $DEPLOY_OPTION in
    1)
        echo -e "${YELLOW}===========================================${NC}"
        echo -e "${RED}⚠️  警告: 全新部署将删除所有现有数据!${NC}"
        echo -e "${YELLOW}===========================================${NC}"
        read -p "确认删除所有数据并重新初始化? [yes/NO]: " CONFIRM
        
        if [ "$CONFIRM" != "yes" ]; then
            echo -e "${BLUE}部署已取消${NC}"
            exit 0
        fi
        
        echo -e "${BLUE}步骤1: 停止PostgreSQL容器...${NC}"
        docker-compose down postgres
        
        echo -e "${BLUE}步骤2: 删除数据卷...${NC}"
        docker volume rm docker_postgres_data 2>/dev/null || true
        
        echo -e "${BLUE}步骤3: 重建镜像（无缓存）...${NC}"
        docker-compose build --no-cache postgres
        
        echo -e "${BLUE}步骤4: 启动PostgreSQL容器（强制重建）...${NC}"
        docker-compose up -d --force-recreate postgres
        
        echo -e "${BLUE}步骤5: 等待PostgreSQL就绪...${NC}"
        sleep 10
        
        # 等待健康检查通过
        echo -e "${BLUE}等待健康检查...${NC}"
        for i in {1..30}; do
            if docker exec postgres pg_isready -U threat_user -d threat_detection > /dev/null 2>&1; then
                echo -e "${GREEN}✓ PostgreSQL已就绪${NC}"
                break
            fi
            echo -n "."
            sleep 2
        done
        echo ""
        
        ;;
        
    2)
        echo -e "${BLUE}===========================================${NC}"
        echo -e "${BLUE}增量部署 - 手动执行SQL脚本${NC}"
        echo -e "${BLUE}===========================================${NC}"
        
        # 检查容器是否运行
        if ! docker ps | grep -q postgres; then
            echo -e "${RED}错误: PostgreSQL容器未运行${NC}"
            echo -e "${YELLOW}提示: 先运行 'docker-compose up -d postgres'${NC}"
            exit 1
        fi
        
        echo -e "${BLUE}执行V4.0初始化脚本...${NC}"
        docker exec -i postgres psql -U threat_user -d threat_detection < "$DOCKER_DIR/12-ip-segment-weights-v4.sql"
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ SQL脚本执行成功${NC}"
        else
            echo -e "${RED}✗ SQL脚本执行失败${NC}"
            exit 1
        fi
        
        ;;
        
    3)
        echo -e "${BLUE}===========================================${NC}"
        echo -e "${BLUE}验证部署${NC}"
        echo -e "${BLUE}===========================================${NC}"
        ;;
        
    *)
        echo -e "${RED}无效选项${NC}"
        exit 1
        ;;
esac

# 验证部署
echo ""
echo -e "${BLUE}===========================================${NC}"
echo -e "${BLUE}验证V4.0部署${NC}"
echo -e "${BLUE}===========================================${NC}"

# 检查表是否存在
echo -e "${BLUE}检查表结构...${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT 
        'attack_source_weights' as table_name,
        COUNT(*) as record_count
    FROM attack_source_weights
    UNION ALL
    SELECT 
        'honeypot_sensitivity_weights',
        COUNT(*)
    FROM honeypot_sensitivity_weights
    ORDER BY table_name;
"

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ 表不存在，部署可能失败${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}检查查询函数...${NC}"

# 测试场景1: IoT设备→管理区蜜罐
echo -e "${YELLOW}场景1: IoT设备(192.168.50.10) → 管理区蜜罐(10.0.100.50)${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT * FROM get_combined_segment_weight('default', '192.168.50.10', '10.0.100.50');
" | grep -q "3.15"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 场景1验证通过 (combined_weight=3.15)${NC}"
else
    echo -e "${RED}✗ 场景1验证失败${NC}"
fi

# 测试场景2: 数据库服务器→办公区蜜罐
echo -e "${YELLOW}场景2: 数据库服务器(10.0.3.10) → 办公区蜜罐(192.168.10.50)${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT * FROM get_combined_segment_weight('default', '10.0.3.10', '192.168.10.50');
" | grep -q "3.90"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 场景2验证通过 (combined_weight=3.90)${NC}"
else
    echo -e "${RED}✗ 场景2验证失败${NC}"
fi

# 测试场景3: 办公区→办公区蜜罐
echo -e "${YELLOW}场景3: 办公区(192.168.10.100) → 办公区蜜罐(192.168.10.50)${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT * FROM get_combined_segment_weight('default', '192.168.10.100', '192.168.10.50');
" | grep -q "1.30"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 场景3验证通过 (combined_weight=1.30)${NC}"
else
    echo -e "${RED}✗ 场景3验证失败${NC}"
fi

echo ""
echo -e "${GREEN}===========================================${NC}"
echo -e "${GREEN}✓ V4.0部署完成并验证成功!${NC}"
echo -e "${GREEN}===========================================${NC}"
echo ""
echo -e "${BLUE}统计信息:${NC}"
docker exec postgres psql -U threat_user -d threat_detection -c "
    SELECT 
        '攻击源权重配置' as config_type,
        COUNT(*) as total_count,
        COUNT(DISTINCT segment_type) as unique_types
    FROM attack_source_weights
    WHERE customer_id = 'default' AND is_active = TRUE
    UNION ALL
    SELECT 
        '蜜罐敏感度配置',
        COUNT(*),
        COUNT(DISTINCT deployment_zone)
    FROM honeypot_sensitivity_weights
    WHERE customer_id = 'default' AND is_active = TRUE;
"

echo ""
echo -e "${BLUE}下一步:${NC}"
echo -e "  1. 运行单元测试: ${GREEN}cd services/threat-assessment && mvn test -Dtest=IpSegmentWeightServiceV4Test${NC}"
echo -e "  2. 集成到ThreatScoreCalculator: ${GREEN}更新威胁评分公式${NC}"
echo -e "  3. 端到端测试: ${GREEN}验证完整评分流程${NC}"
