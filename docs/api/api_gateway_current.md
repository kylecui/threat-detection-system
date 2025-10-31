# API Gateway 当前实现文档

**版本**: 1.0  
**实现日期**: 2025-10-19  
**端口**: 8888 (映射到容器内8080)  
**技术栈**: Spring Cloud Gateway 2022.0.4 + Spring Boot 3.1.5

---

## 概述

API Gateway是威胁检测系统的**统一API入口**，提供路由转发、安全控制、限流保护等功能。

### 核心功能

1. **路由管理** ✅
   - 统一入口：所有前端请求通过 `http://localhost:8888`
   - 智能路由：自动转发到后端微服务
   - 服务发现：基于hostname的服务发现

2. **安全控制** ✅
   - CORS跨域：支持前端跨域访问
   - 请求日志：记录所有请求详情
   - 限流保护：防止API滥用（令牌桶算法）

3. **熔断降级** ✅
   - 服务不可用时返回友好错误
   - 自动重试和超时控制

4. **监控告警** ✅
   - Actuator健康检查
   - Prometheus指标导出
   - 路由状态监控

---

## 路由规则

| 路径前缀 | 目标服务 | 端口 | 说明 |
|---------|---------|------|------|
| `/api/v1/customers/**` | customer-management | 8084 | 客户管理（CRUD） |
| `/api/v1/devices/**` | customer-management | 8084 | 设备绑定管理 |
| `/api/v1/notifications/**` | customer-management | 8084 | 通知配置 |
| `/api/v1/logs/**` | data-ingestion | 8080 | 日志摄取和统计 |
| `/api/v1/assessment/**` | threat-assessment | 8081 | 威胁评估 |
| `/api/v1/alerts/**` | alert-management | 8082 | 告警管理 |

### 路由配置详情

每个路由包含以下特性：
- **熔断保护**: 后端服务故障时自动降级
- **请求头注入**: 添加 `X-Gateway-Service` 标识来源
- **路径保持**: stripPrefix=0，保持原始路径
- **超时控制**: 连接超时3秒，响应超时10秒

---

## 快速开始

### 1. 构建和启动

```bash
# 构建Gateway
cd services/api-gateway
mvn clean package -DskipTests

# 启动整个系统（包含Gateway）
cd ../../docker
docker-compose up -d api-gateway

# 查看日志
docker-compose logs -f api-gateway
```

### 2. 验证部署

```bash
# 健康检查
curl http://localhost:8888/actuator/health

# 查看路由配置
curl http://localhost:8888/actuator/gateway/routes | jq
```

### 3. 运行测试

```bash
# 运行完整测试脚本
./scripts/test_api_gateway.sh
```

---

## API使用示例

### 客户管理API

#### 创建客户
```bash
curl -X POST http://localhost:8888/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer-001",
    "company_name": "示例公司",
    "contact_name": "张三",
    "contact_email": "admin@example.com",
    "contact_phone": "13800138000",
    "subscription_tier": "PROFESSIONAL"
  }'
```

**响应**:
```json
{
  "id": 1,
  "customer_id": "customer-001",
  "company_name": "示例公司",
  "contact_name": "张三",
  "contact_email": "admin@example.com",
  "subscription_tier": "PROFESSIONAL",
  "is_active": true,
  "created_at": 1697723200.0
}
```

#### 查询客户
```bash
# 按ID查询
curl http://localhost:8888/api/v1/customers/customer-001

# 查询所有客户
curl http://localhost:8888/api/v1/customers
```

#### 更新客户
```bash
curl -X PUT http://localhost:8888/api/v1/customers/customer-001 \
  -H "Content-Type: application/json" \
  -d '{
    "company_name": "更新后的公司名",
    "contact_email": "newemail@example.com"
  }'
```

### 设备管理API

#### 绑定设备
```bash
curl -X POST http://localhost:8888/api/v1/devices/bind \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer-001",
    "dev_serial": "DEV-001",
    "device_name": "办公室1号设备",
    "location": "北京办公室"
  }'
```

#### 查询客户设备
```bash
curl http://localhost:8888/api/v1/devices/customer/customer-001
```

### 通知配置API

#### 创建通知配置
```bash
curl -X POST http://localhost:8888/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer-001",
    "notification_type": "EMAIL",
    "min_severity": "HIGH",
    "recipients": ["admin@example.com", "security@example.com"],
    "enabled": true
  }'
```

