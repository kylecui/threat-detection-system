# ===========================
# 阶段1: 依赖安装 (缓存优化)
# ===========================
FROM node:20-alpine AS deps

WORKDIR /app

# 复制依赖文件
COPY package.json ./

ARG USE_CHINA_MIRROR=false
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
      npm config set registry https://registry.npmmirror.com; \
    fi && \
    npm install --legacy-peer-deps && \
    npm install terser --legacy-peer-deps

# ===========================
# 阶段2: 开发环境
# ===========================
FROM node:20-alpine AS development

WORKDIR /app

# 复制依赖
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 暴露开发服务器端口
EXPOSE 3000

# 启动开发服务器 (支持热更新)
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

# ===========================
# 阶段3: 构建生产版本
# ===========================
FROM node:20-alpine AS builder

WORKDIR /app

# 复制依赖和源码
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 设置生产环境变量
ENV NODE_ENV=production

# 构建应用
RUN npm run build

# ===========================
# 阶段4: 生产运行环境 (Nginx)
# ===========================
FROM nginx:1.25-alpine AS production

# 安装健康检查工具
RUN apk add --no-cache curl

# 复制Nginx配置
COPY nginx.conf /etc/nginx/nginx.conf
COPY default.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 创建健康检查脚本
RUN echo '#!/bin/sh' > /healthcheck.sh && \
    echo 'curl -f http://localhost:80/health || exit 1' >> /healthcheck.sh && \
    chmod +x /healthcheck.sh

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD ["/healthcheck.sh"]

# 暴露端口
EXPOSE 80

# 启动Nginx
CMD ["nginx", "-g", "daemon off;"]
