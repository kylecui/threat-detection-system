# 告警升级API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## API列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `POST` | `/api/v1/alerts/{id}/escalate` | 手动升级告警 |
| `POST` | `/api/v1/alerts/{id}/cancel-escalation` | 取消升级 |

---

## 手动升级告警

**端点**: `POST /api/v1/alerts/{id}/escalate`

### 升级场景

| 场景 | 说明 |
|------|------|
| **处理超时** | 告警长时间未响应 |
| **威胁升级** | 攻击范围扩大 |
| **手动升级** | 需要更高级别处理 |

### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/escalate \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "攻击范围扩大,影响多个网段"
  }'
```

```java
public void escalateAlert(Long id, String reason) {
    EscalateAlertRequest request = new EscalateAlertRequest();
    request.setReason(reason);
    
    HttpEntity<EscalateAlertRequest> httpRequest = new HttpEntity<>(request);
    
    restTemplate.postForEntity(
        baseUrl + "/alerts/" + id + "/escalate",
        httpRequest,
        Void.class
    );
}
```

### 响应

**HTTP 200 OK** (无响应体)

---

## 取消升级

**端点**: `POST /api/v1/alerts/{id}/cancel-escalation`

### 请求示例

```bash
curl -X POST http://localhost:8082/api/v1/alerts/12345/cancel-escalation
```

```java
public void cancelEscalation(Long id) {
    restTemplate.postForEntity(
        baseUrl + "/alerts/" + id + "/cancel-escalation",
        null,
        Void.class
    );
}
```

### 响应

**HTTP 200 OK** (无响应体)

---

## 使用场景

### 自动升级策略

```java
public class AutoEscalationService {
    
    /**
     * 检查是否需要自动升级
     */
    public void checkAndEscalate(Alert alert) {
        // 策略1: CRITICAL告警超过15分钟未处理
        if (AlertSeverity.CRITICAL == alert.getSeverity() 
            && alert.getStatus() == AlertStatus.OPEN
            && isOlderThan(alert, 15, ChronoUnit.MINUTES)) {
            
            escalateAlert(alert.getId(), "CRITICAL告警超时未处理");
        }
        
        // 策略2: HIGH告警超过1小时未处理
        if (AlertSeverity.HIGH == alert.getSeverity()
            && alert.getStatus() == AlertStatus.OPEN
            && isOlderThan(alert, 1, ChronoUnit.HOURS)) {
            
            escalateAlert(alert.getId(), "HIGH告警超时未处理");
        }
    }
    
    private boolean isOlderThan(Alert alert, long amount, ChronoUnit unit) {
        Instant threshold = Instant.now().minus(amount, unit);
        return alert.getCreatedAt().isBefore(threshold);
    }
}
```

---

**相关文档**: [生命周期API](./alert_lifecycle_api.md) | [分析API](./alert_analytics_api.md)

---

**文档结束**
