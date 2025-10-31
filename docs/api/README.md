# API文档目录

**Cloud-Native Threat Detection System - REST API文档**

---

## 📖 文档说明

本目录包含所有微服务的REST API文档，每个文档都包含：
- ✅ 完整的curl命令示例
- ✅ Java代码示例（使用RestTemplate）
- ✅ 请求/响应JSON示例
- ✅ 实际使用场景
- ✅ 错误处理和故障排查

---

## 📂 服务分类

### � API Gateway (Port 8888)

统一的API入口，提供路由转发、安全控制、限流保护等功能。

| 文档 | 端点数量 | 状态 | 说明 |
|------|---------|------|------|
| **[api_gateway_current.md](./api_gateway_current.md)** | 5 | ✅ 完整 | 统一API入口、路由管理、熔断降级 |

**核心功能**:
- ✅ **路由管理**: 7个路由规则，自动转发到后端微服务
- ✅ **安全控制**: CORS跨域、请求日志、限流保护
- ✅ **熔断降级**: 服务不可用时友好错误响应
- ✅ **监控告警**: Actuator健康检查、Prometheus指标

**快速开始**:
```bash
# 通过Gateway访问Customer Management
curl http://localhost:8888/api/v1/customers

# 通过Gateway查询威胁评估
curl http://localhost:8888/api/v1/assessment/trends?customerId=customer-001

# 通过Gateway管理告警
curl http://localhost:8888/api/v1/alerts?severity=CRITICAL
```

---

### 🔶 客户管理服务 (Customer Management - Port 8084)

客户配置管理中心，负责客户信息、设备绑定和通知配置管理。

| 文档 | 端点数量 | 状态 | 说明 |
|------|---------|------|------|
| **[customer_management_api.md](./customer_management_api.md)** | 26 | ✅ 完整 | 客户CRUD、设备绑定、通知配置 |

**核心功能**:
- ✅ **客户管理**: 9个端点 - CRUD、搜索、统计
- ✅ **设备绑定**: 9个端点 - 绑定/解绑、配额管理
- ✅ **通知配置**: 8个端点 - Email/SMS/Slack/Webhook配置

**订阅套餐**:
- FREE: 5个设备
- BASIC: 20个设备
- PROFESSIONAL: 100个设备
- ENTERPRISE: 10000个设备

**快速开始**:
```bash
# 创建客户
curl -X POST http://localhost:8084/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"customer_id": "cust-001", "company_name": "示例公司", "subscription_tier": "PROFESSIONAL"}'

# 绑定设备
curl -X POST http://localhost:8084/api/v1/customers/cust-001/devices \
  -H "Content-Type: application/json" \
  -d '{"dev_serial": "DEV-001", "device_name": "网关01"}'

# 配置通知
curl -X PUT http://localhost:8084/api/v1/customers/cust-001/notification-config \
  -H "Content-Type: application/json" \
  -d '{"email_enabled": true, "email_recipients": ["admin@example.com"]}'
```

⚠️ **字段命名注意**: Customer Management服务使用 **snake_case** 命名 (如 `customer_id`, `subscription_tier`)，而非 camelCase。

---

### �🔵 数据摄取服务 (Port 8080)

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [data_ingestion_api.md](./data_ingestion_api.md) | 6 | 日志摄取、批量处理、统计、健康检查 |

**核心功能**: 接收syslog日志 → 解析 → 发布到Kafka

---

### 🟢 威胁评估服务 (Port 8083)

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [threat_assessment_overview.md](./threat_assessment_overview.md) | - | 系统概述、架构、评分算法 |
| [threat_assessment_evaluation_api.md](./threat_assessment_evaluation_api.md) | 2 | 执行威胁评估、缓解措施 |
| [threat_assessment_query_api.md](./threat_assessment_query_api.md) | 3 | 查询评估详情、趋势分析 |
| [customer_port_weights_api.md](./customer_port_weights_api.md) | 15 | 端口权重配置管理 |
| [weight_management_api.md](./weight_management_api.md) | 26 | 多租户权重配置管理 (新增) |
| [threat_assessment_client_guide.md](./threat_assessment_client_guide.md) | - | 完整Java客户端实现 |

**核心功能**: 威胁评分计算 → 风险等级判定 → 缓解建议

---

