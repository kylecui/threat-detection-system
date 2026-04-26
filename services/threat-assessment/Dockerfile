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
COPY services/threat-assessment/pom.xml ./services/threat-assessment/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/threat-assessment/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/threat-assessment/src ./services/threat-assessment/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/threat-assessment/pom.xml -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN apk add --no-cache curl

COPY --from=build /workspace/services/threat-assessment/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/api/v1/assessment/health || exit 1

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
