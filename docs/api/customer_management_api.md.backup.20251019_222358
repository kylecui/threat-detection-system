# Customer Management API 文档

**版本**: 1.0  
**更新日期**: 2025-10-16  
**作者**: 威胁检测系统开发团队  
**服务端口**: 8084

---

## 目录

- [第1部分：系统概述与架构](#第1部分系统概述与架构)
  - [系统概述](#系统概述)
  - [核心特性](#核心特性)
  - [架构设计](#架构设计)
  - [配置管理](#配置管理)
  - [数据模型](#数据模型)
- [第2部分：Customer CRUD API](#第2部分customer-crud-api)
- [第3部分：Device Binding API](#第3部分device-binding-api)
- [第4部分：Notification Config API](#第4部分notification-config-api)

---

## 第1部分：系统概述与架构

### 系统概述

Customer Management Service 是威胁检测系统的**客户配置管理中心**，负责管理所有与客户相关的信息和配置，包括：

- 🏢 **客户基础信息管理**: 客户创建、查询、更新、删除
- 🔗 **设备绑定管理**: 设备与客户的关联关系，配额控制
- 🔔 **通知配置管理**: 多渠道告警通知规则配置

#### 服务定位

| 维度 | 说明 |
|------|------|
| **服务类型** | 配置管理服务 |
| **端口** | 8084 |
| **职责** | 客户信息和配置的**完整CRUD权限** |
| **权限** | 读写 (Create, Read, Update, Delete) |
| **使用场景** | 管理员配置、客户自助管理、系统集成 |
| **数据库** | PostgreSQL (threat_detection) |

#### 与其他服务的关系

```
Customer Management (8084)
    ↓ 提供配置数据
Alert Management (8082) - 只读访问通知配置，执行告警通知
    ↓ 消费告警事件
Stream Processing (8083) - 处理威胁评分
    ↓ 发送告警到Kafka
Kafka (9092) - 消息队列
```

**关键设计原则**: 
- ✅ **职责分离**: Customer Management负责配置管理，Alert Management只读配置用于执行通知
- ✅ **最小权限**: Alert Management以系统身份只读访问，无写权限
- ✅ **单一职责**: 配置管理与通知执行分离

---

### 核心特性

#### 1. 客户管理 (Customer CRUD)

✅ **完整生命周期管理**
- 创建客户，自动分配订阅套餐
- 查询客户信息（单个/列表/搜索）
- 更新客户信息（联系方式、订阅套餐等）
- 软删除/硬删除（状态管理）

✅ **订阅套餐系统**
- **FREE**: 5个设备
- **BASIC**: 20个设备
- **PROFESSIONAL**: 100个设备
- **ENTERPRISE**: 10000个设备

✅ **多维度查询**
- 按状态过滤（ACTIVE/INACTIVE/SUSPENDED）
- 关键词搜索（公司名、联系人、邮箱）
- 分页排序

✅ **统计分析**
- 按状态统计客户数量
- 按订阅套餐统计分布
- 设备使用率统计

#### 2. 设备绑定管理 (Device Binding)

✅ **灵活绑定方式**
- 单个设备绑定
- 批量设备绑定（最多100个）

✅ **智能配额管理**
- 自动验证设备数量上限
- 超限自动拒绝绑定
- 实时同步 current_devices 计数

✅ **设备状态管理**
- 激活/停用设备
- 解绑设备（单个/批量）
- 设备列表查询（支持激活状态过滤）

✅ **配额监控**
- 实时查询配额使用情况
- 手动同步设备计数
- 配额预警

#### 3. 通知配置管理 (Notification Config)

✅ **多渠道支持**
- 📧 **Email**: 支持多收件人，SMTP配置
- 📱 **SMS**: 短信通知，多手机号
- 💬 **Slack**: Webhook集成，自定义频道
- 🔗 **Webhook**: 自定义HTTP回调，支持自定义Header

✅ **告警级别过滤**
- 最小通知级别 (minSeverityLevel)
- 自定义通知级别组合 (notifyOnSeverities)
- 支持级别: CRITICAL, HIGH, MEDIUM, LOW, INFO

✅ **频率控制**
- 每小时最大通知数量限制
- 防止告警风暴
- 频率限制开关

✅ **静默时段**
- 配置静默开始/结束时间
- 支持时区设置
- 避免非工作时间打扰

✅ **快速操作**
- 一键开关邮件/Slack/SMS/Webhook
- 配置测试功能
- 恢复默认配置

---

### 架构设计

#### 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| **开发语言** | Java | 21 (OpenJDK) |
| **应用框架** | Spring Boot | 3.1.5 |
| **数据库** | PostgreSQL | 15+ |
| **ORM框架** | Spring Data JPA | 3.1.5 |
| **验证框架** | Hibernate Validator | 8.0+ |
| **构建工具** | Maven | 3.8.7+ |
| **日志框架** | SLF4J + Logback | 2.0+ |

#### 分层架构

```
┌─────────────────────────────────────────┐
│         Controller Layer                │  REST API端点
│  - CustomerController                   │  - 请求验证
│  - DeviceManagementController           │  - 响应封装
│  - NotificationConfigController         │
├─────────────────────────────────────────┤
│         Service Layer                   │  业务逻辑
│  - CustomerService                      │  - 业务规则
│  - DeviceManagementService              │  - 事务管理
│  - NotificationConfigService            │  - 异常处理
├─────────────────────────────────────────┤
│         Repository Layer                │  数据访问
│  - CustomerRepository                   │  - JPA查询
│  - DeviceMappingRepository              │  - 自定义查询
│  - NotificationConfigRepository         │
├─────────────────────────────────────────┤
│         Model Layer                     │  数据模型
│  - Customer (Entity)                    │  - JPA实体
│  - DeviceMapping (Entity)               │  - 数据验证
│  - NotificationConfig (Entity)          │
├─────────────────────────────────────────┤
│         DTO Layer                       │  数据传输
│  - Request DTOs (验证注解)               │  - 请求封装
│  - Response DTOs (序列化)               │  - 响应封装
└─────────────────────────────────────────┘
            ↓
┌─────────────────────────────────────────┐
│      PostgreSQL Database                │
│  - customers                            │
│  - device_customer_mapping              │
│  - customer_notification_configs        │
└─────────────────────────────────────────┘
```

#### 核心组件说明

##### 1. Controller层

**CustomerController** (`/api/v1/customers`)
- 9个REST端点
- 支持CRUD、搜索、统计
- 请求验证使用 `@Valid`
- 统一异常处理

**DeviceManagementController** (`/api/v1/customers/{customerId}/devices`)
- 9个REST端点
- 设备绑定/解绑（单个/批量）
- 配额查询和同步
- 设备状态管理

**NotificationConfigController** (`/api/v1/customers/{customerId}/notification-config`)
- 8个REST端点
- 配置创建/更新/删除
- 快速toggle开关
- 配置测试

##### 2. Service层

**CustomerService**
- 客户CRUD业务逻辑
- 订阅套餐自动设置
- 软删除/硬删除管理
- 统计信息计算

**DeviceManagementService**
- 设备绑定验证（配额检查）
- 批量操作（最多100个）
- 设备计数同步
- 状态切换

**NotificationConfigService**
- 配置合并更新（部分更新）
- 默认值设置
- 配置测试（验证邮箱格式、Webhook URL等）
- 删除后恢复默认

##### 3. Repository层

**CustomerRepository extends JpaRepository**
- 自定义查询方法
- 按状态查询: `findByStatus`
- 关键词搜索: `findByCompanyNameContainingOrContactNameContaining`
- 统计查询: `countByStatus`, `countBySubscriptionTier`

**DeviceMappingRepository extends JpaRepository**
- 复合查询: `findByCustomerIdAndDevSerial`
- 批量查询: `findByCustomerIdAndDevSerialIn`
- 计数查询: `countByCustomerIdAndIsActive`
- 设备列表: `findByCustomerId`

**NotificationConfigRepository extends JpaRepository**
- 唯一性约束: `findByCustomerId`
- 批量查询: `findByCustomerIdIn`

---

### 配置管理

#### application.yml

```yaml
spring:
  application:
    name: customer-management-service
  
  # 数据库配置
  datasource:
    url: jdbc:postgresql://localhost:5432/threat_detection
    username: threat_user
    password: threat_password
    driver-class-name: org.postgresql.Driver
    
  # JPA配置
  jpa:
    hibernate:
      ddl-auto: none  # 生产环境使用none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
    
  # 连接池配置
  hikari:
    maximum-pool-size: 10
    minimum-idle: 5
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

# 服务器配置
server:
  port: 8084
  servlet:
    context-path: /

# 日志配置
logging:
  level:
    com.threatdetection.customer: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/customer-management.log
    max-size: 10MB
    max-history: 30

# Actuator监控
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

#### application-docker.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/threat_detection
    username: ${DB_USERNAME:threat_user}
    password: ${DB_PASSWORD:threat_password}

server:
  port: ${SERVER_PORT:8084}

logging:
  file:
    name: /app/logs/customer-management.log
```

#### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_USERNAME` | 数据库用户名 | threat_user |
| `DB_PASSWORD` | 数据库密码 | threat_password |
| `SERVER_PORT` | 服务端口 | 8084 |
| `SPRING_PROFILES_ACTIVE` | 激活的配置文件 | default |

---

### 数据模型

#### 1. Customer (客户实体)

**表名**: `customers`

**字段定义**:

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `id` | BIGSERIAL | PRIMARY KEY | 自增主键 |
| `customer_id` | VARCHAR(50) | UNIQUE, NOT NULL | 客户唯一标识 |
| `company_name` | VARCHAR(255) | NOT NULL | 公司名称 |
| `contact_name` | VARCHAR(100) | | 联系人姓名 |
| `contact_email` | VARCHAR(255) | | 联系邮箱 |
| `contact_phone` | VARCHAR(50) | | 联系电话 |
| `status` | VARCHAR(20) | NOT NULL | 客户状态 |
| `subscription_tier` | VARCHAR(20) | NOT NULL | 订阅套餐 |
| `max_devices` | INTEGER | NOT NULL | 最大设备数 |
| `current_devices` | INTEGER | DEFAULT 0 | 当前设备数 |
| `subscription_start_date` | DATE | | 订阅开始日期 |
| `subscription_end_date` | DATE | | 订阅结束日期 |
| `notes` | TEXT | | 备注信息 |
| `is_active` | BOOLEAN | DEFAULT true | 是否激活 |
| `created_at` | TIMESTAMP | DEFAULT now() | 创建时间 |
| `updated_at` | TIMESTAMP | DEFAULT now() | 更新时间 |
| `created_by` | VARCHAR(100) | | 创建人 |
| `updated_by` | VARCHAR(100) | | 更新人 |

**枚举类型**:

```java
// 客户状态
public enum CustomerStatus {
    ACTIVE,      // 激活
    INACTIVE,    // 未激活
    SUSPENDED    // 暂停
}

// 订阅套餐
public enum SubscriptionTier {
    FREE(5),           // 免费版: 5个设备
    BASIC(20),         // 基础版: 20个设备
    PROFESSIONAL(100), // 专业版: 100个设备
    ENTERPRISE(10000); // 企业版: 10000个设备
    
    private final int maxDevices;
}
```

**索引**:
```sql
CREATE UNIQUE INDEX idx_customer_id ON customers(customer_id);
CREATE INDEX idx_status ON customers(status);
CREATE INDEX idx_subscription_tier ON customers(subscription_tier);
CREATE INDEX idx_company_name ON customers(company_name);
CREATE INDEX idx_contact_email ON customers(contact_email);
```

---

#### 2. DeviceMapping (设备绑定实体)

**表名**: `device_customer_mapping`

**字段定义**:

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `id` | BIGSERIAL | PRIMARY KEY | 自增主键 |
| `customer_id` | VARCHAR(50) | NOT NULL | 客户ID |
| `dev_serial` | VARCHAR(100) | UNIQUE, NOT NULL | 设备序列号 |
| `device_name` | VARCHAR(255) | | 设备名称 |
| `device_type` | VARCHAR(50) | | 设备类型 |
| `location` | VARCHAR(255) | | 设备位置 |
| `is_active` | BOOLEAN | DEFAULT true | 是否激活 |
| `bind_time` | TIMESTAMP | DEFAULT now() | 绑定时间 |
| `unbind_time` | TIMESTAMP | | 解绑时间 |
| `created_at` | TIMESTAMP | DEFAULT now() | 创建时间 |
| `updated_at` | TIMESTAMP | DEFAULT now() | 更新时间 |

**索引**:
```sql
CREATE UNIQUE INDEX idx_dev_serial ON device_customer_mapping(dev_serial);
CREATE INDEX idx_customer_id ON device_customer_mapping(customer_id);
CREATE INDEX idx_customer_device ON device_customer_mapping(customer_id, dev_serial);
CREATE INDEX idx_is_active ON device_customer_mapping(is_active);
```

**外键约束**:
```sql
ALTER TABLE device_customer_mapping
ADD CONSTRAINT fk_device_customer
FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
ON DELETE CASCADE;
```

---

#### 3. NotificationConfig (通知配置实体)

**表名**: `customer_notification_configs`

**字段定义**:

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `id` | BIGSERIAL | PRIMARY KEY | 自增主键 |
| `customer_id` | VARCHAR(50) | UNIQUE, NOT NULL | 客户ID |
| **Email配置** | | | |
| `email_enabled` | BOOLEAN | DEFAULT true | 启用邮件通知 |
| `email_recipients` | TEXT | | 收件人列表 (JSON数组) |
| **SMS配置** | | | |
| `sms_enabled` | BOOLEAN | DEFAULT false | 启用短信通知 |
| `sms_recipients` | TEXT | | 手机号列表 (JSON数组) |
| **Slack配置** | | | |
| `slack_enabled` | BOOLEAN | DEFAULT false | 启用Slack通知 |
| `slack_webhook_url` | VARCHAR(500) | | Slack Webhook URL |
| `slack_channel` | VARCHAR(100) | | Slack频道名 |
| **Webhook配置** | | | |
| `webhook_enabled` | BOOLEAN | DEFAULT false | 启用Webhook通知 |
| `webhook_url` | VARCHAR(500) | | Webhook URL |
| `webhook_headers` | TEXT | | 自定义Header (JSON对象) |
| **告警级别过滤** | | | |
| `min_severity_level` | VARCHAR(20) | DEFAULT 'MEDIUM' | 最小通知级别 |
| `notify_on_severities` | TEXT | | 自定义通知级别 (JSON数组) |
| **频率控制** | | | |
| `max_notifications_per_hour` | INTEGER | DEFAULT 10 | 每小时最大通知数 |
| `enable_rate_limiting` | BOOLEAN | DEFAULT true | 启用频率限制 |
| **静默时段** | | | |
| `quiet_hours_enabled` | BOOLEAN | DEFAULT false | 启用静默时段 |
| `quiet_hours_start` | TIME | | 静默开始时间 |
| `quiet_hours_end` | TIME | | 静默结束时间 |
| `quiet_hours_timezone` | VARCHAR(50) | DEFAULT 'UTC' | 时区 |
| **元数据** | | | |
| `is_active` | BOOLEAN | DEFAULT true | 是否激活 |
| `created_at` | TIMESTAMP | DEFAULT now() | 创建时间 |
| `updated_at` | TIMESTAMP | DEFAULT now() | 更新时间 |
| `created_by` | VARCHAR(100) | | 创建人 |
| `updated_by` | VARCHAR(100) | | 更新人 |

**告警级别枚举**:
```java
public enum SeverityLevel {
    INFO,      // 信息级别
    LOW,       // 低危
    MEDIUM,    // 中危
    HIGH,      // 高危
    CRITICAL   // 严重
}
```

**JSON字段格式**:

```json
// email_recipients
["admin@example.com", "security@example.com"]

// sms_recipients
["+86-13800138000", "+86-13900139000"]

// webhook_headers
{
  "Authorization": "Bearer token123",
  "Content-Type": "application/json"
}

// notify_on_severities
["HIGH", "CRITICAL"]
```

**索引**:
```sql
CREATE UNIQUE INDEX idx_notification_customer_id ON customer_notification_configs(customer_id);
CREATE INDEX idx_email_enabled ON customer_notification_configs(email_enabled);
CREATE INDEX idx_is_active ON customer_notification_configs(is_active);
```

**外键约束**:
```sql
ALTER TABLE customer_notification_configs
ADD CONSTRAINT fk_notification_customer
FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
ON DELETE CASCADE;
```

---

### DTO (数据传输对象)

#### Customer相关DTO

**CreateCustomerRequest**:
```java
{
  "customerId": "customer-001",      // 必填
  "companyName": "示例科技公司",       // 必填
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "+86-13800138000",
  "subscriptionTier": "PROFESSIONAL",
  "subscriptionStartDate": "2025-01-01",
  "subscriptionEndDate": "2025-12-31",
  "notes": "VIP客户"
}
```

**UpdateCustomerRequest**:
```java
{
  "companyName": "示例科技有限公司",   // 选填
  "contactName": "李四",
  "contactEmail": "lisi@example.com",
  "contactPhone": "+86-13900139000",
  "status": "ACTIVE",
  "subscriptionTier": "ENTERPRISE",
  "notes": "升级为企业版"
}
```

**CustomerResponse**:
```java
{
  "id": 1,
  "customerId": "customer-001",
  "companyName": "示例科技公司",
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "+86-13800138000",
  "status": "ACTIVE",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 45,
  "subscriptionStartDate": "2025-01-01",
  "subscriptionEndDate": "2025-12-31",
  "notes": "VIP客户",
  "isActive": true,
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

#### Device相关DTO

**DeviceMappingRequest**:
```java
{
  "devSerial": "DEV-001",           // 必填
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室"
}
```

**BatchDeviceMappingRequest**:
```java
{
  "devices": [
    {
      "devSerial": "DEV-001",
      "deviceName": "网关设备01",
      "deviceType": "Gateway",
      "location": "北京办公室"
    },
    {
      "devSerial": "DEV-002",
      "deviceName": "网关设备02",
      "deviceType": "Gateway",
      "location": "上海办公室"
    }
  ]
}
```

**DeviceMappingResponse**:
```java
{
  "id": 1,
  "customerId": "customer-001",
  "devSerial": "DEV-001",
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室",
  "isActive": true,
  "bindTime": "2025-01-01T08:00:00Z",
  "unbindTime": null,
  "createdAt": "2025-01-01T08:00:00Z",
  "updatedAt": "2025-01-01T08:00:00Z"
}
```

**DeviceQuotaResponse**:
```java
{
  "customerId": "customer-001",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 45,
  "availableDevices": 55,
  "usagePercentage": 45.0
}
```

**BatchOperationResponse**:
```java
{
  "totalRequested": 10,
  "successCount": 8,
  "failureCount": 2,
  "results": [
    {
      "devSerial": "DEV-001",
      "success": true,
      "message": "Device bound successfully"
    },
    {
      "devSerial": "DEV-002",
      "success": false,
      "message": "Device already bound to another customer"
    }
  ]
}
```

#### NotificationConfig相关DTO

**NotificationConfigRequest**:
```java
{
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com", "security@example.com"],
  "smsEnabled": false,
  "smsRecipients": ["+86-13800138000"],
  "slackEnabled": true,
  "slackWebhookUrl": "https://hooks.slack.com/services/xxx",
  "slackChannel": "#security-alerts",
  "webhookEnabled": false,
  "webhookUrl": "https://api.example.com/webhook",
  "webhookHeaders": {
    "Authorization": "Bearer token123"
  },
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["HIGH", "CRITICAL"],
  "maxNotificationsPerHour": 20,
  "enableRateLimiting": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai"
}
```

**NotificationConfigResponse**:
```java
{
  "id": 1,
  "customerId": "customer-001",
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com", "security@example.com"],
  "smsEnabled": false,
  "smsRecipients": ["+86-13800138000"],
  "slackEnabled": true,
  "slackWebhookUrl": "https://hooks.slack.com/services/xxx",
  "slackChannel": "#security-alerts",
  "webhookEnabled": false,
  "webhookUrl": "https://api.example.com/webhook",
  "webhookHeaders": {
    "Authorization": "Bearer token123"
  },
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["HIGH", "CRITICAL"],
  "maxNotificationsPerHour": 20,
  "enableRateLimiting": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai",
  "isActive": true,
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

---

## 第2部分：Customer CRUD API

### API概览

Customer CRUD API提供完整的客户生命周期管理功能，所有端点都位于 `/api/v1/customers` 路径下。

**端点列表**:

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/v1/customers` | 创建客户 |
| GET | `/api/v1/customers/{customerId}` | 获取单个客户 |
| GET | `/api/v1/customers` | 获取客户列表 (分页) |
| GET | `/api/v1/customers/search` | 搜索客户 |
| GET | `/api/v1/customers/status/{status}` | 按状态查询客户 |
| PUT | `/api/v1/customers/{customerId}` | 更新客户信息 |
| DELETE | `/api/v1/customers/{customerId}` | 删除客户 (软删除) |
| DELETE | `/api/v1/customers/{customerId}/hard` | 硬删除客户 (物理删除) |
| GET | `/api/v1/customers/statistics` | 获取客户统计信息 |

---

### 1. 创建客户

创建新客户，自动分配订阅套餐对应的设备配额。

#### 请求

```http
POST /api/v1/customers HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "customerId": "customer-001",
  "companyName": "示例科技公司",
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "+86-13800138000",
  "subscriptionTier": "PROFESSIONAL",
  "subscriptionStartDate": "2025-01-01",
  "subscriptionEndDate": "2025-12-31",
  "notes": "VIP客户"
}
```

#### 请求参数

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `customerId` | String | ✅ | 客户唯一标识，长度1-50 | "customer-001" |
| `companyName` | String | ✅ | 公司名称，长度1-255 | "示例科技公司" |
| `contactName` | String | ❌ | 联系人姓名 | "张三" |
| `contactEmail` | String | ❌ | 联系邮箱，需符合邮箱格式 | "zhangsan@example.com" |
| `contactPhone` | String | ❌ | 联系电话 | "+86-13800138000" |
| `subscriptionTier` | String | ❌ | 订阅套餐，默认FREE | "PROFESSIONAL" |
| `subscriptionStartDate` | String | ❌ | 订阅开始日期 (YYYY-MM-DD) | "2025-01-01" |
| `subscriptionEndDate` | String | ❌ | 订阅结束日期 (YYYY-MM-DD) | "2025-12-31" |
| `notes` | String | ❌ | 备注信息 | "VIP客户" |

**订阅套餐选项**:
- `FREE`: 免费版 (5个设备)
- `BASIC`: 基础版 (20个设备)
- `PROFESSIONAL`: 专业版 (100个设备)
- `ENTERPRISE`: 企业版 (10000个设备)

#### 响应

**成功 (201 Created)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "companyName": "示例科技公司",
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "+86-13800138000",
  "status": "ACTIVE",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 0,
  "subscriptionStartDate": "2025-01-01",
  "subscriptionEndDate": "2025-12-31",
  "notes": "VIP客户",
  "isActive": true,
  "createdAt": "2025-10-16T10:30:00Z",
  "updatedAt": "2025-10-16T10:30:00Z",
  "createdBy": null,
  "updatedBy": null
}
```

**失败 (400 Bad Request)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Customer ID already exists: customer-001",
  "path": "/api/v1/customers"
}
```

#### cURL示例

```bash
curl -X POST http://localhost:8084/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "companyName": "示例科技公司",
    "contactName": "张三",
    "contactEmail": "zhangsan@example.com",
    "subscriptionTier": "PROFESSIONAL"
  }'
```

---

### 2. 获取单个客户

根据客户ID查询客户详细信息。

#### 请求

```http
GET /api/v1/customers/customer-001 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "companyName": "示例科技公司",
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "+86-13800138000",
  "status": "ACTIVE",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 45,
  "subscriptionStartDate": "2025-01-01",
  "subscriptionEndDate": "2025-12-31",
  "notes": "VIP客户",
  "isActive": true,
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

**失败 (404 Not Found)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Customer not found: customer-999",
  "path": "/api/v1/customers/customer-999"
}
```

#### cURL示例

```bash
curl -X GET http://localhost:8084/api/v1/customers/customer-001
```

---

### 3. 获取客户列表

分页查询所有客户，支持排序。

#### 请求

```http
GET /api/v1/customers?page=0&size=20&sort=createdAt,desc HTTP/1.1
Host: localhost:8084
```

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | Integer | ❌ | 0 | 页码 (从0开始) |
| `size` | Integer | ❌ | 20 | 每页数量 |
| `sort` | String | ❌ | createdAt,desc | 排序字段和方向 |

**排序选项**:
- `createdAt,desc`: 按创建时间降序
- `createdAt,asc`: 按创建时间升序
- `companyName,asc`: 按公司名称升序
- `currentDevices,desc`: 按设备数量降序

#### 响应

**成功 (200 OK)**:
```json
{
  "content": [
    {
      "id": 1,
      "customerId": "customer-001",
      "companyName": "示例科技公司",
      "status": "ACTIVE",
      "subscriptionTier": "PROFESSIONAL",
      "maxDevices": 100,
      "currentDevices": 45,
      "createdAt": "2025-01-01T00:00:00Z"
    },
    {
      "id": 2,
      "customerId": "customer-002",
      "companyName": "测试公司",
      "status": "ACTIVE",
      "subscriptionTier": "BASIC",
      "maxDevices": 20,
      "currentDevices": 8,
      "createdAt": "2025-01-02T00:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 2,
  "first": true,
  "empty": false
}
```

#### cURL示例

```bash
# 默认查询
curl -X GET "http://localhost:8084/api/v1/customers"

# 分页排序
curl -X GET "http://localhost:8084/api/v1/customers?page=0&size=10&sort=companyName,asc"
```

---

### 4. 搜索客户

根据关键词搜索客户，支持公司名、联系人、邮箱模糊匹配。

#### 请求

```http
GET /api/v1/customers/search?keyword=科技&page=0&size=20 HTTP/1.1
Host: localhost:8084
```

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `keyword` | String | ✅ | - | 搜索关键词 |
| `page` | Integer | ❌ | 0 | 页码 |
| `size` | Integer | ❌ | 20 | 每页数量 |

**搜索范围**:
- 公司名称 (companyName)
- 联系人姓名 (contactName)
- 联系邮箱 (contactEmail)

#### 响应

**成功 (200 OK)**:
```json
{
  "content": [
    {
      "id": 1,
      "customerId": "customer-001",
      "companyName": "示例科技公司",
      "contactName": "张三",
      "contactEmail": "zhangsan@example.com",
      "status": "ACTIVE",
      "subscriptionTier": "PROFESSIONAL",
      "maxDevices": 100,
      "currentDevices": 45
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

#### cURL示例

```bash
curl -X GET "http://localhost:8084/api/v1/customers/search?keyword=科技"
```

---

### 5. 按状态查询客户

查询指定状态的客户列表。

#### 请求

```http
GET /api/v1/customers/status/ACTIVE?page=0&size=20 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `status` | String | 客户状态: ACTIVE, INACTIVE, SUSPENDED |

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | Integer | ❌ | 0 | 页码 |
| `size` | Integer | ❌ | 20 | 每页数量 |

#### 响应

**成功 (200 OK)**:
```json
{
  "content": [
    {
      "id": 1,
      "customerId": "customer-001",
      "companyName": "示例科技公司",
      "status": "ACTIVE",
      "subscriptionTier": "PROFESSIONAL",
      "maxDevices": 100,
      "currentDevices": 45
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### cURL示例

```bash
curl -X GET "http://localhost:8084/api/v1/customers/status/ACTIVE"
```

---

### 6. 更新客户信息

更新客户的基本信息、订阅套餐等。

#### 请求

```http
PUT /api/v1/customers/customer-001 HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "companyName": "示例科技有限公司",
  "contactName": "李四",
  "contactEmail": "lisi@example.com",
  "contactPhone": "+86-13900139000",
  "status": "ACTIVE",
  "subscriptionTier": "ENTERPRISE",
  "notes": "升级为企业版"
}
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 请求参数

所有字段均为**可选**，只更新提供的字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| `companyName` | String | 公司名称 |
| `contactName` | String | 联系人姓名 |
| `contactEmail` | String | 联系邮箱 |
| `contactPhone` | String | 联系电话 |
| `status` | String | 客户状态: ACTIVE, INACTIVE, SUSPENDED |
| `subscriptionTier` | String | 订阅套餐 (会自动更新maxDevices) |
| `subscriptionStartDate` | String | 订阅开始日期 |
| `subscriptionEndDate` | String | 订阅结束日期 |
| `notes` | String | 备注信息 |

**注意**: 
- 更新 `subscriptionTier` 会自动更新 `maxDevices`
- 如果 `currentDevices > 新的maxDevices`，更新会失败

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "companyName": "示例科技有限公司",
  "contactName": "李四",
  "contactEmail": "lisi@example.com",
  "contactPhone": "+86-13900139000",
  "status": "ACTIVE",
  "subscriptionTier": "ENTERPRISE",
  "maxDevices": 10000,
  "currentDevices": 45,
  "notes": "升级为企业版",
  "updatedAt": "2025-10-16T11:00:00Z"
}
```

**失败 (400 Bad Request)**:
```json
{
  "timestamp": "2025-10-16T11:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot downgrade: current devices (45) exceeds new limit (20)",
  "path": "/api/v1/customers/customer-001"
}
```

#### cURL示例

```bash
curl -X PUT http://localhost:8084/api/v1/customers/customer-001 \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionTier": "ENTERPRISE",
    "notes": "升级为企业版"
  }'
```

---

### 7. 删除客户 (软删除)

将客户状态设置为INACTIVE，保留数据。

#### 请求

```http
DELETE /api/v1/customers/customer-001 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "message": "Customer deactivated successfully",
  "customerId": "customer-001"
}
```

**失败 (404 Not Found)**:
```json
{
  "timestamp": "2025-10-16T11:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Customer not found: customer-999",
  "path": "/api/v1/customers/customer-999"
}
```

#### cURL示例

```bash
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001
```

---

### 8. 硬删除客户 (物理删除)

⚠️ **危险操作**: 永久删除客户数据，包括关联的设备绑定和通知配置。

#### 请求

```http
DELETE /api/v1/customers/customer-001/hard HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "message": "Customer permanently deleted",
  "customerId": "customer-001"
}
```

**级联删除**:
- ✅ 自动删除 `device_customer_mapping` 中的所有设备绑定
- ✅ 自动删除 `customer_notification_configs` 中的通知配置
- ✅ 永久删除客户记录

#### cURL示例

```bash
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001/hard
```

---

### 9. 获取客户统计信息

查询客户的统计数据，包括状态分布、订阅套餐分布等。

#### 请求

```http
GET /api/v1/customers/statistics HTTP/1.1
Host: localhost:8084
```

#### 响应

**成功 (200 OK)**:
```json
{
  "totalCustomers": 150,
  "statusDistribution": {
    "ACTIVE": 120,
    "INACTIVE": 20,
    "SUSPENDED": 10
  },
  "subscriptionTierDistribution": {
    "FREE": 50,
    "BASIC": 40,
    "PROFESSIONAL": 35,
    "ENTERPRISE": 25
  },
  "totalDevices": 4500,
  "averageDevicesPerCustomer": 30.0,
  "deviceUtilization": {
    "totalMaxDevices": 15000,
    "totalCurrentDevices": 4500,
    "utilizationRate": 0.30
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalCustomers` | Integer | 总客户数 |
| `statusDistribution` | Object | 按状态分布 |
| `subscriptionTierDistribution` | Object | 按订阅套餐分布 |
| `totalDevices` | Integer | 所有客户的设备总数 |
| `averageDevicesPerCustomer` | Double | 平均每客户设备数 |
| `deviceUtilization.totalMaxDevices` | Integer | 所有客户的最大设备总数 |
| `deviceUtilization.totalCurrentDevices` | Integer | 所有客户的当前设备总数 |
| `deviceUtilization.utilizationRate` | Double | 设备使用率 (0-1) |

#### cURL示例

```bash
curl -X GET http://localhost:8084/api/v1/customers/statistics
```

---

### 错误处理

#### 通用错误响应格式

```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: companyName must not be blank",
  "path": "/api/v1/customers"
}
```

#### 常见错误码

| HTTP状态码 | 说明 | 常见原因 |
|-----------|------|---------|
| 400 | Bad Request | 请求参数验证失败、客户ID已存在、设备数超限 |
| 404 | Not Found | 客户不存在 |
| 409 | Conflict | 资源冲突 (如客户ID重复) |
| 500 | Internal Server Error | 服务器内部错误 |

#### 验证错误示例

```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "customerId",
      "message": "must not be blank"
    },
    {
      "field": "companyName",
      "message": "must not be blank"
    },
    {
      "field": "contactEmail",
      "message": "must be a well-formed email address"
    }
  ],
  "path": "/api/v1/customers"
}
```

---

## 第3部分：Device Binding API

### API概览

Device Binding API提供设备与客户的绑定关系管理，包括单个/批量操作、配额管理等。所有端点都位于 `/api/v1/customers/{customerId}/devices` 路径下。

**端点列表**:

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/devices` | 绑定单个设备 |
| POST | `/devices/batch` | 批量绑定设备 |
| GET | `/devices` | 获取客户的所有设备 |
| GET | `/devices/{devSerial}` | 获取单个设备详情 |
| DELETE | `/devices/{devSerial}` | 解绑单个设备 |
| DELETE | `/devices/batch` | 批量解绑设备 |
| GET | `/devices/quota` | 获取设备配额信息 |
| POST | `/devices/sync` | 同步设备计数 |
| PATCH | `/devices/{devSerial}/status` | 激活/停用设备 |

---

### 1. 绑定单个设备

将单个设备绑定到客户，自动验证配额限制。

#### 请求

```http
POST /api/v1/customers/customer-001/devices HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "devSerial": "DEV-001",
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室"
}
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `devSerial` | String | ✅ | 设备序列号 (唯一) | "DEV-001" |
| `deviceName` | String | ❌ | 设备名称 | "网关设备01" |
| `deviceType` | String | ❌ | 设备类型 | "Gateway" |
| `location` | String | ❌ | 设备位置 | "北京办公室" |

#### 响应

**成功 (201 Created)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "devSerial": "DEV-001",
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室",
  "isActive": true,
  "bindTime": "2025-10-16T10:30:00Z",
  "unbindTime": null,
  "createdAt": "2025-10-16T10:30:00Z",
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

**失败 (400 Bad Request - 超出配额)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Device quota exceeded: current 100, max 100",
  "path": "/api/v1/customers/customer-001/devices"
}
```

**失败 (409 Conflict - 设备已绑定)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Device already bound: DEV-001",
  "path": "/api/v1/customers/customer-001/devices"
}
```

#### cURL示例

```bash
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices \
  -H "Content-Type: application/json" \
  -d '{
    "devSerial": "DEV-001",
    "deviceName": "网关设备01",
    "deviceType": "Gateway",
    "location": "北京办公室"
  }'
```

---

### 2. 批量绑定设备

一次性绑定多个设备，最多100个。

#### 请求

```http
POST /api/v1/customers/customer-001/devices/batch HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "devices": [
    {
      "devSerial": "DEV-001",
      "deviceName": "网关设备01",
      "deviceType": "Gateway",
      "location": "北京办公室"
    },
    {
      "devSerial": "DEV-002",
      "deviceName": "网关设备02",
      "deviceType": "Gateway",
      "location": "上海办公室"
    },
    {
      "devSerial": "DEV-003",
      "deviceName": "传感器01",
      "deviceType": "Sensor",
      "location": "深圳办公室"
    }
  ]
}
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `devices` | Array | ✅ | 设备列表，最多100个 |
| `devices[].devSerial` | String | ✅ | 设备序列号 |
| `devices[].deviceName` | String | ❌ | 设备名称 |
| `devices[].deviceType` | String | ❌ | 设备类型 |
| `devices[].location` | String | ❌ | 设备位置 |

#### 响应

**成功 (201 Created)**:
```json
{
  "totalRequested": 3,
  "successCount": 2,
  "failureCount": 1,
  "results": [
    {
      "devSerial": "DEV-001",
      "success": true,
      "message": "Device bound successfully",
      "device": {
        "id": 1,
        "customerId": "customer-001",
        "devSerial": "DEV-001",
        "deviceName": "网关设备01",
        "isActive": true
      }
    },
    {
      "devSerial": "DEV-002",
      "success": true,
      "message": "Device bound successfully",
      "device": {
        "id": 2,
        "customerId": "customer-001",
        "devSerial": "DEV-002",
        "deviceName": "网关设备02",
        "isActive": true
      }
    },
    {
      "devSerial": "DEV-003",
      "success": false,
      "message": "Device quota exceeded",
      "device": null
    }
  ]
}
```

**失败 (400 Bad Request - 超过100个)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Batch size exceeds limit: 150 > 100",
  "path": "/api/v1/customers/customer-001/devices/batch"
}
```

#### cURL示例

```bash
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '{
    "devices": [
      {"devSerial": "DEV-001", "deviceName": "网关设备01"},
      {"devSerial": "DEV-002", "deviceName": "网关设备02"}
    ]
  }'
```

---

### 3. 获取客户的所有设备

分页查询客户绑定的设备列表，支持激活状态过滤。

#### 请求

```http
GET /api/v1/customers/customer-001/devices?isActive=true&page=0&size=20 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `isActive` | Boolean | ❌ | - | 是否只查询激活设备 |
| `page` | Integer | ❌ | 0 | 页码 (从0开始) |
| `size` | Integer | ❌ | 20 | 每页数量 |

#### 响应

**成功 (200 OK)**:
```json
{
  "content": [
    {
      "id": 1,
      "customerId": "customer-001",
      "devSerial": "DEV-001",
      "deviceName": "网关设备01",
      "deviceType": "Gateway",
      "location": "北京办公室",
      "isActive": true,
      "bindTime": "2025-01-01T08:00:00Z",
      "unbindTime": null,
      "createdAt": "2025-01-01T08:00:00Z",
      "updatedAt": "2025-01-01T08:00:00Z"
    },
    {
      "id": 2,
      "customerId": "customer-001",
      "devSerial": "DEV-002",
      "deviceName": "网关设备02",
      "deviceType": "Gateway",
      "location": "上海办公室",
      "isActive": true,
      "bindTime": "2025-01-02T09:00:00Z",
      "unbindTime": null,
      "createdAt": "2025-01-02T09:00:00Z",
      "updatedAt": "2025-01-02T09:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true,
  "empty": false
}
```

#### cURL示例

```bash
# 查询所有设备
curl -X GET "http://localhost:8084/api/v1/customers/customer-001/devices"

# 只查询激活设备
curl -X GET "http://localhost:8084/api/v1/customers/customer-001/devices?isActive=true"

# 分页查询
curl -X GET "http://localhost:8084/api/v1/customers/customer-001/devices?page=0&size=10"
```

---

### 4. 获取单个设备详情

查询客户指定设备的详细信息。

#### 请求

```http
GET /api/v1/customers/customer-001/devices/DEV-001 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |
| `devSerial` | String | 设备序列号 |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "devSerial": "DEV-001",
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室",
  "isActive": true,
  "bindTime": "2025-01-01T08:00:00Z",
  "unbindTime": null,
  "createdAt": "2025-01-01T08:00:00Z",
  "updatedAt": "2025-01-01T08:00:00Z"
}
```

**失败 (404 Not Found)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Device not found: DEV-999",
  "path": "/api/v1/customers/customer-001/devices/DEV-999"
}
```

#### cURL示例

```bash
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001
```

---

### 5. 解绑单个设备

解除设备与客户的绑定关系，自动更新设备计数。

#### 请求

```http
DELETE /api/v1/customers/customer-001/devices/DEV-001 HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |
| `devSerial` | String | 设备序列号 |

#### 响应

**成功 (200 OK)**:
```json
{
  "message": "Device unbound successfully",
  "customerId": "customer-001",
  "devSerial": "DEV-001"
}
```

**失败 (404 Not Found)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Device not found: DEV-999",
  "path": "/api/v1/customers/customer-001/devices/DEV-999"
}
```

#### cURL示例

```bash
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001
```

---

### 6. 批量解绑设备

一次性解绑多个设备。

#### 请求

```http
DELETE /api/v1/customers/customer-001/devices/batch HTTP/1.1
Host: localhost:8084
Content-Type: application/json

["DEV-001", "DEV-002", "DEV-003"]
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 请求参数

设备序列号数组 (JSON Array)

#### 响应

**成功 (200 OK)**:
```json
{
  "totalRequested": 3,
  "successCount": 2,
  "failureCount": 1,
  "results": [
    {
      "devSerial": "DEV-001",
      "success": true,
      "message": "Device unbound successfully"
    },
    {
      "devSerial": "DEV-002",
      "success": true,
      "message": "Device unbound successfully"
    },
    {
      "devSerial": "DEV-003",
      "success": false,
      "message": "Device not found"
    }
  ]
}
```

#### cURL示例

```bash
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '["DEV-001", "DEV-002", "DEV-003"]'
```

---

### 7. 获取设备配额信息

查询客户的设备配额使用情况。

#### 请求

```http
GET /api/v1/customers/customer-001/devices/quota HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "customerId": "customer-001",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 45,
  "availableDevices": 55,
  "usagePercentage": 45.0,
  "isQuotaExceeded": false
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户ID |
| `subscriptionTier` | String | 订阅套餐 |
| `maxDevices` | Integer | 最大设备数 |
| `currentDevices` | Integer | 当前设备数 |
| `availableDevices` | Integer | 可用设备数 (maxDevices - currentDevices) |
| `usagePercentage` | Double | 使用率 (0-100) |
| `isQuotaExceeded` | Boolean | 是否超出配额 |

#### cURL示例

```bash
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices/quota
```

---

### 8. 同步设备计数

手动同步客户的设备计数，确保 `current_devices` 准确。

#### 请求

```http
POST /api/v1/customers/customer-001/devices/sync HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "customerId": "customer-001",
  "subscriptionTier": "PROFESSIONAL",
  "maxDevices": 100,
  "currentDevices": 45,
  "availableDevices": 55,
  "usagePercentage": 45.0,
  "syncedAt": "2025-10-16T10:30:00Z"
}
```

**用途**:
- 定期同步确保数据一致性
- 修复设备计数不准确的问题
- 系统维护后的验证

#### cURL示例

```bash
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/sync
```

---

### 9. 激活/停用设备

切换设备的激活状态，不删除绑定关系。

#### 请求

```http
PATCH /api/v1/customers/customer-001/devices/DEV-001/status?isActive=false HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |
| `devSerial` | String | 设备序列号 |

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `isActive` | Boolean | ✅ | 激活状态: true (激活) / false (停用) |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "devSerial": "DEV-001",
  "deviceName": "网关设备01",
  "deviceType": "Gateway",
  "location": "北京办公室",
  "isActive": false,
  "bindTime": "2025-01-01T08:00:00Z",
  "unbindTime": null,
  "createdAt": "2025-01-01T08:00:00Z",
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

**说明**:
- 停用设备 (`isActive=false`): 保留绑定关系，但不计入 `current_devices`
- 激活设备 (`isActive=true`): 恢复激活状态，计入 `current_devices`
- 与解绑的区别: 停用后仍可重新激活，解绑需重新绑定

#### cURL示例

```bash
# 停用设备
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001/status?isActive=false"

# 激活设备
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001/status?isActive=true"
```

---

### 设备管理最佳实践

#### 1. 配额管理

**绑定前检查**:
```bash
# 1. 查询配额
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices/quota

# 2. 根据availableDevices决定是否绑定
# 3. 如果配额不足，提示升级订阅套餐
```

**批量绑定策略**:
- 每批最多100个设备
- 超过100个时分批处理
- 检查每批响应的 `successCount` 和 `failureCount`
- 记录失败的设备序列号，稍后重试

#### 2. 设备状态管理

**临时停用设备**:
```bash
# 使用停用而非解绑
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001/status?isActive=false"
```

**永久移除设备**:
```bash
# 使用解绑
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001
```

#### 3. 数据一致性

**定期同步**:
```bash
# 每天同步一次设备计数
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/sync
```

**异常修复**:
- 发现 `current_devices` 不准确时立即同步
- 数据库直接修改后必须同步
- 批量操作后建议同步验证

#### 4. 查询优化

**分页查询**:
```bash
# 大量设备时使用分页
curl -X GET "http://localhost:8084/api/v1/customers/customer-001/devices?page=0&size=50"
```

**状态过滤**:
```bash
# 只查询激活设备
curl -X GET "http://localhost:8084/api/v1/customers/customer-001/devices?isActive=true"
```

---

## 第4部分：Notification Config API

### API概览

Notification Config API提供客户通知配置的完整管理，支持Email/SMS/Slack/Webhook四种通知渠道。所有端点都位于 `/api/v1/customers/{customerId}/notification-config` 路径下。

**端点列表**:

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/notification-config` | 获取通知配置 |
| PUT | `/notification-config` | 创建或更新通知配置 |
| PATCH | `/notification-config` | 部分更新通知配置 |
| DELETE | `/notification-config` | 删除通知配置 (恢复默认) |
| POST | `/notification-config/test` | 测试通知配置 |
| PATCH | `/notification-config/email/toggle` | 快速开关邮件通知 |
| PATCH | `/notification-config/slack/toggle` | 快速开关Slack通知 |
| PATCH | `/notification-config/webhook/toggle` | 快速开关Webhook通知 |

---

### 1. 获取通知配置

查询客户的通知配置，如果不存在则返回默认配置。

#### 请求

```http
GET /api/v1/customers/customer-001/notification-config HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com", "security@example.com"],
  "smsEnabled": false,
  "smsRecipients": ["+86-13800138000"],
  "slackEnabled": true,
  "slackWebhookUrl": "https://hooks.slack.com/services/T00/B00/xxxx",
  "slackChannel": "#security-alerts",
  "webhookEnabled": false,
  "webhookUrl": "https://api.example.com/webhook",
  "webhookHeaders": {
    "Authorization": "Bearer token123",
    "Content-Type": "application/json"
  },
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["HIGH", "CRITICAL"],
  "maxNotificationsPerHour": 20,
  "enableRateLimiting": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai",
  "isActive": true,
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-10-16T10:30:00Z",
  "createdBy": "admin",
  "updatedBy": "admin"
}
```

**默认配置** (客户无自定义配置时):
```json
{
  "customerId": "customer-001",
  "emailEnabled": true,
  "emailRecipients": [],
  "smsEnabled": false,
  "smsRecipients": [],
  "slackEnabled": false,
  "slackWebhookUrl": null,
  "slackChannel": null,
  "webhookEnabled": false,
  "webhookUrl": null,
  "webhookHeaders": {},
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["CRITICAL", "HIGH", "MEDIUM"],
  "maxNotificationsPerHour": 10,
  "enableRateLimiting": true,
  "quietHoursEnabled": false,
  "quietHoursStart": null,
  "quietHoursEnd": null,
  "quietHoursTimezone": "UTC",
  "isActive": true
}
```

#### cURL示例

```bash
curl -X GET http://localhost:8084/api/v1/customers/customer-001/notification-config
```

---

### 2. 创建或更新通知配置

创建或更新客户的通知配置，支持部分字段更新。

#### 请求

```http
PUT /api/v1/customers/customer-001/notification-config HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com", "security@example.com"],
  "smsEnabled": false,
  "smsRecipients": ["+86-13800138000"],
  "slackEnabled": true,
  "slackWebhookUrl": "https://hooks.slack.com/services/T00/B00/xxxx",
  "slackChannel": "#security-alerts",
  "webhookEnabled": false,
  "webhookUrl": "https://api.example.com/webhook",
  "webhookHeaders": {
    "Authorization": "Bearer token123"
  },
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["HIGH", "CRITICAL"],
  "maxNotificationsPerHour": 20,
  "enableRateLimiting": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai"
}
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 请求参数

所有字段均为**可选**，未提供的字段将保持原值或使用默认值。

##### Email配置

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `emailEnabled` | Boolean | 启用邮件通知 | true |
| `emailRecipients` | Array<String> | 收件人邮箱列表 | ["admin@example.com"] |

##### SMS配置

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `smsEnabled` | Boolean | 启用短信通知 | false |
| `smsRecipients` | Array<String> | 收件人手机号列表 | ["+86-13800138000"] |

##### Slack配置

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `slackEnabled` | Boolean | 启用Slack通知 | true |
| `slackWebhookUrl` | String | Slack Webhook URL | "https://hooks.slack.com/..." |
| `slackChannel` | String | Slack频道名 | "#security-alerts" |

##### Webhook配置

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `webhookEnabled` | Boolean | 启用Webhook通知 | false |
| `webhookUrl` | String | Webhook URL | "https://api.example.com/webhook" |
| `webhookHeaders` | Object | 自定义HTTP Header | {"Authorization": "Bearer xxx"} |

##### 告警级别过滤

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `minSeverityLevel` | String | 最小通知级别 | "MEDIUM" |
| `notifyOnSeverities` | Array<String> | 自定义通知级别列表 | ["HIGH", "CRITICAL"] |

**告警级别选项**: `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

**级别优先级**: `INFO` < `LOW` < `MEDIUM` < `HIGH` < `CRITICAL`

**逻辑说明**:
- 如果设置 `minSeverityLevel="MEDIUM"`，则会通知 `MEDIUM`, `HIGH`, `CRITICAL`
- 如果设置 `notifyOnSeverities=["HIGH", "CRITICAL"]`，则只通知这两个级别
- `notifyOnSeverities` 优先级高于 `minSeverityLevel`

##### 频率控制

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `maxNotificationsPerHour` | Integer | 每小时最大通知数 | 20 |
| `enableRateLimiting` | Boolean | 启用频率限制 | true |

##### 静默时段

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `quietHoursEnabled` | Boolean | 启用静默时段 | true |
| `quietHoursStart` | String | 静默开始时间 (HH:mm:ss) | "22:00:00" |
| `quietHoursEnd` | String | 静默结束时间 (HH:mm:ss) | "08:00:00" |
| `quietHoursTimezone` | String | 时区 | "Asia/Shanghai" |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com", "security@example.com"],
  "slackEnabled": true,
  "slackWebhookUrl": "https://hooks.slack.com/services/T00/B00/xxxx",
  "slackChannel": "#security-alerts",
  "minSeverityLevel": "MEDIUM",
  "notifyOnSeverities": ["HIGH", "CRITICAL"],
  "maxNotificationsPerHour": 20,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00",
  "quietHoursTimezone": "Asia/Shanghai",
  "isActive": true,
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

**失败 (400 Bad Request)**:
```json
{
  "timestamp": "2025-10-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid email format: invalid-email",
  "path": "/api/v1/customers/customer-001/notification-config"
}
```

#### cURL示例

```bash
curl -X PUT http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "emailRecipients": ["admin@example.com"],
    "minSeverityLevel": "HIGH",
    "maxNotificationsPerHour": 20
  }'
```

---

### 3. 部分更新通知配置

PATCH方法，只更新提供的字段，其他字段保持不变。

#### 请求

```http
PATCH /api/v1/customers/customer-001/notification-config HTTP/1.1
Host: localhost:8084
Content-Type: application/json

{
  "emailEnabled": true,
  "maxNotificationsPerHour": 30
}
```

#### 响应

与PUT方法相同，返回更新后的完整配置。

#### cURL示例

```bash
curl -X PATCH http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "maxNotificationsPerHour": 30
  }'
```

---

### 4. 删除通知配置

删除客户的自定义配置，恢复为默认配置。

#### 请求

```http
DELETE /api/v1/customers/customer-001/notification-config HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "message": "Notification config deleted successfully",
  "customerId": "customer-001"
}
```

**说明**:
- 删除后，下次获取配置时将返回默认值
- 默认配置: `emailEnabled=true`, `minSeverityLevel=MEDIUM`, `maxNotificationsPerHour=10`

#### cURL示例

```bash
curl -X DELETE http://localhost:8084/api/v1/customers/customer-001/notification-config
```

---

### 5. 测试通知配置

验证通知配置的有效性，不实际发送通知。

#### 请求

```http
POST /api/v1/customers/customer-001/notification-config/test HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 响应

**成功 (200 OK)**:
```json
{
  "customerId": "customer-001",
  "testTimestamp": "2025-10-16T10:30:00Z",
  "results": {
    "email": {
      "enabled": true,
      "valid": true,
      "message": "Email configuration is valid",
      "recipients": ["admin@example.com", "security@example.com"],
      "recipientCount": 2
    },
    "sms": {
      "enabled": false,
      "valid": true,
      "message": "SMS disabled, no validation needed",
      "recipients": [],
      "recipientCount": 0
    },
    "slack": {
      "enabled": true,
      "valid": true,
      "message": "Slack webhook URL is valid",
      "webhookUrl": "https://hooks.slack.com/services/T00/B00/xxxx",
      "channel": "#security-alerts"
    },
    "webhook": {
      "enabled": false,
      "valid": true,
      "message": "Webhook disabled, no validation needed",
      "webhookUrl": null
    }
  },
  "overallValid": true,
  "warnings": [],
  "errors": []
}
```

**配置错误 (200 OK，但包含错误信息)**:
```json
{
  "customerId": "customer-001",
  "testTimestamp": "2025-10-16T10:30:00Z",
  "results": {
    "email": {
      "enabled": true,
      "valid": false,
      "message": "Invalid email format",
      "errors": ["invalid-email is not a valid email address"]
    },
    "slack": {
      "enabled": true,
      "valid": false,
      "message": "Invalid Slack webhook URL",
      "errors": ["Webhook URL must start with https://hooks.slack.com/"]
    }
  },
  "overallValid": false,
  "warnings": ["Email recipients list is empty"],
  "errors": ["Invalid email format", "Invalid Slack webhook URL"]
}
```

#### 测试项目

| 渠道 | 验证内容 |
|------|---------|
| **Email** | - 邮箱格式验证<br>- 收件人数量检查 |
| **SMS** | - 手机号格式验证<br>- 手机号数量检查 |
| **Slack** | - Webhook URL格式验证<br>- URL可达性检查 (可选) |
| **Webhook** | - URL格式验证<br>- Header格式验证 |

#### cURL示例

```bash
curl -X POST http://localhost:8084/api/v1/customers/customer-001/notification-config/test
```

---

### 6. 快速开关邮件通知

一键启用或停用邮件通知，无需提供完整配置。

#### 请求

```http
PATCH /api/v1/customers/customer-001/notification-config/email/toggle?enabled=true HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enabled` | Boolean | ✅ | 启用状态: true (启用) / false (停用) |

#### 响应

**成功 (200 OK)**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "emailEnabled": true,
  "emailRecipients": ["admin@example.com"],
  "updatedAt": "2025-10-16T10:30:00Z"
}
```

#### cURL示例

```bash
# 启用邮件通知
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/email/toggle?enabled=true"

# 停用邮件通知
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/email/toggle?enabled=false"
```

---

### 7. 快速开关Slack通知

一键启用或停用Slack通知。

#### 请求

```http
PATCH /api/v1/customers/customer-001/notification-config/slack/toggle?enabled=true HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enabled` | Boolean | ✅ | 启用状态 |

#### cURL示例

```bash
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/slack/toggle?enabled=true"
```

---

### 8. 快速开关Webhook通知

一键启用或停用Webhook通知。

#### 请求

```http
PATCH /api/v1/customers/customer-001/notification-config/webhook/toggle?enabled=false HTTP/1.1
Host: localhost:8084
```

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `customerId` | String | 客户唯一标识 |

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enabled` | Boolean | ✅ | 启用状态 |

#### cURL示例

```bash
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/webhook/toggle?enabled=false"
```

---

## 使用场景

### 场景1: 新客户注册

**流程**:
1. 创建客户
2. 绑定设备
3. 配置通知规则

```bash
# 1. 创建客户
curl -X POST http://localhost:8084/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "new-customer-001",
    "companyName": "新客户公司",
    "contactEmail": "admin@newcustomer.com",
    "subscriptionTier": "BASIC"
  }'

# 2. 绑定设备
curl -X POST http://localhost:8084/api/v1/customers/new-customer-001/devices \
  -H "Content-Type: application/json" \
  -d '{
    "devSerial": "DEV-101",
    "deviceName": "主网关",
    "deviceType": "Gateway"
  }'

# 3. 配置邮件通知
curl -X PUT http://localhost:8084/api/v1/customers/new-customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "emailRecipients": ["admin@newcustomer.com"],
    "minSeverityLevel": "HIGH"
  }'
```

---

### 场景2: 客户升级套餐

**流程**:
1. 更新订阅套餐
2. 批量绑定新设备
3. 调整通知频率限制

```bash
# 1. 升级到PROFESSIONAL套餐 (100设备)
curl -X PUT http://localhost:8084/api/v1/customers/customer-001 \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionTier": "PROFESSIONAL"
  }'

# 2. 批量绑定50个新设备
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '{
    "devices": [
      {"devSerial": "DEV-101", "deviceName": "设备101"},
      {"devSerial": "DEV-102", "deviceName": "设备102"},
      ...
    ]
  }'

# 3. 提高通知频率限制
curl -X PATCH http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "maxNotificationsPerHour": 50
  }'
```

---

### 场景3: 配置多渠道告警

**流程**:
1. 配置邮件通知
2. 配置Slack通知
3. 配置静默时段

```bash
# 配置多渠道通知
curl -X PUT http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "emailEnabled": true,
    "emailRecipients": ["admin@example.com", "security@example.com"],
    "slackEnabled": true,
    "slackWebhookUrl": "https://hooks.slack.com/services/T00/B00/xxxx",
    "slackChannel": "#security-alerts",
    "minSeverityLevel": "MEDIUM",
    "maxNotificationsPerHour": 30,
    "quietHoursEnabled": true,
    "quietHoursStart": "22:00:00",
    "quietHoursEnd": "08:00:00",
    "quietHoursTimezone": "Asia/Shanghai"
  }'
```

---

### 场景4: 临时停用设备

**流程**:
1. 查询设备列表
2. 停用指定设备
3. 验证配额变化

```bash
# 1. 查询设备
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices

# 2. 停用设备 (不删除绑定)
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/devices/DEV-001/status?isActive=false"

# 3. 查询配额 (current_devices应减少)
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices/quota
```

---

### 场景5: 搜索和筛选客户

```bash
# 按公司名搜索
curl -X GET "http://localhost:8084/api/v1/customers/search?keyword=科技"

# 按状态筛选
curl -X GET "http://localhost:8084/api/v1/customers/status/ACTIVE?page=0&size=20"

# 获取统计信息
curl -X GET http://localhost:8084/api/v1/customers/statistics
```

---

## 最佳实践

### 1. 配额管理

**定期检查配额**:
```bash
# 每日检查所有客户配额使用率
for customer in customer-001 customer-002 customer-003; do
  curl -X GET http://localhost:8084/api/v1/customers/$customer/devices/quota
done
```

**超限预警**:
- 设置 `usagePercentage >= 80%` 时发送预警
- 提示客户升级套餐或停用部分设备

**配额同步**:
```bash
# 每周同步一次
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/sync
```

---

### 2. 通知配置

**测试后启用**:
```bash
# 1. 先配置
curl -X PUT http://localhost:8084/api/v1/customers/customer-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{...}'

# 2. 测试配置
curl -X POST http://localhost:8084/api/v1/customers/customer-001/notification-config/test

# 3. 确认测试通过后启用
curl -X PATCH "http://localhost:8084/api/v1/customers/customer-001/notification-config/email/toggle?enabled=true"
```

**分级通知**:
```json
{
  "minSeverityLevel": "MEDIUM",  // 最低通知MEDIUM级别
  "notifyOnSeverities": ["HIGH", "CRITICAL"],  // 实际只通知这两个级别
  "maxNotificationsPerHour": 20,  // 每小时最多20条
  "quietHoursEnabled": true,  // 启用静默时段
  "quietHoursStart": "22:00:00",
  "quietHoursEnd": "08:00:00"
}
```

**多收件人策略**:
```json
{
  "emailRecipients": [
    "security-team@example.com",  // 安全团队
    "ops@example.com",            // 运维团队
    "manager@example.com"         // 管理层
  ]
}
```

---

### 3. 批量操作

**分批处理**:
```bash
# 绑定200个设备，分成2批
# 第1批: DEV-001 ~ DEV-100
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '{
    "devices": [...]  // 100个设备
  }'

# 第2批: DEV-101 ~ DEV-200
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '{
    "devices": [...]  // 100个设备
  }'
```

**错误重试**:
```bash
# 1. 执行批量操作
response=$(curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/batch \
  -H "Content-Type: application/json" \
  -d '{...}')

# 2. 解析失败的设备
failed_devices=$(echo $response | jq '.results[] | select(.success == false) | .devSerial')

# 3. 重试失败的设备 (单个绑定)
for dev in $failed_devices; do
  curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices \
    -H "Content-Type: application/json" \
    -d "{\"devSerial\": \"$dev\"}"
done
```

---

### 4. 错误处理

**客户端重试策略**:
```python
import requests
import time

def create_customer_with_retry(customer_data, max_retries=3):
    url = "http://localhost:8084/api/v1/customers"
    
    for attempt in range(max_retries):
        try:
            response = requests.post(url, json=customer_data, timeout=10)
            
            if response.status_code == 201:
                return response.json()
            elif response.status_code == 409:
                print(f"Customer already exists: {customer_data['customerId']}")
                return None
            elif response.status_code == 400:
                print(f"Validation error: {response.json()['message']}")
                return None
            else:
                # 5xx错误重试
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)  # 指数退避
                    continue
                else:
                    raise Exception(f"Failed after {max_retries} retries")
        except requests.exceptions.Timeout:
            if attempt < max_retries - 1:
                time.sleep(2 ** attempt)
                continue
            else:
                raise
```

**错误日志记录**:
```python
import logging

logger = logging.getLogger(__name__)

def bind_device(customer_id, device_data):
    try:
        response = requests.post(
            f"http://localhost:8084/api/v1/customers/{customer_id}/devices",
            json=device_data
        )
        response.raise_for_status()
        return response.json()
    except requests.exceptions.HTTPError as e:
        logger.error(f"Failed to bind device {device_data['devSerial']}: {e.response.text}")
        raise
```

---

### 5. 性能优化

**分页查询大数据**:
```bash
# 客户数量很多时使用分页
page=0
size=100
while true; do
  response=$(curl -X GET "http://localhost:8084/api/v1/customers?page=$page&size=$size")
  
  # 处理当前页数据
  echo $response | jq '.content[]'
  
  # 检查是否还有下一页
  is_last=$(echo $response | jq '.last')
  if [ "$is_last" == "true" ]; then
    break
  fi
  
  page=$((page + 1))
done
```

**缓存常用数据**:
```python
import requests
from functools import lru_cache

@lru_cache(maxsize=100)
def get_customer(customer_id):
    """缓存客户信息，5分钟内不重复请求"""
    response = requests.get(f"http://localhost:8084/api/v1/customers/{customer_id}")
    return response.json()
```

---

### 6. 监控和告警

**健康检查**:
```bash
# 检查服务健康状态
curl -X GET http://localhost:8084/actuator/health

# 预期响应
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

**指标监控**:
```bash
# Prometheus指标
curl -X GET http://localhost:8084/actuator/prometheus
```

---

## 故障排查

### 常见问题

#### 1. 设备配额超限

**问题**: 绑定设备时提示 "Device quota exceeded"

**排查**:
```bash
# 1. 查询配额
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices/quota

# 2. 查询当前设备
curl -X GET http://localhost:8084/api/v1/customers/customer-001/devices?isActive=true

# 3. 同步设备计数
curl -X POST http://localhost:8084/api/v1/customers/customer-001/devices/sync
```

**解决方案**:
- 升级订阅套餐
- 停用或解绑不用的设备
- 检查是否有设备计数不准确

---

#### 2. 通知配置无效

**问题**: 配置保存成功但不生效

**排查**:
```bash
# 1. 测试配置
curl -X POST http://localhost:8084/api/v1/customers/customer-001/notification-config/test

# 2. 检查配置详情
curl -X GET http://localhost:8084/api/v1/customers/customer-001/notification-config

# 3. 查看Alert-Management日志
docker logs alert-management
```

**解决方案**:
- 确认 `emailEnabled=true`
- 确认 `emailRecipients` 不为空
- 确认告警级别匹配 (`minSeverityLevel` 或 `notifyOnSeverities`)
- 确认未触发频率限制

---

#### 3. 客户不存在

**问题**: 404 Not Found

**排查**:
```bash
# 1. 列出所有客户
curl -X GET http://localhost:8084/api/v1/customers

# 2. 搜索客户
curl -X GET "http://localhost:8084/api/v1/customers/search?keyword=xxx"

# 3. 检查客户ID拼写
```

---

#### 4. 数据库连接失败

**问题**: 500 Internal Server Error

**排查**:
```bash
# 1. 检查服务健康状态
curl -X GET http://localhost:8084/actuator/health

# 2. 检查PostgreSQL容器状态
docker ps | grep postgres

# 3. 测试数据库连接
docker exec -it postgres psql -U threat_user -d threat_detection -c "SELECT 1"

# 4. 查看服务日志
docker logs customer-management
```

---

## 附录

### A. 完整API端点总结

| 类别 | 方法 | 端点 | 说明 |
|------|------|------|------|
| **Customer** | POST | `/api/v1/customers` | 创建客户 |
| | GET | `/api/v1/customers/{customerId}` | 获取单个客户 |
| | GET | `/api/v1/customers` | 获取客户列表 (分页) |
| | GET | `/api/v1/customers/search` | 搜索客户 |
| | GET | `/api/v1/customers/status/{status}` | 按状态查询 |
| | PUT | `/api/v1/customers/{customerId}` | 更新客户 |
| | DELETE | `/api/v1/customers/{customerId}` | 软删除客户 |
| | DELETE | `/api/v1/customers/{customerId}/hard` | 硬删除客户 |
| | GET | `/api/v1/customers/statistics` | 客户统计 |
| **Device** | POST | `/api/v1/customers/{customerId}/devices` | 绑定单个设备 |
| | POST | `/api/v1/customers/{customerId}/devices/batch` | 批量绑定设备 |
| | GET | `/api/v1/customers/{customerId}/devices` | 获取设备列表 |
| | GET | `/api/v1/customers/{customerId}/devices/{devSerial}` | 获取设备详情 |
| | DELETE | `/api/v1/customers/{customerId}/devices/{devSerial}` | 解绑单个设备 |
| | DELETE | `/api/v1/customers/{customerId}/devices/batch` | 批量解绑设备 |
| | GET | `/api/v1/customers/{customerId}/devices/quota` | 获取配额信息 |
| | POST | `/api/v1/customers/{customerId}/devices/sync` | 同步设备计数 |
| | PATCH | `/api/v1/customers/{customerId}/devices/{devSerial}/status` | 激活/停用设备 |
| **Notification** | GET | `/api/v1/customers/{customerId}/notification-config` | 获取通知配置 |
| | PUT | `/api/v1/customers/{customerId}/notification-config` | 创建或更新配置 |
| | PATCH | `/api/v1/customers/{customerId}/notification-config` | 部分更新配置 |
| | DELETE | `/api/v1/customers/{customerId}/notification-config` | 删除配置 |
| | POST | `/api/v1/customers/{customerId}/notification-config/test` | 测试配置 |
| | PATCH | `/api/v1/customers/{customerId}/notification-config/email/toggle` | 快速开关邮件 |
| | PATCH | `/api/v1/customers/{customerId}/notification-config/slack/toggle` | 快速开关Slack |
| | PATCH | `/api/v1/customers/{customerId}/notification-config/webhook/toggle` | 快速开关Webhook |

**总计**: 26个API端点

---

### B. 订阅套餐对照表

| 套餐 | 最大设备数 | 适用场景 | 推荐客户规模 |
|------|-----------|---------|-------------|
| **FREE** | 5 | 个人用户、小型测试 | 1-5台设备 |
| **BASIC** | 20 | 小微企业 | 5-20台设备 |
| **PROFESSIONAL** | 100 | 中小企业 | 20-100台设备 |
| **ENTERPRISE** | 10000 | 大型企业、集团 | 100+台设备 |

---

### C. 告警级别说明

| 级别 | 说明 | 建议通知方式 | 示例 |
|------|------|-------------|------|
| **INFO** | 信息级别 | 邮件 | 设备上线/下线 |
| **LOW** | 低危威胁 | 邮件 | 单次端口扫描 |
| **MEDIUM** | 中危威胁 | 邮件 + Slack | 多次扫描尝试 |
| **HIGH** | 高危威胁 | 邮件 + Slack + SMS | 横向移动检测 |
| **CRITICAL** | 严重威胁 | 所有渠道 + 电话 | APT攻击检测 |

---

### D. 时区列表

常用时区:
- `UTC`: 协调世界时
- `Asia/Shanghai`: 中国标准时间 (UTC+8)
- `America/New_York`: 美国东部时间
- `Europe/London`: 伦敦时间
- `Asia/Tokyo`: 日本标准时间

完整列表: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones

---

### E. 联系支持

**技术支持**:
- Email: support@threatdetection.com
- Slack: #customer-management-support
- 文档: https://docs.threatdetection.com

**问题反馈**:
- GitHub Issues: https://github.com/yourorg/threat-detection-system/issues
- 邮件: dev@threatdetection.com

---

**文档版本**: 1.0  
**最后更新**: 2025-10-16  
**文档完成** ✅

