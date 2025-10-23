FROM eclipse-temurin:21-jre-alpine

# 添加维护者信息
LABEL maintainer="Threat Detection Team"
LABEL description="API Gateway for Threat Detection System"

# 创建应用目录
WORKDIR /app

# 创建日志目录
RUN mkdir -p /app/logs && \
    chmod 777 /app/logs

# 复制JAR文件
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 暴露端口
EXPOSE 8888

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=docker", \
    "-jar", \
    "app.jar"]
