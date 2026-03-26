# 前端集成指南

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**前端版本**: v2.1

---

## 目录

1. [概述](#1-概述)
2. [技术栈](#2-技术栈)
3. [快速开始](#3-快速开始)
4. [项目结构](#4-项目结构)
5. [API集成](#5-api集成)
6. [Docker部署](#6-docker部署)
7. [开发指南](#7-开发指南)
8. [性能优化](#8-性能优化)
9. [故障排查](#9-故障排查)

---

## 1. 概述

### 1.1 前端定位

威胁检测系统前端是整个系统的用户界面层，提供：

- **威胁检测仪表盘**: 实时威胁可视化
- **威胁评估管理**: 威胁详情查看和处理
- **告警中心**: 多通道告警管理和通知
- **客户管理**: 多租户客户信息管理
- **系统监控**: 实时系统状态监控

### 1.2 架构关系

```
用户浏览器 → 前端 (React + TypeScript) → API Gateway → 后端微服务
                                      ↓
                               PostgreSQL + Kafka
```

### 1.3 核心特性

- **响应式设计**: 支持桌面和移动设备
- **实时更新**: WebSocket支持实时数据推送
- **多租户支持**: 基于客户ID的数据隔离
- **权限管理**: 基于角色的访问控制
- **国际化**: 支持多语言界面

---

## 2. 技术栈

### 2.1 核心框架

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **前端框架** | React | 18.2+ | 函数式组件 + Hooks |
| **开发语言** | TypeScript | 5.0+ | 类型安全 |
| **构建工具** | Vite | 5.0+ | 快速构建和热更新 |
| **UI组件库** | Ant Design | 5.0+ | 企业级UI组件 |
| **高级组件** | Ant Design Pro | 2.0+ | 业务组件 (表格、表单等) |
| **HTTP客户端** | Axios | 1.0+ | RESTful API调用 |
| **路由管理** | React Router | 6.0+ | 客户端路由 |
| **状态管理** | Zustand | 4.0+ | 轻量级状态管理 |
| **图表库** | AntV G2 | 5.0+ | 数据可视化 |

### 2.2 开发工具

- **代码检查**: ESLint + Prettier
- **测试框架**: Vitest + React Testing Library
- **类型检查**: TypeScript Compiler
- **包管理**: npm/yarn/pnpm

### 2.3 部署技术

- **容器化**: Docker (多阶段构建)
- **Web服务器**: Nginx (生产环境)
- **CDN支持**: 静态资源优化
- **HTTPS**: SSL证书支持

---

## 3. 快速开始

### 3.1 前置条件

**重要**: 前端需要连接API Gateway，请确保后端服务已启动：

```bash
# 检查API Gateway状态
curl http://localhost:8082/actuator/health

# 如果未启动，启动所有服务
cd ../docker
docker-compose up -d
```

### 3.2 Docker开发环境 (推荐)

```bash
cd frontend

# 使用快速启动脚本
./quick-start.sh

# 或直接启动
docker-compose up frontend-dev

# 访问应用
open http://localhost:3000
```

**开发环境特性**:
- ✅ 源码挂载，支持热更新
- ✅ 自动API代理配置
- ✅ 开发工具集成
- ✅ 100%隔离的开发环境

### 3.3 Docker生产环境

```bash
cd frontend

# 构建并启动生产版本
docker-compose up -d frontend-prod

# 访问应用
open http://localhost
```

**生产环境特性**:
- ✅ 多阶段构建优化
- ✅ Nginx高性能服务
- ✅ Gzip压缩
- ✅ 静态资源缓存

### 3.4 本地开发 (可选)

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

---

## 4. 项目结构

```
frontend/
├── src/
│   ├── pages/                    # 页面组件
│   │   ├── Dashboard/           # 威胁检测仪表盘
│   │   │   ├── index.tsx        # 仪表盘主页
│   │   │   ├── components/      # 仪表盘子组件
│   │   │   └── types.ts         # 页面类型定义
│   │   ├── ThreatList/          # 威胁评估列表
│   │   ├── AlertCenter/         # 告警中心
│   │   ├── CustomerMgmt/        # 客户管理
│   │   └── SystemMonitor/       # 系统监控
│   ├── components/              # 通用组件
│   │   ├── common/             # 基础通用组件
│   │   ├── layout/             # 布局组件
│   │   └── business/           # 业务组件
│   ├── services/               # API服务层
│   │   ├── api/                # API调用封装
│   │   ├── types/              # API类型定义
│   │   └── config.ts           # API配置
│   ├── utils/                  # 工具函数
│   │   ├── format.ts           # 格式化工具
│   │   ├── validation.ts       # 验证工具
│   │   └── constants.ts        # 常量定义
│   ├── hooks/                  # 自定义Hooks
│   │   ├── useThreatData.ts    # 威胁数据Hook
│   │   ├── useAlertData.ts     # 告警数据Hook
│   │   └── useCustomerData.ts  # 客户数据Hook
│   ├── types/                  # 全局类型定义
│   │   ├── threat.ts           # 威胁相关类型
│   │   ├── alert.ts            # 告警相关类型
│   │   ├── customer.ts         # 客户相关类型
│   │   └── common.ts           # 通用类型
│   ├── App.tsx                 # 根组件
│   ├── main.tsx                # 应用入口
│   └── router.tsx              # 路由配置
├── public/                     # 静态资源
│   ├── favicon.ico
│   └── assets/
├── Dockerfile                  # 多阶段构建配置
├── docker-compose.yml          # Docker配置
├── nginx.conf                  # Nginx配置
├── vite.config.ts              # Vite配置
├── tsconfig.json               # TypeScript配置
├── package.json                # 项目依赖
└── README.md                   # 项目文档
```

### 4.1 关键文件说明

#### 4.1.1 API服务层 (services/)

```typescript
// services/api/threat.ts
export const getThreatAssessments = async (params: ThreatQueryParams) => {
  const response = await apiClient.get('/assessment/threats', { params });
  return response.data;
};

// services/types/threat.ts
export interface ThreatAssessment {
  id: number;
  customerId: string;
  attackMac: string;
  threatScore: number;
  threatLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  attackCount: number;
  uniqueIps: number;
  uniquePorts: number;
  uniqueDevices: number;
  assessmentTime: string;
}
```

#### 4.1.2 页面组件 (pages/)

```typescript
// pages/ThreatList/index.tsx
import { ProTable } from '@ant-design/pro-components';
import { getThreatAssessments } from '@/services/api/threat';

const ThreatList = () => {
  return (
    <ProTable
      columns={threatColumns}
      request={async (params) => {
        const { data } = await getThreatAssessments(params);
        return { data, success: true };
      }}
    />
  );
};
```

#### 4.1.3 自定义Hooks (hooks/)

```typescript
// hooks/useThreatData.ts
import { useState, useEffect } from 'react';
import { getThreatAssessments } from '@/services/api/threat';

export const useThreatData = (customerId: string) => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const result = await getThreatAssessments({ customerId });
      setData(result.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [customerId]);

  return { data, loading, refetch: fetchData };
};
```

---

## 5. API集成

### 5.1 API配置

#### 5.1.1 开发环境

```typescript
// src/services/config.ts
export const API_CONFIG = {
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8082',
  timeout: 10000,
};
```

#### 5.1.2 生产环境

通过Nginx反向代理：

```nginx
# nginx.conf
location /api/ {
    proxy_pass http://api-gateway:8082/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

### 5.2 API客户端

```typescript
// src/services/api/client.ts
import axios from 'axios';
import { API_CONFIG } from '../config';

export const apiClient = axios.create({
  baseURL: API_CONFIG.baseURL,
  timeout: API_CONFIG.timeout,
});

// 请求拦截器 - 添加认证头
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器 - 统一错误处理
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 处理认证失败
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### 5.3 API服务封装

#### 5.3.1 威胁评估API

```typescript
// src/services/api/threat.ts
import { apiClient } from './client';

export interface ThreatQueryParams {
  customerId: string;
  threatLevel?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export const getThreatAssessments = (params: ThreatQueryParams) => {
  return apiClient.get('/api/v1/assessment/threats', { params });
};

export const getThreatDetail = (id: number) => {
  return apiClient.get(`/api/v1/assessment/threats/${id}`);
};

export const getThreatStats = (params: { customerId: string; startTime?: string }) => {
  return apiClient.get('/api/v1/assessment/stats', { params });
};
```

#### 5.3.2 告警管理API

```typescript
// src/services/api/alert.ts
import { apiClient } from './client';

export const getAlerts = (params: AlertQueryParams) => {
  return apiClient.get('/api/v1/alerts', { params });
};

export const acknowledgeAlert = (alertId: number) => {
  return apiClient.post(`/api/v1/alerts/${alertId}/acknowledge`);
};

export const sendNotification = (data: NotificationRequest) => {
  return apiClient.post('/api/v1/alerts/notify', data);
};
```

### 5.4 实时数据集成

#### 5.4.1 WebSocket连接

```typescript
// src/services/websocket.ts
import { io } from 'socket.io-client';

class WebSocketService {
  private socket: any;

  connect(customerId: string) {
    this.socket = io(API_CONFIG.baseURL, {
      query: { customerId },
    });

    this.socket.on('threat-update', (data) => {
      // 处理实时威胁更新
      eventEmitter.emit('threatUpdate', data);
    });

    this.socket.on('alert-new', (data) => {
      // 处理新告警
      eventEmitter.emit('newAlert', data);
    });
  }

  disconnect() {
    if (this.socket) {
      this.socket.disconnect();
    }
  }
}

export const wsService = new WebSocketService();
```

#### 5.4.2 轮询机制

```typescript
// src/hooks/useRealTimeData.ts
import { useEffect, useState } from 'react';

export const useRealTimeData = <T>(
  fetchFn: () => Promise<T>,
  interval = 30000
) => {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const result = await fetchFn();
      setData(result);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(); // 初始加载

    const timer = setInterval(fetchData, interval);
    return () => clearInterval(timer);
  }, []);

  return { data, loading, refetch: fetchData };
};
```

---

## 6. Docker部署

### 6.1 多阶段构建

```dockerfile
# Dockerfile
# 构建阶段
FROM node:18-alpine as builder

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 6.2 Docker Compose配置

```yaml
# docker-compose.yml
version: '3.8'

services:
  frontend-dev:
    build:
      context: .
      dockerfile: Dockerfile.dev
    ports:
      - "3000:3000"
    volumes:
      - .:/app
      - /app/node_modules
    environment:
      - VITE_API_BASE_URL=http://localhost:8082
    networks:
      - threat-detection

  frontend-prod:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "80:80"
    environment:
      - NODE_ENV=production
    networks:
      - threat-detection
```

### 6.3 Nginx配置

```nginx
# nginx.conf
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    # Gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/xml+rss application/json;

    server {
        listen 80;
        server_name localhost;
        root /usr/share/nginx/html;
        index index.html;

        # API代理
        location /api/ {
            proxy_pass http://api-gateway:8082/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }

        # 静态资源缓存
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # SPA路由
        location / {
            try_files $uri $uri/ /index.html;
        }
    }
}
```

---

## 7. 开发指南

### 7.1 添加新页面

1. **创建页面组件**

```typescript
// src/pages/NewPage/index.tsx
import React from 'react';
import { PageContainer } from '@ant-design/pro-components';

const NewPage: React.FC = () => {
  return (
    <PageContainer title="新页面">
      <div>页面内容</div>
    </PageContainer>
  );
};

export default NewPage;
```

2. **添加路由配置**

```typescript
// src/router.tsx
import NewPage from '@/pages/NewPage';

const routes = [
  {
    path: '/new-page',
    name: '新页面',
    component: NewPage,
  },
];
```

3. **添加菜单项** (如需要)

```typescript
// src/components/layout/Menu.tsx
const menuItems = [
  {
    key: 'new-page',
    label: '新页面',
    icon: <Icon />,
  },
];
```

### 7.2 API集成模式

#### 7.2.1 使用ProTable

```typescript
import { ProTable } from '@ant-design/pro-components';

const columns = [
  {
    title: '威胁ID',
    dataIndex: 'id',
    key: 'id',
  },
  {
    title: '威胁等级',
    dataIndex: 'threatLevel',
    key: 'threatLevel',
    valueEnum: {
      CRITICAL: { text: '严重', status: 'error' },
      HIGH: { text: '高危', status: 'warning' },
      MEDIUM: { text: '中危', status: 'processing' },
      LOW: { text: '低危', status: 'success' },
      INFO: { text: '信息', status: 'default' },
    },
  },
];

<ProTable
  columns={columns}
  request={async (params) => {
    const { data } = await getThreatAssessments({
      ...params,
      customerId: currentCustomerId,
    });
    return {
      data: data.data,
      success: true,
      total: data.total,
    };
  }}
/>
```

#### 7.2.2 使用自定义Hook

```typescript
import { useThreatData } from '@/hooks/useThreatData';

const ThreatDashboard = () => {
  const { data, loading, refetch } = useThreatData(customerId);

  if (loading) return <Spin />;

  return (
    <div>
      {data.map(threat => (
        <ThreatCard key={threat.id} threat={threat} />
      ))}
    </div>
  );
};
```

### 7.3 图表集成

```typescript
import { Line } from '@ant-design/charts';

const ThreatTrendChart = ({ data }) => {
  const config = {
    data,
    xField: 'timestamp',
    yField: 'threatCount',
    seriesField: 'threatLevel',
    smooth: true,
  };

  return <Line {...config} />;
};
```

### 7.4 表单处理

```typescript
import { ProForm, ProFormText } from '@ant-design/pro-components';

const ThreatFilterForm = ({ onFilter }) => {
  return (
    <ProForm
      onFinish={onFilter}
      submitter={{
        searchConfig: {
          submitText: '查询',
          resetText: '重置',
        },
      }}
    >
      <ProFormText
        name="customerId"
        label="客户ID"
        placeholder="请输入客户ID"
      />
      <ProFormSelect
        name="threatLevel"
        label="威胁等级"
        options={[
          { label: '严重', value: 'CRITICAL' },
          { label: '高危', value: 'HIGH' },
          { label: '中危', value: 'MEDIUM' },
          { label: '低危', value: 'LOW' },
          { label: '信息', value: 'INFO' },
        ]}
      />
    </ProForm>
  );
};
```

---

## 8. 性能优化

### 8.1 构建优化

- **代码分割**: 使用React.lazy实现路由级代码分割
- **Tree Shaking**: 移除未使用的代码
- **压缩**: Gzip压缩传输
- **缓存**: 静态资源长期缓存

### 8.2 运行时优化

- **虚拟滚动**: 大列表使用虚拟滚动
- **防抖**: API请求防抖处理
- **内存泄漏**: 正确清理定时器和事件监听器
- **懒加载**: 图片和组件懒加载

### 8.3 Docker优化

```dockerfile
# 多阶段构建优化
FROM node:18-alpine as deps
# 依赖安装阶段 - 利用Docker层缓存

FROM node:18-alpine as builder
# 构建阶段 - 只包含构建结果

FROM nginx:alpine as production
# 生产阶段 - 只包含运行时文件
```

---

## 9. 故障排查

### 9.1 常见问题

#### 9.1.1 容器无法启动

```bash
# 查看容器日志
docker-compose logs frontend-dev

# 检查端口占用
lsof -i :3000

# 重新构建镜像
docker-compose build --no-cache frontend-dev
```

#### 9.1.2 API请求失败

```bash
# 检查网络连接
docker-compose exec frontend-dev curl http://api-gateway:8082/actuator/health

# 检查Nginx配置
docker-compose exec frontend-prod nginx -t

# 查看API Gateway日志
docker-compose logs api-gateway
```

#### 9.1.3 热更新不生效

```bash
# 确认源码挂载
docker inspect frontend-dev | grep -A 10 Mounts

# 重启开发容器
docker-compose restart frontend-dev
```

#### 9.1.4 构建失败

```bash
# 清理缓存
rm -rf node_modules/.vite
npm run build

# 检查TypeScript错误
npx tsc --noEmit

# 检查ESLint错误
npm run lint
```

### 9.2 调试技巧

#### 9.2.1 浏览器调试

- 使用React DevTools检查组件状态
- 使用Network面板查看API请求
- 使用Console查看错误日志

#### 9.2.2 容器调试

```bash
# 进入容器
docker-compose exec frontend-dev sh

# 查看运行进程
ps aux

# 检查环境变量
env | grep VITE

# 测试API连接
curl http://api-gateway:8082/api/v1/assessment/threats?customerId=test
```

#### 9.2.3 日志分析

```bash
# 查看应用日志
docker-compose logs -f frontend-dev

# 查看Nginx访问日志
docker-compose exec frontend-prod tail -f /var/log/nginx/access.log

# 查看Nginx错误日志
docker-compose exec frontend-prod tail -f /var/log/nginx/error.log
```

---

## 附录

### A. 环境变量配置

| 变量名 | 开发环境 | 生产环境 | 说明 |
|--------|----------|----------|------|
| `VITE_API_BASE_URL` | `http://localhost:8082` | `/api` | API基础URL |
| `VITE_APP_TITLE` | `威胁检测系统(开发)` | `威胁检测系统` | 应用标题 |
| `VITE_APP_VERSION` | `1.0.0-dev` | `1.0.0` | 应用版本 |
| `VITE_ENABLE_MOCK` | `false` | `false` | 是否启用Mock数据 |

### B. 构建命令

```json
// package.json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint src --ext .ts,.tsx",
    "lint:fix": "eslint src --ext .ts,.tsx --fix",
    "test": "vitest",
    "test:ui": "vitest --ui"
  }
}
```

### C. TypeScript配置

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "allowJs": false,
    "skipLibCheck": false,
    "esModuleInterop": false,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src"]
}
```

---

**文档结束**</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/reviewers/frontend_integration_guide.md