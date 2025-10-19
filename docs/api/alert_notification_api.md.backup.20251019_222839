# 告警通知API文档

**版本**: 1.0  
**服务**: Alert Management Service  
**端口**: 8082  
**基础路径**: `/api/v1/alerts/notify`

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [通知渠道](#3-通知渠道)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
   - 6.1 [发送邮件通知](#61-发送邮件通知)
   - 6.2 [发送SMS通知](#62-发送sms通知)
   - 6.3 [发送Webhook通知](#63-发送webhook通知)
   - 6.4 [配置通知模板](#64-配置通知模板)
   - 6.5 [查询通知历史](#65-查询通知历史)
   - 6.6 [批量发送通知](#66-批量发送通知)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

告警通知系统负责通过多种渠道(Email、SMS、Webhook)向相关人员或系统发送威胁告警通知,确保安全事件得到及时响应。

### 核心特性

- **多渠道支持**: Email、SMS、Webhook、企业微信、钉钉
- **模板管理**: 可配置的通知模板,支持变量替换
- **优先级路由**: 根据告警严重性选择通知渠道
- **失败重试**: 自动重试机制,确保通知送达
- **通知历史**: 完整的通知发送记录和审计日志
- **批量发送**: 支持批量发送和群组通知

### 工作流程

```
告警创建/更新 → 通知规则匹配 → 渠道选择
                                    ↓
                            模板渲染 (变量替换)
                                    ↓
                            发送通知 (Email/SMS/Webhook)
                                    ↓
                            记录历史 → 失败重试
```

### 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| **邮件服务** | Spring Mail + SMTP | 邮件发送 |
| **短信服务** | 阿里云SMS/腾讯云SMS | 短信发送 |
| **HTTP客户端** | RestTemplate/WebClient | Webhook调用 |
| **模板引擎** | Thymeleaf/FreeMarker | 模板渲染 |
| **消息队列** | Kafka | 异步通知处理 |
| **数据库** | PostgreSQL | 模板和历史存储 |

---

## 2. 核心功能

### 2.1 通知触发条件

| 触发类型 | 条件 | 示例 |
|---------|------|------|
| **告警创建** | 新告警生成时 | CRITICAL告警立即通知 |
| **状态变更** | 告警状态改变 | OPEN → IN_PROGRESS |
| **升级通知** | 告警被升级 | L1 → L2升级 |
| **定时摘要** | 定时发送汇总报告 | 每日告警摘要 |
| **手动触发** | 用户手动发送 | 补发通知、测试配置 |

### 2.2 通知优先级

| 严重性 | 通知渠道 | 延迟 | 重试次数 |
|-------|---------|------|---------|
| **CRITICAL** | Email + SMS + Webhook | 立即 | 5次 |
| **HIGH** | Email + Webhook | < 5分钟 | 3次 |
| **MEDIUM** | Email | < 15分钟 | 2次 |
| **LOW** | Email (批量) | < 1小时 | 1次 |

### 2.3 通知内容

- **告警基本信息**: ID、严重性、威胁评分、攻击源
- **检测详情**: 攻击类型、目标端口、设备信息
- **建议措施**: 缓解建议、操作链接
- **时间信息**: 检测时间、通知时间

---

## 3. 通知渠道

### 3.1 Email通知

**特点**:
- 支持HTML格式
- 可附加详细报告
- 支持抄送/密送
- 成本低

**适用场景**: 所有告警级别

### 3.2 SMS短信通知

**特点**:
- 即时送达
- 内容简洁(70字符限制)
- 成本较高

**适用场景**: CRITICAL/HIGH告警

### 3.3 Webhook通知

**特点**:
- 系统集成
- JSON格式数据
- 支持认证

**适用场景**: 第三方系统集成、自动化响应

### 3.4 企业通讯工具

**支持平台**:
- 企业微信
- 钉钉
- Slack
- Microsoft Teams

---

## 4. 数据模型

### 4.1 数据库表结构

```sql
-- 通知模板表
CREATE TABLE notification_templates (
    id SERIAL PRIMARY KEY,
    template_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    channel VARCHAR(50) NOT NULL,  -- EMAIL, SMS, WEBHOOK
    subject_template TEXT,
    content_template TEXT NOT NULL,
    variables JSONB,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_channel (channel),
    INDEX idx_enabled (enabled)
);

-- 通知历史表
CREATE TABLE notification_history (
    id SERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    content TEXT,
    template_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,  -- SENT, FAILED, PENDING, RETRYING
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (alert_id) REFERENCES alerts(id),
    INDEX idx_alert_id (alert_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_channel (channel),
    INDEX idx_status (status),
    INDEX idx_sent_at (sent_at)
);

-- 通知配置表
CREATE TABLE notification_configs (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    channels JSONB NOT NULL,  -- ["EMAIL", "SMS"]
    recipients JSONB NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_severity (customer_id, severity)
);
```

### 4.2 Java实体类

```java
/**
 * 通知模板
 */
@Data
@Builder
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String templateId;
    
    private String name;
    
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;
    
    private String subjectTemplate;
    
    @Column(columnDefinition = "TEXT")
    private String contentTemplate;
    
    @Column(columnDefinition = "jsonb")
    private String variables;  // JSON格式的变量定义
    
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

/**
 * 通知历史
 */
@Data
@Builder
@Entity
@Table(name = "notification_history")
public class NotificationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long alertId;
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;
    
    private String recipient;
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String templateId;
    
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;
    
    private String errorMessage;
    private Integer retryCount;
    private Instant sentAt;
    private Instant createdAt;
}

/**
 * 通知渠道枚举
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    WEBHOOK,
    WECHAT_WORK,
    DINGTALK,
    SLACK
}

/**
 * 通知状态枚举
 */
public enum NotificationStatus {
    SENT,      // 已发送
    FAILED,    // 发送失败
    PENDING,   // 等待发送
    RETRYING   // 重试中
}
```

### 4.3 请求/响应DTO

```java
/**
 * 邮件通知请求
 */
@Data
public class SendEmailRequest {
    private String recipient;          // 收件人
    private String subject;            // 主题
    private String content;            // 内容
    private Long alertId;              // 关联告警ID (可选)
    private List<String> cc;           // 抄送 (可选)
    private List<String> bcc;          // 密送 (可选)
    private Map<String, Object> variables;  // 模板变量 (可选)
}

/**
 * SMS通知请求
 */
@Data
public class SendSmsRequest {
    private String phoneNumber;
    private String message;
    private Long alertId;
}

/**
 * Webhook通知请求
 */
@Data
public class SendWebhookRequest {
    private String url;
    private String method;  // POST, PUT
    private Map<String, String> headers;
    private Map<String, Object> payload;
    private Long alertId;
}

/**
 * 通知响应
 */
@Data
@Builder
public class NotificationResponse {
    private Boolean success;
    private String message;
    private Long notificationId;
    private String recipient;
    private Instant sentAt;
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 认证 |
|------|------|------|------|
| `POST` | `/api/v1/alerts/notify/email` | 发送邮件通知 | ✅ |
| `POST` | `/api/v1/alerts/notify/sms` | 发送SMS通知 | ✅ |
| `POST` | `/api/v1/alerts/notify/webhook` | 发送Webhook通知 | ✅ |
| `POST` | `/api/v1/alerts/notify/templates` | 创建/更新通知模板 | ✅ Admin |
| `GET` | `/api/v1/alerts/notify/templates` | 查询模板列表 | ✅ |
| `GET` | `/api/v1/alerts/{id}/notifications` | 查询告警通知历史 | ✅ |
| `POST` | `/api/v1/alerts/notify/batch` | 批量发送通知 | ✅ |

---

## 6. API详细文档

### 6.1 发送邮件通知

### 6.1 发送邮件通知

**描述**: 手动发送邮件通知,支持HTML格式和模板变量替换。

**端点**: `POST /api/v1/alerts/notify/email`

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `recipient` | String | ✅ | 收件人邮箱 |
| `subject` | String | ✅ | 邮件主题 |
| `content` | String | ✅ | 邮件内容 (支持HTML) |
| `alertId` | Long | ❌ | 关联告警ID |
| `cc` | List<String> | ❌ | 抄送列表 |
| `bcc` | List<String> | ❌ | 密送列表 |
| `variables` | Map | ❌ | 模板变量 |

### 功能说明

手动发送邮件通知,适用于:
- 测试邮件配置
- 手动补发通知
- 自定义通知内容
- 发送告警摘要报告

### 请求示例 (curl)

```bash
# 基本邮件发送
curl -X POST http://localhost:8082/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "security-team@company.com",
    "subject": "🚨 CRITICAL: 检测到严重威胁",
    "content": "检测到CRITICAL级别威胁,请立即处理!",
    "alertId": 12345
  }'

# 带抄送的邮件
curl -X POST http://localhost:8082/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "security-lead@company.com",
    "subject": "威胁告警通知",
    "content": "<h1>威胁检测告警</h1><p>攻击源IP: 192.168.75.188</p>",
    "alertId": 12345,
    "cc": ["team-lead@company.com"],
    "bcc": ["audit@company.com"]
  }'
```

### 请求示例 (Java)

```java
public NotificationResponse sendEmailNotification(
        String recipient,
        String subject,
        String content,
        Long alertId) {
    
    SendEmailRequest request = new SendEmailRequest();
    request.setRecipient(recipient);
    request.setSubject(subject);
    request.setContent(content);
    request.setAlertId(alertId);
    
    HttpEntity<SendEmailRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
        BASE_URL + "/notify/email",
        httpRequest,
        NotificationResponse.class
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "Email notification sent successfully",
  "notificationId": 1001,
  "recipient": "security-team@company.com",
  "sentAt": "2025-01-15T10:30:00Z"
}
```

**HTTP 500 Internal Server Error**

```json
{
  "success": false,
  "message": "Failed to send email: SMTP connection timeout",
  "recipient": "security-team@company.com"
}
```

---

### 6.2 发送SMS通知

**描述**: 发送短信通知,适用于CRITICAL/HIGH级别告警的紧急通知。

**端点**: `POST /api/v1/alerts/notify/sms`

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `phoneNumber` | String | ✅ | 手机号码 |
| `message` | String | ✅ | 短信内容 (最多70字符) |
| `alertId` | Long | ❌ | 关联告警ID |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/notify/sms \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "+86138****8888",
    "message": "CRITICAL告警: 检测到攻击源192.168.75.188,请立即处理!",
    "alertId": 12345
  }'
```

### 请求示例 (Java)

```java
public NotificationResponse sendSmsNotification(
        String phoneNumber,
        String message,
        Long alertId) {
    
    SendSmsRequest request = new SendSmsRequest();
    request.setPhoneNumber(phoneNumber);
    request.setMessage(message);
    request.setAlertId(alertId);
    
    HttpEntity<SendSmsRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
        BASE_URL + "/notify/sms",
        httpRequest,
        NotificationResponse.class
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "SMS sent successfully",
  "notificationId": 1002,
  "recipient": "+86138****8888",
  "sentAt": "2025-01-15T10:30:05Z"
}
```

---

### 6.3 发送Webhook通知

**描述**: 向指定URL发送HTTP请求,用于系统集成和自动化响应。

**端点**: `POST /api/v1/alerts/notify/webhook`

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `url` | String | ✅ | Webhook URL |
| `method` | String | ❌ | HTTP方法 (默认POST) |
| `headers` | Map | ❌ | 自定义HTTP头 |
| `payload` | Map | ✅ | 请求体数据 |
| `alertId` | Long | ❌ | 关联告警ID |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/notify/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://hooks.slack.com/services/YOUR/WEBHOOK/URL",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer token123"
    },
    "payload": {
      "text": "CRITICAL威胁告警",
      "attachments": [
        {
          "color": "danger",
          "title": "攻击源: 192.168.75.188",
          "text": "威胁评分: 7290.0"
        }
      ]
    },
    "alertId": 12345
  }'
```

### 请求示例 (Java)

```java
public NotificationResponse sendWebhookNotification(
        String url,
        Map<String, Object> payload,
        Long alertId) {
    
    SendWebhookRequest request = new SendWebhookRequest();
    request.setUrl(url);
    request.setMethod("POST");
    request.setPayload(payload);
    request.setAlertId(alertId);
    
    HttpEntity<SendWebhookRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
        BASE_URL + "/notify/webhook",
        httpRequest,
        NotificationResponse.class
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "Webhook notification sent successfully",
  "notificationId": 1003,
  "recipient": "https://hooks.slack.com/services/...",
  "sentAt": "2025-01-15T10:30:10Z"
}
```

---

### 6.4 配置通知模板

**描述**: 创建或更新通知模板,支持变量占位符。

**端点**: `POST /api/v1/alerts/notify/templates`

**权限**: 需要管理员权限

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `templateId` | String | ✅ | 模板唯一标识 |
| `name` | String | ✅ | 模板名称 |
| `channel` | String | ✅ | 通知渠道 (EMAIL/SMS/WEBHOOK) |
| `subjectTemplate` | String | ❌ | 主题模板 (EMAIL适用) |
| `contentTemplate` | String | ✅ | 内容模板 |
| `variables` | List | ❌ | 支持的变量列表 |

### 模板变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{alertId}}` | 告警ID | 12345 |
| `{{severity}}` | 严重性 | CRITICAL |
| `{{threatScore}}` | 威胁评分 | 7290.0 |
| `{{attackIp}}` | 攻击源IP | 192.168.75.188 |
| `{{attackMac}}` | 攻击源MAC | 04:42:1a:8e:e3:65 |
| `{{customerId}}` | 客户ID | customer_a |
| `{{detectedAt}}` | 检测时间 | 2025-01-15T10:30:00Z |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/notify/templates \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "critical-alert-email",
    "name": "CRITICAL告警邮件模板",
    "channel": "EMAIL",
    "subjectTemplate": "🚨 CRITICAL: {{customerId}} - 告警 #{{alertId}}",
    "contentTemplate": "<h1>CRITICAL级别威胁告警</h1><p>威胁评分: {{threatScore}}</p><p>攻击源: {{attackIp}}</p><p>检测时间: {{detectedAt}}</p>",
    "variables": ["alertId", "severity", "threatScore", "attackIp", "customerId", "detectedAt"]
  }'
```

### 响应示例

**HTTP 201 Created**

```json
{
  "success": true,
  "message": "Template created successfully",
  "templateId": "critical-alert-email"
}
```

---

### 6.5 查询通知历史

**描述**: 查询指定告警的通知发送历史。

**端点**: `GET /api/v1/alerts/{id}/notifications`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**查询参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `channel` | String | ❌ | 过滤渠道 |
| `status` | String | ❌ | 过滤状态 |

### 请求示例 (curl)

```bash
# 查询所有通知
curl -X GET http://localhost:8082/api/v1/alerts/12345/notifications

# 过滤邮件通知
curl -X GET "http://localhost:8082/api/v1/alerts/12345/notifications?channel=EMAIL"
```

### 请求示例 (Java)

```java
public List<NotificationHistory> getNotificationHistory(Long alertId) {
    String url = BASE_URL + "/" + alertId + "/notifications";
    
    ResponseEntity<List<NotificationHistory>> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<List<NotificationHistory>>() {}
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
[
  {
    "id": 1001,
    "alertId": 12345,
    "channel": "EMAIL",
    "recipient": "security-team@company.com",
    "subject": "🚨 CRITICAL威胁告警",
    "status": "SENT",
    "retryCount": 0,
    "sentAt": "2025-01-15T10:30:00Z",
    "createdAt": "2025-01-15T10:29:55Z"
  },
  {
    "id": 1002,
    "alertId": 12345,
    "channel": "SMS",
    "recipient": "+86138****8888",
    "status": "SENT",
    "retryCount": 0,
    "sentAt": "2025-01-15T10:30:05Z",
    "createdAt": "2025-01-15T10:29:55Z"
  },
  {
    "id": 1003,
    "alertId": 12345,
    "channel": "WEBHOOK",
    "recipient": "https://hooks.slack.com/...",
    "status": "FAILED",
    "errorMessage": "Connection timeout",
    "retryCount": 3,
    "createdAt": "2025-01-15T10:29:55Z"
  }
]
```

---

### 6.6 批量发送通知

**描述**: 向多个收件人批量发送通知。

**端点**: `POST /api/v1/alerts/notify/batch`

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `channel` | String | ✅ | 通知渠道 |
| `recipients` | List<String> | ✅ | 收件人列表 (最多100个) |
| `subject` | String | ❌ | 主题 (EMAIL适用) |
| `content` | String | ✅ | 内容 |
| `alertIds` | List<Long> | ❌ | 关联告警ID列表 |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/notify/batch \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "recipients": [
      "security-team@company.com",
      "oncall-engineer@company.com",
      "team-lead@company.com"
    ],
    "subject": "每日威胁告警摘要",
    "content": "今日检测到25个威胁告警,其中CRITICAL级别5个...",
    "alertIds": [12345, 12346, 12347]
  }'
```

### 响应示例

**HTTP 200 OK**

```json
{
  "success": true,
  "totalRequested": 3,
  "successCount": 2,
  "failureCount": 1,
  "results": [
    {
      "recipient": "security-team@company.com",
      "success": true,
      "notificationId": 1004
    },
    {
      "recipient": "oncall-engineer@company.com",
      "success": true,
      "notificationId": 1005
    },
    {
      "recipient": "team-lead@company.com",
      "success": false,
      "error": "Invalid email address"
    }
  ]
}
```

---

## 7. 使用场景

### 场景1: CRITICAL告警多渠道通知

**需求**: CRITICAL告警需要同时发送Email、SMS和Webhook通知。

**实现**:

```java
@Service
@Slf4j
public class MultiChannelNotificationService {
    
    private final NotificationClient notificationClient;
    private final NotificationConfigRepository configRepository;
    
    /**
     * 发送多渠道通知
     */
    public void sendCriticalAlertNotification(Alert alert) {
        if (alert.getSeverity() != AlertSeverity.CRITICAL) {
            return;
        }
        
        log.warn("Sending CRITICAL alert notifications: alertId={}", alert.getId());
        
        // 1. 发送邮件通知
        try {
            String emailSubject = String.format(
                "🚨 CRITICAL: %s - 告警 #%d",
                alert.getCustomerId(),
                alert.getId()
            );
            
            String emailContent = buildEmailContent(alert);
            
            notificationClient.sendEmail(
                "security-team@company.com",
                emailSubject,
                emailContent,
                alert.getId()
            );
            
            log.info("Email sent: alertId={}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send email: alertId={}", alert.getId(), e);
        }
        
        // 2. 发送SMS通知
        try {
            String smsMessage = String.format(
                "CRITICAL告警#%d: 攻击源%s, 威胁评分%.0f, 请立即处理!",
                alert.getId(),
                alert.getAttackIp(),
                alert.getThreatScore()
            );
            
            notificationClient.sendSms(
                "+86138****8888",
                smsMessage,
                alert.getId()
            );
            
            log.info("SMS sent: alertId={}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send SMS: alertId={}", alert.getId(), e);
        }
        
        // 3. 发送Webhook通知 (Slack)
        try {
            Map<String, Object> slackPayload = buildSlackPayload(alert);
            
            notificationClient.sendWebhook(
                "https://hooks.slack.com/services/YOUR/WEBHOOK/URL",
                slackPayload,
                alert.getId()
            );
            
            log.info("Webhook sent: alertId={}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send webhook: alertId={}", alert.getId(), e);
        }
    }
    
    /**
     * 构建邮件内容
     */
    private String buildEmailContent(Alert alert) {
        return String.format(
            "<html><body>" +
            "<h1 style='color:red;'>🚨 CRITICAL级别威胁告警</h1>" +
            "<table>" +
            "<tr><td><b>告警ID:</b></td><td>%d</td></tr>" +
            "<tr><td><b>威胁评分:</b></td><td>%.2f</td></tr>" +
            "<tr><td><b>攻击源IP:</b></td><td>%s</td></tr>" +
            "<tr><td><b>攻击源MAC:</b></td><td>%s</td></tr>" +
            "<tr><td><b>客户ID:</b></td><td>%s</td></tr>" +
            "<tr><td><b>检测时间:</b></td><td>%s</td></tr>" +
            "</table>" +
            "<h2>建议措施:</h2>" +
            "<ol>" +
            "<li>立即隔离攻击源 %s</li>" +
            "<li>检查同网段其他主机</li>" +
            "<li>审计网络访问日志</li>" +
            "</ol>" +
            "<p><a href='http://threat-detection.company.com/alerts/%d'>查看详情</a></p>" +
            "</body></html>",
            alert.getId(),
            alert.getThreatScore(),
            alert.getAttackIp(),
            alert.getAttackMac(),
            alert.getCustomerId(),
            alert.getCreatedAt(),
            alert.getAttackIp(),
            alert.getId()
        );
    }
    
    /**
     * 构建Slack Webhook payload
     */
    private Map<String, Object> buildSlackPayload(Alert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "🚨 CRITICAL威胁告警");
        
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", "danger");
        attachment.put("title", "告警 #" + alert.getId());
        attachment.put("text", String.format(
            "攻击源: %s\n威胁评分: %.2f\n检测时间: %s",
            alert.getAttackIp(),
            alert.getThreatScore(),
            alert.getCreatedAt()
        ));
        
        payload.put("attachments", Collections.singletonList(attachment));
        
        return payload;
    }
}
```

---

### 场景2: 每日告警摘要报告

**需求**: 每日早上8点发送前一天的告警摘要报告。

**实现**:

```java
@Component
@Slf4j
public class DailyAlertDigestService {
    
    private final AlertRepository alertRepository;
    private final NotificationClient notificationClient;
    
    /**
     * 每日早上8点发送摘要
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyDigest() {
        log.info("Generating daily alert digest");
        
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant today = Instant.now();
        
        // 统计昨日告警
        List<Alert> alerts = alertRepository.findByCreatedAtBetween(yesterday, today);
        
        Map<AlertSeverity, Long> severityCount = alerts.stream()
            .collect(Collectors.groupingBy(Alert::getSeverity, Collectors.counting()));
        
        long criticalCount = severityCount.getOrDefault(AlertSeverity.CRITICAL, 0L);
        long highCount = severityCount.getOrDefault(AlertSeverity.HIGH, 0L);
        long mediumCount = severityCount.getOrDefault(AlertSeverity.MEDIUM, 0L);
        long lowCount = severityCount.getOrDefault(AlertSeverity.LOW, 0L);
        
        // 构建摘要内容
        String subject = String.format(
            "威胁告警日报 - %s (%d个告警)",
            LocalDate.now().minusDays(1),
            alerts.size()
        );
        
        String content = String.format(
            "<html><body>" +
            "<h1>威胁告警日报</h1>" +
            "<p>日期: %s</p>" +
            "<h2>告警统计</h2>" +
            "<ul>" +
            "<li>CRITICAL: %d</li>" +
            "<li>HIGH: %d</li>" +
            "<li>MEDIUM: %d</li>" +
            "<li>LOW: %d</li>" +
            "<li>总计: %d</li>" +
            "</ul>" +
            "<h2>TOP 5 攻击源</h2>" +
            "%s" +
            "</body></html>",
            LocalDate.now().minusDays(1),
            criticalCount,
            highCount,
            mediumCount,
            lowCount,
            alerts.size(),
            buildTopAttackersTable(alerts)
        );
        
        // 批量发送
        List<String> recipients = Arrays.asList(
            "security-team@company.com",
            "team-lead@company.com",
            "ciso@company.com"
        );
        
        try {
            notificationClient.sendBatchEmail(recipients, subject, content, null);
            log.info("Daily digest sent: recipients={}, alerts={}", recipients.size(), alerts.size());
        } catch (Exception e) {
            log.error("Failed to send daily digest", e);
        }
    }
    
    private String buildTopAttackersTable(List<Alert> alerts) {
        Map<String, Long> attackerCount = alerts.stream()
            .collect(Collectors.groupingBy(Alert::getAttackIp, Collectors.counting()));
        
        List<Map.Entry<String, Long>> topAttackers = attackerCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        StringBuilder table = new StringBuilder("<table border='1'><tr><th>攻击源IP</th><th>告警数量</th></tr>");
        for (Map.Entry<String, Long> entry : topAttackers) {
            table.append(String.format("<tr><td>%s</td><td>%d</td></tr>", entry.getKey(), entry.getValue()));
        }
        table.append("</table>");
        
        return table.toString();
    }
}
```

---

### 场景3: 失败重试和降级策略

**需求**: 通知发送失败时自动重试,多次失败后降级到备用渠道。

**实现**:

```java
@Service
@Slf4j
public class NotificationRetryService {
    
    private final NotificationClient notificationClient;
    private final NotificationHistoryRepository historyRepository;
    
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    
    /**
     * 带重试的通知发送
     */
    @Retryable(
        value = {NotificationException.class},
        maxAttempts = MAX_RETRIES,
        backoff = @Backoff(delay = 300000)  // 5分钟
    )
    public NotificationResponse sendWithRetry(
            NotificationChannel channel,
            String recipient,
            String content,
            Long alertId) {
        
        try {
            NotificationResponse response;
            
            switch (channel) {
                case EMAIL:
                    response = notificationClient.sendEmail(recipient, "告警通知", content, alertId);
                    break;
                case SMS:
                    response = notificationClient.sendSms(recipient, content, alertId);
                    break;
                case WEBHOOK:
                    Map<String, Object> payload = Map.of("content", content);
                    response = notificationClient.sendWebhook(recipient, payload, alertId);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported channel: " + channel);
            }
            
            log.info("Notification sent successfully: channel={}, recipient={}", channel, recipient);
            return response;
            
        } catch (Exception e) {
            log.error("Notification failed: channel={}, recipient={}", channel, recipient, e);
            throw new NotificationException("Failed to send notification", e);
        }
    }
    
    /**
     * 重试失败后的降级处理
     */
    @Recover
    public NotificationResponse fallbackNotification(
            NotificationException e,
            NotificationChannel channel,
            String recipient,
            String content,
            Long alertId) {
        
        log.warn("All retries exhausted for {}, using fallback: channel={}", channel, recipient);
        
        // 降级策略: SMS失败 → Email, Email失败 → 记录日志
        if (channel == NotificationChannel.SMS) {
            try {
                log.info("Fallback: sending email instead of SMS");
                return notificationClient.sendEmail(
                    "security-team@company.com",
                    "SMS通知失败 - 降级为邮件",
                    "原SMS收件人: " + recipient + "\n内容: " + content,
                    alertId
                );
            } catch (Exception emailException) {
                log.error("Fallback email also failed", emailException);
            }
        }
        
        // 记录失败到数据库
        NotificationHistory history = NotificationHistory.builder()
            .alertId(alertId)
            .channel(channel)
            .recipient(recipient)
            .content(content)
            .status(NotificationStatus.FAILED)
            .errorMessage(e.getMessage())
            .retryCount(MAX_RETRIES)
            .createdAt(Instant.now())
            .build();
        
        historyRepository.save(history);
        
        return NotificationResponse.builder()
            .success(false)
            .message("All notification attempts failed")
            .build();
    }
}
```

---

## 8. Java客户端完整示例

### AlertNotificationClient

```java
package com.threatdetection.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 告警通知API客户端
 */
@Slf4j
@Component
public class AlertNotificationClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts/notify";
    private final RestTemplate restTemplate;
    
    public AlertNotificationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public NotificationResponse sendEmail(
            String recipient,
            String subject,
            String content,
            Long alertId) {
        
        SendEmailRequest request = new SendEmailRequest();
        request.setRecipient(recipient);
        request.setSubject(subject);
        request.setContent(content);
        request.setAlertId(alertId);
        
        HttpEntity<SendEmailRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
            BASE_URL + "/email",
            httpRequest,
            NotificationResponse.class
        );
        
        log.info("Email notification sent: recipient={}, alertId={}", recipient, alertId);
        return response.getBody();
    }
    
    public NotificationResponse sendSms(
            String phoneNumber,
            String message,
            Long alertId) {
        
        if (message.length() > 70) {
            message = message.substring(0, 67) + "...";
        }
        
        SendSmsRequest request = new SendSmsRequest();
        request.setPhoneNumber(phoneNumber);
        request.setMessage(message);
        request.setAlertId(alertId);
        
        HttpEntity<SendSmsRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
            BASE_URL + "/sms",
            httpRequest,
            NotificationResponse.class
        );
        
        log.info("SMS notification sent: phone={}, alertId={}", phoneNumber, alertId);
        return response.getBody();
    }
    
    public NotificationResponse sendWebhook(
            String url,
            Map<String, Object> payload,
            Long alertId) {
        
        SendWebhookRequest request = new SendWebhookRequest();
        request.setUrl(url);
        request.setMethod("POST");
        request.setPayload(payload);
        request.setAlertId(alertId);
        
        HttpEntity<SendWebhookRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
            BASE_URL + "/webhook",
            httpRequest,
            NotificationResponse.class
        );
        
        log.info("Webhook notification sent: url={}, alertId={}", url, alertId);
        return response.getBody();
    }
    
    public List<NotificationHistory> getNotificationHistory(Long alertId) {
        String url = "http://localhost:8082/api/v1/alerts/" + alertId + "/notifications";
        
        ResponseEntity<List<NotificationHistory>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<NotificationHistory>>() {}
        );
        
        return response.getBody();
    }
    
    public BatchNotificationResult sendBatchEmail(
            List<String> recipients,
            String subject,
            String content,
            List<Long> alertIds) {
        
        if (recipients.size() > 100) {
            throw new IllegalArgumentException("批量发送最多支持100个收件人");
        }
        
        BatchNotificationRequest request = new BatchNotificationRequest();
        request.setChannel("EMAIL");
        request.setRecipients(recipients);
        request.setSubject(subject);
        request.setContent(content);
        request.setAlertIds(alertIds);
        
        HttpEntity<BatchNotificationRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<BatchNotificationResult> response = restTemplate.postForEntity(
            BASE_URL + "/batch",
            httpRequest,
            BatchNotificationResult.class
        );
        
        return response.getBody();
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 9.1 根据严重性选择通知渠道

```java
// ✅ 正确: 根据告警严重性选择合适的通知渠道
public void sendAlertNotification(Alert alert) {
    switch (alert.getSeverity()) {
        case CRITICAL:
            // Email + SMS + Webhook
            notificationClient.sendEmail(recipient, subject, content, alert.getId());
            notificationClient.sendSms(phoneNumber, smsContent, alert.getId());
            notificationClient.sendWebhook(webhookUrl, payload, alert.getId());
            break;
        case HIGH:
            // Email + Webhook
            notificationClient.sendEmail(recipient, subject, content, alert.getId());
            notificationClient.sendWebhook(webhookUrl, payload, alert.getId());
            break;
        case MEDIUM:
            notificationClient.sendEmail(recipient, subject, content, alert.getId());
            break;
    }
}
```

#### 9.2 使用异步通知

```java
// ✅ 正确: 异步发送通知,不阻塞主流程
@Async
public CompletableFuture<Void> sendNotificationAsync(Alert alert) {
    return CompletableFuture.runAsync(() -> {
        try {
            notificationClient.sendEmail(...);
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    });
}
```

#### 9.3 实现降级策略

```java
// ✅ 正确: 主渠道失败后降级到备用渠道
public void sendWithFallback(Alert alert) {
    try {
        notificationClient.sendSms(...);
    } catch (Exception e) {
        log.warn("SMS failed, falling back to email");
        notificationClient.sendEmail(...);
    }
}
```

---

### ❌ 避免的做法

#### 9.4 避免频繁发送通知

```java
// ❌ 错误: 每个小的状态变化都发送通知
@EventListener
public void onAlertUpdated(AlertUpdatedEvent event) {
    notificationClient.sendEmail(...);  // 通知疲劳
}

// ✅ 正确: 只在关键状态变化时通知
@EventListener
public void onAlertUpdated(AlertUpdatedEvent event) {
    if (event.getOldStatus() == AlertStatus.OPEN 
        && event.getNewStatus() != AlertStatus.OPEN) {
        notificationClient.sendEmail(...);
    }
}
```

#### 9.5 避免在循环中单个发送

```java
// ❌ 错误: 循环单个发送
for (String member : team) {
    notificationClient.sendEmail(member, subject, content, alertId);
}

// ✅ 正确: 使用批量发送API
notificationClient.sendBatchEmail(team, subject, content, List.of(alertId));
```

---

## 10. 故障排查

### 10.1 邮件发送失败: SMTP连接超时

**症状**: API返回500错误,提示"SMTP connection timeout"

**诊断步骤**:

```bash
# 测试SMTP连接
telnet smtp.company.com 587

# 查看日志
tail -f /var/log/alert-management/notification.log
```

**解决方案**:

```yaml
# application.yml
spring:
  mail:
    host: smtp.company.com
    port: 587
    username: notifications@company.com
    password: ${SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
          connectiontimeout: 5000
          timeout: 5000
```

---

### 10.2 SMS发送失败: 余额不足

**症状**: SMS通知返回失败

**解决方案**:

```java
@Scheduled(cron = "0 0 9 * * *")
public void checkSmsBalance() {
    double balance = smsProvider.getBalance();
    if (balance < 100) {
        log.error("SMS balance low: {}", balance);
        emailClient.sendEmail(
            "admin@company.com",
            "SMS余额不足警告",
            "当前余额: " + balance,
            null
        );
    }
}
```

---

### 10.3 通知延迟: 队列积压

**症状**: 通知发送延迟超过10分钟

**解决方案**:

```java
// 增加消费者并发度
@KafkaListener(
    topics = "alert-notifications",
    groupId = "notification-service",
    concurrency = "4"  // 增加并发
)
public void onNotificationEvent(NotificationEvent event) {
    processNotification(event);
}
```

---

## 11. 相关文档

| 文档 | 说明 |
|------|------|
| [邮件通知配置](./email_notification_configuration.md) | SMTP配置详细说明 |
| [告警升级API](./alert_escalation_api.md) | 告警升级触发通知 |
| [告警CRUD API](./alert_crud_api.md) | 告警基本操作 |
| [告警生命周期API](./alert_lifecycle_api.md) | 告警状态管理 |
| [告警分析API](./alert_analytics_api.md) | 通知统计分析 |

---

**文档结束**

*最后更新: 2025-10-16*
