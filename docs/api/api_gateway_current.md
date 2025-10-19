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
    "customerId": "customer-001",
    "companyName": "示例公司",
    "contactName": "张三",
    "contactEmail": "admin@example.com",
    "contactPhone": "13800138000",
    "subscriptionTier": "PROFESSIONAL"
  }'
```

**响应**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "companyName": "示例公司",
  "contactName": "张三",
  "contactEmail": "admin@example.com",
  "subscriptionTier": "PROFESSIONAL",
  "isActive": true,
  "createdAt": "2025-10-19T12:00:00Z"
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
    "companyName": "更新后的公司名",
    "contactEmail": "newemail@example.com"
  }'
```

### 设备管理API

#### 绑定设备
```bash
curl -X POST http://localhost:8888/api/v1/devices/bind \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "deviceSerial": "DEV-001",
    "deviceName": "办公室1号设备",
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
    "customerId": "customer-001",
    "notificationType": "EMAIL",
    "minSeverity": "HIGH",
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
  "totalProcessed": 150000,
  "parsedSuccessfully": 148500,
  "parsingFailed": 1500,
  "lastUpdated": "2025-10-19T12:00:00Z"
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
  -d '{"status": "RESOLVED", "resolvedBy": "admin"}'
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
