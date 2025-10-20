# 🔧 问题修复说明

## 问题描述

执行 `./start.sh` 时出现错误：
```
ERROR: Service 'frontend-dev' depends on service 'api-gateway' which is undefined.
```

## 根本原因

`docker-compose.yml` 中引用了 `api-gateway` 服务，但该服务在前端的compose文件中未定义。

## 解决方案

将前端服务配置为**独立运行**，通过 `host.docker.internal` 连接到宿主机上运行的API Gateway。

### 修改内容

1. **docker-compose.yml**:
   - ❌ 移除 `depends_on: api-gateway`
   - ❌ 移除 `networks: threat-detection-network`
   - ✅ 添加 `extra_hosts: host.docker.internal:host-gateway`
   - ✅ 修改 `VITE_API_BASE_URL` 为 `http://host.docker.internal:8888`

2. **default.conf** (Nginx配置):
   - ✅ 修改 `proxy_pass` 为 `http://host.docker.internal:8888`

## 新的启动方式

### 方式1: 快速启动脚本 (推荐)

```bash
./quick-start.sh
```

**功能**:
- ✅ 自动检查API Gateway状态
- ✅ 如果API Gateway未运行，提供启动指令
- ✅ 询问是否继续启动前端
- ✅ 启动开发环境

### 方式2: 直接启动

```bash
# 确保API Gateway已启动
curl http://localhost:8888/health

# 启动前端
docker-compose up frontend-dev
```

### 方式3: 使用start.sh (更新后)

```bash
./start.sh
# 选择 1 - 开发环境
```

## 网络架构变化

### 之前 (错误配置)
```
┌─────────────────┐
│  frontend-dev   │
│                 │
│  depends_on:    │───X──► api-gateway (未定义)
│  - api-gateway  │
└─────────────────┘
```

### 现在 (修复后)
```
┌──────────────────────┐     host.docker.internal      ┌─────────────────┐
│  frontend-dev        │────────────────────────────────►│  localhost:8888 │
│  (Docker容器)        │                                │  API Gateway    │
│                      │                                │  (宿主机)        │
│  VITE_API_BASE_URL=  │                                └─────────────────┘
│  host.docker...8888  │
└──────────────────────┘
```

## 验证步骤

### 1. 检查API Gateway
```bash
curl http://localhost:8888/health
# 期望: 200 OK
```

### 2. 验证Docker配置
```bash
docker-compose config
# 检查是否有错误
```

### 3. 启动前端
```bash
./quick-start.sh
# 或
docker-compose up frontend-dev
```

### 4. 测试连接
```bash
# 在另一个终端
curl http://localhost:3000
# 期望: React应用HTML
```

## 生产环境

生产环境同样使用 `host.docker.internal` 连接API Gateway：

```bash
# 启动生产环境
docker-compose up -d frontend-prod

# 访问
http://localhost

# API请求路径
http://localhost/api → http://host.docker.internal:8888
```

## 替代方案（可选）

如果需要前端和后端在同一个Docker网络中运行，可以：

### 方案A: 使用主项目的docker-compose

在项目根目录 `/home/kylecui/threat-detection-system/docker/` 中添加前端服务：

```yaml
# docker/docker-compose.yml
services:
  # ... 现有服务 ...
  
  frontend-dev:
    build:
      context: ../frontend
      target: development
    ports:
      - "3000:3000"
    volumes:
      - ../frontend/src:/app/src
    environment:
      - VITE_API_BASE_URL=http://api-gateway:8888
    depends_on:
      - api-gateway
    networks:
      - default
```

### 方案B: 加入现有网络

```bash
# 检查现有网络
docker network ls | grep threat-detection

# 在docker-compose.yml中引用
networks:
  default:
    external: true
    name: threat-detection-system_default
```

**注意**: 当前的独立配置更灵活，推荐使用！

## 常见问题

### Q1: 为什么使用 host.docker.internal？

**A**: 这是Docker提供的特殊DNS名称，允许容器访问宿主机的服务。适用于：
- Windows Docker Desktop
- macOS Docker Desktop  
- Linux Docker 20.10+ (需要 `extra_hosts` 配置)

### Q2: API Gateway必须在宿主机运行吗？

**A**: 不是必须的。可以选择：
- ✅ 宿主机运行 (当前方案)
- ✅ Docker容器运行 (需要共享网络)
- ✅ Kubernetes运行 (需要Service配置)

### Q3: 如何在同一网络中运行？

**A**: 使用主项目的docker-compose文件，参考"替代方案"。

### Q4: 生产环境的API代理配置在哪？

**A**: 在 `default.conf` 中，Nginx会将 `/api/*` 请求代理到 API Gateway。

## 文件修改清单

```
✅ docker-compose.yml      - 移除depends_on, 添加extra_hosts
✅ default.conf            - 修改proxy_pass地址
✅ quick-start.sh          - 新增启动脚本
✅ README.md               - 更新启动说明
✅ TROUBLESHOOTING.md      - 本文件
```

## 下次启动

直接运行：
```bash
./quick-start.sh
```

就这么简单！✅
