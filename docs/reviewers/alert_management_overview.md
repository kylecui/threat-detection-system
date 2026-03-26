# 云原生威胁检测系统 - 告警管理服务API概览

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1  
**服务端口**: 8084

---

## 目录

1. [服务概述](#1-服务概述)
2. [核心功能](#2-核心功能)
3. [API端点](#3-api端点)
4. [数据模型](#4-数据模型)
5. [通知渠道](#5-通知渠道)
6. [使用示例](#6-使用示例)
7. [错误处理](#7-错误处理)
8. [性能指标](#8-性能指标)

---

## 1. 服务概述

### 1.1 服务定位

告警管理服务 (Alert Management Service) 是云原生威胁检测系统的告警处理和通知中心，负责：

- **告警接收与处理**: 从威胁评估服务接收威胁告警
- **告警去重与聚合**: 防止告警风暴，提高告警质量
- **多渠道通知**: 支持Email、SMS、Webhook等多种通知方式
- **告警生命周期管理**: 跟踪告警状态和处理进度
- **通知模板管理**: 提供灵活的通知模板配置
- **告警统计分析**: 提供告警趋势和效果分析

### 1.2 架构位置

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Threat Assessment│ => │ Alert Management │ => │  Notification  │
│   (threat-alerts)│    │   (Spring Boot)  │    │   Channels     │
│                 │    │                 │    │                 │
│ Kafka messages  │    │ • 去重聚合      │    │ • Email         │
└─────────────────┘    │ • 通知分发      │    │ • SMS           │
                       │ • 状态跟踪      │    │ • Webhook       │
                       └─────────────────┘    │ • Slack         │
                                              └─────────────────┘
                                                     │
                                                     ▼
                                            ┌─────────────────┐
                                            │   PostgreSQL    │
                                            │                 │
                                            │ alerts          │
                                            │ notifications   │
                                            │ templates       │
                                            └─────────────────┘
```

### 1.3 技术栈

- **框架**: Spring Boot 3.1.5
- **语言**: Java 21 (OpenJDK)
- **数据库**: PostgreSQL 15+
- **消息队列**: Apache Kafka 3.4+
- **邮件**: Spring Boot Mail
- **短信**: 第三方SMS服务集成
- **模板引擎**: Thymeleaf
- **调度**: Spring Scheduler
- **缓存**: Redis (可选)

---

## 2. 核心功能

### 2.1 告警处理流程

```java
// 1. 接收威胁告警 (来自Kafka: threat-alerts)
ThreatAlert alert = consumeFromKafka();

// 2. 告警去重检查
if (isDuplicateAlert(alert)) {
    log.info("Duplicate alert detected, customerId={}, attackMac={}",
             alert.getCustomerId(), alert.getAttackMac());
    return;
}

// 3. 创建告警记录
Alert alertEntity = Alert.builder()
    .customerId(alert.getCustomerId())
    .threatId(alert.getId())
    .threatLevel(alert.getThreatLevel())
    .status(AlertStatus.NEW)
    .title(generateAlertTitle(alert))
    .description(generateAlertDescription(alert))
    .createdAt(Instant.now())
    .build();

alertRepository.save(alertEntity);

// 4. 确定通知配置
List<NotificationConfig> configs = getNotificationConfigs(alert.getCustomerId(), alert.getThreatLevel());

// 5. 发送通知
for (NotificationConfig config : configs) {
    sendNotification(alertEntity, config);
}

// 6. 更新告警状态
alertEntity.setStatus(AlertStatus.NOTIFIED);
alertRepository.save(alertEntity);
```

### 2.2 告警去重机制

#### 去重策略

```java
public boolean isDuplicateAlert(ThreatAlert alert) {
    String key = generateDeduplicationKey(alert);
    
    // 检查最近N分钟内的相似告警
    Instant since = Instant.now().minus(Duration.ofMinutes(10));
    List<Alert> recentAlerts = alertRepository.findByCustomerIdAndCreatedAtAfter(
        alert.getCustomerId(), since);
    
    return recentAlerts.stream()
        .anyMatch(existing -> isSimilarAlert(existing, alert));
}

private boolean isSimilarAlert(Alert existing, ThreatAlert newAlert) {
    // 相同攻击者MAC
    if (!existing.getAttackMac().equals(newAlert.getAttackMac())) {
        return false;
    }
    
    // 相同威胁等级
    if (existing.getThreatLevel() != newAlert.getThreatLevel()) {
        return false;
    }
    
    // 时间窗口内 (10分钟)
    Duration timeDiff = Duration.between(existing.getCreatedAt(), Instant.now());
    if (timeDiff.toMinutes() > 10) {
        return false;
    }
    
    return true;
}
```

#### 去重键生成

```java
private String generateDeduplicationKey(ThreatAlert alert) {
    return String.format("%s:%s:%s:%d",
        alert.getCustomerId(),
        alert.getAttackMac(),
        alert.getThreatLevel(),
        alert.getAssessmentTime().toEpochMilli() / (10 * 60 * 1000)); // 10分钟窗口
}
```

### 2.3 通知分发系统

#### 通知类型

```java
public enum NotificationChannel {
    EMAIL,
    SMS,
    WEBHOOK,
    SLACK,
    TEAMS,
    PAGER_DUTY
}
```

#### 异步通知发送

```java
@Service
@Slf4j
public class NotificationService {

    @Async
    public CompletableFuture<NotificationResult> sendNotification(
            Alert alert, NotificationConfig config) {
        
        try {
            Notification notification = createNotification(alert, config);
            
            switch (config.getChannel()) {
                case EMAIL:
                    return sendEmail(notification);
                case SMS:
                    return sendSms(notification);
                case WEBHOOK:
                    return sendWebhook(notification);
                default:
                    throw new UnsupportedOperationException("Unsupported channel: " + config.getChannel());
            }
            
        } catch (Exception e) {
            log.error("Failed to send notification: alertId={}, channel={}",
                      alert.getId(), config.getChannel(), e);
            
            // 记录失败通知
            saveFailedNotification(alert, config, e);
            
            return CompletableFuture.completedFuture(
                NotificationResult.failure("Send failed: " + e.getMessage()));
        }
    }
}
```

---

## 3. API端点

### 3.1 基础信息

- **Base URL**: `http://localhost:8084/api/v1/alerts`
- **认证**: 无 (开发环境)
- **数据格式**: JSON
- **字符编码**: UTF-8
- **分页**: 支持 `limit` 和 `offset` 参数

### 3.2 告警查询API

#### 3.2.1 获取告警列表

```http
GET /api/v1/alerts
```

**查询参数**:
- `customerId` (必需): 客户ID
- `status` (可选): 告警状态 (NEW, NOTIFIED, ACKNOWLEDGED, RESOLVED)
- `threatLevel` (可选): 威胁等级
- `startTime` (可选): 开始时间 (ISO 8601)
- `endTime` (可选): 结束时间 (ISO 8601)
- `limit` (可选): 返回数量 (默认10, 最大100)
- `offset` (可选): 偏移量 (默认0)

**请求示例**:
```bash
curl "http://localhost:8084/api/v1/alerts?customerId=customer-001&status=NEW&threatLevel=HIGH&limit=5"
```

**响应格式**:
```json
{
  "data": [
    {
      "id": 1,
      "customerId": "customer-001",
      "threatId": 123,
      "threatLevel": "HIGH",
      "status": "NEW",
      "title": "High Threat Detected from MAC AA:BB:CC:DD:EE:FF",
      "description": "Detected high threat activity with score 125.5",
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "attackCount": 150,
      "uniqueIps": 5,
      "uniquePorts": 3,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:05Z",
      "acknowledgedAt": null,
      "resolvedAt": null
    }
  ],
  "total": 25,
  "page": 1,
  "size": 5,
  "hasMore": true
}
```

#### 3.2.2 获取特定告警

```http
GET /api/v1/alerts/{id}
```

**路径参数**:
- `id`: 告警ID

**请求示例**:
```bash
curl http://localhost:8084/api/v1/alerts/1
```

**响应格式**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "threatId": 123,
  "threatLevel": "HIGH",
  "status": "NEW",
  "title": "High Threat Detected from MAC AA:BB:CC:DD:EE:FF",
  "description": "Detected high threat activity with score 125.5 from attacker MAC AA:BB:CC:DD:EE:FF. Attack characteristics: 150 probes across 5 unique IPs and 3 ports.",
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "threatScore": 125.5,
  "assessmentTime": "2024-01-15T10:32:00Z",
  "createdAt": "2024-01-15T10:32:05Z",
  "updatedAt": "2024-01-15T10:32:05Z",
  "acknowledgedAt": null,
  "resolvedAt": null,
  "notifications": [
    {
      "id": 1,
      "channel": "EMAIL",
      "recipient": "admin@company.com",
      "status": "SENT",
      "sentAt": "2024-01-15T10:32:10Z",
      "errorMessage": null
    }
  ]
}
```

#### 3.2.3 获取告警统计

```http
GET /api/v1/alerts/stats
```

**查询参数**:
- `customerId` (必需): 客户ID
- `timeRange` (可选): 时间范围 (1h, 24h, 7d, 30d)

**请求示例**:
```bash
curl "http://localhost:8084/api/v1/alerts/stats?customerId=customer-001&timeRange=24h"
```

**响应格式**:
```json
{
  "totalAlerts": 150,
  "alertsByStatus": {
    "NEW": 10,
    "NOTIFIED": 85,
    "ACKNOWLEDGED": 45,
    "RESOLVED": 10
  },
  "alertsByLevel": {
    "CRITICAL": 5,
    "HIGH": 25,
    "MEDIUM": 45,
    "LOW": 50,
    "INFO": 25
  },
  "notificationsSent": 125,
  "averageResponseTime": "15m 30s",
  "timeRange": "24h",
  "generatedAt": "2024-01-15T12:00:00Z"
}
```

### 3.3 告警管理API

#### 3.3.1 确认告警

```http
POST /api/v1/alerts/{id}/acknowledge
```

**路径参数**:
- `id`: 告警ID

**请求体** (可选):
```json
{
  "acknowledgedBy": "admin@company.com",
  "comment": "Investigating the threat"
}
```

**请求示例**:
```bash
curl -X POST http://localhost:8084/api/v1/alerts/1/acknowledge \
  -H "Content-Type: application/json" \
  -d '{"acknowledgedBy": "admin@company.com", "comment": "Starting investigation"}'
```

**响应格式**:
```json
{
  "success": true,
  "alertId": 1,
  "status": "ACKNOWLEDGED",
  "acknowledgedAt": "2024-01-15T10:35:00Z",
  "acknowledgedBy": "admin@company.com"
}
```

#### 3.3.2 解决告警

```http
POST /api/v1/alerts/{id}/resolve
```

**路径参数**:
- `id`: 告警ID

**请求体**:
```json
{
  "resolvedBy": "admin@company.com",
  "resolution": "BLOCKED_IP",
  "comment": "Blocked attacking IP address 192.168.1.100"
}
```

**请求示例**:
```bash
curl -X POST http://localhost:8084/api/v1/alerts/1/resolve \
  -H "Content-Type: application/json" \
  -d '{
    "resolvedBy": "admin@company.com",
    "resolution": "BLOCKED_IP",
    "comment": "Blocked attacking IP and increased monitoring"
  }'
```

**响应格式**:
```json
{
  "success": true,
  "alertId": 1,
  "status": "RESOLVED",
  "resolvedAt": "2024-01-15T11:00:00Z",
  "resolvedBy": "admin@company.com",
  "resolution": "BLOCKED_IP"
}
```

#### 3.3.3 批量确认告警

```http
POST /api/v1/alerts/batch-acknowledge
```

**请求体**:
```json
{
  "alertIds": [1, 2, 3],
  "customerId": "customer-001",
  "acknowledgedBy": "admin@company.com",
  "comment": "Bulk acknowledgment for routine monitoring"
}
```

**响应格式**:
```json
{
  "results": [
    {
      "alertId": 1,
      "success": true,
      "status": "ACKNOWLEDGED"
    },
    {
      "alertId": 2,
      "success": true,
      "status": "ACKNOWLEDGED"
    }
  ],
  "summary": {
    "total": 3,
    "successful": 2,
    "failed": 1,
    "errors": [
      {
        "alertId": 3,
        "error": "Alert not found"
      }
    ]
  }
}
```

### 3.4 通知管理API

#### 3.4.1 发送Email通知

```http
POST /api/v1/alerts/notify/email
```

**请求体**:
```json
{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "admin@company.com",
  "subject": "High Threat Detected",
  "body": "A high threat has been detected from MAC AA:BB:CC:DD:EE:FF with score 125.5"
}
```

**请求示例**:
```bash
curl -X POST http://localhost:8084/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "alertId": 1,
    "customerId": "customer-001",
    "recipient": "admin@company.com",
    "subject": "High Threat Alert",
    "body": "High threat detected from MAC AA:BB:CC:DD:EE:FF"
  }'
```

**响应格式**:
```json
{
  "success": true,
  "notificationId": 1,
  "channel": "EMAIL",
  "recipient": "admin@company.com",
  "sentAt": "2024-01-15T10:32:10Z",
  "messageId": "msg-12345"
}
```

#### 3.4.2 发送SMS通知

```http
POST /api/v1/alerts/notify/sms
```

**请求体**:
```json
{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "+1234567890",
  "message": "HIGH THREAT: MAC AA:BB:CC:DD:EE:FF detected"
}
```

**响应格式**:
```json
{
  "success": true,
  "notificationId": 2,
  "channel": "SMS",
  "recipient": "+1234567890",
  "sentAt": "2024-01-15T10:32:15Z",
  "messageId": "sms-67890"
}
```

#### 3.4.3 发送Webhook通知

```http
POST /api/v1/alerts/notify/webhook
```

**请求体**:
```json
{
  "alertId": 1,
  "customerId": "customer-001",
  "webhookUrl": "https://hooks.slack.com/services/...",
  "payload": {
    "text": "High threat detected",
    "threatLevel": "HIGH",
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "threatScore": 125.5,
    "timestamp": "2024-01-15T10:32:00Z"
  }
}
```

**响应格式**:
```json
{
  "success": true,
  "notificationId": 3,
  "channel": "WEBHOOK",
  "recipient": "https://hooks.slack.com/services/...",
  "sentAt": "2024-01-15T10:32:20Z",
  "responseCode": 200
}
```

#### 3.4.4 批量发送通知

```http
POST /api/v1/alerts/notify/batch
```

**请求体**:
```json
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
    },
    {
      "channel": "WEBHOOK",
      "webhookUrl": "https://api.pagerduty.com/...",
      "payload": {
        "routing_key": "your-routing-key",
        "event_action": "trigger",
        "payload": {
          "summary": "High threat detected",
          "severity": "critical",
          "source": "threat-detection-system"
        }
      }
    }
  ]
}
```

**响应格式**:
```json
{
  "results": [
    {
      "channel": "EMAIL",
      "recipient": "admin@company.com",
      "success": true,
      "notificationId": 1
    },
    {
      "channel": "SMS",
      "recipient": "+1234567890",
      "success": true,
      "notificationId": 2
    },
    {
      "channel": "WEBHOOK",
      "recipient": "https://api.pagerduty.com/...",
      "success": true,
      "notificationId": 3
    }
  ],
  "summary": {
    "total": 3,
    "successful": 3,
    "failed": 0
  }
}
```

### 3.5 配置管理API

#### 3.5.1 获取通知配置

```http
GET /api/v1/alerts/config/notifications
```

**查询参数**:
- `customerId` (必需): 客户ID

**请求示例**:
```bash
curl "http://localhost:8084/api/v1/alerts/config/notifications?customerId=customer-001"
```

**响应格式**:
```json
{
  "customerId": "customer-001",
  "configurations": [
    {
      "id": 1,
      "channel": "EMAIL",
      "threatLevels": ["HIGH", "CRITICAL"],
      "recipients": ["admin@company.com", "security@company.com"],
      "enabled": true,
      "createdAt": "2024-01-01T00:00:00Z"
    },
    {
      "id": 2,
      "channel": "SMS",
      "threatLevels": ["CRITICAL"],
      "recipients": ["+1234567890"],
      "enabled": true,
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### 3.5.2 更新通知配置

```http
PUT /api/v1/alerts/config/notifications/{id}
```

**路径参数**:
- `id`: 配置ID

**请求体**:
```json
{
  "channel": "EMAIL",
  "threatLevels": ["HIGH", "CRITICAL"],
  "recipients": ["admin@company.com", "security@company.com", "manager@company.com"],
  "enabled": true
}
```

### 3.6 健康检查API

#### 3.6.1 服务健康状态

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
    "mail": {
      "status": "UP",
      "details": {
        "location": "smtp.gmail.com:587"
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

#### 3.6.2 性能指标

```http
GET /actuator/metrics
GET /actuator/metrics/alert.processing.duration
GET /actuator/metrics/notification.sent.count
```

---

## 4. 数据模型

### 4.1 Alert (告警)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "threat_id", nullable = false)
    private Long threatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_level", nullable = false)
    private ThreatLevel threatLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "attack_mac")
    private String attackMac;

    @Column(name = "attack_count")
    private Integer attackCount;

    @Column(name = "unique_ips")
    private Integer uniqueIps;

    @Column(name = "unique_ports")
    private Integer uniquePorts;

    @Column(name = "unique_devices")
    private Integer uniqueDevices;

    @Column(name = "threat_score")
    private Double threatScore;

    @Column(name = "assessment_time")
    private Instant assessmentTime;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution")
    private AlertResolution resolution;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

### 4.2 Notification (通知)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 4.3 NotificationConfig (通知配置)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_configs")
public class NotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "threat_levels", columnDefinition = "jsonb")
    private String threatLevels; // JSON array

    @Column(name = "recipients", columnDefinition = "jsonb")
    private String recipients; // JSON array

    @Column(name = "config", columnDefinition = "jsonb")
    private String config; // JSON object for channel-specific config

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

### 4.4 枚举类型

```java
public enum AlertStatus {
    NEW, NOTIFIED, ACKNOWLEDGED, RESOLVED
}

public enum AlertResolution {
    BLOCKED_IP, BLOCKED_MAC, ISOLATED_DEVICE, INCREASED_MONITORING,
    FALSE_POSITIVE, INVESTIGATION_ONGOING, OTHER
}

public enum NotificationChannel {
    EMAIL, SMS, WEBHOOK, SLACK, TEAMS, PAGER_DUTY
}

public enum NotificationStatus {
    PENDING, SENT, DELIVERED, FAILED, RETRYING
}
```

---

## 5. 通知渠道

### 5.1 Email通知

#### 配置

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

#### 模板示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>Threat Alert</title>
</head>
<body>
    <h1>Threat Alert: ${alert.title}</h1>
    <p><strong>Threat Level:</strong> ${alert.threatLevel}</p>
    <p><strong>Attack MAC:</strong> ${alert.attackMac}</p>
    <p><strong>Threat Score:</strong> ${alert.threatScore}</p>
    <p><strong>Description:</strong> ${alert.description}</p>
    <p><strong>Time:</strong> ${alert.createdAt}</p>
    <br>
    <p>Please investigate immediately.</p>
</body>
</html>
```

### 5.2 SMS通知

#### 支持的提供商

- Twilio
- AWS SNS
- Alibaba Cloud SMS
- Tencent Cloud SMS

#### Twilio配置示例

```yaml
notification:
  sms:
    provider: twilio
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    from-number: ${TWILIO_FROM_NUMBER}
```

### 5.3 Webhook通知

#### 支持的平台

- Slack
- Microsoft Teams
- PagerDuty
- Generic Webhooks

#### Slack Webhook示例

```json
{
  "channel": "#security-alerts",
  "username": "Threat Detection System",
  "icon_emoji": ":warning:",
  "attachments": [
    {
      "color": "danger",
      "title": "High Threat Detected",
      "text": "A high threat has been detected in the network",
      "fields": [
        {
          "title": "Threat Level",
          "value": "HIGH",
          "short": true
        },
        {
          "title": "Attack MAC",
          "value": "AA:BB:CC:DD:EE:FF",
          "short": true
        },
        {
          "title": "Threat Score",
          "value": "125.5",
          "short": true
        }
      ]
    }
  ]
}
```

### 5.4 通知重试机制

```java
@Configuration
public class NotificationRetryConfig {

    @Bean
    public RetryTemplate notificationRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // 固定间隔重试策略
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(5000); // 5秒间隔
        
        // 最大重试3次
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }
}
```

---

## 6. 使用示例

### 6.1 基本告警查询

```bash
# 获取新告警
curl "http://localhost:8084/api/v1/alerts?customerId=customer-001&status=NEW"

# 获取高危告警
curl "http://localhost:8084/api/v1/alerts?customerId=customer-001&threatLevel=HIGH"

# 获取告警统计
curl "http://localhost:8084/api/v1/alerts/stats?customerId=customer-001&timeRange=24h"
```

### 6.2 告警管理

```bash
# 确认告警
ALERT_ID=1
curl -X POST "http://localhost:8084/api/v1/alerts/$ALERT_ID/acknowledge" \
  -H "Content-Type: application/json" \
  -d '{"acknowledgedBy": "admin@company.com"}'

# 解决告警
curl -X POST "http://localhost:8084/api/v1/alerts/$ALERT_ID/resolve" \
  -H "Content-Type: application/json" \
  -d '{
    "resolvedBy": "admin@company.com",
    "resolution": "BLOCKED_IP",
    "comment": "Blocked attacking IP address"
  }'
```

### 6.3 通知发送

```bash
# 发送Email通知
curl -X POST "http://localhost:8084/api/v1/alerts/notify/email" \
  -H "Content-Type: application/json" \
  -d '{
    "alertId": 1,
    "customerId": "customer-001",
    "recipient": "admin@company.com",
    "subject": "Security Alert",
    "body": "High threat detected"
  }'

# 发送Webhook通知
curl -X POST "http://localhost:8084/api/v1/alerts/notify/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "alertId": 1,
    "customerId": "customer-001",
    "webhookUrl": "https://hooks.slack.com/services/...",
    "payload": {"text": "High threat detected"}
  }'
```

### 6.4 配置管理

```bash
# 获取通知配置
curl "http://localhost:8084/api/v1/alerts/config/notifications?customerId=customer-001"

# 更新Email配置
CONFIG_ID=1
curl -X PUT "http://localhost:8084/api/v1/alerts/config/notifications/$CONFIG_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "threatLevels": ["HIGH", "CRITICAL"],
    "recipients": ["admin@company.com", "security@company.com"],
    "enabled": true
  }'
