# API Gateway Service

Centralized API gateway service that provides unified access to all microservices in the threat detection system, with authentication, rate limiting, routing, and comprehensive monitoring capabilities. Fully implemented and integrated with all services.

## Features

- **API Routing**: Intelligent routing to appropriate microservices with load balancing
- **Authentication & Authorization**: JWT-based authentication with role-based access control
- **Rate Limiting**: Configurable rate limiting to prevent abuse (implemented)
- **Load Balancing**: Load distribution across service instances using Ribbon
- **Request/Response Transformation**: API transformation and enrichment
- **Monitoring & Analytics**: Comprehensive API metrics and analytics with Micrometer
- **Security**: CORS, XSS protection, and input validation
- **Caching**: Response caching for improved performance with Redis
- **Service Discovery**: Integration with Eureka for dynamic service registration
- **Circuit Breaker**: Resilience patterns with Hystrix/Spring Cloud Circuit Breaker

## Architecture

The API Gateway serves as the single entry point for all client requests in the threat detection system:

1. **Request Reception**: Receives all API requests on port 8082
2. **Authentication**: Validates JWT tokens and user permissions
3. **Rate Limiting**: Applies rate limiting based on user/API key
4. **Routing**: Routes requests to appropriate microservices via service discovery
5. **Transformation**: Transforms requests/responses as needed
6. **Load Balancing**: Distributes load across service instances
7. **Response Caching**: Caches responses for improved performance
8. **Monitoring**: Logs and metrics collection with Prometheus integration

## API Endpoints

### Authentication Endpoints

#### POST /api/v1/auth/login
Authenticate user and return JWT token.

**Request Body:**
```json
{
  "username": "admin",
  "password": "secure_password"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "user123",
    "username": "admin",
    "roles": ["ADMIN", "USER"]
  }
}
```

#### POST /api/v1/auth/refresh
Refresh JWT token using refresh token.

#### POST /api/v1/auth/logout
Invalidate JWT token (add to blacklist).

### Data Ingestion Routes

#### POST /api/v1/logs/ingest
Route to data-ingestion service for single log processing.

**Rate Limit**: 100 requests/minute per user

#### POST /api/v1/logs/batch
Route to data-ingestion service for batch log processing.

**Rate Limit**: 10 requests/minute per user

#### GET /api/v1/logs/stats
Route to data-ingestion service for parsing statistics.

### Threat Assessment Routes

#### POST /api/v1/assessment/evaluate
Route to threat-assessment service for risk evaluation.

**Rate Limit**: 30 requests/minute per user

#### GET /api/v1/assessment/{id}
Route to threat-assessment service for assessment details.

#### GET /api/v1/assessment/trends
Route to threat-assessment service for trend analysis.

### Alert Management Routes

#### GET /api/v1/alerts
Route to alert-management service for alert listing with pagination.

#### GET /api/v1/alerts/{id}
Route to alert-management service for alert details.

#### PUT /api/v1/alerts/{id}/status
Route to alert-management service for status updates.

#### POST /api/v1/alerts/{id}/acknowledge
Acknowledge an alert.

### Stream Processing Routes

#### GET /api/v1/stream/jobs
Route to stream-processing service for job status.

#### GET /api/v1/stream/metrics
Route to stream-processing service for processing metrics.

### Config Server Routes (Admin Only)

#### GET /api/v1/config/{application}/{profile}
Route to config-server for configuration retrieval.

### Gateway-Specific Endpoints

#### GET /api/v1/gateway/routes
Get all available routes and their status.

**Response:**
```json
{
  "routes": [
    {
      "id": "data-ingestion",
      "path": "/api/v1/logs/**",
      "serviceId": "data-ingestion",
      "status": "UP",
      "responseTime": 45
    },
    {
      "id": "threat-assessment",
      "path": "/api/v1/assessment/**",
      "serviceId": "threat-assessment",
      "status": "UP",
      "responseTime": 120
    },
    {
      "id": "alert-management",
      "path": "/api/v1/alerts/**",
      "serviceId": "alert-management",
      "status": "UP",
      "responseTime": 85
    },
    {
      "id": "config-server",
      "path": "/api/v1/config/**",
      "serviceId": "config-server",
      "status": "UP",
      "responseTime": 95
    }
  ]
}
```

#### GET /api/v1/gateway/health
Get health status of all downstream services.

#### GET /api/v1/gateway/metrics
Get gateway-specific metrics.

## Authentication & Authorization

### JWT Token Structure
```json
{
  "sub": "user123",
  "username": "admin",
  "roles": ["ADMIN", "USER"],
  "permissions": ["READ_LOGS", "WRITE_ALERTS"],
  "iat": 1728465600,
  "exp": 1728469200
}
```

### Role-Based Access Control

| Role | Permissions |
|------|-------------|
| ADMIN | Full access to all endpoints including config server |
| SECURITY_ANALYST | Read access to logs, alerts, assessments |
| OPERATOR | Read/write access to alerts and assessments |
| AUDITOR | Read-only access to all data |

