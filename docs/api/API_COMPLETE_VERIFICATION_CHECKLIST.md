````markdown
# 📋 后端API完整梳理检查清单

**日期**: 2025-10-21  
**目的**: 完整梳理所有58个API端点, 确保没有遗漏  
**状态**: ⏳ 进行中

---

## 🎯 任务说明

在启动Phase 5集成测试前，必须完整梳理和验证所有后端API：

1. ✅ **确认所有58个端点** - 没有遗漏
2. ✅ **确认请求/响应格式** - JSON结构正确
3. ✅ **确认错误处理** - 错误场景已覆盖
4. ✅ **确认依赖关系** - API调用顺序清晰
5. ✅ **确认多租户隔离** - customerId正确处理
6. ✅ **确认数据一致性** - 数据库和API同步

---

## 📊 API清单总表

### 统计
- **总计**: 58个API端点
- **已验证**: 0个 (⏳ 待完成)
- **未验证**: 58个
- **有问题**: 0个

---

## 🔵 Service 1: Customer-Management (Port 8084)

### 客户管理 (Customer CRUD) - 6个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 1 | POST | /api/v1/customers | customer_id, name, email, subscription_tier, max_devices | customer_id, name, email, created_at | 409 Conflict (重复客户), 400 Bad Request | 无 | ⏳ |
| 2 | GET | /api/v1/customers | - | customers[] | 200 OK | 无 | ⏳ |
| 3 | GET | /api/v1/customers/{customerId} | - | customer详情 | 404 Not Found | 无 | ⏳ |
| 4 | PUT | /api/v1/customers/{customerId} | name, email, phone | customer详情 | 404 Not Found | 需存在 | ⏳ |
| 5 | PATCH | /api/v1/customers/{customerId} | name, email, phone (可选) | customer详情 | 404 Not Found | 需存在 | ⏳ |
| 6 | DELETE | /api/v1/customers/{customerId} | - | 200 OK | 404 Not Found | 需存在 | ⏳ |

**验证清单**:
- [ ] 创建客户返回201 Created
- [ ] 重复创建返回409 Conflict
- [ ] 字段验证 (email格式, max_devices > 0)
- [ ] 删除后无法查询 (404)
- [ ] 更新后数据库一致
- [ ] 多租户隔离 (customer_a的数据对customer_b不可见)

---

### 设备管理 (Device Binding) - 9个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 7 | POST | /api/v1/customers/{customerId}/devices | dev_serial, description | device详情, is_active | 201 Created | 需customer存在 | ⏳ |
| 8 | GET | /api/v1/customers/{customerId}/devices | - | devices[] | 200 OK | 需customer存在 | ⏳ |
| 9 | GET | /api/v1/customers/{customerId}/devices/{devSerial} | - | device详情 | 404 Not Found | 需device存在 | ⏳ |
| 10 | DELETE | /api/v1/customers/{customerId}/devices/{devSerial} | - | 200 OK | 404 Not Found | 需device存在 | ⏳ |
| 11 | POST | /api/v1/customers/{customerId}/devices/batch | devices: [{dev_serial, description}] | BatchOperationResponse | 207 Multi-Status | 需customer存在 | ⏳ |
| 12 | DELETE | /api/v1/customers/{customerId}/devices/batch | dev_serials: [] | BatchOperationResponse | 207 Multi-Status | 需device存在 | ⏳ |
| 13 | GET | /api/v1/customers/{customerId}/devices/quota | - | current_devices, max_devices, available_devices | 200 OK | 需customer存在 | ⏳ |
| 14 | PATCH | /api/v1/customers/{customerId}/devices/{devSerial} | description (可选) | device详情 | 404 Not Found | 需device存在 | ⏳ |
| 15 | PUT | /api/v1/customers/{customerId}/devices/{devSerial} | description | device详情 | 404 Not Found | 需device存在 | ⏳ |

**验证清单**:
- [ ] 绑定设备返回201 Created
- [ ] 重复绑定同一设备返回409 Conflict
- [ ] 超过max_devices限制返回400 Bad Request
- [ ] 设备配额计算正确
- [ ] 批量操作返回207 Multi-Status
- [ ] 批量操作中部分失败的处理
- [ ] 解绑后无法查询设备

---

