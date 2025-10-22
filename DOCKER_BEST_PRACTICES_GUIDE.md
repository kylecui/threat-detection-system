# Docker Build Best Practices & Container Update Guide
## Preventing Repeated Container Rebuilds

**Problem Solved:** GET /alerts/{id} 500 error after multiple code changes  
**Root Cause:** Docker layer caching preserving old JAR files  
**Prevention:** Proper Docker build process and configuration management

---

## Part 1: Understanding the Problem

### What Happened

```
Day 1:  docker-compose up -d              (builds images, creates containers)
        ├─ alert-management image built with first JAR
        └─ Layer 4: COPY target/*.jar app.jar → CACHED

Days 2-4: Code changes → Maven build → New JAR created
        ├─ Source files updated ✅
        ├─ target/alert-management-1.0.0.jar updated ✅
        ├─ docker-compose build alert-management ❌ Reuses cached layer
        └─ docker-compose up -d ❌ Runs container with OLD JAR

Result: 5+ hours of troubleshooting, thinking it was a code logic bug
        when it was actually a deployment pipeline issue
```

### Why Standard Solutions Failed

| Solution Attempted | Why It Failed |
|-------------------|---------------|
| `docker-compose restart` | Container still runs old image |
| `docker-compose build` | Docker reused cached COPY layer |
| `docker rmi` + rebuild | Other cached layers still referenced old files |
| Code changes (5+ attempts) | Changes not in container because of cached JAR |
| `docker-compose stop/up -d` | Same image used, same old JAR inside |
| Config changes (OSIV=true) | Config file rebuilt into image from old sources |

### Why `docker system prune -f` Finally Worked

```bash
$ docker system prune -f

Deleted Containers:        9    ✅ Removes containers holding references
Deleted Images:           11    ✅ Removes image layers completely
Deleted Networks:         11    ✅ Clears all associations
Deleted Build Cache:  reclaimed ✅ FORCES fresh build without any cache

Result: Next build uses NO cached layers, picks up latest JAR
```

---

## Part 2: Correct Docker Build Process

### Process 1: Full System Rebuild (When Unsure)

**Use this when:**
- Code changes aren't reflected in container
- Configuration changes aren't applying
- Strange behavior after updates
- Any time you think "is the container actually updated?"

```bash
#!/bin/bash
# scripts/full-rebuild.sh

SERVICE=${1:-alert-management}

echo "🔄 Full rebuild of $SERVICE"
echo "================================================"

# Step 1: Clean local build artifacts
echo "📁 Cleaning local build artifacts..."
rm -rf services/$SERVICE/target/

# Step 2: Maven build (forces compilation)
echo "🔨 Building JAR with Maven..."
cd services/$SERVICE
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Maven build failed"
    exit 1
fi
cd ../..

# Step 3: Docker system cleanup (CRITICAL)
echo "🗑️  Cleaning Docker layer cache..."
docker system prune -f

# Step 4: Docker image rebuild (forces all layers)
echo "🐳 Building Docker image..."
cd docker
docker-compose build --no-cache $SERVICE
if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi

# Step 5: Container restart
echo "🚀 Starting container..."
docker-compose stop $SERVICE
docker-compose rm -f $SERVICE
docker-compose up -d $SERVICE

# Step 6: Wait and verify
echo "⏳ Waiting for service startup..."
sleep 10

echo "✅ Checking service health..."
HEALTH=$(docker-compose ps $SERVICE | grep "healthy")
if [ -n "$HEALTH" ]; then
    echo "✅ Service is healthy"
    exit 0
else
    echo "⚠️  Service status unclear, checking logs..."
    docker-compose logs $SERVICE | tail -20
    exit 1
fi
```

**Usage:**
```bash
bash scripts/full-rebuild.sh alert-management
```

### Process 2: Fast Rebuild (After Small Changes)

**Use this when:**
- Only code logic changed (same files)
- Confident Maven will rebuild
- Quick testing cycle

```bash
#!/bin/bash
# scripts/quick-rebuild.sh

SERVICE=${1:-alert-management}

echo "⚡ Quick rebuild of $SERVICE"

# Skip cleanup, just rebuild Docker without full system prune
cd services/$SERVICE
mvn clean package -DskipTests || exit 1
cd ../..

docker-compose build --no-cache $SERVICE
docker-compose restart $SERVICE

sleep 5
docker-compose logs $SERVICE | tail -10
```

