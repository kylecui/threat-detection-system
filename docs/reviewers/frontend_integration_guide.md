# 云原生威胁检测系统 - 前端集成指南

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1  
**前端版本**: React 18 + TypeScript

---

## 目录

1. [概述](#1-概述)
2. [技术架构](#2-技术架构)
3. [快速开始](#3-快速开始)
4. [API集成](#4-api集成)
5. [组件架构](#5-组件架构)
6. [开发环境](#6-开发环境)
7. [部署配置](#7-部署配置)
8. [故障排查](#8-故障排查)

---

## 1. 概述

### 1.1 前端定位

前端系统是云原生威胁检测系统的用户界面层，提供：

- **实时威胁监控**: 仪表盘展示威胁检测状态
- **告警管理**: 告警列表、确认、解决操作
- **威胁评估**: 威胁详情查看和分析
- **客户管理**: 多租户客户数据管理
- **系统监控**: 后端服务健康状态监控

### 1.2 核心特性

- **响应式设计**: 支持桌面和移动设备
- **实时更新**: WebSocket实时告警推送
- **多租户支持**: 按客户隔离数据展示
- **权限控制**: 基于角色的访问控制
- **国际化**: 支持多语言界面

### 1.3 技术栈对比

| 组件 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| **框架** | React | 18.2.0 | 函数组件 + Hooks |
| **语言** | TypeScript | 5.2.2 | 类型安全 |
| **UI库** | Ant Design | 5.12.0 | 企业级设计系统 |
| **Pro组件** | Ant Design Pro | 2.6.0 | 高级业务组件 |
| **构建工具** | Vite | 5.0.0 | 快速开发构建 |
| **HTTP客户端** | Axios | 1.6.0 | RESTful API调用 |
| **路由** | React Router | 6.20.0 | 客户端路由 |
| **图表** | AntV G2 | 5.1.0 | 数据可视化 |
| **状态管理** | Zustand | 4.4.0 | 轻量级状态管理 |

---

## 2. 技术架构

### 2.1 架构图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web Browser   │ => │   Nginx (Prod)  │ => │   API Gateway   │
│                 │    │                 │    │   (Spring)      │
│ • React SPA     │    │ • 静态资源服务  │    │ • 路由转发      │
│ • TypeScript    │    │ • Gzip压缩      │    │ • 负载均衡      │
│ • Ant Design    │    │ • 缓存策略      │    │ • 认证授权      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Development   │    │   Production     │    │   Backend       │
│   Server        │    │   Container      │    │   Services      │
│   (Vite)        │    │   (Docker)       │    │                 │
│ • 热更新        │    │ • 多阶段构建     │    │ • Threat Assess │
│ • 开发调试      │    │ • Nginx服务      │    │ • Alert Mgmt    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 2.2 数据流

```typescript
// 1. 用户操作触发
User Action → React Component → Service Layer

// 2. API调用
Service → Axios → API Gateway → Backend Service

// 3. 数据返回
Backend → API Gateway → Axios → Component State

// 4. UI更新
State Change → React Re-render → UI Update
```

### 2.3 组件层次

```
App (根组件)
├── Layout (页面布局)
│   ├── Header (顶部导航)
│   ├── Sider (侧边菜单)
│   └── Content (主内容区)
│       ├── Dashboard (仪表盘)
│       ├── ThreatList (威胁列表)
│       ├── AlertCenter (告警中心)
│       ├── CustomerMgmt (客户管理)
│       └── SystemMonitor (系统监控)
└── Global Components (全局组件)
    ├── Loading (加载状态)
    ├── ErrorBoundary (错误边界)
    └── Notification (消息通知)
```

---

## 3. 快速开始

### 3.1 环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| **Node.js** | 18.17.0+ | JavaScript运行时 |
| **npm** | 9.0.0+ | 包管理器 |
| **Docker** | 20.10+ | 容器化部署 |
| **浏览器** | Chrome 90+ | 现代浏览器 |

### 3.2 Docker快速启动 (推荐)

```bash
# 进入前端目录
cd frontend

# 快速启动开发环境
./quick-start.sh

# 访问应用
# http://localhost:3000
```

**启动脚本执行流程**:
```bash
#!/bin/bash
echo "🚀 Starting Threat Detection Frontend..."

# 1. 检查API Gateway
echo "📡 Checking API Gateway..."
curl -s http://localhost:8888/actuator/health > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ API Gateway not available. Please start backend services first."
    exit 1
fi

# 2. 启动前端容器
echo "🐳 Starting frontend container..."
docker-compose up -d frontend-dev

# 3. 等待服务就绪
echo "⏳ Waiting for frontend to be ready..."
sleep 10

# 4. 验证服务
echo "✅ Frontend started successfully!"
echo "🌐 Access at: http://localhost:3000"
```

### 3.3 本地开发启动

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器
npm run dev

# 3. 访问应用
# http://localhost:5173 (Vite默认端口)
```

### 3.4 生产环境部署

```bash
# 1. 构建生产镜像
docker-compose build frontend-prod

# 2. 启动生产容器
docker-compose up -d frontend-prod

# 3. 验证部署
curl http://localhost
```

---

## 4. API集成

### 4.1 API配置

#### 环境变量配置

```typescript
// src/config/env.ts
export const API_CONFIG = {
  development: {
    baseURL: 'http://localhost:8888',
    timeout: 10000,
  },
  production: {
    baseURL: '/api',  // 通过Nginx代理
    timeout: 15000,
  },
};
```

#### Axios实例配置

```typescript
// src/utils/request.ts
import axios from 'axios';
import { API_CONFIG } from '@/config/env';

const request = axios.create({
  baseURL: API_CONFIG[import.meta.env.MODE].baseURL,
  timeout: API_CONFIG[import.meta.env.MODE].timeout,
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 添加认证头
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 添加客户ID
    const customerId = localStorage.getItem('customerId');
    if (customerId) {
      config.headers['X-Customer-ID'] = customerId;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // 统一错误处理
    if (error.response?.status === 401) {
      // 重定向到登录页
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default request;
```

### 4.2 服务层封装

#### 威胁评估服务

```typescript
// src/services/threat.ts
import request from '@/utils/request';

export interface ThreatAssessment {
  id: number;
  customerId: string;
  attackMac: string;
  threatScore: number;
  threatLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  attackCount: number;
  uniqueIps: number;
  uniquePorts: number;
  assessmentTime: string;
}

export interface ThreatQuery {
  customerId?: string;
  threatLevel?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export const getThreatAssessments = (params: ThreatQuery) => {
  return request.get<{
    data: ThreatAssessment[];
    total: number;
    page: number;
    size: number;
  }>('/api/v1/threat-assessments', { params });
};

export const getThreatAssessment = (id: number) => {
  return request.get<ThreatAssessment>(`/api/v1/threat-assessments/${id}`);
};
```

#### 告警管理服务

```typescript
// src/services/alert.ts
import request from '@/utils/request';

export interface Alert {
  id: number;
  customerId: string;
  threatId: number;
  threatLevel: string;
  status: 'NEW' | 'NOTIFIED' | 'ACKNOWLEDGED' | 'RESOLVED';
  title: string;
  description: string;
  attackMac: string;
  threatScore: number;
  createdAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
}

export const getAlerts = (params: {
  customerId: string;
  status?: string;
  threatLevel?: string;
  page?: number;
  size?: number;
}) => {
  return request.get<{
    data: Alert[];
    total: number;
  }>('/api/v1/alerts', { params });
};

export const acknowledgeAlert = (id: number, data: {
  acknowledgedBy: string;
  comment?: string;
}) => {
  return request.post(`/api/v1/alerts/${id}/acknowledge`, data);
};

export const resolveAlert = (id: number, data: {
  resolvedBy: string;
  resolution: string;
  comment?: string;
}) => {
  return request.post(`/api/v1/alerts/${id}/resolve`, data);
};
```

### 4.3 实时数据集成

#### WebSocket连接

```typescript
// src/hooks/useWebSocket.ts
import { useEffect, useRef } from 'react';

export const useWebSocket = (url: string, onMessage: (data: any) => void) => {
  const ws = useRef<WebSocket | null>(null);

  useEffect(() => {
    // 创建WebSocket连接
    ws.current = new WebSocket(url);

    ws.current.onopen = () => {
      console.log('WebSocket connected');
    };

    ws.current.onmessage = (event) => {
      const data = JSON.parse(event.data);
      onMessage(data);
    };

    ws.current.onclose = () => {
      console.log('WebSocket disconnected');
      // 自动重连逻辑
      setTimeout(() => {
        // 重新连接
      }, 5000);
    };

    ws.current.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    return () => {
      ws.current?.close();
    };
  }, [url, onMessage]);

  return ws.current;
};
```

#### 实时告警监听

```typescript
// src/pages/AlertCenter/index.tsx
import { useWebSocket } from '@/hooks/useWebSocket';
import { notification } from 'antd';

const AlertCenter = () => {
  // 实时告警监听
  useWebSocket('ws://localhost:8888/ws/alerts', (data) => {
    if (data.type === 'NEW_ALERT') {
      notification.warning({
        message: '新告警',
        description: data.alert.title,
        duration: 0,
      });

      // 更新告警列表
      refetchAlerts();
    }
  });

  // ... 其他组件逻辑
};
```

---

## 5. 组件架构

### 5.1 页面组件结构

#### 仪表盘页面

```typescript
// src/pages/Dashboard/index.tsx
import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Progress } from 'antd';
import { getThreatStats, getAlertStats } from '@/services/dashboard';

const Dashboard = () => {
  const [stats, setStats] = useState({
    totalThreats: 0,
    activeAlerts: 0,
    criticalAlerts: 0,
    threatTrend: [],
  });

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      const [threatStats, alertStats] = await Promise.all([
        getThreatStats(),
        getAlertStats(),
      ]);

      setStats({
        totalThreats: threatStats.total,
        activeAlerts: alertStats.active,
        criticalAlerts: alertStats.critical,
        threatTrend: threatStats.trend,
      });
    } catch (error) {
      console.error('Failed to load dashboard stats:', error);
    }
  };

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总威胁数"
              value={stats.totalThreats}
              prefix={<AlertOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="活跃告警"
              value={stats.activeAlerts}
              valueStyle={{ color: '#cf1322' }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="严重告警"
              value={stats.criticalAlerts}
              valueStyle={{ color: '#ff4d4f' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Progress
              type="circle"
              percent={75}
              format={() => '系统正常'}
            />
          </Card>
        </Col>
      </Row>

      {/* 威胁趋势图表 */}
      <Card title="威胁趋势" style={{ marginTop: 16 }}>
        <LineChart data={stats.threatTrend} />
      </Card>
    </div>
  );
};

export default Dashboard;
```

#### 威胁列表页面

```typescript
// src/pages/ThreatList/index.tsx
import { useState, useEffect } from 'react';
import { Table, Tag, Button, Space } from 'antd';
import { getThreatAssessments } from '@/services/threat';
import type { ThreatAssessment } from '@/services/threat';

const ThreatList = () => {
  const [data, setData] = useState<ThreatAssessment[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const loadData = async (page = 1, pageSize = 10) => {
    setLoading(true);
    try {
      const response = await getThreatAssessments({
        page: page - 1,
        size: pageSize,
      });

      setData(response.data);
      setPagination({
        ...pagination,
        current: page,
        pageSize,
        total: response.total,
      });
    } catch (error) {
      console.error('Failed to load threats:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const columns = [
    {
      title: '攻击MAC',
      dataIndex: 'attackMac',
      key: 'attackMac',
    },
    {
      title: '威胁等级',
      dataIndex: 'threatLevel',
      key: 'threatLevel',
      render: (level: string) => {
        const colorMap = {
          CRITICAL: 'red',
          HIGH: 'orange',
          MEDIUM: 'yellow',
          LOW: 'blue',
          INFO: 'green',
        };
        return <Tag color={colorMap[level as keyof typeof colorMap]}>{level}</Tag>;
      },
    },
    {
      title: '威胁分数',
      dataIndex: 'threatScore',
      key: 'threatScore',
      sorter: true,
    },
    {
      title: '攻击次数',
      dataIndex: 'attackCount',
      key: 'attackCount',
    },
    {
      title: '评估时间',
      dataIndex: 'assessmentTime',
      key: 'assessmentTime',
      render: (time: string) => new Date(time).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ThreatAssessment) => (
        <Space size="middle">
          <Button type="link" onClick={() => handleViewDetail(record)}>
            查看详情
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={data}
      rowKey="id"
      loading={loading}
      pagination={pagination}
      onChange={(pagination) => {
        loadData(pagination.current, pagination.pageSize);
      }}
    />
  );
};

export default ThreatList;
```

### 5.2 自定义Hooks

#### 数据获取Hook

```typescript
// src/hooks/useApi.ts
import { useState, useEffect } from 'react';

export const useApi = <T>(
  apiCall: () => Promise<T>,
  deps: any[] = []
) => {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const result = await apiCall();
        setData(result);
        setError(null);
      } catch (err) {
        setError(err as Error);
        setData(null);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, deps);

  return { data, loading, error, refetch: () => fetchData() };
};
```

#### 表格查询Hook

```typescript
// src/hooks/useTable.ts
import { useState, useCallback } from 'react';

export const useTable = <T>(
  fetchData: (params: any) => Promise<{ data: T[]; total: number }>
) => {
  const [data, setData] = useState<T[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const loadData = useCallback(async (params = {}) => {
    setLoading(true);
    try {
      const response = await fetchData({
        page: pagination.current - 1,
        size: pagination.pageSize,
        ...params,
      });

      setData(response.data);
      setPagination(prev => ({
        ...prev,
        total: response.total,
      }));
    } catch (error) {
      console.error('Failed to load table data:', error);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, fetchData]);

  const handleTableChange = useCallback((newPagination: any) => {
    setPagination(prev => ({
      ...prev,
      current: newPagination.current,
      pageSize: newPagination.pageSize,
    }));
  }, []);

  return {
    data,
    loading,
    pagination,
    loadData,
    handleTableChange,
  };
};
```

### 5.3 工具函数

#### 日期格式化

```typescript
// src/utils/date.ts
export const formatDateTime = (date: string | Date) => {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(date));
};

export const formatRelativeTime = (date: string | Date) => {
  const now = new Date();
  const target = new Date(date);
  const diff = now.getTime() - target.getTime();

  const minutes = Math.floor(diff / (1000 * 60));
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  return `${days}天前`;
};
```

#### 威胁等级工具

```typescript
// src/utils/threat.ts
export const THREAT_LEVELS = {
  CRITICAL: { label: '严重', color: 'red', priority: 5 },
  HIGH: { label: '高危', color: 'orange', priority: 4 },
  MEDIUM: { label: '中危', color: 'yellow', priority: 3 },
  LOW: { label: '低危', color: 'blue', priority: 2 },
  INFO: { label: '信息', color: 'green', priority: 1 },
} as const;

export const getThreatLevelInfo = (level: keyof typeof THREAT_LEVELS) => {
  return THREAT_LEVELS[level];
};

export const formatThreatScore = (score: number) => {
  if (score >= 200) return 'CRITICAL';
  if (score >= 100) return 'HIGH';
  if (score >= 50) return 'MEDIUM';
  if (score >= 10) return 'LOW';
  return 'INFO';
};
```

---

## 6. 开发环境

### 6.1 项目结构

```
frontend/
├── src/
│   ├── components/         # 通用组件
│   │   ├── common/         # 基础组件
│   │   ├── layout/         # 布局组件
│   │   └── business/       # 业务组件
│   ├── pages/              # 页面组件
│   │   ├── Dashboard/      # 仪表盘
│   │   ├── ThreatList/     # 威胁列表
│   │   ├── AlertCenter/    # 告警中心
│   │   ├── CustomerMgmt/   # 客户管理
│   │   └── SystemMonitor/  # 系统监控
│   ├── services/           # API服务层
│   │   ├── threat.ts       # 威胁评估API
│   │   ├── alert.ts        # 告警管理API
│   │   ├── customer.ts     # 客户管理API
│   │   └── system.ts       # 系统监控API
│   ├── hooks/              # 自定义Hooks
│   │   ├── useApi.ts       # API调用Hook
│   │   ├── useTable.ts     # 表格Hook
│   │   └── useWebSocket.ts # WebSocket Hook
│   ├── utils/              # 工具函数
│   │   ├── request.ts      # HTTP请求封装
│   │   ├── date.ts         # 日期处理
│   │   ├── threat.ts       # 威胁相关工具
│   │   └── index.ts        # 工具导出
│   ├── types/              # TypeScript类型
│   │   ├── api.ts          # API响应类型
│   │   ├── component.ts    # 组件Props类型
│   │   └── index.ts        # 类型导出
│   ├── config/             # 配置文件
│   │   ├── env.ts          # 环境配置
│   │   └── routes.ts       # 路由配置
│   ├── App.tsx             # 根组件
│   ├── main.tsx            # 应用入口
│   └── index.css           # 全局样式
├── public/                 # 静态资源
├── Dockerfile              # Docker构建文件
├── docker-compose.yml      # Docker配置
├── vite.config.ts          # Vite配置
├── tsconfig.json           # TypeScript配置
└── package.json            # 项目配置
```

### 6.2 开发工作流

#### 1. 创建新页面

```bash
# 1. 创建页面组件
mkdir -p src/pages/NewPage
touch src/pages/NewPage/index.tsx

# 2. 添加路由配置
# 编辑 src/config/routes.ts

# 3. 添加菜单项 (如需要)
# 编辑 src/components/layout/SiderMenu.tsx
```

#### 2. 添加API服务

```typescript
// src/services/newService.ts
import request from '@/utils/request';

export const getNewData = (params: any) => {
  return request.get('/api/v1/new-endpoint', { params });
};
```

#### 3. 创建组件

```typescript
// src/components/business/NewComponent.tsx
import React from 'react';
import { Card, Table } from 'antd';
import { useApi } from '@/hooks/useApi';
import { getNewData } from '@/services/newService';

interface NewComponentProps {
  customerId: string;
}

const NewComponent: React.FC<NewComponentProps> = ({ customerId }) => {
  const { data, loading, error } = useApi(() =>
    getNewData({ customerId })
  );

  if (error) {
    return <div>加载失败: {error.message}</div>;
  }

  return (
    <Card title="新组件" loading={loading}>
      <Table
        dataSource={data?.items || []}
        columns={columns}
        rowKey="id"
      />
    </Card>
  );
};

export default NewComponent;
```

### 6.3 代码规范

#### TypeScript配置

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

#### ESLint配置

```json
// .eslintrc.json
{
  "env": {
    "browser": true,
    "es2021": true
  },
  "extends": [
    "eslint:recommended",
    "@typescript-eslint/recommended",
    "plugin:react/recommended",
    "plugin:react/jsx-runtime",
    "plugin:react-hooks/recommended"
  ],
  "parser": "@typescript-eslint/parser",
  "parserOptions": {
    "ecmaFeatures": {
      "jsx": true
    },
    "ecmaVersion": "latest",
    "sourceType": "module"
  },
  "plugins": ["react", "react-hooks", "@typescript-eslint"],
  "rules": {
    "react/prop-types": "off",
    "@typescript-eslint/no-unused-vars": ["error", { "argsIgnorePattern": "^_" }]
  }
}
```

---

## 7. 部署配置

### 7.1 Docker配置

#### 多阶段构建Dockerfile

```dockerfile
# Dockerfile
# 构建阶段
FROM node:18-alpine as builder

WORKDIR /app

# 复制package文件
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 生产阶段
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置
COPY nginx.conf /etc/nginx/nginx.conf
COPY default.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
```

#### Nginx配置

```nginx
# nginx.conf
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    # 日志格式
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    # 基本设置
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;

    # Gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/json
        application/javascript
        application/xml+rss
        application/atom+xml
        image/svg+xml;

    # 包含站点配置
    include /etc/nginx/conf.d/*.conf;
}
```

```nginx
# default.conf
server {
    listen 80;
    server_name localhost;

    # 根目录
    root /usr/share/nginx/html;
    index index.html index.htm;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API代理
    location /api/ {
        proxy_pass http://api-gateway:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # React Router支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
}
```

### 7.2 环境变量

#### 开发环境

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8888
VITE_APP_TITLE=威胁检测系统(开发)
VITE_APP_VERSION=1.0.0-dev
VITE_WS_URL=ws://localhost:8888/ws
```

#### 生产环境

```bash
# .env.production
VITE_API_BASE_URL=/api
VITE_APP_TITLE=威胁检测系统
VITE_APP_VERSION=1.0.0
VITE_WS_URL=ws://localhost/api/ws
```

### 7.3 CI/CD配置

#### GitHub Actions

```yaml
# .github/workflows/deploy.yml
name: Deploy Frontend

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'npm'

    - name: Install dependencies
      run: npm ci

    - name: Run tests
      run: npm run test

    - name: Build
      run: npm run build

    - name: Build Docker image
      run: |
        docker build -t threat-detection-ui:${{ github.sha }} .
        docker tag threat-detection-ui:${{ github.sha }} threat-detection-ui:latest

    - name: Deploy to staging
      if: github.ref == 'refs/heads/main'
      run: |
        echo "Deploy to staging environment"
        # Add deployment commands here
```

---

## 8. 故障排查

### 8.1 常见问题

#### API请求失败

**问题**: 前端无法连接后端API

**检查步骤**:
```bash
# 1. 检查API Gateway状态
curl http://localhost:8888/actuator/health

# 2. 检查网络连接
docker-compose exec frontend-dev ping api-gateway

# 3. 检查环境变量
docker-compose exec frontend-dev env | grep VITE_API

# 4. 查看浏览器开发者工具网络面板
# 检查请求URL和响应状态
```

**解决方案**:
```bash
# 重启API Gateway
docker-compose restart api-gateway

# 检查端口映射
docker-compose ps

# 重新构建前端容器
docker-compose up --build frontend-dev
```

#### 热更新不生效

**问题**: 修改代码后页面不自动刷新

**检查步骤**:
```bash
# 1. 检查Volume挂载
docker-compose config | grep -A 10 frontend-dev

# 2. 检查文件权限
docker-compose exec frontend-dev ls -la /app/src

# 3. 查看Vite日志
docker-compose logs frontend-dev
```

**解决方案**:
```bash
# 重启开发容器
docker-compose restart frontend-dev

# 清理node_modules
docker-compose exec frontend-dev rm -rf node_modules package-lock.json
docker-compose exec frontend-dev npm install
```

#### 构建失败

**问题**: Docker构建过程中断

**检查步骤**:
```bash
# 1. 检查Docker磁盘空间
df -h

# 2. 查看构建日志
docker-compose build --no-cache frontend-prod

# 3. 检查网络连接
curl https://registry.npmjs.org/
```

**解决方案**:
```bash
# 清理Docker缓存
docker system prune -a

# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 增加构建内存
export NODE_OPTIONS="--max-old-space-size=4096"
```

#### WebSocket连接失败

**问题**: 实时功能不工作

**检查步骤**:
```bash
# 1. 检查WebSocket端点
curl -I http://localhost:8888/ws/alerts

# 2. 查看浏览器控制台错误
# 检查WebSocket连接状态

# 3. 检查防火墙设置
sudo ufw status
```

**解决方案**:
```yaml
# 更新nginx配置支持WebSocket
location /ws/ {
    proxy_pass http://api-gateway:8080/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 86400;
}
```

### 8.2 性能优化

#### 监控指标

```typescript
// src/utils/performance.ts
export const measurePerformance = (name: string, fn: () => void) => {
  const start = performance.now();
  fn();
  const end = performance.now();
  console.log(`${name} took ${end - start} milliseconds`);
};

export const measureApiCall = async (name: string, apiCall: () => Promise<any>) => {
  const start = performance.now();
  try {
    const result = await apiCall();
    const end = performance.now();
    console.log(`${name} API call took ${end - start} milliseconds`);
    return result;
  } catch (error) {
    const end = performance.now();
    console.error(`${name} API call failed after ${end - start} milliseconds:`, error);
    throw error;
  }
};
```

#### 内存泄漏检查

```typescript
// src/hooks/useMemoryMonitor.ts
import { useEffect } from 'react';

export const useMemoryMonitor = () => {
  useEffect(() => {
    const interval = setInterval(() => {
      if ('memory' in performance) {
        const memory = (performance as any).memory;
        console.log('Memory usage:', {
          used: Math.round(memory.usedJSHeapSize / 1048576 * 100) / 100 + ' MB',
          total: Math.round(memory.totalJSHeapSize / 1048576 * 100) / 100 + ' MB',
          limit: Math.round(memory.jsHeapSizeLimit / 1048576 * 100) / 100 + ' MB',
        });
      }
    }, 10000);

    return () => clearInterval(interval);
  }, []);
};
```

### 8.3 日志配置

#### 客户端日志

```typescript
// src/utils/logger.ts
type LogLevel = 'debug' | 'info' | 'warn' | 'error';

class Logger {
  private level: LogLevel = 'info';

  setLevel(level: LogLevel) {
    this.level = level;
  }

  debug(message: string, ...args: any[]) {
    if (this.shouldLog('debug')) {
      console.debug(`[DEBUG] ${message}`, ...args);
    }
  }

  info(message: string, ...args: any[]) {
    if (this.shouldLog('info')) {
      console.info(`[INFO] ${message}`, ...args);
    }
  }

  warn(message: string, ...args: any[]) {
    if (this.shouldLog('warn')) {
      console.warn(`[WARN] ${message}`, ...args);
    }
  }

  error(message: string, ...args: any[]) {
    if (this.shouldLog('error')) {
      console.error(`[ERROR] ${message}`, ...args);
    }
  }

  private shouldLog(level: LogLevel): boolean {
    const levels = ['debug', 'info', 'warn', 'error'];
    return levels.indexOf(level) >= levels.indexOf(this.level);
  }
}

export const logger = new Logger();
```

---

**文档结束**