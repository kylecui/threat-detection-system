# Alert Management Service

Comprehensive alert management and notification service that handles threat alerts, provides alert lifecycle management, notification routing, and integration with external systems.

## Features

- **Alert Lifecycle Management**: Complete alert tracking from creation to resolution
- **Multi-Channel Notifications**: Email, SMS, webhook, and integration notifications
- **Alert Deduplication**: Intelligent deduplication to prevent alert fatigue
- **Escalation Policies**: Automated escalation based on severity and response time
- **Integration APIs**: RESTful APIs for alert management and external system integration
- **Alert Analytics**: Comprehensive reporting and analytics on alert patterns

## Architecture

The service manages the complete alert lifecycle:

1. **Alert Ingestion**: Receives alerts from `threat-alerts` Kafka topic
2. **Deduplication**: Applies intelligent deduplication rules
3. **Enrichment**: Adds contextual information and routing rules
4. **Notification**: Routes alerts to appropriate channels and recipients
5. **Escalation**: Monitors response times and escalates as needed
6. **Resolution**: Tracks alert resolution and generates reports

## Alert Lifecycle

```
Created → Deduplicated → Enriched → Notified → Escalated → Resolved → Archived
```

### Alert States
- **NEW**: Newly created alert
- **DEDUPLICATED**: Duplicate of existing alert
- **ENRICHED**: Context and routing information added
- **NOTIFIED**: Notifications sent to recipients
- **ESCALATED**: Escalated due to lack of response
- **RESOLVED**: Alert resolved by operator
- **ARCHIVED**: Moved to long-term storage

## API Endpoints

### POST /api/v1/alerts
Create a new alert manually.

**Request Body:**
```json
{
  "title": "High Risk Threat Detected",
  "description": "Multiple port scan attempts from single IP",
  "severity": "HIGH",
  "source": "threat-detection-system",
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 750.5,
  "affectedAssets": ["web_server", "api_gateway"],
  "recommendations": ["Block IP", "Enable enhanced monitoring"]
}
```

### GET /api/v1/alerts
Retrieve alerts with filtering and pagination.

