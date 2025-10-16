# 告警分析API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## API列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `GET` | `/api/v1/alerts/analytics` | 获取告警统计 |
| `GET` | `/api/v1/alerts/notifications/analytics` | 获取通知统计 |
| `GET` | `/api/v1/alerts/escalations/analytics` | 获取升级统计 |

---

## 获取告警统计

**端点**: `GET /api/v1/alerts/analytics`

### 请求示例

```bash
curl -X GET http://localhost:8082/api/v1/alerts/analytics
```

```java
public AlertStatistics getAlertAnalytics() {
    return restTemplate.getForObject(
        baseUrl + "/alerts/analytics",
        AlertStatistics.class
    );
}
```

### 响应示例

```json
{
  "totalAlerts": 1250,
  "openAlerts": 35,
  "inProgressAlerts": 18,
  "resolvedAlerts": 1180,
  "escalatedAlerts": 12,
  "severityDistribution": {
    "CRITICAL": 45,
    "HIGH": 180,
    "MEDIUM": 520,
    "LOW": 450,
    "INFO": 55
  },
  "averageResolutionTime": "2.5 hours",
  "criticalAlertRate": 3.6
}
```

---

## 获取通知统计

**端点**: `GET /api/v1/alerts/notifications/analytics`

### 请求示例

```bash
curl -X GET http://localhost:8082/api/v1/alerts/notifications/analytics
```

```java
public NotificationStatistics getNotificationAnalytics() {
    return restTemplate.getForObject(
        baseUrl + "/alerts/notifications/analytics",
        NotificationStatistics.class
    );
}
```

### 响应示例

```json
{
  "totalNotifications": 5680,
  "sentNotifications": 5650,
  "failedNotifications": 30,
  "successRate": 99.5,
  "channelDistribution": {
    "EMAIL": 5200,
    "SMS": 320,
    "SLACK": 120,
    "WEBHOOK": 40
  },
  "averageSendTime": "1.2 seconds"
}
```

---

## 获取升级统计

**端点**: `GET /api/v1/alerts/escalations/analytics`

### 请求示例

```bash
curl -X GET http://localhost:8082/api/v1/alerts/escalations/analytics
```

```java
public EscalationStatistics getEscalationAnalytics() {
    return restTemplate.getForObject(
        baseUrl + "/alerts/escalations/analytics",
        EscalationStatistics.class
    );
}
```

### 响应示例

```json
{
  "totalEscalations": 125,
  "autoEscalations": 98,
  "manualEscalations": 27,
  "cancelledEscalations": 15,
  "escalationRate": 10.0,
  "averageEscalationTime": "45 minutes"
}
```

---

## 使用场景

### 生成监控仪表板

```java
public class MonitoringDashboard {
    
    public void generateDashboard() {
        // 获取所有统计数据
        AlertStatistics alertStats = client.getAlertAnalytics();
        NotificationStatistics notifStats = client.getNotificationAnalytics();
        EscalationStatistics escalStats = client.getEscalationAnalytics();
        
        // 显示关键指标
        System.out.println("=== 告警系统仪表板 ===");
        System.out.println("总告警: " + alertStats.getTotalAlerts());
        System.out.println("待处理: " + alertStats.getOpenAlerts());
        System.out.println("CRITICAL: " + alertStats.getSeverityDistribution().get("CRITICAL"));
        System.out.println("通知成功率: " + notifStats.getSuccessRate() + "%");
        System.out.println("升级率: " + escalStats.getEscalationRate() + "%");
    }
}
```

---

**相关文档**: [告警概述](./alert_management_overview.md) | [通知API](./alert_notification_api.md)

---

**文档结束**
