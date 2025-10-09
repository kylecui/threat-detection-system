wsl -d Ubuntu -- bash -c "cat > ~/threat-detection-system/services/data-ingestion/data-ingestion-service/README.md << 'EOF'
# Data Ingestion Service

This service handles the ingestion and processing of threat detection logs from Logstash.

## Features

- Parses attack logs (log_type=1) and status logs (log_type=2) from Logstash
- Publishes parsed events to Kafka topics
- REST API for log ingestion
- Health check endpoint

## API Endpoints

### POST /api/v1/logs/ingest
Ingests a log entry for processing.

**Request Body:** Raw log string from Logstash

**Response:**
- 200: Attack/Status event processed successfully
- 400: Failed to process log
- 500: Internal server error

### GET /api/v1/logs/health
Health check endpoint.

**Response:** "Log Ingestion Service is healthy"

## Log Formats

### Attack Log (log_type=1)
```
syslog_version=1.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=10.0.0.1,response_port=80,line_id=1,Iface_type=1,Vlan_id=100,log_time=1693526400,eth_type=2048,ip_type=4
```

### Status Log (log_type=2)
```
syslog_version=1.0,dev_serial=ABC123,log_type=2,sentry_count=5,real_host_count=10,dev_start_time=1693526400,dev_end_time=1693526500,time=2023-09-01 12:00:00
```

## Kafka Topics

- `attack-events`: Attack event messages
- `status-events`: Status event messages

## Running the Service

### Prerequisites
- Java 21
- Maven 3.8+
- Kafka (for message publishing)

### Build
```bash
mvn clean compile
```

### Test
```bash
mvn test
```

### Run
```bash
mvn spring-boot:run
```

The service will start on port 8080.

## Configuration

Configuration is managed through `application.properties`:

- `server.port`: Server port (default: 8080)
- `spring.kafka.bootstrap-servers`: Kafka broker addresses
- `logging.level.com.threatdetection`: Log level for the application