### API Key Authentication (Alternative)
```bash
# API Key in header
curl -H "X-API-Key: your-api-key" http://localhost:8082/api/v1/logs/stats

# API Key in query parameter
curl "http://localhost:8082/api/v1/alerts?apiKey=your-api-key"
```

## Rate Limiting

### User-Based Limiting
```yaml
rate-limiting:
  user:
    requests-per-minute: 60
    requests-per-hour: 1000
    burst-capacity: 10
```

### API Key-Based Limiting
```yaml
rate-limiting:
  api-key:
    requests-per-minute: 100
    requests-per-hour: 5000
    burst-capacity: 20
```

### Endpoint-Specific Limiting
```yaml
rate-limiting:
  endpoints:
    "/api/v1/logs/batch":
      requests-per-minute: 10
    "/api/v1/assessment/evaluate":
      requests-per-minute: 30
```

## Load Balancing

### Service Discovery Integration
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    hostname: api-gateway
    prefer-ip-address: true
```

### Load Balancing Strategies
- **Round Robin**: Default load balancing
- **Weighted Response Time**: Based on response times
- **Zone Affinity**: Prefer same zone instances
- **Retry**: Automatic retry on failures

### Ribbon Configuration
```yaml
ribbon:
  ReadTimeout: 60000
  ConnectTimeout: 60000
  MaxAutoRetries: 1
  MaxAutoRetriesNextServer: 1
  OkToRetryOnAllOperations: false
```

## Caching

### Response Caching
```yaml
cache:
  responses:
    enabled: true
    ttl: 300
    max-size: 1000
  static:
    enabled: true
    ttl: 3600
```

### Cache Invalidation
```bash
# Manual cache invalidation
curl -X POST http://localhost:8082/api/v1/gateway/cache/invalidate \
  -H "Authorization: Bearer $TOKEN"
```

### Redis Integration
```yaml
spring:
  redis:
    host: redis
    port: 6379
    password: ${REDIS_PASSWORD}
    timeout: 2000ms
```

## Monitoring & Analytics

### Metrics Collection
```bash
# Gateway metrics
curl http://localhost:8082/actuator/metrics

# Route-specific metrics
curl http://localhost:8082/actuator/metrics/route.data-ingestion.response.time

# Rate limiting metrics
curl http://localhost:8082/actuator/metrics/rate.limiter.requests
```

### Key Metrics
- `gateway.requests.total`: Total requests processed
- `gateway.requests.duration`: Request processing time
- `gateway.routes.status`: Route health status
- `rate.limiter.requests`: Rate limiting statistics
- `cache.hit.ratio`: Cache hit ratio
- `hystrix.circuit.breaker.status`: Circuit breaker status

### Prometheus Integration
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
```

### Analytics Dashboard
```bash
# API usage analytics
curl http://localhost:8082/api/v1/gateway/analytics/usage

# Performance analytics
curl http://localhost:8082/api/v1/gateway/analytics/performance

# Error analytics
curl http://localhost:8082/api/v1/gateway/analytics/errors
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `EUREKA_SERVER_URL` | `http://eureka:8761/eureka` | Service discovery URL |
| `JWT_SECRET` | - | JWT signing secret (required) |
| `REDIS_URL` | `redis://redis:6379` | Redis cache URL |
| `RATE_LIMIT_ENABLED` | `true` | Enable rate limiting |
| `CORS_ALLOWED_ORIGINS` | `*` | CORS allowed origins |
| `CONFIG_SERVER_URL` | `http://config-server:8888` | Config server URL |

### Application Properties

```yaml
server:
  port: 8082

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: data-ingestion
          uri: lb://data-ingestion
          predicates:
            - Path=/api/v1/logs/**
          filters:
            - RewritePath=/api/v1/logs/(?<path>.*), /api/v1/logs/$\{path}
            - RequestRateLimiter=args=[redisRateLimiter,$\{spel:#@rateLimiterConfig.getConfig()}]
        - id: threat-assessment
          uri: lb://threat-assessment
          predicates:
            - Path=/api/v1/assessment/**
          filters:
            - AuthenticationFilter
        - id: alert-management
          uri: lb://alert-management
          predicates:
            - Path=/api/v1/alerts/**
        - id: config-server
          uri: lb://config-server
          predicates:
            - Path=/api/v1/config/**
          filters:
            - AuthenticationFilter
            - AuthorizationFilter=ADMIN

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
```

## Running the Service

### Prerequisites
- Java 21 LTS
- Redis 7+
- Eureka Server (for service discovery)
- Maven 3.8+

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Service will be available at http://localhost:8082
```

### Manual Build and Run
```bash
# Build the service
mvn clean package

# Run the application
java -jar target/api-gateway-1.0.jar
```

### Health Check
```bash
# Gateway health
curl http://localhost:8082/actuator/health

