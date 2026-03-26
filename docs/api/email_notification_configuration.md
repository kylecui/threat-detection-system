# 邮件通知配置系统文档

**版本**: 1.1  
**更新日期**: 2025-10-16  
**作者**: 威胁检测系统开发团队

---

## ⚠️ 重要更新 (2025-10-16)

### 客户通知配置管理迁移

客户通知配置的**创建/更新/删除**功能已迁移至 **Customer-Management Service (端口8084)**。

#### 职责分离

| 服务 | 职责 | 权限 | 端口 |
|------|------|------|------|
| **Alert-Management** | 告警通知发送 + SMTP配置管理 | **只读**客户通知配置 | 8082 |
| **Customer-Management** | 客户信息 + 通知配置管理 | **读写**客户通知配置 | 8084 |

#### 新API端点 (推荐使用)

**服务地址**: `http://localhost:8084`  
**文档**: `docs/api/customer_management_api.md`

```bash
# 获取配置
GET /api/v1/customers/{customerId}/notification-config

# 创建或更新配置
PUT /api/v1/customers/{customerId}/notification-config

# 部分更新配置
PATCH /api/v1/customers/{customerId}/notification-config

# 删除配置
DELETE /api/v1/customers/{customerId}/notification-config

# 快速开关
PATCH /api/v1/customers/{customerId}/notification-config/email/toggle?enabled=true
PATCH /api/v1/customers/{customerId}/notification-config/slack/toggle?enabled=true

# 测试配置
POST /api/v1/customers/{customerId}/notification-config/test
```

#### 旧API端点 (已废弃)

**服务地址**: `http://localhost:8082`

```bash
❌ POST   /api/notification-config/customer           # 已废弃，返回403
❌ PUT    /api/notification-config/customer/{id}      # 已废弃，返回403
❌ DELETE /api/notification-config/customer/{id}      # 已废弃，返回403

✅ GET    /api/notification-config/customer           # 保留，内部只读访问
✅ GET    /api/notification-config/customer/{id}      # 保留，内部只读访问
```

#### 迁移指南

**场景1**: 创建新配置
```bash
# 旧方式 (已废弃)
curl -X POST http://localhost:8082/api/notification-config/customer \
  -H "Content-Type: application/json" \
  -d '{...}'

# 新方式 (推荐)
curl -X PUT http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{...}'
```

**场景2**: 更新配置
```bash
# 旧方式 (已废弃)
curl -X PUT http://localhost:8082/api/notification-config/customer/customer-001 \
  -H "Content-Type: application/json" \
  -d '{...}'

# 新方式 (推荐)
curl -X PUT http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{...}'
```

**场景3**: 快速开关
```bash
# 新增功能 (仅Customer-Management支持)
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/email/toggle?enabled=true"
```

---

## 目录

