# Config Server Service

Centralized configuration management service that provides externalized configuration for all microservices in the threat detection system, with support for multiple environments, encrypted properties, and dynamic configuration updates.

## Features

- **Centralized Configuration**: Single source of truth for all service configurations
- **Environment-Specific Configs**: Separate configurations for dev, staging, and production
- **Encrypted Properties**: Secure storage of sensitive configuration data
- **Dynamic Configuration**: Runtime configuration updates without service restarts
- **Version Control Integration**: Git-based configuration versioning and history
- **Health Monitoring**: Configuration server health checks and status monitoring
- **Multi-Format Support**: YAML, JSON, and Properties file formats

## Architecture

The Config Server acts as the central configuration hub:

1. **Configuration Storage**: Stores configurations in Git repository
2. **Service Discovery**: Registers with Eureka for service location
3. **Configuration Serving**: Serves configurations via REST API
4. **Encryption/Decryption**: Handles encrypted property values
5. **Caching**: Caches configurations for improved performance
6. **Monitoring**: Provides health checks and metrics

## Configuration Structure

### Repository Structure
```
config-repo/
├── application.yml          # Common configurations
├── data-ingestion.yml       # Data ingestion service configs
├── stream-processing.yml    # Stream processing service configs
├── threat-assessment.yml    # Threat assessment service configs
├── alert-management.yml     # Alert management service configs
├── api-gateway.yml          # API gateway service configs
├── config-server.yml        # Config server self-config
├── docker/                  # Docker environment configs
│   ├── application.yml
│   ├── data-ingestion.yml
│   └── ...
├── development/             # Development environment
│   ├── application.yml
│   ├── data-ingestion.yml
│   └── ...
├── staging/                 # Staging environment
│   ├── application.yml
│   ├── data-ingestion.yml
│   └── ...
└── production/              # Production environment
    ├── application.yml
    ├── data-ingestion.yml
    └── ...
```

### Configuration Priority
1. **Service-Specific**: `{service-name}.yml`
2. **Environment-Specific**: `{profile}/{service-name}.yml`
3. **Common**: `application.yml`
4. **Environment Common**: `{profile}/application.yml`

## API Endpoints

### GET /{application}/{profile}[/{label}]
Retrieve configuration for a specific application and profile.

**Parameters:**
- `application`: Service name (e.g., data-ingestion)
- `profile`: Spring profile (e.g., docker, development)
- `label`: Git branch/tag (optional, default: master)

**Example Requests:**
```bash
# Get data-ingestion config for docker profile
curl http://localhost:8888/data-ingestion/docker

# Get config with specific Git branch
curl http://localhost:8888/data-ingestion/docker/feature-branch

# Get default config
curl http://localhost:8888/application/default
```

**Response:**
```json
{
  "name": "data-ingestion",
  "profiles": ["docker"],
  "label": "master",
  "version": "a1b2c3d4...",
  "state": null,
  "propertySources": [
    {
      "name": "https://github.com/user/config-repo/data-ingestion-docker.yml",
      "source": {
        "spring.kafka.bootstrap-servers": "kafka:29092",
        "server.port": 8080,
        "logging.level.com.threatdetection": "INFO"
      }
    },
    {
      "name": "https://github.com/user/config-repo/application.yml",
      "source": {
        "spring.application.name": "data-ingestion-service",
        "management.endpoints.web.exposure.include": "health,info,metrics"
      }
    }
  ]
}
```

### GET /{application}-{profile}.yml
Retrieve configuration in YAML format.

### GET /{application}-{profile}.json
Retrieve configuration in JSON format.

### GET /{application}-{profile}.properties
Retrieve configuration in Properties format.

### POST /encrypt
Encrypt a plain text value.

**Request Body:** Plain text to encrypt
**Content-Type:** `text/plain`

**Response:** Encrypted value

**Example:**
```bash
curl -X POST http://localhost:8888/encrypt \
  -H "Content-Type: text/plain" \
  -d "my_secret_password"
```

