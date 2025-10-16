# 告警升级API文档

**版本**: 1.0  
**服务**: Alert Management Service  
**端口**: 8082  
**基础路径**: `/api/v1/alerts`

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [升级策略引擎](#3-升级策略引擎)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
   - 6.1 [手动升级告警](#61-手动升级告警)
   - 6.2 [取消升级](#62-取消升级)
   - 6.3 [配置升级策略](#63-配置升级策略)
   - 6.4 [查询升级历史](#64-查询升级历史)
   - 6.5 [批量升级](#65-批量升级)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

告警升级系统负责在威胁告警需要更高级别关注或处理时,自动或手动将告警升级到更高的优先级或通知范围。

### 核心特性

- **自动升级**: 基于时间、严重性、影响范围的自动升级规则
- **手动升级**: 安全运营人员可手动升级任何告警
- **升级策略**: 可配置的多级升级策略和通知路径
- **升级历史**: 完整的升级操作审计日志
- **升级取消**: 支持撤销不当的升级操作
- **批量升级**: 支持批量升级相关告警

### 工作流程

```
告警创建 → 自动评估 → 触发升级规则? 
                           ↓ 是
                      执行升级操作 → 通知相关人员
                           ↓
                      记录升级历史
                           ↓
                      持续监控 → 二次升级?
```

### 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| **后端框架** | Spring Boot 3.1.5 | REST API服务 |
| **数据库** | PostgreSQL 15+ | 升级策略和历史存储 |
| **定时任务** | Spring @Scheduled | 自动升级检查 |
| **消息队列** | Kafka | 升级事件发布 |
| **缓存** | Redis | 升级规则缓存 |

---

## 2. 核心功能

### 2.1 升级触发条件

| 触发类型 | 条件 | 示例 |
|---------|------|------|
| **时间超时** | 告警超过阈值时间未处理 | CRITICAL告警15分钟未响应 |
| **严重性升级** | 威胁评分或影响范围增加 | 攻击范围从1台扩展到5台设备 |
| **重复攻击** | 同一攻击源反复触发告警 | 同一IP在1小时内触发3次告警 |
| **手动升级** | 运营人员判断需要升级 | 发现攻击具有APT特征 |
| **关联事件** | 多个告警形成攻击链 | 同时检测到端口扫描+远程登录 |

### 2.2 升级级别

| 级别 | 描述 | 通知范围 | 响应时间 |
|------|------|---------|---------|
| **L1** | 一线运营团队 | 值班工程师 | 1小时 |
| **L2** | 安全分析师 | 安全团队主管 | 30分钟 |
| **L3** | 安全专家 | CISO、技术总监 | 15分钟 |
| **L4** | 高级管理层 | CEO、董事会 | 立即 |

### 2.3 升级操作

- **提升优先级**: LOW → MEDIUM → HIGH → CRITICAL
- **扩大通知范围**: 通知更多人员或更高级别管理层
- **增加资源**: 分配更多分析师或自动化工具
- **触发应急响应**: 启动应急响应预案

---

## 3. 升级策略引擎

### 3.1 策略配置结构

```json
{
  "strategyId": "auto-escalation-critical",
  "name": "CRITICAL告警自动升级策略",
  "enabled": true,
  "conditions": [
    {
      "type": "SEVERITY",
      "operator": "EQUALS",
      "value": "CRITICAL"
    },
    {
      "type": "STATUS",
      "operator": "EQUALS",
      "value": "OPEN"
    },
    {
      "type": "AGE",
      "operator": "GREATER_THAN",
      "value": "PT15M"
    }
  ],
  "actions": [
    {
      "type": "ESCALATE_PRIORITY",
      "targetLevel": "L2"
    },
    {
      "type": "NOTIFY",
      "recipients": ["security-lead@company.com"]
    },
    {
      "type": "ADD_TAG",
      "tag": "auto-escalated"
    }
  ],
  "checkInterval": "PT5M"
}
```

### 3.2 内置策略

| 策略名称 | 触发条件 | 升级动作 |
|---------|---------|---------|
| **critical-timeout** | CRITICAL告警15分钟未处理 | 升级到L2,通知安全主管 |
| **high-timeout** | HIGH告警1小时未处理 | 升级到L2 |
| **medium-timeout** | MEDIUM告警4小时未处理 | 升级到L1主管 |
| **repeated-attacker** | 同一IP 1小时内触发3次 | 升级到L2,自动隔离 |
| **multi-device-attack** | 影响5台以上设备 | 升级到L3,启动应急响应 |

---

## 4. 数据模型

### 4.1 数据库表结构

```sql
-- 升级策略配置表
CREATE TABLE alert_escalation_strategies (
    id SERIAL PRIMARY KEY,
    strategy_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    enabled BOOLEAN DEFAULT true,
    conditions JSONB NOT NULL,
    actions JSONB NOT NULL,
    check_interval INTERVAL DEFAULT '5 minutes',
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    INDEX idx_enabled (enabled),
    INDEX idx_priority (priority)
);

-- 升级历史记录表
CREATE TABLE alert_escalation_history (
    id SERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    escalation_type VARCHAR(50) NOT NULL,  -- AUTO, MANUAL
    previous_level VARCHAR(20),
    new_level VARCHAR(20) NOT NULL,
    reason TEXT,
    strategy_id VARCHAR(100),
    escalated_by VARCHAR(100),
    escalated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled BOOLEAN DEFAULT false,
    cancelled_at TIMESTAMP,
    cancelled_by VARCHAR(100),
    cancel_reason TEXT,
    FOREIGN KEY (alert_id) REFERENCES alerts(id),
    INDEX idx_alert_id (alert_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_escalated_at (escalated_at),
    INDEX idx_escalation_type (escalation_type)
);
```

### 4.2 Java实体类

```java
/**
 * 升级策略配置
 */
@Data
@Builder
@Entity
@Table(name = "alert_escalation_strategies")
public class EscalationStrategy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String strategyId;
    
    private String name;
    private String description;
    private Boolean enabled;
    
    @Column(columnDefinition = "jsonb")
    private String conditions;  // JSON格式的条件配置
    
    @Column(columnDefinition = "jsonb")
    private String actions;     // JSON格式的动作配置
    
    private Duration checkInterval;
    private Integer priority;
    
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}

/**
 * 升级历史记录
 */
@Data
@Builder
@Entity
@Table(name = "alert_escalation_history")
public class EscalationHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long alertId;
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    private EscalationType escalationType;  // AUTO, MANUAL
    
    private String previousLevel;
    private String newLevel;
    private String reason;
    private String strategyId;
    private String escalatedBy;
    private Instant escalatedAt;
    
    private Boolean cancelled;
    private Instant cancelledAt;
    private String cancelledBy;
    private String cancelReason;
}

/**
 * 升级类型枚举
 */
public enum EscalationType {
    AUTO,      // 自动升级
    MANUAL     // 手动升级
}
```

### 4.3 请求/响应DTO

```java
/**
 * 手动升级请求
 */
@Data
public class EscalateAlertRequest {
    private String reason;              // 升级原因
    private String targetLevel;         // 目标级别 (可选)
    private List<String> notifyList;    // 额外通知人员 (可选)
}

/**
 * 升级策略配置请求
 */
@Data
public class ConfigureStrategyRequest {
    private String strategyId;
    private String name;
    private String description;
    private Boolean enabled;
    private List<StrategyCondition> conditions;
    private List<StrategyAction> actions;
    private String checkInterval;  // ISO8601 duration格式
}

/**
 * 升级历史查询响应
 */
@Data
@Builder
public class EscalationHistoryResponse {
    private Long id;
    private Long alertId;
    private String escalationType;
    private String previousLevel;
    private String newLevel;
    private String reason;
    private String escalatedBy;
    private Instant escalatedAt;
    private Boolean cancelled;
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 认证 |
|------|------|------|------|
| `POST` | `/api/v1/alerts/{id}/escalate` | 手动升级告警 | ✅ |
| `POST` | `/api/v1/alerts/{id}/cancel-escalation` | 取消升级 | ✅ |
| `POST` | `/api/v1/alerts/escalation/strategies` | 配置升级策略 | ✅ Admin |
| `GET` | `/api/v1/alerts/escalation/strategies` | 查询升级策略列表 | ✅ |
| `GET` | `/api/v1/alerts/{id}/escalation-history` | 查询告警升级历史 | ✅ |
| `POST` | `/api/v1/alerts/escalation/batch` | 批量升级告警 | ✅ |

---

## 6. API详细文档

### 6.1 手动升级告警

### 6.1 手动升级告警

**描述**: 手动将告警升级到更高优先级或通知级别,适用于需要人工干预的紧急情况。

**端点**: `POST /api/v1/alerts/{id}/escalate`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `reason` | String | ✅ | 升级原因 |
| `targetLevel` | String | ❌ | 目标级别 (L1/L2/L3/L4) |
| `notifyList` | List<String> | ❌ | 额外通知人员邮箱列表 |

### 升级场景

| 场景 | 说明 | 示例 |
|------|------|------|
| **处理超时** | 告警长时间未响应 | CRITICAL告警15分钟无人处理 |
| **威胁升级** | 攻击范围扩大 | 从单台设备扩展到整个网段 |
| **手动升级** | 需要更高级别处理 | 发现APT攻击特征 |
| **关联事件** | 多个告警形成攻击链 | 端口扫描+远程登录+数据外泄 |

### 请求示例 (curl)

```bash
# 基本升级
curl -X POST http://localhost:8082/api/v1/alerts/12345/escalate \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "攻击范围扩大,影响多个网段,需要安全专家介入"
  }'

# 升级到指定级别
curl -X POST http://localhost:8082/api/v1/alerts/12345/escalate \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "检测到APT攻击特征,需要立即升级",
    "targetLevel": "L3",
    "notifyList": ["ciso@company.com", "security-director@company.com"]
  }'
```

### 请求示例 (Java)

```java
public class EscalationExample {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 手动升级告警
     */
    public void escalateAlert(Long alertId, String reason) {
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setReason(reason);
        
        HttpEntity<EscalateAlertRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/" + alertId + "/escalate",
            httpRequest,
            Void.class
        );
        
        log.info("Alert escalated: id={}, reason={}", alertId, reason);
    }
    
    /**
     * 升级到指定级别并通知关键人员
     */
    public void escalateToLevel(Long alertId, String reason, String targetLevel, List<String> notifyList) {
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setReason(reason);
        request.setTargetLevel(targetLevel);
        request.setNotifyList(notifyList);
        
        HttpEntity<EscalateAlertRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/" + alertId + "/escalate",
            httpRequest,
            Void.class
        );
        
        log.info("Alert escalated to {}: id={}", targetLevel, alertId);
    }
}
```

### 响应

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "告警已成功升级",
  "escalationId": 789,
  "newLevel": "L2",
  "notifiedUsers": ["security-lead@company.com", "ciso@company.com"]
}
```

**HTTP 400 Bad Request** (告警已关闭)

```json
{
  "error": "INVALID_STATE",
  "message": "无法升级已关闭的告警"
}
```

---

### 6.2 取消升级

**描述**: 撤销不当的升级操作,将告警恢复到升级前的状态。

**端点**: `POST /api/v1/alerts/{id}/cancel-escalation`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `reason` | String | ✅ | 取消原因 |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/cancel-escalation \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "误判,该告警为正常业务流量"
  }'
```

### 请求示例 (Java)

```java
public void cancelEscalation(Long alertId, String reason) {
    CancelEscalationRequest request = new CancelEscalationRequest();
    request.setReason(reason);
    
    HttpEntity<CancelEscalationRequest> httpRequest = new HttpEntity<>(request);
    
    restTemplate.postForEntity(
        BASE_URL + "/" + alertId + "/cancel-escalation",
        httpRequest,
        Void.class
    );
    
    log.info("Escalation cancelled: alertId={}, reason={}", alertId, reason);
}
```

### 响应

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "升级已取消",
  "restoredLevel": "L1"
}
```

---

### 6.3 配置升级策略

**描述**: 创建或更新自动升级策略配置。

**端点**: `POST /api/v1/alerts/escalation/strategies`

**权限**: 需要管理员权限

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `strategyId` | String | ✅ | 策略唯一标识 |
| `name` | String | ✅ | 策略名称 |
| `description` | String | ❌ | 策略描述 |
| `enabled` | Boolean | ❌ | 是否启用 (默认true) |
| `conditions` | List | ✅ | 触发条件列表 |
| `actions` | List | ✅ | 执行动作列表 |
| `checkInterval` | String | ❌ | 检查间隔 (默认PT5M) |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/escalation/strategies \
  -H "Content-Type: application/json" \
  -d '{
    "strategyId": "critical-15min-timeout",
    "name": "CRITICAL告警15分钟超时策略",
    "description": "CRITICAL级别告警15分钟未处理则升级到L2",
    "enabled": true,
    "conditions": [
      {
        "type": "SEVERITY",
        "operator": "EQUALS",
        "value": "CRITICAL"
      },
      {
        "type": "STATUS",
        "operator": "EQUALS",
        "value": "OPEN"
      },
      {
        "type": "AGE",
        "operator": "GREATER_THAN",
        "value": "PT15M"
      }
    ],
    "actions": [
      {
        "type": "ESCALATE_PRIORITY",
        "targetLevel": "L2"
      },
      {
        "type": "NOTIFY",
        "recipients": ["security-lead@company.com"]
      }
    ],
    "checkInterval": "PT5M"
  }'
