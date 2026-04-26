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
COPY services/customer-management/pom.xml ./services/customer-management/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/customer-management/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/customer-management/src ./services/customer-management/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/customer-management/pom.xml -DskipTests -B -q

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