### 数据摄取API

#### 查询日志统计
```bash
curl http://localhost:8888/api/v1/logs/stats
```

**响应**:
```json
{
  "total_processed": 150000,
  "parsed_successfully": 148500,
  "parsing_failed": 1500,
  "last_updated": "2025-10-19T12:00:00Z"
}
```

### 威胁评估API

#### 查询威胁趋势
```bash
curl "http://localhost:8888/api/v1/assessment/trends?customerId=customer-001&hours=24"
```

#### 查询威胁详情
```bash
curl http://localhost:8888/api/v1/assessment/test-customer-001/00:11:22:33:44:55
```

### 告警管理API

#### 查询告警列表
```bash
# 查询所有告警
curl "http://localhost:8888/api/v1/alerts?page=0&size=20"

# 按严重级别查询
curl "http://localhost:8888/api/v1/alerts?severity=CRITICAL&page=0&size=20"

# 按状态查询
curl "http://localhost:8888/api/v1/alerts?status=OPEN&page=0&size=20"
```

#### 更新告警状态
```bash
curl -X PUT http://localhost:8888/api/v1/alerts/123/status \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED", "resolved_by": "admin"}'
```

---

## Java客户端完整示例

### 完整客户端实现

```java
package com.threatdetection.client;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * API Gateway 完整客户端
 * 
 * 统一访问所有微服务的入口点，自动路由到对应的后端服务
 */
@Slf4j
public class ApiGatewayClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    
    public ApiGatewayClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }
    
    // ===== 客户管理服务 =====
    
    /**
     * 创建客户
     */
    public Customer createCustomer(CustomerRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CustomerRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<Customer> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/customers",
            httpRequest,
            Customer.class
        );
        
        log.info("Created customer: {}", response.getBody().getCustomerId());
        return response.getBody();
    }
    
    /**
     * 查询客户
     */
    public Customer getCustomer(String customerId) {
        String url = baseUrl + "/api/v1/customers/" + customerId;
        return restTemplate.getForObject(url, Customer.class);
    }
    
    /**
     * 查询所有客户
     */
    public List<Customer> getAllCustomers() {
        String url = baseUrl + "/api/v1/customers";
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 更新客户
     */
    public Customer updateCustomer(String customerId, CustomerUpdateRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CustomerUpdateRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<Customer> response = restTemplate.exchange(
            baseUrl + "/api/v1/customers/" + customerId,
            HttpMethod.PUT,
            httpRequest,
            Customer.class
        );
        
        return response.getBody();
    }
    
    // ===== 设备管理服务 =====
    
    /**
     * 绑定设备
     */
    public Device bindDevice(DeviceBindRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<DeviceBindRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<Device> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/devices/bind",
            httpRequest,
            Device.class
        );
        
        return response.getBody();
    }
    
    /**
     * 查询客户设备
     */
    public List<Device> getCustomerDevices(String customerId) {
        String url = baseUrl + "/api/v1/devices/customer/" + customerId;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    // ===== 数据摄取服务 =====
    
    /**
     * 提交日志
     */
    public void ingestLog(String syslogMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        
        HttpEntity<String> httpRequest = new HttpEntity<>(syslogMessage, headers);
        
        restTemplate.postForEntity(
            baseUrl + "/api/v1/logs/ingest",
            httpRequest,
            Void.class
        );
        
        log.info("Log ingested successfully");
    }
    
    /**
     * 查询日志统计
     */
    public LogStats getLogStats() {
        String url = baseUrl + "/api/v1/logs/stats";
        return restTemplate.getForObject(url, LogStats.class);
    }
    
    // ===== 威胁评估服务 =====
    
    /**
     * 执行威胁评估
     */
    public AssessmentResponse evaluateThreat(AssessmentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AssessmentRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<AssessmentResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/assessment/evaluate",
            httpRequest,
            AssessmentResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 查询威胁趋势
     */
    public List<ThreatTrend> getThreatTrends(String customerId, int hours) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/assessment/trends")
            .queryParam("customerId", customerId)
            .queryParam("hours", hours)
            .toUriString();
            
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    // ===== 告警管理服务 =====
    
    /**
     * 查询告警列表
     */
    public Page<Alert> getAlerts(String severity, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/alerts");
            
        if (severity != null) builder.queryParam("severity", severity);
        if (status != null) builder.queryParam("status", status);
        builder.queryParam("page", page);
        builder.queryParam("size", size);
        
        String url = builder.toUriString();
        return restTemplate.getForObject(url, Page.class);
    }
    
    /**
     * 获取告警详情
     */
    public Alert getAlert(Long alertId) {
        String url = baseUrl + "/api/v1/alerts/" + alertId;
        return restTemplate.getForObject(url, Alert.class);
    }
    
    /**
     * 更新告警状态
     */
    public Alert updateAlertStatus(Long alertId, AlertStatusUpdate request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AlertStatusUpdate> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<Alert> response = restTemplate.exchange(
            baseUrl + "/api/v1/alerts/" + alertId + "/status",
            HttpMethod.PUT,
            httpRequest,
            Alert.class
        );
        
        return response.getBody();
    }
    
    // ===== 监控和健康检查 =====
    
    /**
     * 健康检查
     */
    public HealthStatus getHealth() {
        String url = baseUrl + "/actuator/health";
        return restTemplate.getForObject(url, HealthStatus.class);
    }
    
    /**
     * 获取路由配置
     */
    public List<RouteDefinition> getRoutes() {
        String url = baseUrl + "/actuator/gateway/routes";
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取性能指标
     */
    public Map<String, Object> getMetrics() {
        String url = baseUrl + "/actuator/metrics";
        return restTemplate.getForObject(url, Map.class);
    }
}

// ===== 数据模型 =====

/**
 * 客户请求
 */
class CustomerRequest {
    private String customerId;
    private String companyName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String subscriptionTier;
    
    // getters and setters
}

/**
 * 客户响应
 */
class Customer {
    private Long id;
    private String customerId;
    private String companyName;
    private String contactName;
    private String contactEmail;
    private String subscriptionTier;
    private boolean isActive;
    private Long createdAt;
    
    // getters and setters
}

/**
 * 设备绑定请求
 */
class DeviceBindRequest {
    private String customerId;
    private String devSerial;
    private String deviceName;
    private String location;
    
    // getters and setters
}

/**
 * 设备信息
 */
class Device {
    private Long id;
    private String customerId;
    private String devSerial;
    private String deviceName;
    private String location;
    private boolean isActive;
    
    // getters and setters
}

/**
 * 日志统计
 */
class LogStats {
    private long totalProcessed;
    private long parsedSuccessfully;
    private long parsingFailed;
    private String lastUpdated;
    
    // getters and setters
}

/**
 * 威胁评估请求
 */
class AssessmentRequest {
    private String customerId;
    private String attackMac;
    private int attackCount;
    private int uniqueIps;
    private int uniquePorts;
    private int uniqueDevices;
    
    // getters and setters
}

/**
 * 威胁评估响应
 */
class AssessmentResponse {
    private String customerId;
    private String attackMac;
    private double threatScore;
    private String threatLevel;
    private String assessmentTime;
    
    // getters and setters
}

/**
 * 威胁趋势
 */
class ThreatTrend {
    private String customerId;
    private String timeWindow;
    private double avgThreatScore;
    private int totalAlerts;
    
    // getters and setters
}

/**
 * 分页响应
 */
class Page<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    
    // getters and setters
}

/**
 * 告警信息
 */
class Alert {
    private Long id;
    private String title;
    private String severity;
    private String status;
    private String attackMac;
    private String attackIp;
    private String customerId;
    private double threatScore;
    private String createdAt;
    
    // getters and setters
}

/**
 * 告警状态更新请求
 */
class AlertStatusUpdate {
    private String status;
    private String resolvedBy;
    
    // getters and setters
}

/**
 * 健康状态
 */
class HealthStatus {
    private String status;
    private Map<String, Object> details;
    
    // getters and setters
}

/**
 * 路由定义
 */
class RouteDefinition {
    private String routeId;
    private String uri;
    private int order;
    private String predicate;
    private List<String> filters;
    
    // getters and setters
}
```

