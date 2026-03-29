############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-11 AS build
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
RUN mvn dependency:go-offline -pl services/stream-processing -am -B -q || true

# Copy source for this module
COPY services/stream-processing/src ./services/stream-processing/src

# Build only this module (uses shade plugin for uber-jar)
RUN mvn clean package -pl services/stream-processing -am -DskipTests -B -q

############################
# Runtime stage
############################
FROM flink:1.17.1-scala_2.12-java11

WORKDIR /opt/flink

# Copy shaded JAR to lib directory (so both JobManager and TaskManager can load JDBC classes)
COPY --from=build /workspace/services/stream-processing/target/stream-processing-*.jar /opt/flink/lib/stream-processing.jar

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["standalone-job", "--job-classname", "com.threatdetection.stream.StreamProcessingJob"]
