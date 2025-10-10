# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY ../../.mvn /app/.mvn
COPY ../../mvnw /app/mvnw
COPY services/threat-assessment/docker-pom.xml /app/pom.xml

# Copy source code
COPY services/threat-assessment/src /app/src

# Download dependencies and build
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/api/v1/assessment/health || exit 1

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]