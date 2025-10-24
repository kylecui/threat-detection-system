#!/bin/bash

# 容器重建标准脚本
# 用法: ./rebuild_service.sh <service-name>
# 示例: ./rebuild_service.sh threat-assessment

set -e  # 遇到错误立即退出

SERVICE_NAME=$1

if [ -z "$SERVICE_NAME" ]; then
    echo "❌ 错误: 请提供服务名称"
    echo ""
    echo "用法: ./rebuild_service.sh <service-name>"
    echo ""
    echo "可用服务:"
    echo "  - threat-assessment"
    echo "  - data-ingestion"
    echo "  - stream-processing"
    echo "  - alert-management"
    echo "  - api-gateway"
    echo ""
    echo "示例: ./rebuild_service.sh threat-assessment"
    exit 1
fi

echo "==================================================
容器重建流程: $SERVICE_NAME
==================================================
"

# 第一步：重新编译
echo "📦 [1/3] 重新编译 Maven 项目..."
cd ~/threat-detection-system/services/$SERVICE_NAME

if [ ! -f "pom.xml" ]; then
    echo "❌ 错误: 服务 $SERVICE_NAME 不存在或不是 Maven 项目"
    exit 1
fi

mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 编译失败，终止流程"
    exit 1
fi
echo "✅ 编译成功"
echo ""

# 第二步：重构容器
echo "🐳 [2/3] 重构 Docker 容器..."
cd ~/threat-detection-system/docker

echo "  → 停止并删除容器（包括数据卷）..."
docker compose down -v $SERVICE_NAME

echo "  → 重新构建镜像（无缓存）..."
docker compose build --no-cache $SERVICE_NAME

if [ $? -ne 0 ]; then
    echo "❌ 镜像构建失败，终止流程"
    exit 1
fi

echo "  → 启动新容器（强制重建）..."
docker compose up -d --force-recreate $SERVICE_NAME

if [ $? -ne 0 ]; then
    echo "❌ 容器启动失败，检查日志"
    docker logs ${SERVICE_NAME}-service --tail 50
    exit 1
fi
echo "✅ 容器重构成功"
echo ""

# 第三步：检查容器状态
echo "🔍 [3/3] 检查容器状态..."
sleep 5  # 等待容器完全启动

CONTAINER_STATUS=$(docker compose ps | grep $SERVICE_NAME | awk '{print $NF}')

echo ""
docker compose ps | head -1
docker compose ps | grep $SERVICE_NAME

if echo "$CONTAINER_STATUS" | grep -q "Up"; then
    echo ""
    echo "✅ 容器运行正常"
else
    echo ""
    echo "⚠️ 警告: 容器状态异常，请检查日志"
    docker logs ${SERVICE_NAME}-service --tail 30
fi

echo ""
echo "==================================================
✅ 重建完成！
==================================================

📋 下一步:

1. 查看完整日志:
   docker logs ${SERVICE_NAME}-service --tail 50

2. 实时监控日志:
   docker logs ${SERVICE_NAME}-service -f

3. 运行集成测试:
   cd ~/threat-detection-system/scripts
   bash test_v4_phase1_integration.sh

4. 检查服务健康状态:
   curl http://localhost:8083/actuator/health

==================================================
"