```

---

## 7. 错误处理

### 7.1 HTTP状态码

| 状态码 | 说明 | 示例场景 |
|--------|------|----------|
| 200 | 成功 | 查询成功 |
| 201 | 创建成功 | 通知发送成功 |
| 400 | 请求错误 | 参数无效 |
| 404 | 资源不存在 | 告警ID不存在 |
| 409 | 冲突 | 告警已被确认 |
| 422 | 业务逻辑错误 | 通知渠道不可用 |
| 500 | 服务器错误 | 邮件服务失败 |

### 7.2 错误响应格式

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for alert acknowledgment",
  "details": {
    "alertId": "Alert not found or already acknowledged",
    "customerId": "Access denied for customer"
  },
  "path": "/api/v1/alerts/1/acknowledge",
  "requestId": "req-12345"
}
```

### 7.3 常见错误

#### 7.3.1 通知发送失败

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 422,
  "error": "Notification Failed",
  "message": "Failed to send email notification",
  "details": {
    "channel": "EMAIL",
    "recipient": "admin@company.com",
    "error": "SMTP server connection failed",
    "retryable": true,
    "nextRetryAt": "2024-01-15T10:35:00Z"
  }
}
```

#### 7.3.2 配置错误

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 422,
  "error": "Configuration Error",
  "message": "Invalid notification configuration",
  "details": {
    "channel": "SMS",
    "error": "Missing required configuration: account_sid",
    "suggestion": "Configure TWILIO_ACCOUNT_SID environment variable"
  }
}
```