- [系统概述](#系统概述)
- [架构设计](#架构设计)
- [数据库表结构](#数据库表结构)
- [配置管理API](#配置管理api)
- [使用示例](#使用示例)
- [最佳实践](#最佳实践)

---

## 系统概述

### 核心特性

本系统实现了基于数据库的动态邮件通知配置，支持以下功能：

✅ **动态SMTP配置** - 从数据库读取SMTP服务器配置，无需重启应用  
✅ **客户级配置** - 每个客户独立配置通知邮箱和规则 (**管理功能已迁移至Customer-Management**)  
✅ **多收件人支持** - 单个客户可配置多个邮箱接收告警  
✅ **告警级别过滤** - 灵活配置哪些告警级别触发通知  
✅ **频率限制** - 防止告警风暴，每小时通知数量可控  
✅ **静默时段** - 支持配置静默时段，避免非工作时间打扰  
✅ **RESTful API** - SMTP配置管理接口 (客户配置管理已迁移)  

### 工作流程

```
威胁检测 → Kafka (threat-alerts) → Alert Management Service
                                            ↓
                            读取客户通知配置 (只读，从customer_notification_configs表)
                                            ↓
                                    检查告警级别 + 频率限制
                                            ↓
                                    读取SMTP配置 (缓存)
                                            ↓
                                    发送邮件到配置的收件人
                                            ↓
                                    记录通知状态到 notifications 表
```

**配置管理流程** (新增):
```
管理员/客户 → Customer-Management (8084) → customer_notification_configs表 (写入)
                                                    ↓
Alert-Management (8082) → customer_notification_configs表 (只读) → 执行通知发送
```

---

## 架构设计

### 核心组件

#### 1. **DynamicMailSenderService**
- 从数据库动态加载SMTP配置
- 创建和缓存JavaMailSender实例
- 配置缓存有效期：5分钟
- 支持手动刷新缓存

#### 2. **KafkaConsumerService**
- 消费Kafka威胁告警事件
- 根据客户ID查询通知配置
- 检查告警级别是否需要通知
- 调用NotificationService发送邮件

#### 3. **NotificationService**
- 使用DynamicMailSenderService发送邮件
- 记录通知状态和重试次数
- 支持多种通知渠道（EMAIL/SMS/Slack/Webhook）

#### 4. **NotificationConfigController**
- 提供RESTful API管理配置
- 支持SMTP配置和客户配置的CRUD操作
- 提供SMTP连接测试功能

---

## 数据库表结构

### 1. smtp_configs (SMTP服务器配置)

```sql
CREATE TABLE smtp_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL UNIQUE,      -- 配置名称
    host VARCHAR(255) NOT NULL,                     -- SMTP服务器地址
    port INTEGER NOT NULL,                          -- SMTP端口
    username VARCHAR(255) NOT NULL,                 -- 用户名
    password VARCHAR(500) NOT NULL,                 -- 密码
    from_address VARCHAR(255) NOT NULL,             -- 发件人地址
    from_name VARCHAR(255) DEFAULT 'Threat Detection System',
    
    -- 安全配置
    enable_tls BOOLEAN DEFAULT false,
    enable_ssl BOOLEAN DEFAULT false,
    enable_starttls BOOLEAN DEFAULT false,
    
    -- 连接配置
    connection_timeout INTEGER DEFAULT 5000,
    timeout INTEGER DEFAULT 5000,
    write_timeout INTEGER DEFAULT 5000,
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT false,               -- 是否为默认配置
    
    -- 元数据
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);
```

**示例数据**:
```json
{
  "id": 1,
  "configName": "default-smtp-163",
  "host": "smtp.163.com",
  "port": 25,
  "username": "threat_detection@163.com",
  "password": "<REDACTED>",
  "fromAddress": "threat_detection@163.com",
  "fromName": "威胁检测系统",
  "enableTls": false,
  "enableSsl": false,
  "enableStarttls": false,
  "isActive": true,
  "isDefault": true
}
```

### 2. customer_notification_configs (客户通知配置)

```sql
CREATE TABLE customer_notification_configs (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL UNIQUE,       -- 客户ID
    
    -- 邮件配置
    email_enabled BOOLEAN DEFAULT true,
    email_recipients TEXT NOT NULL,                  -- JSON数组: ["email1@example.com"]
    
    -- 短信配置
    sms_enabled BOOLEAN DEFAULT false,
    sms_recipients TEXT,
    
    -- Slack配置
    slack_enabled BOOLEAN DEFAULT false,
    slack_webhook_url VARCHAR(500),
    slack_channel VARCHAR(100),
    
    -- Webhook配置
    webhook_enabled BOOLEAN DEFAULT false,
    webhook_url VARCHAR(500),
    webhook_headers TEXT,
    
    -- 告警级别过滤
    min_severity_level VARCHAR(20) DEFAULT 'MEDIUM',
    notify_on_severities TEXT DEFAULT '["MEDIUM","HIGH","CRITICAL"]',
    
    -- 通知频率控制
    max_notifications_per_hour INTEGER DEFAULT 100,
    enable_rate_limiting BOOLEAN DEFAULT true,
    
    -- 静默时段配置
    quiet_hours_enabled BOOLEAN DEFAULT false,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    
    -- 元数据
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**示例数据**:
```json
{
  "id": 1,
  "customerId": "customer_a",
  "emailEnabled": true,
  "emailRecipients": "[\"kylecui@outlook.com\", \"security@company.com\"]",
  "minSeverityLevel": "CRITICAL",
  "notifyOnSeverities": "[\"CRITICAL\"]",
  "maxNotificationsPerHour": 50,
  "enableRateLimiting": true,
  "isActive": true
}
```

### 3. notification_rate_limits (通知频率限制)

```sql
CREATE TABLE notification_rate_limits (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    notification_hour TIMESTAMP NOT NULL,            -- 精确到小时
    notification_count INTEGER DEFAULT 0,
    
    CONSTRAINT unique_customer_hour UNIQUE (customer_id, notification_hour)
);
```

---

## 配置管理API

### 基础信息

**服务地址**: `http://localhost:8082`  
**API前缀**: `/api/notification-config`  
**认证**: 暂无（后续可添加JWT/OAuth2）

---

## SMTP配置管理API

### 1. 获取所有SMTP配置

**请求**:
```bash
GET /api/notification-config/smtp
```

**cURL示例**:
```bash
curl -X GET http://localhost:8082/api/notification-config/smtp
```

**Java示例**:
```java
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8082/api/notification-config/smtp";

List<SmtpConfig> configs = restTemplate.exchange(
    url,
    HttpMethod.GET,
    null,
    new ParameterizedTypeReference<List<SmtpConfig>>() {}
).getBody();

System.out.println("Found " + configs.size() + " SMTP configurations");
```

**响应示例**:
```json
[
  {
    "id": 1,
    "configName": "default-smtp-163",
    "host": "smtp.163.com",
    "port": 25,
    "username": "threat_detection@163.com",
    "password": "<REDACTED>",
    "fromAddress": "threat_detection@163.com",
    "fromName": "威胁检测系统",
    "enableTls": false,
    "enableSsl": false,
    "enableStarttls": false,
    "connectionTimeout": 5000,
    "timeout": 5000,
    "writeTimeout": 5000,
    "isActive": true,
    "isDefault": true,
    "description": "默认SMTP配置 - 网易163邮箱",
    "createdAt": "2025-10-15T15:48:29.582477Z",
    "updatedAt": "2025-10-15T15:48:29.582477Z",
    "createdBy": "system",
    "updatedBy": "system"
  }
]
```

---

### 2. 获取默认SMTP配置

**请求**:
```bash
GET /api/notification-config/smtp/default
```

**cURL示例**:
```bash
curl -X GET http://localhost:8082/api/notification-config/smtp/default | jq
```

**Java示例**:
```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8082/api/notification-config/smtp/default";

SmtpConfig defaultConfig = restTemplate.getForObject(url, SmtpConfig.class);
System.out.println("Default SMTP: " + defaultConfig.getHost() + ":" + defaultConfig.getPort());
```

---

### 3. 根据ID获取SMTP配置

**请求**:
```bash
GET /api/notification-config/smtp/{id}
```

**cURL示例**:
```bash
curl -X GET http://localhost:8082/api/notification-config/smtp/1
```

**Java示例**:
```java
Long smtpConfigId = 1L;
String url = "http://localhost:8082/api/notification-config/smtp/" + smtpConfigId;

SmtpConfig config = restTemplate.getForObject(url, SmtpConfig.class);
```

---

### 4. 创建SMTP配置

**请求**:
```bash
POST /api/notification-config/smtp
Content-Type: application/json
```

**请求体**:
```json
{
  "configName": "smtp-gmail",
  "host": "smtp.gmail.com",
  "port": 587,
  "username": "your-email@gmail.com",
  "password": "your-app-password",
  "fromAddress": "your-email@gmail.com",
  "fromName": "Threat Detection System",
  "enableTls": false,
  "enableSsl": false,
  "enableStarttls": true,
  "connectionTimeout": 5000,
  "timeout": 5000,
  "writeTimeout": 5000,
  "isActive": true,
  "isDefault": false,
  "description": "Gmail SMTP配置"
}
```

**cURL示例**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp \
  -H "Content-Type: application/json" \
  -d '{
    "configName": "smtp-gmail",
    "host": "smtp.gmail.com",
    "port": 587,
    "username": "your-email@gmail.com",
    "password": "your-app-password",
    "fromAddress": "your-email@gmail.com",
    "fromName": "Threat Detection System",
    "enableStarttls": true,
    "isActive": true,
    "isDefault": false,
    "description": "Gmail SMTP配置"
  }'
```

**Java示例**:
```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8082/api/notification-config/smtp";

SmtpConfig newConfig = SmtpConfig.builder()
    .configName("smtp-gmail")
    .host("smtp.gmail.com")
    .port(587)
    .username("your-email@gmail.com")
    .password("your-app-password")
    .fromAddress("your-email@gmail.com")
    .fromName("Threat Detection System")
    .enableStarttls(true)
    .isActive(true)
    .isDefault(false)
    .description("Gmail SMTP配置")
    .build();

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);

HttpEntity<SmtpConfig> request = new HttpEntity<>(newConfig, headers);
SmtpConfig createdConfig = restTemplate.postForObject(url, request, SmtpConfig.class);

System.out.println("Created SMTP config with ID: " + createdConfig.getId());
```

---

### 5. 更新SMTP配置

**请求**:
```bash
PUT /api/notification-config/smtp/{id}
Content-Type: application/json
```

**请求体**:
```json
{
  "host": "smtp.163.com",
  "port": 465,
  "username": "threat_detection@163.com",
  "password": "new-password",
  "fromAddress": "threat_detection@163.com",
  "fromName": "威胁检测系统V2",
  "enableTls": false,
  "enableSsl": true,
  "enableStarttls": false,
  "connectionTimeout": 10000,
  "timeout": 10000,
  "writeTimeout": 10000,
  "isActive": true,
  "description": "更新后的SMTP配置"
}
```

**cURL示例**:
```bash
curl -X PUT http://localhost:8082/api/notification-config/smtp/1 \
  -H "Content-Type: application/json" \
  -d '{
    "host": "smtp.163.com",
    "port": 465,
    "username": "threat_detection@163.com",
    "password": "new-password",
    "fromAddress": "threat_detection@163.com",
    "fromName": "威胁检测系统V2",
    "enableSsl": true,
    "isActive": true,
    "description": "更新后的SMTP配置"
  }'
```

**Java示例**:
```java
import org.springframework.http.HttpMethod;

Long smtpConfigId = 1L;
String url = "http://localhost:8082/api/notification-config/smtp/" + smtpConfigId;

SmtpConfig updateConfig = SmtpConfig.builder()
    .host("smtp.163.com")
    .port(465)
    .username("threat_detection@163.com")
    .password("new-password")
    .fromAddress("threat_detection@163.com")
    .fromName("威胁检测系统V2")
    .enableSsl(true)
    .isActive(true)
    .description("更新后的SMTP配置")
    .build();

HttpEntity<SmtpConfig> request = new HttpEntity<>(updateConfig, headers);
SmtpConfig updatedConfig = restTemplate.exchange(
    url, 
    HttpMethod.PUT, 
    request, 
    SmtpConfig.class
).getBody();
```

---

### 6. 测试SMTP连接

**请求**:
```bash
POST /api/notification-config/smtp/{id}/test
```

**cURL示例**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp/1/test
```

**Java示例**:
```java
Long smtpConfigId = 1L;
String url = "http://localhost:8082/api/notification-config/smtp/" + smtpConfigId + "/test";

Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
boolean success = (Boolean) result.get("success");
String message = (String) result.get("message");

System.out.println("SMTP测试结果: " + message);
```

**响应示例**:
```json
{
  "success": true,
  "message": "SMTP连接测试成功"
}
```

---

### 7. 刷新SMTP配置缓存

**请求**:
```bash
POST /api/notification-config/smtp/refresh-cache
```

**说明**: 修改SMTP配置后，调用此API刷新缓存，使新配置立即生效（无需重启服务）

**cURL示例**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp/refresh-cache
```

**Java示例**:
```java
String url = "http://localhost:8082/api/notification-config/smtp/refresh-cache";
Map<String, String> result = restTemplate.postForObject(url, null, Map.class);
System.out.println(result.get("message")); // 输出: SMTP配置缓存已刷新
```

**响应示例**:
```json
{
  "message": "SMTP配置缓存已刷新"
}
```

---

## 客户通知配置管理API

### 1. 获取所有客户通知配置

**请求**:
```bash
GET /api/notification-config/customer
```

**cURL示例**:
```bash
curl -X GET http://localhost:8082/api/notification-config/customer
```

**Java示例**:
```java
String url = "http://localhost:8082/api/notification-config/customer";

List<CustomerNotificationConfig> configs = restTemplate.exchange(
    url,
    HttpMethod.GET,
    null,
    new ParameterizedTypeReference<List<CustomerNotificationConfig>>() {}
).getBody();
```

**响应示例**:
```json
[
  {
    "id": 1,
    "customerId": "customer_a",
    "emailEnabled": true,
    "emailRecipients": "[\"kylecui@outlook.com\"]",
    "smsEnabled": false,
    "slackEnabled": false,
    "webhookEnabled": false,
    "minSeverityLevel": "CRITICAL",
    "notifyOnSeverities": "[\"CRITICAL\"]",
    "maxNotificationsPerHour": 50,
    "enableRateLimiting": true,
    "quietHoursEnabled": false,
    "isActive": true,
    "description": "客户A - 仅CRITICAL级别告警"
  }
]
```

---

### 2. 获取指定客户的通知配置

**请求**:
```bash
GET /api/notification-config/customer/{customerId}
```

**cURL示例**:
```bash
curl -X GET http://localhost:8082/api/notification-config/customer/customer_a | jq
```

**Java示例**:
```java
String customerId = "customer_a";
String url = "http://localhost:8082/api/notification-config/customer/" + customerId;

CustomerNotificationConfig config = restTemplate.getForObject(url, CustomerNotificationConfig.class);
System.out.println("客户 " + customerId + " 的邮箱: " + config.getEmailRecipients());
```

---

### 3. 创建客户通知配置

**请求**:
```bash
POST /api/notification-config/customer
Content-Type: application/json
```

**请求体**:
```json
{
  "customerId": "customer_d",
  "emailEnabled": true,
  "emailRecipients": "[\"admin@company.com\", \"security@company.com\"]",
  "smsEnabled": false,
  "slackEnabled": false,
  "webhookEnabled": false,
  "minSeverityLevel": "HIGH",
  "notifyOnSeverities": "[\"HIGH\", \"CRITICAL\"]",
  "maxNotificationsPerHour": 100,
  "enableRateLimiting": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai",
  "isActive": true,
  "description": "客户D - HIGH及以上级别告警，启用静默时段"
}
```

**cURL示例**:
```bash
curl -X POST http://localhost:8082/api/notification-config/customer \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer_d",
    "emailEnabled": true,
    "emailRecipients": "[\"admin@company.com\", \"security@company.com\"]",
    "minSeverityLevel": "HIGH",
    "notifyOnSeverities": "[\"HIGH\", \"CRITICAL\"]",
    "maxNotificationsPerHour": 100,
    "enableRateLimiting": true,
    "quietHoursEnabled": true,
    "quietHoursStart": "22:00:00",
    "quietHoursEnd": "08:00:00",
    "isActive": true,
    "description": "客户D - HIGH及以上级别告警，启用静默时段"
  }'
```

**Java示例**:
```java
import com.fasterxml.jackson.databind.ObjectMapper;

String url = "http://localhost:8082/api/notification-config/customer";
ObjectMapper mapper = new ObjectMapper();

// 构建邮箱列表（JSON数组）
List<String> emails = Arrays.asList("admin@company.com", "security@company.com");
String emailRecipientsJson = mapper.writeValueAsString(emails);

// 构建通知级别列表
List<String> severities = Arrays.asList("HIGH", "CRITICAL");
String severitiesJson = mapper.writeValueAsString(severities);

CustomerNotificationConfig newConfig = CustomerNotificationConfig.builder()
    .customerId("customer_d")
    .emailEnabled(true)
    .emailRecipients(emailRecipientsJson)
    .minSeverityLevel("HIGH")
    .notifyOnSeverities(severitiesJson)
    .maxNotificationsPerHour(100)
    .enableRateLimiting(true)
    .quietHoursEnabled(true)
    .quietHoursStart(LocalTime.parse("22:00:00"))
    .quietHoursEnd(LocalTime.parse("08:00:00"))
    .quietHoursTimezone("Asia/Shanghai")
    .isActive(true)
    .description("客户D - HIGH及以上级别告警，启用静默时段")
    .build();

HttpEntity<CustomerNotificationConfig> request = new HttpEntity<>(newConfig, headers);
CustomerNotificationConfig created = restTemplate.postForObject(url, request, CustomerNotificationConfig.class);
```

---

### 4. 更新客户通知配置

**请求**:
```bash
PUT /api/notification-config/customer/{customerId}
Content-Type: application/json
```

**请求体**:
```json
{
  "emailEnabled": true,
  "emailRecipients": "[\"newadmin@company.com\", \"security@company.com\", \"ops@company.com\"]",
  "smsEnabled": false,
  "slackEnabled": false,
  "webhookEnabled": false,
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": "[\"MEDIUM\", \"HIGH\", \"CRITICAL\"]",
  "maxNotificationsPerHour": 200,
  "enableRateLimiting": true,
  "quietHoursEnabled": false,
  "isActive": true,
  "description": "更新后的配置 - 新增ops邮箱，调整告警级别"
}
```

**cURL示例**:
```bash
curl -X PUT http://localhost:8082/api/notification-config/customer/customer_a \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "emailRecipients": "[\"newadmin@company.com\", \"security@company.com\"]",
    "minSeverityLevel": "MEDIUM",
    "notifyOnSeverities": "[\"MEDIUM\", \"HIGH\", \"CRITICAL\"]",
    "maxNotificationsPerHour": 200,
    "isActive": true,
    "description": "更新后的配置"
  }'
```

**Java示例**:
```java
String customerId = "customer_a";
String url = "http://localhost:8082/api/notification-config/customer/" + customerId;

// 更新邮箱列表
List<String> newEmails = Arrays.asList("newadmin@company.com", "security@company.com");
String emailRecipientsJson = mapper.writeValueAsString(newEmails);

CustomerNotificationConfig updateConfig = CustomerNotificationConfig.builder()
    .emailEnabled(true)
    .emailRecipients(emailRecipientsJson)
    .minSeverityLevel("MEDIUM")
    .notifyOnSeverities("[\"MEDIUM\", \"HIGH\", \"CRITICAL\"]")
    .maxNotificationsPerHour(200)
    .enableRateLimiting(true)
    .isActive(true)
    .description("更新后的配置")
    .build();

HttpEntity<CustomerNotificationConfig> request = new HttpEntity<>(updateConfig, headers);
CustomerNotificationConfig updated = restTemplate.exchange(
    url,
    HttpMethod.PUT,
    request,
    CustomerNotificationConfig.class
).getBody();
```

---

### 5. 删除客户通知配置

**请求**:
```bash
DELETE /api/notification-config/customer/{customerId}
```

**cURL示例**:
```bash
curl -X DELETE http://localhost:8082/api/notification-config/customer/customer_d
```

**Java示例**:
```java
String customerId = "customer_d";
String url = "http://localhost:8082/api/notification-config/customer/" + customerId;

restTemplate.delete(url);
System.out.println("已删除客户 " + customerId + " 的通知配置");
```

**响应示例**:
```json
{
  "message": "配置已删除"
}
```

---

## 使用示例

### 场景1: 为新客户配置邮件通知

**需求**: 客户X需要接收所有HIGH和CRITICAL级别的告警，发送到3个邮箱

**步骤**:

1. **创建客户通知配置**:
```bash
curl -X POST http://localhost:8082/api/notification-config/customer \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer_x",
    "emailEnabled": true,
    "emailRecipients": "[\"admin@x.com\", \"security@x.com\", \"ops@x.com\"]",
    "minSeverityLevel": "HIGH",
    "notifyOnSeverities": "[\"HIGH\", \"CRITICAL\"]",
    "maxNotificationsPerHour": 50,
    "enableRateLimiting": true,
    "isActive": true,
    "description": "客户X - HIGH和CRITICAL告警通知"
  }'
```

2. **验证配置**:
```bash
curl http://localhost:8082/api/notification-config/customer/customer_x | jq
```

3. **系统自动生效**: 下次CRITICAL或HIGH告警触发时，将自动发送到这3个邮箱

---

### 场景2: 切换SMTP服务器

**需求**: 从163邮箱切换到Gmail

**步骤**:

1. **创建新的SMTP配置**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp \
  -H "Content-Type: application/json" \
  -d '{
    "configName": "smtp-gmail-prod",
    "host": "smtp.gmail.com",
    "port": 587,
    "username": "alerts@company.com",
    "password": "your-app-password",
    "fromAddress": "alerts@company.com",
    "fromName": "威胁检测系统",
    "enableStarttls": true,
    "isActive": true,
    "isDefault": false,
    "description": "生产环境Gmail SMTP"
  }'
```

2. **测试连接**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp/2/test
```

3. **如果测试成功，更新为默认配置**:
```bash
# 先将旧的默认配置改为非默认
curl -X PUT http://localhost:8082/api/notification-config/smtp/1 \
  -H "Content-Type: application/json" \
  -d '{
    "isDefault": false
  }'

