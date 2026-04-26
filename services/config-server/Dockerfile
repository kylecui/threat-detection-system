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
COPY services/config-server/pom.xml ./services/config-server/pom.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -f services/config-server/pom.xml -B -q || true

# Copy source and build this module only (direct POM, no reactor)
COPY services/config-server/src ./services/config-server/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -f services/config-server/pom.xml -DskipTests -B -q

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/config-server/target/config-server-*.jar /app/app.jar
EXPOSE 8899
ENTRYPOINT ["java","-jar","/app/app.jar"]
