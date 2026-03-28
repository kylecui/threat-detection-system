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
RUN mvn dependency:go-offline -pl services/api-gateway -am -B -q || true

# Copy source for this module
COPY services/api-gateway/src ./services/api-gateway/src

# Build only this module
RUN mvn clean package -pl services/api-gateway -am -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Threat Detection Team"
LABEL description="API Gateway for Threat Detection System"

WORKDIR /app

RUN mkdir -p /app/logs && chmod 777 /app/logs

COPY --from=build /workspace/services/api-gateway/target/*.jar app.jar

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=docker", \
    "-jar", \
    "app.jar"]
