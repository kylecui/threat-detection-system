# 告警生命周期API文档

**服务**: Alert Management Service  
**服务端口**: 8082  
**基础路径**: `/api/v1/alerts`  
**版本**: v1.0  
**最后更新**: 2025-10-16

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [生命周期状态机](#3-生命周期状态机)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
   - 6.1 [更新告警状态](#61-更新告警状态)
   - 6.2 [解决告警](#62-解决告警)
   - 6.3 [关闭告警](#63-关闭告警)
   - 6.4 [重新打开告警](#64-重新打开告警)
   - 6.5 [升级告警](#65-升级告警)
   - 6.6 [分配告警](#66-分配告警)
   - 6.7 [归档告警](#67-归档告警)
   - 6.8 [批量状态更新](#68-批量状态更新)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

告警生命周期API负责管理告警从创建到归档的整个生命周期,包括状态转换、责任人分配、解决方案记录和历史追踪。

### 核心特性

- ✅ **状态管理**: 6种状态流转 (OPEN → IN_PROGRESS → RESOLVED → CLOSED)
- ✅ **责任追踪**: 记录分配人、处理人、解决人
- ✅ **审计日志**: 完整的状态变更历史
- ✅ **并发控制**: 乐观锁防止并发冲突
- ✅ **批量操作**: 支持批量状态更新
- ✅ **自动归档**: 定期归档历史告警

### 工作流程

```
┌─────────┐     分配     ┌──────────────┐    解决     ┌──────────┐    关闭    ┌────────┐
│  OPEN   │ ─────────→  │ IN_PROGRESS  │ ─────────→ │ RESOLVED │ ────────→ │ CLOSED │
│ (新建)  │              │   (处理中)    │            │  (已解决) │           │(已关闭)│
└─────────┘              └──────────────┘            └──────────┘           └────────┘
     │                          │                          │                      │
     │                          │                          │                      │
     └──────────────────────────┴──────────────────────────┴──────────────────────┘
                                       升级 ↓
                                   ┌────────────┐
                                   │ ESCALATED  │
                                   │  (已升级)   │
                                   └────────────┘
                                         ↓ 30天后
                                   ┌────────────┐
                                   │  ARCHIVED  │
                                   │  (已归档)   │
                                   └────────────┘
```

### 技术栈

- **框架**: Spring Boot 3.1.5
- **数据库**: PostgreSQL 15+
- **API风格**: RESTful
- **数据格式**: JSON
- **认证**: JWT Token (可选)

---

## 2. 核心功能

### 2.1 状态管理

- **状态转换**: OPEN → IN_PROGRESS → RESOLVED → CLOSED
- **状态验证**: 防止非法状态转换
- **状态回滚**: 支持重新打开已解决的告警

### 2.2 责任分配

- **手动分配**: 分配给指定分析师或团队
- **自动分配**: 基于规则的自动分配
- **负载均衡**: 平衡分析师工作量

### 2.3 解决方案记录

- **解决描述**: 记录处理措施和结果
- **解决人**: 记录谁解决了告警
- **解决时间**: 自动记录解决时间戳

### 2.4 审计追踪

- **状态变更历史**: 记录每次状态变更
- **操作人追踪**: 记录谁执行了操作
- **时间戳**: 精确到毫秒的时间记录

### 2.5 自动归档

- **定期归档**: 归档超过N天的已解决告警
- **手动归档**: 支持手动归档指定告警
- **归档恢复**: 支持从归档恢复告警

---

## 3. 生命周期状态机

### 状态定义

| 状态 | 英文 | 说明 | 可转换到 |
|------|------|------|---------|
| 新建 | OPEN | 新创建的告警,待处理 | IN_PROGRESS, CLOSED |
| 处理中 | IN_PROGRESS | 分析师正在处理 | RESOLVED, ESCALATED, OPEN |
| 已解决 | RESOLVED | 威胁已处理完毕 | CLOSED, OPEN (重新打开) |
| 已关闭 | CLOSED | 告警已关闭,不再处理 | OPEN (重新打开) |
| 已升级 | ESCALATED | 升级到更高级别团队 | RESOLVED, CLOSED |
| 已归档 | ARCHIVED | 已归档到历史库 | - (不可转换) |

### 状态转换规则

```java
// 允许的状态转换
OPEN → IN_PROGRESS  // 开始处理
OPEN → CLOSED       // 直接关闭 (如误报)

IN_PROGRESS → RESOLVED   // 解决完成
IN_PROGRESS → ESCALATED  // 升级处理
IN_PROGRESS → OPEN       // 退回重新评估

RESOLVED → CLOSED  // 确认关闭
RESOLVED → OPEN    // 重新打开 (如问题复发)

ESCALATED → RESOLVED  // 升级后解决
ESCALATED → CLOSED    // 升级后关闭

CLOSED → ARCHIVED  // 归档 (自动,30天后)
```

---

## 4. 数据模型

### 数据库表结构

```sql
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,  -- CRITICAL/HIGH/MEDIUM/LOW/INFO
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- 告警状态
    
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45) NOT NULL,
    threat_score DECIMAL(10,2),
    attack_count INTEGER,
    
    customer_id VARCHAR(50) NOT NULL,
    assessment_id VARCHAR(100),
    source VARCHAR(50),
    
    -- 生命周期字段
    assigned_to VARCHAR(255),        -- 责任人邮箱
    resolved_by VARCHAR(255),        -- 解决人邮箱
    resolution TEXT,                 -- 解决方案描述
    resolved_at TIMESTAMP,           -- 解决时间
    closed_at TIMESTAMP,             -- 关闭时间
    archived_at TIMESTAMP,           -- 归档时间
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    
    -- 索引
    INDEX idx_alerts_status (status),
    INDEX idx_alerts_assigned_to (assigned_to),
    INDEX idx_alerts_lifecycle (customer_id, status, updated_at),
    CONSTRAINT alerts_customer_id_fk FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- 状态变更历史表
CREATE TABLE alert_status_history (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(255),
    change_reason TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_alert FOREIGN KEY (alert_id) REFERENCES alerts(id)
);
```

### Java DTO

```java
/**
 * 告警实体 (扩展生命周期字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private Long id;
    private String title;
    private String description;
    private AlertSeverity severity;
    private AlertStatus status;  // 核心状态字段
    
    private String attackMac;
    private String attackIp;
    private Double threatScore;
    private Integer attackCount;
    
    private String customerId;
    private String assessmentId;
    private String source;
    
    // 生命周期字段
    private String assignedTo;      // 当前责任人
    private String resolvedBy;      // 解决人
    private String resolution;      // 解决方案
    private Instant resolvedAt;     // 解决时间
    private Instant closedAt;       // 关闭时间
    private Instant archivedAt;     // 归档时间
    
    private Instant createdAt;
    private Instant updatedAt;
}

/**
 * 告警状态枚举
 */
public enum AlertStatus {
    OPEN,        // 新建
    IN_PROGRESS, // 处理中
    RESOLVED,    // 已解决
    CLOSED,      // 已关闭
    ESCALATED,   // 已升级
    ARCHIVED     // 已归档
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 参数 |
|------|------|------|------|
| `PUT` | `/api/v1/alerts/{id}/status` | 更新告警状态 | status (query) |
| `POST` | `/api/v1/alerts/{id}/resolve` | 解决告警 | resolution, resolvedBy (body) |
| `POST` | `/api/v1/alerts/{id}/close` | 关闭告警 | closeReason (body) |
| `POST` | `/api/v1/alerts/{id}/reopen` | 重新打开告警 | reopenReason (body) |
| `POST` | `/api/v1/alerts/{id}/escalate` | 升级告警 | escalateTo, reason (body) |
| `POST` | `/api/v1/alerts/{id}/assign` | 分配告警 | assignedTo (body) |
| `POST` | `/api/v1/alerts/archive` | 归档旧告警 | daysOld (query) |
| `POST` | `/api/v1/alerts/batch-status` | 批量更新状态 | alertIds, status (body) |

---

## 6. API详细文档

### 6.1 更新告警状态

**描述**: 更新告警的状态,支持状态转换验证。

**端点**: `PUT /api/v1/alerts/{id}/status`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**查询参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `status` | String | ✅ | 目标状态 (OPEN/IN_PROGRESS/RESOLVED/CLOSED/ESCALATED) |

#### 请求示例 (curl)

```bash
# 开始处理告警
curl -X PUT "http://localhost:8082/api/v1/alerts/12345/status?status=IN_PROGRESS"

# 解决告警
curl -X PUT "http://localhost:8082/api/v1/alerts/12345/status?status=RESOLVED"

# 关闭告警
curl -X PUT "http://localhost:8082/api/v1/alerts/12345/status?status=CLOSED"
```

#### 请求示例 (Java)

```java
/**
 * 更新告警状态
 */
public Alert updateAlertStatus(Long alertId, AlertStatus newStatus) {
    String url = BASE_URL + "/" + alertId + "/status?status=" + newStatus;
    
    HttpEntity<Void> request = new HttpEntity<>(null);
    
    ResponseEntity<Alert> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        Alert.class
    );
    
    Alert updatedAlert = response.getBody();
    System.out.println("Status updated: " + updatedAlert.getStatus());
    
    return updatedAlert;
}
```

#### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到横向移动攻击",
  "status": "IN_PROGRESS",
  "assignedTo": "security-analyst@company.com",
  "updatedAt": "2025-10-16T04:15:00Z"
}
```

#### 状态转换验证

| 当前状态 | 允许转换到 | 说明 |
|---------|-----------|------|
| OPEN | IN_PROGRESS, CLOSED | 开始处理或直接关闭 |
| IN_PROGRESS | RESOLVED, ESCALATED, OPEN | 解决、升级或退回 |
| RESOLVED | CLOSED, OPEN | 关闭或重新打开 |
| CLOSED | OPEN | 重新打开 |
| ESCALATED | RESOLVED, CLOSED | 解决或关闭 |

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 状态更新成功 |
| 400 | 非法状态转换 |
| 404 | 告警不存在 |
| 409 | 并发冲突 |

---

### 6.2 解决告警

**描述**: 标记告警为已解决,记录解决方案和解决人。

**端点**: `POST /api/v1/alerts/{id}/resolve`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**请求体**: `Content-Type: application/json`

```json
{
  "resolution": "已隔离攻击源主机并清除恶意软件,加固防火墙规则",
  "resolvedBy": "security-analyst@company.com"
}
```

#### 请求参数

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `resolution` | String | ✅ | 解决方案描述 (10-2000字符) |
| `resolvedBy` | String | ✅ | 解决人邮箱 |

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/resolve \
  -H "Content-Type: application/json" \
  -d '{
    "resolution": "已隔离攻击源主机并清除恶意软件,加固防火墙规则",
    "resolvedBy": "security-analyst@company.com"
  }'
```

#### 请求示例 (Java)

```java
/**
 * 解决告警
 */
public Alert resolveAlert(Long alertId, String resolution, String resolvedBy) {
    String url = BASE_URL + "/" + alertId + "/resolve";
    
    ResolveAlertRequest request = new ResolveAlertRequest();
    request.setResolution(resolution);
    request.setResolvedBy(resolvedBy);
    
    HttpEntity<ResolveAlertRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<Alert> response = restTemplate.postForEntity(
        url,
        httpRequest,
        Alert.class
    );
    
    Alert resolved = response.getBody();
    System.out.println("Alert resolved at: " + resolved.getResolvedAt());
    
    return resolved;
}

@Data
public static class ResolveAlertRequest {
    private String resolution;
    private String resolvedBy;
}
```

#### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到横向移动攻击",
  "status": "RESOLVED",
  "resolution": "已隔离攻击源主机并清除恶意软件,加固防火墙规则",
  "resolvedBy": "security-analyst@company.com",
  "resolvedAt": "2025-10-16T05:30:00Z",
  "updatedAt": "2025-10-16T05:30:00Z"
}
```

---

### 6.3 关闭告警

**描述**: 关闭告警,记录关闭原因。

**端点**: `POST /api/v1/alerts/{id}/close`

**请求体**:

```json
{
  "closeReason": "威胁已彻底消除,防护措施已加固"
}
```

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/close \
  -H "Content-Type: application/json" \
  -d '{
    "closeReason": "威胁已彻底消除,防护措施已加固"
  }'
```

---

### 6.4 重新打开告警

**描述**: 重新打开已解决或已关闭的告警。

**端点**: `POST /api/v1/alerts/{id}/reopen`

**请求体**:

```json
{
  "reopenReason": "威胁再次出现,需要重新处理"
}
```

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/reopen \
  -H "Content-Type: application/json" \
  -d '{
    "reopenReason": "威胁再次出现,需要重新处理"
  }'
```

---

### 6.5 升级告警

**描述**: 升级告警到更高级别的团队处理。

**端点**: `POST /api/v1/alerts/{id}/escalate`

**请求体**:

```json
{
  "escalateTo": "senior-security-team@company.com",
  "reason": "威胁等级超出当前团队处理能力,需要高级安全团队介入"
}
```

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/escalate \
  -H "Content-Type: application/json" \
  -d '{
    "escalateTo": "senior-security-team@company.com",
    "reason": "威胁等级超出当前团队处理能力"
  }'
```

---

### 6.6 分配告警

**描述**: 将告警分配给指定的分析师或团队。

**端点**: `POST /api/v1/alerts/{id}/assign`

**请求体**:

```json
{
  "assignedTo": "security-analyst@company.com"
}
```

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/assign \
  -H "Content-Type: application/json" \
  -d '{
    "assignedTo": "security-analyst@company.com"
  }'
```

#### 请求示例 (Java)

```java
/**
 * 分配告警
 */
public Alert assignAlert(Long alertId, String assignedTo) {
    String url = BASE_URL + "/" + alertId + "/assign";
    
    AssignAlertRequest request = new AssignAlertRequest();
    request.setAssignedTo(assignedTo);
    
    HttpEntity<AssignAlertRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<Alert> response = restTemplate.postForEntity(
        url,
        httpRequest,
        Alert.class
    );
    
    return response.getBody();
}

@Data
public static class AssignAlertRequest {
    private String assignedTo;
}
```

---

### 6.7 归档告警

**描述**: 归档超过指定天数的已解决告警。

**端点**: `POST /api/v1/alerts/archive`

**查询参数**:

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `daysOld` | Integer | ❌ | 30 | 归档超过N天的告警 |

#### 请求示例 (curl)

```bash
# 归档30天前的已解决告警
curl -X POST "http://localhost:8082/api/v1/alerts/archive?daysOld=30"

# 归档90天前的告警
curl -X POST "http://localhost:8082/api/v1/alerts/archive?daysOld=90"
```

#### 请求示例 (Java)

```java
/**
 * 归档旧告警
 */
public int archiveOldAlerts(int daysOld) {
    String url = BASE_URL + "/archive?daysOld=" + daysOld;
    
    ResponseEntity<ArchiveResponse> response = restTemplate.postForEntity(
        url,
        null,
        ArchiveResponse.class
    );
    
    int archived = response.getBody().getArchivedCount();
    System.out.println("Archived " + archived + " alerts");
    
    return archived;
}

@Data
public static class ArchiveResponse {
    private int archivedCount;
}
```

#### 响应示例

**HTTP 200 OK**

```json
{
  "archivedCount": 125
}
```

---

### 6.8 批量状态更新

**描述**: 批量更新多个告警的状态。

**端点**: `POST /api/v1/alerts/batch-status`

**请求体**:

```json
{
  "alertIds": [12345, 12346, 12347],
  "status": "CLOSED"
}
```

#### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/batch-status \
  -H "Content-Type: application/json" \
  -d '{
    "alertIds": [12345, 12346, 12347],
    "status": "CLOSED"
  }'
```

#### 响应示例

**HTTP 200 OK**

```json
{
  "successCount": 3,
  "failedCount": 0,
  "updatedAlerts": [12345, 12346, 12347],
  "failedAlerts": []
}
```

---

## 7. 使用场景

### 场景1: 安全分析师处理告警流程

```java
/**
 * 完整的告警处理流程
 */
public class SecurityAnalystWorkflow {
    
    private AlertLifecycleClient lifecycleClient;
    
    /**
     * 处理单个告警的完整流程
     */
    public void processAlert(Long alertId) {
        System.out.println("===== 开始处理告警 " + alertId + " =====");
        
        // 1. 分配给自己
        lifecycleClient.assignAlert(alertId, "analyst@company.com");
        System.out.println("✅ 已分配给自己");
        
        // 2. 更新状态为处理中
        lifecycleClient.updateStatus(alertId, AlertStatus.IN_PROGRESS);
        System.out.println("✅ 状态更新为 IN_PROGRESS");
        
        // 3. 分析威胁
        ThreatAnalysisResult analysis = analyzeThreat(alertId);
        System.out.println("✅ 威胁分析完成: " + analysis.getThreatType());
        
        // 4. 如果需要升级
        if (analysis.requiresEscalation()) {
            lifecycleClient.escalateAlert(
                alertId,
                "senior-team@company.com",
                "威胁等级过高,需要高级团队介入"
            );
            System.out.println("⬆️ 已升级到高级团队");
            return;
        }
        
        // 5. 执行处置措施
        String resolutionActions = executeRemediationActions(analysis);
        System.out.println("✅ 处置措施已执行: " + resolutionActions);
        
        // 6. 解决告警
        lifecycleClient.resolveAlert(
            alertId,
            resolutionActions,
            "analyst@company.com"
        );
        System.out.println("✅ 告警已解决");
        
        // 7. 验证威胁消除
        Thread.sleep(3600000);  // 等待1小时
        
        if (verifyThreatEliminated(alertId)) {
            // 8. 关闭告警
            lifecycleClient.closeAlert(alertId, "威胁已彻底消除,防护措施已加固");
            System.out.println("✅ 告警已关闭");
        } else {
            // 9. 重新打开
            lifecycleClient.reopenAlert(alertId, "威胁未彻底消除,需要进一步处理");
            System.out.println("⚠️ 告警已重新打开");
        }
        
        System.out.println("===== 告警处理完成 =====");
    }
}
```

---

### 场景2: 自动归档任务

```java
/**
 * 定时归档任务
 */
@Scheduled(cron = "0 0 2 * * *")  // 每天凌晨2点执行
public void scheduledArchiveTask() {
    System.out.println("开始自动归档任务...");
    
    // 归档30天前的已解决告警
    int archived = lifecycleClient.archiveOldAlerts(30);
    
    System.out.println("归档完成: " + archived + " 条告警已归档");
}
```

---

## 8. Java客户端完整示例

```java
package com.threatdetection.client;

/**
 * 告警生命周期客户端 - 完整实现
 */
public class AlertLifecycleClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate;
    
    public AlertLifecycleClient() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 更新告警状态
     */
    public Alert updateStatus(Long alertId, AlertStatus status) {
        String url = BASE_URL + "/" + alertId + "/status?status=" + status;
        
        return restTemplate.exchange(
            url,
            HttpMethod.PUT,
            null,
            Alert.class
        ).getBody();
    }
    
    /**
     * 解决告警
     */
    public Alert resolveAlert(Long alertId, String resolution, String resolvedBy) {
        String url = BASE_URL + "/" + alertId + "/resolve";
        
        ResolveAlertRequest request = new ResolveAlertRequest();
        request.setResolution(resolution);
        request.setResolvedBy(resolvedBy);
        
        return restTemplate.postForEntity(
            url,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 关闭告警
     */
    public Alert closeAlert(Long alertId, String closeReason) {
        String url = BASE_URL + "/" + alertId + "/close";
        
        CloseAlertRequest request = new CloseAlertRequest();
        request.setCloseReason(closeReason);
        
        return restTemplate.postForEntity(
            url,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 重新打开告警
     */
    public Alert reopenAlert(Long alertId, String reopenReason) {
        String url = BASE_URL + "/" + alertId + "/reopen";
        
        ReopenAlertRequest request = new ReopenAlertRequest();
        request.setReopenReason(reopenReason);
        
        return restTemplate.postForEntity(
            url,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 升级告警
     */
    public Alert escalateAlert(Long alertId, String escalateTo, String reason) {
        String url = BASE_URL + "/" + alertId + "/escalate";
        
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setEscalateTo(escalateTo);
        request.setReason(reason);
        
        return restTemplate.postForEntity(
            url,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 分配告警
     */
    public Alert assignAlert(Long alertId, String assignedTo) {
        String url = BASE_URL + "/" + alertId + "/assign";
        
        AssignAlertRequest request = new AssignAlertRequest();
        request.setAssignedTo(assignedTo);
        
        return restTemplate.postForEntity(
            url,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 归档旧告警
     */
    public int archiveOldAlerts(int daysOld) {
        String url = BASE_URL + "/archive?daysOld=" + daysOld;
        
        ResponseEntity<ArchiveResponse> response = restTemplate.postForEntity(
            url,
            null,
            ArchiveResponse.class
        );
        
        return response.getBody().getArchivedCount();
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

1. **总是记录状态变更原因**
2. **在解决告警前验证威胁已消除**
3. **定期归档历史告警释放存储空间**
4. **使用批量操作提高效率**

### ❌ 反模式

1. **跳过状态直接关闭** (应该: OPEN → IN_PROGRESS → RESOLVED → CLOSED)
2. **不记录解决方案** (必须详细记录处理措施)
3. **频繁重新打开已关闭告警** (应重新评估是否为新威胁)

---

## 10. 故障排查

### 问题1: 非法状态转换 (HTTP 400)

**原因**: 尝试非法的状态转换 (如 OPEN → ARCHIVED)

**解决方案**: 遵循状态机规则,使用允许的转换路径

---

### 问题2: 并发冲突 (HTTP 409)

**原因**: 多个用户同时修改同一告警

**解决方案**: 实现乐观锁或重试机制

---

## 11. 相关文档

- [告警CRUD API](./alert_crud_api.md) - 告警基本CRUD操作
- [邮件通知配置API](./email_notification_configuration.md) - 告警通知配置
- [威胁评估查询API](./threat_assessment_query_api.md) - 威胁评估数据

---

**文档结束**

*最后更新: 2025-10-16*  
*版本: 1.0*  
*维护团队: Security Platform Team*
