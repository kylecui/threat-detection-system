# syntax=docker/dockerfile:1.7

############################
# Build stage
############################
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先安装父 POM 到本地仓库（注意：也使用同一个 /root/.m2 缓存挂载）
COPY ./pom.xml /tmp/parent-pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -N -f /tmp/parent-pom.xml install


# 拷贝子模块 pom 并预拉依赖
COPY ./services/data-ingestion/data-ingestion-service/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests dependency:go-offline

# 拷贝子模块源码并打包
COPY ./services/data-ingestion/data-ingestion-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/data-ingestion-service-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
