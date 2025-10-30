````markdown
# 告警CRUD API文档

**服务名称**: Alert Management Service  
**服务端口**: 8082  
**基础路径**: `/api/v1/alerts`  
**版本**: 1.0  
**更新日期**: 2025-10-16

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [数据模型](#数据模型)
4. [API端点列表](#api端点列表)
5. [API详细文档](#api详细文档)
   - [5.1 创建告警](#51-创建告警)
   - [5.2 获取告警详情](#52-获取告警详情)
   - [5.3 查询告警列表](#53-查询告警列表)
   - [5.4 更新告警](#54-更新告警)
   - [5.5 删除告警](#55-删除告警)
   - [5.6 批量操作](#56-批量操作)
6. [使用场景](#使用场景)
7. [Java客户端示例](#java客户端示例)
8. [最佳实践](#最佳实践)
9. [故障排查](#故障排查)
10. [相关文档](#相关文档)

---

## 系统概述

### 核心职责

告警CRUD服务负责威胁检测系统中告警的完整生命周期管理:

```
威胁评估 → 创建告警 → 查询/更新 → 处理/解决 → 归档/删除
     ↓           ↓           ↓           ↓           ↓
  Alert API   存储到DB   分页查询    状态管理    数据清理
```

### 核心特性

✅ **告警创建** - 基于威胁评估结果自动或手动创建告警  
✅ **灵活查询** - 支持多维度过滤、排序、分页  
✅ **状态管理** - 完整的状态转换和审计追踪  
✅ **批量操作** - 支持批量创建、更新、删除  
✅ **多租户隔离** - 基于customerId的数据隔离  
✅ **审计日志** - 记录所有CRUD操作历史  

### 工作流程

```
1. 威胁评估服务 → 发现CRITICAL威胁 → 调用创建告警API
2. 告警存储到PostgreSQL (alerts表)
3. 安全分析师通过查询API查看待处理告警
4. 分配告警给责任人 → 更新状态为IN_PROGRESS
5. 处理完成后 → 更新状态为RESOLVED
6. 定期归档或删除旧告警
```

### 技术栈

- **框架**: Spring Boot 3.1.5
- **数据库**: PostgreSQL 15+ (告警存储)
- **消息队列**: Kafka (告警通知事件)
- **序列化**: JSON (Jackson)

---

## 核心功能

### 1. 告警创建

**触发方式**:
- **自动创建**: 威胁评估服务检测到HIGH/CRITICAL威胁时自动创建
- **手动创建**: 安全分析师手动创建特定告警

**必需字段**:
- `title`: 告警标题
- `severity`: 严重等级 (CRITICAL/HIGH/MEDIUM/LOW/INFO)
- `customerId`: 客户ID (租户隔离)
- `attackMac`: 被诱捕者MAC地址
- `attackIp`: 被诱捕者IP地址
- `threatScore`: 威胁评分

### 2. 告警查询

**查询维度**:
- **按状态**: OPEN/IN_PROGRESS/RESOLVED/CLOSED
- **按等级**: CRITICAL/HIGH/MEDIUM/LOW/INFO
- **按时间**: 指定时间范围
- **按客户**: customerId过滤 (自动隔离)
- **按攻击源**: attackMac/attackIp

**分页支持**:
- 页码: 从0开始
- 每页大小: 1-100 (默认20)
- 排序: 支持多字段排序

### 3. 告警更新

**可更新字段**:
- `title`: 告警标题
- `description`: 详细描述
- `severity`: 严重等级
- `status`: 告警状态
- `assignedTo`: 责任人

**不可更新字段** (创建后只读):
- `id`: 告警ID
- `customerId`: 客户ID
- `createdAt`: 创建时间

### 4. 告警删除

**删除策略**:
- **软删除**: 标记为已删除,保留数据
- **硬删除**: 物理删除数据库记录 (谨慎使用)
- **批量删除**: 支持按条件批量删除

### 5. 批量操作

**支持的批量操作**:
- 批量创建告警 (最多100条/次)
- 批量更新状态
- 批量分配责任人
- 批量删除

---

## 数据模型

### Alert实体

**数据库表**: `alerts`

```sql
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    
    -- 基础信息
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    
    -- 攻击源信息
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(50) NOT NULL,
    threat_score DECIMAL(10,2),
    
    -- 租户隔离
    customer_id VARCHAR(100) NOT NULL,
    
    -- 关联信息
    assessment_id VARCHAR(50),
    source VARCHAR(50) DEFAULT 'threat-assessment-service',
    
    -- 处理信息
    assigned_to VARCHAR(255),
    resolved_by VARCHAR(255),
    resolution TEXT,
    resolved_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_status (customer_id, status),
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at),
    INDEX idx_attack_ip (attack_ip)
);
```

### Alert DTO (Java)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private Long id;
    
    // 基础信息
    private String title;
    private String description;
    private AlertSeverity severity;  // CRITICAL, HIGH, MEDIUM, LOW, INFO
    private AlertStatus status;      // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    
    // 攻击源信息
    private String attackMac;
    private String attackIp;
    private Double threatScore;
    
    // 租户信息
    private String customerId;
    
    // 关联信息
    private String assessmentId;
    private String source;
    
    // 处理信息
    private String assignedTo;
    private String resolvedBy;
    private String resolution;
    private Instant resolvedAt;
    
    // 时间戳
    private Instant createdAt;
    private Instant updatedAt;
}

public enum AlertSeverity {
    INFO, LOW, MEDIUM, HIGH, CRITICAL
}

public enum AlertStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED, ESCALATED
}
```

---

## API端点列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `POST` | `/api/v1/alerts` | 创建告警 |
| `GET` | `/api/v1/alerts/{id}` | 获取告警详情 |
| `GET` | `/api/v1/alerts` | 查询告警列表 |

---

## API端点列表

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/v1/alerts` | 创建告警 | 创建单个告警 |
| `POST` | `/api/v1/alerts/batch` | 批量创建告警 | 批量创建(最多100条) |
| `GET` | `/api/v1/alerts/{id}` | 获取告警详情 | 根据ID查询 |
| `GET` | `/api/v1/alerts` | 查询告警列表 | 支持过滤、分页、排序 |
| `PUT` | `/api/v1/alerts/{id}` | 更新告警 | 更新告警信息 |
| `DELETE` | `/api/v1/alerts/{id}` | 删除告警 | 软删除或硬删除 |
| `DELETE` | `/api/v1/alerts/batch` | 批量删除告警 | 按条件批量删除 |

---

## API详细文档

### 5.1 创建告警

**描述**: 创建单个告警记录。通常由威胁评估服务自动调用,或由安全分析师手动创建。

**端点**: `POST /api/v1/alerts`

**请求体**: `Content-Type: application/json`

#### 请求参数

| 字段 | 类型 | 必需 | 验证规则 | 说明 |
|-----|------|------|---------|------|
| `title` | String | ✅ | @NotBlank, max=255 | 告警标题 |
| `description` | String | ❌ | max=2000 | 详细描述 |
| `severity` | String | ✅ | CRITICAL/HIGH/MEDIUM/LOW/INFO | 严重等级 |
| `attack_mac` | String | ✅ | @NotBlank | 被诱捕者MAC |
| `attack_ip` | String | ✅ | @NotBlank | 被诱捕者IP |
| `threat_score` | Double | ❌ | ≥0 | 威胁评分 |
| `customer_id` | String | ✅ | @NotBlank | 客户ID |
| `assessmentId` | String | ❌ | - | 关联的评估ID |
| `source` | String | ❌ | - | 告警来源 |

#### 请求示例 (curl)

**场景1: CRITICAL级别告警 (深夜横向移动)**

```bash
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "CRITICAL: 检测到深夜横向移动攻击",
    "description": "检测到IP 192.168.75.188 在凌晨2:30访问5个诱饵IP的3个端口,疑似APT横向移动",
    "severity": "CRITICAL",
    "attack_mac": "04:42:1a:8e:e3:65",
    "attack_ip": "192.168.75.188",
    "threat_score": 7290.0,
    "customer_id": "customer_a",
    "assessmentId": "ASSESS-12345",
    "source": "threat-assessment-service"
  }'
```

**场景2: HIGH级别告警 (端口扫描)**

```bash
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "HIGH: 检测到大规模端口扫描",
    "description": "IP 10.0.1.50 在短时间内扫描了20+个端口",
    "severity": "HIGH",
    "attack_mac": "aa:bb:cc:dd:ee:ff",
    "attack_ip": "10.0.1.50",
    "threat_score": 150.0,
    "customer_id": "customer_a",
    "source": "manual"
  }'
```

#### 请求示例 (Java)

```java
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class AlertCrudExample {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 创建CRITICAL告警
     */
    public Alert createCriticalAlert(
            String customerId,
            String attackMac,
            String attackIp,
            double threatScore,
            String assessmentId) {
        
        // 构建告警对象
        Alert alert = Alert.builder()
            .title("CRITICAL: 检测到深夜横向移动攻击")
            .description(String.format(
                "检测到IP %s 访问多个诱饵IP,威胁评分: %.2f",
                attackIp, threatScore
            ))
            .severity(AlertSeverity.CRITICAL)
            .status(AlertStatus.OPEN)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .threatScore(threatScore)
            .customerId(customerId)
            .assessmentId(assessmentId)
            .source("threat-assessment-service")
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Alert> request = new HttpEntity<>(alert, headers);
        
        // 发送请求
        ResponseEntity<Alert> response = restTemplate.postForEntity(
            BASE_URL,
            request,
            Alert.class
        );
        
        Alert createdAlert = response.getBody();
        System.out.println("Created alert ID: " + createdAlert.getId());
        
        return createdAlert;
    }
    
    /**
     * 创建手动告警
     */
    public Alert createManualAlert(
            String customerId,
            String title,
            AlertSeverity severity,
            String attackIp) {
        
        Alert alert = Alert.builder()
            .title(title)
            .severity(severity)
            .attackMac("MANUAL")
            .attackIp(attackIp)
            .customerId(customerId)
            .source("manual")
            .build();
        
        HttpEntity<Alert> request = new HttpEntity<>(alert, new HttpHeaders());
        
        ResponseEntity<Alert> response = restTemplate.postForEntity(
            BASE_URL,
            request,
            Alert.class
        );
        
        return response.getBody();
    }
}
```

#### 响应示例 (成功)

**HTTP 201 Created**

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到深夜横向移动攻击",
  "description": "检测到IP 192.168.75.188 在凌晨2:30访问5个诱饵IP的3个端口,疑似APT横向移动",
  "severity": "CRITICAL",
  "status": "OPEN",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "threat_score": 7290.0,
  "customer_id": "customer_a",
  "assessmentId": "ASSESS-12345",
  "source": "threat-assessment-service",
  "assigned_to": null,
  "resolved_by": null,
  "resolution": null,
  "resolved_at": null,
  "created_at": "2025-10-16T02:30:00Z",
  "updated_at": "2025-10-16T02:30:00Z"
}
```

#### 响应示例 (验证失败)

**HTTP 400 Bad Request**

```json
{
  "timestamp": "2025-10-16T02:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "title",
      "message": "title must not be blank"
    },
    {
      "field": "severity",
      "message": "severity must be one of: CRITICAL, HIGH, MEDIUM, LOW, INFO"
    }
  ]
}
```

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 201 | 告警创建成功 |
| 400 | 请求参数验证失败 |
| 409 | 告警已存在 (重复创建) |
| 500 | 服务器内部错误 |

---

### 5.2 获取告警详情

**描述**: 根据告警ID获取完整的告警信息,包括处理历史和审计记录。

**端点**: `GET /api/v1/alerts/{id}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

#### 请求示例 (curl)

```bash
curl -X GET http://localhost:8082/api/v1/alerts/12345
```

#### 请求示例 (Java)

```java
/**
 * 获取告警详情
 */
public Alert getAlertById(Long alertId) {
    String url = BASE_URL + "/" + alertId;
    
    try {
        Alert alert = restTemplate.getForObject(url, Alert.class);
        
        System.out.println("Alert ID: " + alert.getId());
        System.out.println("Severity: " + alert.getSeverity());
        System.out.println("Status: " + alert.getStatus());
        System.out.println("Threat Score: " + alert.getThreatScore());
        
        return alert;
        
    } catch (HttpClientErrorException.NotFound e) {
        System.err.println("Alert not found: " + alertId);
        return null;
    }
}
```

#### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到深夜横向移动攻击",
  "description": "检测到IP 192.168.75.188 在凌晨2:30访问5个诱饵IP的3个端口,疑似APT横向移动",
  "severity": "CRITICAL",
  "status": "IN_PROGRESS",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "threat_score": 7290.0,
  "customer_id": "customer_a",
  "assessmentId": "ASSESS-12345",
  "source": "threat-assessment-service",
  "assigned_to": "security-analyst@company.com",
  "resolved_by": null,
  "resolution": null,
  "resolved_at": null,
  "created_at": "2025-10-16T02:30:00Z",
  "updated_at": "2025-10-16T03:00:00Z"
}
```

#### 响应示例 (未找到)

**HTTP 404 Not Found**

```json
{
  "timestamp": "2025-10-16T02:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Alert with ID 99999 not found"
}
```

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 查询成功 |
| 404 | 告警不存在 |
| 403 | 无权访问 (跨租户) |
| 500 | 服务器内部错误 |

---

### 5.3 查询告警列表

```bash
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "CRITICAL: 检测到横向移动攻击",
    "severity": "CRITICAL",
    "attack_mac": "04:42:1a:8e:e3:65",
    "attack_ip": "192.168.75.188",
    "threat_score": 7290.0,
    "customer_id": "customer_a"
  }'
```

### 请求示例 (Java)

```java
public Alert createAlert(String customerId, String attackMac, 
                        String attackIp, double threatScore) {
    Alert alert = new Alert();
    alert.setTitle("CRITICAL: 检测到横向移动攻击");
    alert.setSeverity(AlertSeverity.CRITICAL);
    alert.setAttackMac(attackMac);
    alert.setAttackIp(attackIp);
    alert.setThreatScore(threatScore);
    alert.setCustomerId(customerId);
    alert.setStatus(AlertStatus.OPEN);
    
    HttpEntity<Alert> request = new HttpEntity<>(alert);
    
    ResponseEntity<Alert> response = restTemplate.postForEntity(
        baseUrl + "/alerts",
        request,
        Alert.class
    );
    
    return response.getBody();
}
```

### 响应示例

```json
{
  "id": 12345,
  "title": "CRITICAL: 检测到横向移动攻击",
  "severity": "CRITICAL",
  "status": "OPEN",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "threat_score": 7290.0,
  "customer_id": "customer_a",
  "created_at": "2025-01-15T02:30:00Z"
}
```

---

## 获取告警详情

**端点**: `GET /api/v1/alerts/{id}`

### 请求示例

```bash
curl -X GET http://localhost:8082/api/v1/alerts/12345
```

```java
public Alert getAlert(Long id) {
    return restTemplate.getForObject(
        baseUrl + "/alerts/" + id,
        Alert.class
    );
}
```

---

### 5.3 查询告警列表

**描述**: 分页查询告警列表,支持多维度过滤、排序和全文搜索。

**端点**: `GET /api/v1/alerts`

#### 查询参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `customer_id` | String | ❌ | - | 客户ID (自动从上下文获取) |
| `status` | String | ❌ | - | 告警状态 (OPEN/IN_PROGRESS/RESOLVED/CLOSED) |
| `severity` | String | ❌ | - | 严重等级 (CRITICAL/HIGH/MEDIUM/LOW/INFO) |
| `attack_ip` | String | ❌ | - | 攻击源IP地址 |
| `attack_mac` | String | ❌ | - | 攻击源MAC地址 |
| `startTime` | String | ❌ | - | 开始时间 (ISO8601格式) |
| `endTime` | String | ❌ | - | 结束时间 (ISO8601格式) |
| `assigned_to` | String | ❌ | - | 责任人邮箱 |
| `page` | Integer | ❌ | 0 | 页码 (从0开始) |
| `size` | Integer | ❌ | 20 | 每页大小 (1-100) |
| `sortBy` | String | ❌ | createdAt | 排序字段 |
| `sortDir` | String | ❌ | DESC | 排序方向 (ASC/DESC) |

#### 请求示例 (curl)

**场景1: 查询CRITICAL级别的OPEN状态告警**

```bash
curl -X GET "http://localhost:8082/api/v1/alerts?severity=CRITICAL&status=OPEN&page=0&size=20&sortDir=DESC"
```

**场景2: 查询指定IP的所有告警**

```bash
curl -X GET "http://localhost:8082/api/v1/alerts?attackIp=192.168.75.188&page=0&size=50"
```

**场景3: 查询指定时间范围的HIGH级别告警**

```bash
curl -X GET "http://localhost:8082/api/v1/alerts?severity=HIGH&startTime=2025-10-15T00:00:00Z&endTime=2025-10-16T23:59:59Z&page=0&size=100"
```

**场景4: 查询分配给特定分析师的告警**

```bash
curl -X GET "http://localhost:8082/api/v1/alerts?assignedTo=analyst@company.com&status=IN_PROGRESS"
```

#### 请求示例 (Java)

```java
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 查询告警列表 (支持多条件过滤)
 */
public Page<Alert> queryAlerts(AlertQueryParams params) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL);
    
    // 添加查询参数
    if (params.getSeverity() != null) {
        builder.queryParam("severity", params.getSeverity());
    }
    if (params.getStatus() != null) {
        builder.queryParam("status", params.getStatus());
    }
    if (params.getAttackIp() != null) {
        builder.queryParam("attackIp", params.getAttackIp());
    }
    if (params.getStartTime() != null) {
        builder.queryParam("startTime", params.getStartTime());
    }
    if (params.getEndTime() != null) {
        builder.queryParam("endTime", params.getEndTime());
    }
    
    // 分页参数
    builder.queryParam("page", params.getPage());
    builder.queryParam("size", params.getSize());
    builder.queryParam("sortBy", params.getSortBy());
    builder.queryParam("sortDir", params.getSortDir());
    
    String url = builder.toUriString();
    
    ResponseEntity<RestResponsePage<Alert>> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<RestResponsePage<Alert>>() {}
    );
    
    return response.getBody();
}

/**
 * 查询CRITICAL级别的OPEN告警
 */
public List<Alert> queryCriticalOpenAlerts() {
    AlertQueryParams params = AlertQueryParams.builder()
        .severity(AlertSeverity.CRITICAL)
        .status(AlertStatus.OPEN)
        .page(0)
        .size(100)
        .sortBy("created_at")
        .sortDir("DESC")
        .build();
    
    Page<Alert> page = queryAlerts(params);
    
    System.out.println("Total elements: " + page.getTotalElements());
    System.out.println("Total pages: " + page.getTotalPages());
    
    return page.getContent();
}

/**
 * 查询参数对象
 */
@Data
@Builder
public static class AlertQueryParams {
    private AlertSeverity severity;
    private AlertStatus status;
    private String attackIp;
    private String attackMac;
    private String startTime;
    private String endTime;
    private String assignedTo;
    private int page = 0;
    private int size = 20;
    private String sortBy = "created_at";
    private String sortDir = "DESC";
}
```

#### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "content": [
    {
      "id": 12345,
      "title": "CRITICAL: 检测到深夜横向移动攻击",
      "severity": "CRITICAL",
      "status": "OPEN",
      "attack_mac": "04:42:1a:8e:e3:65",
      "attack_ip": "192.168.75.188",
      "threat_score": 7290.0,
      "customer_id": "customer_a",
      "created_at": "2025-10-16T02:30:00Z"
    },
    {
      "id": 12346,
      "title": "CRITICAL: 勒索软件行为检测",
      "severity": "CRITICAL",
      "status": "OPEN",
      "attack_mac": "11:22:33:44:55:66",
      "attack_ip": "192.168.75.200",
      "threat_score": 8500.0,
      "customer_id": "customer_a",
      "created_at": "2025-10-16T01:15:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "size": 20,
  "number": 0,
  "first": true,
  "numberOfElements": 20
}
```

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 查询成功 |
| 400 | 查询参数无效 (如size>100) |
| 500 | 服务器内部错误 |

---

### 5.4 更新告警

**描述**: 更新告警的基本信息,不包括状态变更 (状态变更请使用生命周期API)。

**端点**: `PUT /api/v1/alerts/{id}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**请求体**: `Content-Type: application/json`

#### 可更新字段

| 字段 | 类型 | 说明 |
|-----|------|------|
| `title` | String | 告警标题 |
| `description` | String | 详细描述 |
| `severity` | String | 严重等级 |

**注意**: `customerId`, `attackMac`, `attackIp`, `createdAt` 等字段不可更新

#### 请求示例 (curl)

```bash
curl -X PUT http://localhost:8082/api/v1/alerts/12345 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "CRITICAL: 确认为APT横向移动攻击",
    "description": "经过分析确认为APT攻击,攻击者使用了多种横向移动技术,建议立即隔离",
    "severity": "CRITICAL"
  }'
```

#### 请求示例 (Java)

```java
/**
 * 更新告警信息
 */
public Alert updateAlert(Long alertId, String newTitle, String newDescription) {
    String url = BASE_URL + "/" + alertId;
    
    // 构建更新请求
    Map<String, Object> updateData = new HashMap<>();
    updateData.put("title", newTitle);
    updateData.put("description", newDescription);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateData, headers);
    
    // 发送PUT请求
    ResponseEntity<Alert> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        Alert.class
    );
    
    Alert updatedAlert = response.getBody();
    System.out.println("Updated alert: " + updatedAlert.getTitle());
    
    return updatedAlert;
}
```

#### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "id": 12345,
  "title": "CRITICAL: 确认为APT横向移动攻击",
  "description": "经过分析确认为APT攻击,攻击者使用了多种横向移动技术,建议立即隔离",
  "severity": "CRITICAL",
  "status": "IN_PROGRESS",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "threat_score": 7290.0,
  "customer_id": "customer_a",
  "created_at": "2025-10-16T02:30:00Z",
  "updated_at": "2025-10-16T04:15:00Z"
}
```

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 更新成功 |
| 400 | 请求参数验证失败 |
| 404 | 告警不存在 |
| 409 | 更新冲突 (并发修改) |
| 500 | 服务器内部错误 |

---

### 5.5 删除告警

**描述**: 删除告警记录。支持软删除和硬删除。

**端点**: `DELETE /api/v1/alerts/{id}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `id` | Long | ✅ | 告警ID |

**查询参数**:

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `hard` | Boolean | ❌ | false | 是否硬删除 (物理删除) |

#### 请求示例 (curl)

**软删除 (推荐)**:
```bash
curl -X DELETE http://localhost:8082/api/v1/alerts/12345
```

**硬删除 (谨慎使用)**:
```bash
curl -X DELETE "http://localhost:8082/api/v1/alerts/12345?hard=true"
```

#### 请求示例 (Java)

```java
/**
 * 软删除告警
 */
public boolean deleteAlert(Long alertId) {
    String url = BASE_URL + "/" + alertId;
    
    try {
        restTemplate.delete(url);
        System.out.println("Alert " + alertId + " deleted successfully");
        return true;
    } catch (HttpClientErrorException.NotFound e) {
        System.err.println("Alert not found: " + alertId);
        return false;
    }
}

/**
 * 硬删除告警 (物理删除)
 */
public boolean hardDeleteAlert(Long alertId) {
    String url = BASE_URL + "/" + alertId + "?hard=true";
    
    try {
        restTemplate.delete(url);
        System.out.println("Alert " + alertId + " permanently deleted");
        return true;
    } catch (Exception e) {
        System.err.println("Failed to delete alert: " + e.getMessage());
        return false;
    }
}
```

#### 响应示例 (成功)

**HTTP 204 No Content**

(无响应体)

#### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 204 | 删除成功 |
| 404 | 告警不存在 |
| 409 | 无法删除 (如已关联其他记录) |
| 500 | 服务器内部错误 |

---

### 5.6 批量操作

#### 批量创建告警

**端点**: `POST /api/v1/alerts/batch`

**描述**: 批量创建告警,最多100条/次。

**请求体**:

```json
{
  "alerts": [
    {
      "title": "CRITICAL: 告警1",
      "severity": "CRITICAL",
      "attack_mac": "00:11:22:33:44:55",
      "attack_ip": "192.168.1.100",
      "customer_id": "customer_a"
    },
    {
      "title": "HIGH: 告警2",
      "severity": "HIGH",
      "attack_mac": "aa:bb:cc:dd:ee:ff",
      "attack_ip": "192.168.1.101",
      "customer_id": "customer_a"
    }
  ]
}
```

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8082/api/v1/alerts/batch \
  -H "Content-Type: application/json" \
  -d '{
    "alerts": [
      {
        "title": "CRITICAL: 批量告警1",
        "severity": "CRITICAL",
        "attack_mac": "00:11:22:33:44:55",
        "attack_ip": "192.168.1.100",
        "customer_id": "customer_a"
      },
      {
        "title": "HIGH: 批量告警2",
        "severity": "HIGH",
        "attack_mac": "aa:bb:cc:dd:ee:ff",
        "attack_ip": "192.168.1.101",
        "customer_id": "customer_a"
      }
    ]
  }'
```

**请求示例 (Java)**:

```java
/**
 * 批量创建告警
 */
public List<Alert> batchCreateAlerts(List<Alert> alerts) {
    String url = BASE_URL + "/batch";
    
    Map<String, List<Alert>> request = new HashMap<>();
    request.put("alerts", alerts);
    
    HttpEntity<Map<String, List<Alert>>> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<BatchAlertResponse> response = restTemplate.postForEntity(
        url,
        httpRequest,
        BatchAlertResponse.class
    );
    
    BatchAlertResponse result = response.getBody();
    System.out.println("Created: " + result.getCreatedCount());
    System.out.println("Failed: " + result.getFailedCount());
    
    return result.getCreatedAlerts();
}

@Data
public static class BatchAlertResponse {
    private int createdCount;
    private int failedCount;
    private List<Alert> createdAlerts;
    private List<String> errors;
}
```

**响应示例**:

```json
{
  "createdCount": 2,
  "failedCount": 0,
  "createdAlerts": [
    {
      "id": 12345,
      "title": "CRITICAL: 批量告警1",
      "created_at": "2025-10-16T02:30:00Z"
    },
    {
      "id": 12346,
      "title": "HIGH: 批量告警2",
      "created_at": "2025-10-16T02:30:01Z"
    }
  ],
  "errors": []
}
```

---

## 6. 使用场景

### 场景1: 自动告警创建 (Stream Processing → Alert Management)

**背景**: Flink流处理引擎检测到威胁后自动创建告警

**完整流程**:

```java
/**
 * 威胁评估服务 - 自动创建告警
 */
@Service
@Slf4j
public class ThreatAssessmentService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String ALERT_API_URL = "http://alert-management:8082/api/v1/alerts";
    
    /**
     * 处理威胁评估结果
     */
    public void processThreatAssessment(ThreatAlert threatAlert) {
        log.info("Processing threat assessment: customerId={}, attackMac={}, score={}", 
                 threatAlert.getCustomerId(), 
                 threatAlert.getAttackMac(), 
                 threatAlert.getThreatScore());
        
        // 1. 只处理HIGH和CRITICAL级别的威胁
        if (threatAlert.getThreatLevel() == ThreatLevel.HIGH || 
            threatAlert.getThreatLevel() == ThreatLevel.CRITICAL) {
            
            // 2. 构建告警请求
            Alert alert = buildAlert(threatAlert);
            
            // 3. 调用告警管理API
            try {
                ResponseEntity<Alert> response = restTemplate.postForEntity(
                    ALERT_API_URL,
                    alert,
                    Alert.class
                );
                
                Alert createdAlert = response.getBody();
                log.info("Alert created successfully: id={}, title={}", 
                         createdAlert.getId(), 
                         createdAlert.getTitle());
                
                // 4. 如果是CRITICAL级别,立即发送邮件通知
                if (threatAlert.getThreatLevel() == ThreatLevel.CRITICAL) {
                    sendCriticalAlertNotification(createdAlert);
                }
                
            } catch (Exception e) {
                log.error("Failed to create alert: customerId={}, error={}", 
                          threatAlert.getCustomerId(), 
                          e.getMessage(), 
                          e);
            }
        }
    }
    
    private Alert buildAlert(ThreatAlert threatAlert) {
        String title = String.format("%s: 检测到%s攻击行为", 
                                     threatAlert.getThreatLevel(), 
                                     getAttackType(threatAlert));
        
        return Alert.builder()
            .title(title)
            .severity(AlertSeverity.valueOf(threatAlert.getThreatLevel().name()))
            .attackMac(threatAlert.getAttackMac())
            .attackIp(threatAlert.getAttackIp())
            .threatScore(threatAlert.getThreatScore())
            .customerId(threatAlert.getCustomerId())
            .build();
    }
}
```

---

### 场景2: 安全分析师工作流

**背景**: 安全分析师处理每日告警

```java
/**
 * 安全分析师工作流
 */
public class SecurityAnalystWorkflow {
    
    private AlertCrudClient alertClient;
    
    public void dailyAlertProcessing() {
        // 1. 查询所有CRITICAL级别的OPEN告警
        List<Alert> criticalAlerts = alertClient.queryCriticalOpenAlerts();
        System.out.println("发现 " + criticalAlerts.size() + " 条CRITICAL告警");
        
        // 2. 逐条处理告警
        for (Alert alert : criticalAlerts) {
            Alert detailAlert = alertClient.getAlertDetails(alert.getId());
            
            // 3. 分析攻击类型
            String attackType = analyzeAttack(detailAlert);
            
            // 4. 更新告警描述
            String updatedDescription = detailAlert.getDescription() + 
                "\n\n【分析师备注】攻击类型: " + attackType;
            
            alertClient.updateAlert(alert.getId(), 
                                   detailAlert.getTitle(), 
                                   updatedDescription);
        }
    }
}
```

---

### 场景3: 批量告警导入

**背景**: 从旧系统迁移历史告警数据

```java
/**
 * 历史告警数据迁移工具
 */
public class AlertMigrationTool {
    
    private AlertCrudClient alertClient;
    private static final int BATCH_SIZE = 100;
    
    public void importAlertsFromCsv(String csvFilePath) {
        List<Alert> batch = new ArrayList<>();
        int totalProcessed = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            reader.readLine(); // 跳过标题行
            
            while ((line = reader.readLine()) != null) {
                Alert alert = parseCsvLine(line);
                batch.add(alert);
                
                if (batch.size() >= BATCH_SIZE) {
                    int created = batchCreateAlerts(batch);
                    totalProcessed += batch.size();
                    
                    System.out.println(String.format(
                        "已处理 %d 条,成功创建 %d 条", 
                        totalProcessed, 
                        created
                    ));
                    
                    batch.clear();
                }
            }
            
            if (!batch.isEmpty()) {
                batchCreateAlerts(batch);
            }
            
        } catch (Exception e) {
            System.err.println("导入失败: " + e.getMessage());
        }
    }
}
```

---

## 7. Java客户端完整示例

```java
package com.threatdetection.client;

import lombok.Data;
import lombok.Builder;
import org.springframework.web.client.RestTemplate;

/**
 * 告警CRUD客户端 - 完整实现
 */
public class AlertCrudClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate;
    
    public AlertCrudClient() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 创建CRITICAL级别告警
     */
    public Alert createCriticalAlert(String customerId, String attackMac, 
                                     String attackIp, double threatScore) {
        Alert alert = Alert.builder()
            .title("CRITICAL: 检测到严重威胁行为")
            .severity(AlertSeverity.CRITICAL)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .threatScore(threatScore)
            .customerId(customerId)
            .build();
        
        return createAlert(alert);
    }
    
    /**
     * 获取告警详情
     */
    public Alert getAlertDetails(Long alertId) {
        String url = BASE_URL + "/" + alertId;
        return restTemplate.getForObject(url, Alert.class);
    }
    
    /**
     * 查询CRITICAL级别的OPEN告警
     */
    public List<Alert> queryCriticalOpenAlerts() {
        AlertQueryParams params = AlertQueryParams.builder()
            .severity(AlertSeverity.CRITICAL)
            .status(AlertStatus.OPEN)
            .page(0)
            .size(100)
            .build();
        
        Page<Alert> page = queryAlerts(params);
        return page.getContent();
    }
    
    /**
     * 更新告警信息
     */
    public Alert updateAlert(Long alertId, String newTitle, String newDescription) {
        String url = BASE_URL + "/" + alertId;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", newTitle);
        updateData.put("description", newDescription);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateData);
        
        return restTemplate.exchange(
            url,
            HttpMethod.PUT,
            request,
            Alert.class
        ).getBody();
    }
    
    /**
     * 软删除告警
     */
    public boolean deleteAlert(Long alertId) {
        String url = BASE_URL + "/" + alertId;
        
        try {
            restTemplate.delete(url);
            return true;
        } catch (Exception e) {
            System.err.println("删除失败: " + e.getMessage());
            return false;
        }
    }
}
```

---

## 8. 最佳实践

### ✅ 推荐做法

#### 1. 分页查询大量数据

```java
// ✅ 推荐: 使用分页查询
AlertQueryParams params = AlertQueryParams.builder()
    .page(0)
    .size(20)  // 每页20条
    .build();

Page<Alert> page = client.queryAlerts(params);
```

```java
// ❌ 不推荐: 一次查询所有数据
AlertQueryParams params = AlertQueryParams.builder()
    .size(10000)  // 过大的size会导致性能问题
    .build();
```

#### 2. 使用过滤条件减少数据量

```java
// ✅ 推荐: 使用精确过滤条件
AlertQueryParams params = AlertQueryParams.builder()
    .severity(AlertSeverity.CRITICAL)
    .status(AlertStatus.OPEN)
    .startTime("2025-10-15T00:00:00Z")
    .endTime("2025-10-16T23:59:59Z")
    .build();
```

#### 3. 批量操作提高效率

```java
// ✅ 推荐: 批量创建告警
List<Alert> alerts = prepareAlerts();
client.batchCreateAlerts(alerts);  // 一次批量提交
```

```java
// ❌ 不推荐: 循环单个创建
for (Alert alert : alerts) {
    client.createAlert(alert);  // 多次HTTP请求
}
```

---

## 9. 故障排查

### 问题1: 告警创建失败 (HTTP 400)

**症状**:
```
org.springframework.web.client.HttpClientErrorException$BadRequest: 400 Bad Request
```

**排查步骤**:

```bash
# 检查请求体
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "TEST",
    "severity": "CRITICAL",
    "attack_mac": "invalid-mac",
    "attack_ip": "192.168.1.100",
    "customer_id": "customer_a"
  }' -v
```

**常见原因**:

| 原因 | 说明 | 解决方案 |
|-----|------|---------|
| MAC格式错误 | 必须是 `xx:xx:xx:xx:xx:xx` 格式 | 使用正则验证 |
| IP格式错误 | 必须是有效的IPv4地址 | 使用正则验证 |
| customerId为空 | customerId是必需字段 | 确保请求中包含customerId |

---

### 问题2: 告警查询不到 (HTTP 404)

**症状**:
```
Alert not found: 12345
```

**排查步骤**:

```bash
# 检查数据库
psql -U threat_user -d threat_detection -c \
  "SELECT id, title, customer_id FROM alerts WHERE id = 12345;"
```

**常见原因**:
- 告警不存在或已删除
- 跨租户查询被拒绝

---

### 问题3: 查询性能慢

**优化建议**:

```sql
-- 添加复合索引
CREATE INDEX idx_alerts_customer_created ON alerts(customer_id, created_at DESC);
CREATE INDEX idx_alerts_severity_status ON alerts(severity, status);
```

---

## 10. 相关文档

- [告警生命周期API](./alert_lifecycle_api.md) - 告警状态管理和工作流
- [邮件通知配置API](./email_notification_configuration.md) - 告警通知规则配置
- [威胁评估查询API](./threat_assessment_query_api.md) - 威胁评估数据查询
- [数据模型文档](../data_structures.md) - 完整数据结构定义

---

**文档结束**

*最后更新: 2025-10-16*  
*版本: 1.0*  
*维护团队: Security Platform Team*
