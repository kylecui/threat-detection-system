FROM flink:1.17.1-scala_2.12-java11

WORKDIR /opt/flink

# Copy shaded JAR to lib directory (so both JobManager and TaskManager can load JDBC classes)
COPY target/stream-processing-*.jar /opt/flink/lib/stream-processing.jar

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["standalone-job", "--job-classname", "com.threatdetection.stream.StreamProcessingJob"]