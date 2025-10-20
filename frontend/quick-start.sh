#!/bin/bash

echo "🚀 快速启动 - 威胁检测系统前端"
echo "================================="
echo ""
echo "📝 说明:"
echo "   前端服务会连接到宿主机的 API Gateway (localhost:8888)"
echo "   请确保API Gateway已启动"
echo ""
echo "🔍 检查API Gateway状态..."

# 检查API Gateway
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8888/actuator/health 2>/dev/null | grep -q "200"; then
    echo "   ✅ API Gateway 运行中 (http://localhost:8888)"
else
    echo "   ⚠️  API Gateway 未运行"
    echo ""
    echo "   启动API Gateway:"
    echo "   cd ../docker"
    echo "   docker-compose up -d api-gateway"
    echo ""
    read -p "   是否继续启动前端? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "   已取消"
        exit 0
    fi
fi

echo ""
echo "🎨 启动前端开发环境..."
echo "   访问地址: http://localhost:3000"
echo "   API地址: http://localhost:8888"
echo ""
echo "提示: 按 Ctrl+C 停止服务"
echo ""

docker-compose up frontend-dev
