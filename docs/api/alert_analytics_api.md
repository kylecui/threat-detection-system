# 告警分析API文档

**服务**: Alert Management Service  
**端口**: 8082  
**基础路径**: `/api/v1/alerts/analytics`  
**认证**: Bearer Token  
**版本**: v1

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [分析维度](#3-分析维度)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

### 1.1 核心功能

告警分析API提供多维度的数据分析能力,支持:

- **实时统计**: 告警数量、状态分布、严重性分析
- **趋势分析**: 时间序列、增长率、同比环比
- **性能指标**: 响应时间、处理时长、SLA达成率
- **通知分析**: 多渠道发送统计、成功率监控
- **升级分析**: 自动/手动升级比例、升级触发因素
- **攻击者画像**: TOP攻击源、攻击模式识别
- **导出功能**: CSV/Excel/PDF报表导出

### 1.2 分析流程

```
告警数据 → 多维聚合 → 统计计算 → 趋势分析 → 可视化 → 报表导出
   ↓           ↓          ↓          ↓         ↓         ↓
PostgreSQL  时间窗口   指标计算   时序对比   JSON API   文件下载
```

### 1.3 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| **数据库** | PostgreSQL 15+ | 原始数据存储 |
| **聚合引擎** | JPA Criteria API | 动态查询构建 |
| **时序分析** | Spring Data JPA | 时间窗口查询 |
| **报表生成** | Apache POI / iText | Excel/PDF导出 |
| **缓存** | Redis | 热点数据缓存 |
| **调度** | Spring @Scheduled | 定期统计任务 |

---

## 2. 核心功能

### 2.1 实时告警统计

**指标**:
- 总告警数 (totalAlerts)
- 各状态告警数 (OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED)
- 严重性分布 (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- 平均解决时长 (averageResolutionTime)
- SLA达成率 (slaComplianceRate)

**时间范围**: 支持今天、本周、本月、本季度、本年度、自定义

### 2.2 趋势分析

**分析维度**:
- **时间序列**: 按小时/天/周/月聚合
- **增长率**: 环比增长率、同比增长率
- **峰值检测**: 识别告警高峰时段
- **异常检测**: 告警数量异常波动

**输出格式**: 时间点数组 + 对应数值

### 2.3 性能分析

**关键指标**:
- 平均首次响应时间 (MTTD - Mean Time To Detect)
- 平均解决时间 (MTTR - Mean Time To Resolve)
- 平均升级时间
- 处理中告警平均停留时长

### 2.4 通知效能分析

**统计维度**:
- 通道分布 (Email, SMS, Webhook, 企业工具)
- 发送成功率
- 失败原因分析
- 平均发送延迟

### 2.5 攻击者分析

**分析内容**:
- TOP 10 攻击源MAC/IP
- 攻击频率排名
- 攻击目标端口分布
- 威胁评分排名

---

## 3. 分析维度

### 3.1 时间维度

| 维度 | 描述 | 聚合粒度 |
|------|------|---------|
| **实时** | 当前时刻 | 实时查询 |
| **今日** | 00:00 至今 | 按小时 |
| **本周** | 周一至今 | 按天 |
| **本月** | 月初至今 | 按天 |
| **本季度** | 季度初至今 | 按周 |
| **本年度** | 年初至今 | 按月 |
| **自定义** | 指定时间范围 | 自适应 |

### 3.2 业务维度

- **客户维度**: 多租户隔离,按customerId分析
- **严重性维度**: 按告警等级分组统计
- **状态维度**: 按生命周期状态分析
- **来源维度**: 按攻击源IP/MAC分析
- **设备维度**: 按deviceSerial分析

### 3.3 性能维度

- **响应时效**: 创建→首次响应耗时
- **处理时效**: 创建→处理中耗时
- **解决时效**: 创建→解决耗时
- **升级时效**: 创建→升级耗时

---

## 4. 数据模型

### 4.1 统计实体类

#### AlertStatistics (告警统计)

```java
@Data
@Builder
public class AlertStatistics {
    private String customerId;
    private Long totalAlerts;
    private Long openAlerts;
    private Long inProgressAlerts;
    private Long resolvedAlerts;
    private Long closedAlerts;
    private Long cancelledAlerts;
    
    private Map<String, Long> severityDistribution;
    private Map<String, Long> statusDistribution;
    
    private Double averageResolutionTimeMinutes;
    private Double averageThreatScore;
    private Double criticalAlertPercentage;
    private Double slaComplianceRate;
    
    private LocalDateTime calculatedAt;
}
```

#### TrendAnalysis (趋势分析)

```java
@Data
@Builder
public class TrendAnalysis {
    private String customerId;
    private String dimension;  // HOURLY, DAILY, WEEKLY, MONTHLY
    private LocalDate startDate;
    private LocalDate endDate;
    
    private List<TimeSeriesDataPoint> timeSeries;
    private Double growthRate;        // 增长率
    private Double peakValue;         // 峰值
    private LocalDateTime peakTime;   // 峰值时间
}

@Data
@Builder
public class TimeSeriesDataPoint {
    private LocalDateTime timestamp;
    private Long alertCount;
    private Double averageThreatScore;
}
```

#### NotificationAnalytics (通知分析)

```java
@Data
@Builder
public class NotificationAnalytics {
    private String customerId;
    private Long totalNotifications;
    private Long sentNotifications;
    private Long failedNotifications;
    private Double successRate;
    
    private Map<String, Long> channelDistribution;  // EMAIL, SMS, WEBHOOK
    private Map<String, Double> channelSuccessRate;
    
    private Double averageSendTimeSeconds;
    private Long retriedNotifications;
}
```

#### EscalationAnalytics (升级分析)

```java
@Data
@Builder
public class EscalationAnalytics {
    private String customerId;
    private Long totalEscalations;
    private Long autoEscalations;
    private Long manualEscalations;
    private Long cancelledEscalations;
    
    private Double escalationRate;  // 升级率 = 升级数/总告警数
    private Double averageEscalationTimeMinutes;
    
    private Map<String, Long> escalationReasons;  // SLA_BREACH, SEVERITY_UPGRADE
}
```

#### AttackerProfile (攻击者画像)

```java
@Data
@Builder
public class AttackerProfile {
    private String customerId;
    private String attackMac;
    private String attackIp;
    
    private Long totalAlerts;
    private Long criticalAlerts;
    private Double averageThreatScore;
    
    private Set<Integer> targetPorts;
    private Set<String> targetIps;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
}
```

### 4.2 请求DTO

#### AnalyticsRequest

```java
@Data
public class AnalyticsRequest {
    private String customerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String dimension;  // HOURLY, DAILY, WEEKLY, MONTHLY
    private List<String> severities;
    private List<String> statuses;
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 认证 |
|------|------|------|------|
| `GET` | `/api/v1/alerts/analytics/summary` | 获取告警统计摘要 | ✅ |
| `GET` | `/api/v1/alerts/analytics/trend` | 获取趋势分析 | ✅ |
| `GET` | `/api/v1/alerts/analytics/performance` | 获取性能指标 | ✅ |
| `GET` | `/api/v1/alerts/analytics/notifications` | 获取通知统计 | ✅ |
| `GET` | `/api/v1/alerts/analytics/escalations` | 获取升级统计 | ✅ |
| `GET` | `/api/v1/alerts/analytics/attackers/top` | 获取TOP攻击者 | ✅ |
| `GET` | `/api/v1/alerts/analytics/export` | 导出统计报表 | ✅ |

---

---

## 6. API详细文档

### 6.1 获取告警统计摘要

**端点**: `GET /api/v1/alerts/analytics/summary`

**查询参数**:
- `customerId` (必需): 客户ID
- `startTime` (可选): 开始时间,默认今日00:00
- `endTime` (可选): 结束时间,默认当前时刻
- `severities` (可选): 严重性过滤,逗号分隔

**请求示例**:

```bash
# 获取今日所有告警统计
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/summary?customerId=customer-001" \
  -H "Authorization: Bearer ${TOKEN}"

# 获取本月CRITICAL和HIGH告警统计
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/summary?customerId=customer-001&startTime=2024-01-01T00:00:00Z&severities=CRITICAL,HIGH" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public AlertStatistics getSummary(String customerId, LocalDateTime start, LocalDateTime end) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts/analytics/summary")
        .queryParam("customer_id", customerId)
        .queryParam("startTime", start.toString())
        .queryParam("endTime", end.toString())
        .toUriString();
    
    return restTemplate.getForObject(url, AlertStatistics.class);
}
```

**响应示例**:

```json
{
  "customer_id": "customer-001",
  "totalAlerts": 1250,
  "openAlerts": 35,
  "inProgressAlerts": 18,
  "resolvedAlerts": 1150,
  "closedAlerts": 42,
  "cancelledAlerts": 5,
  "severityDistribution": {
    "CRITICAL": 45,
    "HIGH": 180,
    "MEDIUM": 520,
    "LOW": 450,
    "INFO": 55
  },
  "statusDistribution": {
    "OPEN": 35,
    "IN_PROGRESS": 18,
    "RESOLVED": 1150,
    "CLOSED": 42,
    "CANCELLED": 5
  },
  "averageResolutionTimeMinutes": 152.5,
  "averageThreatScore": 87.3,
  "criticalAlertPercentage": 3.6,
  "slaComplianceRate": 95.8,
  "calculatedAt": "2024-01-15T10:30:00Z"
}
```

**状态码**:
- `200 OK`: 成功返回统计数据
- `400 Bad Request`: 参数错误
- `401 Unauthorized`: 认证失败
- `500 Internal Server Error`: 服务器错误

---

### 6.2 获取趋势分析

**端点**: `GET /api/v1/alerts/analytics/trend`

**查询参数**:
- `customerId` (必需): 客户ID
- `dimension` (必需): 时间粒度 (HOURLY, DAILY, WEEKLY, MONTHLY)
- `startDate` (必需): 开始日期
- `endDate` (必需): 结束日期

**请求示例**:

```bash
# 获取过去7天的每日趋势
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/trend?customerId=customer-001&dimension=DAILY&startDate=2024-01-08&endDate=2024-01-15" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public TrendAnalysis getDailyTrend(String customerId, LocalDate start, LocalDate end) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts/analytics/trend")
        .queryParam("customer_id", customerId)
        .queryParam("dimension", "DAILY")
        .queryParam("startDate", start.toString())
        .queryParam("endDate", end.toString())
        .toUriString();
    
    return restTemplate.getForObject(url, TrendAnalysis.class);
}
```

**响应示例**:

```json
{
  "customer_id": "customer-001",
  "dimension": "DAILY",
  "startDate": "2024-01-08",
  "endDate": "2024-01-15",
  "timeSeries": [
    {
      "timestamp": "2024-01-08T00:00:00Z",
      "alertCount": 145,
      "averageThreatScore": 82.5
    },
    {
      "timestamp": "2024-01-09T00:00:00Z",
      "alertCount": 167,
      "averageThreatScore": 89.2
    },
    {
      "timestamp": "2024-01-10T00:00:00Z",
      "alertCount": 203,
      "averageThreatScore": 95.7
    },
    {
      "timestamp": "2024-01-11T00:00:00Z",
      "alertCount": 188,
      "averageThreatScore": 91.3
    },
    {
      "timestamp": "2024-01-12T00:00:00Z",
      "alertCount": 156,
      "averageThreatScore": 85.6
    },
    {
      "timestamp": "2024-01-13T00:00:00Z",
      "alertCount": 142,
      "averageThreatScore": 80.1
    },
    {
      "timestamp": "2024-01-14T00:00:00Z",
      "alertCount": 178,
      "averageThreatScore": 88.9
    },
    {
      "timestamp": "2024-01-15T00:00:00Z",
      "alertCount": 71,
      "averageThreatScore": 83.4
    }
  ],
  "growthRate": 15.2,
  "peakValue": 203,
  "peakTime": "2024-01-10T00:00:00Z"
}
```

---

### 6.3 获取性能指标

**端点**: `GET /api/v1/alerts/analytics/performance`

**查询参数**:
- `customerId` (必需): 客户ID
- `startTime` (可选): 开始时间
- `endTime` (可选): 结束时间

**请求示例**:

```bash
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/performance?customerId=customer-001" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public PerformanceMetrics getPerformanceMetrics(String customerId) {
    String url = baseUrl + "/alerts/analytics/performance?customerId=" + customerId;
    return restTemplate.getForObject(url, PerformanceMetrics.class);
}
```

**响应示例**:

```json
{
  "customer_id": "customer-001",
  "meanTimeToDetect": 3.5,
  "meanTimeToRespond": 15.2,
  "meanTimeToResolve": 152.5,
  "meanTimeToEscalate": 45.8,
  "slaBreachCount": 12,
  "slaComplianceRate": 95.8,
  "criticalAlertResponseTime": 8.3,
  "highAlertResponseTime": 12.7,
  "mediumAlertResponseTime": 25.4,
  "lowAlertResponseTime": 48.6
}
```

---

### 6.4 获取通知统计

**端点**: `GET /api/v1/alerts/analytics/notifications`

**查询参数**:
- `customerId` (必需): 客户ID
- `startTime` (可选): 开始时间
- `endTime` (可选): 结束时间
- `channel` (可选): 通知渠道过滤

**请求示例**:

```bash
# 获取所有渠道通知统计
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/notifications?customerId=customer-001" \
  -H "Authorization: Bearer ${TOKEN}"

# 仅获取EMAIL渠道统计
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/notifications?customerId=customer-001&channel=EMAIL" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public NotificationAnalytics getNotificationStats(String customerId, String channel) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts/analytics/notifications")
        .queryParam("customer_id", customerId)
        .queryParamIfPresent("channel", Optional.ofNullable(channel))
        .toUriString();
    
    return restTemplate.getForObject(url, NotificationAnalytics.class);
}
```

**响应示例**:

```json
{
  "customer_id": "customer-001",
  "totalNotifications": 5680,
  "sentNotifications": 5650,
  "failedNotifications": 30,
  "successRate": 99.47,
  "channelDistribution": {
    "EMAIL": 5200,
    "SMS": 320,
    "WEBHOOK": 120,
    "SLACK": 40
  },
  "channelSuccessRate": {
    "EMAIL": 99.8,
    "SMS": 98.1,
    "WEBHOOK": 97.5,
    "SLACK": 100.0
  },
  "averageSendTimeSeconds": 1.23,
  "retriedNotifications": 45
}
```

---

### 6.5 获取升级统计

**端点**: `GET /api/v1/alerts/analytics/escalations`

**请求示例**:

```bash
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/escalations?customerId=customer-001" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public EscalationAnalytics getEscalationStats(String customerId) {
    String url = baseUrl + "/alerts/analytics/escalations?customerId=" + customerId;
    return restTemplate.getForObject(url, EscalationAnalytics.class);
}
```

**响应示例**:

```json
{
  "customer_id": "customer-001",
  "totalEscalations": 125,
  "autoEscalations": 98,
  "manualEscalations": 27,
  "cancelledEscalations": 15,
  "escalationRate": 10.0,
  "averageEscalationTimeMinutes": 45.3,
  "escalationReasons": {
    "SLA_BREACH": 78,
    "SEVERITY_UPGRADE": 32,
    "MANUAL_REQUEST": 15
  }
}
```

---

### 6.6 获取TOP攻击者

**端点**: `GET /api/v1/alerts/analytics/attackers/top`

**查询参数**:
- `customerId` (必需): 客户ID
- `limit` (可选): 返回数量,默认10
- `startTime` (可选): 开始时间
- `endTime` (可选): 结束时间

**请求示例**:

```bash
# 获取TOP 10攻击者
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/attackers/top?customerId=customer-001&limit=10" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public List<AttackerProfile> getTopAttackers(String customerId, int limit) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts/analytics/attackers/top")
        .queryParam("customer_id", customerId)
        .queryParam("limit", limit)
        .toUriString();
    
    return restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<List<AttackerProfile>>() {}
    ).getBody();
}
```

**响应示例**:

```json
[
  {
    "customer_id": "customer-001",
    "attack_mac": "00:11:22:33:44:55",
    "attack_ip": "192.168.1.100",
    "totalAlerts": 385,
    "criticalAlerts": 45,
    "averageThreatScore": 125.8,
    "targetPorts": [3389, 445, 22, 3306],
    "targetIps": ["10.0.0.1", "10.0.0.5", "10.0.0.12"],
    "firstSeen": "2024-01-01T08:23:00Z",
    "lastSeen": "2024-01-15T16:45:00Z"
  },
  {
    "customer_id": "customer-001",
    "attack_mac": "AA:BB:CC:DD:EE:FF",
    "attack_ip": "192.168.1.105",
    "totalAlerts": 267,
    "criticalAlerts": 32,
    "averageThreatScore": 98.3,
    "targetPorts": [445, 139, 135],
    "targetIps": ["10.0.0.3", "10.0.0.8"],
    "firstSeen": "2024-01-03T12:10:00Z",
    "lastSeen": "2024-01-15T14:22:00Z"
  }
]
```

---

### 6.7 导出统计报表

**端点**: `GET /api/v1/alerts/analytics/export`

**查询参数**:
- `customerId` (必需): 客户ID
- `format` (必需): 导出格式 (CSV, EXCEL, PDF)
- `reportType` (必需): 报表类型 (SUMMARY, TREND, PERFORMANCE, FULL)
- `startTime` (可选): 开始时间
- `endTime` (可选): 结束时间

**请求示例**:

```bash
# 导出Excel格式完整报表
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/export?customerId=customer-001&format=EXCEL&reportType=FULL" \
  -H "Authorization: Bearer ${TOKEN}" \
  --output alert_report.xlsx

