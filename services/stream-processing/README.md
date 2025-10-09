# Stream Processing Service

Real-time threat detection and analysis service built with Apache Flink, providing continuous stream processing for attack events and threat scoring.

## Features

- **Real-time Stream Processing**: Continuous processing of attack events with sub-second latency
- **Advanced Threat Scoring**: Multi-dimensional algorithm with port diversity and device coverage analysis
- **Time Window Aggregation**: Configurable sliding and tumbling windows for attack pattern detection
- **Kafka Integration**: Optimized consumer/producer configuration with fault tolerance
- **Flink Web UI**: Built-in monitoring and job management interface
- **Scalable Architecture**: Horizontal scaling with configurable parallelism

## Architecture

The service processes data through multiple stages:

1. **Event Ingestion**: Consumes attack events from Kafka `attack-events` topic
2. **Real-time Aggregation**: 30-second tumbling windows for attack pattern analysis
3. **Threat Scoring**: 2-minute sliding windows for threat level calculation
4. **Alert Generation**: Publishes high-priority threats to `threat-alerts` topic
5. **Data Persistence**: Stores aggregated metrics to `minute-aggregations` topic

## Threat Scoring Algorithm

Enhanced multi-dimensional threat scoring:

```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
```

### Weight Factors

- **portWeight**: Port diversity multiplier (1.0-2.0)
  - Single port: 1.0
  - Multiple ports: 1.0-2.0 (based on diversity)

- **deviceWeight**: Multi-device coverage reward (1.0-1.5)
  - Single device: 1.0
  - Multiple devices: 1.0-1.5 (based on coverage)

- **timeWeight**: Time-based decay factor
- **ipWeight**: IP diversity amplification

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | Kafka broker addresses |
| `INPUT_TOPIC` | `attack-events` | Input topic for attack events |
| `OUTPUT_TOPIC` | `threat-alerts` | Output topic for threat alerts |
| `AGGREGATION_TOPIC` | `minute-aggregations` | Topic for aggregated metrics |
| `AGGREGATION_WINDOW_SECONDS` | `30` | Aggregation window duration |
| `THREAT_SCORING_WINDOW_MINUTES` | `2` | Threat scoring window duration |

### Flink Configuration

```yaml
jobmanager.rpc.address: stream-processing
parallelism.default: 1
taskmanager.numberOfTaskSlots: 1
rest.port: 8081
```

## Data Flow

### Input: Attack Events
```json
{
  "eventId": "attack-ABC123-1728465600-0",
  "deviceSerial": "ABC123",
  "attackMac": "00:11:22:33:44:55",
  "attackIp": "192.168.1.100",
  "targetPort": 80,
  "timestamp": 1728465600000,
  "eventType": "ATTACK"
}
```

### Output: Threat Alerts
```json
{
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 18.48,
  "threatLevel": "INFO",
  "threatName": "信息",
  "timestamp": 1728465600000,
  "windowStart": 1728465480000,
  "windowEnd": 1728465600000,
  "totalAggregations": 1
}
```

### Output: Aggregations
```json
{
  "attackMac": "00:11:22:33:44:55",
  "uniqueIps": 1,
  "uniquePorts": 2,
  "uniqueDevices": 1,
  "attackCount": 7,
  "timestamp": 1728465600000,
  "windowStart": 1728465570000,
  "windowEnd": 1728465600000
}
```

## Threat Levels

| Level | Score Range | Description |
|-------|-------------|-------------|
| CRITICAL | > 1000 | Critical threat requiring immediate action |
| HIGH | 500-1000 | High-risk threat |
| MEDIUM | 100-500 | Medium-risk threat |
| LOW | 10-100 | Low-risk threat |
| INFO | < 10 | Informational level |

## Running the Service

### Prerequisites
- Java 11+
- Apache Flink 1.17+
- Kafka 3.4+
- Maven 3.8+

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Access Flink Web UI
open http://localhost:8081
```

### Manual Build and Run
```bash
# Build the project
mvn clean package

