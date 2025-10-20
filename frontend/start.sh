#!/bin/bash

echo "🚀 威胁检测系统 - 前端开发环境启动脚本"
echo "=========================================="
echo ""

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查必要文件
if [ ! -f "package.json" ]; then
    echo "❌ package.json不存在"
    exit 1
fi

if [ ! -f "Dockerfile" ]; then
    echo "❌ Dockerfile不存在"
    exit 1
fi

echo "✅ 环境检查通过"
echo ""

# 选择环境
echo "请选择环境:"
echo "1) 开发环境 (localhost:3000, 支持热更新)"
echo "2) 生产环境 (localhost:80, Nginx服务)"
read -p "请输入选择 [1/2]: " choice

case $choice in
    1)
        echo ""
        echo "🔧 启动开发环境..."
        echo "访问地址: http://localhost:3000"
        echo "API地址: http://localhost:8888"
        echo ""
        echo "提示: 按 Ctrl+C 停止服务"
        echo ""
        docker-compose up frontend-dev
        ;;
    2)
        echo ""
        echo "🏭 构建并启动生产环境..."
        echo "访问地址: http://localhost"
        echo "API代理: /api -> http://api-gateway:8888"
        echo ""
        docker-compose up -d frontend-prod
        echo ""
        echo "✅ 生产环境已启动"
        echo ""
        echo "查看日志: docker-compose logs -f frontend-prod"
        echo "停止服务: docker-compose down"
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac
