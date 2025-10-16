# 威胁评估API - 查询和趋势分析

**服务名称**: Threat Assessment Service  
**服务端口**: 8081  
**文档版本**: 1.0  
**更新日期**: 2025-01-16

---

## 目录

1. [API概述](#api概述)
2. [获取评估详情](#获取评估详情)
3. [威胁趋势分析](#威胁趋势分析)
4. [健康检查](#健康检查)

---

## API概述

本文档涵盖威胁评估服务的**查询和分析操作**:

| 方法 | 端点 | 功能 |
|------|------|------|
| `GET` | `/api/v1/assessment/{assessmentId}` | 获取评估详情 |
| `GET` | `/api/v1/assessment/trends` | 威胁趋势分析 |
| `GET` | `/api/v1/assessment/health` | 健康检查 |

**相关文档**: [威胁评估概述](./threat_assessment_overview.md) | [评估操作API](./threat_assessment_evaluation_api.md)

---

## 获取评估详情

### 端点信息

**描述**: 根据评估ID查询威胁评估的详细信息。

**端点**: `GET /api/v1/assessment/{assessmentId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `assessmentId` | String | ✅ | 评估记录ID |

### 请求示例 (curl)

```bash
curl -X GET http://localhost:8081/api/v1/assessment/12345
```

### 请求示例 (Java)

```java
public class QueryExample {
    
    private static final String BASE_URL = "http://localhost:8081/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    public AssessmentResponse getAssessmentDetails(String assessmentId) {
        String url = BASE_URL + "/" + assessmentId;
        return restTemplate.getForObject(url, AssessmentResponse.class);
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "assessmentId": "12345",
  "customerId": "customer_a",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "threatScore": 7290.0,
  "threatLevel": "CRITICAL",
  "riskFactors": {
    "attackCount": 150,
    "uniqueIps": 5,
    "uniquePorts": 3,
    "uniqueDevices": 2,
    "timeWeight": 1.2,
    "ipWeight": 1.5,
    "portWeight": 1.2,
    "deviceWeight": 1.5
  },
  "mitigationRecommendations": [
    "立即隔离攻击源 192.168.75.188",
    "检查同网段其他主机是否被攻陷"
  ],
  "assessmentTime": "2025-01-15T02:30:00Z",
  "createdAt": "2025-01-15T02:30:05Z"
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 查询成功 |
| 404 | 评估记录不存在 |
| 500 | 服务器内部错误 |

---

## 威胁趋势分析

### 端点信息

**描述**: 分析指定时间范围内的威胁趋势,支持多种时间粒度。

**端点**: `GET /api/v1/assessment/trends`

### 查询参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `customerId` | String | ❌ | - | 客户ID (不指定则查询所有) |
| `startTime` | String (ISO8601) | ✅ | - | 开始时间 |
| `endTime` | String (ISO8601) | ✅ | - | 结束时间 |
| `granularity` | String | ❌ | HOURLY | 时间粒度 (HOURLY/DAILY/WEEKLY) |
| `riskLevel` | String | ❌ | - | 威胁等级过滤 |

### 请求示例 (curl)

#### 查询过去24小时的小时级趋势

```bash
curl -X GET "http://localhost:8081/api/v1/assessment/trends?customerId=customer_a&startTime=2025-01-15T00:00:00Z&endTime=2025-01-15T23:59:59Z&granularity=HOURLY"
```

#### 查询CRITICAL级别威胁趋势

```bash
curl -X GET "http://localhost:8081/api/v1/assessment/trends?customerId=customer_a&startTime=2025-01-01T00:00:00Z&endTime=2025-01-31T23:59:59Z&granularity=DAILY&riskLevel=CRITICAL"
```

### 请求示例 (Java)

```java
public class TrendAnalysisExample {
    
    private static final String BASE_URL = "http://localhost:8081/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 查询威胁趋势
     */
    public ThreatTrendResponse getThreatTrends(
            String customerId,
            String startTime,
            String endTime,
            String granularity,
            String riskLevel) {
        
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(BASE_URL + "/trends")
            .queryParam("startTime", startTime)
            .queryParam("endTime", endTime);
        
        if (customerId != null) {
            builder.queryParam("customerId", customerId);
        }
        if (granularity != null) {
            builder.queryParam("granularity", granularity);
        }
        if (riskLevel != null) {
            builder.queryParam("riskLevel", riskLevel);
        }
        
        return restTemplate.getForObject(
            builder.toUriString(),
            ThreatTrendResponse.class
        );
    }
    
    /**
     * 分析过去24小时趋势
     */
    public void analyzeLast24Hours(String customerId) {
        Instant now = Instant.now();
        Instant yesterday = now.minus(24, ChronoUnit.HOURS);
        
        ThreatTrendResponse trends = getThreatTrends(
            customerId,
            yesterday.toString(),
            now.toString(),
            "HOURLY",
            null
        );
        
        System.out.println("24小时威胁趋势分析:");
        System.out.println("总威胁数: " + trends.getSummary().getTotalThreats());
        System.out.println("平均评分: " + trends.getSummary().getAverageScore());
        System.out.println("CRITICAL数量: " + trends.getSummary().getCriticalCount());
        
        // 绘制趋势图
        trends.getDataPoints().forEach(point -> {
            System.out.println(point.getTimestamp() + ": " 
                              + point.getThreatCount() + " threats (平均分: " 
                              + point.getAverageScore() + ")");
        });
    }
    
    // DTO类
    public static class ThreatTrendResponse {
        private String customerId;
        private TimeRange timeRange;
        private String granularity;
        private List<DataPoint> dataPoints;
        private TrendSummary summary;
        
        // Getters
        public List<DataPoint> getDataPoints() { return dataPoints; }
        public TrendSummary getSummary() { return summary; }
    }
    
    public static class DataPoint {
        private String timestamp;
        private int threatCount;
        private double averageScore;
        private Map<String, Integer> levelDistribution;
        
        // Getters
        public String getTimestamp() { return timestamp; }
        public int getThreatCount() { return threatCount; }
        public double getAverageScore() { return averageScore; }
    }
    
    public static class TrendSummary {
        private int totalThreats;
        private double averageScore;
        private double highestScore;
        private int criticalCount;
        
        // Getters
        public int getTotalThreats() { return totalThreats; }
        public double getAverageScore() { return averageScore; }
        public int getCriticalCount() { return criticalCount; }
    }
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "customerId": "customer_a",
  "timeRange": {
    "start": "2025-01-15T00:00:00Z",
    "end": "2025-01-15T23:59:59Z"
  },
  "granularity": "HOURLY",
  "dataPoints": [
    {
      "timestamp": "2025-01-15T00:00:00Z",
      "threatCount": 15,
      "averageScore": 45.2,
      "levelDistribution": {
        "INFO": 5,
        "LOW": 8,
        "MEDIUM": 2,
        "HIGH": 0,
        "CRITICAL": 0
      }
    },
    {
      "timestamp": "2025-01-15T02:00:00Z",
      "threatCount": 23,
      "averageScore": 325.8,
      "levelDistribution": {
        "INFO": 2,
        "LOW": 5,
        "MEDIUM": 10,
        "HIGH": 4,
        "CRITICAL": 2
      }
    }
  ],
  "summary": {
    "totalThreats": 450,
    "averageScore": 85.3,
    "highestScore": 7290.0,
    "criticalCount": 12
  }
}
```

---

## 健康检查

### 端点信息

**描述**: 检查威胁评估服务的健康状态。

**端点**: `GET /api/v1/assessment/health`

### 请求示例 (curl)

```bash
curl -X GET http://localhost:8081/api/v1/assessment/health
```

### 请求示例 (Java)

```java
public boolean checkHealth() {
    try {
        HealthStatus status = restTemplate.getForObject(
            BASE_URL + "/health",
            HealthStatus.class
        );
        return "UP".equals(status.getStatus());
    } catch (Exception e) {
        return false;
    }
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "status": "UP",
  "components": {
    "database": "UP",
    "kafka": "UP"
  }
}
```

---

**相关文档**: [威胁评估概述](./threat_assessment_overview.md) | [客户端指南](./threat_assessment_client_guide.md)

---

**文档结束**

*最后更新: 2025-01-16*
