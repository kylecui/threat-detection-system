#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 镜像版本
VERSION="${VERSION:-latest}"
REGISTRY="${REGISTRY:-threat-detection}"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}威胁检测系统 - Docker镜像批量构建${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""
echo -e "${YELLOW}镜像版本: ${VERSION}${NC}"
echo -e "${YELLOW}镜像仓库: ${REGISTRY}${NC}"
echo ""

# 服务列表
declare -A SERVICES=(
    ["data-ingestion"]="services/data-ingestion"
    ["customer-management"]="services/customer-management"
    ["threat-assessment"]="services/threat-assessment"
    ["alert-management"]="services/alert-management"
    ["stream-processing"]="services/stream-processing"
    ["api-gateway"]="services/api-gateway"
)

# 检查Maven
check_maven() {
    echo -e "${YELLOW}🔍 检查Maven...${NC}"
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven未安装${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Maven已安装: $(mvn -version | head -1)${NC}"
    echo ""
}

# 检查Docker
check_docker() {
    echo -e "${YELLOW}🔍 检查Docker...${NC}"
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker未安装${NC}"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        echo -e "${RED}❌ Docker daemon未运行${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Docker已安装: $(docker version --format '{{.Server.Version}}')${NC}"
    echo ""
}

# Maven构建
maven_build() {
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}📦 Maven构建所有服务${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
    
    echo -e "${YELLOW}清理旧构建...${NC}"
    mvn clean -q
    
    echo -e "${YELLOW}开始Maven构建...${NC}"
    if mvn package -DskipTests -T 4 -q; then
        echo -e "${GREEN}✅ Maven构建成功${NC}"
    else
        echo -e "${RED}❌ Maven构建失败${NC}"
        exit 1
    fi
    
    echo ""
}

# 构建单个镜像
build_image() {
    local service_name=$1
    local service_path=$2
    local image_name="${REGISTRY}/${service_name}:${VERSION}"
    
    echo -e "${YELLOW}🐳 构建镜像: ${image_name}${NC}"
    
    # 检查Dockerfile
    if [ ! -f "$PROJECT_ROOT/$service_path/Dockerfile" ]; then
        echo -e "${RED}❌ Dockerfile不存在: $service_path/Dockerfile${NC}"
        return 1
    fi
    
    # 检查JAR文件
    local jar_file=$(find "$PROJECT_ROOT/$service_path/target" -name "*.jar" ! -name "*-sources.jar" 2>/dev/null | head -1)
    if [ -z "$jar_file" ]; then
        echo -e "${RED}❌ JAR文件不存在: $service_path/target/*.jar${NC}"
        return 1
    fi
    
    echo -e "${BLUE}   JAR: $(basename $jar_file)${NC}"
    
    # 构建镜像
    if docker build \
        --build-arg JAR_FILE="target/$(basename $jar_file)" \
        -t "$image_name" \
        -f "$PROJECT_ROOT/$service_path/Dockerfile" \
        "$PROJECT_ROOT/$service_path" \
        2>&1 | grep -E "Step |Successfully built|Successfully tagged" || docker build \
        --build-arg JAR_FILE="target/$(basename $jar_file)" \
        -t "$image_name" \
        -f "$PROJECT_ROOT/$service_path/Dockerfile" \
        "$PROJECT_ROOT/$service_path"; then
        
        echo -e "${GREEN}✅ 镜像构建成功: ${image_name}${NC}"
        
        # 添加latest标签
        if [ "$VERSION" != "latest" ]; then
            docker tag "$image_name" "${REGISTRY}/${service_name}:latest"
            echo -e "${GREEN}   标签: ${REGISTRY}/${service_name}:latest${NC}"
        fi
        
        return 0
    else
        echo -e "${RED}❌ 镜像构建失败: ${image_name}${NC}"
        return 1
    fi
}

# 构建所有镜像
build_all_images() {
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}🐳 构建所有Docker镜像${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    
    local success_count=0
    local fail_count=0
    local failed_services=()
    
    for service in "${!SERVICES[@]}"; do
        echo ""
        if build_image "$service" "${SERVICES[$service]}"; then
            ((success_count++))
        else
            ((fail_count++))
            failed_services+=("$service")
        fi
    done
    
    echo ""
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}📊 构建统计${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${GREEN}成功: ${success_count}${NC}"
    echo -e "${RED}失败: ${fail_count}${NC}"
    
    if [ $fail_count -gt 0 ]; then
        echo ""
        echo -e "${RED}失败的服务:${NC}"
        for service in "${failed_services[@]}"; do
            echo -e "${RED}  - $service${NC}"
        done
        exit 1
    fi
    
    echo ""
}

# 显示镜像列表
show_images() {
    echo -e "${BLUE}=====================================${NC}"
    echo -e "${BLUE}📋 构建的镜像列表${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    
    docker images | grep -E "^${REGISTRY}/|REPOSITORY" | head -20
    
    echo ""
}

# 推送镜像 (可选)
push_images() {
    echo ""
    read -p "是否推送镜像到远程仓库? (y/N): " push_choice
    
    if [[ $push_choice =~ ^[Yy]$ ]]; then
        echo ""
        echo -e "${YELLOW}🚀 推送镜像...${NC}"
        
        for service in "${!SERVICES[@]}"; do
            local image_name="${REGISTRY}/${service}:${VERSION}"
            echo -e "${BLUE}推送: ${image_name}${NC}"
            
            if docker push "$image_name"; then
                echo -e "${GREEN}✅ 推送成功${NC}"
                
                if [ "$VERSION" != "latest" ]; then
                    docker push "${REGISTRY}/${service}:latest"
                fi
            else
                echo -e "${RED}❌ 推送失败${NC}"
            fi
        done
    fi
}

# 清理旧镜像 (可选)
cleanup_old_images() {
    echo ""
    read -p "是否清理未使用的镜像? (y/N): " cleanup_choice
    
    if [[ $cleanup_choice =~ ^[Yy]$ ]]; then
        echo ""
        echo -e "${YELLOW}🧹 清理未使用的镜像...${NC}"
        docker image prune -f
        echo -e "${GREEN}✅ 清理完成${NC}"
    fi
}

# 主函数
main() {
    check_maven
    check_docker
    maven_build
    build_all_images
    show_images
    push_images
    cleanup_old_images
    
    echo ""
    echo -e "${GREEN}=====================================${NC}"
    echo -e "${GREEN}✅ 所有镜像构建完成！${NC}"
    echo -e "${GREEN}=====================================${NC}"
    echo ""
    echo -e "${YELLOW}下一步:${NC}"
    echo -e "  1. 部署到K8s: ${GREEN}./scripts/k8s-deploy.sh${NC}"
    echo -e "  2. 查看镜像: ${GREEN}docker images | grep ${REGISTRY}${NC}"
    echo ""
}

# 运行主函数
main
