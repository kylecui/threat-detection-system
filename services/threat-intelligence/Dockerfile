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
COPY services/threat-intelligence/pom.xml ./services/threat-intelligence/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/threat-intelligence/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/threat-intelligence/src ./services/threat-intelligence/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/threat-intelligence/pom.xml -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/threat-intelligence/target/threat-intelligence-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