#### 7.3.3 业务逻辑错误

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Alert status conflict",
  "details": {
    "alertId": 1,
    "currentStatus": "RESOLVED",
    "requestedOperation": "ACKNOWLEDGE",
    "message": "Cannot acknowledge a resolved alert"
  }
}
```

---

## 8. 性能指标

### 8.1 响应时间

| 操作 | 平均响应时间 | 95%响应时间 | 99%响应时间 |
|------|--------------|--------------|--------------|
| 告警查询 | 50ms | 100ms | 200ms |
| 告警确认 | 100ms | 200ms | 500ms |
| Email发送 | 500ms | 1000ms | 2000ms |
| SMS发送 | 300ms | 800ms | 1500ms |
| Webhook发送 | 200ms | 500ms | 1000ms |

### 8.2 吞吐量

| 操作 | 目标QPS | 当前QPS | 并发用户数 |
|------|---------|---------|------------|
| 告警查询 | 200 | 300 | 100 |
| 告警确认 | 50 | 75 | 25 |
| 通知发送 | 100 | 150 | 50 |

### 8.3 资源使用

| 指标 | 平均值 | 峰值 | 阈值 |
|------|--------|------|------|
| CPU使用率 | 20% | 50% | 80% |
| 内存使用 | 256MB | 512MB | 1GB |
| 数据库连接 | 5 | 15 | 30 |
| 邮件队列长度 | 0 | 10 | 50 |

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
      service: alert-management
      version: "2.1"
```

