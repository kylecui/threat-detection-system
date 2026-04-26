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
COPY services/data-ingestion/pom.xml ./services/data-ingestion/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/data-ingestion/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/data-ingestion/src ./services/data-ingestion/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/data-ingestion/pom.xml -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/data-ingestion/target/data-ingestion-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
