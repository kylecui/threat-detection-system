#!/bin/bash

# 时间窗口自定义配置测试脚本
# 用途: 验证时间窗口环境变量配置功能

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 日志函数
log_info() { echo -e "${CYAN}ℹ️  $1${NC}"; }
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }
log_header() { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n${BLUE}$1${NC}\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

# 测试场景配置
declare -A TEST_SCENARIOS=(
    ["default"]="30,300,900,默认配置(30s/5min/15min)"
    ["fast"]="15,120,300,快速响应(15s/2min/5min)"
    ["balanced"]="60,600,1800,平衡配置(1min/10min/30min)"
    ["minimal"]="10,10,10,最小窗口(10s/10s/10s)"
)

# 主函数
main() {
    log_header "🧪 时间窗口自定义配置测试"
    
    # 检查参数
    if [ $# -eq 0 ]; then
        show_usage
        exit 0
    fi
    
    local scenario=$1
    
    case $scenario in
        default|fast|balanced|minimal)
            test_scenario "$scenario"
            ;;
        custom)
            if [ $# -ne 4 ]; then
                log_error "自定义模式需要3个参数: tier1 tier2 tier3"
                echo "用法: $0 custom <tier1_seconds> <tier2_seconds> <tier3_seconds>"
                exit 1
            fi
            test_custom "$2" "$3" "$4"
            ;;
        verify)
            verify_current_config
            ;;
        *)
            log_error "未知场景: $scenario"
            show_usage
            exit 1
            ;;
    esac
}

# 显示用法
show_usage() {
    cat << EOF
${CYAN}用法:${NC}
    $0 <scenario>          # 使用预定义场景
    $0 custom <t1> <t2> <t3>  # 自定义窗口时长
    $0 verify              # 验证当前配置

${CYAN}预定义场景:${NC}
EOF
    for key in "${!TEST_SCENARIOS[@]}"; do
        IFS=',' read -r tier1 tier2 tier3 desc <<< "${TEST_SCENARIOS[$key]}"
        printf "    ${GREEN}%-10s${NC} - %s (Tier1=%ss, Tier2=%ss, Tier3=%ss)\n" \
               "$key" "$desc" "$tier1" "$tier2" "$tier3"
    done
    cat << EOF

${CYAN}示例:${NC}
    $0 default             # 测试默认配置
    $0 fast                # 测试快速响应配置
    $0 custom 45 450 1350  # 自定义配置: 45秒/7.5分钟/22.5分钟
    $0 verify              # 验证当前运行配置

${CYAN}测试流程:${NC}
    1. 备份原始配置
    2. 修改docker-compose.yml中的环境变量
    3. 重启stream-processing服务
    4. 验证配置是否生效
    5. 发送测试事件
    6. 等待并观察不同窗口的告警生成
    7. 恢复原始配置
EOF
}

# 测试预定义场景
test_scenario() {
    local scenario=$1
    IFS=',' read -r tier1 tier2 tier3 desc <<< "${TEST_SCENARIOS[$scenario]}"
    
    log_header "测试场景: $desc"
    log_info "Tier 1 窗口: ${tier1} 秒"
    log_info "Tier 2 窗口: ${tier2} 秒 ($((tier2 / 60)) 分钟)"
    log_info "Tier 3 窗口: ${tier3} 秒 ($((tier3 / 60)) 分钟)"
    
    apply_configuration "$tier1" "$tier2" "$tier3"
    verify_configuration "$tier1" "$tier2" "$tier3"
    run_alert_test "$tier1" "$tier2" "$tier3"
}