### 使用示例

```java
public class ApiGatewayExample {
    
    public static void main(String[] args) {
        ApiGatewayClient client = new ApiGatewayClient("http://localhost:8888");
        
        try {
            // 1. 创建客户
            CustomerRequest customerReq = new CustomerRequest();
            customerReq.setCustomerId("customer-001");
            customerReq.setCompanyName("示例公司");
            customerReq.setContactEmail("admin@example.com");
            customerReq.setSubscriptionTier("PROFESSIONAL");
            
            Customer customer = client.createCustomer(customerReq);
            System.out.println("Created customer: " + customer.getCustomerId());
            
            // 2. 绑定设备
            DeviceBindRequest deviceReq = new DeviceBindRequest();
            deviceReq.setCustomerId("customer-001");
            deviceReq.setDevSerial("DEV-001");
            deviceReq.setDeviceName("办公室网关");
            
            Device device = client.bindDevice(deviceReq);
            System.out.println("Bound device: " + device.getDevSerial());
            
            // 3. 提交测试日志
            String syslogMessage = "syslog_version=1.10.0,dev_serial=DEV-001,log_type=1,...";
            client.ingestLog(syslogMessage);
            
            // 4. 执行威胁评估
            AssessmentRequest assessReq = new AssessmentRequest();
            assessReq.setCustomerId("customer-001");
            assessReq.setAttackMac("00:11:22:33:44:55");
            assessReq.setAttackCount(150);
            assessReq.setUniqueIps(5);
            assessReq.setUniquePorts(3);
            assessReq.setUniqueDevices(1);
            
            AssessmentResponse assessment = client.evaluateThreat(assessReq);
            System.out.println("Threat level: " + assessment.getThreatLevel());
            
            // 5. 查询告警
            Page<Alert> alerts = client.getAlerts("CRITICAL", null, 0, 20);
            System.out.println("Found " + alerts.getTotalElements() + " alerts");
            
            // 6. 健康检查
            HealthStatus health = client.getHealth();
            System.out.println("Gateway health: " + health.getStatus());
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

### 错误处理

```java
public class ApiGatewayErrorHandling {
    
