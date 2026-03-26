# 云原生威胁检测系统数据结构规范

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1

---

## 目录

1. [概述](#1-概述)
2. [Kafka消息格式](#2-kafka消息格式)
3. [PostgreSQL表结构](#3-postgresql表结构)
4. [Java数据模型](#4-java数据模型)
5. [枚举定义](#5-枚举定义)
6. [API数据传输对象](#6-api数据传输对象)
7. [配置数据结构](#7-配置数据结构)

---

## 1. 概述

### 1.1 设计原则

- **一致性**: 所有数据结构在Kafka、数据库、Java对象间保持一致
- **可扩展性**: 支持新字段的添加，不破坏现有结构
- **多租户**: 所有数据包含`customerId`字段
- **时间戳**: 使用UTC时间戳，毫秒精度
- **命名规范**: 
  - Kafka消息: snake_case (JSON)
  - Java对象: camelCase
  - 数据库表/字段: snake_case

### 1.2 数据流向

```
外部日志 → Kafka消息 → Java对象 → 数据库表 → API响应
```

---

## 2. Kafka消息格式

### 2.1 attack-events (攻击事件)

**用途**: 原始攻击事件数据，包含所有从设备接收到的攻击信息

```json
{
  "id": "string",                    // 事件唯一ID (设备序列号_时间戳_序列号)
  "devSerial": "string",             // 设备序列号 (如: "DEV-001")
  "logType": 1,                      // 日志类型 (1=攻击, 2=状态)
  "subType": 1,                      // 子类型 (攻击类型)
  "attackMac": "string",             // 攻击者MAC地址 (AA:BB:CC:DD:EE:FF)
  "attackIp": "string",              // 攻击者IP地址 (192.168.1.100)
  "responseIp": "string",            // 响应IP (诱饵IP, 如: 10.0.0.1)
  "responsePort": 80,                // 响应端口 (攻击目标端口)
  "lineId": 1,                       // 线路ID
  "ifaceType": 1,                    // 接口类型
  "vlanId": 30,                      // VLAN ID
  "logTime": 1234567890,             // 日志时间戳 (Unix秒)
  "ethType": 2048,                   // 以太网类型
  "ipType": 6,                       // IP协议类型 (6=TCP, 17=UDP)
  "customerId": "string",            // 客户ID (如: "customer-001")
  "timestamp": "2024-01-01T00:00:00Z" // 处理时间戳 (ISO 8601)
}
```

**字段说明**:
- `id`: 全局唯一标识符，格式: `{devSerial}_{logTime}_{sequence}`
- `attackMac`: 被诱捕的内网主机MAC地址
- `attackIp`: 被诱捕的内网主机IP地址
- `responseIp`: 诱饵IP地址 (不存在的虚拟哨兵)
- `responsePort`: 攻击者尝试访问的端口 (暴露攻击意图)

### 2.2 status-events (状态事件)

**用途**: 设备状态报告，包含设备运行统计信息

```json
{
  "id": "string",                    // 事件唯一ID
  "devSerial": "string",             // 设备序列号
  "logType": 2,                      // 日志类型 (固定为2)
  "sentryCount": 100,                // 哨兵数量 (诱饵IP数量)
  "realHostCount": 50,               // 真实主机数量
  "devStartTime": 1234567890,        // 设备启动时间
  "devEndTime": 1234567890,          // 设备结束时间
  "timestamp": "2024-01-01T00:00:00Z" // 处理时间戳
}
```

### 2.3 minute-aggregations (分钟聚合)

**用途**: 30秒聚合窗口的统计结果

```json
{
  "customerId": "string",            // 客户ID
  "attackMac": "string",             // 攻击者MAC
  "uniqueIps": 5,                    // 访问的诱饵IP数量
  "uniquePorts": 3,                  // 尝试的端口种类
  "uniqueDevices": 2,                // 检测到该攻击者的设备数
  "attackCount": 150,                // 探测次数
  "timestamp": 1234567890,           // 聚合时间戳
  "windowStart": 1234567800,         // 窗口开始时间
  "windowEnd": 1234567830            // 窗口结束时间
}
```

### 2.4 threat-alerts (威胁警报)

**用途**: 威胁评分结果，包含最终的威胁评估

```json
{
  "customerId": "string",            // 客户ID
  "attackMac": "string",             // 攻击者MAC
  "threatScore": 125.5,              // 威胁评分
  "threatLevel": "HIGH",             // 威胁等级
  "threatName": "高危威胁",           // 威胁名称
  "timestamp": 1234567890,           // 评分时间戳
  "windowStart": 1234567800,         // 评分窗口开始
  "windowEnd": 1234567920,           // 评分窗口结束
  "totalAggregations": 4             // 聚合次数
}
```

---

## 3. PostgreSQL表结构

### 3.1 threat_assessments (威胁评估表)

**用途**: 存储威胁评估结果和详细信息

```sql
CREATE TABLE threat_assessments (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    assessment_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_mac (customer_id, attack_mac),
    INDEX idx_assessment_time (assessment_time),
    INDEX idx_threat_level (threat_level)
);
```

**字段说明**:
- `id`: 自增主键
- `customer_id`: 客户ID，用于多租户隔离
- `attack_mac`: 攻击者MAC地址
- `threat_score`: 计算出的威胁评分
- `threat_level`: 威胁等级 (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- `attack_count`: 攻击事件总数
- `unique_ips`: 唯一诱饵IP数量
- `unique_ports`: 唯一端口数量
- `unique_devices`: 唯一设备数量
- `assessment_time`: 评估时间
- `created_at`: 记录创建时间

### 3.2 threat_alerts (威胁警报表)

**用途**: 存储威胁警报历史记录

```sql
CREATE TABLE threat_alerts (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    alert_time TIMESTAMP NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_alert_time (customer_id, alert_time),
    INDEX idx_threat_level_time (threat_level, alert_time)
);
```

### 3.3 alerts (告警管理表)

**用途**: 存储告警通知记录

```sql
CREATE TABLE alerts (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'NEW',
    assigned_to VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_status (customer_id, status),
    INDEX idx_created_at (created_at)
);
```

### 3.4 notifications (通知记录表)

**用途**: 存储通知发送记录

```sql
CREATE TABLE notifications (
    id SERIAL PRIMARY KEY,
    alert_id INTEGER REFERENCES alerts(id),
    customer_id VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,     -- EMAIL, SMS, WEBHOOK, SLACK, TEAMS
    recipient VARCHAR(200) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_alert_channel (alert_id, channel),
    INDEX idx_status_sent (status, sent_at)
);
```

### 3.5 recommendations (缓解建议表)

**用途**: 存储威胁缓解建议

```sql
CREATE TABLE recommendations (
    id SERIAL PRIMARY KEY,
    assessment_id INTEGER REFERENCES threat_assessments(id),
    customer_id VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,      -- BLOCK_IP, RATE_LIMIT, ISOLATE_HOST, etc.
    priority VARCHAR(20) NOT NULL,     -- CRITICAL, HIGH, MEDIUM, LOW
    description TEXT NOT NULL,
    parameters JSONB,                  -- 行动参数
    executed BOOLEAN DEFAULT FALSE,
    executed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_assessment (assessment_id),
    INDEX idx_priority (priority)
);
```

### 3.6 device_customer_mapping (设备客户映射表)

**用途**: 维护设备序列号到客户ID的映射关系

```sql
CREATE TABLE device_customer_mapping (
    id SERIAL PRIMARY KEY,
    device_serial VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(50) NOT NULL,
    device_name VARCHAR(100),
    location VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer (customer_id),
    INDEX idx_device (device_serial)
);
```

### 3.7 port_weights (端口权重配置表)

**用途**: 配置不同端口的风险权重

```sql
CREATE TABLE port_weights (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    port INTEGER NOT NULL,
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.0,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    UNIQUE KEY uk_customer_port (customer_id, port),
    CHECK (weight >= 0.1 AND weight <= 5.0)
);
```

### 3.8 ip_segment_weights (IP段权重配置表)

**用途**: 配置不同IP段的风险权重

```sql
CREATE TABLE ip_segment_weights (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    ip_segment VARCHAR(43) NOT NULL,   -- 支持IPv4和IPv6
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.0,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    UNIQUE KEY uk_customer_segment (customer_id, ip_segment),
    CHECK (weight >= 0.1 AND weight <= 5.0)
);
```

### 3.9 customer_time_weights (客户时间权重配置表)

**用途**: 客户自定义时间权重配置

```sql
CREATE TABLE customer_time_weights (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    hour_start INTEGER NOT NULL,       -- 0-23
    hour_end INTEGER NOT NULL,         -- 0-23
    weight DECIMAL(3,2) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    CHECK (hour_start >= 0 AND hour_start <= 23),
    CHECK (hour_end >= 0 AND hour_end <= 23),
    CHECK (weight >= 0.1 AND weight <= 2.0)
);
```

---

## 4. Java数据模型

### 4.1 AttackEvent (攻击事件)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackEvent {
    private String id;
    private String devSerial;
    private Integer logType;
    private Integer subType;
    private String attackMac;
    private String attackIp;
    private String responseIp;
    private Integer responsePort;
    private Integer lineId;
    private Integer ifaceType;
    private Integer vlanId;
    private Long logTime;
    private Integer ethType;
    private Integer ipType;
    private String customerId;
    private Instant timestamp;
}
```

### 4.2 AggregatedAttackData (聚合攻击数据)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedAttackData {
    private String customerId;
    private String attackMac;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    private Integer attackCount;
    private Instant timestamp;
    private Instant windowStart;
    private Instant windowEnd;
}
```

### 4.3 ThreatAlert (威胁警报)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAlert {
    private String customerId;
    private String attackMac;
    private BigDecimal threatScore;
    private ThreatLevel threatLevel;
    private String threatName;
    private Instant timestamp;
    private Instant windowStart;
    private Instant windowEnd;
    private Integer totalAggregations;
}
```

### 4.4 ThreatAssessment (威胁评估)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAssessment {
    private Long id;
    private String customerId;
    private String attackMac;
    private BigDecimal threatScore;
    private ThreatLevel threatLevel;
    private Integer attackCount;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    private Instant assessmentTime;
    private Instant createdAt;
}
```

---

## 5. 枚举定义

### 5.1 ThreatLevel (威胁等级)

```java
public enum ThreatLevel {
    INFO("信息", 0, 50),
    LOW("低危", 50, 200),
    MEDIUM("中危", 200, 500),
    HIGH("高危", 500, 1000),
    CRITICAL("严重", 1000, Integer.MAX_VALUE);
    
    private final String displayName;
    private final int minScore;
    private final int maxScore;
    
    ThreatLevel(String displayName, int minScore, int maxScore) {
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }
    
    public static ThreatLevel fromScore(double score) {
        for (ThreatLevel level : values()) {
            if (score >= level.minScore && score < level.maxScore) {
                return level;
            }
        }
        return CRITICAL;
    }
}
```

### 5.2 AlertStatus (告警状态)

```java
public enum AlertStatus {
    NEW,                // 新告警
    ACKNOWLEDGED,       // 已确认
    INVESTIGATING,      // 调查中
    RESOLVED,           // 已解决
    CLOSED              // 已关闭
}
```

### 5.3 NotificationChannel (通知渠道)

```java
public enum NotificationChannel {
    EMAIL,
    SMS,
    WEBHOOK,
    SLACK,
    TEAMS
}
```

### 5.4 NotificationStatus (通知状态)

```java
public enum NotificationStatus {
    PENDING,    // 待发送
    SENT,       // 已发送
    FAILED,     // 发送失败
    RETRYING    // 重试中
}
```

### 5.5 RecommendationAction (建议行动)

```java
public enum RecommendationAction {
    BLOCK_IP("封禁IP"),
    RATE_LIMIT("限速"),
    ISOLATE_HOST("隔离主机"),
    MONITOR("加强监控"),
    ALERT_ADMIN("通知管理员"),
    QUARANTINE("隔离网络");
    
    private final String description;
    
    RecommendationAction(String description) {
        this.description = description;
    }
}
```

---

## 6. API数据传输对象

### 6.1 ThreatAssessmentDTO (威胁评估DTO)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAssessmentDTO {
    private Long id;
    private String customerId;
    private String attackMac;
    private BigDecimal threatScore;
    private String threatLevel;
    private Integer attackCount;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    private Instant assessmentTime;
    
    // 关联数据
    private List<RecommendationDTO> recommendations;
    private List<AlertDTO> relatedAlerts;
}
```

### 6.2 AlertDTO (告警DTO)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private Long id;
    private String customerId;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private String status;
    private String assignedTo;
    private Instant createdAt;
    private Instant updatedAt;
    
    // 关联数据
    private List<NotificationDTO> notifications;
}
```

### 6.3 NotificationDTO (通知DTO)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long alertId;
    private String customerId;
    private String channel;
    private String recipient;
    private String status;
    private Instant sentAt;
    private String errorMessage;
    private Integer retryCount;
}
```

---

## 7. 配置数据结构

### 7.1 Kafka配置

```java
@ConfigurationProperties(prefix = "app.kafka")
@Data
public class KafkaConfig {
    private String bootstrapServers;
    private String groupId;
    private String attackEventsTopic = "attack-events";
    private String statusEventsTopic = "status-events";
    private String aggregationsTopic = "minute-aggregations";
    private String alertsTopic = "threat-alerts";
    
    // 生产者配置
    private Map<String, Object> producer = new HashMap<>();
    
    // 消费者配置
    private Map<String, Object> consumer = new HashMap<>();
}
```

### 7.2 Flink配置

```java
@ConfigurationProperties(prefix = "app.flink")
@Data
public class FlinkConfig {
    private Integer aggregationWindowSeconds = 30;
    private Integer threatScoringWindowMinutes = 2;
    private Integer parallelism = 4;
    private Long checkpointingInterval = 60000L;
    private String stateBackend = "filesystem";
}
```

### 7.3 威胁评分配置

```java
@ConfigurationProperties(prefix = "app.threat-scoring")
@Data
public class ThreatScoringConfig {
    // 时间权重配置
    private Map<String, Double> timeWeights = Map.of(
        "00-05", 1.2,
        "05-09", 1.1,
        "09-17", 1.0,
        "17-21", 0.9,
        "21-24", 0.8
    );
    
    // IP权重配置
    private Map<String, Double> ipWeights = Map.of(
        "single", 1.0,
        "multiple", 2.0
    );
    
    // 端口权重配置
    private Map<String, Double> portWeights = Map.of(
        "1", 1.0,
        "2-5", 1.2,
        "6-10", 1.5,
        "11-20", 1.8,
        "20+", 2.0
    );
    
    // 设备权重配置
    private Map<String, Double> deviceWeights = Map.of(
        "single", 1.0,
        "multiple", 1.5
    );
}
```

---

## 附录

### A. 数据一致性检查

**Kafka → Java对象映射**:
- `snake_case` → `camelCase`
- `timestamp` → `Instant`
- `threat_score` → `threatScore`

**Java对象 → 数据库映射**:
- `camelCase` → `snake_case`
- `Instant` → `TIMESTAMP`
- `BigDecimal` → `DECIMAL`

### B. 数据验证规则

**AttackEvent验证**:
- `id` 不能为空且唯一
- `attackMac` 必须是有效的MAC地址格式
- `attackIp` 必须是有效的IP地址
- `responsePort` 必须在1-65535范围内
- `customerId` 不能为空

**ThreatAlert验证**:
- `threatScore` 必须 >= 0
- `threatLevel` 必须是有效枚举值
- `timestamp` 不能为null

### C. 索引优化建议

**高频查询索引**:
- `(customer_id, attack_mac, assessment_time)`
- `(customer_id, threat_level, created_at)`
- `(alert_id, channel, status)`

**复合索引**:
- `(customer_id, status, created_at)` 用于告警查询
- `(customer_id, sent_at, status)` 用于通知统计

---

**文档结束**</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/reviewers/data_structures.md