############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ARG USE_CHINA_MIRROR=false
COPY maven-settings.xml /tmp/maven-settings.xml
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
      cp /tmp/maven-settings.xml /root/.m2/settings.xml; \
    fi
# Install parent POM only (non-recursive, no module resolution)
COPY pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn install -N -B -q

# Copy only this module's POM for dependency resolution
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/api-gateway/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/api-gateway/src ./services/api-gateway/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/api-gateway/pom.xml -DskipTests -B -q

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