# 将新配置设为默认
curl -X PUT http://localhost:8082/api/notification-config/smtp/2 \
  -H "Content-Type: application/json" \
  -d '{
    "isDefault": true
  }'
```

4. **刷新缓存**:
```bash
curl -X POST http://localhost:8082/api/notification-config/smtp/refresh-cache
```

5. **新配置立即生效**（无需重启服务）

---

### 场景3: 配置静默时段

**需求**: 客户Y希望晚上10点到早上8点不接收通知

**步骤**:

```bash
curl -X PUT http://localhost:8082/api/notification-config/customer/customer_y \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "emailRecipients": "[\"admin@y.com\"]",
    "quietHoursEnabled": true,
    "quietHoursStart": "22:00:00",
    "quietHoursEnd": "08:00:00",
    "quietHoursTimezone": "Asia/Shanghai",
    "isActive": true,
    "description": "配置静默时段：22:00-08:00"
  }'
```

---

### 场景4: 完整的Java客户端示例

```java
package com.example.notification;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class NotificationConfigService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl = "http://localhost:8082/api/notification-config";
    
    /**
     * 为新客户创建完整的通知配置
     */
    public void setupCustomerNotification(
            String customerId, 
            List<String> emailAddresses,
            List<String> notifySeverities) throws Exception {
        
        // 1. 检查SMTP配置是否可用
        SmtpConfig smtpConfig = getDefaultSmtpConfig();
        if (smtpConfig == null || !smtpConfig.getIsActive()) {
            throw new RuntimeException("没有可用的SMTP配置");
        }
        
        System.out.println("使用SMTP配置: " + smtpConfig.getConfigName());
        
        // 2. 测试SMTP连接
        boolean smtpOk = testSmtpConnection(smtpConfig.getId());
        if (!smtpOk) {
            throw new RuntimeException("SMTP连接测试失败");
        }
        
        System.out.println("SMTP连接测试成功");
        
        // 3. 创建客户通知配置
        CustomerNotificationConfig config = new CustomerNotificationConfig();
        config.setCustomerId(customerId);
        config.setEmailEnabled(true);
        config.setEmailRecipients(objectMapper.writeValueAsString(emailAddresses));
        config.setMinSeverityLevel("CRITICAL");
        config.setNotifyOnSeverities(objectMapper.writeValueAsString(notifySeverities));
        config.setMaxNotificationsPerHour(100);
        config.setEnableRateLimiting(true);
        config.setIsActive(true);
        config.setDescription("自动创建的通知配置");
        
        CustomerNotificationConfig created = createCustomerConfig(config);
        System.out.println("创建客户配置成功: ID=" + created.getId());
    }
    
    /**
     * 获取默认SMTP配置
     */
    public SmtpConfig getDefaultSmtpConfig() {
        String url = baseUrl + "/smtp/default";
        try {
            return restTemplate.getForObject(url, SmtpConfig.class);
        } catch (Exception e) {
            System.err.println("获取默认SMTP配置失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 测试SMTP连接
     */
    public boolean testSmtpConnection(Long smtpConfigId) {
        String url = baseUrl + "/smtp/" + smtpConfigId + "/test";
        try {
            Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
            return (Boolean) result.get("success");
        } catch (Exception e) {
            System.err.println("SMTP测试失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建客户通知配置
     */
    public CustomerNotificationConfig createCustomerConfig(CustomerNotificationConfig config) {
        String url = baseUrl + "/customer";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CustomerNotificationConfig> request = new HttpEntity<>(config, headers);
        return restTemplate.postForObject(url, request, CustomerNotificationConfig.class);
    }
    
    /**
     * 更新客户邮箱列表
     */
    public void updateCustomerEmails(String customerId, List<String> newEmails) throws Exception {
        String url = baseUrl + "/customer/" + customerId;
        
        // 先获取现有配置
        CustomerNotificationConfig existing = restTemplate.getForObject(url, CustomerNotificationConfig.class);
        
        // 更新邮箱列表
        existing.setEmailRecipients(objectMapper.writeValueAsString(newEmails));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CustomerNotificationConfig> request = new HttpEntity<>(existing, headers);
        restTemplate.exchange(url, HttpMethod.PUT, request, CustomerNotificationConfig.class);
        
        System.out.println("已更新客户 " + customerId + " 的邮箱列表");
    }
    
    /**
     * 批量获取所有客户配置
     */
    public Map<String, List<String>> getAllCustomerEmails() throws Exception {
        String url = baseUrl + "/customer";
        
        List<CustomerNotificationConfig> configs = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<CustomerNotificationConfig>>() {}
        ).getBody();
        
        Map<String, List<String>> result = new HashMap<>();
        for (CustomerNotificationConfig config : configs) {
            if (config.getEmailEnabled()) {
                List<String> emails = objectMapper.readValue(
                    config.getEmailRecipients(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
                result.put(config.getCustomerId(), emails);
            }
        }
        
        return result;
    }
}
```

**使用示例**:
```java
public class Main {
    public static void main(String[] args) throws Exception {
        NotificationConfigService service = new NotificationConfigService();
        
        // 为新客户设置通知
        service.setupCustomerNotification(
            "customer_new",
            Arrays.asList("admin@new.com", "security@new.com"),
            Arrays.asList("HIGH", "CRITICAL")
        );
        
        // 更新邮箱
        service.updateCustomerEmails(
            "customer_new",
            Arrays.asList("admin@new.com", "security@new.com", "ops@new.com")
        );
        
        // 获取所有客户邮箱
        Map<String, List<String>> allEmails = service.getAllCustomerEmails();
        allEmails.forEach((customerId, emails) -> {
            System.out.println(customerId + ": " + String.join(", ", emails));
        });
    }
}
```

---

## 最佳实践

### 1. SMTP配置管理

✅ **推荐做法**:
- 为每个环境（开发/测试/生产）配置独立的SMTP
- 定期测试SMTP连接确保可用性
- 使用应用专用密码而非账号主密码
- 敏感信息（密码）考虑加密存储

❌ **避免**:
- 在生产环境使用个人邮箱
- 频繁修改默认SMTP配置
- 忘记刷新缓存

### 2. 客户通知配置

✅ **推荐做法**:
- 为每个客户配置2-3个接收邮箱（冗余）
- 根据客户业务重要性调整告警级别
- 启用频率限制防止告警风暴
- 为非7×24业务启用静默时段

❌ **避免**:
- 将所有告警级别都发送（造成告警疲劳）
- 配置过低的频率限制（可能丢失重要告警）
- 邮箱列表格式错误（必须是JSON数组）

### 3. 邮箱列表格式

**正确格式** (JSON数组):
```json
{
  "emailRecipients": "[\"email1@example.com\", \"email2@example.com\"]"
}
```

**错误格式**:
```json
{
  "emailRecipients": "email1@example.com, email2@example.com"  // ❌ 错误
}
```

### 4. 配置更新流程

**标准流程**:
1. 更新配置（通过API）
2. 刷新缓存（`POST /smtp/refresh-cache`）
3. 验证配置（查询API确认）
4. 测试通知（触发一个测试告警）

### 5. 监控和告警

建议监控以下指标:
- SMTP连接失败次数
- 邮件发送成功率
- 每小时通知数量（检测告警风暴）
- 配置更新记录

---

## 故障排查

### 问题1: 邮件发送失败

**检查步骤**:
```bash
# 1. 检查SMTP配置
curl http://localhost:8082/api/notification-config/smtp/default

# 2. 测试SMTP连接
curl -X POST http://localhost:8082/api/notification-config/smtp/1/test

# 3. 查看通知表中的错误信息
docker exec postgres psql -U threat_user -d threat_detection \
  -c "SELECT id, status, error_message FROM notifications WHERE status='FAILED' ORDER BY id DESC LIMIT 10;"

# 4. 查看服务日志
docker logs alert-management-service 2>&1 | grep -i "mail\|smtp" | tail -50
```

### 问题2: 客户收不到告警邮件

**检查步骤**:
```bash
# 1. 确认客户配置存在且激活
curl http://localhost:8082/api/notification-config/customer/customer_id

# 2. 检查告警级别配置
# 确保 notifyOnSeverities 包含实际告警级别

# 3. 检查邮箱格式
# 必须是 JSON 数组格式

# 4. 检查频率限制
docker exec postgres psql -U threat_user -d threat_detection \
  -c "SELECT * FROM notification_rate_limits WHERE customer_id='customer_id' ORDER BY notification_hour DESC LIMIT 5;"
```

### 问题3: 配置更新未生效

**解决方案**:
```bash
# 刷新SMTP配置缓存
curl -X POST http://localhost:8082/api/notification-config/smtp/refresh-cache

# 重启服务（最后手段）
cd /home/kylecui/threat-detection-system/docker
docker-compose restart alert-management
```

---

## 附录

### A. 告警级别说明

| 级别 | 威胁分数范围 | 说明 | 建议通知 |
|------|-------------|------|---------|
| **INFO** | < 10 | 信息性事件 | ❌ 不推荐 |
| **LOW** | 10-50 | 低危威胁 | ❌ 不推荐 |
| **MEDIUM** | 50-100 | 中危威胁 | ⚠️ 可选 |
| **HIGH** | 100-200 | 高危威胁 | ✅ 推荐 |
| **CRITICAL** | > 200 | 严重威胁 | ✅ 必须 |

### B. 常用SMTP服务器配置

**163邮箱**:
```json
{
  "host": "smtp.163.com",
  "port": 25,
  "enableStarttls": false,
  "enableSsl": false
}
```

**QQ邮箱**:
```json
{
  "host": "smtp.qq.com",
  "port": 587,
  "enableStarttls": true,
  "enableSsl": false
}
```

**Gmail**:
```json
{
  "host": "smtp.gmail.com",
  "port": 587,
  "enableStarttls": true,
  "enableSsl": false
}
```

**企业邮箱（示例）**:
```json
{
  "host": "smtp.exmail.qq.com",
  "port": 465,
  "enableStarttls": false,
  "enableSsl": true
}
```

### C. 通知模板

**CRITICAL告警邮件内容**:
```
检测到严重威胁！

告警ID: 1234
标题: [threat-assessment-service] null 威胁检测
描述: 检测到大规模端口扫描行为
威胁分数: 256.50
来源: threat-assessment-service
时间: 2025-10-16T08:30:00Z

请立即处理！
```

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.0 | 2025-10-16 | 初始版本，完整的配置管理API和使用文档 |

---

**文档结束**