# 导出PDF格式摘要报表
curl -X GET "http://localhost:8082/api/v1/alerts/analytics/export?customerId=customer-001&format=PDF&reportType=SUMMARY&startTime=2024-01-01T00:00:00Z&endTime=2024-01-31T23:59:59Z" \
  -H "Authorization: Bearer ${TOKEN}" \
  --output alert_summary.pdf
```

```java
public byte[] exportReport(String customerId, String format, String reportType) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/alerts/analytics/export")
        .queryParam("customer_id", customerId)
        .queryParam("format", format)
        .queryParam("reportType", reportType)
        .toUriString();
    
    ResponseEntity<byte[]> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        byte[].class
    );
    
    return response.getBody();
}

// 保存到文件
public void saveReport(String customerId, String filename) {
    byte[] reportData = exportReport(customerId, "EXCEL", "FULL");
    Files.write(Paths.get(filename), reportData);
}
```

**响应**:
- Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (Excel)
- Content-Type: `application/pdf` (PDF)
- Content-Type: `text/csv` (CSV)
- 文件下载流

---

## 7. 使用场景

### 场景1: 实时监控仪表板

```java
@Service
@Slf4j
public class RealTimeDashboardService {
    
    private final AlertAnalyticsClient analyticsClient;
    
    @Scheduled(fixedRate = 60000)  // 每分钟刷新
    public void refreshDashboard() {
        String customerId = "customer-001";
        
        // 获取实时统计
        AlertStatistics stats = analyticsClient.getSummary(
            customerId,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now()
        );
        
        // 获取通知状态
        NotificationAnalytics notifStats = analyticsClient.getNotificationStats(
            customerId,
            null
        );
        
        // 获取TOP攻击者
        List<AttackerProfile> topAttackers = analyticsClient.getTopAttackers(
            customerId,
            5
        );
        
        // 构建仪表板数据
        DashboardData dashboard = DashboardData.builder()
            .totalAlerts(stats.getTotalAlerts())
            .openAlerts(stats.getOpenAlerts())
            .criticalAlerts(stats.getSeverityDistribution().get("CRITICAL"))
            .notificationSuccessRate(notifStats.getSuccessRate())
            .topAttackers(topAttackers)
            .refreshedAt(LocalDateTime.now())
            .build();
        
        log.info("Dashboard refreshed: open={}, critical={}, notification_success={}%",
            dashboard.getOpenAlerts(),
            dashboard.getCriticalAlerts(),
            dashboard.getNotificationSuccessRate()
        );
        
        // 推送到前端
        webSocketTemplate.convertAndSend("/topic/dashboard", dashboard);
    }
}
```

---

### 场景2: 每日自动报表生成

```java
@Service
@Slf4j
public class DailyReportService {
    
