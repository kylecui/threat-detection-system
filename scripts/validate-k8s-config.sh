#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(dirname "$SCRIPT_DIR")/k8s"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}K8s配置验证工具${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# 验证YAML语法
validate_yaml_syntax() {
    echo -e "${YELLOW}🔍 验证YAML语法...${NC}"
    
    local error_count=0
    local file_count=0
    
    while IFS= read -r yaml_file; do
        ((file_count++))
        
        if kubectl apply --dry-run=client -f "$yaml_file" &> /dev/null; then
            echo -e "${GREEN}✅ $(basename $yaml_file)${NC}"
        else
            echo -e "${RED}❌ $(basename $yaml_file)${NC}"
            kubectl apply --dry-run=client -f "$yaml_file" 2>&1 | head -5
            ((error_count++))
        fi
    done < <(find "$K8S_DIR/base" -name "*.yaml" -not -name "kustomization.yaml")
    
    echo ""
    echo -e "${BLUE}检查了 $file_count 个文件, 发现 $error_count 个错误${NC}"
    
    return $error_count
}

# 验证Kustomize配置
validate_kustomize() {
    echo ""
    echo -e "${YELLOW}🔍 验证Kustomize配置...${NC}"
    
    # Base
    echo -e "${BLUE}检查 base...${NC}"
    if kubectl kustomize "$K8S_DIR/base" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ base kustomization 有效${NC}"
        local base_resources=$(kubectl kustomize "$K8S_DIR/base" | grep -c "^kind:")
        echo -e "${GREEN}   生成 $base_resources 个资源${NC}"
    else
        echo -e "${RED}❌ base kustomization 无效${NC}"
        kubectl kustomize "$K8S_DIR/base" 2>&1
        return 1
    fi
    
    # Development overlay
    if [ -d "$K8S_DIR/overlays/development" ]; then
        echo -e "${BLUE}检查 development overlay...${NC}"
        if kubectl kustomize "$K8S_DIR/overlays/development" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ development overlay 有效${NC}"
            local dev_resources=$(kubectl kustomize "$K8S_DIR/overlays/development" | grep -c "^kind:")
            echo -e "${GREEN}   生成 $dev_resources 个资源${NC}"
        else
            echo -e "${RED}❌ development overlay 无效${NC}"
            kubectl kustomize "$K8S_DIR/overlays/development" 2>&1
            return 1
        fi
    fi
    
    # Production overlay
    if [ -d "$K8S_DIR/overlays/production" ]; then
        echo -e "${BLUE}检查 production overlay...${NC}"
        if kubectl kustomize "$K8S_DIR/overlays/production" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ production overlay 有效${NC}"
            local prod_resources=$(kubectl kustomize "$K8S_DIR/overlays/production" | grep -c "^kind:")
            echo -e "${GREEN}   生成 $prod_resources 个资源${NC}"
        else
            echo -e "${RED}❌ production overlay 无效${NC}"
            kubectl kustomize "$K8S_DIR/overlays/production" 2>&1
            return 1
        fi
    fi
    
    return 0
}

# 检查资源定义
check_resources() {
    echo ""
    echo -e "${YELLOW}🔍 检查资源定义...${NC}"
    
    local manifest=$(kubectl kustomize "$K8S_DIR/base")
    
    # 统计资源类型
    echo -e "${BLUE}资源类型统计:${NC}"
    echo "$manifest" | grep "^kind:" | sort | uniq -c | while read count kind; do
        echo -e "  ${GREEN}$count${NC} × $kind"
    done
    
    echo ""
    
    # 检查Service
    local services=$(echo "$manifest" | grep -A 1 "^kind: Service$" | grep "  name:" | awk '{print $2}')
    echo -e "${BLUE}Services ($(echo "$services" | wc -l)):${NC}"
    echo "$services" | while read svc; do
        echo -e "  ${GREEN}✓${NC} $svc"
    done
    
    echo ""
    
    # 检查Deployment
    local deployments=$(echo "$manifest" | grep -A 1 "^kind: Deployment$" | grep "  name:" | awk '{print $2}')
    echo -e "${BLUE}Deployments ($(echo "$deployments" | wc -l)):${NC}"
    echo "$deployments" | while read dep; do
        echo -e "  ${GREEN}✓${NC} $dep"
    done
    
    echo ""
    
    # 检查StatefulSet
    local statefulsets=$(echo "$manifest" | grep -A 1 "^kind: StatefulSet$" | grep "  name:" | awk '{print $2}')
    echo -e "${BLUE}StatefulSets ($(echo "$statefulsets" | wc -l)):${NC}"
    echo "$statefulsets" | while read sts; do
        echo -e "  ${GREEN}✓${NC} $sts"
    done
}