### 通知配置 (Notification Config) - 8个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 16 | PUT | /api/v1/customers/{customerId}/notification-config | email_enabled, email_recipients[], min_severity_level, slack_enabled等 | 配置详情, created_at | 201 Created | 需customer存在 | ⏳ |
| 17 | GET | /api/v1/customers/{customerId}/notification-config | - | 配置详情 | 200 OK / 404 | 需customer存在 | ⏳ |
| 18 | PATCH | /api/v1/customers/{customerId}/notification-config | 部分字段 | 配置详情 | 200 OK / 404 | 需customer存在 | ⏳ |
| 19 | DELETE | /api/v1/customers/{customerId}/notification-config | - | 200 OK | 404 Not Found | 需配置存在 | ⏳ |
| 20 | POST | /api/v1/customers/{customerId}/notification-config/test | - | 200 OK (发送测试邮件) | 502 Bad Gateway (SMTP错误) | 需配置存在 | ⏳ |
| 21 | GET | /api/v1/customers/{customerId}/notification-config/history | - | 配置历史记录 | 200 OK | 需customer存在 | ⏳ |
| 22 | POST | /api/v1/customers/{customerId}/notification-config/validate | email_enabled, email_recipients等 | validation_result | 200 OK | 无 | ⏳ |
| 23 | PUT | /api/v1/customers/{customerId}/notification-config/rules | rules配置 | 规则详情 | 200 OK / 400 Bad Request | 需customer存在 | ⏳ |

**验证清单**:
- [ ] 创建配置返回201 Created
- [ ] snake_case字段正确处理 (email_enabled, not emailEnabled)
- [ ] PATCH部分更新正确
- [ ] 测试邮件发送正确
- [ ] 验证端点检查格式正确
- [ ] 数据库中email_recipients正确存储 (数组类型)
- [ ] 多个客户的配置相互隔离

---

## 🟢 Service 2: Alert-Management (Port 8082)

### 告警CRUD (Alert CRUD) - 3个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 24 | POST | /api/v1/alerts | title, description, severity, status | alert_id, created_at | 201 Created | 无 | ⏳ |
| 25 | GET | /api/v1/alerts | page, size, severity (可选) | alerts[], total_count | 200 OK | 无 | ⏳ |
| 26 | GET | /api/v1/alerts/{alertId} | - | alert详情 | 404 Not Found | 需alert存在 | ⏳ |

---

### 告警生命周期 (Alert Lifecycle) - 4个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 27 | POST | /api/v1/alerts/{alertId}/resolve | resolution, resolvedBy | alert详情 (status=RESOLVED) | 404 Not Found | 需alert存在 | ⏳ |
| 28 | POST | /api/v1/alerts/{alertId}/reassign | assignedTo, reason | alert详情 | 404 Not Found | 需alert存在 | ⏳ |
| 29 | POST | /api/v1/alerts/{alertId}/acknowledge | acknowledgedBy, notes | alert详情 (status=ACKNOWLEDGED) | 404 Not Found | 需alert存在 | ⏳ |
| 30 | POST | /api/v1/alerts/{alertId}/archive | archiveReason | 200 OK | 404 Not Found | 需alert存在 | ⏳ |

---

### 告警升级 (Alert Escalation) - 2个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 31 | POST | /api/v1/alerts/{alertId}/escalate | escalationLevel, reason | alert详情 (escalated=true) | 404 Not Found | 需alert存在 | ⏳ |
| 32 | POST | /api/v1/alerts/{alertId}/cancel-escalation | reason | alert详情 (escalated=false) | 404 Not Found | 需alert存在 | ⏳ |

---

### 告警分析 (Alert Analytics) - 3个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 33 | GET | /api/v1/alerts/analytics/stats | - | alert_count_by_severity, resolved_rate等 | 200 OK | 无 | ⏳ |
| 34 | GET | /api/v1/alerts/analytics/trends | days (可选) | time_series数据 | 200 OK | 无 | ⏳ |
| 35 | GET | /api/v1/alerts/analytics/notifications | - | notification_stats | 200 OK | 无 | ⏳ |

---

### 告警通知 (Alert Notification) - 1个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 36 | POST | /api/v1/alerts/{alertId}/notify | channel (email/sms/slack) | notification_id | 400 Bad Request (无效channel) | 需alert存在, 需配置 | ⏳ |

