############################
# Runtime stage (直接使用本地编译的JAR)
############################
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Install curl for health checks (Debian-based, not Alpine)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built JAR from local target directory
COPY target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

# Expose port
EXPOSE 8084

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