**关键指标**:
- `alert_processing_duration`: 告警处理耗时
- `notification_send_duration`: 通知发送耗时
- `alert_acknowledged_count`: 确认告警数
- `notification_failed_count`: 失败通知数
- `alert_deduplication_rate`: 去重率

---

**文档结束**

---

## 2. 核心功能

### 2.1 威胁消费与评估

**功能描述**: 监听threat-alerts主题，对每个威胁警报执行深度分析

**处理流程**:
1. 消费Kafka消息 (threat-alerts)
2. 解析威胁数据 (threatScore, attackMac, etc.)
3. 执行威胁分类和优先级评估
4. 生成缓解建议
5. 持久化到数据库
6. 发布评估结果 (可选)

### 2.2 情报关联

**功能描述**: 将当前威胁与已知威胁情报进行关联分析

**情报来源**:
- 内部威胁数据库
- 已知攻击者MAC/IP映射
- 历史威胁模式
- 自定义威胁规则

### 2.3 缓解建议生成

**功能描述**: 基于威胁评估结果生成具体的缓解措施

**建议类型**:
- **BLOCK_IP**: 封禁攻击IP
- **RATE_LIMIT**: 实施速率限制
- **ISOLATE_HOST**: 隔离受影响主机
- **MONITOR**: 加强监控
- **ALERT_ADMIN**: 通知管理员
- **QUARANTINE**: 网络隔离

