threat-detection-system

A modern, scalable threat detection system built with cloud-native technologies.

## Architecture

This system consists of the following microservices:

- **Data Ingestion Service**: Receives logs from rsyslog/logstash and publishes to Kafka
- **Stream Processing Service**: Real-time threat detection using Apache Flink
- **Threat Assessment Service**: Processes threat scores and risk evaluation
- **Alert Management Service**: Manages alerts and notifications
- **API Gateway**: Centralized API management and routing
- **Config Server**: Centralized configuration management

## Technologies

- **Backend**: Spring Boot 3.1+ (OpenJDK 21 LTS)
- **Event Streaming**: Apache Kafka 3.4+
- **Stream Processing**: Apache Flink 1.17+
- **Containerization**: Docker + Docker Compose
- **Orchestration**: Kubernetes + Kustomize
- **Build Tool**: Maven 3.8.7
- **Development**: Ubuntu 24.04.3 LTS (WSL2)

## Quick Start

### Development Environment (Docker)

```bash
# Clone repository
git clone <repository-url>
cd threat-detection-system

# Start all services
docker-compose up -d

# Check service health
curl http://localhost:8080/actuator/health

# View Flink Web UI
open http://localhost:8081

# View logs
docker-compose logs -f
```

### Production Deployment (Kubernetes)

```bash
# Deploy to development environment
kubectl apply -k k8s/overlays/development

# Deploy to production environment
kubectl apply -k k8s/overlays/production

# Check deployment status
kubectl get pods -n threat-detection-dev
```

## Data Flow

1. **Log Ingestion**: Logs arrive via rsyslog:9080 → Data Ingestion Service
2. **Event Publishing**: Structured events published to Kafka topics
3. **Real-time Processing**: Apache Flink processes threat scoring in real-time
4. **Threat Evaluation**: Threat Assessment Service evaluates risk levels
5. **Alert Generation**: Alert Management Service handles notifications
6. **API Access**: API Gateway provides unified access to all services

## Key Features

- **Real-time Threat Detection**: Continuous monitoring with sub-second latency
- **Scalable Architecture**: Microservices with horizontal scaling
- **Event-Driven Processing**: Kafka-based event streaming
- **Multi-Environment Support**: Development and production configurations
- **Health Monitoring**: Comprehensive health checks and metrics
- **Offline Data Import**: Historical data processing with replay capabilities
- **High-Reliability Bulk Ingestion**: Connection pooling, retry logic, and automatic recovery for large-scale log processing
- **Enhanced Threat Scoring**: Multi-dimensional algorithm with port diversity and device coverage rewards

## Threat Scoring Algorithm

The system uses a sophisticated threat scoring algorithm with enhanced multi-dimensional analysis:

```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
```

Where:
- `portWeight`: Risk weight based on port diversity (1.0-2.0)
- `deviceWeight`: Multi-device coverage reward (1.0-1.5)
- `timeWeight`: Time-based decay factor
- `ipWeight`: IP diversity amplification
- `attackCount`: Frequency of attack attempts
- `uniqueIps`: Number of distinct source IPs
- `uniquePorts`: Number of distinct target ports

**Latest Enhancements:**
- Port diversity analysis for sophisticated attack detection
- Multi-device coverage rewards for distributed attacks
- Configurable time windows (default: 30s aggregation, 2min scoring)
- Improved accuracy for complex threat patterns

## Project Structure