**Usage:**
```bash
bash scripts/quick-rebuild.sh alert-management
```

### Process 3: Configuration-Only Changes

**Use this when:**
- Only properties/config files changed
- No code changes

**Problem:** Config changes aren't picked up by running containers

**Solution 1: Use environment variables (RECOMMENDED)**
```yaml
# docker-compose.yml - Don't copy config into image
alert-management:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_JPA_OPEN_IN_VIEW: "true"
    SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL: "false"
    LOGGING_LEVEL_ROOT: "WARN"
    # Changes here apply immediately on container restart
```

**Then just:**
```bash
docker-compose restart alert-management
```

**Solution 2: If using config files (not recommended)**
```bash
# Must rebuild container since config is baked into image
docker-compose stop alert-management
docker-compose build --no-cache alert-management
docker-compose up -d alert-management
```

---

## Part 3: Recommended Dockerfile Pattern

### Current Dockerfile (Simple but Cache-Prone)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN apk add --no-cache curl
COPY target/*.jar app.jar          # ❌ May be cached
RUN chown -R appuser:appgroup /app
USER appuser
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
```

**Issue:** COPY layer caches even when JAR changes

### Improved Dockerfile Pattern

```dockerfile
############################
# Build stage
############################
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder (fresh build)
COPY --from=builder /src/target/*.jar app.jar

# Validate JAR exists (forces cache invalidation on missing JAR)
RUN test -f app.jar || (echo "ERROR: JAR not found" && exit 1)

# Change ownership
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
```

**Advantages:**
- ✅ Build happens in container (no external dependency)
- ✅ JAR validation ensures layer cache misses when JAR missing
- ✅ Cleaner separation of concerns

---

## Part 4: Docker Compose Best Practices

### Current docker-compose.yml Configuration (Needs Updates)

```yaml
alert-management:
  build:
    context: ../services/alert-management
    dockerfile: Dockerfile
    # ❌ Missing options for reproducible builds
  
  environment:
    SPRING_PROFILES_ACTIVE: docker
    # ❌ Config properties loaded from file, not env vars
  
  # ❌ No cache control
```

### Improved Configuration

```yaml
alert-management:
  # Add build context options
  build:
    context: ../services/alert-management
    dockerfile: Dockerfile
    # Optional: build args for version/timestamp
    args:
      BUILD_DATE: ${BUILD_DATE:-latest}
      APP_VERSION: ${APP_VERSION:-1.0.0}
    # Future: registry cache for CI/CD
    cache_from:
      - type=registry,ref=your-registry/alert-management:latest

  image: threat-detection/alert-management:${APP_VERSION:-latest}
  container_name: alert-management-service
  
  depends_on:
    kafka:
      condition: service_healthy
    postgres:
      condition: service_healthy
  
  ports:
    - "8082:8084"
  
  environment:
    # ✅ Use env vars instead of config files
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/threat_detection
    SPRING_DATASOURCE_USERNAME: threat_user
    SPRING_DATASOURCE_PASSWORD: threat_password
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    SPRING_DATA_REDIS_HOST: redis
    SPRING_DATA_REDIS_PORT: 6379
    
    # ✅ Logging configuration (production-appropriate)
    LOGGING_LEVEL_ROOT: WARN
    LOGGING_LEVEL_COM_THREATDETECTION: INFO
    SPRING_JPA_OPEN_IN_VIEW: "true"
    SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL: "false"
    
    # Mail config (as example of other envs)
    SPRING_MAIL_HOST: smtp.163.com
    SPRING_MAIL_PORT: 25
    SPRING_MAIL_USERNAME: ${SPRING_MAIL_USERNAME}
    SPRING_MAIL_PASSWORD: ${SPRING_MAIL_PASSWORD}
  
  volumes:
    # ❌ Remove if using env vars for config
    # - ./application-docker.properties:/app/config/application-docker.properties
  
  healthcheck:
    test: ["CMD", "curl", "-fsS", "http://localhost:8084/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 60s
  
  restart: unless-stopped
  
  # ✅ Add resource limits for production
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 2G
      reservations:
        cpus: '1.0'
        memory: 1G
```

---

## Part 5: Automated Build Script (Complete)

### Master Build Script

```bash
#!/bin/bash
# scripts/rebuild.sh
# Complete, intelligent rebuild system

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}ℹ️  ${NC}$1"
}

log_success() {
    echo -e "${GREEN}✅${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠️  ${NC}$1"
}

log_error() {
    echo -e "${RED}❌${NC} $1"
}

# Parse arguments
SERVICE=${1:-alert-management}
FULL_REBUILD=${2:-false}

# Validate service
VALID_SERVICES=("alert-management" "data-ingestion" "threat-assessment" \
                "customer-management" "stream-processing" "api-gateway")
if [[ ! " ${VALID_SERVICES[@]} " =~ " ${SERVICE} " ]]; then
    log_error "Invalid service: $SERVICE"
    echo "Valid services: ${VALID_SERVICES[*]}"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════╗"
echo "║   Docker Build & Deploy for $SERVICE   ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Step 1: Pre-flight checks
log_info "Running pre-flight checks..."
if ! command -v docker &> /dev/null; then
    log_error "Docker not found"
    exit 1
fi
if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose not found"
    exit 1
fi
log_success "Docker tools available"

# Step 2: Clean local artifacts if full rebuild
if [ "$FULL_REBUILD" = "true" ]; then
    log_info "Cleaning local build artifacts (FULL rebuild requested)..."
    rm -rf services/$SERVICE/target/
    log_success "Cleaned"
fi

# Step 3: Maven build
log_info "Building with Maven..."
cd services/$SERVICE
if ! mvn clean package -DskipTests > /tmp/maven-build.log 2>&1; then
    log_error "Maven build failed"
    tail -30 /tmp/maven-build.log
    exit 1
fi
cd ../..
log_success "Maven build successful"

# Step 4: Verify JAR exists
JAR_PATH=$(ls services/$SERVICE/target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR_PATH" ]; then
    log_error "No JAR file found after build"
    exit 1
fi
log_success "JAR ready: $(basename $JAR_PATH)"

# Step 5: System cleanup if full rebuild
if [ "$FULL_REBUILD" = "true" ]; then
    log_info "Cleaning Docker layer cache (FULL rebuild)..."
    PRUNE_OUTPUT=$(docker system prune -f 2>&1)
    echo "$PRUNE_OUTPUT" | grep -i "reclaimed" || true
fi

# Step 6: Docker build
log_info "Building Docker image..."
cd docker
if ! docker-compose build --no-cache $SERVICE > /tmp/docker-build.log 2>&1; then
    log_error "Docker build failed"
    tail -30 /tmp/docker-build.log
    exit 1
fi
cd ..
log_success "Docker image built"

# Step 7: Stop and remove old container
log_info "Stopping old container..."
docker-compose stop $SERVICE 2>/dev/null || true
docker-compose rm -f $SERVICE 2>/dev/null || true
log_success "Old container removed"

# Step 8: Start new container
log_info "Starting new container..."
cd docker
docker-compose up -d $SERVICE
cd ..
log_success "Container started"

# Step 9: Wait for startup
log_info "Waiting for service to be ready..."
RETRY=0
MAX_RETRY=30
while [ $RETRY -lt $MAX_RETRY ]; do
    if docker-compose exec -T $SERVICE curl -f http://localhost:8084/actuator/health &>/dev/null 2>&1 || \
       docker-compose exec -T $SERVICE curl -f http://localhost:8080/actuator/health &>/dev/null 2>&1; then
        log_success "Service is healthy"
        break
    fi
    RETRY=$((RETRY + 1))
    sleep 1
done

if [ $RETRY -eq $MAX_RETRY ]; then
    log_warning "Service health check timeout"
    log_info "Checking logs..."
    docker-compose logs $SERVICE | tail -20
fi

# Step 10: Verification
log_info "Running verification..."
if docker-compose ps | grep "$SERVICE" | grep -q "Up"; then
    log_success "Container is running"
else
    log_error "Container is not running"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════╗"
echo "║       ✅ Build Complete Successfully    ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "Service: $SERVICE"
echo "To view logs: docker-compose logs $SERVICE"
echo "To test: bash scripts/test_backend_api_happy_path.sh"
echo ""
```

**Usage:**
```bash
# Quick rebuild (no system prune)
bash scripts/rebuild.sh alert-management

# Full rebuild (includes system prune)
bash scripts/rebuild.sh alert-management true

# Other services
bash scripts/rebuild.sh data-ingestion
bash scripts/rebuild.sh customer-management true  # With full cleanup
```

---

## Part 6: Prevention Checklist

### For Development Teams

- [ ] **Never assume rebuild worked** - verify with `curl` or tests
- [ ] **Always use `--no-cache`** when rebuilding after code changes
- [ ] **Use full rebuild script** when debugging deployment issues
- [ ] **Check container with `docker exec`** before assuming it has new code
- [ ] **Document:** If it takes 15+ minutes to fix, it's probably caching

### For DevOps/CI/CD

- [ ] **Script all builds** with `--no-cache` option
- [ ] **Add verification step** after deployment (health check + smoke test)
- [ ] **Use environment variables** for configuration (not config files in image)
- [ ] **Tag images with version** (not just `latest`)
- [ ] **Clean up regularly:** `docker system prune -f` in cron
- [ ] **Document build process** for new team members

### For Production

- [ ] **Use multi-stage builds** (builder + runtime)
- [ ] **Cache registry** for CI/CD speed (build cache, not image cache)
- [ ] **Minimal runtime image** (jre-alpine, not full jdk)
- [ ] **Health checks** required (detects old code faster)
- [ ] **Blue-green deployment** (don't restart in-place)

---

## Troubleshooting Guide

### Symptom: "Code change not reflected in container"

```bash
# 1. Verify JAR was built
ls -lh services/alert-management/target/*.jar

# 2. Full rebuild
bash scripts/rebuild.sh alert-management true

# 3. Verify inside container
docker exec alert-management-service ls -lh /app/app.jar

# 4. Check startup logs
docker logs alert-management-service
```

### Symptom: "Configuration not applied"

```bash
# 1. Check env vars in container
docker exec alert-management-service env | grep SPRING

# 2. Verify in docker-compose.yml
grep -A 20 "alert-management:" docker/docker-compose.yml

# 3. Restart container to pick up env changes
docker-compose stop alert-management
docker-compose up -d alert-management
```

### Symptom: "Service crashes immediately after rebuild"

```bash
# 1. Check logs
docker logs alert-management-service

# 2. Verify dependencies running
docker-compose ps postgres kafka redis

# 3. Check health
docker-compose logs alert-management-service --tail 50
```

---

## Summary: New Mental Model

### Old (Buggy) Process ❌
```
Code change → Maven build → docker-compose build → docker-compose up -d
           → Container still runs old code because of layer caching
```

### New (Correct) Process ✅
```
Code change → Maven build → docker system prune -f → docker-compose build --no-cache 
           → docker-compose stop/rm/up -d → curl verify → Tests run
           → Container has new code ✅
```

### When in Doubt 🤔
```
Use: bash scripts/rebuild.sh SERVICE_NAME true
      (The 'true' parameter forces full system cleanup)
```

---

## Files Updated

**Create these scripts:**
1. `scripts/rebuild.sh` - Master build script (RECOMMENDED)
2. `scripts/full-rebuild.sh` - Simple full rebuild
3. `scripts/quick-rebuild.sh` - Fast rebuild

**Update these:**
1. `docker/docker-compose.yml` - Add env var support
2. `README.md` - Document rebuild process

**Deprecate these:**
1. `application-docker.properties` - Move to env vars
2. Manual build commands - Use scripts instead

---

## Next Time This Happens

**Step 1: Recognize the symptom**
- Code changed but container behavior didn't
- "I know I fixed this" feeling

**Step 2: Use the script**
```bash
bash scripts/rebuild.sh YOUR_SERVICE true
```

**Step 3: Re-test**
```bash
bash scripts/test_backend_api_happy_path.sh
```

**Step 4: Done**
- No 5+ hours of troubleshooting
- No "Is it the code or the container?" uncertainty
- Reproducible, reliable deployment

---

**Document Version:** 1.0  
**Created:** 2025-10-22  
**Status:** Ready for implementation
