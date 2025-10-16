# 告警管理系统概述

**服务名称**: Alert Management Service  
**服务端口**: 8082  
**基础路径**: `http://localhost:8082/api/v1`  
**版本**: 1.0  
**更新日期**: 2025-01-16

---

## 系统架构

```
Kafka (threat-alerts) → Alert Management Service → PostgreSQL (alerts, notifications)
                                ↓
                        邮件通知、升级处理、归档管理
```

## 核心功能

1. **告警生命周期管理**: 创建、查询、状态更新、解决、归档
2. **告警升级机制**: 自动/手动升级、取消升级
3. **多渠道通知**: 邮件、SMS、Slack、Webhook
4. **统计分析**: 告警统计、通知统计、升级统计
5. **集成测试**: 测试工具和状态监控

## API分类

### 告警管理 (16个端点)

| 分类 | 端点数量 | 文档链接 |
|------|---------|---------|
| **CRUD操作** | 4 | [告警CRUD API](./alert_crud_api.md) |
| **生命周期管理** | 4 | [生命周期API](./alert_lifecycle_api.md) |
| **升级管理** | 2 | [升级API](./alert_escalation_api.md) |
| **统计分析** | 3 | [分析API](./alert_analytics_api.md) |
| **通知管理** | 1 | [通知API](./alert_notification_api.md) |
| **维护操作** | 1 | [维护API](./alert_maintenance_api.md) |

### 集成测试 (2个端点)

| 文档 | 端点数量 |
|------|---------|
| [集成测试API](./integration_test_api.md) | 2 |

## 数据模型

### Alert (告警)

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到大规模横向移动",
  "severity": "CRITICAL",
  "status": "OPEN",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "threatScore": 7290.0,
  "customerId": "customer_a",
  "assignedTo": "security-team@company.com",
  "createdAt": "2025-01-15T02:30:00Z"
}
```

### 告警状态流转

```
OPEN → IN_PROGRESS → RESOLVED → ARCHIVED
  ↓
ESCALATED (可选)
```

### 严重等级

| 等级 | 说明 | 响应时间 |
|------|------|---------|
| CRITICAL | 严重威胁 | 立即响应 |
| HIGH | 高危威胁 | 1小时内 |
| MEDIUM | 中等威胁 | 4小时内 |
| LOW | 低危威胁 | 24小时内 |
| INFO | 信息级别 | 无需响应 |

---

**相关文档**: [邮件通知配置](./email_notification_configuration.md) | [威胁评估API](./threat_assessment_overview.md)

---

**文档结束**

*最后更新: 2025-01-16*
