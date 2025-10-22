# 🐳 容器结构及API配置

**更新日期**: 2025-10-22
**容器化**: Docker + Docker Compose
**状态**: ✅ 生产就绪

---

## 🏗️ 容器架构概览

### 微服务架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Gateway   │    │  Data Ingestion │    │ Stream Processing│
│     (8888)      │◄──►│     (8080)      │◄──►│     (8081)       │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Alert Management│    │Threat Assessment│    │ Customer Mgmt   │
│     (8082)      │◄──►│     (8083)      │◄──►│     (8084)       │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure                           │
│  PostgreSQL (5432) • Kafka (9092) • Zookeeper (2181)       │
│  Redis (6379) • Logstash (9080/9081)                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 容器清单及端口配置

### 应用服务容器

| 服务名称 | 容器名称 | 内部端口 | 暴露端口 | 健康检查端点 | 依赖服务 |
|----------|----------|----------|----------|-------------|----------|
| **API Gateway** | api-gateway | 8080 | 8888 | `/actuator/health` | - |
| **Data Ingestion** | data-ingestion-service | 8080 | 8080 | `/api/v1/logs/health` | kafka, postgres |
| **Stream Processing** | stream-processing | 8081 | 8081 | - | kafka, postgres |
| **Threat Assessment** | threat-assessment-service | 8081 | 8083 | `/api/v1/assessment/health` | kafka, postgres |
| **Alert Management** | alert-management-service | 8084 | 8082 | `/actuator/health` | postgres |
| **Customer Management** | customer-management-service | 8084 | 8084 | `/actuator/health` | postgres |

### 基础设施容器

| 服务名称 | 容器名称 | 内部端口 | 暴露端口 | 健康检查 | 用途 |
|----------|----------|----------|----------|----------|------|
| **PostgreSQL** | postgres | 5432 | 5432 | `pg_isready` | 主数据库 |
| **Kafka** | kafka | 9092 | 9092 | - | 消息队列 |
| **Zookeeper** | zookeeper | 2181 | 2181 | - | Kafka协调器 |
| **Redis** | redis | 6379 | 6379 | - | 缓存 (可选) |
| **Logstash** | logstash | 9080/9081/9600 | 9080/9081/9600 | - | 日志处理 |
| **Topic Init** | topic-init | - | - | - | Kafka主题初始化 |

---

## 🔗 服务间通信

