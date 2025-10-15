############################
# Runtime stage (直接使用本地编译的JAR)
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/data-ingestion-service-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
