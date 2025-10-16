# 告警CRUD API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## API列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `POST` | `/api/v1/alerts` | 创建告警 |
| `GET` | `/api/v1/alerts/{id}` | 获取告警详情 |
| `GET` | `/api/v1/alerts` | 查询告警列表 |

---

## 创建告警

**端点**: `POST /api/v1/alerts`

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "CRITICAL: 检测到横向移动攻击",
    "severity": "CRITICAL",
    "attackMac": "04:42:1a:8e:e3:65",
    "attackIp": "192.168.75.188",
    "threatScore": 7290.0,
    "customerId": "customer_a"
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
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "threatScore": 7290.0,
  "customerId": "customer_a",
  "createdAt": "2025-01-15T02:30:00Z"
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

## 查询告警列表

**端点**: `GET /api/v1/alerts`

### 查询参数

| 参数 | 类型 | 说明 |
|-----|------|------|
| `status` | String | 告警状态 (OPEN/IN_PROGRESS/RESOLVED) |
| `severity` | String | 严重等级 (CRITICAL/HIGH/MEDIUM/LOW) |
| `startTime` | String | 开始时间 (ISO8601) |
| `endTime` | String | 结束时间 (ISO8601) |
| `page` | Integer | 页码 (从0开始) |
| `size` | Integer | 每页大小 (1-100) |
| `sortBy` | String | 排序字段 (默认: createdAt) |
| `sortDir` | String | 排序方向 (ASC/DESC) |

### 请求示例

```bash
# 查询CRITICAL级别的OPEN状态告警
curl -X GET "http://localhost:8082/api/v1/alerts?severity=CRITICAL&status=OPEN&page=0&size=20&sortDir=DESC"
```

```java
public Page<Alert> queryAlerts(AlertSeverity severity, AlertStatus status) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts")
        .queryParam("severity", severity)
        .queryParam("status", status)
        .queryParam("page", 0)
        .queryParam("size", 20)
        .queryParam("sortDir", "DESC")
        .toUriString();
    
    return restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<RestResponsePage<Alert>>() {}
    ).getBody();
}
```

### 响应示例

```json
{
  "content": [
    {
      "id": 12345,
      "title": "CRITICAL: 横向移动攻击",
      "severity": "CRITICAL",
      "status": "OPEN",
      "createdAt": "2025-01-15T02:30:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

---

**相关文档**: [告警概述](./alert_management_overview.md) | [生命周期API](./alert_lifecycle_api.md)

---

**文档结束**