```

### 请求示例 (Java)

```java
public void configureStrategy() {
    ConfigureStrategyRequest request = new ConfigureStrategyRequest();
    request.setStrategyId("critical-15min-timeout");
    request.setName("CRITICAL告警15分钟超时策略");
    request.setEnabled(true);
    
    // 配置条件
    List<StrategyCondition> conditions = Arrays.asList(
        new StrategyCondition("SEVERITY", "EQUALS", "CRITICAL"),
        new StrategyCondition("STATUS", "EQUALS", "OPEN"),
        new StrategyCondition("AGE", "GREATER_THAN", "PT15M")
    );
    request.setConditions(conditions);
    
    // 配置动作
    List<StrategyAction> actions = Arrays.asList(
        new StrategyAction("ESCALATE_PRIORITY", Map.of("targetLevel", "L2")),
        new StrategyAction("NOTIFY", Map.of("recipients", List.of("security-lead@company.com")))
    );
    request.setActions(actions);
    
    request.setCheckInterval("PT5M");
    
    HttpEntity<ConfigureStrategyRequest> httpRequest = new HttpEntity<>(request);
    
    restTemplate.postForEntity(
        BASE_URL + "/escalation/strategies",
        httpRequest,
        Void.class
    );
}
```

### 响应

**HTTP 201 Created**

```json
{
  "success": true,
  "message": "升级策略已创建",
  "strategyId": "critical-15min-timeout"
}
```

---

### 6.4 查询升级历史

**描述**: 查询指定告警的升级操作历史记录。

**端点**: `GET /api/v1/alerts/{id}/escalation-history`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

### 请求示例 (curl)

```bash
curl -X GET http://localhost:8082/api/v1/alerts/12345/escalation-history
```

### 请求示例 (Java)

```java
public List<EscalationHistoryResponse> getEscalationHistory(Long alertId) {
    String url = BASE_URL + "/" + alertId + "/escalation-history";
    
    ResponseEntity<List<EscalationHistoryResponse>> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<List<EscalationHistoryResponse>>() {}
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
[
  {
    "id": 789,
    "alertId": 12345,
    "escalationType": "AUTO",
    "previousLevel": "L1",
    "newLevel": "L2",
    "reason": "CRITICAL告警超时15分钟未处理",
    "strategyId": "critical-15min-timeout",
    "escalatedBy": "system",
    "escalatedAt": "2025-01-15T10:15:00Z",
    "cancelled": false
  },
  {
    "id": 790,
    "alertId": 12345,
    "escalationType": "MANUAL",
    "previousLevel": "L2",
    "newLevel": "L3",
    "reason": "检测到APT攻击特征,需要专家分析",
    "escalatedBy": "john.doe@company.com",
    "escalatedAt": "2025-01-15T10:30:00Z",
    "cancelled": false
  }
]
```

---

### 6.5 批量升级

**描述**: 批量升级多个相关告警,适用于大规模攻击或关联事件。

**端点**: `POST /api/v1/alerts/escalation/batch`

**请求体参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `alertIds` | List<Long> | ✅ | 告警ID列表 (最多100个) |
| `reason` | String | ✅ | 升级原因 |
| `targetLevel` | String | ❌ | 目标级别 |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/escalation/batch \
  -H "Content-Type: application/json" \
  -d '{
    "alertIds": [12345, 12346, 12347, 12348],
    "reason": "同一攻击者在多个网段发起攻击,批量升级",
    "targetLevel": "L2"
  }'
```

### 请求示例 (Java)

```java
public BatchEscalationResult batchEscalate(List<Long> alertIds, String reason, String targetLevel) {
    BatchEscalationRequest request = new BatchEscalationRequest();
    request.setAlertIds(alertIds);
    request.setReason(reason);
    request.setTargetLevel(targetLevel);
    
    HttpEntity<BatchEscalationRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<BatchEscalationResult> response = restTemplate.postForEntity(
        BASE_URL + "/escalation/batch",
        httpRequest,
        BatchEscalationResult.class
    );
    
    return response.getBody();
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "success": true,
  "totalRequested": 4,
  "successCount": 3,
  "failureCount": 1,
  "results": [
    {
      "alertId": 12345,
      "success": true,
      "message": "升级成功"
    },
    {
      "alertId": 12346,
      "success": true,
      "message": "升级成功"
    },
    {
      "alertId": 12347,
      "success": true,
      "message": "升级成功"
    },
    {
      "alertId": 12348,
      "success": false,
      "message": "告警已关闭,无法升级"
    }
  ]
}
```

---

## 7. 使用场景

### 场景1: 自动升级超时告警

**需求**: 自动检测并升级长时间未处理的高优先级告警。

**实现**:

```java
@Component
@Slf4j
public class AutoEscalationService {
    
    private final AlertRepository alertRepository;
    private final EscalationClient escalationClient;
    
    /**
     * 每5分钟检查一次是否有需要升级的告警
     */
    @Scheduled(fixedDelay = 300000)  // 5分钟
    public void checkAndEscalateTimeoutAlerts() {
        Instant now = Instant.now();
        
        // 策略1: CRITICAL告警超过15分钟未处理
        Instant criticalThreshold = now.minus(15, ChronoUnit.MINUTES);
        List<Alert> criticalTimeouts = alertRepository.findBySeverityAndStatusAndCreatedAtBefore(
            AlertSeverity.CRITICAL,
            AlertStatus.OPEN,
            criticalThreshold
        );
        
        for (Alert alert : criticalTimeouts) {
            try {
                escalationClient.escalateAlert(
                    alert.getId(),
                    "CRITICAL告警超过15分钟未处理,自动升级"
                );
                log.warn("Auto-escalated CRITICAL alert: id={}, age={}min", 
                         alert.getId(), 
                         Duration.between(alert.getCreatedAt(), now).toMinutes());
            } catch (Exception e) {
                log.error("Failed to escalate alert: id={}", alert.getId(), e);
            }
        }
        
        // 策略2: HIGH告警超过1小时未处理
        Instant highThreshold = now.minus(1, ChronoUnit.HOURS);
        List<Alert> highTimeouts = alertRepository.findBySeverityAndStatusAndCreatedAtBefore(
            AlertSeverity.HIGH,
            AlertStatus.OPEN,
            highThreshold
        );
        
        for (Alert alert : highTimeouts) {
            try {
                escalationClient.escalateAlert(
                    alert.getId(),
                    "HIGH告警超过1小时未处理,自动升级"
                );
                log.info("Auto-escalated HIGH alert: id={}", alert.getId());
            } catch (Exception e) {
                log.error("Failed to escalate alert: id={}", alert.getId(), e);
            }
        }
        
        log.info("Auto-escalation check completed: CRITICAL={}, HIGH={}", 
                 criticalTimeouts.size(), highTimeouts.size());
    }
    
    /**
     * 检查是否需要升级 (用于事件驱动场景)
     */
    public void checkAndEscalate(Alert alert) {
        Instant now = Instant.now();
        Duration age = Duration.between(alert.getCreatedAt(), now);
        
        // CRITICAL告警15分钟未处理
        if (AlertSeverity.CRITICAL == alert.getSeverity() 
            && alert.getStatus() == AlertStatus.OPEN
            && age.toMinutes() >= 15) {
            
            escalationClient.escalateAlert(
                alert.getId(),
                "CRITICAL告警超时未处理"
            );
        }
        
        // HIGH告警1小时未处理
        if (AlertSeverity.HIGH == alert.getSeverity()
            && alert.getStatus() == AlertStatus.OPEN
            && age.toMinutes() >= 60) {
            
            escalationClient.escalateAlert(
                alert.getId(),
                "HIGH告警超时未处理"
            );
        }
    }
}
```

---

### 场景2: 基于攻击范围的智能升级

**需求**: 当攻击者影响的设备数量超过阈值时自动升级。

**实现**:

```java
@Component
@Slf4j
public class AttackScopeEscalationService {
    
    private final AlertRepository alertRepository;
    private final ThreatAssessmentClient assessmentClient;
    private final EscalationClient escalationClient;
    
    /**
     * 监听威胁评估更新事件
     */
    @KafkaListener(topics = "threat-assessments", groupId = "escalation-service")
    public void onThreatAssessmentUpdated(ThreatAssessmentEvent event) {
        // 检查攻击范围
        if (event.getUniqueDevices() >= 5) {
            // 查询关联的告警
            List<Alert> alerts = alertRepository.findByAttackMac(event.getAttackMac());
            
            for (Alert alert : alerts) {
                if (alert.getStatus() == AlertStatus.OPEN) {
                    // 批量升级相关告警
                    try {
                        escalationClient.escalateAlert(
                            alert.getId(),
                            String.format(
                                "攻击范围扩大: 影响%d台设备,威胁评分%.1f",
                                event.getUniqueDevices(),
                                event.getThreatScore()
                            )
                        );
                        log.warn("Escalated due to attack scope: alertId={}, devices={}", 
                                 alert.getId(), event.getUniqueDevices());
                    } catch (Exception e) {
                        log.error("Failed to escalate: alertId={}", alert.getId(), e);
                    }
                }
            }
        }
        
        // 威胁评分达到CRITICAL级别
        if ("CRITICAL".equals(event.getThreatLevel())) {
            List<Alert> alerts = alertRepository.findByAttackMac(event.getAttackMac());
            
            for (Alert alert : alerts) {
                if (alert.getStatus() == AlertStatus.OPEN && alert.getSeverity() != AlertSeverity.CRITICAL) {
                    // 升级到L3级别
                    try {
                        EscalateAlertRequest request = new EscalateAlertRequest();
                        request.setReason("威胁评分达到CRITICAL级别: " + event.getThreatScore());
                        request.setTargetLevel("L3");
                        request.setNotifyList(Arrays.asList(
                            "ciso@company.com",
                            "security-director@company.com"
                        ));
                        
                        escalationClient.escalateToLevel(
                            alert.getId(),
                            request.getReason(),
                            request.getTargetLevel(),
                            request.getNotifyList()
                        );
                        
                        log.error("Critical escalation: alertId={}, score={}", 
                                  alert.getId(), event.getThreatScore());
                    } catch (Exception e) {
                        log.error("Failed to escalate to L3: alertId={}", alert.getId(), e);
                    }
                }
            }
        }
    }
}
```

---

### 场景3: 手动升级工作流

**需求**: 安全分析师在调查过程中发现需要升级的情况。

**实现**:

```java
@Service
@Slf4j
public class ManualEscalationService {
    
    private final EscalationClient escalationClient;
    private final NotificationService notificationService;
    
    /**
     * 分析师手动升级告警
     */
    public void escalateByAnalyst(Long alertId, String analystEmail, String findings) {
        String reason = String.format(
            "分析师 %s 发现: %s",
            analystEmail,
            findings
        );
        
        try {
            // 升级告警
            escalationClient.escalateAlert(alertId, reason);
            
            // 发送通知
            notificationService.sendEscalationNotification(
                alertId,
                analystEmail,
                findings
            );
            
            log.info("Manual escalation by analyst: alertId={}, analyst={}", 
                     alertId, analystEmail);
                     
        } catch (Exception e) {
            log.error("Failed manual escalation: alertId={}", alertId, e);
            throw new EscalationException("升级失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 升级到高级管理层
     */
    public void escalateToExecutive(Long alertId, String reason) {
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setReason(reason);
        request.setTargetLevel("L4");
        request.setNotifyList(Arrays.asList(
            "ceo@company.com",
            "ciso@company.com",
            "board@company.com"
        ));
        
        escalationClient.escalateToLevel(
            alertId,
            request.getReason(),
            request.getTargetLevel(),
            request.getNotifyList()
        );
        
        log.error("Executive escalation: alertId={}, reason={}", alertId, reason);
    }
    
    /**
     * 批量升级关联告警
     */
    public void escalateRelatedAlerts(List<Long> alertIds, String reason) {
        if (alertIds.size() > 100) {
            throw new IllegalArgumentException("批量升级最多支持100个告警");
        }
        
        BatchEscalationResult result = escalationClient.batchEscalate(
            alertIds,
            reason,
            "L2"
        );
        
        log.info("Batch escalation completed: success={}, failure={}", 
                 result.getSuccessCount(), result.getFailureCount());
                 
        if (result.getFailureCount() > 0) {
            log.warn("Some alerts failed to escalate: {}", result.getResults());
        }
    }
}
```

---

## 8. Java客户端完整示例

### AlertEscalationClient

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

/**
 * 告警升级API客户端
 * 
 * <p>提供以下功能:
 * <ul>
 *   <li>手动升级告警</li>
 *   <li>取消升级</li>
 *   <li>配置升级策略</li>
 *   <li>查询升级历史</li>
 *   <li>批量升级</li>
 * </ul>
 * 
 * @author ThreatDetection Team
 * @version 1.0
 */
@Slf4j
@Component
public class AlertEscalationClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate;
    
    public AlertEscalationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 手动升级告警
     * 
     * @param alertId 告警ID
     * @param reason 升级原因
     */
    public void escalateAlert(Long alertId, String reason) {
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setReason(reason);
        
        HttpEntity<EscalateAlertRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/" + alertId + "/escalate",
            httpRequest,
            Void.class
        );
        
        log.info("Alert escalated: id={}, reason={}", alertId, reason);
    }
    
    /**
     * 升级到指定级别
     * 
     * @param alertId 告警ID
     * @param reason 升级原因
     * @param targetLevel 目标级别 (L1/L2/L3/L4)
     * @param notifyList 额外通知人员列表
     */
    public void escalateToLevel(
            Long alertId,
            String reason,
            String targetLevel,
            List<String> notifyList) {
        
        EscalateAlertRequest request = new EscalateAlertRequest();
        request.setReason(reason);
        request.setTargetLevel(targetLevel);
        request.setNotifyList(notifyList);
        
        HttpEntity<EscalateAlertRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/" + alertId + "/escalate",
            httpRequest,
            Void.class
        );
        
        log.info("Alert escalated to {}: id={}", targetLevel, alertId);
    }
    
    /**
     * 取消升级
     * 
     * @param alertId 告警ID
     * @param reason 取消原因
     */
    public void cancelEscalation(Long alertId, String reason) {
        CancelEscalationRequest request = new CancelEscalationRequest();
        request.setReason(reason);
        
        HttpEntity<CancelEscalationRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/" + alertId + "/cancel-escalation",
            httpRequest,
            Void.class
        );
        
        log.info("Escalation cancelled: alertId={}, reason={}", alertId, reason);
    }
    
    /**
     * 配置升级策略
     * 
     * @param request 策略配置请求
     */
    public void configureStrategy(ConfigureStrategyRequest request) {
        HttpEntity<ConfigureStrategyRequest> httpRequest = new HttpEntity<>(request);
        
        restTemplate.postForEntity(
            BASE_URL + "/escalation/strategies",
            httpRequest,
            Void.class
        );
        
        log.info("Escalation strategy configured: strategyId={}", request.getStrategyId());
    }
    
    /**
     * 查询升级历史
     * 
     * @param alertId 告警ID
     * @return 升级历史列表
     */
    public List<EscalationHistoryResponse> getEscalationHistory(Long alertId) {
        String url = BASE_URL + "/" + alertId + "/escalation-history";
        
        ResponseEntity<List<EscalationHistoryResponse>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<EscalationHistoryResponse>>() {}
        );
        
        return response.getBody();
    }
    
    /**
     * 批量升级告警
     * 
     * @param alertIds 告警ID列表
     * @param reason 升级原因
     * @param targetLevel 目标级别 (可选)
     * @return 批量升级结果
     */
    public BatchEscalationResult batchEscalate(
            List<Long> alertIds,
            String reason,
            String targetLevel) {
        
        if (alertIds.size() > 100) {
            throw new IllegalArgumentException("批量升级最多支持100个告警");
        }
        
        BatchEscalationRequest request = new BatchEscalationRequest();
        request.setAlertIds(alertIds);
        request.setReason(reason);
        request.setTargetLevel(targetLevel);
        
        HttpEntity<BatchEscalationRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<BatchEscalationResult> response = restTemplate.postForEntity(
            BASE_URL + "/escalation/batch",
            httpRequest,
            BatchEscalationResult.class
        );
        
        BatchEscalationResult result = response.getBody();
        log.info("Batch escalation completed: success={}, failure={}", 
                 result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * 查询所有升级策略
     * 
     * @return 策略列表
     */
    public List<EscalationStrategy> getAllStrategies() {
        String url = BASE_URL + "/escalation/strategies";
        
        ResponseEntity<List<EscalationStrategy>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<EscalationStrategy>>() {}
        );
        
        return response.getBody();
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 9.1 设置合理的升级阈值

```java
// ✅ 正确: 根据严重性设置不同的超时阈值
private static final Map<AlertSeverity, Duration> ESCALATION_THRESHOLDS = Map.of(
    AlertSeverity.CRITICAL, Duration.ofMinutes(15),
    AlertSeverity.HIGH, Duration.ofHours(1),
    AlertSeverity.MEDIUM, Duration.ofHours(4),
    AlertSeverity.LOW, Duration.ofHours(24)
);

// ❌ 错误: 所有告警使用相同的超时时间
private static final Duration ESCALATION_THRESHOLD = Duration.ofHours(1);
```

#### 9.2 记录升级原因

```java
// ✅ 正确: 详细记录升级原因,便于审计
escalationClient.escalateAlert(
    alertId,
    String.format(
        "攻击范围扩大: 从%d台设备增加到%d台设备, " +
        "威胁评分从%.1f升至%.1f, " +
        "建议立即隔离攻击源%s",
        oldDeviceCount, newDeviceCount,
        oldScore, newScore,
        attackIp
    )
);

// ❌ 错误: 升级原因不明确
escalationClient.escalateAlert(alertId, "需要升级");
```

#### 9.3 验证升级前提条件

```java
// ✅ 正确: 检查告警状态后再升级
public void escalateIfOpen(Long alertId, String reason) {
    Alert alert = alertRepository.findById(alertId)
        .orElseThrow(() -> new AlertNotFoundException(alertId));
    
    if (alert.getStatus() == AlertStatus.OPEN || alert.getStatus() == AlertStatus.IN_PROGRESS) {
        escalationClient.escalateAlert(alertId, reason);
    } else {
        log.warn("Cannot escalate closed alert: id={}", alertId);
    }
}

// ❌ 错误: 不检查状态直接升级
public void escalate(Long alertId, String reason) {
    escalationClient.escalateAlert(alertId, reason);  // 可能失败
}
```

#### 9.4 使用批量升级处理关联事件

```java
// ✅ 正确: 批量升级相关告警
public void escalateAttackCampaign(String attackIp) {
    List<Alert> relatedAlerts = alertRepository.findByAttackIp(attackIp);
    List<Long> alertIds = relatedAlerts.stream()
        .map(Alert::getId)
        .collect(Collectors.toList());
    
    if (!alertIds.isEmpty()) {
        escalationClient.batchEscalate(
            alertIds,
            "检测到来自" + attackIp + "的协调攻击活动",
            "L2"
        );
    }
}

// ❌ 错误: 循环单个升级 (性能差)
public void escalateRelated(List<Alert> alerts) {
    for (Alert alert : alerts) {
        escalationClient.escalateAlert(alert.getId(), "关联攻击");
    }
}
```

---

### ❌ 避免的做法

#### 9.5 避免重复升级

```java
// ❌ 错误: 没有检查是否已经升级过
@Scheduled(fixedDelay = 60000)
public void checkEscalation() {
    List<Alert> alerts = alertRepository.findByStatus(AlertStatus.OPEN);
    for (Alert alert : alerts) {
        escalationClient.escalateAlert(alert.getId(), "定时检查");  // 可能重复升级
    }
}

// ✅ 正确: 检查升级历史
@Scheduled(fixedDelay = 60000)
public void checkEscalation() {
    List<Alert> alerts = alertRepository.findByStatus(AlertStatus.OPEN);
    for (Alert alert : alerts) {
        List<EscalationHistoryResponse> history = 
            escalationClient.getEscalationHistory(alert.getId());
        
        if (history.isEmpty() && needsEscalation(alert)) {
            escalationClient.escalateAlert(alert.getId(), "超时未处理");
        }
    }
}
```

#### 9.6 避免升级风暴

```java
// ❌ 错误: 短时间内多次升级
public void aggressiveEscalation(Long alertId) {
    escalationClient.escalateAlert(alertId, "第一次升级");
    Thread.sleep(1000);
    escalationClient.escalateAlert(alertId, "第二次升级");
    Thread.sleep(1000);
    escalationClient.escalateAlert(alertId, "第三次升级");
}

// ✅ 正确: 设置升级冷却期
private final Map<Long, Instant> lastEscalationTime = new ConcurrentHashMap<>();
private static final Duration COOLDOWN = Duration.ofMinutes(10);

public void escalateWithCooldown(Long alertId, String reason) {
    Instant lastTime = lastEscalationTime.get(alertId);
    Instant now = Instant.now();
    
    if (lastTime == null || Duration.between(lastTime, now).compareTo(COOLDOWN) > 0) {
        escalationClient.escalateAlert(alertId, reason);
        lastEscalationTime.put(alertId, now);
    } else {
        log.warn("Escalation cooldown active for alert: id={}", alertId);
    }
}
```

---

## 10. 故障排查

### 10.1 升级失败: 告警已关闭

**症状**: API返回400错误,提示"无法升级已关闭的告警"

**诊断步骤**:

1. **检查告警状态**:
```bash
curl -X GET http://localhost:8082/api/v1/alerts/12345
```

2. **查看告警生命周期**:
```sql
SELECT id, status, severity, created_at, updated_at, closed_at
FROM alerts
WHERE id = 12345;
```

**解决方案**:

```java
// 升级前检查状态
public void safeEscalate(Long alertId, String reason) {
    Alert alert = alertRepository.findById(alertId)
        .orElseThrow(() -> new AlertNotFoundException(alertId));
    
    if (alert.getStatus() == AlertStatus.CLOSED || alert.getStatus() == AlertStatus.RESOLVED) {
        log.warn("Cannot escalate closed alert: id={}, status={}", alertId, alert.getStatus());
        return;
    }
    
    escalationClient.escalateAlert(alertId, reason);
}
```

---

### 10.2 自动升级未触发

**症状**: 符合条件的告警没有被自动升级

**诊断步骤**:

1. **检查升级策略配置**:
```bash
curl -X GET http://localhost:8082/api/v1/alerts/escalation/strategies
```

2. **检查定时任务是否运行**:
```bash
# 查看日志
tail -f /var/log/alert-management/auto-escalation.log

# 检查定时任务状态
curl -X GET http://localhost:8082/actuator/scheduledtasks
```

3. **检查告警是否满足条件**:
```sql
-- 查询15分钟前创建的CRITICAL告警
SELECT id, severity, status, created_at, 
       AGE(NOW(), created_at) as age
FROM alerts
WHERE severity = 'CRITICAL'
  AND status = 'OPEN'
  AND created_at < NOW() - INTERVAL '15 minutes';
```

**解决方案**:

```java
// 添加详细日志
@Scheduled(fixedDelay = 300000)
public void checkEscalation() {
    log.info("Starting auto-escalation check");
    
    Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);
    List<Alert> alerts = alertRepository.findCriticalTimeoutAlerts(threshold);
    
    log.info("Found {} CRITICAL alerts older than 15 minutes", alerts.size());
    
    for (Alert alert : alerts) {
        try {
            escalationClient.escalateAlert(alert.getId(), "自动升级");
            log.info("Escalated alert: id={}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to escalate: id={}", alert.getId(), e);
        }
    }
    
    log.info("Auto-escalation check completed");
}
```

---

### 10.3 升级通知未发送

**症状**: 告警升级成功但相关人员未收到通知

**诊断步骤**:

1. **检查升级历史**:
```bash
curl -X GET http://localhost:8082/api/v1/alerts/12345/escalation-history
```

2. **检查通知配置**:
```sql
SELECT * FROM notification_configs WHERE enabled = true;
```

3. **查看邮件发送日志**:
```bash
tail -f /var/log/alert-management/notification.log
```

**解决方案**:

```java
// 确保升级时指定通知列表
EscalateAlertRequest request = new EscalateAlertRequest();
request.setReason("CRITICAL告警超时");
request.setTargetLevel("L2");
request.setNotifyList(Arrays.asList(
    "security-lead@company.com",
    "oncall-engineer@company.com"
));

escalationClient.escalateToLevel(
    alertId,
    request.getReason(),
    request.getTargetLevel(),
    request.getNotifyList()
);
```

---

## 11. 相关文档

| 文档 | 说明 |
|------|------|
| [告警CRUD API](./alert_crud_api.md) | 告警基本操作 |
| [告警生命周期API](./alert_lifecycle_api.md) | 告警状态管理 |
| [告警通知API](./alert_notification_api.md) | 通知配置和发送 |
| [告警分析API](./alert_analytics_api.md) | 告警统计分析 |
| [威胁评估API](./threat_assessment_evaluation_api.md) | 威胁评分和评估 |

---

**相关文档**: [生命周期API](./alert_lifecycle_api.md) | [分析API](./alert_analytics_api.md)

---

**文档结束**

*最后更新: 2025-10-16*
