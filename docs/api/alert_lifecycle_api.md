# 告警生命周期API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## API列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `PUT` | `/api/v1/alerts/{id}/status` | 更新告警状态 |
| `POST` | `/api/v1/alerts/{id}/resolve` | 解决告警 |
| `POST` | `/api/v1/alerts/{id}/assign` | 分配告警 |
| `POST` | `/api/v1/alerts/archive` | 归档旧告警 |

---

## 更新告警状态

**端点**: `PUT /api/v1/alerts/{id}/status`

### 请求示例

```bash
curl -X PUT "http://localhost:8082/api/v1/alerts/12345/status?status=IN_PROGRESS"
```

```java
public Alert updateStatus(Long id, AlertStatus status) {
    String url = baseUrl + "/alerts/" + id + "/status?status=" + status;
    
    HttpEntity<Void> request = new HttpEntity<>(null);
    
    ResponseEntity<Alert> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        Alert.class
    );
    
    return response.getBody();
}
```

### 可用状态

| 状态 | 说明 |
|------|------|
| `OPEN` | 新创建,待处理 |
| `IN_PROGRESS` | 处理中 |
| `RESOLVED` | 已解决 |
| `CLOSED` | 已关闭 |
| `ESCALATED` | 已升级 |

---

## 解决告警

**端点**: `POST /api/v1/alerts/{id}/resolve`

### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/resolve \
  -H "Content-Type: application/json" \
  -d '{
    "resolution": "已隔离攻击源并清除恶意软件",
    "resolvedBy": "security-team@company.com"
  }'
```

```java
public Alert resolveAlert(Long id, String resolution, String resolvedBy) {
    ResolveAlertRequest request = new ResolveAlertRequest();
    request.setResolution(resolution);
    request.setResolvedBy(resolvedBy);
    
    HttpEntity<ResolveAlertRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<Alert> response = restTemplate.postForEntity(
        baseUrl + "/alerts/" + id + "/resolve",
        httpRequest,
        Alert.class
    );
    
    return response.getBody();
}
```

---

## 分配告警

**端点**: `POST /api/v1/alerts/{id}/assign`

### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/assign \
  -H "Content-Type: application/json" \
  -d '{
    "assignedTo": "security-analyst@company.com"
  }'
```

```java
public Alert assignAlert(Long id, String assignedTo) {
    AssignAlertRequest request = new AssignAlertRequest();
    request.setAssignedTo(assignedTo);
    
    HttpEntity<AssignAlertRequest> httpRequest = new HttpEntity<>(request);
    
    ResponseEntity<Alert> response = restTemplate.postForEntity(
        baseUrl + "/alerts/" + id + "/assign",
        httpRequest,
        Alert.class
    );
    
    return response.getBody();
}
```

---

## 归档旧告警

**端点**: `POST /api/v1/alerts/archive`

### 请求示例

```bash
# 归档30天前的已解决告警
curl -X POST "http://localhost:8082/api/v1/alerts/archive?daysOld=30"
```

```java
public int archiveOldAlerts(int daysOld) {
    String url = baseUrl + "/alerts/archive?daysOld=" + daysOld;
    
    ResponseEntity<Map<String, Integer>> response = restTemplate.postForEntity(
        url,
        null,
        new ParameterizedTypeReference<Map<String, Integer>>() {}
    );
    
    return response.getBody().get("archivedCount");
}
```

### 响应示例

```json
{
  "archivedCount": 125
}
```

---

**相关文档**: [告警CRUD](./alert_crud_api.md) | [升级API](./alert_escalation_api.md)

---

**文档结束**