### POST /decrypt
Decrypt an encrypted value.

**Request Body:** Encrypted text to decrypt
**Content-Type:** `text/plain`

**Response:** Decrypted value

### GET /actuator/health
Health check endpoint.

### GET /actuator/info
Application information.

## Configuration Examples

### Data Ingestion Service Config
```yaml
# data-ingestion.yml
server:
  port: 8080

spring:
  application:
    name: data-ingestion-service
  kafka:
    bootstrap-servers: kafka:29092
    producer:
      batch-size: 16384
      linger-ms: 5
      compression-type: snappy
      retries: 3

logging:
  level:
    com.threatdetection: INFO
    org.springframework.kafka: WARN

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

data:
  ingestion:
    batch:
      size: 25
      timeout: 30
    validation:
      dev-serial-pattern: "[0-9A-Za-z]+"
```

### Stream Processing Service Config
```yaml
# stream-processing.yml
jobmanager:
  rpc:
    address: stream-processing

parallelism:
  default: 1

taskmanager:
  numberOfTaskSlots: 1

rest:
  port: 8081

kafka:
  bootstrap:
    servers: kafka:29092

flink:
  checkpoints:
    dir: file:///tmp/flink-checkpoints
    interval: 60000

threat:
  scoring:
    window:
      aggregation: 30
      scoring: 2
    algorithm:
      port-weight: 1.0
      device-weight: 1.0
```

### Encrypted Properties
```yaml
# Use {cipher} prefix for encrypted values
database:
  password: "{cipher}AQB8...encrypted-value..."

external:
  api:
    key: "{cipher}BQC9...encrypted-value..."
```

## Encryption Configuration

### Symmetric Encryption (Default)
```yaml
encrypt:
  key: my-encryption-key-32-characters-long
```

### Asymmetric Encryption
```yaml
encrypt:
  key-store:
    location: classpath:/server.jks
    password: keystore_password
    alias: config-server-key
    secret: key_password
```

## Git Repository Setup

### Repository Structure
```bash
# Initialize config repository
mkdir config-repo
cd config-repo
git init

# Create directory structure
mkdir -p {docker,development,staging,production}

# Add configuration files
# ... add your config files ...

# Commit changes
git add .
git commit -m "Initial configuration"
```

### Repository URL Configuration
```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/threat-detection-config
          username: ${GIT_USERNAME}
          password: ${GIT_PASSWORD}
          clone-on-start: true
          default-label: master
```

## Running the Service

### Prerequisites
- Java 21 LTS
- Git repository with configuration files
- Maven 3.8+

### Quick Start with Docker
```bash
# From project root
docker-compose up -d

# Service will be available at http://localhost:8888
```

### Manual Build and Run
```bash
# Build the service
mvn clean package

# Run the application
java -jar target/config-server-1.0.jar
```

### Local Git Repository
```bash
# Use local Git repository for development
spring.cloud.config.server.git.uri=file://${user.home}/config-repo
```

## Client Configuration

### Spring Boot Client Setup
```yaml
# bootstrap.yml (for each service)
spring:
  application:
    name: data-ingestion
  profiles:
    active: docker
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
      retry:
        max-attempts: 10
        initial-interval: 1000
```

### Configuration Refresh
```yaml
# Enable configuration refresh
management:
  endpoints:
    web:
      exposure:
        include: refresh
```

```bash
# Trigger configuration refresh
curl -X POST http://localhost:8080/actuator/refresh \
  -H "Authorization: Bearer $TOKEN"
```

## Monitoring

### Health Checks
```bash
# Service health
curl http://localhost:8888/actuator/health

# Git repository health
curl http://localhost:8888/actuator/health/git

# Encryption health
curl http://localhost:8888/actuator/health/encrypt
```

### Metrics
```bash
# Prometheus metrics
curl http://localhost:8888/actuator/prometheus

# Configuration metrics
curl http://localhost:8888/actuator/metrics/config.requests
```