# Submit to Flink cluster
flink run -c com.threatdetection.stream.ThreatDetectionJob target/stream-processing-1.0.jar
```

### Verify Service Health
```bash
# Check Flink job status
curl http://localhost:8081/jobs/overview

# View running jobs
curl http://localhost:8081/jobs
```

## Monitoring

### Flink Web UI
- **URL**: `http://localhost:8081`
- **Features**:
  - Job status and metrics
  - Task manager information
  - Checkpointing status
  - Watermark progress

### Key Metrics
- **Processing Latency**: End-to-end event processing time
- **Throughput**: Events processed per second
- **Checkpoint Duration**: State checkpointing performance
- **Kafka Lag**: Consumer lag monitoring

## Development

### Project Structure
```
src/main/java/com/threatdetection/stream/
├── model/              # Data models (AttackEvent, ThreatAlert, etc.)
├── function/           # Flink processing functions
│   ├── aggregation/    # Window aggregation logic
│   ├── scoring/        # Threat scoring algorithms
│   └── enrichment/     # Data enrichment functions
├── sink/               # Kafka sink implementations
├── source/             # Kafka source implementations
└── ThreatDetectionJob.java
```

### Key Classes
- `ThreatDetectionJob`: Main Flink job definition
- `AttackEventAggregator`: Window aggregation function
- `ThreatScorer`: Threat scoring logic
- `KafkaEventSink`: Kafka output sink
- `KafkaEventSource`: Kafka input source

### Adding New Features
1. Define data models in `model/` package
2. Implement processing logic in `function/` package
3. Update `ThreatDetectionJob` to include new operators
4. Add tests and update documentation

## Testing

### Unit Tests
```bash
mvn test -Dtest="*Test"
```

### Integration Tests
```bash
# Start test environment
docker-compose -f docker/docker-compose.test.yml up -d

# Run integration tests
mvn verify
```

### Performance Testing
```bash
# Generate test data
python3 scripts/generate-test-data.py --events 10000

# Run performance tests
mvn test -Dtest="*PerformanceTest"
```

## Configuration Tuning

### Performance Optimization
```yaml
# High-throughput configuration
parallelism.default: 4
taskmanager.numberOfTaskSlots: 2
pipeline.object-reuse: true

# Kafka tuning
kafka.consumer.fetch.min.bytes: 1024
kafka.consumer.fetch.max.wait.ms: 500
```

### Memory Configuration
```yaml
jobmanager.memory.process.size: 2048m
taskmanager.memory.process.size: 4096m
taskmanager.memory.managed.size: 1024m
```

## Troubleshooting

### Common Issues

1. **Job Submission Failed**
   ```bash
   # Check Flink logs
   docker-compose logs stream-processing

   # Verify JAR file
   ls -la target/*.jar
   ```

2. **High Latency**
   ```bash
   # Check watermark progress in Flink UI
   # Adjust window sizes in configuration
   # Increase parallelism
   ```

3. **Kafka Connection Issues**
   ```bash
   # Verify Kafka connectivity
   docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

   # Check consumer group lag
   docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group flink-consumer
   ```

4. **Out of Memory**
   ```bash
   # Increase Flink memory settings
   # Reduce state size with more frequent checkpoints
   # Optimize window sizes
   ```

### Debugging
```bash
# Enable debug logging
export FLINK_LOG_LEVEL=DEBUG

# View job logs
docker-compose logs -f stream-processing

# Access Flink UI for detailed metrics
open http://localhost:8081
```

## Deployment

### Docker Deployment
```yaml
version: '3.8'
services:
  stream-processing:
    image: threat-detection/stream-processing:latest
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - AGGREGATION_WINDOW_SECONDS=30
      - THREAT_SCORING_WINDOW_MINUTES=2
    ports:
      - "8081:8081"
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: stream-processing
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: flink-jobmanager
        image: threat-detection/stream-processing:latest
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-cluster:9092"
```

## Future Enhancements

- **Phase 2**: Machine learning-based anomaly detection
- **Phase 3**: Graph-based attack pattern recognition
- **Phase 4**: Real-time model updates and A/B testing
- **Phase 5**: Integration with external threat intelligence feeds</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/services/stream-processing/README.md