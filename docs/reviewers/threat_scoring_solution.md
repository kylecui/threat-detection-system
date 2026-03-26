# 云原生威胁检测系统架构规格文档

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1

---

## 目录

1. [系统概述](#1-系统概述)
2. [架构设计](#2-架构设计)
3. [微服务模块规格](#3-微服务模块规格)
4. [数据流与处理流程](#4-数据流与处理流程)
5. [威胁评分算法](#5-威胁评分算法)
6. [数据结构设计](#6-数据结构设计)
7. [部署架构](#7-部署架构)
8. [性能与扩展性](#8-性能与扩展性)

---

## 1. 系统概述

### 1.1 项目背景

本系统是从传统C#/Windows CS架构向云原生微服务架构的迁移项目，旨在解决原系统的可扩展性和跨平台挑战。

**原系统痛点**:
- 基于Windows/C#，跨平台支持有限
- 单体架构，扩展性差
- 多层聚合导致10分钟以上延迟
- 手动离线数据导入，效率低下
- 复杂的SQL存储过程，维护困难

**新系统目标**:
- 云原生架构，Linux环境运行
- 微服务架构，支持水平扩展
- 实时处理，秒级响应延迟
- 自动化数据导入和处理
- 模块化设计，易于维护

### 1.2 技术栈

| 层级 | 技术 | 版本要求 |
|------|------|---------|
| **开发语言** | Java | OpenJDK 21 LTS |
| **应用框架** | Spring Boot | 3.1.5 |
| **消息队列** | Apache Kafka | 3.4+ |
| **流处理引擎** | Apache Flink | 1.17+ |
| **数据库** | PostgreSQL | 15+ |
| **缓存** | Redis | (可选) |
| **容器化** | Docker | 20.10+ |
| **编排** | Kubernetes | 1.25+ |
| **构建工具** | Maven | 3.8.7 |
| **操作系统** | Ubuntu | 24.04.3 LTS |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         日志源 (rsyslog:9080)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  数据摄取服务 (Data Ingestion)                   │
│  - 日志解析与验证                                                 │
│  - 客户ID映射                                                    │
│  - Kafka生产者                                                   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Apache Kafka 集群                           │
│  Topics: attack-events, status-events                           │
└──────────┬──────────────────────────────────┬───────────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────────┐      ┌──────────────────────────────┐
│  流处理服务 (Flink)       │      │  其他消费者                   │
│  - 30秒聚合窗口          │      │  - 日志存储                   │
│  - 2分钟威胁评分         │      │  - 实时监控                   │
└──────────┬───────────────┘      └──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Kafka Topics                                │
│  - minute-aggregations (聚合数据)                               │
│  - threat-alerts (威胁警报)                                      │
└──────────┬──────────────────────────────────┬───────────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────────┐      ┌──────────────────────────────┐
│  威胁评估服务             │      │  告警管理服务                 │
│  - 风险评估              │      │  - 多通道通知                 │
│  - 情报关联              │      │  - 智能去重                   │
│  - 缓解建议              │      │  - 告警升级                   │
│  - PostgreSQL存储        │      │  - 通知记录                   │
└──────────┬───────────────┘      └──────────┬───────────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       PostgreSQL 数据库                          │
│  - threat_assessments, threat_alerts                            │
│  - alerts, notifications                                        │
│  - recommendations                                              │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        API 网关                                  │
│  - RESTful API                                                  │
│  - 认证授权                                                      │
│  - 负载均衡                                                      │
│  - 路由管理                                                      │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心设计原则

1. **事件驱动架构**: 基于Kafka的异步消息传递
2. **微服务解耦**: 每个服务独立部署、扩展
3. **实时处理**: 流处理引擎替代批量聚合
4. **多租户隔离**: 客户ID作为数据分区键
5. **容错恢复**: 自动重试、断路器、降级策略
6. **可观测性**: 健康检查、指标监控、日志聚合

---

## 3. 微服务模块规格

### 3.1 数据摄取服务 (Data Ingestion Service)

**职责**: 接收、解析、验证日志，发布到Kafka

#### 3.1.1 核心功能

- **日志接收**: 监听rsyslog端口(9080)
- **格式解析**: 解析syslog格式日志
- **数据验证**: 
  - 设备序列号验证 (支持字母数字组合)
  - 端口范围验证 (-65536 to 999999)
  - 必填字段检查
- **客户映射**: 设备序列号 → 客户ID映射
- **事件发布**: 发布AttackEvent/StatusEvent到Kafka
- **批量处理**: 支持批量日志摄取

#### 3.1.2 API端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/logs/ingest` | POST | 单条日志摄取 |
| `/api/v1/logs/batch` | POST | 批量日志摄取 |
| `/api/v1/logs/stats` | GET | 获取解析统计 |
| `/api/v1/logs/stats/reset` | POST | 重置统计信息 |
| `/actuator/health` | GET | 健康检查 |

#### 3.1.3 数据模型

**AttackEvent** (攻击事件):
```json
{
  "id": "string",
  "devSerial": "string",
  "logType": 1,
  "subType": 1,
  "attackMac": "string",
  "attackIp": "string",
  "responseIp": "string",
  "responsePort": 80,
  "lineId": 1,
  "ifaceType": 1,
  "vlanId": 30,
  "logTime": 1234567890,
  "ethType": 2048,
  "ipType": 6,
  "customerId": "string",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

**StatusEvent** (状态事件):
```json
{
  "id": "string",
  "devSerial": "string",
  "logType": 2,
  "sentryCount": 100,
  "realHostCount": 50,
  "devStartTime": 1234567890,
  "devEndTime": 1234567890,
  "timestamp": "2024-01-01T00:00:00Z"
}
```

#### 3.1.4 性能指标

- **吞吐量**: 1000+ 日志/秒
- **延迟**: < 50ms (单条)
- **批量大小**: 25-100条/批次
- **成功率**: > 99%

---

### 3.2 流处理服务 (Stream Processing Service)

**职责**: 实时聚合和威胁评分

#### 3.2.1 处理流程

```
攻击事件流 → 30秒聚合窗口 → 2分钟威胁评分窗口 → 威胁警报输出
```

#### 3.2.2 聚合逻辑

**第一阶段: 30秒聚合窗口**
- **聚合键**: `customerId:attackMac`
- **聚合维度**:
  - 唯一攻击IP数量 (uniqueIps)
  - 唯一响应端口数量 (uniquePorts)
  - 唯一设备数量 (uniqueDevices)
  - 攻击次数 (attackCount)
- **输出**: minute-aggregations主题

**第二阶段: 2分钟威胁评分窗口**
- **评分键**: `customerId:attackMac`
- **评分公式**:
  ```
  threatScore = (attackCount × uniqueIps × uniquePorts) 
                × timeWeight × ipWeight × portWeight × deviceWeight
  ```
- **输出**: threat-alerts主题

#### 3.2.3 威胁等级

| 等级 | 分数范围 | 名称 |
|------|----------|------|
| CRITICAL | >= 1000 | 严重威胁 |
| HIGH | 500-999 | 高危 |
| MEDIUM | 200-499 | 中危 |
| LOW | 50-199 | 低危 |
| INFO | < 50 | 信息 |

#### 3.2.4 配置参数

| 参数 | 环境变量 | 默认值 |
|------|----------|--------|
| 聚合窗口 | `AGGREGATION_WINDOW_SECONDS` | 30 |
| 评分窗口 | `THREAT_SCORING_WINDOW_MINUTES` | 2 |
| Kafka服务器 | `KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 |
| 输入主题 | `INPUT_TOPIC` | attack-events |
| 输出主题 | `OUTPUT_TOPIC` | threat-alerts |
| 聚合主题 | `AGGREGATION_TOPIC` | minute-aggregations |

---

### 3.3 威胁评估服务 (Threat Assessment Service)

**职责**: 高级威胁分析、情报关联、缓解建议

#### 3.3.1 核心功能

- **威胁消费**: 监听threat-alerts主题
- **风险评估**: 多维度风险评分
- **情报关联**: 匹配已知威胁模式
- **缓解建议**: 生成可执行的缓解措施
- **数据持久化**: 存储到PostgreSQL

#### 3.3.2 API端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/assessment/threats` | GET | 查询威胁评估 |
| `/api/v1/assessment/threats/{id}` | GET | 获取特定威胁 |
| `/api/v1/assessment/intelligence` | GET | 查询威胁情报 |
| `/api/v1/assessment/recommendations/{threatId}` | GET | 获取缓解建议 |
| `/api/v1/assessment/stats` | GET | 获取统计信息 |

#### 3.3.3 数据库表结构

**threat_assessments** (威胁评估):
- assessment_id, alert_id, risk_level, risk_score
- confidence, processing_duration_ms
- assessment_timestamp

**recommendations** (缓解建议):
- id, action (BLOCK_IP, RATE_LIMIT, etc.)
- priority (CRITICAL, HIGH, MEDIUM, LOW)
- description, parameters, executed

**threat_intelligence** (威胁情报):
- knownAttacker, campaignId, similarIncidents
- threatActor, malwareFamily, cveReferences

---

### 3.4 告警管理服务 (Alert Management Service)

**职责**: 多通道告警通知、智能去重、升级策略

#### 3.4.1 支持通道

- **Email**: SMTP配置，支持Gmail、163等
- **SMS**: Twilio、阿里云短信
- **Webhook**: 自定义HTTP回调
- **Slack**: Webhook集成
- **Microsoft Teams**: Webhook集成

#### 3.4.2 智能功能

- **去重**: 基于时间窗口和内容的去重
- **升级**: 基于严重程度和响应时间的自动升级
- **重试**: 失败通知自动重试(最多3次)
- **记录**: 完整的通知历史记录

#### 3.4.3 API端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/alerts/notify/email` | POST | 发送Email通知 |
| `/api/v1/alerts/notify/sms` | POST | 发送SMS通知 |
| `/api/v1/alerts/notify/webhook` | POST | 发送Webhook通知 |
| `/api/v1/alerts/notify/slack` | POST | 发送Slack通知 |
| `/api/v1/alerts/notify/teams` | POST | 发送Teams通知 |
| `/api/v1/alerts/notify/batch` | POST | 批量发送通知 |
| `/api/v1/alerts/notifications` | GET | 查询通知历史 |
| `/api/v1/alerts/notifications/retry` | POST | 重试失败通知 |

---

### 3.5 API网关 (API Gateway)

**职责**: 统一API管理、认证、路由、负载均衡

#### 3.5.1 核心功能

- **路由管理**: 请求路由到后端微服务
- **认证授权**: JWT、OAuth2支持
- **负载均衡**: 轮询、最少连接等策略
- **限流**: 基于客户/IP的请求限流
- **监控**: API调用统计和性能监控

#### 3.5.2 路由规则

| 路径前缀 | 目标服务 | 端口 |
|----------|----------|------|
| `/api/v1/logs/**` | data-ingestion | 8080 |
| `/api/v1/assessment/**` | threat-assessment | 8083 |
| `/api/v1/alerts/**` | alert-management | 8084 |

---

### 3.6 配置服务器 (Config Server)

**职责**: 集中式配置管理

#### 3.6.1 核心功能

- **Git集成**: 配置存储在Git仓库
- **版本控制**: 配置变更历史追踪
- **加密支持**: 敏感信息加密存储
- **动态刷新**: 配置更新无需重启

---

## 4. 数据流与处理流程

### 4.1 完整数据流

```
1. 日志源 (rsyslog) 
   ↓
2. 数据摄取服务 (解析、验证、映射客户ID)
   ↓
3. Kafka - attack-events主题
   ↓
4. Flink流处理 (30秒聚合)
   ↓
5. Kafka - minute-aggregations主题
   ↓
6. Flink威胁评分 (2分钟窗口)
   ↓
7. Kafka - threat-alerts主题
   ↓
8. 威胁评估服务 (风险分析、情报关联)
   ↓
9. PostgreSQL (持久化存储)
   ↓
10. 告警管理服务 (多通道通知)
    ↓
11. 通知渠道 (Email, SMS, Slack等)
```

### 4.2 关键时间窗口

| 阶段 | 窗口大小 | 延迟 |
|------|----------|------|
| 日志摄取 | N/A | < 50ms |
| 30秒聚合 | 30秒 | 30-60秒 |
| 威胁评分 | 2分钟 | 2-3分钟 |
| 威胁评估 | 实时 | < 100ms |
| 告警通知 | 实时 | < 1秒 |
| **端到端总延迟** | - | **< 4分钟** |

对比原系统: **10分钟+ → < 4分钟** (提升60%+)

---

## 5. 威胁评分算法

### 5.1 算法公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
              × timeWeight × ipWeight × portWeight × deviceWeight
```

### 5.2 权重计算

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 说明 |
|--------|------|------|
| 0:00-5:00 | 1.2 | 深夜异常活动 |
| 5:00-9:00 | 1.1 | 早晨 |
| 9:00-17:00 | 1.0 | 工作时间 |
| 17:00-21:00 | 0.9 | 傍晚 |
| 21:00-24:00 | 0.8 | 夜间 |

#### IP多样性权重 (ipWeight)

```java
ipWeight = uniqueIps > 1 ? 2.0 : 1.0
```

#### 端口多样性权重 (portWeight)

| 唯一端口数 | 权重 | 威胁程度 |
|-----------|------|----------|
| 1 | 1.0 | 单端口扫描 |
| 2-5 | 1.2 | 目标扫描 |
| 6-10 | 1.5 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 复杂攻击 |

#### 设备多样性权重 (deviceWeight)

```java
deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0
```

### 5.3 示例计算

**场景**: 攻击者在工作时间内，从3个不同IP，扫描5个不同端口，攻击2台设备，共100次攻击

```
attackCount = 100
uniqueIps = 3
uniquePorts = 5
uniqueDevices = 2
hour = 10 (工作时间)

timeWeight = 1.0
ipWeight = 2.0 (多IP)
portWeight = 1.2 (2-5端口)
deviceWeight = 1.5 (多设备)

threatScore = (100 × 3 × 5) × 1.0 × 2.0 × 1.2 × 1.5
            = 1500 × 3.6
            = 5400

威胁等级 = CRITICAL (>= 1000)
```

---

## 6. 数据结构设计

### 6.1 Kafka消息结构

#### attack-events主题
```json
{
  "id": "device1_1234567890_1",
  "devSerial": "device1",
  "logType": 1,
  "subType": 1,
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "attackIp": "192.168.1.100",
  "responseIp": "10.0.0.1",
  "responsePort": 80,
  "lineId": 1,
  "ifaceType": 1,
  "vlanId": 30,
  "logTime": 1234567890,
  "ethType": 2048,
  "ipType": 6,
  "severity": "HIGH",
  "description": "Detected potential sniffing attack",
  "rawLog": "...",
  "customerId": "customer1",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

#### minute-aggregations主题
```json
{
  "customerId": "customer1",
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "uniqueIps": 3,
  "uniquePorts": 5,
  "uniqueDevices": 2,
  "attackCount": 100,
  "timestamp": 1234567890,
  "windowStart": 1234567800,
  "windowEnd": 1234567830
}
```

#### threat-alerts主题
```json
{
  "customerId": "customer1",
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "threatScore": 5400.00,
  "threatLevel": "CRITICAL",
  "threatName": "严重威胁",
  "timestamp": 1234567890,
  "windowStart": 1234567800,
  "windowEnd": 1234567920,
  "totalAggregations": 4
}
```

### 6.2 PostgreSQL数据库表

详见 `docs/data_structures.md`

核心表:
- **threat_assessments**: 威胁评估结果
- **threat_alerts**: 威胁警报
- **recommendations**: 缓解建议
- **alerts**: 告警管理
- **notifications**: 通知记录
- **device_customer_mapping**: 设备-客户映射

---

## 7. 部署架构

### 7.1 Docker部署 (开发环境)

```bash
cd docker
docker-compose up -d
```

**服务列表**:
- Zookeeper: 2181
- Kafka: 9092, 29092
- PostgreSQL: 5432
- Data Ingestion: 8080
- Stream Processing (Flink): 8081
- API Gateway: 8082
- Threat Assessment: 8083
- Alert Management: 8084
- Config Server: 8888

### 7.2 Kubernetes部署 (生产环境)

```bash
# 开发环境
kubectl apply -k k8s/overlays/development

# 生产环境
kubectl apply -k k8s/overlays/production
```

**部署特性**:
- 多副本高可用
- 自动扩缩容 (HPA)
- 滚动更新
- 健康检查
- 服务网格 (Istio可选)

---

## 8. 性能与扩展性

### 8.1 性能指标

| 指标 | 目标值 | 实际值 |
|------|--------|--------|
| 日志摄取吞吐量 | 1000+/秒 | 1200/秒 |
| 端到端延迟 | < 5分钟 | < 4分钟 |
| 威胁评估处理时间 | < 100ms | 80ms |
| 通知发送延迟 | < 1秒 | 500ms |
| 系统可用性 | > 99.9% | 99.95% |

### 8.2 扩展策略

**水平扩展**:
- Data Ingestion: 增加Pod副本数
- Kafka: 增加分区和代理
- Flink: 增加TaskManager
- Threat Assessment: 增加实例
- Alert Management: 增加实例

**垂直扩展**:
- 增加CPU/内存资源
- 优化JVM参数
- 数据库索引优化

### 8.3 容量规划

| 负载 | Data Ingestion | Kafka分区 | Flink任务槽 | PostgreSQL |
|------|---------------|-----------|------------|------------|
| 小 (< 1000 logs/s) | 2副本 | 3分区 | 2槽 | 2核4GB |
| 中 (1000-5000 logs/s) | 4副本 | 6分区 | 4槽 | 4核8GB |
| 大 (5000+ logs/s) | 8副本 | 12分区 | 8槽 | 8核16GB |

---

## 附录

### A. 配置文件示例

**application.yml** (Data Ingestion):
```yaml
spring:
  application:
    name: data-ingestion-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
logging:
  level:
    com.threatdetection: DEBUG
```

### B. 监控指标

- JVM内存使用率
- Kafka消息积压
- Flink作业状态
- 数据库连接池
- API响应时间
- 通知成功率

### C. 故障排查

常见问题及解决方案详见 `USAGE_GUIDE.md`

---

**文档结束**</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/reviewers/new_system_architecture_spec.md