### 网络配置
```yaml
networks:
  threat-detection-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### 服务发现
- **内部通信**: 使用容器名称作为主机名
- **外部访问**: 通过暴露端口访问
- **API Gateway**: 统一入口点 (8888)

---

## 🌐 API端点总览

### 1. API Gateway (端口 8888)
**路由所有微服务API请求**

| 路径模式 | 目标服务 | 说明 |
|----------|----------|------|
| `/api/v1/customers/**` | customer-management:8084 | 客户管理API |
| `/api/v1/alerts/**` | alert-management:8082 | 告警管理API |
| `/api/v1/logs/**` | data-ingestion:8080 | 数据摄取API |
| `/api/v1/assessment/**` | threat-assessment:8083 | 威胁评估API |

### 2. Customer Management Service (端口 8084)

#### 客户管理 API
```
GET    /api/v1/customers                           # 列表所有客户
POST   /api/v1/customers                           # 创建客户
GET    /api/v1/customers/{customerId}              # 获取客户详情
PATCH  /api/v1/customers/{customerId}              # 更新客户
DELETE /api/v1/customers/{customerId}              # 删除客户
```

#### 设备绑定 API
```
POST   /api/v1/customers/{customerId}/devices      # 绑定设备
GET    /api/v1/customers/{customerId}/devices      # 列表设备
PUT    /api/v1/customers/{customerId}/devices/{devSerial}    # 更新设备
DELETE /api/v1/customers/{customerId}/devices/{devSerial}    # 解绑设备
GET    /api/v1/customers/{customerId}/devices/quota          # 获取设备配额
```

#### 通知配置 API
```
GET    /api/v1/customers/{customerId}/notification-config     # 获取通知配置
PUT    /api/v1/customers/{customerId}/notification-config     # 设置通知配置
PATCH  /api/v1/customers/{customerId}/notification-config     # 更新通知配置
```

### 3. Alert Management Service (端口 8082)

#### 告警管理 API
```
GET    /api/v1/alerts                           # 列表告警 (分页)
POST   /api/v1/alerts                           # 创建告警
GET    /api/v1/alerts/{id}                      # 获取告警详情
PUT    /api/v1/alerts/{id}                      # 更新告警
DELETE /api/v1/alerts/{id}                      # 删除告警
POST   /api/v1/alerts/{id}/resolve              # 解决告警
```

#### 告警统计 API
```
GET    /api/v1/alerts/stats                     # 告警统计
GET    /api/v1/alerts/count                     # 告警计数
```

### 4. Data Ingestion Service (端口 8080)

#### 日志摄取 API
```
POST   /api/v1/logs/ingest                      # 摄取单条日志
GET    /api/v1/logs/stats                       # 获取摄取统计
GET    /api/v1/logs/health                      # 健康检查
```

#### 批量处理 API
```
POST   /api/v1/logs/batch                       # 批量摄取日志
GET    /api/v1/logs/batch/{batchId}             # 获取批量处理状态
```

### 5. Threat Assessment Service (端口 8083)

#### 威胁评估 API
```
POST   /api/v1/assessment/evaluate              # 威胁评估 ⚠️ (当前有问题)
GET    /api/v1/assessment/trends                # 威胁趋势分析
GET    /api/v1/assessment/health                # 健康检查
```

#### 配置查询 API
```
GET    /api/v1/assessment/config/ip-weights     # IP权重配置
GET    /api/v1/assessment/config/port-weights   # 端口权重配置
GET    /api/v1/assessment/config/threat-labels  # 威胁标签配置
```

---

## 🏥 健康检查配置

### 健康检查端点
```yaml
healthcheck:
  test: ["CMD", "curl", "-fsS", "http://localhost:8081/api/v1/assessment/health"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 60s
```

### 健康状态监控
- **HTTP 200**: 服务正常
- **HTTP 503**: 服务不可用
- **连接超时**: 网络问题

---

## ⚙️ 环境变量配置

### 通用配置
```yaml
common-env: &common-env
  LOGGING_LEVEL_ROOT: "WARN"
  LOGGING_LEVEL_COM_THREATDETECTION: "INFO"
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_KAFKA: "INFO"
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB: "INFO"
  SPRING_JPA_OPEN_IN_VIEW: "true"
  SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL: "false"
```

### 服务特定配置

#### Data Ingestion
```yaml
SPRING_PROFILES_ACTIVE: docker
SERVER_PORT: 8080
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/threat_detection
SPRING_DATASOURCE_USERNAME: threat_user
SPRING_DATASOURCE_PASSWORD: threat_password
```

#### Threat Assessment
```yaml
SPRING_PROFILES_ACTIVE: docker
SERVER_PORT: 8081
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/threat_detection
SPRING_DATASOURCE_USERNAME: threat_user
SPRING_DATASOURCE_PASSWORD: threat_password
```

#### Alert Management
```yaml
SPRING_PROFILES_ACTIVE: docker
SERVER_PORT: 8084
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/threat_detection
SPRING_DATASOURCE_USERNAME: threat_user
SPRING_DATASOURCE_PASSWORD: threat_password
```

---

## 📊 资源限制

### CPU 和内存限制
```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 2G
    reservations:
      cpus: '1.0'
      memory: 1G
```

### 资源分配表

| 服务 | CPU限制 | 内存限制 | CPU预留 | 内存预留 |
|------|---------|----------|---------|----------|
| API Gateway | 1.0 | 1G | 0.5 | 512M |
| Data Ingestion | 2.0 | 2G | 1.0 | 1G |
| Stream Processing | 2.0 | 4G | 1.0 | 2G |
| Threat Assessment | 2.0 | 2G | 1.0 | 1G |
| Alert Management | 1.0 | 1G | 0.5 | 512M |
| Customer Management | 1.0 | 1G | 0.5 | 512M |
| PostgreSQL | 2.0 | 4G | 1.0 | 2G |
| Kafka | 2.0 | 4G | 1.0 | 2G |

---

## 🔄 启动顺序和依赖

### 启动顺序
1. **基础设施层**: PostgreSQL, Zookeeper, Redis
2. **消息队列层**: Kafka, Topic Init
3. **数据处理层**: Logstash
4. **应用服务层**: Data Ingestion, Stream Processing
5. **业务服务层**: Threat Assessment, Alert Management, Customer Management
6. **入口层**: API Gateway

### 依赖关系图
```
PostgreSQL → Customer Management
           → Alert Management
           → Threat Assessment
           → Data Ingestion
           → Stream Processing

Zookeeper → Kafka → Data Ingestion
                → Stream Processing
                → Threat Assessment

Data Ingestion → Stream Processing → Threat Assessment → Alert Management
```

---

## 🚀 快速启动命令

### 开发环境启动
```bash
cd docker
docker-compose up -d
sleep 60  # 等待服务启动
```

### 服务状态检查
```bash
# 检查所有服务状态
docker-compose ps

# 检查特定服务日志
docker-compose logs threat-assessment-service

# 测试API连通性
curl http://localhost:8888/api/v1/customers
```

### 停止服务
```bash
docker-compose down
```

---

## 🔧 故障排查

### 常见问题

#### 服务启动失败
```bash
# 检查依赖服务状态
docker-compose ps postgres kafka

# 查看服务日志
docker-compose logs <service-name>

# 重启失败的服务
docker-compose restart <service-name>
```

#### 端口冲突
```bash
# 检查端口占用
netstat -tulpn | grep :8080

# 修改端口映射
# 编辑 docker-compose.yml 中的 ports 配置
```

#### 数据库连接失败
```bash
# 检查数据库状态
docker-compose exec postgres pg_isready -U threat_user -d threat_detection

# 查看数据库日志
docker-compose logs postgres
```

---

## 📈 监控和日志

### 日志级别配置
- **生产**: WARN (减少日志量)
- **开发**: INFO (详细调试信息)
- **调试**: DEBUG (完整调试信息)

### 关键监控指标
- **响应时间**: API响应时间 < 1秒
- **错误率**: HTTP 5xx 错误 < 1%
- **资源使用**: CPU < 80%, 内存 < 80%
- **队列积压**: Kafka消息积压 < 1000

---

*此文档定义了完整的容器架构、端口配置和API端点*
