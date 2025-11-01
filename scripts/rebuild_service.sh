#!/bin/bash

# 容器重建标准脚本
# 用法: ./rebuild_service.sh <service-name>
# 示例: ./rebuild_service.sh threat-assessment

set +e  # 遇到错误立即退出

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
    echo "  - customer-management"
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
# 加入timer倒计时
for i in {30..1}; do
    echo -ne "\r🔍 [3/3] 检查容器状态...等待: $i 秒..."
    sleep 1
done
# sleep 30  # 等待容器完全启动


CONTAINER_STATUS=$(docker compose ps | grep $SERVICE_NAME)

echo ""
docker compose ps | head -1
docker compose ps | grep $SERVICE_NAME

if echo "$CONTAINER_STATUS" | grep -q "(healthy)"; then
    echo ""
    echo "✅ 容器运行正常"
elif echo "$CONTAINER_STATUS" | grep -q "healthy:starting"; then
    echo ""
    echo "❌ 容器启动时间过长"
    echo "请检查日志以获取更多信息"
    # docker logs ${SERVICE_NAME}-service --tail 30
    docker compose logs $SERVICE_NAME --tail 30
elif echo "$CONTAINER_STATUS" | grep -q "unhealthy"; then
    echo ""
    echo "❌ 容器状态不健康"
    echo "请检查日志以获取更多信息"
    # docker logs ${SERVICE_NAME}-service --tail 30
    docker compose logs $SERVICE_NAME --tail 30
elif echo "$CONTAINER_STATUS" | grep -q "Up"; then
    echo ""
    echo "⚠️ 警告: 容器正在运行但未通过健康检查"
    echo "请检查日志以获取更多信息"
    docker compose logs $SERVICE_NAME --tail 30
else
    echo ""
    echo "⚠️ 警告: 容器状态异常，请检查日志"
    docker compose logs $SERVICE_NAME --tail 30
fi

echo ""
echo "==================================================
✅ 重建完成！
==================================================
"
