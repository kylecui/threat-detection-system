# Data Ingestion Service

This service handles the ingestion and processing of threat detection logs from Logstash, with enhanced error handling, batch processing, and comprehensive monitoring capabilities.

## Features

- **Enhanced Log Parsing**: Supports both JSON and plain text log formats with robust error handling
- **Data Validation**: Comprehensive validation for IP addresses, MAC addresses, ports, and timestamps
- **Batch Processing**: High-performance asynchronous batch log processing
- **Kafka Optimization**: Configured with connection pooling, retries, batching, and compression
- **Monitoring & Metrics**: Micrometer-based metrics with Prometheus support
- **REST API**: Single and batch log ingestion endpoints
- **Health Checks**: Comprehensive health monitoring

## API Endpoints

### POST /api/v1/logs/ingest
Ingests a single log entry for processing.

**Request Body:** Raw log string (plain text or JSON format)
**Content-Type:** `text/plain`

**Response:**
- `200 OK`: Event processed successfully
- `400 Bad Request`: Failed to process log
- `500 Internal Server Error`: Server error

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6"
```

### POST /api/v1/logs/batch
**Phase 1A: New Feature** - Ingests multiple log entries in a single request for improved throughput.

**Request Body:** JSON array of log strings
**Content-Type:** `application/json`
**Limit:** Maximum 1000 logs per request

**Request Format:**
```json
{
  "logs": [
    "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,...",
    "syslog_version=1.10.0,dev_serial=DEF456,log_type=2,..."
  ]
}
```

**Response Format:**
```json
{
  "totalCount": 2,
  "successCount": 2,
  "errorCount": 0,
  "results": [
    {
      "logId": "attack-ABC123-1728465600-0",
      "success": true,
      "eventType": "ATTACK"
    },
    {
      "logId": "status-DEF456-1728462000-1",
      "success": true,
      "eventType": "STATUS"
    }
  ],
  "processingTimeMs": 45
}
```

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/logs/batch \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6",
      "syslog_version=1.10.0,dev_serial=ABC123,log_type=2,sentry_count=5,real_host_count=10,dev_start_time=1728462000,dev_end_time=1728465600,time=2025-10-09 10:00:00"
    ]
  }'
```

### GET /api/v1/logs/stats
**Phase 1A: New Feature** - Retrieves parsing statistics for monitoring.

**Response:** JSON object with parsing metrics
```json
{
  "attack_events_parsed": 150,
  "status_events_parsed": 45,
  "invalid_input": 3,
  "invalid_content": 2,
  "extraction_failed": 1,
  "unknown_log_type": 0,
  "attack_parse_failed": 2,
  "status_parse_failed": 1,
  "unexpected_errors": 0
}
```

### POST /api/v1/logs/stats/reset
**Phase 1A: New Feature** - Resets parsing statistics counters.

**Response:** `"Statistics reset successfully"`

### GET /api/v1/logs/health
Health check endpoint.

**Response:** `"Log Ingestion Service is healthy"`

## Log Formats

### Attack Log (log_type=1)
```
syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6
```

**Required Fields:**
- `syslog_version`: Version string (e.g., "1.10.0")
- `dev_serial`: Device serial (hexadecimal)
- `log_type`: Must be "1" for attack logs
- `sub_type`: Attack sub-type
- `attack_mac`: Attacker MAC address (format: XX:XX:XX:XX:XX:XX)
- `attack_ip`: Attacker IP address (valid IPv4)
- `response_ip`: Target IP address (valid IPv4)
- `response_port`: Target port (1-65535)
- `line_id`: Line identifier
- `Iface_type`: Interface type
- `Vlan_id`: VLAN identifier
- `log_time`: Unix timestamp
- `eth_type`: Ethernet type
- `ip_type`: IP protocol type

### Status Log (log_type=2)
```
syslog_version=1.10.0,dev_serial=ABC123,log_type=2,sentry_count=5,real_host_count=10,dev_start_time=1728462000,dev_end_time=1728465600,time=2025-10-09 10:00:00
```

**Required Fields:**
- `syslog_version`: Version string
- `dev_serial`: Device serial (hexadecimal)
- `log_type`: Must be "2" for status logs
- `sentry_count`: Number of sentries (≥0)
- `real_host_count`: Number of real hosts (≥0)
- `dev_start_time`: Device start timestamp
- `dev_end_time`: Device end timestamp (-1 for ongoing)
- `time`: Formatted timestamp

### JSON Format Support
**Phase 1A: New Feature** - The service now supports JSON-formatted logs:

```json
{
  "@timestamp": "2025-10-09T10:00:00.000Z",
  "host": "sensor-01",
  "message": "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6",
  "type": "threat-log"
}
```

## Data Validation

**Phase 1A: Enhanced Validation** - The service performs comprehensive validation:

- **Input Validation**: Null checks, length limits (max 10KB), required field presence
- **IP Address**: Valid IPv4 format validation
- **MAC Address**: Standard colon-separated hex format
- **Port Numbers**: Range validation (1-65535)
- **Timestamps**: Reasonable date range validation (1970-2100)
- **Device Serial**: Hexadecimal format validation
- **Counts**: Non-negative integer validation

Invalid data is logged and rejected with detailed error messages.

## Kafka Configuration

**Phase 1A: Optimized Configuration**