**Query Parameters:**
- `status`: Alert status (NEW, NOTIFIED, RESOLVED, etc.)
- `severity`: Alert severity (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- `source`: Alert source system
- `startTime`: Start timestamp
- `endTime`: End timestamp
- `page`: Page number (0-based)
- `size`: Page size (default: 20)

### GET /api/v1/alerts/{alertId}
Retrieve detailed alert information.

### PUT /api/v1/alerts/{alertId}/status
Update alert status.

**Request Body:**
```json
{
  "status": "RESOLVED",
  "resolution": "Blocked malicious IP address",
  "resolvedBy": "security_team",
  "resolutionTime": "2025-10-09T11:30:00Z"
}
```

### POST /api/v1/alerts/{alertId}/acknowledge
Acknowledge an alert.

### GET /api/v1/alerts/analytics
Get alert analytics and statistics.

**Response:**
```json
{
  "timeRange": {
    "start": "2025-10-09T00:00:00Z",
    "end": "2025-10-09T23:59:59Z"
  },
  "summary": {
    "totalAlerts": 145,
    "resolvedAlerts": 132,
    "averageResolutionTime": 1800,
    "escalatedAlerts": 8
  },
  "bySeverity": {
    "CRITICAL": 5,
    "HIGH": 25,
    "MEDIUM": 45,
    "LOW": 70
  },
  "byStatus": {
    "NEW": 10,
    "NOTIFIED": 15,
    "RESOLVED": 120
  },
  "topSources": [
    {"source": "threat-detection-system", "count": 98},
    {"source": "network-monitor", "count": 32},
    {"source": "endpoint-protection", "count": 15}
  ]
}
```

## Notification Channels

### Email Notifications
```json
{
  "channel": "EMAIL",
  "recipients": ["security@company.com", "admin@company.com"],
  "template": "threat_alert",
  "priority": "HIGH",
  "attachments": ["threat_details.pdf"]
}
```

### Webhook Notifications
```json
{
  "channel": "WEBHOOK",
  "url": "https://slack-webhook.company.com/alerts",
  "headers": {
    "Authorization": "Bearer token123",
    "Content-Type": "application/json"
  },
  "template": "slack_alert"
}
```

### SMS Notifications
```json
{
  "channel": "SMS",
  "recipients": ["+1234567890", "+0987654321"],
  "template": "sms_alert",
  "provider": "twilio"
}
```

## Escalation Policies

### Time-Based Escalation
```json
{
  "policyName": "critical_escalation",
  "triggers": [
    {
      "severity": "CRITICAL",
      "unacknowledgedTime": 300,
      "escalationLevel": 1,
      "notify": ["security_lead@company.com", "+1234567890"]
    },
    {
      "severity": "CRITICAL",
      "unacknowledgedTime": 900,
      "escalationLevel": 2,
      "notify": ["ciso@company.com", "security_team@company.com"]
    }
  ]
}
```

### Count-Based Escalation
```json
{
  "policyName": "volume_escalation",
  "triggers": [
    {
      "alertCount": 10,
      "timeWindow": 300,
      "severity": "HIGH",
      "escalationLevel": 1,
      "notify": ["security_team@company.com"]
    }
  ]
}
```

## Deduplication Rules

### Time-Based Deduplication
```json
{
  "ruleName": "time_window_dedup",
  "timeWindow": 300,
  "matchFields": ["attackMac", "sourceIp"],
  "action": "MERGE"
}
```

### Content-Based Deduplication
```json
{
  "ruleName": "content_dedup",
  "similarityThreshold": 0.8,
  "matchFields": ["description", "affectedAssets"],
  "action": "DISCARD"
}
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | Kafka broker addresses |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/alertdb` | Database connection URL |
| `REDIS_URL` | `redis://redis:6379` | Redis cache URL |
| `SMTP_HOST` | `smtp.company.com` | SMTP server for email |
| `SMS_PROVIDER` | `twilio` | SMS service provider |

### Application Properties

```yaml
spring:
  kafka:
    consumer:
      group-id: alert-management-group
      auto-offset-reset: earliest

  mail:
    host: ${SMTP_HOST}
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}

alert:
  deduplication:
    enabled: true
    time-window: 300
  escalation:
    enabled: true
    check-interval: 60
  notification:
    retry-attempts: 3
    retry-delay: 30
```

## Running the Service

### Prerequisites
- Java 21 LTS
- PostgreSQL 15+
- Redis 7+
- Kafka 3.4+
- SMTP server (for email notifications)

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Service will be available at http://localhost:8084
```

### Manual Build and Run
```bash
# Build the service
mvn clean package

# Run the application
java -jar target/alert-management-1.0.jar
```

### Database Setup
```sql
-- Create database
CREATE DATABASE alertdb;

-- Create user
CREATE USER alert_user WITH PASSWORD 'secure_password';

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE alertdb TO alert_user;
```

## Monitoring

### Health Checks
```bash
# Service health
curl http://localhost:8084/actuator/health

# Database health
curl http://localhost:8084/actuator/health/db

# External service health
curl http://localhost:8084/actuator/health/smtp
```

### Metrics
```bash
# Prometheus metrics
curl http://localhost:8084/actuator/prometheus

# Alert metrics
curl http://localhost:8084/actuator/metrics/alerts.created
```

### Key Metrics
- `alerts.created.total`: Total alerts created
- `alerts.deduplicated.total`: Alerts deduplicated
- `alerts.notified.total`: Successful notifications
- `alerts.escalated.total`: Escalated alerts
- `notifications.failed.total`: Failed notification attempts

## Development

### Project Structure
```
src/main/java/com/threatdetection/alert/
├── controller/         # REST API controllers
├── service/           # Business logic services
│   ├── alert/         # Alert management
│   ├── notification/  # Notification services
│   ├── escalation/    # Escalation logic
│   └── deduplication/ # Deduplication engine
├── model/             # Data models and DTOs
├── repository/        # Data access layer
├── config/            # Configuration classes
└── AlertManagementApplication.java
```

### Key Components
- `AlertService`: Core alert management logic
- `NotificationService`: Multi-channel notification handling
- `EscalationService`: Alert escalation management
- `DeduplicationService`: Intelligent deduplication engine
- `AlertController`: REST API endpoints

### Testing
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# End-to-end tests
mvn test -Dtest="*E2ETest"
```

## Integration Examples

### SIEM Integration
```python
import requests

# Create alert from SIEM
response = requests.post('http://localhost:8084/api/v1/alerts',
    json={
        'title': 'SIEM Alert',
        'description': 'Suspicious login attempts',
        'severity': 'HIGH',
        'source': 'siem_system',
        'affectedAssets': ['authentication_server']
    })

alert = response.json()
print(f"Alert created: {alert['id']}")
```

### Slack Integration
```bash
# Webhook configuration for Slack
curl -X POST http://localhost:8084/api/v1/notifications/webhooks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "slack_alerts",
    "url": "https://hooks.slack.com/services/...",
    "template": "slack_template"
  }'
```

## Security Considerations

- **API Security**: JWT authentication and role-based access control
- **Data Protection**: Alert data encryption and PII masking
- **Rate Limiting**: API rate limiting and abuse prevention
- **Audit Logging**: Comprehensive audit trail for all alert operations
- **Notification Security**: Secure webhook signatures and API keys

## Troubleshooting

### Common Issues

1. **Notification Failures**
   ```bash
   # Check SMTP configuration
   curl http://localhost:8084/actuator/health/smtp

   # View notification logs
   docker-compose logs alert-management
   ```

2. **Database Performance**
   ```bash
   # Check slow queries
   # Enable query logging
   # Optimize indexes
   ```

3. **Escalation Not Working**
   ```bash
   # Verify escalation policies
   curl http://localhost:8084/api/v1/escalation/policies

   # Check scheduler status
   curl http://localhost:8084/actuator/scheduledtasks
   ```

### Performance Tuning
```yaml
# Database optimization
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

# Async processing
alert:
  async:
    core-pool-size: 4
    max-pool-size: 16
    queue-capacity: 1000

# Caching
cache:
  alert-ttl: 3600
  user-ttl: 7200
```

## Current Implementation Status

- ✅ **Alert Lifecycle Management**: Complete alert tracking from creation to resolution
- ✅ **Multi-Channel Notifications**: Email, SMS, webhook, and integration notifications
- ✅ **Alert Deduplication**: Intelligent deduplication to prevent alert fatigue
- ✅ **Escalation Policies**: Automated escalation based on severity and response time
- ✅ **Integration APIs**: RESTful APIs for alert management and external system integration
- ✅ **Alert Analytics**: Comprehensive reporting and analytics on alert patterns
- ✅ **Kafka Integration**: Consumer for threat-alerts topic with automatic alert creation
- ✅ **Database Persistence**: PostgreSQL storage with JPA/Hibernate
- ✅ **Notification Templates**: Configurable templates for different channels
- ✅ **Health Monitoring**: Comprehensive health checks and metrics</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/services/alert-management/README.md