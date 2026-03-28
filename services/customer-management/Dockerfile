############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./pom.xml

# Copy all module POMs for dependency resolution
COPY services/data-ingestion/pom.xml ./services/data-ingestion/pom.xml
COPY services/stream-processing/pom.xml ./services/stream-processing/pom.xml
COPY services/alert-management/pom.xml ./services/alert-management/pom.xml
COPY services/threat-assessment/pom.xml ./services/threat-assessment/pom.xml
COPY services/customer-management/pom.xml ./services/customer-management/pom.xml
COPY services/threat-intelligence/pom.xml ./services/threat-intelligence/pom.xml
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/config-server/pom.xml ./services/config-server/pom.xml

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -pl services/customer-management -am -B -q || true

# Copy source for this module
COPY services/customer-management/src ./services/customer-management/src

# Build only this module
RUN mvn clean package -pl services/customer-management -am -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/services/customer-management/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
