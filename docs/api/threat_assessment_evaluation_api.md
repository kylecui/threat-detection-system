# 威胁评估API - 评估操作

**服务名称**: Threat Assessment Service  
**服务端口**: 8081  
**文档版本**: 1.0  
**更新日期**: 2025-01-16

---

## 目录

1. [API概述](#api概述)
2. [执行威胁评估](#执行威胁评估)
3. [执行缓解措施](#执行缓解措施)
4. [使用场景](#使用场景)

---

## API概述

本文档涵盖威胁评估服务的**核心评估操作**:

| 方法 | 端点 | 功能 |
|------|------|------|
| `POST` | `/api/v1/assessment/evaluate` | 执行威胁评估 |
| `POST` | `/api/v1/assessment/mitigation/{assessmentId}` | 执行缓解措施 |

**相关文档**: [威胁评估概述](./threat_assessment_overview.md) | [查询分析API](./threat_assessment_query_api.md)

---

## 执行威胁评估

### 端点信息

**描述**: 对聚合攻击数据进行综合威胁评估,计算威胁评分和风险等级。

**端点**: `POST /api/v1/assessment/evaluate`

**Swagger注解**: 
```java
@Operation(summary = "执行威胁评估", description = "对攻击数据进行综合威胁评估")
```

### 请求参数

**Content-Type**: `application/json`

**请求体模型** (AssessmentRequest):

```json
{
  "customerId": "customer_a",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "timestamp": "2025-01-15T02:30:00Z"
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 验证规则 | 说明 |
|-----|------|------|---------|------|
| `customerId` | String | ✅ | @NotBlank | 客户ID (租户隔离) |
| `attackMac` | String | ✅ | @NotBlank | 被诱捕者MAC地址 |
| `attackIp` | String | ✅ | @NotBlank | 被诱捕者IP地址 |
| `attackCount` | Integer | ✅ | @Min(1) | 探测次数 (≥1) |
| `uniqueIps` | Integer | ✅ | @Min(1) | 访问的诱饵IP数量 |
| `uniquePorts` | Integer | ✅ | @Min(1) | 尝试的端口种类 |
| `uniqueDevices` | Integer | ✅ | @Min(1) | 检测到的蜜罐设备数 |
| `timestamp` | String | ✅ | ISO8601 | 评估时间 |

### 请求示例 (curl)

#### 场景1: CRITICAL级别威胁 (深夜大规模横向移动)

```bash
curl -X POST http://localhost:8081/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer_a",
    "attackMac": "04:42:1a:8e:e3:65",
    "attackIp": "192.168.75.188",
    "attackCount": 150,
    "uniqueIps": 5,
    "uniquePorts": 3,
    "uniqueDevices": 2,
    "timestamp": "2025-01-15T02:30:00Z"
  }'
```

#### 场景2: MEDIUM级别威胁 (工作时间单目标探测)

```bash
curl -X POST http://localhost:8081/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer_a",
    "attackMac": "aa:bb:cc:dd:ee:ff",
    "attackIp": "10.0.1.50",
    "attackCount": 30,
    "uniqueIps": 2,
    "uniquePorts": 1,
    "uniqueDevices": 1,
    "timestamp": "2025-01-15T14:30:00Z"
  }'
```

### 请求示例 (Java)

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

public class ThreatAssessmentExample {
    
    private static final String BASE_URL = "http://localhost:8081/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    /**
     * 执行威胁评估
     */
    public AssessmentResponse evaluateThreat(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices) {
        
        // 构建请求
        AssessmentRequest request = new AssessmentRequest();
        request.setCustomerId(customerId);
        request.setAttackMac(attackMac);
        request.setAttackIp(attackIp);
        request.setAttackCount(attackCount);
        request.setUniqueIps(uniqueIps);
        request.setUniquePorts(uniquePorts);
        request.setUniqueDevices(uniqueDevices);
        request.setTimestamp(Instant.now().toString());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AssessmentRequest> httpRequest = new HttpEntity<>(request, headers);
        
        // 发送请求
        ResponseEntity<AssessmentResponse> response = restTemplate.postForEntity(
            BASE_URL + "/evaluate",
            httpRequest,
            AssessmentResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 评估CRITICAL级别威胁 (深夜横向移动)
     */
    public void evaluateCriticalThreat() {
        AssessmentResponse result = evaluateThreat(
            "customer_a",
            "04:42:1a:8e:e3:65",
            "192.168.75.188",
            150,    // 高频探测
            5,      // 5个诱饵IP (横向移动)
            3,      // 3种端口 (多种攻击手段)
            2       // 2个蜜罐设备检测到
        );
        
        System.out.println("Assessment ID: " + result.getAssessmentId());
        System.out.println("Threat Score: " + result.getThreatScore());
        System.out.println("Threat Level: " + result.getThreatLevel());
        System.out.println("\nMitigation Recommendations:");
        result.getMitigationRecommendations().forEach(r -> 
            System.out.println("- " + r)
        );
    }
    
    // DTO类
    public static class AssessmentRequest {
        private String customerId;
        private String attackMac;
        private String attackIp;
        private int attackCount;
        private int uniqueIps;
        private int uniquePorts;
        private int uniqueDevices;
        private String timestamp;
        
        // Getters and Setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getAttackMac() { return attackMac; }
        public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
        
        public String getAttackIp() { return attackIp; }
        public void setAttackIp(String attackIp) { this.attackIp = attackIp; }
        
        public int getAttackCount() { return attackCount; }
        public void setAttackCount(int attackCount) { this.attackCount = attackCount; }
        
        public int getUniqueIps() { return uniqueIps; }
        public void setUniqueIps(int uniqueIps) { this.uniqueIps = uniqueIps; }
        
        public int getUniquePorts() { return uniquePorts; }
        public void setUniquePorts(int uniquePorts) { this.uniquePorts = uniquePorts; }
        
        public int getUniqueDevices() { return uniqueDevices; }
        public void setUniqueDevices(int uniqueDevices) { this.uniqueDevices = uniqueDevices; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
    
    public static class AssessmentResponse {
        private String assessmentId;
        private String customerId;
        private String attackMac;
        private String attackIp;
        private double threatScore;
        private String threatLevel;
        private RiskFactors riskFactors;
        private List<String> mitigationRecommendations;
        private String assessmentTime;
        private String createdAt;
        
        // Getters
        public String getAssessmentId() { return assessmentId; }
        public String getCustomerId() { return customerId; }
        public String getAttackMac() { return attackMac; }
        public String getAttackIp() { return attackIp; }
        public double getThreatScore() { return threatScore; }
        public String getThreatLevel() { return threatLevel; }
        public RiskFactors getRiskFactors() { return riskFactors; }
        public List<String> getMitigationRecommendations() { return mitigationRecommendations; }
        public String getAssessmentTime() { return assessmentTime; }
        public String getCreatedAt() { return createdAt; }
    }
    
    public static class RiskFactors {
        private int attackCount;
        private int uniqueIps;
        private int uniquePorts;
        private int uniqueDevices;
        private double timeWeight;
        private double ipWeight;
        private double portWeight;
        private double deviceWeight;
        
        // Getters
        public int getAttackCount() { return attackCount; }
        public int getUniqueIps() { return uniqueIps; }
        public int getUniquePorts() { return uniquePorts; }
        public int getUniqueDevices() { return uniqueDevices; }
        public double getTimeWeight() { return timeWeight; }
        public double getIpWeight() { return ipWeight; }
        public double getPortWeight() { return portWeight; }
        public double getDeviceWeight() { return deviceWeight; }
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
    "检查同网段其他主机是否被攻陷",
    "审计攻击源的网络访问日志",
    "启动应急响应流程",
    "通知安全团队进行深度分析"
  ],
  "assessmentTime": "2025-01-15T02:30:00Z",
  "createdAt": "2025-01-15T02:30:05Z"
}
```

### 响应示例 (验证失败)

**HTTP 400 Bad Request**

```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "attackCount",
      "message": "must be greater than or equal to 1"
    },
    {
      "field": "customerId",
      "message": "must not be blank"
    }
  ]
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 评估成功完成 |
| 400 | 请求参数验证失败 |
| 500 | 服务器内部错误 (数据库/计算异常) |

---

## 执行缓解措施

### 端点信息

**描述**: 对已评估的威胁执行缓解措施,如隔离、阻断、告警等。

**端点**: `POST /api/v1/assessment/mitigation/{assessmentId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `assessmentId` | String | ✅ | 评估记录ID |

### 请求参数

**Content-Type**: `application/json`

**请求体模型** (MitigationRequest):

```json
{
  "mitigationType": "ISOLATE",
  "autoExecute": true,
  "reason": "CRITICAL级别威胁,立即隔离",
  "executedBy": "admin@company.com"
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 可选值 | 说明 |
|-----|------|------|--------|------|
| `mitigationType` | String | ✅ | ISOLATE, BLOCK, ALERT, MONITOR | 缓解类型 |
| `autoExecute` | Boolean | ❌ | true/false | 是否自动执行 (默认false) |
| `reason` | String | ❌ | - | 执行原因 |
| `executedBy` | String | ✅ | - | 执行人 |

**缓解类型说明**:

| 类型 | 操作 | 适用等级 |
|------|------|---------|
| `ISOLATE` | 完全隔离攻击源 (断网) | CRITICAL, HIGH |
| `BLOCK` | 阻断特定端口/协议 | HIGH, MEDIUM |
| `ALERT` | 仅发送告警 | MEDIUM, LOW |
| `MONITOR` | 加入监控列表 | LOW, INFO |

### 请求示例 (curl)

```bash
# 对CRITICAL威胁立即执行隔离
curl -X POST http://localhost:8081/api/v1/assessment/mitigation/12345 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "ISOLATE",
    "autoExecute": true,
    "reason": "深夜检测到大规模横向移动,立即隔离",
    "executedBy": "security-admin@company.com"
  }'

# 对MEDIUM威胁执行端口阻断
curl -X POST http://localhost:8081/api/v1/assessment/mitigation/12346 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "BLOCK",
    "autoExecute": false,
    "reason": "阻断RDP和SMB端口访问",
    "executedBy": "admin@company.com"
  }'
```

### 请求示例 (Java)

```java
public class MitigationExample {
    
    private static final String BASE_URL = "http://localhost:8081/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 执行缓解措施
     */
    public MitigationResponse executeMitigation(
            String assessmentId,
            String mitigationType,
            boolean autoExecute,
            String reason,
            String executedBy) {
        
        // 构建请求
        MitigationRequest request = new MitigationRequest();
        request.setMitigationType(mitigationType);
        request.setAutoExecute(autoExecute);
        request.setReason(reason);
        request.setExecutedBy(executedBy);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<MitigationRequest> httpRequest = new HttpEntity<>(request, headers);
        
        String url = BASE_URL + "/mitigation/" + assessmentId;
        
        ResponseEntity<MitigationResponse> response = restTemplate.postForEntity(
            url,
            httpRequest,
            MitigationResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 自动隔离CRITICAL威胁
     */
    public void isolateCriticalThreat(String assessmentId) {
        MitigationResponse result = executeMitigation(
            assessmentId,
            "ISOLATE",
            true,
            "CRITICAL级别威胁,自动隔离",
            "system-auto"
        );
        
        System.out.println("Mitigation Status: " + result.getStatus());
        System.out.println("Execution Time: " + result.getExecutionTime());
        System.out.println("Actions Taken:");
        result.getActionsTaken().forEach(action -> 
            System.out.println("- " + action)
        );
    }
    
    // DTO类
    public static class MitigationRequest {
        private String mitigationType;
        private boolean autoExecute;
        private String reason;
        private String executedBy;
        
        // Getters and Setters
        public String getMitigationType() { return mitigationType; }
        public void setMitigationType(String mitigationType) { this.mitigationType = mitigationType; }
        
        public boolean isAutoExecute() { return autoExecute; }
        public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getExecutedBy() { return executedBy; }
        public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }
    }
    
    public static class MitigationResponse {
        private String mitigationId;
        private String assessmentId;
        private String status;
        private List<String> actionsTaken;
        private String executionTime;
        
        // Getters
        public String getMitigationId() { return mitigationId; }
        public String getAssessmentId() { return assessmentId; }
        public String getStatus() { return status; }
        public List<String> getActionsTaken() { return actionsTaken; }
        public String getExecutionTime() { return executionTime; }
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "mitigationId": "MIT-67890",
  "assessmentId": "12345",
  "status": "EXECUTED",
  "actionsTaken": [
    "已隔离攻击源 192.168.75.188 (MAC: 04:42:1a:8e:e3:65)",
    "已阻断该MAC地址的所有网络访问",
    "已发送CRITICAL级别告警给安全团队",
    "已记录事件日志到审计系统"
  ],
  "executionTime": "2025-01-15T02:31:00Z"
}
```

### 响应示例 (评估不存在)

**HTTP 404 Not Found**

```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Assessment with ID 99999 not found"
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 缓解措施执行成功 |
| 400 | 请求参数无效 |
| 404 | 评估记录不存在 |
| 500 | 缓解措施执行失败 |

---

## 使用场景

### 场景1: 自动威胁评估和缓解

**需求**: 检测到CRITICAL威胁时,自动评估并隔离攻击源。

**实现**:

```java
public class AutoMitigationService {
    
    private final ThreatAssessmentExample assessmentClient;
    private final MitigationExample mitigationClient;
    
    /**
     * 自动评估和缓解流程
     */
    public void handleThreatAlert(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices) {
        
        // 1. 执行威胁评估
        AssessmentResponse assessment = assessmentClient.evaluateThreat(
            customerId, attackMac, attackIp,
            attackCount, uniqueIps, uniquePorts, uniqueDevices
        );
        
        System.out.println("Threat assessed: " + assessment.getThreatLevel() 
                          + " (Score: " + assessment.getThreatScore() + ")");
        
        // 2. 根据威胁等级自动缓解
        if ("CRITICAL".equals(assessment.getThreatLevel())) {
            // CRITICAL: 立即隔离
            mitigationClient.executeMitigation(
                assessment.getAssessmentId(),
                "ISOLATE",
                true,
                "CRITICAL级别威胁,自动隔离",
                "system-auto"
            );
            System.out.println("✅ Attack source isolated automatically");
            
        } else if ("HIGH".equals(assessment.getThreatLevel())) {
            // HIGH: 阻断危险端口
            mitigationClient.executeMitigation(
                assessment.getAssessmentId(),
                "BLOCK",
                true,
                "HIGH级别威胁,阻断高危端口",
                "system-auto"
            );
            System.out.println("✅ Dangerous ports blocked");
            
        } else if ("MEDIUM".equals(assessment.getThreatLevel())) {
            // MEDIUM: 发送告警
            mitigationClient.executeMitigation(
                assessment.getAssessmentId(),
                "ALERT",
                true,
                "MEDIUM级别威胁,发送告警",
                "system-auto"
            );
            System.out.println("✅ Alert sent to security team");
        }
    }
}
```

### 场景2: 批量威胁评估

**需求**: 对过去1小时的所有攻击事件进行批量评估。

**实现**:

```java
public class BatchAssessmentService {
    
    /**
     * 批量评估威胁
     */
    public List<AssessmentResponse> batchEvaluate(List<AttackEvent> events) {
        List<AssessmentResponse> results = new ArrayList<>();
        
        for (AttackEvent event : events) {
            try {
                AssessmentResponse assessment = assessmentClient.evaluateThreat(
                    event.getCustomerId(),
                    event.getAttackMac(),
                    event.getAttackIp(),
                    event.getAttackCount(),
                    event.getUniqueIps(),
                    event.getUniquePorts(),
                    event.getUniqueDevices()
                );
                
                results.add(assessment);
                
                // 记录高危威胁
                if ("HIGH".equals(assessment.getThreatLevel()) || 
                    "CRITICAL".equals(assessment.getThreatLevel())) {
                    System.err.println("⚠️ High-risk threat detected: " 
                                      + assessment.getAttackIp());
                }
                
            } catch (Exception e) {
                System.err.println("Failed to assess: " + event.getAttackIp() 
                                  + " - " + e.getMessage());
            }
        }
        
        // 统计
        long criticalCount = results.stream()
            .filter(r -> "CRITICAL".equals(r.getThreatLevel()))
            .count();
        
        System.out.println("Batch assessment completed:");
        System.out.println("- Total: " + results.size());
        System.out.println("- CRITICAL: " + criticalCount);
        
        return results;
    }
}
```

---

## 相关文档

- **[威胁评估概述](./threat_assessment_overview.md)** - 系统架构和评分算法
- **[查询和趋势API](./threat_assessment_query_api.md)** - 查询评估历史和趋势分析
- **[客户端指南](./threat_assessment_client_guide.md)** - 完整客户端实现和最佳实践
- **[蜜罐评分方案](./honeypot_based_threat_scoring.md)** - 威胁评分算法详解

---

**文档结束**

*最后更新: 2025-01-16*
