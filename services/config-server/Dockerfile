############################
# Runtime stage (直接使用本地编译的JAR)
############################
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/config-server-*.jar /app/app.jar
EXPOSE 8899
ENTRYPOINT ["java","-jar","/app/app.jar"]