    private final AlertAnalyticsClient analyticsClient;
    private final EmailService emailService;
    
    @Scheduled(cron = "0 0 8 * * *")  // 每天早上8点
    public void generateDailyReport() {
        String customerId = "customer-001";
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withHour(0).withMinute(0);
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0);
        
        // 获取昨日统计
        AlertStatistics yesterdayStats = analyticsClient.getSummary(
            customerId,
            yesterday,
            today
        );
        
        // 获取性能指标
        PerformanceMetrics performance = analyticsClient.getPerformanceMetrics(customerId);
        
        // 获取TOP 10攻击者
        List<AttackerProfile> topAttackers = analyticsClient.getTopAttackers(customerId, 10);
        
        // 导出Excel报表
        byte[] reportFile = analyticsClient.exportReport(
            customerId,
            "EXCEL",
            "FULL"
        );
        
        // 构建邮件内容
        String emailContent = buildReportEmail(yesterdayStats, performance, topAttackers);
        
        // 发送邮件
        emailService.sendEmailWithAttachment(
            "security-team@company.com",
            "每日安全告警报表 - " + LocalDate.now().minusDays(1),
            emailContent,
            "alert_report_" + LocalDate.now().minusDays(1) + ".xlsx",
            reportFile
        );
        
        log.info("Daily report sent: totalAlerts={}, criticalAlerts={}",
            yesterdayStats.getTotalAlerts(),
            yesterdayStats.getSeverityDistribution().get("CRITICAL")
        );
    }
    
    private String buildReportEmail(
            AlertStatistics stats,
            PerformanceMetrics perf,
            List<AttackerProfile> attackers) {
        
        return String.format("""
            <html>
            <body>
                <h2>每日安全告警报表</h2>
                <h3>告警概览</h3>
                <table border="1">
                    <tr><td>总告警数</td><td>%d</td></tr>
                    <tr><td>CRITICAL</td><td>%d</td></tr>
                    <tr><td>HIGH</td><td>%d</td></tr>
                    <tr><td>已解决</td><td>%d</td></tr>
                    <tr><td>待处理</td><td>%d</td></tr>
                </table>
                
                <h3>性能指标</h3>
                <table border="1">
                    <tr><td>平均解决时间</td><td>%.1f分钟</td></tr>
                    <tr><td>SLA达成率</td><td>%.1f%%</td></tr>
                </table>
                
                <h3>TOP 5 攻击者</h3>
                <ol>
                %s
                </ol>
                
                <p>详细数据请查看附件Excel报表</p>
            </body>
            </html>
            """,
            stats.getTotalAlerts(),
            stats.getSeverityDistribution().get("CRITICAL"),
            stats.getSeverityDistribution().get("HIGH"),
            stats.getResolvedAlerts(),
            stats.getOpenAlerts() + stats.getInProgressAlerts(),
            perf.getMeanTimeToResolve(),
            perf.getSlaComplianceRate(),
            attackers.stream()
                .limit(5)
                .map(a -> String.format("<li>%s (%s) - %d告警</li>",
                    a.getAttackMac(), a.getAttackIp(), a.getTotalAlerts()))
                .collect(Collectors.joining("\n"))
        );
    }
}
```

---

### 场景3: 告警趋势异常检测

```java
@Service
@Slf4j
public class AnomalyDetectionService {
    
