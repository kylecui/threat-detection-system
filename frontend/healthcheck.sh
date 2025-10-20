#!/bin/sh
# Nginx健康检查脚本

# 检查Nginx进程
if ! pgrep nginx > /dev/null; then
    echo "Nginx进程未运行"
    exit 1
fi

# 检查HTTP响应
if ! wget --spider -q http://localhost/health; then
    echo "健康检查端点无响应"
    exit 1
fi

echo "健康检查通过"
exit 0
