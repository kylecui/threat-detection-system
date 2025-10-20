#!/bin/bash

echo "🔍 威胁检测系统 - 连接测试"
echo "============================"
echo ""

# 测试API Gateway
echo "📡 测试API Gateway连接..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8888/actuator/health | grep -q "200"; then
    echo "✅ API Gateway (http://localhost:8888) - 可访问"
else
    echo "❌ API Gateway (http://localhost:8888) - 不可访问"
    echo "   请先启动API Gateway服务"
fi
echo ""

# 测试前端开发服务器
echo "🌐 测试前端开发服务器..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 | grep -q "200"; then
    echo "✅ 前端开发服务器 (http://localhost:3000) - 运行中"
else
    echo "ℹ️  前端开发服务器 (http://localhost:3000) - 未启动"
    echo "   启动命令: docker-compose up frontend-dev"
fi
echo ""

# 测试前端生产服务器
echo "🏭 测试前端生产服务器..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost | grep -q "200"; then
    echo "✅ 前端生产服务器 (http://localhost) - 运行中"
else
    echo "ℹ️  前端生产服务器 (http://localhost) - 未启动"
    echo "   启动命令: docker-compose up -d frontend-prod"
fi
echo ""

# Docker容器状态
echo "🐳 Docker容器状态..."
docker ps --filter "name=frontend" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "   无运行中的前端容器"
echo ""

echo "✅ 测试完成"