    private final AlertAnalyticsClient analyticsClient;
    private final NotificationClient notificationClient;
    
    @Scheduled(fixedRate = 300000)  // 每5分钟检测一次
    public void detectAnomalies() {
        String customerId = "customer-001";
        
        // 获取过去24小时的小时级趋势
        TrendAnalysis trend = analyticsClient.getHourlyTrend(
            customerId,
            LocalDate.now().minusDays(1),
            LocalDate.now()
        );
        
        // 计算平均值和标准差
        List<Long> counts = trend.getTimeSeries().stream()
            .map(TimeSeriesDataPoint::getAlertCount)
            .collect(Collectors.toList());
        
        double average = counts.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        double stdDev = calculateStandardDeviation(counts, average);
        
        // 获取最新数据点
        TimeSeriesDataPoint latest = trend.getTimeSeries().get(
            trend.getTimeSeries().size() - 1
        );
        
        // 检测异常 (超过3个标准差)
        if (latest.getAlertCount() > average + 3 * stdDev) {
            log.warn("Alert spike detected: current={}, average={}, threshold={}",
                latest.getAlertCount(),
                average,
                average + 3 * stdDev
            );
            
            // 发送告警通知
            notificationClient.sendEmail(
                "security-ops@company.com",
                "告警数量异常峰值检测",
                String.format(
                    "当前小时告警数量(%d)超过正常水平(%.1f)的3倍标准差,请关注!",
                    latest.getAlertCount(),
                    average
                ),
                null
            );
        }
        
        // 检测告警数量骤降 (可能是监控失效)
        if (latest.getAlertCount() < average - 3 * stdDev && average > 10) {
            log.warn("Alert drop detected: current={}, average={}",
                latest.getAlertCount(),
                average
            );
            
            notificationClient.sendEmail(
                "security-ops@company.com",
                "告警数量异常下降检测",
                "告警数量显著低于正常水平,请检查监控系统是否正常运行!",
                null
            );
        }
    }
    
