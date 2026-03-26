# 云原生威胁检测系统 - 威胁评估服务API概览

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1  
**服务端口**: 8083

---

## 目录

1. [服务概述](#1-服务概述)
2. [核心功能](#2-核心功能)
3. [API端点](#3-api端点)
4. [数据模型](#4-数据模型)
5. [威胁评分算法](#5-威胁评分算法)
6. [使用示例](#6-使用示例)
7. [错误处理](#7-错误处理)
8. [性能指标](#8-性能指标)

---

## 1. 服务概述

### 1.1 服务定位

威胁评估服务 (Threat Assessment Service) 是云原生威胁检测系统的核心组件之一，负责：

- **实时威胁评估**: 对流处理聚合数据进行威胁评分
- **威胁等级划分**: 根据评分结果确定威胁严重程度
- **评估结果存储**: 将评估结果持久化到PostgreSQL数据库
- **缓解建议生成**: 提供针对性安全建议
- **历史数据查询**: 支持威胁评估历史的检索和分析

### 1.2 架构位置

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Stream Processing│ => │ Threat Assessment │ => │   PostgreSQL   │
│   (Flink Job)   │    │   (Spring Boot)  │    │                 │
│                 │    │                 │    │ threat_assessments│
│ minute-aggregations│    │ threat-alerts     │    │ recommendations │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │ Alert Management│
                       │   (Notifications)│
                       └─────────────────┘
```

### 1.3 技术栈

- **框架**: Spring Boot 3.1.5
- **语言**: Java 21 (OpenJDK)
- **数据库**: PostgreSQL 15+
- **消息队列**: Apache Kafka 3.4+
- **ORM**: Spring Data JPA
- **缓存**: Redis (可选)
- **监控**: Spring Boot Actuator + Micrometer

---

## 2. 核心功能

### 2.1 威胁评估流程

```java
// 1. 接收聚合数据 (来自Kafka: threat-alerts)
AggregatedAttackData data = consumeFromKafka();

// 2. 计算威胁评分
double threatScore = threatScoreCalculator.calculate(data);

// 3. 确定威胁等级
ThreatLevel level = determineThreatLevel(threatScore);

// 4. 生成评估结果
ThreatAlert alert = ThreatAlert.builder()
    .customerId(data.getCustomerId())
    .attackMac(data.getAttackMac())
    .threatScore(threatScore)
    .threatLevel(level)
    .attackCount(data.getAttackCount())
    .uniqueIps(data.getUniqueIps())
    .uniquePorts(data.getUniquePorts())
    .uniqueDevices(data.getUniqueDevices())
    .assessmentTime(Instant.now())
    .build();

// 5. 存储到数据库
threatAlertRepository.save(alert);

// 6. 生成缓解建议
List<Recommendation> recommendations = generateRecommendations(alert);

// 7. 发送通知 (异步)
notificationService.sendAlert(alert);
```

### 2.2 威胁评分算法

#### 核心公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
              × timeWeight × ipWeight × portWeight × deviceWeight
```

#### 权重计算

| 权重类型 | 计算逻辑 | 示例 |
|----------|----------|------|
| **时间权重** | 基于攻击发生时间 | 深夜(0:00-6:00): 1.2 |
| **IP权重** | 基于唯一IP数量 | 多IP攻击: 2.0 |
| **端口权重** | 基于唯一端口数量 | 广泛扫描: 1.6+ |
| **设备权重** | 基于唯一设备数量 | 多设备攻击: 1.5 |

#### 威胁等级划分

| 等级 | 分数范围 | 颜色 | 说明 |
|------|----------|------|------|
| INFO | < 50 | 蓝色 | 信息级别 |
| LOW | 50-199 | 绿色 | 低危威胁 |
| MEDIUM | 200-499 | 黄色 | 中危威胁 |
| HIGH | 500-999 | 橙色 | 高危威胁 |
| CRITICAL | ≥ 1000 | 红色 | 严重威胁 |

### 2.3 缓解建议系统

#### 建议类型

```java
public enum RecommendationAction {
    BLOCK_IP,           // 封禁IP地址
    MONITOR,            // 增强监控
    ISOLATE_DEVICE,     // 隔离设备
    ALERT_SECURITY,     // 安全团队告警
    QUARANTINE,         // 隔离网络段
    INVESTIGATE         // 深入调查
}
```

#### 建议生成逻辑

```java
public List<Recommendation> generateRecommendations(ThreatAlert alert) {
    List<Recommendation> recommendations = new ArrayList<>();
    
    if (alert.getThreatLevel() == ThreatLevel.CRITICAL) {
        recommendations.add(createBlockIpRecommendation(alert));
        recommendations.add(createSecurityAlertRecommendation(alert));
    }
    
    if (alert.getUniqueDevices() > 1) {
        recommendations.add(createIsolateDeviceRecommendation(alert));
    }
    
    if (alert.getUniquePorts() > 10) {
        recommendations.add(createMonitorRecommendation(alert));
    }
    
    return recommendations;
}
```

---

## 3. API端点

### 3.1 基础信息

- **Base URL**: `http://localhost:8083/api/v1/assessment`
- **认证**: 无 (开发环境)
- **数据格式**: JSON
- **字符编码**: UTF-8
- **分页**: 支持 `limit` 和 `offset` 参数

### 3.2 威胁查询API

#### 3.2.1 获取威胁列表

```http
GET /api/v1/assessment/threats
```

**查询参数**:
- `customerId` (必需): 客户ID
- `threatLevel` (可选): 威胁等级过滤
- `startTime` (可选): 开始时间 (ISO 8601)
- `endTime` (可选): 结束时间 (ISO 8601)
- `limit` (可选): 返回数量 (默认10, 最大100)
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

#### 3.2.2 获取特定威胁

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

#### 3.2.3 获取威胁统计

```http
GET /api/v1/assessment/threats/stats
```

**查询参数**:
- `customerId` (必需): 客户ID
- `timeRange` (可选): 时间范围 (1h, 24h, 7d, 30d)

**请求示例**:
```bash
curl "http://localhost:8083/api/v1/assessment/threats/stats?customerId=customer-001&timeRange=24h"
```

**响应格式**:
```json
{
  "totalThreats": 150,
  "threatsByLevel": {
    "CRITICAL": 5,
    "HIGH": 25,
    "MEDIUM": 45,
    "LOW": 50,
    "INFO": 25
  },
  "topAttackers": [
    {
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "threatCount": 15,
      "highestScore": 125.5
    }
  ],
  "timeRange": "24h",
  "generatedAt": "2024-01-15T12:00:00Z"
}
```

### 3.3 缓解建议API

#### 3.3.1 获取威胁建议

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

#### 3.3.2 执行缓解建议

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

#### 3.3.3 批量执行建议

```http
POST /api/v1/assessment/recommendations/batch-execute
```

**请求体**:
```json
{
  "recommendationIds": [1, 2, 3],
  "customerId": "customer-001"
}
```

**响应格式**:
```json
{
  "results": [
    {
      "recommendationId": 1,
      "success": true,
      "executedAt": "2024-01-15T10:35:00Z",
      "result": {
        "status": "SUCCESS",
        "message": "IP blocked successfully"
      }
    },
    {
      "recommendationId": 2,
      "success": false,
      "error": "Firewall service unavailable"
    }
  ],
  "summary": {
    "total": 3,
    "successful": 1,
    "failed": 2
  }
}
```

### 3.4 威胁情报API

#### 3.4.1 获取威胁情报

```http
GET /api/v1/assessment/intelligence/{threatId}
```

**路径参数**:
- `threatId`: 威胁评估ID

**请求示例**:
```bash
curl http://localhost:8083/api/v1/assessment/intelligence/1
```

**响应格式**:
```json
{
  "threatId": 1,
  "intelligence": {
    "knownAttacker": false,
    "campaignId": null,
    "similarIncidents": 3,
    "threatActor": null,
    "malwareFamily": null,
    "cveReferences": [],
    "geolocation": {
      "country": "China",
      "city": "Beijing",
      "coordinates": [116.4074, 39.9042]
    },
    "attackPatterns": [
      "Port Scanning",
      "Horizontal Movement"
    ],
    "riskFactors": [
      "Multiple IP addresses",
      "Unusual time pattern",
      "High port diversity"
    ]
  },
  "lastUpdated": "2024-01-15T10:32:00Z",
  "confidence": 0.85
}
```

#### 3.4.2 搜索相似威胁

```http
GET /api/v1/assessment/intelligence/search-similar
```

**查询参数**:
- `customerId` (必需): 客户ID
- `attackMac` (可选): 攻击者MAC
- `threatLevel` (可选): 威胁等级
- `timeWindow` (可选): 时间窗口 (1h, 24h, 7d)

**请求示例**:
```bash
curl "http://localhost:8083/api/v1/assessment/intelligence/search-similar?customerId=customer-001&attackMac=AA:BB:CC:DD:EE:FF&timeWindow=24h"
```

**响应格式**:
```json
{
  "query": {
    "customerId": "customer-001",
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "timeWindow": "24h"
  },
  "similarThreats": [
    {
      "id": 5,
      "threatScore": 118.3,
      "threatLevel": "HIGH",
      "similarity": 0.92,
      "assessmentTime": "2024-01-15T09:15:00Z"
    },
    {
      "id": 12,
      "threatScore": 95.7,
      "threatLevel": "MEDIUM",
      "similarity": 0.78,
      "assessmentTime": "2024-01-15T08:45:00Z"
    }
  ],
  "totalMatches": 2,
  "searchTimeMs": 45
}
```

### 3.5 健康检查API

#### 3.5.1 服务健康状态

```http
GET /actuator/health
```

**响应格式**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "SELECT 1"
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "kafka-cluster-1"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 1073741824,
        "free": 536870912,
        "threshold": 10485760
      }
    }
  }
}
```

#### 3.5.2 性能指标

```http
GET /actuator/metrics
GET /actuator/metrics/threat.assessment.duration
GET /actuator/metrics/threat.assessment.count
```

---

## 4. 数据模型

### 4.1 ThreatAlert (威胁告警)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "threat_assessments")
public class ThreatAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "attack_mac", nullable = false)
    private String attackMac;

    @Column(name = "threat_score", nullable = false)
    private Double threatScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_level", nullable = false)
    private ThreatLevel threatLevel;

    @Column(name = "attack_count", nullable = false)
    private Integer attackCount;

    @Column(name = "unique_ips", nullable = false)
    private Integer uniqueIps;

    @Column(name = "unique_ports", nullable = false)
    private Integer uniquePorts;

    @Column(name = "unique_devices", nullable = false)
    private Integer uniqueDevices;

    @Column(name = "assessment_time", nullable = false)
    private Instant assessmentTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 4.2 Recommendation (缓解建议)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private RecommendationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private RecommendationPriority priority;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters; // JSON string

    @Column(name = "executed", nullable = false)
    private Boolean executed = false;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 4.3 ThreatIntelligence (威胁情报)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatIntelligence {

    private Long threatId;
    private boolean knownAttacker;
    private String campaignId;
    private Integer similarIncidents;
    private String threatActor;
    private String malwareFamily;
    private List<String> cveReferences;
    private Geolocation geolocation;
    private List<String> attackPatterns;
    private List<String> riskFactors;
    private Instant lastUpdated;
    private Double confidence;
}
```

### 4.4 枚举类型

```java
public enum ThreatLevel {
    INFO, LOW, MEDIUM, HIGH, CRITICAL
}

public enum RecommendationAction {
    BLOCK_IP, MONITOR, ISOLATE_DEVICE, ALERT_SECURITY, QUARANTINE, INVESTIGATE
}

public enum RecommendationPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

---

## 5. 威胁评分算法

### 5.1 算法实现

```java
@Service
@Slf4j
public class ThreatScoreCalculator {

    public double calculate(AggregatedAttackData data) {
        double baseScore = data.getAttackCount() * data.getUniqueIps() * data.getUniquePorts();
        
        double timeWeight = calculateTimeWeight(data.getTimestamp());
        double ipWeight = calculateIpWeight(data.getUniqueIps());
        double portWeight = calculatePortWeight(data.getUniquePorts());
        double deviceWeight = calculateDeviceWeight(data.getUniqueDevices());
        
        double finalScore = baseScore * timeWeight * ipWeight * portWeight * deviceWeight;
        
        log.debug("Threat score calculation: base={}, time={}, ip={}, port={}, device={}, final={}",
                  baseScore, timeWeight, ipWeight, portWeight, deviceWeight, finalScore);
        
        return Math.max(0, finalScore); // 确保非负
    }

    private double calculateTimeWeight(Instant timestamp) {
        LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
        int hour = time.getHour();
        
        if (hour >= 0 && hour < 6) return 1.2;   // 深夜
        if (hour >= 6 && hour < 9) return 1.1;   // 早晨
        if (hour >= 9 && hour < 17) return 1.0;  // 工作时间
        if (hour >= 17 && hour < 21) return 0.9; // 傍晚
        return 0.8; // 夜间
    }

    private double calculateIpWeight(int uniqueIps) {
        return uniqueIps > 1 ? 2.0 : 1.0;
    }

    private double calculatePortWeight(int uniquePorts) {
        if (uniquePorts <= 1) return 1.0;
        if (uniquePorts <= 5) return 1.2;
        if (uniquePorts <= 10) return 1.5;
        if (uniquePorts <= 20) return 1.8;
        return 2.0;
    }

    private double calculateDeviceWeight(int uniqueDevices) {
        return uniqueDevices > 1 ? 1.5 : 1.0;
    }
}
```

### 5.2 评分示例

#### 高危威胁示例

**输入数据**:
```json
{
  "customerId": "customer-001",
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "attackCount": 100,
  "uniqueIps": 5,
  "uniquePorts": 15,
  "uniqueDevices": 3,
  "timestamp": "2024-01-15T02:00:00Z"
}
```

**计算过程**:
```
基础分数 = 100 × 5 × 15 = 7500
时间权重 = 1.2 (深夜)
IP权重 = 2.0 (多IP)
端口权重 = 1.8 (11-20端口)
设备权重 = 1.5 (多设备)

最终分数 = 7500 × 1.2 × 2.0 × 1.8 × 1.5 = 7500 × 6.48 = 48600
威胁等级 = CRITICAL
```

---

## 6. 使用示例

### 6.1 基本威胁查询

```bash
# 获取最近的威胁
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&limit=10"

# 获取高危威胁
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&threatLevel=HIGH"

# 获取特定时间范围的威胁
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&startTime=2024-01-15T00:00:00Z&endTime=2024-01-15T23:59:59Z"
```

### 6.2 威胁详情查询

```bash
# 获取威胁详情
THREAT_ID=1
curl "http://localhost:8083/api/v1/assessment/threats/$THREAT_ID"

# 获取缓解建议
curl "http://localhost:8083/api/v1/assessment/recommendations/$THREAT_ID"

# 执行建议
RECOMMENDATION_ID=1
curl -X POST "http://localhost:8083/api/v1/assessment/recommendations/$RECOMMENDATION_ID/execute"
```

### 6.3 统计查询

```bash
# 获取威胁统计
curl "http://localhost:8083/api/v1/assessment/threats/stats?customerId=customer-001&timeRange=24h"

# 获取威胁情报
curl "http://localhost:8083/api/v1/assessment/intelligence/$THREAT_ID"
```

### 6.4 批量操作

```bash
# 批量执行建议
curl -X POST "http://localhost:8083/api/v1/assessment/recommendations/batch-execute" \
  -H "Content-Type: application/json" \
  -d '{
    "recommendationIds": [1, 2, 3],
    "customerId": "customer-001"
  }'
```

---

## 7. 错误处理

### 7.1 HTTP状态码

| 状态码 | 说明 | 示例场景 |
|--------|------|----------|
| 200 | 成功 | 查询成功 |
| 201 | 创建成功 | 建议执行成功 |
| 400 | 请求错误 | 参数无效 |
| 404 | 资源不存在 | 威胁ID不存在 |
| 409 | 冲突 | 建议已被执行 |
| 500 | 服务器错误 | 数据库连接失败 |

### 7.2 错误响应格式

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "customerId is required",
  "path": "/api/v1/assessment/threats",
  "requestId": "req-12345"
}
```

### 7.3 常见错误

#### 7.3.1 参数验证错误

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "customerId": "must not be blank",
    "threatLevel": "must be one of [INFO, LOW, MEDIUM, HIGH, CRITICAL]"
  }
}
```

#### 7.3.2 数据库错误

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Database connection failed",
  "details": {
    "errorCode": "08006",
    "sqlState": "connection_failure"
  }
}
```

#### 7.3.3 Kafka错误

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Kafka broker unavailable",
  "details": {
    "brokers": ["kafka:9092"],
    "topic": "threat-alerts"
  }
}
```

---

## 8. 性能指标

### 8.1 响应时间

| 操作 | 平均响应时间 | 95%响应时间 | 99%响应时间 |
|------|--------------|--------------|--------------|
| 威胁查询 | 80ms | 150ms | 300ms |
| 威胁详情 | 50ms | 100ms | 200ms |
| 建议执行 | 200ms | 500ms | 1000ms |
| 统计查询 | 150ms | 300ms | 600ms |

### 8.2 吞吐量

| 操作 | 目标QPS | 当前QPS | 并发用户数 |
|------|---------|---------|------------|
| 威胁查询 | 100 | 150 | 50 |
| 威胁评估 | 50 | 75 | 20 |
| 建议执行 | 20 | 30 | 10 |

### 8.3 资源使用

| 指标 | 平均值 | 峰值 | 阈值 |
|------|--------|------|------|
| CPU使用率 | 25% | 60% | 80% |
| 内存使用 | 512MB | 1GB | 2GB |
| 数据库连接 | 10 | 25 | 50 |
| Kafka消费者延迟 | 100ms | 500ms | 2000ms |

### 8.4 监控指标

```yaml
# 自定义指标
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      service: threat-assessment
      version: "2.1"
```

**关键指标**:
- `threat_assessment_duration`: 评估耗时
- `threat_assessment_count`: 评估次数
- `recommendation_execution_duration`: 建议执行耗时
- `db_connection_pool_active`: 数据库连接数
- `kafka_consumer_lag`: Kafka消费者延迟

---

**文档结束**