---

### 告警维护 (Alert Maintenance) - 1个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 37 | DELETE | /api/v1/alerts/archive-old | daysOld (默认30) | deleted_count | 200 OK | 无 | ⏳ |

---

### Alert-Management 只读通知配置 (ReadOnly) - 2个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 38 | GET | /api/notification-config/customer | - | customer_configs[] | 200 OK | 无 | ⏳ |
| 39 | GET | /api/notification-config/customer/{customerId} | - | 配置详情 | 200 OK / 404 | 无 | ⏳ |

**验证清单**:
- [ ] Alert-Management只能读取配置, 不能修改 (POST/PUT/DELETE返回403)
- [ ] 告警状态转换逻辑正确 (OPEN → ACKNOWLEDGED → RESOLVED)
- [ ] 告警升级标记正确
- [ ] 分析统计计算正确
- [ ] 多租户隔离 (只看自己的告警)

---

## 🟡 Service 3: Data-Ingestion (Port 8080)

### 日志摄取 (Log Ingestion) - 6个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 40 | POST | /api/v1/logs/ingest | syslog格式字符串 | 202 Accepted | 400 Bad Request (格式错误) | Kafka | ⏳ |
| 41 | POST | /api/v1/logs/batch-ingest | 多行syslog字符串 | 202 Accepted, processed_count | 207 Multi-Status (部分失败) | Kafka | ⏳ |
| 42 | GET | /api/v1/logs/stats | - | ingestion_count, success_rate, error_count | 200 OK | 无 | ⏳ |
| 43 | GET | /api/v1/logs/errors | - | recent_errors[] | 200 OK | 无 | ⏳ |
| 44 | GET | /api/v1/logs/health | - | status, kafka_status, db_status | 200 OK | 无 | ⏳ |
| 45 | POST | /api/v1/logs/test-connection | - | connection_status | 200 OK | 无 | ⏳ |

**验证清单**:
- [ ] 日志发送返回202 Accepted (异步处理)
- [ ] 日志格式验证正确
- [ ] 批量上传部分失败正确处理
- [ ] Kafka连接正常
- [ ] 错误日志记录完整
- [ ] 统计数据准确

---

## 🟠 Service 4: Threat-Assessment (Port 8083)

### 威胁评估执行 (Threat Evaluation) - 2个端点

| # | 方法 | 路径 | 请求体 | 响应体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 46 | POST | /api/v1/assessment/evaluate | customerId, attackMac, attackCount, uniqueIps, uniquePorts, uniqueDevices | threat_score, threat_level, recommendations | 400 Bad Request (字段缺失) | 无 | ⏳ |
| 47 | POST | /api/v1/assessment/mitigate | threat_id, mitigation_action | mitigation_result | 404 Not Found | 需threat存在 | ⏳ |

---

### 威胁查询 (Threat Query) - 3个端点

| # | 方法 | 路径 | 请求体 | 响体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 48 | GET | /api/v1/assessment/trends | customerId, days (可选) | trends[] | 200 OK | 无 | ⏳ |
| 49 | GET | /api/v1/assessment/details/{threatId} | - | threat详情 | 404 Not Found | 需threat存在 | ⏳ |
| 50 | GET | /api/v1/assessment/health | - | status, db_connection, kafka_status | 200 OK | 无 | ⏳ |

**验证清单**:
- [ ] 威胁评分计算公式正确
- [ ] 权重计算正确 (时间/IP/端口/设备)
- [ ] 威胁等级分类正确 (5级)
- [ ] 建议缓解措施准确
- [ ] 多租户隔离正确

---

## 🔴 Service 5: API-Gateway (Port 8888)

### 网关管理 (Gateway Management) - 5个端点

| # | 方法 | 路径 | 请求体 | 响体 | 错误处理 | 依赖 | 验证状态 |
|---|------|------|--------|--------|---------|------|----------|
| 51 | GET | /actuator/health | - | status, components | 200 OK | 无 | ⏳ |
| 52 | GET | /api/v1/gateway/routes | - | routes[] | 200 OK | 无 | ⏳ |
| 53 | GET | /api/v1/gateway/stats | - | request_count, response_time | 200 OK | 无 | ⏳ |
| 54 | POST | /api/v1/gateway/rate-limit | client_id, limit, period | 200 OK | 400 Bad Request | 无 | ⏳ |
| 55 | GET | /api/v1/gateway/metrics | - | endpoint_metrics[] | 200 OK | 无 | ⏳ |