### Key Metrics
- `config.requests.total`: Total configuration requests
- `config.requests.duration`: Request processing time
- `git.fetch.duration`: Git repository fetch time
- `encrypt.operations.total`: Encryption/decryption operations

## Security

### Basic Authentication
```yaml
spring:
  security:
    user:
      name: config_user
      password: ${CONFIG_PASSWORD}
```

### OAuth2 Integration
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
```

### Access Control
```yaml
config:
  server:
    security:
      enabled: true
      health:
        enabled: true
      encrypt:
        enabled: true
        roles: ADMIN
```

## Development

### Project Structure
```
src/main/java/com/threatdetection/config/
├── ConfigServerApplication.java
├── config/                  # Configuration classes
├── controller/              # Custom controllers
├── service/                 # Configuration services
│   ├── encryption/          # Encryption services
│   ├── git/                 # Git integration
│   └── monitoring/          # Monitoring services
└── security/                # Security configuration
```

### Key Components
- `ConfigServerApplication`: Main application class
- `EncryptionService`: Property encryption/decryption
- `GitConfigServer`: Git repository integration
- `ConfigMonitoring`: Configuration monitoring

### Custom Property Sources
```java
@Configuration
public class CustomPropertySourceLocator implements PropertySourceLocator {

    @Override
    public PropertySource<?> locate(Environment environment) {
        // Custom property source logic
        return new MapPropertySource("custom", customProperties);
    }
}
```

### Testing
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Test with local config
mvn spring-boot:run -Dspring.profiles.active=native
```

## Troubleshooting

### Common Issues

1. **Git Repository Access**
   ```bash
   # Test Git access
   git clone https://github.com/your-org/config-repo /tmp/test-clone

   # Check credentials
   curl -u username:password https://api.github.com/user
   ```

2. **Configuration Not Loading**
   ```bash
   # Check config server logs
   docker-compose logs config-server

   # Test configuration endpoint
   curl http://localhost:8888/data-ingestion/docker

   # Verify application name
   ```

3. **Encryption Issues**
   ```bash
   # Test encryption
   curl -X POST http://localhost:8888/encrypt -d "test"

   # Check encryption configuration
   curl http://localhost:8888/actuator/configprops | grep encrypt
   ```

4. **Client Connection Issues**
   ```bash
   # Check client bootstrap configuration
   # Verify config server URL
   # Test connectivity
   telnet config-server 8888
   ```

### Performance Tuning
```yaml
# Git optimization
spring:
  cloud:
    config:
      server:
        git:
          timeout: 10
          refresh-rate: 30
          clone-on-start: true
          delete-untracked-branches: true

# Caching
config:
  server:
    cache:
      enabled: true
      ttl: 300
```

## Integration Examples

### Jenkins Pipeline Integration
```groovy
pipeline {
    stages {
        stage('Update Config') {
            steps {
                sh '''
                    cd config-repo
                    git checkout master
                    # Update configuration files
                    git add .
                    git commit -m "Update config for build ${BUILD_NUMBER}"
                    git push origin master
                '''
            }
        }
        stage('Refresh Services') {
            steps {
                sh '''
                    # Refresh all services
                    curl -X POST http://data-ingestion:8080/actuator/refresh
                    curl -X POST http://stream-processing:8081/actuator/refresh
                    # ... refresh other services
                '''
            }
        }
    }
}
```

### Kubernetes ConfigMaps Integration
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: config-server-env
data:
  ENCRYPT_KEY: "your-encryption-key"
  GIT_URI: "https://github.com/your-org/config-repo"
  GIT_USERNAME: "config-user"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
spec:
  template:
    spec:
      containers:
      - name: config-server
        envFrom:
        - configMapRef:
            name: config-server-env
```

## Future Enhancements

- **Phase 2**: Database-backed configuration storage
- **Phase 3**: Configuration validation and schema checking
- **Phase 4**: Advanced encryption with HSM integration
- **Phase 5**: Multi-region configuration replication</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/services/config-server/README.md