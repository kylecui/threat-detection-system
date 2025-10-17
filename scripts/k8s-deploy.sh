#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(dirname "$SCRIPT_DIR")/k8s"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}威胁检测系统 - K8s 一键部署脚本${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# 检查前置条件
check_prerequisites() {
    echo -e "${YELLOW}🔍 检查前置条件...${NC}"
    
    # 检查kubectl
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}❌ kubectl未安装，请先安装kubectl${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ kubectl已安装: $(kubectl version --client --short 2>/dev/null | head -1)${NC}"
    
    # 检查kubectl连接
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}❌ 无法连接到Kubernetes集群，请检查kubeconfig${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Kubernetes集群连接正常${NC}"
    
    # 检查kustomize
    if ! kubectl kustomize --help &> /dev/null; then
        echo -e "${RED}❌ kustomize功能不可用，请升级kubectl${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ kustomize功能可用${NC}"
    
    echo ""
}

# 选择部署环境
select_environment() {
    echo -e "${YELLOW}📋 请选择部署环境:${NC}"
    echo "  1) Development (开发环境)"
    echo "  2) Production (生产环境)"
    echo "  3) Base (基础配置)"
    echo ""
    read -p "请输入选择 (1-3): " env_choice
    
    case $env_choice in
        1)
            DEPLOY_ENV="development"
            KUSTOMIZE_PATH="$K8S_DIR/overlays/development"
            ;;
        2)
            DEPLOY_ENV="production"
            KUSTOMIZE_PATH="$K8S_DIR/overlays/production"
            ;;
        3)
            DEPLOY_ENV="base"
            KUSTOMIZE_PATH="$K8S_DIR/base"
            ;;
        *)
            echo -e "${RED}❌ 无效选择${NC}"
            exit 1
            ;;
    esac
    
    echo -e "${GREEN}✅ 选择环境: $DEPLOY_ENV${NC}"
    echo ""
}

