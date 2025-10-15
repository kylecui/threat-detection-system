#!/bin/bash
# MVP Phase 0: 功能测试启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_header() {
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  $1${NC}"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# 检查依赖
check_dependencies() {
    print_header "检查测试依赖"
    
    # Python 3
    if ! command -v python3 &> /dev/null; then
        print_error "Python 3 未安装"
        exit 1
    fi
    print_success "Python 3: $(python3 --version)"
    
    # pip packages
    required_packages=("kafka-python" "psycopg2-binary")
    for package in "${required_packages[@]}"; do
        if ! python3 -c "import ${package//-/_}" 2>/dev/null; then
            print_warning "$package 未安装，尝试安装..."
            pip3 install "$package" --quiet
        fi
        print_success "$package 已安装"
    done
    
    echo
}

# 检查服务状态
check_services() {
    print_header "检查服务状态"
    
    # Kafka
    if nc -z localhost 9092 2>/dev/null; then
        print_success "Kafka 运行中 (localhost:9092)"
    else
        print_error "Kafka 未运行，请先启动 Docker Compose"
        exit 1
    fi
    
    # PostgreSQL
    if nc -z localhost 5432 2>/dev/null; then
        print_success "PostgreSQL 运行中 (localhost:5432)"
    else
        print_error "PostgreSQL 未运行，请先启动 Docker Compose"
        exit 1
    fi
    
    # Flink (可选)
    if nc -z localhost 8081 2>/dev/null; then
        print_success "Flink 运行中 (localhost:8081)"
    else
        print_warning "Flink 未运行，流处理可能无法工作"
    fi
    
    echo
}

# 运行单元测试
run_unit_tests() {
    print_header "运行单元测试"
    
    cd "$PROJECT_ROOT"
    python3 scripts/test/unit_test_mvp.py
    
    if [ $? -eq 0 ]; then
        print_success "单元测试通过"
    else
        print_error "单元测试失败"
        exit 1
    fi
    
    echo
}

# 运行端到端测试
run_e2e_tests() {
    print_header "运行端到端功能测试"
    
    cd "$PROJECT_ROOT"
    
    # 设置环境变量
    export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
    export DB_HOST="${DB_HOST:-localhost}"
    export DB_PORT="${DB_PORT:-5432}"
    export DB_NAME="${DB_NAME:-threat_detection}"
    export DB_USER="${DB_USER:-threat_user}"
    export DB_PASSWORD="${DB_PASSWORD:-threat_password}"
    
    print_info "测试配置:"
    print_info "  Kafka: $KAFKA_BOOTSTRAP_SERVERS"
    print_info "  PostgreSQL: $DB_HOST:$DB_PORT/$DB_NAME"
    echo
    
    python3 scripts/test/e2e_mvp_test.py
    
    if [ $? -eq 0 ]; then
        print_success "端到端测试完成"
    else
        print_error "端到端测试失败"
        exit 1
    fi
    
    echo
}

# 显示测试日志统计
show_test_log_stats() {
    print_header "测试日志统计"
    
    if [ ! -d "$PROJECT_ROOT/tmp/real_test_logs" ]; then
        print_warning "测试日志目录不存在: tmp/real_test_logs"
        return
    fi
    
    cd "$PROJECT_ROOT/tmp/real_test_logs"
    
    total_files=$(ls *.log 2>/dev/null | wc -l)
    total_lines=$(cat *.log 2>/dev/null | wc -l)
    
    print_info "日志文件数: $total_files"
    print_info "总行数: $total_lines"
    
    # 统计log_type
    attack_events=$(grep -h "log_type=1" *.log 2>/dev/null | wc -l)
    print_info "攻击事件 (log_type=1): $attack_events"
    
    # 统计高危端口
    snmp_attacks=$(grep -h "response_port=161" *.log 2>/dev/null | wc -l)
    ssh_attacks=$(grep -h "response_port=22" *.log 2>/dev/null | wc -l)
    rdp_attacks=$(grep -h "response_port=3389" *.log 2>/dev/null | wc -l)
    
    if [ $snmp_attacks -gt 0 ]; then
        print_info "  SNMP (161): $snmp_attacks"
    fi
    if [ $ssh_attacks -gt 0 ]; then
        print_info "  SSH (22): $ssh_attacks"
    fi
    if [ $rdp_attacks -gt 0 ]; then
        print_info "  RDP (3389): $rdp_attacks"
    fi
    
    echo
}

# 主函数
main() {
    echo
    echo -e "${BLUE}${BOLD}"
    cat << "EOF"
╔═══════════════════════════════════════════════════════════════════════════╗
║                                                                           ║
║           MVP Phase 0: 功能测试套件                                       ║
║           Cloud-Native Threat Detection System                            ║
║                                                                           ║
╚═══════════════════════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
    
    # 解析参数
    TEST_TYPE="${1:-all}"
    
    case "$TEST_TYPE" in
        unit)
            check_dependencies
            run_unit_tests
            ;;
        e2e)
            check_dependencies
            check_services
            show_test_log_stats
            run_e2e_tests
            ;;
        all)
            check_dependencies
            run_unit_tests
            
            print_info "准备运行端到端测试，需要运行的服务..."
            read -p "是否继续端到端测试? (y/N) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                check_services
                show_test_log_stats
                run_e2e_tests
            else
                print_info "跳过端到端测试"
            fi
            ;;
        stats)
            show_test_log_stats
            ;;
        *)
            echo "用法: $0 [unit|e2e|all|stats]"
            echo
            echo "  unit  - 运行单元测试"
            echo "  e2e   - 运行端到端测试"
            echo "  all   - 运行所有测试 (默认)"
            echo "  stats - 显示测试日志统计"
            exit 1
            ;;
    esac
    
    print_header "测试完成"
}

# 执行主函数
main "$@"
