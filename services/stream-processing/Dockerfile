############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-17 AS build
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
COPY services/stream-processing/pom.xml ./services/stream-processing/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/stream-processing/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor, uses shade plugin for uber-jar)
COPY services/stream-processing/src ./services/stream-processing/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/stream-processing/pom.xml -DskipTests -B -q

############################
# Runtime stage
############################
FROM flink:1.18.1-scala_2.12-java17

WORKDIR /opt/flink

# Copy shaded JAR to lib directory (so both JobManager and TaskManager can load JDBC classes)
COPY --from=build /workspace/services/stream-processing/target/stream-processing-*.jar /opt/flink/lib/stream-processing.jar

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["standalone-job", "--job-classname", "com.threatdetection.stream.StreamProcessingJob"]
