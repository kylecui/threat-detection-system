# 威胁检测系统 - 前端

Cloud-Native Threat Detection System Frontend

## 技术栈

- **框架**: React 18 + TypeScript
- **UI库**: Ant Design 5 + Ant Design Pro Components
- **构建工具**: Vite 5
- **HTTP客户端**: Axios
- **路由**: React Router v6
- **图表**: @ant-design/charts (基于AntV G2)

## 快速开始

### 前置条件

**重要**: 前端服务需要连接到API Gateway，请先确保API Gateway已启动：

```bash
# 检查API Gateway状态
curl http://localhost:8888/actuator/health

# 如果未启动，先启动API Gateway
cd ../docker
docker-compose up -d api-gateway
```

### Docker开发环境 (推荐)

使用快速启动脚本（会自动检查API Gateway状态）：

```bash
./quick-start.sh
# 访问: http://localhost:3000
```

或者直接使用Docker Compose：

```bash
# 启动开发环境
docker-compose up frontend-dev

# 访问应用
http://localhost:3000

**特点**:
- ✅ 开发环境100%隔离
- ✅ 支持热更新 (Volume挂载源码)
- ✅ 自动连接后端API Gateway

### 2. Docker生产环境

```bash
# 构建并启动生产容器
docker-compose up -d frontend-prod

# 访问: http://localhost
```

**特点**:
- ✅ 多阶段构建优化
- ✅ Nginx高性能Web服务器
- ✅ Gzip压缩
- ✅ 静态资源缓存

### 3. 本地开发 (可选)

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

## 项目结构

```
frontend/
├── src/
│   ├── pages/              # 页面组件
│   │   ├── Dashboard/      # 威胁检测仪表盘
│   │   ├── ThreatList/     # 威胁评估列表
│   │   ├── AlertCenter/    # 告警中心
│   │   ├── CustomerMgmt/   # 客户管理
│   │   └── SystemMonitor/  # 系统监控
│   ├── components/         # 通用组件
│   ├── services/           # API服务层
│   ├── utils/              # 工具函数
│   ├── hooks/              # 自定义Hooks
│   ├── types/              # TypeScript类型定义
│   └── App.tsx             # 根组件
├── public/                 # 静态资源
├── Dockerfile              # 多阶段Docker构建文件
├── docker-compose.yml      # Docker Compose配置
├── nginx.conf              # Nginx主配置
├── default.conf            # Nginx站点配置
├── vite.config.ts          # Vite配置
└── tsconfig.json           # TypeScript配置
```

## Docker命令速查

### 开发环境

```bash
# 启动开发容器
docker-compose up frontend-dev

# 后台启动
docker-compose up -d frontend-dev

# 查看日志
docker-compose logs -f frontend-dev

# 进入容器
docker-compose exec frontend-dev sh

# 重启容器
docker-compose restart frontend-dev

# 停止并删除容器
docker-compose down
```

### 生产环境

```bash
# 构建镜像
docker-compose build frontend-prod

# 启动生产容器
docker-compose up -d frontend-prod

# 查看容器状态
docker-compose ps

# 查看资源使用
docker stats threat-detection-ui-prod
```

### 镜像管理

```bash
# 查看镜像
docker images | grep threat-detection-ui

# 删除旧镜像
docker image prune -a

# 导出镜像
docker save -o threat-detection-ui.tar frontend-prod

# 导入镜像
docker load -i threat-detection-ui.tar
```

## API配置

### 开发环境
- API Base URL: `http://localhost:8888`
- 直接访问API Gateway

### 生产环境
- API Base URL: `/api`
- 通过Nginx反向代理到API Gateway

### API端点示例

```typescript
// 威胁评估列表
GET /api/v1/threat-assessments?page=0&size=10

// 告警列表
GET /api/v1/alerts?page=0&size=10

// 客户列表
GET /api/v1/customers?page=0&size=10

// 系统监控
GET /api/v1/logs/stats
```

## 环境变量

| 变量 | 开发环境 | 生产环境 | 说明 |
|------|---------|---------|------|
| `VITE_API_BASE_URL` | `http://localhost:8888` | `/api` | API基础URL |
| `VITE_APP_TITLE` | 威胁检测系统(开发) | 威胁检测系统 | 应用标题 |
| `VITE_APP_VERSION` | 1.0.0-dev | 1.0.0 | 应用版本 |

## 性能优化

### Docker优化
- ✅ 多阶段构建 (减少镜像大小)
- ✅ 依赖缓存层 (加速构建)
- ✅ Alpine Linux基础镜像
- ✅ 生产构建使用Nginx

### 前端优化
- ✅ Vite快速热更新
- ✅ 代码分割 (React.lazy)
- ✅ Tree Shaking
- ✅ Gzip压缩
- ✅ 静态资源CDN缓存

## 故障排查

### 容器无法启动

```bash
# 查看容器日志
docker-compose logs frontend-dev

# 检查端口占用
lsof -i :3000

# 重新构建镜像
docker-compose build --no-cache frontend-dev
```

### 热更新不生效

```bash
# 确认Volume挂载
docker-compose config

# 重启容器
docker-compose restart frontend-dev
```

### API请求失败

```bash
# 检查网络连接
docker-compose exec frontend-dev ping api-gateway

# 检查Nginx配置
docker-compose exec frontend-prod nginx -t
```

## 开发指南

### 添加新页面

1. 在 `src/pages/` 创建页面组件
2. 在路由配置中注册
3. 添加菜单项 (如需要)

### 调用API

```typescript
import { getThreatAssessments } from '@/services/threat';

const data = await getThreatAssessments({ page: 0, size: 10 });
```

### 使用Pro Components

```typescript
import { ProTable } from '@ant-design/pro-components';

<ProTable
  columns={columns}
  request={async (params) => {
    const { data } = await getThreatAssessments(params);
    return { data, success: true };
  }}
/>
```

## 部署

### Docker Swarm

```bash
docker stack deploy -c docker-compose.yml threat-detection
```

### Kubernetes

```bash
kubectl apply -f k8s/frontend-deployment.yaml
```

## License

Proprietary - © 2025 Threat Detection System

## 联系方式

- **项目**: threat-detection-system
- **文档**: `/docs/api/`
- **后端**: Spring Boot 3.1.5 + Kafka + Flink