### 2.4 数据持久化

**存储内容**:
- 威胁评估结果
- 缓解建议
- 处理时间戳
- 关联情报
- 执行状态

---

## 3. API端点

### 3.1 基础信息

- **Base URL**: `/api/v1/assessment`
- **认证**: 无 (开发环境)
- **数据格式**: JSON
- **字符编码**: UTF-8
- **分页**: 支持 `limit` 和 `offset` 参数

### 3.2 威胁评估查询

#### 3.2.1 查询威胁评估列表

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

#### 3.2.2 获取特定威胁评估

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

### 3.3 缓解建议管理

#### 3.3.1 获取威胁缓解建议

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

### 3.4 威胁情报查询

#### 3.4.1 查询威胁情报

```http
GET /api/v1/assessment/intelligence
```

**查询参数**:
- `customerId` (必需): 客户ID
- `attackMac` (可选): 攻击者MAC地址
- `attackIp` (可选): 攻击者IP地址
- `threatLevel` (可选): 威胁等级

**请求示例**:
```bash
curl "http://localhost:8083/api/v1/assessment/intelligence?customerId=customer-001&attackMac=AA:BB:CC:DD:EE:FF"
```

**响应格式**:
```json
{
  "data": [
    {
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "attackIp": "192.168.1.100",
      "knownAttacker": false,
      "firstSeen": "2024-01-10T08:00:00Z",
      "lastSeen": "2024-01-15T10:32:00Z",
      "totalIncidents": 5,
      "threatLevels": ["MEDIUM", "HIGH", "HIGH"],
      "campaignId": null,
      "threatActor": null,
      "malwareFamily": null,
      "cveReferences": [],
      "similarIncidents": 3,
      "recommendations": ["BLOCK_IP", "ISOLATE_HOST"]
    }
  ],
  "total": 1
}
```