    private double calculateStandardDeviation(List<Long> values, double mean) {
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
}
```

---

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

## 8. Java客户端完整示例

### AlertAnalyticsClient

```java
package com.threatdetection.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 告警分析API客户端
 */
@Slf4j
@Component
public class AlertAnalyticsClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts/analytics";
    private final RestTemplate restTemplate;
    
    public AlertAnalyticsClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 获取告警统计摘要
     */
    public AlertStatistics getSummary(
            String customerId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/summary")
            .queryParam("customer_id", customerId)
            .queryParam("startTime", startTime.toString())
            .queryParam("endTime", endTime.toString())
            .toUriString();
        
        AlertStatistics stats = restTemplate.getForObject(url, AlertStatistics.class);
        
        log.info("Retrieved summary: customerId={}, totalAlerts={}, criticalAlerts={}",
            customerId,
            stats.getTotalAlerts(),
            stats.getSeverityDistribution().get("CRITICAL")
        );
        
        return stats;
    }
    
    /**
     * 获取今日统计
     */
    public AlertStatistics getTodaySummary(String customerId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();
        return getSummary(customerId, startOfDay, now);
    }
    
    /**
     * 获取本周统计
     */
    public AlertStatistics getWeeklySummary(String customerId) {
        LocalDateTime startOfWeek = LocalDateTime.now()
            .minusDays(LocalDateTime.now().getDayOfWeek().getValue() - 1)
            .withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();
        return getSummary(customerId, startOfWeek, now);
    }
    
    /**
     * 获取趋势分析
     */
    public TrendAnalysis getTrend(
            String customerId,
            String dimension,
            LocalDate startDate,
            LocalDate endDate) {
        
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/trend")
            .queryParam("customer_id", customerId)
            .queryParam("dimension", dimension)
            .queryParam("startDate", startDate.toString())
            .queryParam("endDate", endDate.toString())
            .toUriString();
        
        TrendAnalysis trend = restTemplate.getForObject(url, TrendAnalysis.class);
        
        log.info("Retrieved trend: dimension={}, dataPoints={}, growthRate={}%",
            dimension,
            trend.getTimeSeries().size(),
            trend.getGrowthRate()
        );
        
        return trend;
    }
    
    /**
     * 获取每日趋势 (过去7天)
     */
    public TrendAnalysis getDailyTrend(String customerId) {
        return getTrend(
            customerId,
            "DAILY",
            LocalDate.now().minusDays(7),
            LocalDate.now()
        );
    }
    
    /**
     * 获取每小时趋势 (过去24小时)
     */
    public TrendAnalysis getHourlyTrend(String customerId, LocalDate start, LocalDate end) {
        return getTrend(customerId, "HOURLY", start, end);
    }
    
