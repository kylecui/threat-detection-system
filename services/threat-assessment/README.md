# Threat Assessment Service

Advanced threat evaluation and risk assessment service that processes threat alerts and provides comprehensive risk analysis, mitigation recommendations, and historical trend analysis.

## Features

- **Risk Assessment Engine**: Multi-dimensional threat evaluation with contextual analysis
- **Historical Trend Analysis**: Long-term threat pattern recognition and trend prediction
- **Mitigation Recommendations**: Automated security response suggestions
- **Alert Correlation**: Cross-reference multiple alerts for attack campaign detection
- **Risk Scoring**: Dynamic risk scoring based on threat intelligence and historical data
- **RESTful API**: Comprehensive API for threat assessment and reporting

## Architecture

The service operates as a downstream processor that:

1. **Consumes Threat Alerts**: Processes alerts from `threat-alerts` Kafka topic
2. **Enrichment**: Adds contextual information and threat intelligence
3. **Risk Evaluation**: Applies advanced risk scoring algorithms
4. **Correlation Analysis**: Identifies attack campaigns and patterns
5. **Recommendation Engine**: Generates mitigation strategies
6. **Persistence**: Stores assessment results for historical analysis

## Risk Assessment Algorithm

Advanced risk scoring combining multiple factors:

```
riskScore = baseThreatScore × contextMultiplier × trendMultiplier × intelligenceMultiplier
```

### Multipliers

- **contextMultiplier**: Based on asset value, network segment, and business impact
- **trendMultiplier**: Historical trend analysis and frequency patterns
- **intelligenceMultiplier**: External threat intelligence correlation

## API Endpoints

### POST /api/v1/assessment/evaluate
Evaluate a threat alert and provide risk assessment.

**Request Body:**
```json
{
  "alertId": "alert-12345",
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 18.48,
  "threatLevel": "MEDIUM",
  "timestamp": 1728465600000,
  "attackPatterns": ["port_scan", "brute_force"],
  "affectedAssets": ["web_server", "database"]
}
```

**Response:**
```json
{
  "assessmentId": "assessment-67890",
  "riskLevel": "HIGH",
  "riskScore": 750.5,
  "confidence": 0.85,
  "recommendations": [
    {
      "action": "BLOCK_IP",
      "priority": "CRITICAL",
      "description": "Block source IP 192.168.1.100"
    },
    {
      "action": "INCREASE_MONITORING",
      "priority": "HIGH",
      "description": "Enable enhanced logging for port 80"
    }
  ],
  "threatIntelligence": {
    "knownAttacker": false,
    "campaignId": null,
    "similarIncidents": 3
  },
  "assessmentTimestamp": 1728465605000
}
```

### GET /api/v1/assessment/{assessmentId}
Retrieve detailed assessment information.

### GET /api/v1/assessment/trends
Get threat trends and statistics.

**Query Parameters:**
- `startTime`: Start timestamp (Unix milliseconds)
- `endTime`: End timestamp (Unix milliseconds)
- `threatLevel`: Filter by threat level
- `limit`: Maximum results (default: 100)

**Response:**
```json
{
  "trends": [
    {
      "timeBucket": "2025-10-09T10:00:00Z",
      "threatLevels": {
        "CRITICAL": 2,
        "HIGH": 15,
        "MEDIUM": 45,
        "LOW": 120,
        "INFO": 300
      },
      "topAttackTypes": ["port_scan", "brute_force"],
      "riskScoreAverage": 125.5
    }
  ],
  "summary": {
    "totalAssessments": 482,
    "averageRiskScore": 95.2,
    "mostCommonThreat": "port_scan",
    "trendDirection": "increasing"
  }
}
```

### POST /api/v1/assessment/mitigation/{assessmentId}
Execute mitigation actions for an assessment.

## Data Models

### ThreatAssessment
```java
public class ThreatAssessment {
    private String assessmentId;
    private String alertId;
    private RiskLevel riskLevel;
    private double riskScore;
    private double confidence;
    private List<Recommendation> recommendations;
    private ThreatIntelligence threatIntelligence;
    private LocalDateTime assessmentTimestamp;
}
```

### Recommendation
```java
public class Recommendation {
    private MitigationAction action;
    private Priority priority;
    private String description;
    private Map<String, Object> parameters;
}
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | Kafka broker addresses |
| `INPUT_TOPIC` | `threat-alerts` | Input topic for threat alerts |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/threatdb` | Database connection URL |
| `REDIS_URL` | `redis://redis:6379` | Redis cache URL |
| `THREAT_INTELLIGENCE_API` | - | External threat intelligence API |

### Application Properties

```yaml
spring:
  kafka:
    consumer:
      group-id: threat-assessment-group
      auto-offset-reset: earliest
    producer:
      retries: 3
      batch-size: 16384

  datasource:
    url: jdbc:postgresql://localhost:5432/threatdb
    username: threat_user
    password: ${DB_PASSWORD}

assessment:
  risk-thresholds:
    critical: 1000
    high: 500
    medium: 100
    low: 10
  cache-ttl: 3600
  intelligence-enabled: true
```

