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
     * 执行缓解措施
     */
    public MitigationResponse executeMitigation(
            String assessmentId,
            MitigationRequest request) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<MitigationRequest> httpRequest = new HttpEntity<>(request, headers);
        
        String url = baseUrl + "/mitigation/" + assessmentId;
        
        ResponseEntity<MitigationResponse> response = restTemplate.postForEntity(
            url,
            httpRequest,
            MitigationResponse.class
        );
        
        return response.getBody();
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
            "http://localhost:8081/api/v1/assessment"
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
        
        // 2. 如果是CRITICAL,执行隔离
        if ("CRITICAL".equals(assessment.getThreatLevel())) {
            MitigationRequest mitigation = new MitigationRequest();
            mitigation.setMitigationType("ISOLATE");
            mitigation.setAutoExecute(true);
            mitigation.setReason("CRITICAL威胁自动隔离");
            mitigation.setExecutedBy("system");
            
            client.executeMitigation(assessment.getAssessmentId(), mitigation);
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