### 🟠 告警管理服务 (Port 8082)

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [alert_management_overview.md](./alert_management_overview.md) | - | 系统概述、架构、数据模型 |
| [alert_crud_api.md](./alert_crud_api.md) | 3 | 创建告警、查询详情、列表查询 |
| [alert_lifecycle_api.md](./alert_lifecycle_api.md) | 4 | 状态更新、解决、分配、归档 |
| [alert_escalation_api.md](./alert_escalation_api.md) | 2 | 手动升级、取消升级 |
| [alert_analytics_api.md](./alert_analytics_api.md) | 3 | 告警统计、通知统计、升级统计 |
| [alert_notification_api.md](./alert_notification_api.md) | 1 | 手动发送邮件通知 |
| [alert_maintenance_api.md](./alert_maintenance_api.md) | 1 | 归档旧告警 |

**核心功能**: 告警生命周期管理 → 通知发送 → 升级处理

---

### 🟣 集成测试服务

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [integration_test_api.md](./integration_test_api.md) | 2 | 测试统计、状态查询 |

**核心功能**: 监控邮件配额 → 测试环境验证

---

### ⚙️ 配置管理

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [email_notification_configuration.md](./email_notification_configuration.md) | 12 | SMTP配置、客户通知设置 |

**核心功能**: 动态SMTP配置 → 客户邮件设置 → 通知规则管理

---

## 🚀 快速开始

### 场景1: 提交日志进行威胁检测

```bash
# 1. 提交攻击日志
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,..."

# 2. 等待流处理 (约2-4分钟)

# 3. 查询威胁趋势
curl -X GET "http://localhost:8081/api/v1/assessment/trends?customerId=customer_a&..."
```

### 场景2: 手动执行威胁评估

```bash
# 评估威胁
curl -X POST http://localhost:8081/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer_a",
    "attackMac": "04:42:1a:8e:e3:65",
    "attackCount": 150,
    "uniqueIps": 5,
    "uniquePorts": 3,
    "uniqueDevices": 2
  }'
```

### 场景3: 查询和管理告警

```bash
# 查询CRITICAL级别告警
curl -X GET "http://localhost:8082/api/v1/alerts?severity=CRITICAL&status=OPEN"

# 解决告警
curl -X POST http://localhost:8082/api/v1/alerts/12345/resolve \
  -H "Content-Type: application/json" \
  -d '{"resolution": "已隔离攻击源", "resolvedBy": "admin"}'
```

---

## � API端点总览

### 当前状态

**总端点数**: 99
**服务数量**: 5
**已完成文档**: 17 (100%)

| 服务 | 端口 | 总端点数 | 已完成文档 | 主要功能 |
|------|------|---------|-----------|----------|
| **API Gateway** | 8888 | 5 | ✅ | 统一API入口、路由转发、CORS、限流、熔断 |
| **Customer Management** | 8084 | 26 | ✅ | 客户管理、设备绑定、通知配置 |
| **Data Ingestion** | 8080 | 6 | ✅ | 日志摄取、批量处理、统计监控 |
| **Threat Assessment** | 8083 | 46 | ✅ | 威胁评估、查询分析、趋势预测、端口权重配置、多租户权重管理 |
| **Alert Management** | 8082 | 16 | ✅ | 告警管理、通知发送、升级管理 |
| **Integration Test** | - | 2 | ✅ | 系统集成测试支持 |

## � 文档补充计划

**当前状态**: 部分API文档为精简版,完整版补充工作正在进行中。

**详细信息**:
- 📋 **[API文档补充计划](./API_DOCUMENTATION_ENHANCEMENT_PLAN.md)** - 完整补充计划和标准化规范
- 📊 **[补充工作总结](./API_ENHANCEMENT_SUMMARY.md)** - 当前进度和下一步行动

**补充进度**:
| 状态 | 数量 | 说明 |
|------|------|------|
| ✅ 详细版 | 4 | `email_notification_configuration.md`, `data_ingestion_api.md`, `customer_port_weights_api.md`, `api_gateway_current.md` |
| ⏳ 待补充 | 0 | 所有文档已完成补充 |
| ✅ 概述版 | 2 | 无需补充 |

**参考标准**: 所有补充文档将参考 `email_notification_configuration.md` 的详细格式,包含:
- ✅ 完整的curl和Java示例
- ✅ 真实使用场景 (至少3个)
- ✅ 最佳实践和性能优化建议
- ✅ 故障排查指南

---

## �🔗 相关文档

- **[系统架构](../design/new_system_architecture_spec.md)** - 了解整体架构
- **[蜜罐评分算法](../design/honeypot_based_threat_scoring.md)** - 理解威胁评分机制
- **[数据结构](../design/data_structures.md)** - Kafka消息和数据库表结构
- **[调试指南](../design/debugging_and_testing_guide.md)** - API调试方法

---

**最后更新**: 2025-10-31  
**下一步**: API文档体系已完善，所有新增功能都有完整文档覆盖 🎉
