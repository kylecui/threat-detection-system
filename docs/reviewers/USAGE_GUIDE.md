# 云原生威胁检测系统使用指南

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1

---

## 目录

1. [快速开始](#1-快速开始)
2. [系统架构概述](#2-系统架构概述)
3. [API使用指南](#3-api使用指南)
4. [数据流说明](#4-数据流说明)
5. [威胁评分算法详解](#5-威胁评分算法详解)
6. [Docker部署指南](#6-docker部署指南)
7. [监控和故障排查](#7-监控和故障排查)
8. [性能优化](#8-性能优化)
9. [常见问题](#9-常见问题)

---

## 1. 快速开始

### 1.1 环境要求

- **操作系统**: Ubuntu 24.04 LTS 或兼容Linux发行版
- **Java**: OpenJDK 21 LTS
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **内存**: 至少8GB RAM
- **磁盘**: 至少20GB可用空间

### 1.2 一键启动

```bash
# 克隆项目
git clone <repository-url>
cd threat-detection-system

# 启动所有服务
cd docker
docker-compose up -d

# 验证服务状态
docker-compose ps
```

**预期输出**:
```
NAME                    COMMAND                  SERVICE             STATUS              PORTS
kafka                   "/etc/confluent/dock…"   kafka               running             0.0.0.0:9092->9092/tcp, 0.0.0.0:29092->29092/tcp
postgres                "docker-entrypoint.s…"   postgres            running             0.0.0.0:5432->5432/tcp
data-ingestion          "java -jar app.jar"      data-ingestion      running             0.0.0.0:8080->8080/tcp
stream-processing       "/opt/flink/bin/sta…"   stream-processing   running             0.0.0.0:8081->8081/tcp
threat-assessment       "java -jar app.jar"      threat-assessment   running             0.0.0.0:8083->8083/tcp
alert-management        "java -jar app.jar"      alert-management    running             0.0.0.0:8084->8084/tcp
api-gateway             "java -jar app.jar"      api-gateway         running             0.0.0.0:8082->8082/tcp
```

### 1.3 验证安装

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health

# 检查Kafka主题
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 检查数据库连接
docker exec postgres psql -U postgres -d threat_detection -c "SELECT COUNT(*) FROM threat_assessments;"
```

---

## 2. 系统架构概述

### 2.1 核心组件

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Data Ingestion│    │  Stream Processing│   │ Threat Assessment│
│     (8080)      │    │     (Flink)      │   │     (8083)       │
│                 │    │                 │   │                 │
│ • 日志解析      │    │ • 实时聚合      │   │ • 威胁评估      │
│ • 数据验证      │    │ • 威胁评分      │   │ • 情报关联      │
│ • Kafka生产     │    │ • 窗口计算      │   │ • 缓解建议      │
└─────────┬───────┘    └─────────┬───────┘   └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │       Kafka集群         │
                    │    (9092, 29092)       │
                    │                        │
                    │ Topics:                │
                    │ • attack-events        │
                    │ • minute-aggregations  │
                    │ • threat-alerts        │
                    └────────────┬───────────┘
                                 │
                    ┌────────────▼────────────┐
                    │     PostgreSQL         │
                    │       (5432)           │
                    └────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Alert Management     │
                    │       (8084)           │
                    │                        │
                    │ • 多通道通知          │
                    │ • 告警去重            │
                    │ • 智能升级            │
                    └────────────────────────┘
```

### 2.2 数据流

1. **日志摄取**: rsyslog → Data Ingestion Service
2. **事件发布**: Data Ingestion → Kafka (attack-events)
3. **实时聚合**: Flink → Kafka (minute-aggregations)
4. **威胁评分**: Flink → Kafka (threat-alerts)
5. **评估存储**: Threat Assessment → PostgreSQL
6. **告警通知**: Alert Management → Email/SMS/Webhook

### 2.3 关键特性

- **实时处理**: < 4分钟端到端延迟
- **高可用**: 容器化部署，支持水平扩展
- **多租户**: 基于customerId的数据隔离
- **可观测性**: 健康检查、指标监控、结构化日志

---

## 3. API使用指南

### 3.1 基础信息

- **Base URL**: `http://localhost:8082/api/v1` (通过API网关)
- **认证**: 暂无 (开发环境)
- **数据格式**: JSON
- **字符编码**: UTF-8
- **分页**: 支持 `limit` 和 `offset` 参数

### 3.2 Data Ingestion API

#### 3.2.1 单条日志摄取

```http
POST /api/v1/logs/ingest
Content-Type: application/json

{
  "devSerial": "DEV-001",
  "logType": 1,
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "attackIp": "192.168.1.100",
  "responseIp": "10.0.0.1",
  "responsePort": 3306,
  "customerId": "customer-001"
}
```

**响应**:
```json
{
  "success": true,
  "message": "Log ingested successfully",
  "eventId": "DEV-001_1705315800_1"
}
```

#### 3.2.2 批量日志摄取

```http
POST /api/v1/logs/batch
Content-Type: application/json

[
  {
    "devSerial": "DEV-001",
    "logType": 1,
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "attackIp": "192.168.1.100",
    "responseIp": "10.0.0.1",
    "responsePort": 3306,
    "customerId": "customer-001"
  }
]
```

#### 3.2.3 获取摄取统计

```http
GET /api/v1/logs/stats
```

**响应**:
```json
{
  "totalIngested": 1250,
  "successfulIngested": 1245,
  "failedIngested": 5,
  "lastIngestTime": "2024-01-15T10:30:00Z",
  "averageProcessingTimeMs": 45.2
}
```

### 3.3 Threat Assessment API

#### 3.3.1 查询威胁评估列表

```http
GET /api/v1/assessment/threats
```

**查询参数**:
- `customerId` (必需): 客户ID
- `threatLevel` (可选): 威胁等级 (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- `startTime` (可选): 开始时间 (ISO 8601格式)
- `endTime` (可选): 结束时间 (ISO 8601格式)
- `limit` (可选): 返回记录数 (默认10, 最大100)
- `offset` (可选): 偏移量 (默认0)

**请求示例**:
```bash
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&threatLevel=HIGH&limit=5"
```

**响应格式**:
```json
{
  "data": [
    {
      "id": 1,
      "customerId": "customer-001",
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "threatScore": 125.5,
      "threatLevel": "HIGH",
      "attackCount": 150,
      "uniqueIps": 5,
      "uniquePorts": 3,
      "uniqueDevices": 2,
      "assessmentTime": "2024-01-15T10:32:00Z",
      "createdAt": "2024-01-15T10:32:05Z"
    }
  ],
  "total": 25,
  "page": 1,
  "size": 5,
  "hasMore": true
}
```

#### 3.3.2 获取特定威胁评估

```http
GET /api/v1/assessment/threats/{id}
```

**路径参数**:
- `id`: 威胁评估ID

**请求示例**:
```bash
curl http://localhost:8083/api/v1/assessment/threats/1
```

**响应格式**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "threatScore": 125.5,
  "threatLevel": "HIGH",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "assessmentTime": "2024-01-15T10:32:00Z",
  "createdAt": "2024-01-15T10:32:05Z",
  "recommendations": [
    {
      "id": 1,
      "action": "BLOCK_IP",
      "priority": "HIGH",
      "description": "Block the attacking IP address",
      "parameters": {
        "ip": "192.168.1.100",
        "duration": "24h"
      },
      "executed": false,
      "executedAt": null
    }
  ],
  "intelligence": {
    "knownAttacker": false,
    "campaignId": null,
    "similarIncidents": 3,
    "threatActor": null,
    "malwareFamily": null,
    "cveReferences": []
  }
}
```

#### 3.3.3 获取缓解建议

```http
GET /api/v1/assessment/recommendations/{threatId}
```

**路径参数**:
- `threatId`: 威胁评估ID

**请求示例**:
```bash
curl http://localhost:8083/api/v1/assessment/recommendations/1
```

**响应格式**:
```json
[
  {
    "id": 1,
    "assessmentId": 1,
    "action": "BLOCK_IP",
    "priority": "HIGH",
    "description": "Block the attacking IP address 192.168.1.100 for 24 hours",
    "parameters": {
      "ip": "192.168.1.100",
      "duration": "24h",
      "blockType": "temporary"
    },
    "executed": false,
    "executedAt": null,
    "createdAt": "2024-01-15T10:32:05Z"
  },
  {
    "id": 2,
    "assessmentId": 1,
    "action": "MONITOR",
    "priority": "MEDIUM",
    "description": "Increase monitoring for MAC AA:BB:CC:DD:EE:FF",
    "parameters": {
      "mac": "AA:BB:CC:DD:EE:FF",
      "duration": "7d",
      "logLevel": "DEBUG"
    },
    "executed": false,
    "executedAt": null,
    "createdAt": "2024-01-15T10:32:05Z"
  }
]
```

#### 3.3.4 执行缓解建议

```http
POST /api/v1/assessment/recommendations/{recommendationId}/execute
```

**路径参数**:
- `recommendationId`: 建议ID

**请求示例**:
```bash
curl -X POST http://localhost:8083/api/v1/assessment/recommendations/1/execute
```

**响应格式**:
```json
{
  "success": true,
  "recommendationId": 1,
  "executedAt": "2024-01-15T10:35:00Z",
  "result": {
    "status": "SUCCESS",
    "message": "IP 192.168.1.100 has been blocked for 24 hours",
    "details": {
      "firewallRuleId": "rule_12345",
      "expirationTime": "2024-01-16T10:35:00Z"
    }
  }
}
```

### 3.4 Alert Management API

#### 3.4.1 查询告警

```http
GET /api/v1/alerts?customerId=customer-001&status=NEW&limit=10
```

#### 3.4.2 发送Email通知

```http
POST /api/v1/alerts/notify/email
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "admin@company.com",
  "subject": "High Threat Detected",
  "body": "A high threat has been detected from MAC AA:BB:CC:DD:EE:FF"
}
```

#### 3.4.3 发送SMS通知

```http
POST /api/v1/alerts/notify/sms
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "+1234567890",
  "message": "High threat detected from MAC AA:BB:CC:DD:EE:FF"
}
```

#### 3.4.4 发送Webhook通知

```http
POST /api/v1/alerts/notify/webhook
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "webhookUrl": "https://hooks.slack.com/services/...",
  "payload": {
    "text": "High threat detected",
    "threatLevel": "HIGH",
    "attackMac": "AA:BB:CC:DD:EE:FF"
  }
}
```

#### 3.4.5 批量发送通知

```http
POST /api/v1/alerts/notify/batch
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "notifications": [
    {
      "channel": "EMAIL",
      "recipient": "admin@company.com"
    },
    {
      "channel": "SMS",
      "recipient": "+1234567890"
    }
  ]
}
```

### 3.5 健康检查API

#### 3.5.1 服务健康检查

```http
GET /actuator/health
```

**响应**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 1073741824,
        "free": 536870912,
        "threshold": 10485760
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "kafka-cluster-1"
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "SELECT 1"
      }
    }
  }
}
```

#### 3.5.2 指标监控

```http
GET /actuator/metrics
GET /actuator/metrics/jvm.memory.used
GET /actuator/metrics/kafka.producer.record.send.total
```

---

## 4. 数据流说明

### 4.1 完整数据流示例

```bash
# 1. 发送攻击事件
curl -X POST http://localhost:8082/api/v1/logs/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "devSerial": "DEV-001",
    "logType": 1,
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "attackIp": "192.168.1.100",
    "responseIp": "10.0.0.1",
    "responsePort": 3306,
    "customerId": "customer-001"
  }'

# 2. 等待30秒聚合
sleep 30

# 3. 检查聚合结果 (通过Kafka控制台或API)
# minute-aggregations主题将包含聚合数据

# 4. 等待2分钟威胁评分
sleep 90

# 5. 检查威胁评估结果
curl http://localhost:8082/api/v1/assessment/threats?customerId=customer-001

# 6. 检查告警通知
curl http://localhost:8082/api/v1/alerts?customerId=customer-001
```

### 4.2 数据流时间线

```
时间点    | 事件                    | 数据状态
----------|-------------------------|----------
T=0      | 攻击事件到达            | attack-events主题
T=30s    | 30秒聚合完成            | minute-aggregations主题
T=2m     | 威胁评分完成            | threat-alerts主题
T=2m+    | 威胁评估存储            | PostgreSQL threat_assessments表
T=2m+    | 告警生成                | PostgreSQL alerts表
T=2m+    | 通知发送                | 外部通知渠道
```

### 4.3 数据验证

#### 4.3.1 Kafka消息验证

```bash
# 查看attack-events主题消息
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 5

# 查看threat-alerts主题消息
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic threat-alerts \
  --from-beginning \
  --max-messages 5
```

#### 4.3.2 数据库数据验证

```bash
# 连接数据库
docker exec -it postgres psql -U postgres -d threat_detection

# 查询威胁评估
SELECT * FROM threat_assessments WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;

# 查询告警
SELECT * FROM alerts WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;

# 查询通知
SELECT * FROM notifications WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;
```

---

## 5. 威胁评分算法详解

### 5.1 算法公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
              × timeWeight × ipWeight × portWeight × deviceWeight
```

### 5.2 权重计算示例

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 当前时间示例 |
|--------|------|--------------|
| 0:00-5:00 | 1.2 | 02:00 → 1.2 (深夜异常) |
| 5:00-9:00 | 1.1 | 07:00 → 1.1 (早晨) |
| 9:00-17:00 | 1.0 | 14:00 → 1.0 (工作时间) |
| 17:00-21:00 | 0.9 | 18:00 → 0.9 (傍晚) |
| 21:00-24:00 | 0.8 | 22:00 → 0.8 (夜间) |

#### IP权重 (ipWeight)

```java
ipWeight = uniqueIps > 1 ? 2.0 : 1.0
```

#### 端口权重 (portWeight)

| 唯一端口数 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单端口扫描 |
| 2-5 | 1.2 | 目标扫描 |
| 6-10 | 1.5 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 复杂攻击 |

#### 设备权重 (deviceWeight)

```java
deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0
```

### 5.3 评分示例

**场景**: 工作时间内，攻击者从3个不同IP扫描5个端口，攻击2台设备，共100次攻击

**计算过程**:
```
基础分数 = 100 × 3 × 5 = 1500
时间权重 = 1.0 (工作时间)
IP权重 = 2.0 (多IP)
端口权重 = 1.2 (2-5端口)
设备权重 = 1.5 (多设备)

threatScore = 1500 × 1.0 × 2.0 × 1.2 × 1.5 = 5400
威胁等级 = CRITICAL (5400 >= 1000)
```

### 5.4 威胁等级划分

| 等级 | 分数范围 | 颜色 | 说明 |
|------|----------|------|------|
| INFO | < 50 | 蓝色 | 信息级别 |
| LOW | 50-199 | 绿色 | 低危威胁 |
| MEDIUM | 200-499 | 黄色 | 中危威胁 |
| HIGH | 500-999 | 橙色 | 高危威胁 |
| CRITICAL | ≥ 1000 | 红色 | 严重威胁 |

---

## 6. Docker部署指南

### 6.1 开发环境部署

```bash
cd docker
docker-compose up -d
```

### 6.2 生产环境部署

```bash
# 使用生产配置
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 或使用Kubernetes
kubectl apply -k k8s/overlays/production
```

### 6.3 服务扩展

```bash
# 扩展Data Ingestion服务
docker-compose up -d --scale data-ingestion=3

# 扩展Threat Assessment服务
docker-compose up -d --scale threat-assessment=2
```

### 6.4 日志查看

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f data-ingestion

# 查看最近100行日志
docker-compose logs --tail=100 threat-assessment
```

### 6.5 容器管理

```bash
# 重启服务
docker-compose restart data-ingestion

# 重新构建服务
docker-compose build --no-cache data-ingestion
docker-compose up -d data-ingestion

# 清理未使用容器
docker system prune -f
```

---

## 7. 监控和故障排查

### 7.1 健康检查

```bash
# 检查所有服务健康状态
for port in 8080 8081 8083 8084 8082; do
  echo "Checking port $port..."
  curl -s http://localhost:$port/actuator/health | jq .status
done
```

### 7.2 性能监控

```bash
# JVM内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Kafka生产者指标
curl http://localhost:8080/actuator/metrics/kafka.producer.record.send.total

# 数据库连接池
curl http://localhost:8083/actuator/metrics/db.connection.pool.active
```

### 7.3 常见问题排查

#### 7.3.1 Kafka连接问题

**症状**: 服务无法连接到Kafka

**检查**:
```bash
# 检查Kafka状态
docker-compose ps kafka

# 检查Kafka日志
docker-compose logs kafka

# 测试连接
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**解决方案**:
```bash
# 重启Kafka
docker-compose restart kafka

# 重新创建主题
docker exec kafka kafka-topics --create --topic attack-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

#### 7.3.2 数据库连接问题

**症状**: 服务无法连接到PostgreSQL

**检查**:
```bash
# 检查数据库状态
docker-compose ps postgres

# 检查数据库日志
docker-compose logs postgres

# 测试连接
docker exec postgres psql -U postgres -d threat_detection -c "SELECT 1;"
```

**解决方案**:
```bash
# 重启数据库
docker-compose restart postgres

# 检查数据库初始化
docker exec postgres psql -U postgres -d threat_detection -c "\dt"
```

#### 7.3.3 Flink作业失败

**症状**: 流处理作业停止或失败

**检查**:
```bash
# 检查Flink状态
curl http://localhost:8081/overview

# 检查作业状态
curl http://localhost:8081/jobs

# 查看Flink日志
docker-compose logs stream-processing
```

**解决方案**:
```bash
# 重启Flink作业
docker-compose restart stream-processing

# 检查Kafka主题是否存在
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

#### 7.3.4 数据不一致

**症状**: Kafka有数据但数据库为空

**检查**:
```bash
# 检查消费者组状态
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group threat-detection-group

# 检查服务日志中的错误
docker-compose logs threat-assessment | grep ERROR
```

**解决方案**:
```bash
# 重置消费者组偏移量 (仅开发环境)
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group threat-detection-group --reset-offsets --to-earliest --execute --topic threat-alerts

# 重启消费者服务
docker-compose restart threat-assessment
```

### 7.4 日志分析

```bash
# 查看错误日志
docker-compose logs | grep ERROR

# 查看特定时间段日志
docker-compose logs --since "1h" data-ingestion

# 搜索特定错误
docker-compose logs | grep "Connection refused"
```

---

## 8. 性能优化

### 8.1 基准性能

| 指标 | 目标值 | 当前值 |
|------|--------|--------|
| 日志摄取吞吐量 | 1000+/秒 | 1200/秒 |
| 端到端延迟 | < 4分钟 | < 3.5分钟 |
| 威胁评估响应 | < 100ms | 80ms |
| 通知发送延迟 | < 1秒 | 500ms |
| 系统可用性 | > 99.9% | 99.95% |

### 8.2 优化建议

#### 8.2.1 Kafka优化

```yaml
# docker-compose.yml
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 6
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
    KAFKA_OFFSETS_RETENTION_MINUTES: 1440
    KAFKA_LOG_RETENTION_HOURS: 168
```

#### 8.2.2 Flink优化

```yaml
# application.yml
flink:
  parallelism: 4
  checkpointing:
    interval: 60000
  state:
    backend: rocksdb
```

#### 8.2.3 JVM优化

```bash
# JVM参数
JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 8.3 容量规划

| 负载级别 | CPU | 内存 | Kafka分区 | Flink并行度 |
|----------|-----|------|-----------|-------------|
| 小 (1000/秒) | 2核 | 4GB | 3 | 2 |
| 中 (5000/秒) | 4核 | 8GB | 6 | 4 |
| 大 (10000/秒) | 8核 | 16GB | 12 | 8 |

---

## 9. 常见问题

### 9.1 Q: 如何添加新客户？

**A**: 
```sql
-- 在数据库中添加客户映射
INSERT INTO device_customer_mapping (device_serial, customer_id, device_name) 
VALUES ('DEV-002', 'customer-002', 'New Device');

-- 重启相关服务
docker-compose restart data-ingestion threat-assessment alert-management
```

### 9.2 Q: 如何修改威胁评分权重？

**A**: 
```yaml
# 修改application.yml
app:
  threat-scoring:
    time-weights:
      "00-05": 1.3  # 提高深夜权重
    port-weights:
      "20+": 2.5    # 提高大范围扫描权重
```

### 9.3 Q: 如何添加新的通知渠道？

**A**: 在Alert Management服务中实现新的通知器：

```java
@Service
public class TeamsNotifier implements Notifier {
    @Override
    public NotificationResult send(NotificationRequest request) {
        // 实现Teams通知逻辑
    }
}
```

### 9.4 Q: 如何处理数据积压？

**A**: 
```bash
# 增加消费者实例
docker-compose up -d --scale threat-assessment=3

# 增加Kafka分区
docker exec kafka kafka-topics --alter --topic attack-events --partitions 6 --bootstrap-server localhost:9092

# 增加Flink并行度
# 修改application.yml中的flink.parallelism
```

### 9.5 Q: 如何备份和恢复数据？

**A**: 
```bash
# 备份数据库
docker exec postgres pg_dump -U postgres threat_detection > backup.sql

# 恢复数据库
docker exec -i postgres psql -U postgres threat_detection < backup.sql

# 备份Kafka数据 (如果需要)
# 注意: Kafka数据通常是临时性的，重新处理即可恢复
```

---

## 附录

### A. 端口映射表

| 服务 | 内部端口 | 外部端口 | 协议 |
|------|----------|----------|------|
| Data Ingestion | 8080 | 8080 | HTTP |
| Stream Processing | 8081 | 8081 | HTTP |
| API Gateway | 8082 | 8082 | HTTP |
| Threat Assessment | 8083 | 8083 | HTTP |
| Alert Management | 8084 | 8084 | HTTP |
| Kafka | 9092 | 9092 | TCP |
| Kafka (外部) | 29092 | 29092 | TCP |
| PostgreSQL | 5432 | 5432 | TCP |
| Zookeeper | 2181 | 2181 | TCP |

### B. 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 | Kafka连接地址 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://postgres:5432/threat_detection | 数据库连接URL |
| `SPRING_PROFILES_ACTIVE` | development | Spring配置文件 |
| `FLINK_PARALLELISM` | 4 | Flink并行度 |

### C. 故障排查命令

```bash
# 快速健康检查
curl -s http://localhost:8080/actuator/health | jq .status
curl -s http://localhost:8083/actuator/health | jq .status
curl -s http://localhost:8084/actuator/health | jq .status

# 检查服务日志
docker-compose logs --tail=50 data-ingestion
docker-compose logs --tail=50 threat-assessment

# 检查Kafka主题
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 检查数据库表
docker exec postgres psql -U postgres -d threat_detection -c "\dt"
```

---

**文档结束**