    public void handleErrors(ApiGatewayClient client) {
        try {
            // 尝试调用API
            client.getCustomer("non-existent-customer");
            
        } catch (HttpClientErrorException.NotFound e) {
            // 404错误 - 资源不存在
            System.err.println("Customer not found: " + e.getMessage());
            
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429错误 - 限流触发
            System.err.println("Rate limit exceeded: " + e.getMessage());
            // 等待后重试或减少请求频率
            
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            // 503错误 - 服务不可用（熔断）
            System.err.println("Service unavailable: " + e.getMessage());
            // 实现重试逻辑或降级处理
            
        } catch (ResourceAccessException e) {
            // 网络错误
            System.err.println("Network error: " + e.getMessage());
            // 检查网络连接或Gateway状态
            
        } catch (Exception e) {
            // 其他错误
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }
}
```

---

## 配置说明

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SERVER_PORT` | 8080 | Gateway监听端口 |
| `REDIS_HOST` | redis | Redis主机名 |
| `REDIS_PORT` | 6379 | Redis端口 |
| `RATE_LIMIT_ENABLED` | true | 是否启用限流 |
| `RATE_LIMIT_CAPACITY` | 100 | 令牌桶容量 |
| `RATE_LIMIT_REFILL_RATE` | 10 | 每秒填充令牌数 |

### 限流配置

**默认限流策略**：
- **算法**: 令牌桶（Token Bucket）
- **容量**: 100个令牌
- **填充速率**: 10令牌/秒
- **粒度**: 基于客户端IP
- **限流响应**: 429 Too Many Requests

**修改限流参数**：
```bash
# docker-compose.yml
environment:
  RATE_LIMIT_CAPACITY: 200  # 增加容量
  RATE_LIMIT_REFILL_RATE: 20  # 提高填充速率
```

### CORS配置

**默认CORS设置**：
- **允许的来源**: `*` (所有来源，开发环境)
- **允许的方法**: GET, POST, PUT, DELETE, PATCH, OPTIONS
- **允许的Header**: Authorization, Content-Type, Accept, X-Customer-Id
- **允许Cookie**: 是
- **预检缓存**: 3600秒

⚠️ **生产环境建议**：在 `CorsConfig.java` 中修改 `allowedOriginPatterns` 为具体的前端域名。

---

## 监控端点

### Actuator端点

| 端点 | 说明 | 示例 |
|------|------|------|
| `/actuator/health` | 健康检查 | `curl http://localhost:8888/actuator/health` |
| `/actuator/metrics` | 性能指标 | `curl http://localhost:8888/actuator/metrics` |
| `/actuator/prometheus` | Prometheus格式指标 | `curl http://localhost:8888/actuator/prometheus` |
| `/actuator/gateway/routes` | 路由配置 | `curl http://localhost:8888/actuator/gateway/routes` |
| `/actuator/gateway/filters` | 过滤器列表 | `curl http://localhost:8888/actuator/gateway/filters` |

### 查看路由配置

```bash
curl http://localhost:8888/actuator/gateway/routes | jq
```

**响应示例**:
```json
[
  {
    "route_id": "customer-management-customers",
    "uri": "http://customer-management:8084",
    "order": 0,
    "predicate": "Paths: [/api/v1/customers/**], match trailing slash: true",
    "filters": [
      "[[StripPrefix parts = 0], order = 1]",
      "[[AddRequestHeader name = 'X-Gateway-Service', value = 'customer-management'], order = 0]"
    ]
  }
]
```

### 性能指标

```bash
# 查看所有指标
curl http://localhost:8888/actuator/metrics | jq

# 查看特定指标
curl http://localhost:8888/actuator/metrics/jvm.memory.used | jq
curl http://localhost:8888/actuator/metrics/http.server.requests | jq
```

---

## 熔断降级

### 降级响应

当后端服务不可用时，Gateway会返回友好的降级响应：

```json
{
  "error": "Service Unavailable",
  "message": "Customer Management Service is temporarily unavailable. Please try again later.",
  "timestamp": "2025-10-19T12:00:00Z",
  "status": 503
}
```

### 降级端点

| 路径 | 说明 |
|------|------|
| `/fallback/customer-management` | Customer Management降级 |
| `/fallback/data-ingestion` | Data Ingestion降级 |
| `/fallback/threat-assessment` | Threat Assessment降级 |
| `/fallback/alert-management` | Alert Management降级 |

---

## 故障排查

### 1. Gateway无法启动

**症状**: 容器启动失败或健康检查失败

**检查步骤**:
```bash
# 1. 查看日志
docker-compose logs api-gateway

# 2. 检查端口占用
netstat -tlnp | grep 8888

# 3. 检查依赖服务
docker-compose ps redis customer-management data-ingestion

# 4. 验证配置
docker-compose config | grep -A 20 api-gateway
```

### 2. 路由404错误

**症状**: 访问 `http://localhost:8888/api/v1/customers` 返回404

**检查步骤**:
```bash
# 1. 验证路由配置
curl http://localhost:8888/actuator/gateway/routes

# 2. 检查后端服务
curl http://localhost:8084/api/v1/customers

# 3. 检查hostname解析
docker-compose exec api-gateway ping customer-management

# 4. 检查容器网络
docker network inspect docker_default
```

### 3. 限流触发过于频繁

**症状**: 频繁收到429 Too Many Requests

**解决方法**:
```bash
# 方法1: 调整限流参数
docker-compose up -d api-gateway \
  -e RATE_LIMIT_CAPACITY=200 \
  -e RATE_LIMIT_REFILL_RATE=20

# 方法2: 修改配置后重启
vim docker/docker-compose.yml  # 修改环境变量
docker-compose restart api-gateway
```

### 4. CORS错误

**症状**: 浏览器报CORS错误

**检查步骤**:
```bash
# 1. 测试预检请求
curl -X OPTIONS http://localhost:8888/api/v1/customers \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -I

# 2. 检查响应头
# 应包含:
# Access-Control-Allow-Origin: http://localhost:3000
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
# Access-Control-Allow-Credentials: true
```

### 5. 熔断器未触发

**症状**: 后端服务停止但未返回降级响应

**检查步骤**:
```bash
# 1. 停止后端服务测试
docker-compose stop customer-management

# 2. 测试访问
curl http://localhost:8888/api/v1/customers

# 3. 查看Gateway日志
docker-compose logs api-gateway | grep -i "circuit"

# 4. 检查熔断器配置
curl http://localhost:8888/actuator/circuitbreakers
```

---

## 性能优化

### 1. 连接池配置

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 500
          max-pending-acquires: 1000
          max-idle-time: 30s
```

### 2. 超时配置

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 3000  # 连接超时
        response-timeout: 10s  # 响应超时
```

### 3. JVM调优

```bash
# Dockerfile中添加
ENTRYPOINT ["java", \
  "-Xmx2g", \
  "-Xms512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+UseContainerSupport", \
  "-Dspring.profiles.active=docker", \
  "-jar", "app.jar"]
```

### 4. Redis限流优化（生产环境）

当前使用内存限流（ConcurrentHashMap），生产环境建议升级为Redis分布式限流：

```java
@Bean
public RedisRateLimiter redisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
    return new RedisRateLimiter(10, 100, 1);  // replenishRate, burstCapacity, requestedTokens
}
```

---

## 测试

### 功能测试

```bash
# 运行完整测试脚本
./scripts/test_api_gateway.sh
```

测试脚本包含8个测试项：
1. ✅ 健康检查
2. ✅ Customer Management路由
3. ✅ 查询客户
4. ✅ Data Ingestion路由
5. ✅ Threat Assessment路由
6. ✅ Alert Management路由
7. ✅ CORS配置
8. ✅ 限流功能

### 压力测试

```bash
# 使用Apache Benchmark
ab -n 1000 -c 100 http://localhost:8888/api/v1/customers

# 使用wrk
wrk -t12 -c400 -d30s http://localhost:8888/api/v1/customers

# 使用hey
hey -n 1000 -c 50 http://localhost:8888/api/v1/customers
```

---

## 下一步开发计划

### 短期计划（1-2周）

- [ ] **JWT认证集成**
  - 实现JwtAuthenticationFilter
  - 配置公钥/私钥验证
  - Token黑名单机制

- [ ] **Redis分布式限流**
  - 替换内存TokenBucket为Redis实现
  - 支持多实例部署
  - 限流指标监控

### 中期计划（1个月）

- [ ] **API版本管理**
  - 支持/api/v1/**, /api/v2/**
  - 版本路由规则
  - 版本迁移策略

- [ ] **监控增强**
  - 集成Grafana Dashboard
  - 配置Prometheus告警规则
  - API调用统计分析

### 长期计划（2-3个月）

- [ ] **分布式追踪**
  - 集成Zipkin/Jaeger
  - 请求链路追踪
  - 性能瓶颈分析

- [ ] **服务网格集成**
  - Istio集成
  - 高级流量管理
  - 灰度发布支持

---

## 附录

### A. 项目结构

```
services/api-gateway/
├── src/main/
│   ├── java/com/threatdetection/gateway/
│   │   ├── ApiGatewayApplication.java      # 主应用类 + 路由配置
│   │   ├── config/
│   │   │   └── CorsConfig.java             # CORS跨域配置
│   │   ├── filter/
│   │   │   ├── RequestLoggingFilter.java   # 请求日志过滤器
│   │   │   └── RateLimitFilter.java        # 限流过滤器
│   │   ├── controller/
│   │   │   └── FallbackController.java     # 熔断降级控制器
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java # 全局异常处理
│   └── resources/
│       ├── application.yml                  # 主配置文件
│       └── application-docker.yml           # Docker配置
├── Dockerfile                               # 容器化配置
├── .dockerignore                            # Docker忽略文件
└── pom.xml                                  # Maven依赖配置
```

### B. 依赖版本

```xml
<spring-boot.version>3.1.5</spring-boot.version>
<spring-cloud.version>2022.0.4</spring-cloud.version>
<java.version>21</java.version>

主要依赖:
- spring-cloud-starter-gateway
- spring-boot-starter-actuator
- spring-boot-starter-data-redis-reactive
- io.jsonwebtoken:jjwt-api:0.11.5
- micrometer-registry-prometheus
```

### C. 端口映射

| 服务 | 容器内端口 | 宿主机端口 | 说明 |
|------|-----------|-----------|------|
| API Gateway | 8080 | 8888 | 统一API入口 |
| Customer Management | 8084 | 8084 | 客户管理服务 |
| Data Ingestion | 8080 | 8080 | 日志摄取服务 |
| Threat Assessment | 8081 | 8083 | 威胁评估服务 |
| Alert Management | 8084 | 8082 | 告警管理服务 |

---

**文档版本**: 1.0  
**最后更新**: 2025-10-19  
**维护者**: Threat Detection Team