# 检查镜像
check_images() {
    echo -e "${YELLOW}🐳 检查Docker镜像...${NC}"
    
    local images=(
        "threat-detection/data-ingestion:latest"
        "threat-detection/customer-management:latest"
        "threat-detection/threat-assessment:latest"
        "threat-detection/alert-management:latest"
    )
    
    local missing_images=()
    
    for img in "${images[@]}"; do
        if ! docker image inspect "$img" &> /dev/null; then
            missing_images+=("$img")
        fi
    done
    
    if [ ${#missing_images[@]} -gt 0 ]; then
        echo -e "${YELLOW}⚠️  以下镜像未找到:${NC}"
        for img in "${missing_images[@]}"; do
            echo "   - $img"
        done
        echo ""
        read -p "是否继续部署? (y/N): " continue_choice
        if [[ ! $continue_choice =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}提示: 运行 ./scripts/build-all-images.sh 构建镜像${NC}"
            exit 0
        fi
    else
        echo -e "${GREEN}✅ 所有镜像已就绪${NC}"
    fi
    echo ""
}

# 部署资源
deploy_resources() {
    echo -e "${YELLOW}🚀 开始部署到Kubernetes...${NC}"
    echo -e "${BLUE}环境: $DEPLOY_ENV${NC}"
    echo -e "${BLUE}路径: $KUSTOMIZE_PATH${NC}"
    echo ""
    
    # 确认部署
    read -p "确认部署? (y/N): " confirm
    if [[ ! $confirm =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}部署已取消${NC}"
        exit 0
    fi
    
    echo ""
    echo -e "${YELLOW}📦 应用Kubernetes配置...${NC}"
    
    # 使用kustomize部署
    if kubectl apply -k "$KUSTOMIZE_PATH"; then
        echo -e "${GREEN}✅ 资源部署成功${NC}"
    else
        echo -e "${RED}❌ 资源部署失败${NC}"
        exit 1
    fi
    
    echo ""
}

# 等待Pod就绪
wait_for_pods() {
    echo -e "${YELLOW}⏳ 等待Pod启动...${NC}"
    echo ""
    
    local namespace="threat-detection"
    local timeout=300  # 5分钟超时
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        # 获取Pod状态
        local total_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | wc -l)
        local running_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | grep "Running" | wc -l)
        local ready_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | awk '{if ($2 ~ /^[0-9]+\/[0-9]+$/) {split($2, a, "/"); if (a[1] == a[2]) print $0}}' | wc -l)
        
        echo -e "${BLUE}状态: $ready_pods/$total_pods Pods Ready${NC}"
        
        # 显示未就绪的Pod
        kubectl get pods -n $namespace --no-headers 2>/dev/null | grep -v "Running.*[0-9]/[0-9]" | awk '{print "  ⏳ " $1 " - " $3}' || true
        
        # 检查是否全部就绪
        if [ "$total_pods" -gt 0 ] && [ "$ready_pods" -eq "$total_pods" ]; then
            echo -e "${GREEN}✅ 所有Pod已就绪！${NC}"
            return 0
        fi
        
        sleep 5
        elapsed=$((elapsed + 5))
        echo ""
    done
    
    echo -e "${YELLOW}⚠️  超时: 部分Pod未在${timeout}秒内就绪${NC}"
    return 1
}

# 显示部署信息
show_deployment_info() {
    echo ""
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}📊 部署信息${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    
    local namespace="threat-detection"
    
    # Pods
    echo -e "${YELLOW}Pods:${NC}"
    kubectl get pods -n $namespace -o wide
    echo ""
    
    # Services
    echo -e "${YELLOW}Services:${NC}"
    kubectl get svc -n $namespace
    echo ""
    
    # PVCs
    echo -e "${YELLOW}Persistent Volume Claims:${NC}"
    kubectl get pvc -n $namespace
    echo ""
    
    # Logstash LoadBalancer外部IP
    echo -e "${YELLOW}Logstash外部访问:${NC}"
    local logstash_ip=$(kubectl get svc logstash -n $namespace -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
    if [ -n "$logstash_ip" ]; then
        echo -e "  rsyslog: ${GREEN}$logstash_ip:9080${NC}"
        echo -e "  monitoring: ${GREEN}$logstash_ip:9600${NC}"
    else
        echo -e "  ${YELLOW}⏳ 等待LoadBalancer分配外部IP...${NC}"
        echo -e "  ${YELLOW}运行以下命令查看: kubectl get svc logstash -n $namespace${NC}"
    fi
    echo ""
}

# 显示后续步骤
show_next_steps() {
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}✅ 部署完成！${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    echo -e "${YELLOW}📝 后续步骤:${NC}"
    echo ""
    echo "1. 查看Pod日志:"
    echo -e "   ${GREEN}kubectl logs -n threat-detection -l app=logstash -f${NC}"
    echo ""
    echo "2. 访问API服务 (通过Port-Forward):"
    echo -e "   ${GREEN}kubectl port-forward -n threat-detection svc/customer-management 8081:8081${NC}"
    echo -e "   ${GREEN}kubectl port-forward -n threat-detection svc/threat-assessment 8082:8082${NC}"
    echo -e "   ${GREEN}kubectl port-forward -n threat-detection svc/alert-management 8083:8083${NC}"
    echo ""
    echo "3. 获取Logstash外部IP:"
    echo -e "   ${GREEN}kubectl get svc logstash -n threat-detection${NC}"
    echo ""
    echo "4. 测试发送日志:"
    echo -e "   ${GREEN}echo \"test_log\" | nc <LOGSTASH_IP> 9080${NC}"
    echo ""
    echo "5. 查看Kafka Topics:"
    echo -e "   ${GREEN}kubectl exec -it -n threat-detection kafka-0 -- kafka-topics --list --bootstrap-server localhost:9092${NC}"
    echo ""
    echo "6. 卸载部署:"
    echo -e "   ${GREEN}kubectl delete -k $KUSTOMIZE_PATH${NC}"
    echo ""
    echo -e "${BLUE}=====================================${NC}"
}

# 主函数
main() {
    check_prerequisites
    select_environment
    check_images
    deploy_resources
    wait_for_pods
    show_deployment_info
    show_next_steps
}

# 运行主函数
main