### 3.5 统计查询

#### 3.5.1 获取评估统计

```http
GET /api/v1/assessment/stats
```

**查询参数**:
- `customerId` (必需): 客户ID
- `startTime` (可选): 开始时间
- `endTime` (可选): 结束时间

**请求示例**:
```bash
curl "http://localhost:8083/api/v1/assessment/stats?customerId=customer-001&startTime=2024-01-15T00:00:00Z"
```

**响应格式**:
```json
{
  "customerId": "customer-001",
  "timeRange": {
    "start": "2024-01-15T00:00:00Z",
    "end": "2024-01-15T23:59:59Z"
  },
  "summary": {
    "totalAssessments": 45,
    "criticalThreats": 2,
    "highThreats": 8,
    "mediumThreats": 15,
    "lowThreats": 12,
    "infoThreats": 8
  },
  "topAttackers": [
    {
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "attackCount": 150,
      "threatScore": 125.5,
      "lastSeen": "2024-01-15T10:32:00Z"
    }
  ],
  "recommendations": {
    "totalGenerated": 67,
    "executed": 23,
    "pending": 44,
    "byAction": {
      "BLOCK_IP": 15,
      "MONITOR": 28,
      "ISOLATE_HOST": 8,
      "RATE_LIMIT": 16
    }
  }
}
```