### Producer Settings
- **Bootstrap Servers**: `localhost:9092`
- **Batch Size**: 16KB for optimal throughput
- **Linger Time**: 5ms to balance latency and throughput
- **Compression**: Snappy for CPU/memory efficiency
- **Retries**: 3 attempts with exponential backoff
- **ACKs**: 1 (leader acknowledgment)

### Topics
- `attack-events`: Attack event messages
- `status-events`: Status event messages

### Connection Pooling
- **Max In-Flight Requests**: 5 per connection
- **Connection Idle Timeout**: 9 minutes
- **Request Timeout**: 30 seconds
- **Delivery Timeout**: 2 minutes

## Monitoring & Metrics

**Phase 1A: Comprehensive Monitoring**

### Micrometer Metrics
- `logs.received.total`: Total logs received
- `logs.processed.total`: Successfully processed logs
- `logs.failed.total`: Failed log processing attempts
- `events.attack.total`: Attack events processed
- `events.status.total`: Status events processed
- `batch.requests.total`: Batch requests received
- `logs.processing.duration`: Single log processing time (p50, p95, p99)
- `batch.processing.duration`: Batch processing time (p50, p95, p99)

### Prometheus Endpoints
- `/actuator/prometheus`: Metrics in Prometheus format
- `/actuator/health`: Health check with details
- `/actuator/info`: Application information
- `/actuator/metrics`: All available metrics

### Custom Statistics
Accessible via `/api/v1/logs/stats`:
- Parsing success/failure counts
- Validation error breakdowns
- Performance statistics

## Performance Characteristics

**Phase 1A: Performance Optimizations**

### Single Log Processing
- **Target Latency**: < 10ms p95
- **Throughput**: 100+ logs/second
- **Error Rate**: < 5%

### Batch Processing
- **Batch Size**: 1-1000 logs
- **Parallel Processing**: Asynchronous with configurable thread pool
- **Target Latency**: < 100ms for 100 logs
- **Throughput**: 1000+ logs/second

### Resource Usage
- **Memory**: < 256MB heap usage
- **CPU**: < 70% under load
- **Network**: Optimized Kafka batching

## Configuration

Configuration is managed through `application.properties`:

### Server Configuration
```properties
server.port=8080
spring.application.name=data-ingestion-service
```

### Kafka Configuration
```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.batch-size=16384
spring.kafka.producer.linger-ms=5
spring.kafka.producer.compression-type=snappy
spring.kafka.producer.retries=3
```

### Async Processing
```properties
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.pool.queue-capacity=100
```

### Monitoring
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

## Running the Service

### Prerequisites
- Java 21 LTS
- Maven 3.8.7+
- Kafka 3.4+ (running on localhost:9092)

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Service will be available at http://localhost:8080
```

### Manual Build and Run
```bash
# Build
mvn clean compile

# Run tests
mvn test

# Run application
mvn spring-boot:run
```

### Verify Service Health
```bash
# Health check
curl http://localhost:8080/api/v1/logs/health

# Metrics
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Testing

### Unit Tests
```bash
mvn test -Dtest="*Test"
```

### Integration Tests
```bash
mvn test -Dtest="*IntegrationTest"
```

### Performance Testing
```bash
# Using Apache JMeter or similar tools
# See docs/performance-testing.md for details
```

## Error Handling

**Phase 1A: Robust Error Handling**

### Validation Errors
- Invalid IP/MAC addresses
- Out-of-range values
- Malformed data

### Processing Errors
- Kafka connection failures
- Parsing exceptions
- Resource exhaustion

### Monitoring Errors
- Metrics collection failures
- Statistics reset issues

All errors are logged with appropriate levels and included in metrics.

## Troubleshooting

### Common Issues

1. **Kafka Connection Failed**
   ```bash
   # Check Kafka status
   docker-compose ps kafka

   # Verify topic exists
   docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```

2. **High Memory Usage**
   ```bash
   # Check JVM settings
   java -XX:+PrintFlagsFinal -version | grep -i heap

   # Monitor with VisualVM or JConsole
   ```

3. **Slow Processing**
   ```bash
   # Check metrics
   curl http://localhost:8080/actuator/metrics

   # Review async configuration
   ```

### Logs and Debugging
```bash
# Application logs
tail -f logs/spring.log

# Kafka producer logs
docker-compose logs kafka

# Debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

## Development

### Code Structure
```
src/main/java/com/threatdetection/ingestion/
├── controller/          # REST controllers
├── model/              # Data models and DTOs
├── service/            # Business logic services
└── DataIngestionApplication.java
```

### Key Classes
- `LogIngestionController`: REST API endpoints
- `LogParserService`: Log parsing and validation
- `AsyncBatchLogIngestionService`: Batch processing
- `KafkaProducerService`: Kafka message publishing
- `MetricsService`: Monitoring and metrics

### Adding New Features
1. Create feature branch
2. Add tests first (TDD approach)
3. Implement functionality
4. Update documentation
5. Submit pull request

## API Versioning

Current API version: **v1**
- All endpoints are prefixed with `/api/v1/`
- Backward compatibility maintained within major versions
- Breaking changes will increment major version

## Security Considerations

- Input validation prevents injection attacks
- Resource limits prevent DoS attacks
- Sensitive data is not logged in plain text
- Health endpoints don't expose sensitive information

## Future Enhancements

- **Phase 1B**: Schema validation with JSON Schema
- **Phase 1C**: Rate limiting and request throttling
- **Phase 2**: gRPC support for high-performance clients
- **Phase 3**: Event-driven architecture with WebFlux