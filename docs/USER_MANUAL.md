# 威胁检测系统用户手册

**版本**: 3.0  
**更新日期**: 2026-04-24  
**适用对象**: 系统管理员、运维工程师、开发人员

---

## 目录

- [第一章 系统概述](#第一章-系统概述)
  - [1.1 系统定位](#11-系统定位)
  - [1.2 核心概念](#12-核心概念)
  - [1.3 系统架构](#13-系统架构)
  - [1.4 数据流](#14-数据流)
  - [1.5 技术栈](#15-技术栈)
- [第二章 部署指南](#第二章-部署指南)
  - [2.1 环境要求](#21-环境要求)
  - [2.2 Docker Compose 部署 (开发环境)](#22-docker-compose-部署-开发环境)
  - [2.3 Kubernetes 部署 (生产环境)](#23-kubernetes-部署-生产环境)
  - [2.4 镜像构建](#24-镜像构建)
- [第三章 使用指南](#第三章-使用指南)
  - [3.1 API 网关](#31-api-网关)
  - [3.2 核心 API 端点](#32-核心-api-端点)
  - [3.3 前端仪表板](#33-前端仪表板)
  - [3.4 威胁评分算法](#34-威胁评分算法)
  - [3.5 TIRE 威胁情报引擎](#35-tire-威胁情报引擎)
  - [3.6 ML 检测流水线](#36-ml-检测流水线)
- [第四章 运维指南](#第四章-运维指南)
  - [4.1 监控体系](#41-监控体系)
  - [4.2 备份与恢复](#42-备份与恢复)
  - [4.3 网络安全策略](#43-网络安全策略)
  - [4.4 资源管理](#44-资源管理)
  - [4.5 日志与排障](#45-日志与排障)
  - [4.6 CI/CD 流水线](#46-cicd-流水线)
  - [4.7 回归测试](#47-回归测试)
- [第五章 二次开发指南](#第五章-二次开发指南)
  - [5.1 项目结构](#51-项目结构)
  - [5.2 添加新 Java 微服务](#52-添加新-java-微服务)
  - [5.3 添加新 Python 服务](#53-添加新-python-服务)
  - [5.4 扩展 TIRE 插件](#54-扩展-tire-插件)
  - [5.5 Kafka 主题与消息格式](#55-kafka-主题与消息格式)
  - [5.6 数据库扩展](#56-数据库扩展)
  - [5.7 前端扩展](#57-前端扩展)
  - [5.8 编码规范](#58-编码规范)
- [附录](#附录)
  - [A. 端口分配表](#a-端口分配表)
  - [B. 环境变量参考](#b-环境变量参考)
  - [C. 常见问题速查](#c-常见问题速查)

---

## 第一章 系统概述

### 1.1 系统定位

本系统是一个基于蜜罐 (Honeypot) 机制的内网威胁检测平台。与传统边界防御不同，系统在内网部署终端设备（硬件哨兵），利用未使用的 IP 地址作为诱饵，诱导内网失陷主机（已被攻破的设备）暴露攻击行为。由于正常用户不会访问这些诱饵 IP，任何对诱饵的访问都可确认为恶意行为，因此具备极低的误报率。

系统从原有 C# + Windows + MySQL 单体架构迁移至云原生微服务架构，实现了：

- 从批处理（10-30 分钟延迟）到实时流处理（小于 4 分钟端到端延迟）
- 从单机 MySQL 到分布式 Kafka + Flink + PostgreSQL
- 从 Windows 专属到跨平台 Linux (Ubuntu 24.04)
- 多租户隔离、RBAC 权限体系、ML 增强检测

### 1.2 核心概念

| 概念 | 说明 |
|------|------|
| 终端设备 (dev_serial) | 部署在内网的硬件哨兵，监控二层网络，发现未使用 IP 并响应 ARP/ICMP 诱导攻击 |
| 诱饵 IP (response_ip) | 由终端设备识别的未使用 IP 地址，作为虚拟蜜罐存在 |
| 被诱捕主机 (attack_mac / attack_ip) | 访问诱饵 IP 的内网主机，即已被攻破或存在恶意行为的设备 |
| 攻击意图端口 (response_port) | 攻击者尝试访问的端口号，反映其攻击意图（如 3389=远程控制, 445=横向移动, 3306=数据窃取） |
| 威胁评分 (threatScore) | 基于攻击频率、横向移动范围、端口多样性等多维度综合计算的威胁程度数值 |
| 客户 (customerId) | 多租户体系下的客户标识，所有数据按客户隔离 |

**关键认知**：`response_ip` 是诱饵而非真实服务器，`attack_ip` 是被诱捕的内网失陷主机而非外部攻击者。所有对诱饵 IP 的访问行为都是确认的恶意行为。

### 1.3 系统架构

系统由 11 个微服务组成：

| 服务 | 语言 | 端口 | 职责 |
|------|------|------|------|
| data-ingestion | Java (Spring Boot 3.1.5) | 8080 | 接收 rsyslog/MQTT 日志，解析后发布到 Kafka |
| stream-processing | Java (Apache Flink 1.17) | 8081 (Flink UI) | 实时威胁检测，3 级时间窗口聚合与评分 |
| alert-management | Java (Spring Boot) | 8082 | 多通道告警通知（Email/SMS/Webhook/Slack/Teams），智能去重与升级 |
| threat-assessment | Java (Spring Boot) | 8083 | 威胁评分计算、风险评估、历史趋势分析、设备管理 |
| customer-management | Java (Spring Boot) | 8084 | 客户 CRUD、设备绑定、多租户层级、网段权重配置 |
| threat-intelligence | Java (Spring Boot) | 8085 | 威胁情报指标管理、IP 信誉查询、多源 IOC 聚合 |
| ml-detection | Python (FastAPI) | 8086 | PyTorch 自编码器 + BiGRU 异常检测，ONNX Runtime 推理 |
| api-gateway | Java (Spring Cloud Gateway) | 8888 | 统一入口，JWT RBAC 鉴权，路由转发，系统配置管理 |
| config-server | Java (Spring Cloud Config) | 8899 | 统一配置中心 (native backend) |
| tire | Python (FastAPI) | 5000 | 威胁情报信誉引擎 (TIRE)，11 款插件集成，LLM 验证 |
| frontend | React 18 | 3000 | Web 管理仪表板 |

### 1.4 数据流

```
V1 哨兵 (syslog KV)  --> rsyslog:9080 --> Data Ingestion --> Kafka (attack-events / status-events)
V2 哨兵 (MQTT JSON)  --> EMQX:1883  -->        |                          |
                                                 v                          v
                                          Flink Stream Processing (30s/5min/15min 三级窗口)
                                                 |
                                                 v
                                          Kafka (threat-alerts)
                                           |              |
                                           v              v
                                    Threat Assessment   ML Detection (PyTorch/ONNX)
                                           |              |
                                           v              v
                                    TIRE Enrichment    Kafka (ml-threat-detections)
                                           |
                                           v
                                    Alert Management --> Email / SMS / Slack / Webhook / Teams
                                           |
                                           v
                                      PostgreSQL (持久化)
```

**处理流程说明**：

1. V1 哨兵通过 rsyslog 发送 syslog KV 格式日志到 Data Ingestion 服务 (端口 9080)
2. V2 哨兵通过 EMQX MQTT Broker 发送 JSON 格式事件（支持 attack/sniffer/threat/heartbeat 等 7 种事件类型）
3. Data Ingestion 解析日志并发布结构化事件到 Kafka 主题 (`attack-events`, `status-events`)
4. Apache Flink 进行多维度威胁评分：30 秒（勒索软件）、5 分钟（主要威胁）、15 分钟（APT）三级时间窗口
5. 威胁评估服务计算最终风险等级，持久化到 PostgreSQL
6. ML 检测服务提供辅助异常检测评分 (mlWeight 0.5-3.0)
7. TIRE 引擎通过 11 款插件聚合外部威胁情报，丰富告警上下文
8. 告警管理服务执行多通道通知，支持智能去重和升级策略

### 1.5 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.1.5 |
| JDK | OpenJDK | 21 LTS |
| 前端框架 | React | 18 |
| 消息队列 | Apache Kafka | 3.4+ |
| 流处理 | Apache Flink | 1.17+ |
| 数据库 | PostgreSQL | 15 |
| 缓存 | Redis | 7 |
| MQTT Broker | EMQX | 5.5.1 |
| ML 推理 | PyTorch + ONNX Runtime | 2.2+ |
| TIRE 后端 | Python (FastAPI) | 3.11+ |
| 容器 | Docker + Docker Compose | 20.10+ |
| 编排 | Kubernetes (K3s) | 1.25+ |
| 构建 | Maven | 3.8.7 |

---

## 第二章 部署指南

### 2.1 环境要求

**最低硬件配置**：

| 环境 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| 开发 (Docker Compose) | 4 核 | 16 GB | 50 GB |
| 生产 (K3s 单节点) | 8 核 | 32 GB | 100 GB |

**软件依赖**：

| 软件 | 版本 | 用途 |
|------|------|------|
| Ubuntu | 24.04 LTS | 操作系统 |
| OpenJDK | 21 LTS | Java 服务编译运行 |
| Maven | 3.8.7+ | Java 项目构建 |
| Docker Engine | 20.10+ | 容器化 |
| Docker Compose | v2+ | 开发环境编排 |
| Node.js | 18+ | 前端构建 |
| Python | 3.11+ | ML 检测和 TIRE 服务 |
| kubectl | 1.25+ | K8s 部署 (生产) |
| K3s | 1.25+ | 轻量级 Kubernetes (生产) |

### 2.2 Docker Compose 部署 (开发环境)

#### 2.2.1 获取代码

```bash
git clone <repository-url>
cd threat-detection-system
```

#### 2.2.2 构建 Java 服务

```bash
# 在项目根目录编译所有 Java 服务
mvn clean package -DskipTests
```

#### 2.2.3 启动服务

```bash
cd docker
docker compose up -d
```

Docker Compose 将启动以下容器：

| 容器名 | 镜像 | 说明 |
|--------|------|------|
| postgres | postgres:15-alpine | 数据库，自动执行 SQL 初始化脚本 |
| zookeeper | confluentinc/cp-zookeeper:7.4.0 | Kafka 协调服务 |
| kafka | confluentinc/cp-kafka:7.4.0 | 消息队列 |
| redis | redis:7-alpine | 缓存 |
| logstash | docker.elastic.co/logstash/logstash:8.11.1 | 日志收集（V1 syslog 接入） |
| emqx | emqx/emqx:5.5.1 | MQTT Broker（V2 哨兵接入） |
| config-server | 本地构建 | 配置中心 |
| data-ingestion-service | 本地构建 | 数据摄取 |
| stream-processing | 本地构建 | Flink JobManager |
| taskmanager | 本地构建 | Flink TaskManager |
| threat-assessment-service | 本地构建 | 威胁评估 |
| alert-management-service | 本地构建 | 告警管理 |
| customer-management-service | 本地构建 | 客户管理 |
| threat-intelligence-service | 本地构建 | 威胁情报 |
| ml-detection-service | 本地构建 | ML 检测 |
| api-gateway | 本地构建 | API 网关 |
| frontend | 本地构建 | 前端界面 |
| topic-init | 本地构建 | Kafka 主题初始化 (一次性 Job) |

#### 2.2.4 数据库初始化

PostgreSQL 容器启动时自动执行 `docker/` 目录下的 SQL 初始化脚本，按编号顺序执行。主要脚本包括：

| 脚本 | 说明 |
|------|------|
| 01-init-db-production-safe.sql | 核心表结构创建 |
| 02-attack-events-storage.sql | 攻击事件存储表 |
| 03-port-weights.sql | 端口权重配置 |
| 04-alert-management-tables.sql | 告警管理表 |
| 04-threat-assessment-tables.sql | 威胁评估表 |
| 05-notification-config-tables.sql | 通知配置表 |
| 17-customers-init.sql | 客户初始数据 |
| 18-net-segment-weights.sql | 网段权重表 |
| 19-device-inventory.sql | 设备清单表 |
| 22-threat-indicators.sql | 威胁指标表 |
| 26-ml-detection-tables.sql | ML 检测表 |

#### 2.2.5 验证服务健康

等待约 60-90 秒后，逐一检查服务状态：

```bash
# 基础设施
curl -s http://localhost:5432 > /dev/null && echo "PostgreSQL: OK"

# Java 服务
curl -s http://localhost:8080/actuator/health | jq .status   # Data Ingestion
curl -s http://localhost:8082/actuator/health | jq .status   # Alert Management
curl -s http://localhost:8083/api/v1/assessment/health        # Threat Assessment
curl -s http://localhost:8084/actuator/health | jq .status   # Customer Management
curl -s http://localhost:8085/actuator/health | jq .status   # Threat Intelligence
curl -s http://localhost:8888/actuator/health | jq .status   # API Gateway
curl -s http://localhost:8899/actuator/health | jq .status   # Config Server

# Python 服务
curl -s http://localhost:8086/health                          # ML Detection
curl -s http://localhost:5000/health                          # TIRE

# 前端
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000  # Frontend (应返回 200)

# 管理界面
# Flink Web UI: http://localhost:8081
# EMQX Dashboard: http://localhost:18083 (默认账号 admin/public)
```

#### 2.2.6 查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看单个服务日志
docker compose logs -f data-ingestion-service
docker compose logs -f stream-processing
docker compose logs -f api-gateway

# 使用辅助脚本
./scripts/tools/tail_logs.sh
```

### 2.3 Kubernetes 部署 (生产环境)

#### 2.3.1 K3s 安装

```bash
# 安装 K3s 单节点
curl -sfL https://get.k3s.io | sh -

# 配置 kubectl
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config

# 验证
kubectl get nodes
```

#### 2.3.2 部署方式

系统使用 Kustomize 管理 K8s 清单，提供三种部署方式：

**基础部署**（直接使用基础配置，namespace: threat-detection）：

```bash
kubectl apply -k k8s/base
```

**开发环境部署**（低资源、单副本，namespace: threat-detection-dev）：

```bash
kubectl apply -k k8s/overlays/development
```

**生产环境部署**（高资源、多副本、安全上下文，namespace: threat-detection-prod）：

```bash
kubectl apply -k k8s/overlays/production
```

每种部署方式渲染 79 个 K8s 资源，包括：

| 资源类型 | 说明 |
|----------|------|
| Namespace | 命名空间定义 |
| Deployments (15+) | 所有微服务 + 基础设施 + 监控组件 |
| Services | ClusterIP 服务发现 |
| ConfigMaps | 应用配置、Prometheus 抓取配置、Grafana 仪表板 |
| PersistentVolumeClaims | PostgreSQL 数据卷、Kafka 数据卷、Prometheus 存储 |
| Jobs | Kafka 主题初始化、数据库 Schema 应用 |
| CronJobs | PostgreSQL 定时备份 |
| NetworkPolicies | 网络安全策略 |
| RBAC | Prometheus 集群角色绑定 |

#### 2.3.3 验证部署

```bash
# 查看所有 Pod 状态
kubectl get pods -n threat-detection

# 查看服务端点
kubectl get svc -n threat-detection

# 查看资源使用
kubectl top pods -n threat-detection

# 端口转发访问 API Gateway
kubectl port-forward -n threat-detection svc/api-gateway 8888:8888

# 端口转发访问 Flink UI
kubectl port-forward -n threat-detection svc/stream-processing 8081:8081

# 端口转发访问 Grafana
kubectl port-forward -n threat-detection svc/grafana 3000:3000

# 端口转发访问 Prometheus
kubectl port-forward -n threat-detection svc/prometheus 9090:9090
```

#### 2.3.4 K8s 清单文件说明

| 文件 | 说明 |
|------|------|
| namespace.yaml | 命名空间定义 |
| postgres.yaml | PostgreSQL 数据库部署 |
| redis.yaml | Redis 缓存部署 |
| zookeeper.yaml | Zookeeper 部署 |
| kafka.yaml | Kafka 部署 |
| kafka-topic-init.yaml | Kafka 主题初始化 Job |
| emqx.yaml | EMQX MQTT Broker |
| logstash.yaml | Logstash 日志收集 |
| config-server.yaml | 配置服务器 |
| data-ingestion.yaml | 数据摄取服务 |
| stream-processing.yaml | Flink 流处理 (JobManager + TaskManager) |
| threat-assessment.yaml | 威胁评估服务 |
| alert-management.yaml | 告警管理服务 |
| customer-management.yaml | 客户管理服务 |
| threat-intelligence.yaml | 威胁情报服务 |
| ml-detection.yaml | ML 检测服务 |
| api-gateway.yaml | API 网关 |
| tire.yaml | TIRE 服务 |
| prometheus.yaml | Prometheus 监控 + RBAC |
| prometheus-rules.yaml | Prometheus 告警规则 |
| grafana.yaml | Grafana 仪表板 |
| alertmanager.yaml | Alertmanager 告警路由 |
| postgres-backup.yaml | 数据库备份 CronJob |
| postgres-schema-apply.yaml | 数据库 Schema 初始化 |
| network-policies.yaml | 网络安全策略 |

### 2.4 镜像构建

#### 2.4.1 Java 服务

```bash
# 编译
cd services/<service-name>
mvn clean package -DskipTests

# 构建镜像
docker build -t threat-detection/<service-name>:latest .
```

#### 2.4.2 Python 服务

```bash
# ML Detection
cd services/ml-detection
docker build -t threat-detection/ml-detection:latest .

# TIRE
cd services/tire
docker build -t threat-detection/tire:latest .
```

#### 2.4.3 前端

```bash
cd frontend
docker build -t threat-detection/frontend:latest .
```

#### 2.4.4 完整重建（代码修改后必做）

代码修改后容器不会自动加载新代码，必须重新构建镜像：

```bash
# 推荐：使用自动化脚本
./scripts/tools/full_restart.sh

# 手动执行
mvn clean package -DskipTests
cd docker
docker compose build --no-cache
docker compose up -d --force-recreate
```

单个服务重建：

```bash
# 重建单个 Java 服务
cd services/<service-name>
mvn clean package -DskipTests
cd ../../docker
docker compose build --no-cache <container-name>
docker compose up -d --force-recreate <container-name>
```

---

## 第三章 使用指南

### 3.1 API 网关

所有外部请求通过 API 网关统一接入：

- **开发环境**: `http://localhost:8888`
- **K8s 环境**: `http://<node-ip>:30080` (NodePort) 或通过 Ingress

#### 3.1.1 认证

系统使用 JWT (JSON Web Token) 进行认证：

```bash
# 登录获取 Token
curl -X POST http://localhost:8888/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'

# 响应示例
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

后续请求携带 Token：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8888/api/v1/...
```

#### 3.1.2 JSON 命名规范

所有 API 请求和响应统一使用 **camelCase** 命名：

```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "responseIp": "10.0.0.1",
  "threatScore": 125.5,
  "threatLevel": "HIGH"
}
```

### 3.2 核心 API 端点

#### 3.2.1 数据摄取服务 (端口 8080)

```bash
# 发送单条日志
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "deviceSerial": "DEV-001",
    "attackMac": "00:11:22:33:44:55",
    "attackIp": "192.168.1.100",
    "responseIp": "10.0.0.1",
    "responsePort": 3306,
    "timestamp": "2026-04-24T10:30:00Z"
  }'

# 批量发送
curl -X POST http://localhost:8080/api/v1/logs/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"customerId": "customer-001", "deviceSerial": "DEV-001", ...},
    {"customerId": "customer-001", "deviceSerial": "DEV-001", ...}
  ]'

# 健康检查
curl http://localhost:8080/actuator/health
```

#### 3.2.2 威胁评估服务 (端口 8083)

```bash
# 查询威胁评估列表
curl http://localhost:8083/api/v1/assessment/threats?customerId=customer-001

# 查询趋势分析
curl http://localhost:8083/api/v1/assessment/trends?customerId=customer-001&period=24h

# 按攻击者 MAC 查询
curl http://localhost:8083/api/v1/assessment/threats/00:11:22:33:44:55

# 健康检查
curl http://localhost:8083/api/v1/assessment/health
```

#### 3.2.3 客户管理服务 (端口 8084)

```bash
# 客户 CRUD
curl http://localhost:8084/api/v1/customers                    # 列表
curl http://localhost:8084/api/v1/customers/{id}               # 详情
curl -X POST http://localhost:8084/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"name": "客户A", "subscriptionTier": "PREMIUM"}'       # 创建

# 设备管理
curl http://localhost:8084/api/v1/devices?customerId=customer-001

# 网段权重配置
curl http://localhost:8084/api/v1/net-weights?customerId=customer-001
curl -X POST http://localhost:8084/api/v1/net-weights \
  -H "Content-Type: application/json" \
  -d '{"customerId": "customer-001", "cidr": "192.168.1.0/24", "weight": 2.5}'
```

#### 3.2.4 告警管理服务 (端口 8082)

```bash
# 查询告警列表
curl http://localhost:8082/api/v1/alerts?customerId=customer-001

# 查询告警详情
curl http://localhost:8082/api/v1/alerts/{id}

# 更新告警状态
curl -X PUT http://localhost:8082/api/v1/alerts/{id}/status \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED"}'

# 告警统计
curl http://localhost:8082/api/v1/alerts/analytics/stats?customerId=customer-001
```

#### 3.2.5 威胁情报服务 (端口 8085)

```bash
# IP 信誉查询
curl http://localhost:8085/api/v1/threat-intel/lookup/192.168.1.100

# 威胁指标管理
curl http://localhost:8085/api/v1/threat-intel/indicators
```

#### 3.2.6 ML 检测服务 (端口 8086)

```bash
# 健康检查
curl http://localhost:8086/health

# Prometheus 指标
curl http://localhost:8086/metrics
```

ML 检测服务通过 Kafka 异步工作，消费 `threat-alerts` 主题，产出 `ml-threat-detections`。不提供同步 REST 接口调用检测。

#### 3.2.7 TIRE 服务 (端口 5000)

```bash
# IP 信誉查询
curl -X POST http://localhost:5000/api/v1/tire/lookup \
  -H "Content-Type: application/json" \
  -d '{"ip": "192.168.1.100"}'

# 健康检查
curl http://localhost:5000/health

# Prometheus 指标
curl http://localhost:5000/metrics
```

### 3.3 前端仪表板

访问地址：
- 开发环境: `http://localhost:3000`
- 通过 API Gateway: `http://localhost:8888` (代理到前端)

#### 3.3.1 页面功能

| 页面 | 路径 | 说明 |
|------|------|------|
| Dashboard | / | 系统总览：实时威胁态势、攻击统计、热力图 |
| Analytics | /analytics | 深度分析：趋势图表、攻击模式、Top-N 统计 |
| Alerts | /alerts | 告警管理：告警列表、状态更新、升级处理 |
| Customers | /customers | 客户管理：客户 CRUD、设备绑定、网段配置 |
| ML Detection | /ml-detection | ML 检测：模型状态、异常检测结果、置信度分析 |
| Threat Intel | /threat-intel | 威胁情报：IOC 查询、情报源管理、TIRE 结果 |
| Logs | /logs | 日志查看：原始日志检索、事件时间线 |
| Settings | /settings | 系统设置：TIRE 插件管理、LLM 配置、RBAC 权限 |
| Login | /login | 登录页面 |

#### 3.3.2 RBAC 角色

| 角色 | 权限范围 |
|------|---------|
| SuperAdmin | 系统全局管理，可管理所有租户和用户 |
| TenantAdmin | 分销商/大客户管理员，管理旗下客户 |
| CustomerUser | 普通用户，仅查看自身客户数据 |

### 3.4 威胁评分算法

#### 3.4.1 核心公式

```
threatScore = (attackCount * uniqueIps * uniquePorts)
            * timeWeight * ipWeight * portWeight * deviceWeight
            * netWeight * intelWeight * mlWeight
```

| 因子 | 说明 | 范围 |
|------|------|------|
| attackCount | 对诱饵的探测次数 | - |
| uniqueIps | 访问的诱饵 IP 数量（横向移动范围） | - |
| uniquePorts | 尝试的端口种类（攻击意图多样性） | - |
| timeWeight | 时间衰减因子 | 0.8 - 1.2 |
| ipWeight | IP 多样性放大 | 1.0 - 2.0 |
| portWeight | 端口多样性权重 | 1.0 - 2.0 |
| deviceWeight | 多设备覆盖奖励 | 1.0 - 1.5 |
| netWeight | 网段权重（按客户 + CIDR 配置） | 0.01 - 10.0 |
| intelWeight | 威胁情报权重（IOC 置信度 + 严重性） | 1.0 - 4.5 |
| mlWeight | ML 异常检测建议性倍率 | 0.5 - 3.0 |

#### 3.4.2 时间权重

| 时段 | 权重 | 说明 |
|------|------|------|
| 00:00 - 06:00 | 1.2 | 深夜异常行为，权重最高 |
| 06:00 - 09:00 | 1.1 | 早晨时段 |
| 09:00 - 17:00 | 1.0 | 工作时间基准 |
| 17:00 - 21:00 | 0.9 | 傍晚时段 |
| 21:00 - 24:00 | 0.8 | 夜间时段 |

#### 3.4.3 IP 权重

| 唯一 IP 数 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单一目标 |
| 2-3 | 1.3 | 小范围扫描 |
| 4-5 | 1.5 | 中等扫描 |
| 6-10 | 1.7 | 广泛扫描 |
| 10+ | 2.0 | 大规模扫描 |

#### 3.4.4 端口权重

| 唯一端口数 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单端口攻击 |
| 2-3 | 1.2 | 小范围探测 |
| 4-5 | 1.4 | 中等扫描 |
| 6-10 | 1.6 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 全端口扫描 |

#### 3.4.5 设备权重

| 唯一设备数 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单一设备检测 |
| 2+ | 1.5 | 多设备检测到同一攻击者（横向移动指标） |

#### 3.4.6 威胁等级

| 等级 | 分数范围 | 说明 |
|------|---------|------|
| INFO | < 10 | 信息级别，无需处理 |
| LOW | 10 - 50 | 低危威胁，记录观察 |
| MEDIUM | 50 - 100 | 中危威胁，需要关注 |
| HIGH | 100 - 200 | 高危威胁，需要处理 |
| CRITICAL | > 200 | 严重威胁，需要立即响应 |

### 3.5 TIRE 威胁情报引擎

TIRE (Threat Intelligence Reputation Engine) 集成了 11 款内置情报插件和社区插件扩展机制。

#### 3.5.1 内置插件

| 插件 | 数据源 | 能力 |
|------|--------|------|
| AbuseIPDB | abuseipdb.com | IP 滥用历史、置信度评分 |
| VirusTotal | virustotal.com | 多引擎恶意软件检测 |
| OTX | AlienVault OTX | 开放威胁情报交换 |
| GreyNoise | greynoise.io | 互联网扫描器/噪音识别 |
| Shodan | shodan.io | 设备指纹、开放端口 |
| RDAP | 各区域 RIR | IP 注册信息、ASN |
| Reverse DNS | DNS 反向解析 | 域名关联 |
| Honeynet | 内部蜜网 | 内部情报关联 |
| Internal Flow | 内部流量分析 | 网络行为基线 |
| ThreatBook | threatbook.cn | 中文威胁情报平台 |
| 天际友盟 | 天际友盟 API | 国内威胁情报聚合 |

#### 3.5.2 插件配置

通过前端 Settings 页面管理插件的启用/禁用、API 密钥、优先级和超时时间。也可通过 API Gateway 的系统配置接口管理。

#### 3.5.3 LLM 验证

TIRE 支持连接 LLM（大语言模型）对聚合后的情报结果进行二次验证和上下文分析。通过 Settings 页面配置 LLM 连接参数。

### 3.6 ML 检测流水线

#### 3.6.1 三级模型架构

| 级别 | 时间窗口 | 检测目标 | 模型 |
|------|---------|---------|------|
| Tier 1 | 30 秒 | 勒索软件快速传播 | BiGRU + 自编码器 |
| Tier 2 | 5 分钟 | 主要威胁检测 | BiGRU + 自编码器 |
| Tier 3 | 15 分钟 | APT 长期行为分析 | BiGRU + 自编码器 |

#### 3.6.2 工作流

1. ML 检测服务消费 Kafka `threat-alerts` 主题
2. 使用 ONNX Runtime 执行高性能推理
3. 计算异常分数并映射为 mlWeight (0.5 - 3.0)
4. 产出结果发布到 Kafka `ml-threat-detections` 主题
5. 结果持久化到 PostgreSQL
6. mlWeight 作为建议性评分倍率参与最终 threatScore 计算（通过特征开关控制）

#### 3.6.3 时间窗口配置

通过环境变量自定义时间窗口：

| 变量 | 默认值 | 建议范围 |
|------|--------|---------|
| TIER1_WINDOW_SECONDS | 30 | 10 - 300 |
| TIER2_WINDOW_SECONDS | 300 | 60 - 1800 |
| TIER3_WINDOW_SECONDS | 900 | 300 - 7200 |

---

## 第四章 运维指南

### 4.1 监控体系

系统部署了完整的 Prometheus + Grafana + Alertmanager 监控栈。

#### 4.1.1 Prometheus

- **端口**: 9090
- **抓取目标**: 14 个（所有微服务 + 基础设施）
- **存储**: PersistentVolumeClaim 5Gi
- **配置文件**: `k8s/base/prometheus.yaml` (包含 ConfigMap 的 scrape_configs)

Java 服务通过 Spring Boot Actuator 暴露指标：

```
http://<service>:<port>/actuator/prometheus
```

Python 服务通过 prometheus_client 暴露指标：

```
http://<service>:<port>/metrics
```

注意：stream-processing 是纯 Flink Job，不暴露 Spring Actuator，使用 Flink 原生 metrics 系统。

#### 4.1.2 Grafana

- **端口**: 3000
- **默认数据源**: Prometheus (自动配置)
- **预置仪表板**: 包含 3 个行 (Row)：
  - Overview: 服务在线状态、请求总量、错误率
  - Services: 各服务 HTTP 请求延迟、Kafka 消费者延迟
  - Infrastructure: Kafka/PostgreSQL 连接数、JVM 内存使用

访问：`kubectl port-forward -n threat-detection svc/grafana 3000:3000`

#### 4.1.3 告警规则

系统预配置了 5 条告警规则（`k8s/base/prometheus-rules.yaml`）：

| 规则名 | 条件 | 严重性 | 说明 |
|--------|------|--------|------|
| ServiceDown | up == 0, 持续 2 分钟 | critical | 任何被监控服务宕机 |
| HighErrorRate | 5xx 比例 > 5%, 持续 5 分钟 | warning | HTTP 错误率过高 |
| KafkaConsumerLag | 消费者延迟 > 10000, 持续 10 分钟 | warning | Kafka 消费者处理能力不足 |
| HighMemoryUsage | JVM 内存 > 90%, 持续 5 分钟 | warning | 内存接近上限 |
| ThreatDetectionPipelineStalled | 30 分钟内无新告警产出 | critical | 检测流水线可能停滞 |

#### 4.1.4 Alertmanager

- **端口**: 9093
- **路由**: 默认路由 + critical 路由
- **通知渠道**: 通过 ConfigMap 配置 webhook/email 接收器

### 4.2 备份与恢复

#### 4.2.1 PostgreSQL 自动备份

系统配置了 K8s CronJob 每日凌晨 2:00 自动备份：

- **调度**: `0 2 * * *`（每日 02:00）
- **保留**: 7 天（自动清理过期备份）
- **存储**: PersistentVolumeClaim 10Gi
- **配置文件**: `k8s/base/postgres-backup.yaml`

备份使用 `pg_dump` 生成 SQL 格式文件，存储路径：`/backups/threat_detection_<date>.sql`

#### 4.2.2 手动备份

```bash
# Docker 环境
docker exec postgres pg_dump -U threat_user -d threat_detection > backup_$(date +%Y%m%d).sql

# K8s 环境
kubectl exec -n threat-detection deployment/postgres -- \
  pg_dump -U threat_user -d threat_detection > backup_$(date +%Y%m%d).sql
```

#### 4.2.3 恢复

```bash
# Docker 环境
cat backup_20260424.sql | docker exec -i postgres psql -U threat_user -d threat_detection

# K8s 环境 (使用备份 ConfigMap 中的恢复脚本)
kubectl exec -n threat-detection deployment/postgres -- \
  psql -U threat_user -d threat_detection < /backups/threat_detection_20260424.sql
```

#### 4.2.4 Kafka 数据恢复

Kafka 主题数据恢复通过消费者 offset 重置实现：

```bash
# 重置到最早位置（重新处理所有消息）
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group threat-detection-group --topic attack-events \
  --reset-offsets --to-earliest --execute
```

### 4.3 网络安全策略

系统部署了 8 条 K8s NetworkPolicy（`k8s/base/network-policies.yaml`）：

| 策略名 | 说明 |
|--------|------|
| default-deny | 默认拒绝所有入站流量 |
| allow-api-gateway-ingress | 允许外部流量到达 API Gateway |
| allow-data-ingestion-ingress | 允许外部日志数据到达数据摄取服务 |
| allow-kafka-ingress | 允许服务访问 Kafka |
| allow-postgres-ingress | 允许服务访问 PostgreSQL |
| allow-service-from-gateway | 允许 API Gateway 转发到后端服务 |
| allow-prometheus-scrape | 允许 Prometheus 抓取所有服务指标 |
| allow-dns | 允许所有 Pod 访问 DNS |

### 4.4 资源管理

#### 4.4.1 开发环境资源配置

| 服务 | 请求内存 | 限制内存 | 请求 CPU | 限制 CPU |
|------|---------|---------|---------|---------|
| data-ingestion | 256Mi | 512Mi | 200m | 500m |
| stream-processing (JM) | 512Mi | 1Gi | 250m | 500m |
| stream-processing (TM) | 512Mi | 1Gi | 250m | 500m |
| threat-assessment | 256Mi | 512Mi | 200m | 500m |
| customer-management | 256Mi | 512Mi | 150m | 400m |
| alert-management | 256Mi | 512Mi | 150m | 400m |
| threat-intelligence | 256Mi | 512Mi | 150m | 400m |
| api-gateway | 256Mi | 512Mi | 200m | 500m |
| config-server | 192Mi | 384Mi | 100m | 300m |
| ml-detection | 512Mi | 1Gi | 250m | 500m |
| tire | 256Mi | 512Mi | 150m | 400m |
| prometheus | 256Mi | 512Mi | 150m | 400m |
| grafana | 128Mi | 256Mi | 100m | 200m |
| alertmanager | 64Mi | 128Mi | 50m | 100m |

#### 4.4.2 生产环境资源配置

生产环境资源上限约为开发环境的 2 倍，详见 `k8s/overlays/production/resource-patch.yaml`。同时开启安全上下文（`runAsNonRoot`, `readOnlyRootFilesystem`, `drop ALL capabilities`）。

#### 4.4.3 手动扩缩容

```bash
# 调整副本数
kubectl scale deployment data-ingestion --replicas=3 -n threat-detection

# 查看资源使用
kubectl top pods -n threat-detection
```

### 4.5 日志与排障

#### 4.5.1 日志查看

```bash
# Docker Compose 环境
docker compose logs -f <container-name>          # 单个服务
docker compose logs -f --tail=100                 # 所有服务最后 100 行

# K8s 环境
kubectl logs -f deployment/<name> -n threat-detection
kubectl logs -f deployment/data-ingestion -n threat-detection --since=1h
kubectl logs --previous deployment/<name> -n threat-detection  # 上一个 Pod 的日志
```

#### 4.5.2 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| Kafka 消费者未启动 | 配置文件优先级冲突 | 检查 application.properties vs application-docker.yml，确认 bootstrap-servers 配置正确 |
| 数据未持久化 | JSONB 字段注解缺失 | 验证实体类是否有 `@JdbcTypeCode(SqlTypes.JSON)` 注解 |
| 容器代码未更新 | 未重新构建镜像 | 执行 `docker compose build --no-cache <service> && docker compose up -d <service>` |
| E2E 测试失败 | 表名不匹配 | 确认表名为 threat_alerts 还是 threat_assessments |
| 端口冲突 | 本地进程占用 | 检查 8080-8086, 8888, 8899, 5000, 3000 端口是否被占用 |
| 连接被重置 | 并发请求过高 | 使用批量接口，降低并发数：`python3 scripts/tools/bulk_ingest_logs.py --count 5 --workers 4 --batch-size 25` |
| Flink Job 不启动 | TaskManager 未就绪 | 检查 Flink UI (`localhost:8081`)，确认 TaskManager 已注册 |
| ML 模型加载失败 | ONNX 文件缺失 | 检查 ML 服务日志，确认模型文件路径和权限 |
| TIRE 插件超时 | 外部 API 不可达 | 检查网络连通性，调整插件超时配置 |

#### 4.5.3 健康检查脚本

```bash
# 检查数据持久化状态
./scripts/tools/check_persistence.sh

# 运行端到端测试
python3 scripts/test/e2e_mvp_test.py

# 完整重启
./scripts/tools/full_restart.sh
```

### 4.6 CI/CD 流水线

系统配置了两条 GitHub Actions 工作流：

#### 4.6.1 CI 流水线 (`.github/workflows/ci.yml`)

在 push 和 pull_request 时触发，包含 4 个并行 Job：

| Job | 内容 |
|-----|------|
| java-build | OpenJDK 21, Maven compile + test |
| python-lint-test | Python 3.11, pytest |
| frontend-build | Node 18, npm ci / build / lint |
| kustomize-validate | kubectl kustomize 验证 base + overlays |

#### 4.6.2 Docker 构建流水线 (`.github/workflows/docker-build.yml`)

矩阵构建 11 个服务的 Docker 镜像，仅在 main 分支推送时触发。

### 4.7 回归测试

系统提供离线回放回归工具，用于验证 V1/V2 数据处理流水线：

```bash
# 完整回放测试（需要 Kafka 和后端服务运行）
python scripts/regression/replay_harness.py \
  --kafka-broker kafka:9092 \
  --api-url http://localhost:8083

# 干运行（仅验证数据加载，不需要实际服务）
python scripts/regression/replay_harness.py --dry-run

# 指定测试数据
python scripts/regression/replay_harness.py \
  --fixtures-dir scripts/regression/fixtures \
  --kafka-broker kafka:9092 \
  --api-url http://localhost:8083
```

测试数据位于 `scripts/regression/fixtures/`：

| 文件 | 说明 |
|------|------|
| v1_sample_events.json | 12 条 V1 syslog KV 格式攻击事件 |
| v2_sample_events.json | 12 条 V2 MQTT JSON 格式事件（attack + heartbeat + sniffer） |

回放流程：Load（加载测试数据）-> Replay（发送到 Kafka）-> Wait（等待处理）-> Validate（查询 API 验证结果）-> Report（输出报告）。

---

## 第五章 二次开发指南

### 5.1 项目结构

```
threat-detection-system/
├── services/                       # 微服务源码
│   ├── data-ingestion/             # Java - 数据摄取
│   ├── stream-processing/          # Java - Flink 流处理
│   ├── alert-management/           # Java - 告警管理
│   ├── threat-assessment/          # Java - 威胁评估
│   ├── customer-management/        # Java - 客户管理
│   ├── threat-intelligence/        # Java - 威胁情报
│   ├── api-gateway/                # Java - API 网关
│   ├── config-server/              # Java - 配置中心
│   ├── ml-detection/               # Python - ML 检测
│   └── tire/                       # Python - TIRE 情报引擎
├── frontend/                       # React 18 前端
├── docker/                         # Docker Compose 配置 + SQL 初始化脚本
├── k8s/                            # Kubernetes 清单
│   ├── base/                       # 基础配置 (79 resources)
│   └── overlays/                   # 环境覆盖 (development, production)
├── scripts/                        # 工具脚本
│   ├── tools/                      # 运维工具 (full_restart.sh 等)
│   ├── test/                       # 测试脚本
│   └── regression/                 # 回归测试工具
├── docs/                           # 文档
│   ├── api/                        # REST API 文档
│   ├── design/                     # 架构设计文档
│   ├── testing/                    # 测试指南
│   └── fixes/                      # 问题修复记录
├── .github/workflows/              # CI/CD 流水线
└── infrastructure/                 # 基础设施配置
```

每个 Java 微服务遵循标准 Spring Boot 项目结构：

```
services/<service-name>/
├── pom.xml                                    # Maven 构建配置
├── Dockerfile                                 # 容器构建
├── src/main/java/com/threatdetection/<name>/
│   ├── <Name>Application.java                 # 启动类
│   ├── config/                                # 配置类
│   ├── controller/                            # REST 控制器
│   ├── service/                               # 业务逻辑
│   ├── model/                                 # 数据模型 / DTO
│   ├── repository/                            # 数据访问层
│   └── kafka/                                 # Kafka 生产者/消费者
└── src/main/resources/
    ├── application.properties                 # 主配置
    └── application-docker.yml                 # Docker 环境配置
```

### 5.2 添加新 Java 微服务

#### 步骤 1：创建项目

在 `services/` 下创建新目录，创建 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.1.5</version>
    </parent>

    <groupId>com.threatdetection</groupId>
    <artifactId>new-service</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

#### 步骤 2：配置 Actuator + Prometheus

在 `application.properties` 中添加：

```properties
server.port=8090
spring.application.name=new-service
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.enabled=true
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

#### 步骤 3：创建 Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 步骤 4：创建 K8s 清单

在 `k8s/base/` 创建 `new-service.yaml` 并在 `k8s/base/kustomization.yaml` 的 `resources` 中添加。不要在清单中写入 `namespace` 字段，由 kustomization 的 `namespace` 配置统一管理。

#### 步骤 5：注册到监控

在 `k8s/base/prometheus.yaml` 的 ConfigMap `scrape_configs` 中添加新的 scrape job。

### 5.3 添加新 Python 服务

#### 模板

```python
# app/main.py
from fastapi import FastAPI
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

app = FastAPI(title="New Service")

# Prometheus 指标
REQUEST_COUNT = Counter("new_service_requests_total", "Total requests", ["endpoint"])
REQUEST_DURATION = Histogram("new_service_request_duration_seconds", "Request duration")

@app.get("/health")
def health():
    return {"status": "UP"}

@app.get("/metrics")
def metrics():
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
```

#### requirements.txt

```
fastapi>=0.104.0
uvicorn>=0.24.0
prometheus-client>=0.19.0
pydantic>=2.5.0
```

#### Pydantic 模型规范

所有 Pydantic 模型必须使用 camelCase 别名：

```python
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

class MyModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)
    
    customer_id: str
    threat_score: float
```

### 5.4 扩展 TIRE 插件

#### 插件目录结构

```
services/tire/plugins/
├── base.py                    # 插件基类
├── registry.py                # 插件注册表
├── sandbox.py                 # 插件沙箱
├── builtin/                   # 内置插件
│   ├── abuseipdb.py
│   ├── virustotal.py
│   ├── otx.py
│   ├── greynoise.py
│   ├── shodan.py
│   ├── rdap.py
│   ├── reverse_dns.py
│   ├── honeynet.py
│   ├── internal_flow.py
│   ├── threatbook.py
│   └── tianjiyoumeng.py
└── community/                 # 社区插件
    └── example_plugin.py
```

#### 创建新插件

1. 在 `plugins/community/` 下创建新文件
2. 继承 `plugins/base.py` 中的基类
3. 实现 `lookup(ip: str)` 方法
4. 在 `plugins/registry.py` 中注册

```python
# plugins/community/my_plugin.py
from plugins.base import BasePlugin

class MyPlugin(BasePlugin):
    name = "my_plugin"
    
    async def lookup(self, ip: str) -> dict:
        # 实现 IP 查询逻辑
        result = await self._query_external_api(ip)
        return {
            "source": self.name,
            "reputation": result.get("score"),
            "details": result
        }
```

### 5.5 Kafka 主题与消息格式

#### 主题列表

| 主题名 | 生产者 | 消费者 | 说明 |
|--------|--------|--------|------|
| attack-events | data-ingestion | stream-processing | 原始攻击事件 |
| status-events | data-ingestion | stream-processing | 设备状态事件 |
| minute-aggregations | stream-processing | stream-processing | 1 分钟聚合数据（内部） |
| threat-alerts | stream-processing | threat-assessment, ml-detection | 威胁告警 |
| ml-threat-detections | ml-detection | threat-assessment | ML 检测结果 |

#### 消息格式规范

所有 Kafka 消息使用 camelCase JSON 格式，必须包含 `schemaVersion` 字段：

```json
{
  "schemaVersion": "1.0",
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "attackIp": "192.168.1.100",
  "responseIp": "10.0.0.1",
  "responsePort": 3306,
  "deviceSerial": "DEV-001",
  "timestamp": "2026-04-24T10:30:00Z"
}
```

#### 向后兼容

消费端使用 `@JsonAlias` 注解支持读取旧版 snake_case 消息。新服务只需产出 camelCase 格式即可。消费端的 `@JsonIgnoreProperties(ignoreUnknown = true)` 确保新增字段不会破坏旧消费者。

### 5.6 数据库扩展

#### 添加新表

1. 在 `docker/` 目录创建编号递增的 SQL 文件（如 `28-new-table.sql`）
2. 在 `docker-compose.yml` 的 postgres volumes 中挂载
3. 在 K8s 的 `postgres-schema-apply.yaml` 中添加

```sql
-- docker/28-new-table.sql
CREATE TABLE IF NOT EXISTS new_table (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id)
);
```

#### JPA 实体映射

Java 服务使用 Spring Data JPA：

```java
@Entity
@Table(name = "new_table")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;
    
    @Column(name = "created_at")
    private Instant createdAt;
}
```

### 5.7 前端扩展

#### 添加新页面

1. 在 `frontend/src/pages/` 创建新组件目录
2. 在路由配置中注册
3. 在导航菜单中添加入口

#### API 调用

使用 `frontend/src/services/api.ts` 中的封装：

```typescript
// api.ts 响应拦截器自动将 snake_case 转为 camelCase（安全网）
// 新 API 调用直接使用 camelCase 即可

const response = await api.get('/api/v1/new-endpoint', {
  params: { customerId: 'customer-001' }
});
```

### 5.8 编码规范

#### JSON 命名

全项目统一使用 **camelCase**：
- Java 服务：Jackson 默认序列化 camelCase（不需要额外配置）
- Python 服务：Pydantic 配置 `alias_generator=to_camel, populate_by_name=True`
- 前端：直接使用 camelCase
- Kafka 消息：camelCase + `schemaVersion` 字段

详细规范参见 `docs/design/PROJECT_STANDARDS.md`。

#### 多租户隔离

所有数据操作必须包含 `customerId`：
- Kafka 消息分区键：`customerId`
- Flink 分组键：`customerId:attackMac`
- 数据库查询：WHERE `customer_id = ?`
- 日志记录：MDC 包含 `customerId`

#### Java 编码

- 使用 Lombok (`@Data`, `@Builder`, `@Slf4j`)
- 使用 SLF4J 日志（异常必须记录 customerId 和错误详情）
- 异步处理使用 `CompletableFuture`
- 熔断保护使用 Resilience4j

#### Python 编码

- FastAPI + Pydantic 2.x
- 异步优先 (async/await)
- prometheus_client 暴露指标
- Pydantic model 使用 camelCase 别名

---

## 附录

### A. 端口分配表

| 端口 | 服务 | 协议 |
|------|------|------|
| 3000 | Frontend / Grafana | HTTP |
| 5000 | TIRE | HTTP |
| 5432 | PostgreSQL | TCP |
| 6379 | Redis | TCP |
| 8080 | Data Ingestion | HTTP |
| 8081 | Flink Web UI | HTTP |
| 8082 | Alert Management | HTTP |
| 8083 | Threat Assessment | HTTP |
| 8084 | Customer Management | HTTP |
| 8085 | Threat Intelligence | HTTP |
| 8086 | ML Detection | HTTP |
| 8888 | API Gateway | HTTP |
| 8899 | Config Server | HTTP |
| 9090 | Prometheus | HTTP |
| 9092 | Kafka | TCP |
| 9093 | Alertmanager | HTTP |
| 1883 | EMQX (MQTT) | TCP |
| 2181 | Zookeeper | TCP |
| 18083 | EMQX Dashboard | HTTP |
| 30080 | API Gateway (K8s NodePort) | HTTP |

### B. 环境变量参考

| 变量 | 说明 | 默认值 |
|------|------|--------|
| KAFKA_BOOTSTRAP_SERVERS | Kafka 连接地址 | localhost:9092 |
| SPRING_PROFILES_ACTIVE | Spring 激活配置 | development |
| SPRING_DATASOURCE_URL | 数据库连接 URL | jdbc:postgresql://localhost:5432/threat_detection |
| SPRING_DATASOURCE_USERNAME | 数据库用户名 | threat_user |
| SPRING_DATASOURCE_PASSWORD | 数据库密码 | threat_password |
| MQTT_ENABLED | 启用 V2 MQTT 摄取 | false |
| MQTT_BROKER_URL | EMQX Broker 地址 | tcp://emqx:1883 |
| NET_WEIGHT_SERVICE_URL | 网段权重服务地址 | http://customer-management:8084 |
| THREAT_INTEL_SERVICE_URL | 威胁情报服务地址 | http://threat-intelligence:8085 |
| TIER1_WINDOW_SECONDS | Tier 1 时间窗口 | 30 |
| TIER2_WINDOW_SECONDS | Tier 2 时间窗口 | 300 |
| TIER3_WINDOW_SECONDS | Tier 3 时间窗口 | 900 |
| ENCRYPT_KEY | Config Server 加密密钥 | - |
| SLACK_WEBHOOK_URL | Slack 通知 Webhook | - |
| TEAMS_WEBHOOK_URL | Teams 通知 Webhook | - |
| SMS_PROVIDER | SMS 服务提供商 | twilio |

### C. 常见问题速查

**Q: 代码修改后服务行为没有变化？**
A: Docker 容器不会自动加载新代码，必须重新构建镜像：`mvn clean package -DskipTests && cd docker && docker compose build --no-cache <service> && docker compose up -d <service>`

**Q: 如何查看 Kafka 主题数据？**
A: `docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic attack-events --from-beginning --max-messages 5`

**Q: 如何重置 Kafka 消费者 offset？**
A: `docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group <group-id> --topic <topic> --reset-offsets --to-earliest --execute`

**Q: Flink Job 状态如何查看？**
A: 访问 Flink Web UI `http://localhost:8081`，查看 Running Jobs 列表和各 TaskManager 状态。

**Q: 如何添加新的 Kafka 主题？**
A: 修改 `k8s/base/kafka-topic-init.yaml` 中的初始化脚本，或在 Docker 环境中执行 `docker exec -it kafka kafka-topics --create --bootstrap-server localhost:9092 --topic <name> --partitions 3 --replication-factor 1`

**Q: 如何配置 TIRE 插件的 API 密钥？**
A: 通过前端 Settings 页面配置，或通过 `k8s/base/tire-secret.yaml` 管理 Kubernetes Secret。

**Q: PostgreSQL 连接数不足怎么办？**
A: 调整 PostgreSQL 配置 `max_connections` 参数，或在各服务配置中降低连接池大小 (`spring.datasource.hikari.maximum-pool-size`)。

**Q: 如何在不影响生产的情况下测试流水线？**
A: 使用回归测试工具的干运行模式：`python scripts/regression/replay_harness.py --dry-run`

---

*本手册基于威胁检测系统 v3.0 编写*  
*最后更新: 2026-04-24*