# 检查镜像引用
check_images() {
    echo ""
    echo -e "${YELLOW}🔍 检查镜像引用...${NC}"
    
    local manifest=$(kubectl kustomize "$K8S_DIR/base")
    local images=$(echo "$manifest" | grep "image:" | awk '{print $2}' | sort -u)
    
    echo -e "${BLUE}镜像列表:${NC}"
    echo "$images" | while read img; do
        # 检查是否是自定义镜像
        if [[ $img == threat-detection/* ]]; then
            if docker image inspect "$img" &> /dev/null; then
                echo -e "  ${GREEN}✅${NC} $img"
            else
                echo -e "  ${YELLOW}⚠️${NC}  $img ${YELLOW}(未构建)${NC}"
            fi
        else
            echo -e "  ${BLUE}ℹ️${NC}  $img ${BLUE}(公共镜像)${NC}"
        fi
    done
}

# 检查配置文件完整性
check_completeness() {
    echo ""
    echo -e "${YELLOW}🔍 检查配置完整性...${NC}"
    
    local expected_files=(
        "namespace.yaml"
        "postgres.yaml"
        "redis.yaml"
        "zookeeper.yaml"
        "kafka.yaml"
        "kafka-topic-init.yaml"
        "logstash.yaml"
        "data-ingestion.yaml"
        "stream-processing.yaml"
        "taskmanager.yaml"
        "customer-management.yaml"
        "threat-assessment.yaml"
        "alert-management.yaml"
        "kustomization.yaml"
    )
    
    local missing_count=0
    
    for file in "${expected_files[@]}"; do
        if [ -f "$K8S_DIR/base/$file" ]; then
            echo -e "  ${GREEN}✅${NC} $file"
        else
            echo -e "  ${RED}❌${NC} $file ${RED}(缺失)${NC}"
            ((missing_count++))
        fi
    done
    
    echo ""
    if [ $missing_count -eq 0 ]; then
        echo -e "${GREEN}✅ 所有配置文件完整！${NC}"
    else
        echo -e "${RED}❌ 缺失 $missing_count 个配置文件${NC}"
        return 1
    fi
    
    return 0
}

# 生成部署预览
generate_preview() {
    echo ""
    echo -e "${YELLOW}📋 生成部署预览...${NC}"
    echo ""
    
    read -p "选择环境 (base/development/production): " env_choice
    
    case $env_choice in
        base|"")
            PREVIEW_PATH="$K8S_DIR/base"
            ;;
        dev|development)
            PREVIEW_PATH="$K8S_DIR/overlays/development"
            ;;
        prod|production)
            PREVIEW_PATH="$K8S_DIR/overlays/production"
            ;;
        *)
            echo -e "${RED}无效选择${NC}"
            return 1
            ;;
    esac
    
    local preview_file="/tmp/k8s-preview-$(date +%Y%m%d-%H%M%S).yaml"
    
    if kubectl kustomize "$PREVIEW_PATH" > "$preview_file"; then
        echo -e "${GREEN}✅ 部署预览已生成: $preview_file${NC}"
        echo ""
        echo -e "${BLUE}文件统计:${NC}"
        wc -l "$preview_file"
        echo ""
        echo -e "${BLUE}资源数量:${NC}"
        grep "^kind:" "$preview_file" | sort | uniq -c
        echo ""
        echo -e "${YELLOW}查看完整内容: cat $preview_file${NC}"
    else
        echo -e "${RED}❌ 生成失败${NC}"
        return 1
    fi
}

# 主函数
main() {
    local exit_code=0
    
    # 检查kubectl
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}❌ kubectl未安装${NC}"
        exit 1
    fi
    
    check_completeness || exit_code=1
    validate_yaml_syntax || exit_code=1
    validate_kustomize || exit_code=1
    check_resources
    check_images
    
    echo ""
    echo -e "${BLUE}=====================================${NC}"
    
    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}✅ 所有验证通过！${NC}"
        echo -e "${GREEN}K8s配置就绪,可以部署！${NC}"
        echo ""
        echo -e "${YELLOW}下一步:${NC}"
        echo -e "  1. 构建镜像: ${GREEN}./scripts/build-all-images.sh${NC}"
        echo -e "  2. 一键部署: ${GREEN}./scripts/k8s-deploy.sh${NC}"
    else
        echo -e "${RED}❌ 验证失败,请修复错误${NC}"
    fi
    
    echo -e "${BLUE}=====================================${NC}"
    echo ""
    
    # 可选: 生成部署预览
    read -p "是否生成部署预览? (y/N): " gen_preview
    if [[ $gen_preview =~ ^[Yy]$ ]]; then
        generate_preview
    fi
    
    exit $exit_code
}

main