    /**
     * 获取性能指标
     */
    public PerformanceMetrics getPerformanceMetrics(String customerId) {
        String url = BASE_URL + "/performance?customerId=" + customerId;
        
        PerformanceMetrics metrics = restTemplate.getForObject(url, PerformanceMetrics.class);
        
        log.info("Performance metrics: MTTR={} min, SLA={}%",
            metrics.getMeanTimeToResolve(),
            metrics.getSlaComplianceRate()
        );
        
        return metrics;
    }
    
    /**
     * 获取通知统计
     */
    public NotificationAnalytics getNotificationStats(String customerId, String channel) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/notifications")
            .queryParam("customer_id", customerId)
            .queryParamIfPresent("channel", Optional.ofNullable(channel))
            .toUriString();
        
        NotificationAnalytics analytics = restTemplate.getForObject(
            url,
            NotificationAnalytics.class
        );
        
        log.info("Notification stats: total={}, successRate={}%",
            analytics.getTotalNotifications(),
            analytics.getSuccessRate()
        );
        
        return analytics;
    }
    
    /**
     * 获取升级统计
     */
    public EscalationAnalytics getEscalationStats(String customerId) {
        String url = BASE_URL + "/escalations?customerId=" + customerId;
        
        EscalationAnalytics analytics = restTemplate.getForObject(
            url,
            EscalationAnalytics.class
        );
        
        log.info("Escalation stats: total={}, rate={}%, auto={}",
            analytics.getTotalEscalations(),
            analytics.getEscalationRate(),
            analytics.getAutoEscalations()
        );
        
        return analytics;
    }
    
    /**
     * 获取TOP攻击者
     */
    public List<AttackerProfile> getTopAttackers(String customerId, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/attackers/top")
            .queryParam("customer_id", customerId)
            .queryParam("limit", limit)
            .toUriString();
        
        ResponseEntity<List<AttackerProfile>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<AttackerProfile>>() {}
        );
        
        List<AttackerProfile> attackers = response.getBody();
        
        log.info("Retrieved top {} attackers", attackers.size());
        
        return attackers;
    }
    
    /**
     * 导出报表
     */
    public byte[] exportReport(
            String customerId,
            String format,
            String reportType) {
        
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/export")
            .queryParam("customer_id", customerId)
            .queryParam("format", format)
            .queryParam("reportType", reportType)
            .toUriString();
        
        ResponseEntity<byte[]> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            byte[].class
        );
        
        byte[] reportData = response.getBody();
        
        log.info("Exported report: format={}, type={}, size={} bytes",
            format,
            reportType,
            reportData.length
        );
        
        return reportData;
    }
    
    /**
     * 导出Excel报表并保存
     */
    public void exportToExcel(String customerId, String filename) throws IOException {
        byte[] reportData = exportReport(customerId, "EXCEL", "FULL");
        Files.write(Paths.get(filename), reportData);
        log.info("Report saved to: {}", filename);
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 9.1 使用缓存减少查询压力

```java
@Service
public class CachedAnalyticsService {
    
    @Cacheable(value = "analytics:summary", key = "#customerId + ':' + #date")
    public AlertStatistics getDailySummary(String customerId, LocalDate date) {
        return analyticsClient.getSummary(
            customerId,
            date.atStartOfDay(),
            date.plusDays(1).atStartOfDay()
        );
    }
}
```

#### 9.2 异步获取多个统计数据

```java
// ✅ 正确: 并行获取多个统计
@Async
public CompletableFuture<AlertStatistics> getSummaryAsync(String customerId) {
    return CompletableFuture.completedFuture(
        analyticsClient.getTodaySummary(customerId)
    );
}

@Async
public CompletableFuture<NotificationAnalytics> getNotificationStatsAsync(String customerId) {
    return CompletableFuture.completedFuture(
        analyticsClient.getNotificationStats(customerId, null)
    );
}

public DashboardData getDashboard(String customerId) {
    CompletableFuture<AlertStatistics> summaryFuture = getSummaryAsync(customerId);
    CompletableFuture<NotificationAnalytics> notifFuture = getNotificationStatsAsync(customerId);
    
    // 等待所有完成
    CompletableFuture.allOf(summaryFuture, notifFuture).join();
    
    return DashboardData.builder()
        .summary(summaryFuture.join())
        .notifications(notifFuture.join())
        .build();
}
```

#### 9.3 合理选择时间粒度

```java
// ✅ 正确: 根据时间范围选择合适的粒度
public TrendAnalysis getAdaptiveTrend(String customerId, LocalDate start, LocalDate end) {
    long days = ChronoUnit.DAYS.between(start, end);
    
    String dimension;
    if (days <= 2) {
        dimension = "HOURLY";   // 2天内用小时级
    } else if (days <= 31) {
        dimension = "DAILY";    // 31天内用天级
    } else if (days <= 180) {
        dimension = "WEEKLY";   // 半年内用周级
    } else {
        dimension = "MONTHLY";  // 半年以上用月级
    }
    
    return analyticsClient.getTrend(customerId, dimension, start, end);
}
```

---

### ❌ 避免的做法

#### 9.4 避免频繁查询大时间范围

```java
// ❌ 错误: 每次都查询全年数据
@Scheduled(fixedRate = 60000)
public void refreshDashboard() {
    TrendAnalysis trend = analyticsClient.getTrend(
        customerId,
        "DAILY",
        LocalDate.now().minusYears(1),
        LocalDate.now()
    );  // 365个数据点,性能差
}

// ✅ 正确: 只查询最近7天
@Scheduled(fixedRate = 60000)
public void refreshDashboard() {
    TrendAnalysis trend = analyticsClient.getDailyTrend(customerId);  // 7个数据点
}
```

#### 9.5 避免同步阻塞导出大文件

```java
// ❌ 错误: 同步导出阻塞主线程
public void exportReport() {
    byte[] report = analyticsClient.exportReport(customerId, "EXCEL", "FULL");
    // 可能需要10秒以上
}

// ✅ 正确: 异步导出
@Async
public CompletableFuture<String> exportReportAsync(String customerId) {
    byte[] report = analyticsClient.exportReport(customerId, "EXCEL", "FULL");
    String filename = "report_" + LocalDate.now() + ".xlsx";
    Files.write(Paths.get(filename), report);
    return CompletableFuture.completedFuture(filename);
}
```

---

## 10. 故障排查

### 10.1 统计数据不准确

**症状**: 统计数字与实际不符

**诊断步骤**:

```bash
# 检查数据库查询
psql -U threat_user -d threat_detection -c "
SELECT 
    COUNT(*) as total,
    COUNT(*) FILTER (WHERE status = 'OPEN') as open,
    COUNT(*) FILTER (WHERE severity = 'CRITICAL') as critical
FROM alerts
WHERE customer_id = 'customer-001'
  AND created_at >= CURRENT_DATE;
"
```

**解决方案**:

```yaml
# 清除缓存
curl -X DELETE http://localhost:8082/actuator/caches/analytics:summary

# 检查时区配置
spring:
  jackson:
    time-zone: UTC
```

---

### 10.2 趋势分析查询超时

**症状**: 大时间范围查询超过30秒

**诊断步骤**:

```sql
-- 检查索引
EXPLAIN ANALYZE
SELECT DATE_TRUNC('day', created_at) as day, COUNT(*)
FROM alerts
WHERE customer_id = 'customer-001'
  AND created_at >= '2024-01-01'
GROUP BY day
ORDER BY day;
```

**解决方案**:

```sql
-- 添加复合索引
CREATE INDEX idx_alerts_customer_created 
ON alerts(customer_id, created_at);

-- 添加物化视图
CREATE MATERIALIZED VIEW daily_alert_stats AS
SELECT 
    customer_id,
    DATE_TRUNC('day', created_at) as day,
    COUNT(*) as alert_count,
    AVG(threat_score) as avg_score
FROM alerts
GROUP BY customer_id, day;

-- 定期刷新
REFRESH MATERIALIZED VIEW daily_alert_stats;
```

---

### 10.3 报表导出内存溢出

**症状**: 导出大报表时OOM错误

**解决方案**:

```java
// 使用流式导出
@GetMapping("/analytics/export")
public void exportReportStream(
        @RequestParam String customerId,
        HttpServletResponse response) throws IOException {
    
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=report.xlsx");
    
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {  // 内存中保留100行
        Sheet sheet = workbook.createSheet("Alerts");
        
        // 分批查询写入
        int pageSize = 1000;
        int page = 0;
        while (true) {
            List<Alert> alerts = alertRepository.findByCustomerId(
                customerId,
                PageRequest.of(page, pageSize)
            );
            
            if (alerts.isEmpty()) break;
            
            for (Alert alert : alerts) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                // 写入数据...
            }
            
            page++;
        }
        
        workbook.write(response.getOutputStream());
    }
}
```

```yaml
# 增加堆内存
JAVA_OPTS: "-Xmx2g -Xms1g"
```

---

## 11. 相关文档

| 文档 | 说明 |
|------|------|
| [告警CRUD API](./alert_crud_api.md) | 告警基本操作 |
| [告警生命周期API](./alert_lifecycle_api.md) | 状态管理 |
| [告警通知API](./alert_notification_api.md) | 通知统计来源 |
| [告警升级API](./alert_escalation_api.md) | 升级统计来源 |
| [威胁评估查询API](./threat_assessment_query_api.md) | 威胁评分数据 |

---

**文档结束**

*最后更新: 2025-10-16*
