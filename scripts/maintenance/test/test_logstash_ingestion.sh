#!/bin/bash

# 测试 Logstash 日志摄取功能
# 用途: 验证 Logstash 能否正确接收和处理攻击日志和心跳日志
# 日志格式: key=value 对，逗号分隔

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
LOGSTASH_HOST="${LOGSTASH_HOST:-localhost}"
LOGSTASH_PORT="${LOGSTASH_PORT:-9080}"
LOGSTASH_API_PORT="${LOGSTASH_API_PORT:-9600}"
ATTACK_EVENTS_TOPIC="attack-events"
STATUS_EVENTS_TOPIC="status-events"
TEST_ATTACK_EVENTS=8
TEST_HEARTBEAT_EVENTS=2

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖工具..."
    
    local missing_deps=()
    
    for cmd in nc curl jq docker; do
        if ! command -v $cmd &> /dev/null; then
            missing_deps+=($cmd)
        fi
    done
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "缺少依赖工具: ${missing_deps[*]}"
        log_info "安装命令:"
        log_info "  sudo apt-get install -y netcat-openbsd curl jq docker.io"
        exit 1
    fi
    
    log_success "所有依赖工具已安装"
}

# 检查 Logstash 状态
check_logstash_status() {
    log_info "检查 Logstash 容器状态..."
    
    if ! docker ps --format '{{.Names}}' | grep -q "logstash"; then
        log_error "Logstash 容器未运行"
        log_info "启动命令: docker-compose up -d logstash"
        return 1
    fi
    
    log_success "Logstash 容器正在运行"
    
    log_info "检查 Logstash API..."
    if ! curl -s -f "http://${LOGSTASH_HOST}:${LOGSTASH_API_PORT}/_node/stats" > /dev/null; then
        log_error "Logstash API 无响应"
        return 1
    fi
    
    log_success "Logstash API 正常"
    
    # 检查 Pipeline 状态
    log_info "检查 Pipeline 状态..."
    local pipeline_status=$(curl -s "http://${LOGSTASH_HOST}:${LOGSTASH_API_PORT}/_node/stats/pipelines" | jq -r '.pipelines | keys[]')
    
    if [ -z "$pipeline_status" ]; then
        log_warning "未检测到 Pipeline"
    else
        log_success "Pipeline: $pipeline_status"
    fi
}

# 测试 TCP 连接
test_tcp_connection() {
    log_info "测试 TCP 连接 (${LOGSTASH_HOST}:${LOGSTASH_PORT})..."
    
    if ! timeout 5 bash -c "echo '' > /dev/tcp/${LOGSTASH_HOST}/${LOGSTASH_PORT}" 2>/dev/null; then
        log_error "无法连接到 Logstash TCP 端口 ${LOGSTASH_PORT}"
        log_info "检查命令:"
        log_info "  docker-compose logs logstash"
        log_info "  sudo netstat -tlnp | grep ${LOGSTASH_PORT}"
        return 1
    fi
    
    log_success "TCP 连接成功"
}

# 生成攻击日志 (log_type=1)
generate_attack_log() {
    local event_id=$1
    local log_time=$(date +%s)
    local mac=$(printf '00:68:EB:BB:C7:%02X' $((event_id % 256)))
    local attack_ip="192.168.73.$(( 100 + event_id % 150 ))"
    local response_ip="192.168.73.$(( event_id % 50 + 1 ))"
    local response_port=$((3306 + event_id % 10))
    
    echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=${mac},attack_ip=${attack_ip},response_ip=${response_ip},response_port=${response_port},line_id=${event_id},Iface_type=1,Vlan_id=0,log_time=${log_time}"
}

# 生成心跳日志 (log_type=2)
generate_heartbeat_log() {
    local sentry_count=$((6000 + RANDOM % 1000))
    local real_host_count=$((600 + RANDOM % 100))
    local start_time=$(date +%s)
    local current_time=$(date '+%Y-%m-%d %H:%M:%S')
    
    echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=${sentry_count},real_host_count=${real_host_count},dev_start_time=${start_time},dev_end_time=-1,time=${current_time}"
}

# 发送测试日志
send_test_logs() {
    log_info "发送测试日志..."
    log_info "  - 攻击日志: ${TEST_ATTACK_EVENTS} 条"
    log_info "  - 心跳日志: ${TEST_HEARTBEAT_EVENTS} 条"
    
    local sent_attack=0
    local sent_heartbeat=0
    local failed_count=0
    
    # 发送攻击日志
    for i in $(seq 1 $TEST_ATTACK_EVENTS); do
        local attack_log=$(generate_attack_log $i)
        
        if echo "$attack_log" | nc -w 1 ${LOGSTASH_HOST} ${LOGSTASH_PORT} > /dev/null 2>&1; then
            sent_attack=$((sent_attack + 1))
            log_info "  ✓ 攻击日志 $i/$TEST_ATTACK_EVENTS"
        else
            failed_count=$((failed_count + 1))
            log_warning "  ✗ 攻击日志 $i/$TEST_ATTACK_EVENTS 失败"
        fi
        
        sleep 0.2
    done
    
    # 发送心跳日志
    for i in $(seq 1 $TEST_HEARTBEAT_EVENTS); do
        local heartbeat_log=$(generate_heartbeat_log)
        
        if echo "$heartbeat_log" | nc -w 1 ${LOGSTASH_HOST} ${LOGSTASH_PORT} > /dev/null 2>&1; then
            sent_heartbeat=$((sent_heartbeat + 1))
            log_info "  ✓ 心跳日志 $i/$TEST_HEARTBEAT_EVENTS"
        else
            failed_count=$((failed_count + 1))
            log_warning "  ✗ 心跳日志 $i/$TEST_HEARTBEAT_EVENTS 失败"
        fi
        
        sleep 0.2
    done
    
    log_success "发送完成:"
    echo "  - 攻击日志: ${sent_attack}/${TEST_ATTACK_EVENTS}"
    echo "  - 心跳日志: ${sent_heartbeat}/${TEST_HEARTBEAT_EVENTS}"
    echo "  - 失败: ${failed_count}"
    
    # 等待处理
    log_info "等待 Logstash 处理 (5秒)..."
    sleep 5
}

