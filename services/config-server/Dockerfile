############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy parent POM first for dependency caching
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
RUN mvn dependency:go-offline -pl services/config-server -am -B -q || true

# Copy source for this module
COPY services/config-server/src ./services/config-server/src

# Build only this module
RUN mvn clean package -pl services/config-server -am -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/config-server/target/config-server-*.jar /app/app.jar
EXPOSE 8899
ENTRYPOINT ["java","-jar","/app/app.jar"]
