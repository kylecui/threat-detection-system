# Customer Management Service

客户管理服务 - 多租户SaaS平台的客户CRUD和配置管理核心服务

## 📋 功能特性

### 核心功能
- ✅ **客户CRUD**: 创建、查询、更新、删除客户信息
- ✅ **多租户隔离**: 基于customerId的完整数据隔离
- ✅ **订阅管理**: 支持多种订阅套餐(FREE/BASIC/PROFESSIONAL/ENTERPRISE)
- ✅ **设备配额**: 基于订阅套餐的设备数量限制
- ✅ **状态管理**: 客户激活/暂停/停用状态管理
- ✅ **搜索功能**: 支持名称、ID、邮箱模糊搜索
- ✅ **统计分析**: 客户分布统计(状态、套餐)

### 订阅套餐

| 套餐 | 最大设备数 | 适用场景 |
|------|-----------|---------|
| **FREE** | 5 | 个人用户、小型测试 |
| **BASIC** | 20 | 小型企业 |
| **PROFESSIONAL** | 100 | 中型企业 |
| **ENTERPRISE** | 无限制 | 大型企业 |

---

## 🏗️ 架构设计

### 技术栈
- **框架**: Spring Boot 3.1.5
- **数据库**: PostgreSQL 15+
- **ORM**: Spring Data JPA + Hibernate
- **API**: RESTful API
- **验证**: Jakarta Validation
- **监控**: Spring Actuator + Prometheus

### 项目结构
```
customer-management/
├── src/main/java/com/threatdetection/customer/
│   ├── controller/         # REST API控制器
│   │   └── CustomerController.java
│   ├── service/           # 业务逻辑层
│   │   └── CustomerService.java
│   ├── repository/        # 数据访问层
│   │   └── CustomerRepository.java
│   ├── model/             # JPA实体
│   │   └── Customer.java
│   ├── dto/               # 数据传输对象
│   │   ├── CreateCustomerRequest.java
│   │   ├── UpdateCustomerRequest.java
│   │   └── CustomerResponse.java
│   ├── exception/         # 自定义异常
│   │   ├── CustomerNotFoundException.java
│   │   └── CustomerAlreadyExistsException.java
│   └── config/            # 配置类
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml    # 应用配置
│   └── schema.sql         # 数据库Schema
└── pom.xml                # Maven依赖
```

---

## 🚀 快速开始

### 前置要求
- JDK 21+
- Maven 3.8+
- PostgreSQL 15+

### 1. 配置数据库

```bash
# 创建数据库 (如果尚未创建)
psql -U threat_user -c "CREATE DATABASE threat_detection;"

# 执行Schema脚本
psql -U threat_user -d threat_detection -f src/main/resources/schema.sql
```

### 2. 配置环境变量

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/threat_detection
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=your_password
export SERVER_PORT=8083
```

### 3. 构建和运行

```bash
# 构建
mvn clean package

# 运行
mvn spring-boot:run

# 或直接运行jar
java -jar target/customer-management-1.0.0-SNAPSHOT.jar
```

### 4. 验证服务

```bash
# 健康检查
curl http://localhost:8083/actuator/health

# 获取所有客户
curl http://localhost:8083/api/v1/customers

# 创建客户
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "test_customer_001",
    "name": "Test Company",
    "email": "test@example.com",
    "subscriptionTier": "BASIC"
  }'
```

---

## 📡 API端点

### 客户管理

| 方法 | 端点 | 说明 |
|------|------|------|
| `POST` | `/api/v1/customers` | 创建客户 |
| `GET` | `/api/v1/customers/{customerId}` | 获取客户详情 |
| `GET` | `/api/v1/customers` | 获取所有客户(分页) |
| `GET` | `/api/v1/customers/search?keyword=xxx` | 搜索客户 |
| `GET` | `/api/v1/customers/status/{status}` | 按状态查询 |
| `PUT` | `/api/v1/customers/{customerId}` | 更新客户 |
| `DELETE` | `/api/v1/customers/{customerId}` | 删除客户(软删除) |
| `DELETE` | `/api/v1/customers/{customerId}/hard` | 硬删除客户 |
| `GET` | `/api/v1/customers/statistics` | 获取统计信息 |

### 示例请求

**创建客户**:
```bash
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "acme_corp",
    "name": "ACME Corporation",
    "email": "admin@acme.com",
    "phone": "+1-555-1234",
    "address": "123 Main St, New York, NY",
    "subscriptionTier": "PROFESSIONAL",
    "maxDevices": 100,
    "description": "Enterprise customer"
  }'