# 测试自定义配置
test_custom() {
    local tier1=$1
    local tier2=$2
    local tier3=$3
    
    log_header "自定义时间窗口配置"
    log_info "Tier 1 窗口: ${tier1} 秒"
    log_info "Tier 2 窗口: ${tier2} 秒 ($((tier2 / 60)) 分钟)"
    log_info "Tier 3 窗口: ${tier3} 秒 ($((tier3 / 60)) 分钟)"
    
    # 验证配置合理性
    if [ "$tier1" -lt 10 ] || [ "$tier2" -lt 10 ] || [ "$tier3" -lt 10 ]; then
        log_error "所有窗口必须 >= 10秒"
        exit 1
    fi
    
    apply_configuration "$tier1" "$tier2" "$tier3"
    verify_configuration "$tier1" "$tier2" "$tier3"
    run_alert_test "$tier1" "$tier2" "$tier3"
}

# 应用配置
apply_configuration() {
    local tier1=$1
    local tier2=$2
    local tier3=$3
    
    log_info "正在备份docker-compose.yml..."
    cp "$DOCKER_DIR/docker-compose.yml" "$DOCKER_DIR/docker-compose.yml.backup-$(date +%Y%m%d_%H%M%S)"
    
    log_info "正在修改环境变量配置..."
    cd "$DOCKER_DIR"
    
    # 使用sed修改环境变量（如果已存在）或添加新的环境变量
    if grep -q "TIER1_WINDOW_SECONDS:" docker-compose.yml; then
        sed -i "s/TIER1_WINDOW_SECONDS: \"[0-9]*\"/TIER1_WINDOW_SECONDS: \"$tier1\"/" docker-compose.yml
        sed -i "s/TIER2_WINDOW_SECONDS: \"[0-9]*\"/TIER2_WINDOW_SECONDS: \"$tier2\"/" docker-compose.yml
        sed -i "s/TIER3_WINDOW_SECONDS: \"[0-9]*\"/TIER3_WINDOW_SECONDS: \"$tier3\"/" docker-compose.yml
        log_success "环境变量已更新"
    else
        log_warning "未在docker-compose.yml中找到窗口配置，请手动添加"
        return 1
    fi
    
    log_info "正在重启stream-processing服务..."
    docker compose down stream-processing
    docker compose up -d stream-processing
    
    log_info "等待服务启动... (30秒)"
    sleep 30
    
    # 检查服务状态
    if docker compose ps stream-processing | grep -q "Up"; then
        log_success "stream-processing服务已启动"
    else
        log_error "stream-processing服务启动失败"
        docker logs stream-processing --tail 50
        exit 1
    fi
}

# 验证配置
verify_configuration() {
    local expected_tier1=$1
    local expected_tier2=$2
    local expected_tier3=$3
    
    log_info "正在验证配置..."
    
    # 从日志中提取实际配置
    local logs=$(docker logs stream-processing 2>&1 | grep -A 5 "时间窗口配置" | tail -6)
    
    if [ -z "$logs" ]; then
        log_error "无法从日志中找到窗口配置信息"
        log_info "尝试查看完整日志:"
        docker logs stream-processing --tail 50
        return 1
    fi
    
    echo "$logs"
    
    # 提取实际值
    local actual_tier1=$(echo "$logs" | grep "Tier 1" | grep -oP '\d+(?= 秒)')
    local actual_tier2=$(echo "$logs" | grep "Tier 2" | grep -oP '\d+(?= 秒)')
    local actual_tier3=$(echo "$logs" | grep "Tier 3" | grep -oP '\d+(?= 秒)')
    
    # 验证
    local all_match=true
    
    if [ "$actual_tier1" == "$expected_tier1" ]; then
        log_success "Tier 1 配置正确: ${actual_tier1}秒"
    else
        log_error "Tier 1 配置不匹配: 期望=${expected_tier1}, 实际=${actual_tier1}"
        all_match=false
    fi
    
    if [ "$actual_tier2" == "$expected_tier2" ]; then
        log_success "Tier 2 配置正确: ${actual_tier2}秒"
    else
        log_error "Tier 2 配置不匹配: 期望=${expected_tier2}, 实际=${actual_tier2}"
        all_match=false
    fi
    
    if [ "$actual_tier3" == "$expected_tier3" ]; then
        log_success "Tier 3 配置正确: ${actual_tier3}秒"
    else
        log_error "Tier 3 配置不匹配: 期望=${expected_tier3}, 实际=${actual_tier3}"
        all_match=false
    fi
    
    if [ "$all_match" = true ]; then
        log_success "✅ 所有窗口配置验证通过"
        return 0
    else
        log_error "❌ 配置验证失败"
        return 1
    fi
}