**验证清单**:
- [ ] 所有路由规则转发正确
- [ ] CORS配置正确
- [ ] 限流保护生效
- [ ] 认证/授权正确
- [ ] 熔断降级正确

---

## 🔵 Cross-Service APIs - 共3个端点

| # | 方法 | 路径 | 说明 | 验证状态 |
|---|------|------|------|----------|
| 56 | GET | /api/v1/dashboard/overview | 系统总体概览 (组合多个服务) | ⏳ |
| 57 | GET | /api/v1/system/health | 系统整体健康状态 (所有服务) | ⏳ |
| 58 | GET | /api/v1/system/metrics | 系统整体指标 (汇聚所有服务) | ⏳ |

---

## ✅ 验证流程

### 第1步: 逐个端点验证
```bash
# 对每个API执行以下验证:

1. 正常请求
   curl -X {METHOD} "{URL}" -H "Content-Type: application/json" -d '{...}'
   ✓ 验证HTTP状态码正确
   ✓ 验证响应体格式正确
   ✓ 验证数据库记录已生成

2. 边界情况
   - 空值/null
   - 超长字符串
   - 非法字符
   - 缺失必需字段

3. 错误情况
   - 资源不存在 (404)
   - 冲突 (409)
   - 无效请求 (400)
   - 权限不足 (403)

4. 多租户隔离
   - customer_a的请求无法看到customer_b的数据
   - 每个API都应该隐式或显式过滤customerId
```

### 第2步: 依赖关系验证
```bash
# 验证API调用链
1. 创建客户 (API#1)
2. 绑定设备 (API#7) ← 依赖API#1
3. 配置通知 (API#16) ← 依赖API#1
4. 创建告警 (API#24) ← 依赖API#3?
...
```

### 第3步: 数据一致性验证
```bash
# 通过API操作后验证数据库状态
1. 通过API创建客户
   POST /api/v1/customers
2. 直接查询数据库
   SELECT * FROM customers WHERE customer_id = '...'
3. 验证两者一致
```

### 第4步: 多租户隔离验证
```bash
# 使用两个不同客户进行测试
1. 创建 customer_a 和 customer_b
2. 为 customer_a 创建设备
3. 查询 customer_b 的设备列表
4. 验证结果不包含 customer_a 的设备
```

---

## 📝 发现的问题记录

*此部分在验证过程中更新*

### P0 (严重)
- [ ] 问题编号: 问题描述 (API端点)

### P1 (警告)  
- [ ] 问题编号: 问题描述 (API端点)

### P2 (低)
- [ ] 问题编号: 问题描述 (API端点)

---

## 📊 验证进度

```
[████░░░░░░░░░░░░░░░░] 20% (12/58 APIs verified)

Customer-Management:  ░░░░░░░░░░ (0/23 endpoints)
Alert-Management:    ░░░░░░░░░░ (0/16 endpoints)
Data-Ingestion:      ░░░░░░░░░░ (0/6 endpoints)
Threat-Assessment:   ░░░░░░░░░░ (0/5 endpoints)
API-Gateway:         ░░░░░░░░░░ (0/5 endpoints)
Cross-Service:       ░░░░░░░░░░ (0/3 endpoints)
```

---

## 🎯 下一步

1. **今日** (2025-10-21):
   - [ ] 完成此清单梳理
   - [ ] 确认所有58个端点
   - [ ] 识别依赖关系

2. **明日** (2025-10-22):
   - [ ] 编写测试脚本
   - [ ] 开始逐个验证
   - [ ] 记录发现的问题

3. **后天** (2025-10-23):
   - [ ] 完成所有验证
   - [ ] 汇总问题清单
   - [ ] 制定修复计划

4. **本周末** (2025-10-24):
   - [ ] 执行修复
   - [ ] 二轮验证
   - [ ] 生成最终报告

---

**开始日期**: 2025-10-21  
**目标完成**: 2025-10-24  
**预计工时**: 16-20小时

````