---

## 4. 数据模型

### 4.1 ThreatAssessment (威胁评估)

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
    
    // 关联数据
    private List<Recommendation> recommendations;
    private ThreatIntelligence intelligence;
}
```

### 4.2 Recommendation (缓解建议)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {
    private Long id;
    private Long assessmentId;
    private String customerId;
    private RecommendationAction action;
    private Priority priority;
    private String description;
    private JsonNode parameters;  // JSON对象
    private Boolean executed;
    private Instant executedAt;
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
    private String attackMac;
    private String attackIp;
    private Boolean knownAttacker;
    private Instant firstSeen;
    private Instant lastSeen;
    private Integer totalIncidents;
    private List<String> threatLevels;
    private String campaignId;
    private String threatActor;
    private String malwareFamily;
    private List<String> cveReferences;
    private Integer similarIncidents;
    private List<String> recommendations;
}
```

### 4.4 枚举定义

#### 4.4.1 ThreatLevel

```java
public enum ThreatLevel {
    INFO("信息", 0, 50),
    LOW("低危", 50, 200),
    MEDIUM("中危", 200, 500),
    HIGH("高危", 500, 1000),
    CRITICAL("严重", 1000, Integer.MAX_VALUE);
}
```

#### 4.4.2 RecommendationAction

```java
public enum RecommendationAction {
    BLOCK_IP("封禁IP"),
    RATE_LIMIT("限速"),
    ISOLATE_HOST("隔离主机"),
    MONITOR("加强监控"),
    ALERT_ADMIN("通知管理员"),
    QUARANTINE("隔离网络");
}
```

#### 4.4.3 Priority

```java
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

---

## 5. 威胁评分算法

### 5.1 算法概述

威胁评估服务接收来自Flink的初步威胁评分，并执行更深入的分析：

1. **基础评分验证**: 验证Flink计算的threatScore
2. **上下文增强**: 加入历史行为模式
3. **情报关联**: 匹配已知威胁特征
4. **动态权重调整**: 基于当前环境调整评分
5. **最终评估**: 生成综合威胁等级

### 5.2 增强评分因子

#### 5.2.1 历史行为因子

```java
private double calculateHistoricalFactor(String attackMac, String customerId) {
    // 查询过去24小时的行为
    int pastIncidents = queryPastIncidents(attackMac, customerId, 24);
    
    if (pastIncidents == 0) return 1.0;        // 新威胁
    if (pastIncidents <= 3) return 1.2;       // 偶尔出现
    if (pastIncidents <= 10) return 1.5;      // 频繁出现
    return 2.0;                               // 持续威胁
}
```

#### 5.2.2 情报关联因子

```java
private double calculateIntelligenceFactor(String attackMac, String attackIp) {
    ThreatIntelligence intel = intelligenceService.getIntelligence(attackMac, attackIp);
    
    double factor = 1.0;
    if (intel.isKnownAttacker()) factor *= 2.0;
    if (intel.getCampaignId() != null) factor *= 1.5;
    if (intel.getThreatActor() != null) factor *= 1.8;
    if (!intel.getCveReferences().isEmpty()) factor *= 1.3;
    
    return factor;
}
```

#### 5.2.3 业务影响因子

```java
private double calculateBusinessImpactFactor(AggregatedAttackData data) {
    // 基于受影响的设备数量和端口重要性
    double deviceFactor = Math.min(data.getUniqueDevices() * 0.2 + 1.0, 2.0);
    double portFactor = calculatePortCriticality(data.getUniquePorts());
    
    return deviceFactor * portFactor;
}
```

### 5.3 最终评分计算

```java
public ThreatAssessment assessThreat(ThreatAlert alert) {
    double baseScore = alert.getThreatScore().doubleValue();
    
    // 应用增强因子
    double historicalFactor = calculateHistoricalFactor(alert.getAttackMac(), alert.getCustomerId());
    double intelligenceFactor = calculateIntelligenceFactor(alert.getAttackMac(), alert.getAttackIp());
    double businessFactor = calculateBusinessImpactFactor(alert.getAggregatedData());
    
    // 计算最终评分
    double finalScore = baseScore * historicalFactor * intelligenceFactor * businessFactor;
    
    // 确定威胁等级
    ThreatLevel finalLevel = ThreatLevel.fromScore(finalScore);
    
    // 生成建议
    List<Recommendation> recommendations = generateRecommendations(alert, finalLevel);
    
    return ThreatAssessment.builder()
        .customerId(alert.getCustomerId())
        .attackMac(alert.getAttackMac())
        .threatScore(BigDecimal.valueOf(finalScore))
        .threatLevel(finalLevel)
        .attackCount(alert.getAttackCount())
        .uniqueIps(alert.getUniqueIps())
        .uniquePorts(alert.getUniquePorts())
        .uniqueDevices(alert.getUniqueDevices())
        .assessmentTime(Instant.now())
        .recommendations(recommendations)
        .build();
}
```

---

## 6. 使用示例

### 6.1 完整工作流

```bash
# 1. 发送攻击事件
curl -X POST http://localhost:8080/api/v1/logs/ingest \
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

