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

### 🔵 数据摄取服务 (Port 8080)

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [data_ingestion_api.md](./data_ingestion_api.md) | 6 | 日志摄取、批量处理、统计、健康检查 |

**核心功能**: 接收syslog日志 → 解析 → 发布到Kafka

---

### 🟢 威胁评估服务 (Port 8081)

| 文档 | 端点数量 | 说明 |
|------|---------|------|
| [threat_assessment_overview.md](./threat_assessment_overview.md) | - | 系统概述、架构、评分算法 |
| [threat_assessment_evaluation_api.md](./threat_assessment_evaluation_api.md) | 2 | 执行威胁评估、缓解措施 |
| [threat_assessment_query_api.md](./threat_assessment_query_api.md) | 3 | 查询评估详情、趋势分析 |
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

## 📊 API端点总览

| 服务 | 端口 | 总端点数 | 主要功能 |
|------|------|---------|---------|
| **Data Ingestion** | 8080 | 6 | 日志摄取和解析 |
| **Threat Assessment** | 8081 | 5 | 威胁评估和分析 |
| **Alert Management** | 8082 | 16 | 告警管理和通知 |
| **Integration Test** | 8082 | 2 | 集成测试工具 |
| **总计** | - | **29** | - |

---

## 🔗 相关文档

- **[系统架构](../design/new_system_architecture_spec.md)** - 了解整体架构
- **[蜜罐评分算法](../design/honeypot_based_threat_scoring.md)** - 理解威胁评分机制
- **[数据结构](../design/data_structures.md)** - Kafka消息和数据库表结构
- **[调试指南](../design/debugging_and_testing_guide.md)** - API调试方法

---

**最后更新**: 2025-01-16