```

**更新客户**:
```bash
curl -X PUT http://localhost:8083/api/v1/customers/acme_corp \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ACME Corporation Inc.",
    "phone": "+1-555-5678",
    "subscriptionTier": "ENTERPRISE"
  }'
```

**搜索客户**:
```bash
curl "http://localhost:8083/api/v1/customers/search?keyword=acme&page=0&size=10"
```

**获取统计**:
```bash
curl http://localhost:8083/api/v1/customers/statistics
```

响应示例:
```json
{
  "totalCustomers": 5,
  "statusDistribution": {
    "ACTIVE": 4,
    "SUSPENDED": 1
  },
  "tierDistribution": {
    "FREE": 1,
    "BASIC": 2,
    "PROFESSIONAL": 1,
    "ENTERPRISE": 1
  }
}
```

---

## 🗄️ 数据库设计

### customers表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGSERIAL | 主键ID |
| `customer_id` | VARCHAR(100) | 客户唯一标识符(业务主键) |
| `name` | VARCHAR(200) | 客户名称 |
| `email` | VARCHAR(255) | 联系邮箱 |
| `phone` | VARCHAR(50) | 联系电话 |
| `address` | VARCHAR(500) | 地址 |
| `status` | VARCHAR(20) | 状态(ACTIVE/SUSPENDED/INACTIVE) |
| `subscription_tier` | VARCHAR(20) | 订阅套餐 |
| `max_devices` | INTEGER | 最大设备数 |
| `current_devices` | INTEGER | 当前设备数 |
| `description` | VARCHAR(1000) | 描述 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 更新时间 |
| `created_by` | VARCHAR(100) | 创建人 |
| `updated_by` | VARCHAR(100) | 更新人 |
| `subscription_start_date` | TIMESTAMP | 订阅开始日期 |
| `subscription_end_date` | TIMESTAMP | 订阅结束日期 |
| `alert_enabled` | BOOLEAN | 是否启用告警 |

**索引**:
- `idx_customer_id`: customer_id (唯一索引)
- `idx_customer_email`: email
- `idx_customer_status`: status

---

## 🔗 集成指南

### 与其他服务集成

**Data Ingestion Service**:
- 使用`DeviceCustomerMapping`表关联设备和客户
- 通过`customer_id`实现日志隔离

**Alert Management Service**:
- 使用`CustomerNotificationConfig`表配置通知
- 基于`customer_id`发送独立告警

**Threat Assessment Service**:
- 所有威胁评估结果按`customer_id`隔离
- 支持客户级别的威胁统计

---

## 🧪 测试

### 单元测试
```bash
mvn test
```

### 集成测试
```bash
# 使用H2内存数据库
mvn verify
```

### API测试
```bash
# 使用提供的测试脚本
./test_customer_api.sh
```

---

## 📊 监控和运维

### Actuator端点
- `/actuator/health` - 健康检查
- `/actuator/info` - 服务信息
- `/actuator/metrics` - 性能指标
- `/actuator/prometheus` - Prometheus格式指标

### Prometheus指标
```bash
curl http://localhost:8083/actuator/prometheus
```

### 日志
日志级别: DEBUG (开发环境), INFO (生产环境)
日志位置: 控制台输出

---

## 🔧 配置说明

### application.yml主要配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/threat_detection
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update  # 生产环境改为validate

server:
  port: 8083

logging:
  level:
    com.threatdetection.customer: DEBUG
```

---

## 📝 开发计划

### 已完成 ✅
- [x] 客户基础CRUD功能
- [x] 多租户数据隔离
- [x] 订阅套餐管理
- [x] 搜索和统计功能

### 待实现 ⏳
- [ ] 设备绑定管理API (DeviceManagementController)
- [ ] 客户通知配置API (NotificationConfigController)
- [ ] 批量导入/导出功能
- [ ] 客户使用报表
- [ ] API认证和授权
- [ ] 审计日志

---

## 🤝 贡献指南

遵循项目编码规范:
- 使用Lombok简化样板代码
- 使用SLF4J进行日志记录
- 所有API必须包含customerId上下文
- 编写单元测试覆盖核心逻辑

---

**服务端口**: 8083  
**API基础路径**: `/api/v1/customers`  
**健康检查**: `http://localhost:8083/actuator/health`