# 2. 等待处理 (约2分钟)
sleep 120

# 3. 查询威胁评估
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&limit=1"

# 4. 查看缓解建议
THREAT_ID=$(curl -s "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&limit=1" | jq -r '.data[0].id')
curl "http://localhost:8083/api/v1/assessment/recommendations/$THREAT_ID"

# 5. 执行建议 (如果需要)
curl -X POST "http://localhost:8083/api/v1/assessment/recommendations/1/execute"
```

### 6.2 批量查询

```bash
# 查询高危威胁
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&threatLevel=HIGH&startTime=2024-01-15T00:00:00Z"

# 分页查询
curl "http://localhost:8083/api/v1/assessment/threats?customerId=customer-001&limit=20&offset=0"

# 查询统计信息
curl "http://localhost:8083/api/v1/assessment/stats?customerId=customer-001&startTime=2024-01-15T00:00:00Z"
```

### 6.3 监控集成

```bash
# 健康检查
curl http://localhost:8083/actuator/health

# 性能指标
curl http://localhost:8083/actuator/metrics

# 自定义指标
curl http://localhost:8083/actuator/metrics/threat.assessment.duration
curl http://localhost:8083/actuator/metrics/threat.assessment.success.rate
```

---

## 7. 错误处理

### 7.1 HTTP状态码

| 状态码 | 含义 | 说明 |
|--------|------|------|
| 200 | 成功 | 请求成功处理 |
| 400 | 请求错误 | 参数无效或缺失 |
| 404 | 未找到 | 威胁评估不存在 |
| 500 | 服务器错误 | 内部处理错误 |

### 7.2 错误响应格式

```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "customerId is required",
  "path": "/api/v1/assessment/threats"
}
```

### 7.3 常见错误

#### 7.3.1 客户ID缺失

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required request parameter 'customerId' is missing"
}
```

#### 7.3.2 威胁评估不存在

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Threat assessment with id 999 not found"
}
```

#### 7.3.3 数据库连接错误

```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to connect to database"
}
```

### 7.4 重试机制

服务实现自动重试机制：
- **Kafka消费失败**: 自动重试3次，失败后发送到死信队列
- **数据库操作失败**: 使用Spring Retry机制，最多重试2次
- **外部服务调用**: 使用Resilience4j熔断器

---

## 8. 性能指标

### 8.1 基准性能

| 指标 | 目标值 | 当前值 |
|------|--------|--------|
| 评估处理时间 | < 100ms | 80ms |
| 并发处理能力 | 100 req/s | 120 req/s |
| 数据库查询时间 | < 50ms | 30ms |
| 内存使用 | < 512MB | 380MB |
| CPU使用率 | < 70% | 45% |

### 8.2 监控指标

#### 8.2.1 应用指标

- `threat.assessment.duration`: 评估处理时间
- `threat.assessment.success.rate`: 成功率
- `threat.assessment.error.rate`: 错误率
- `threat.recommendation.generated`: 生成建议数
- `threat.recommendation.executed`: 执行建议数

#### 8.2.2 系统指标

- JVM内存使用率
- GC暂停时间
- 数据库连接池状态
- Kafka消费者延迟
- HTTP请求响应时间

### 8.3 扩展策略

#### 8.3.1 水平扩展

```bash
# 增加服务实例
docker-compose up -d --scale threat-assessment=3
```

#### 8.3.2 垂直扩展

```yaml
# 增加资源限制
threat-assessment:
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 2G
      reservations:
        cpus: '1.0'
        memory: 1G
```

#### 8.3.3 数据库优化

- 添加适当索引
- 读写分离 (可选)
- 连接池调优

---

**文档结束**</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/reviewers/threat_assessment_overview.md