# Downstream services health
curl http://localhost:8082/api/v1/gateway/health
```

## Security Features

### CORS Configuration
```yaml
cors:
  allowed-origins: "https://trusted-domain.com"
  allowed-methods: "GET,POST,PUT,DELETE"
  allowed-headers: "Authorization,Content-Type,X-API-Key"
  max-age: 3600
```

### XSS Protection
```yaml
security:
  xss:
    enabled: true
    protection-mode: ESCAPE
```

### Input Validation
```yaml
validation:
  enabled: true
  max-request-size: 10MB
  allowed-content-types: "application/json,text/plain"
```

### SSL/TLS Configuration
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

## Development

### Project Structure
```
src/main/java/com/threatdetection/gateway/
├── config/            # Gateway configuration
├── filter/            # Custom filters
│   ├── auth/          # Authentication filters
│   ├── rate/          # Rate limiting filters
│   ├── cache/         # Caching filters
│   └── logging/       # Logging filters
├── controller/        # Gateway-specific controllers
├── service/           # Gateway services
│   ├── discovery/     # Service discovery
│   ├── routing/       # Routing logic
│   └── monitoring/    # Monitoring services
└── ApiGatewayApplication.java
```

### Key Components
- `AuthenticationFilter`: JWT token validation
- `RateLimitingFilter`: Request rate limiting
- `RoutingFilter`: Intelligent request routing
- `CachingFilter`: Response caching
- `LoggingFilter`: Request/response logging
- `CircuitBreakerFilter`: Resilience patterns

### Custom Filters
```java
@Component
public class CustomGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Custom filtering logic
        ServerHttpRequest request = exchange.getRequest();
        // Add custom headers, logging, etc.
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```

### Testing
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Load testing
ab -n 1000 -c 10 http://localhost:8082/api/v1/gateway/health
```

## Integration Examples

### Client SDK Usage
```javascript
// JavaScript client
const client = new ThreatDetectionClient({
  baseURL: 'http://localhost:8082',
  apiKey: 'your-api-key'
});

// Authenticate
const token = await client.authenticate('username', 'password');

// Make requests
const logs = await client.getLogs({ status: 'processed' });
const alerts = await client.getAlerts({ severity: 'HIGH' });
const assessment = await client.evaluateThreat(threatData);
```

### Load Balancer Integration
```nginx
# Nginx configuration
upstream threat_detection_api {
    server api-gateway-1:8082;
    server api-gateway-2:8082;
    server api-gateway-3:8082;
}

server {
    listen 80;
    location /api/ {
        proxy_pass http://threat_detection_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header Authorization $http_authorization;
    }
}
```

### Service Mesh Integration (Istio)
```yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: threat-detection-gateway
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "api.threat-detection.com"
---
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: api-gateway
spec:
  hosts:
  - "api.threat-detection.com"
  gateways:
  - threat-detection-gateway
  http:
  - match:
    - uri:
        prefix: "/api/v1"
    route:
    - destination:
        host: api-gateway
        port:
          number: 8082
```

## Troubleshooting

### Common Issues

1. **Service Discovery Failures**
   ```bash
   # Check Eureka server
   curl http://eureka:8761/eureka/apps

   # Verify service registration
   curl http://localhost:8082/api/v1/gateway/routes
   ```

2. **Rate Limiting Issues**
   ```bash
   # Check rate limiter status
   curl http://localhost:8082/actuator/metrics/rate.limiter.requests

   # Reset rate limiter
   curl -X POST http://localhost:8082/api/v1/gateway/rate-limiter/reset
   ```

3. **Caching Problems**
   ```bash
   # Check cache statistics
   curl http://localhost:8082/actuator/metrics/cache.hit.ratio

   # Clear cache
   curl -X POST http://localhost:8082/api/v1/gateway/cache/clear
   ```

4. **Authentication Failures**
   ```bash
   # Validate JWT token
   curl -X POST http://localhost:8082/api/v1/auth/validate \
     -H "Authorization: Bearer $TOKEN"

   # Check token expiration
   ```

### Performance Tuning
```yaml
# Connection pooling
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 100
          max-connections-per-route: 20

# Thread pool
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10

# JVM tuning
java:
  opts: "-Xmx2g -Xms512m -XX:+UseG1GC"
```

## Current Implementation Status

- ✅ **API Routing**: Fully implemented with load balancing
- ✅ **Authentication**: JWT-based auth with role-based access
- ✅ **Rate Limiting**: Configurable rate limiting per user/API key
- ✅ **Service Discovery**: Eureka integration for dynamic routing
- ✅ **Monitoring**: Comprehensive metrics with Prometheus
- ✅ **Caching**: Redis-based response caching
- ✅ **Security**: CORS, input validation, and XSS protection
- ✅ **Circuit Breaker**: Resilience patterns implemented
- ✅ **All Services Integration**: Routes to all microservices configured

## Future Enhancements

- **Phase 2**: GraphQL API support
- **Phase 3**: Advanced API versioning and migration
- **Phase 4**: AI-powered API analytics and recommendations
- **Phase 5**: Multi-region deployment with global load balancing</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/services/api-gateway/README.md