# 检查 Logstash 统计
check_logstash_stats() {
    log_info "检查 Logstash 统计信息..."
    
    local stats=$(curl -s "http://${LOGSTASH_HOST}:${LOGSTASH_API_PORT}/_node/stats/pipelines")
    
    if [ -z "$stats" ]; then
        log_error "无法获取 Logstash 统计信息"
        return 1
    fi
    
    local events_in=$(echo "$stats" | jq '.pipelines | to_entries[0].value.events.in // 0')
    local events_filtered=$(echo "$stats" | jq '.pipelines | to_entries[0].value.events.filtered // 0')
    local events_out=$(echo "$stats" | jq '.pipelines | to_entries[0].value.events.out // 0')
    
    log_info "Pipeline 统计:"
    echo "  输入事件: ${events_in}"
    echo "  过滤后事件: ${events_filtered}"
    echo "  输出事件: ${events_out}"
    
    if [ "$events_out" -eq 0 ]; then
        log_warning "未检测到输出事件，可能存在问题"
    else
        log_success "Logstash 已处理 ${events_out} 个事件"
    fi
}

# 检查 Kafka 攻击日志
check_attack_events() {
    log_info "检查 Kafka 攻击日志 (topic: ${ATTACK_EVENTS_TOPIC})..."
    
    local messages=$(docker-compose exec -T kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic ${ATTACK_EVENTS_TOPIC} \
        --from-beginning \
        --max-messages ${TEST_ATTACK_EVENTS} \
        --timeout-ms 5000 2>/dev/null || true)
    
    if [ -z "$messages" ]; then
        log_error "未找到攻击日志消息"
        return 1
    fi
    
    local message_count=$(echo "$messages" | grep -c "deviceSerial" || echo "0")
    log_success "攻击日志: ${message_count} 条"
    
    # 验证消息格式
    local first_message=$(echo "$messages" | head -n 1)
    
    if echo "$first_message" | jq empty 2>/dev/null; then
        log_success "攻击日志格式有效 (JSON)"
        
        # 检查关键字段 (camelCase)
        local required_fields=("attackMac" "attackIp" "responseIp" "responsePort" "deviceSerial" "logTime")
        local missing_fields=()
        
        for field in "${required_fields[@]}"; do
            if ! echo "$first_message" | jq -e ".$field" > /dev/null 2>&1; then
                missing_fields+=($field)
            fi
        done
        
        if [ ${#missing_fields[@]} -eq 0 ]; then
            log_success "所有必需字段存在 (camelCase)"
        else
            log_error "缺少字段: ${missing_fields[*]}"
        fi
        
        # 显示示例消息
        log_info "攻击日志示例:"
        echo "$first_message" | jq '{deviceSerial, attackMac, attackIp, responseIp, responsePort, logTime, timestamp}'
    else
        log_error "攻击日志格式无效"
        return 1
    fi
}

# 检查 Kafka 心跳日志
check_heartbeat_events() {
    log_info "检查 Kafka 心跳日志 (topic: ${STATUS_EVENTS_TOPIC})..."
    
    local messages=$(docker-compose exec -T kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic ${STATUS_EVENTS_TOPIC} \
        --from-beginning \
        --max-messages ${TEST_HEARTBEAT_EVENTS} \
        --timeout-ms 5000 2>/dev/null || true)
    
    if [ -z "$messages" ]; then
        log_warning "未找到心跳日志消息 (可能topic尚未创建)"
        return 0
    fi
    
    local message_count=$(echo "$messages" | grep -c "deviceSerial" || echo "0")
    log_success "心跳日志: ${message_count} 条"
    
    # 验证消息格式
    local first_message=$(echo "$messages" | head -n 1)
    
    if echo "$first_message" | jq empty 2>/dev/null; then
        log_success "心跳日志格式有效 (JSON)"
        
        # 检查关键字段
        local required_fields=("deviceSerial" "sentryCount" "realHostCount" "devStartTime")
        local missing_fields=()
        
        for field in "${required_fields[@]}"; do
            if ! echo "$first_message" | jq -e ".$field" > /dev/null 2>&1; then
                missing_fields+=($field)
            fi
        done
        
        if [ ${#missing_fields[@]} -eq 0 ]; then
            log_success "所有必需字段存在"
        else
            log_error "缺少字段: ${missing_fields[*]}"
        fi
        
        # 显示示例消息
        log_info "心跳日志示例:"
        echo "$first_message" | jq '{deviceSerial, sentryCount, realHostCount, devStartTime, heartbeatTime}'
    else
        log_error "心跳日志格式无效"
        return 1
    fi
}

# 运行完整测试
run_full_test() {
    echo ""
    echo "=========================================="
    echo "  Logstash 日志摄取测试"
    echo "=========================================="
    echo ""
    
    check_dependencies
    echo ""
    
    check_logstash_status
    echo ""
    
    test_tcp_connection
    echo ""
    
    send_test_logs
    echo ""
    
    check_logstash_stats
    echo ""
    
    check_attack_events
    echo ""
    
    check_heartbeat_events
    echo ""
    
    echo "=========================================="
    echo -e "${GREEN}测试完成${NC}"
    echo "=========================================="
}

# 主函数
main() {
    # 切换到项目根目录
    cd "$(dirname "$0")/../.."
    
    run_full_test
}

# 执行
main "$@"