# 验证当前配置
verify_current_config() {
    log_header "验证当前时间窗口配置"
    
    log_info "从容器环境变量读取配置..."
    docker exec stream-processing env | grep "TIER.*WINDOW" || log_warning "未找到窗口环境变量"
    
    log_info "从服务日志读取配置..."
    docker logs stream-processing 2>&1 | grep -A 5 "时间窗口配置" | tail -6
    
    log_info "检查Flink作业状态..."
    curl -s http://localhost:8081/jobs | jq -r '.jobs[] | "\(.id) - \(.status)"' || log_warning "无法连接Flink Web UI"
}

# 运行告警测试
run_alert_test() {
    local tier1=$1
    local tier2=$2
    local tier3=$3
    
    log_header "运行告警生成测试"
    
    # 清理旧告警
    log_info "清理旧告警数据..."
    docker exec -i postgres psql -U threat_user -d threat_detection -c "DELETE FROM alerts;" > /dev/null 2>&1
    
    # 发送测试事件
    log_info "发送测试事件..."
    cd "$SCRIPT_DIR"
    if [ -f "test_v4_phase2_dual_dimension.sh" ]; then
        bash test_v4_phase2_dual_dimension.sh > /dev/null 2>&1
        log_success "测试事件已发送"
    else
        log_warning "未找到测试脚本，跳过事件发送"
        return 0
    fi
    
    # 计算观察时长（取最大窗口时长的1.5倍，至少2分钟）
    local max_window=$tier3
    local observe_time=$((max_window + max_window / 2))
    [ $observe_time -lt 120 ] && observe_time=120
    
    log_info "观察告警生成... (等待 $observe_time 秒)"
    
    local intervals=$((observe_time / 30))
    [ $intervals -lt 1 ] && intervals=1
    
    for i in $(seq 1 $intervals); do
        sleep 30
        local elapsed=$((i * 30))
        log_info "[$elapsed/$observe_time 秒] 告警统计:"
        docker exec -i postgres psql -U threat_user -d threat_detection -c "
        SELECT 
            CASE substring(metadata from 'tier\":([0-9]+)')
                WHEN '1' THEN 'Tier 1'
                WHEN '2' THEN 'Tier 2'
                WHEN '3' THEN 'Tier 3'
                ELSE 'Unknown'
            END as tier,
            COUNT(*) as count,
            MIN(created_at) as first_alert,
            MAX(created_at) as last_alert
        FROM alerts 
        WHERE created_at > NOW() - INTERVAL '$observe_time seconds'
        GROUP BY substring(metadata from 'tier\":([0-9]+)')
        ORDER BY substring(metadata from 'tier\":([0-9]+)');
        "
    done
    
    log_success "✅ 测试完成"
    
    # 显示最终统计
    log_header "最终告警统计"
    docker exec -i postgres psql -U threat_user -d threat_detection -c "
    SELECT 
        CASE substring(metadata from 'tier\":([0-9]+)')
            WHEN '1' THEN 'Tier 1 (' || '$tier1' || 's)'
            WHEN '2' THEN 'Tier 2 (' || '$tier2' || 's)'
            WHEN '3' THEN 'Tier 3 (' || '$tier3' || 's)'
            ELSE 'Unknown'
        END as window,
        COUNT(*) as total_alerts,
        AVG(threat_score) as avg_score,
        MAX(threat_score) as max_score
    FROM alerts 
    WHERE created_at > NOW() - INTERVAL '1 hour'
    GROUP BY substring(metadata from 'tier\":([0-9]+)')
    ORDER BY substring(metadata from 'tier\":([0-9]+)');
    "
}

# 执行主函数
main "$@"
