# 威胁评估客户端指南

**文档版本**: 1.0  
**更新日期**: 2025-01-16

---

## 完整Java客户端实现

```java
package com.threatdetection.client;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

/**
 * 威胁评估服务完整客户端
 */
public class ThreatAssessmentClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    
    public ThreatAssessmentClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 执行威胁评估
     */
    public AssessmentResponse evaluate(AssessmentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AssessmentRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<AssessmentResponse> response = restTemplate.postForEntity(
            baseUrl + "/evaluate",
            httpRequest,
            AssessmentResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 获取评估详情
     */
    public AssessmentResponse getAssessment(String assessmentId) {
        return restTemplate.getForObject(
            baseUrl + "/" + assessmentId,
            AssessmentResponse.class
        );
    }
    
    /**
     * 查询威胁趋势
     */
    public ThreatTrendResponse getTrends(
            String customerId,
            String startTime,
            String endTime,
            String granularity) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/trends")
            .queryParam("customerId", customerId)
            .queryParam("startTime", startTime)
            .queryParam("endTime", endTime)
            .queryParam("granularity", granularity)
            .toUriString();
        
        return restTemplate.getForObject(url, ThreatTrendResponse.class);
    }
    
    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            HealthStatus status = restTemplate.getForObject(
                baseUrl + "/health",
                HealthStatus.class
            );
            return "UP".equals(status.getStatus());
        } catch (Exception e) {
            return false;
        }
    }
}
```

## 使用示例

```java
public class ClientUsageExample {
    
    public static void main(String[] args) {
        ThreatAssessmentClient client = new ThreatAssessmentClient(
            "http://localhost:8083/api/v1/assessment"
        );
        
        // 1. 执行威胁评估
        AssessmentRequest request = new AssessmentRequest();
        request.setCustomerId("customer_a");
        request.setAttackMac("04:42:1a:8e:e3:65");
        request.setAttackIp("192.168.75.188");
        request.setAttackCount(150);
        request.setUniqueIps(5);
        request.setUniquePorts(3);
        request.setUniqueDevices(2);
        request.setTimestamp(Instant.now().toString());
        
        AssessmentResponse assessment = client.evaluate(request);
        System.out.println("Threat Level: " + assessment.getThreatLevel());
        
        // 2. 根据威胁等级执行相应操作
        switch (assessment.getThreatLevel()) {
            case "CRITICAL":
                System.out.println("CRITICAL威胁检测到，建议立即响应");
                break;
            case "HIGH":
                System.out.println("HIGH威胁检测到，建议1小时内响应");
                break;
            case "MEDIUM":
                System.out.println("MEDIUM威胁检测到，建议4小时内响应");
                break;
            default:
                System.out.println("低危威胁，正常监控即可");
                break;
        }
    }
}
```

## 最佳实践

### 1. 连接池配置

```java
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

public RestTemplate createOptimizedRestTemplate() {
    HttpClient httpClient = HttpClientBuilder.create()
        .setMaxConnTotal(100)
        .setMaxConnPerRoute(20)
        .build();
    
    HttpComponentsClientHttpRequestFactory factory = 
        new HttpComponentsClientHttpRequestFactory(httpClient);
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    
    return new RestTemplate(factory);
}
```

### 2. 错误处理

```java
public AssessmentResponse evaluateWithRetry(AssessmentRequest request) {
    int maxRetries = 3;
    int retryCount = 0;
    
    while (retryCount < maxRetries) {
        try {
            return client.evaluate(request);
        } catch (Exception e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                throw e;
            }
            Thread.sleep(1000 * retryCount); // 指数退避
        }
    }
}
```

---

**相关文档**: [威胁评估概述](./threat_assessment_overview.md) | [评估API](./threat_assessment_evaluation_api.md)

---

**文档结束**

*最后更新: 2025-01-16*