```
threat-detection-system/
├── docker/                 # Docker development environment
│   ├── docker-compose.yml
│   └── README.md          # Docker setup guide
├── k8s/                   # Kubernetes deployment configs
│   ├── base/              # Base configurations
│   ├── overlays/          # Environment-specific overlays
│   └── README.md          # K8s deployment guide
├── services/              # Microservices source code
│   ├── data-ingestion/    # Data ingestion service
│   ├── stream-processing/ # Flink stream processing
│   ├── threat-assessment/ # Threat evaluation service
│   ├── alert-management/  # Alert management service
│   ├── api-gateway/       # API gateway service
│   └── config-server/     # Configuration service
├── infrastructure/        # Infrastructure configurations
├── scripts/               # Organized utility scripts and tools
│   ├── test/              # Testing scripts and utilities
│   ├── tools/             # Production-ready utility scripts
│   ├── utils/             # Helper utilities and development tools
│   ├── init-kafka.sh      # Kafka initialization script
│   ├── Dockerfile         # Docker image for scripts
│   └── README.md          # Scripts usage guide
└── docs/                  # Documentation
```

## Development

### Prerequisites

- **OS**: Ubuntu 24.04.3 LTS (or WSL2 on Windows)
- **Java**: OpenJDK 21 LTS
- **Build Tool**: Maven 3.8.7
- **Container Runtime**: Docker Desktop 4.0+ or Docker Engine 20.10+
- **Kubernetes**: kubectl 1.25+ (for k8s deployment)

### Building Services

```bash
# Build all services
mvn clean install

# Build specific service
cd services/data-ingestion
mvn clean package
```

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Run with coverage
mvn test jacoco:report
```

## Deployment Options

### Option 1: Docker Development

Perfect for local development and testing. See `docker/README.md` for detailed instructions.

**Pros**: Fast startup, easy debugging, hot reload
**Cons**: Single-node, resource limits

### Option 2: Kubernetes Production

Ready for production deployment with high availability. See `k8s/README.md` for detailed instructions.

**Pros**: Scalable, multi-node, production-ready
**Cons**: Higher resource requirements, complex setup

## API Documentation

Once services are running:

- **Data Ingestion API**: `http://localhost:8080/swagger-ui.html`
- **API Gateway**: `http://localhost:8082/swagger-ui.html`
- **Flink Web UI**: `http://localhost:8081`

## Monitoring

### Health Checks

```bash
# Service health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Flink job status
curl http://localhost:8081/jobs/overview
```

### Logging

```bash
# Docker logs
docker-compose logs -f data-ingestion

# Kubernetes logs
kubectl logs -f deployment/data-ingestion -n threat-detection-dev
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `SPRING_PROFILES_ACTIVE` | Spring active profile | `development` |
| `FLINK_JOBMANAGER_RPC_ADDRESS` | Flink JobManager address | `stream-processing` |

### Configuration Files

- `docker-compose.yml`: Docker service definitions
- `k8s/base/`: Kubernetes base configurations
- `k8s/overlays/`: Environment-specific overrides

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

### Development Workflow

1. **Local Development**: Use Docker Compose for fast iteration
2. **Testing**: Run unit and integration tests
3. **Code Review**: Submit PR with detailed description
4. **CI/CD**: Automated testing and deployment
5. **Production**: Deploy via Kubernetes manifests

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Check if ports 8080-8082 are available
2. **Memory Issues**: Ensure Docker has at least 4GB RAM allocated
3. **Kafka Connection**: Verify Zookeeper is running before Kafka
4. **Flink Jobs**: Check JobManager logs for submission errors
5. **Connection Reset Errors**: Use the enhanced bulk ingestion script with built-in connection pooling:
   ```bash
   python3 scripts/tools/bulk_ingest_logs.py --count 5 --workers 4 --batch-size 25
   ```
6. **Bulk Ingestion Failures**: Monitor connection pool refresh logs and ensure batch sizes are optimized

### Getting Help

- Check service logs: `docker-compose logs <service-name>`
- Review documentation in `docs/` directory
- Check GitHub Issues for known problems

## Roadmap

- [ ] Complete Threat Assessment Service implementation
- [ ] Add Alert Management Service
- [ ] Implement API Gateway with authentication
- [ ] Add comprehensive monitoring and alerting
- [ ] Implement data persistence layer
- [ ] Add machine learning-based threat detection
- [ ] Create web-based management dashboard

## License

This project is licensed under the MIT License - see the LICENSE file for details.