## Running the Service

### Prerequisites
- Java 21 LTS
- PostgreSQL 15+
- Redis 7+
- Kafka 3.4+
- Maven 3.8+

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Service will be available at http://localhost:8083
```

### Manual Build and Run
```bash
# Build the service
mvn clean compile

# Run with Spring Boot
mvn spring-boot:run

# Run as JAR
java -jar target/threat-assessment-1.0.jar
```

### Database Setup
```sql
-- Create database
CREATE DATABASE threatdb;

-- Create user
CREATE USER threat_user WITH PASSWORD 'secure_password';

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE threatdb TO threat_user;
```

## Monitoring

### Health Checks
```bash
# Service health
curl http://localhost:8083/actuator/health

# Database connectivity
curl http://localhost:8083/actuator/health/db

# Kafka connectivity
curl http://localhost:8083/actuator/health/kafka
```

### Metrics
```bash
# Prometheus metrics
curl http://localhost:8083/actuator/prometheus

# Custom metrics
curl http://localhost:8083/actuator/metrics/assessment.duration
```

### Key Metrics
- `assessment.requests.total`: Total assessment requests
- `assessment.duration`: Assessment processing time
- `assessment.risk_levels`: Distribution by risk level
- `recommendations.generated.total`: Mitigation recommendations created
- `threat_intelligence.calls`: External API calls

## Development

### Project Structure
```
src/main/java/com/threatdetection/assessment/
├── controller/         # REST API controllers
├── service/           # Business logic services
│   ├── assessment/    # Risk assessment logic
│   ├── intelligence/  # Threat intelligence integration
│   ├── correlation/   # Alert correlation engine
│   └── mitigation/    # Recommendation engine
├── model/             # Data models and DTOs
├── repository/        # Data access layer
├── config/            # Configuration classes
└── ThreatAssessmentApplication.java
```

### Key Components
- `RiskAssessmentService`: Core risk evaluation logic
- `ThreatIntelligenceService`: External threat intelligence integration
- `CorrelationEngine`: Attack campaign detection
- `RecommendationEngine`: Mitigation strategy generation
- `AssessmentController`: REST API endpoints

### Testing
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Performance tests
mvn test -Dtest="*PerformanceTest"
```

## Security Considerations

- **API Authentication**: JWT-based authentication for API access
- **Data Encryption**: Sensitive assessment data encrypted at rest
- **Rate Limiting**: API rate limiting to prevent abuse
- **Audit Logging**: Comprehensive audit trail for all assessments
- **Input Validation**: Strict input validation and sanitization

## Integration Examples

### With SIEM Systems
```python
import requests

# Submit alert for assessment
response = requests.post('http://localhost:8083/api/v1/assessment/evaluate',
    json={
        'alertId': 'siem-123',
        'attackMac': '00:11:22:33:44:55',
        'threatScore': 25.0,
        'threatLevel': 'HIGH',
        'attackPatterns': ['sql_injection'],
        'affectedAssets': ['customer_db']
    })

assessment = response.json()
print(f"Risk Level: {assessment['riskLevel']}")
```

### With Orchestration Tools
```bash
# Automated mitigation execution
curl -X POST http://localhost:8083/api/v1/assessment/mitigation/assessment-67890 \
  -H "Authorization: Bearer $TOKEN"
```

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   ```bash
   # Check database status
   docker-compose logs postgres

   # Verify connection string
   curl http://localhost:8083/actuator/health
   ```

2. **High Memory Usage**
   ```bash
   # Check JVM settings
   java -XX:+PrintHeapDumpOnOutOfMemoryError

   # Monitor with VisualVM
   ```

3. **Slow Assessment Times**
   ```bash
   # Check metrics
   curl http://localhost:8083/actuator/metrics

   # Optimize database queries
   # Enable caching
   ```

### Performance Tuning
```yaml
# Database connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

# Redis caching
cache:
  ttl: 1800
  size: 10000

# Async processing
assessment:
  async:
    core-pool-size: 4
    max-pool-size: 8
```

## Current Implementation Status

- ✅ **Risk Assessment Engine**: Multi-dimensional threat evaluation with contextual analysis
- ✅ **Historical Trend Analysis**: Long-term threat pattern recognition and trend prediction
- ✅ **Database Persistence**: PostgreSQL storage with JPA/Hibernate
- ✅ **Kafka Integration**: Consumer for threat-alerts topic
- ✅ **RESTful API**: Comprehensive API for threat assessment and reporting
- ✅ **Risk Scoring**: Dynamic risk scoring based on threat intelligence
- ✅ **Mitigation Recommendations**: Automated security response suggestions
- ✅ **Alert Correlation**: Cross-reference multiple alerts for attack campaign detection
- ✅ **Health Monitoring**: Comprehensive health checks and metrics</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/services/threat-assessment/README.md