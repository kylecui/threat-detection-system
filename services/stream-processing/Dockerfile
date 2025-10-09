FROM flink:1.17.1-scala_2.12-java11

WORKDIR /opt/flink

COPY target/stream-processing-*.jar /opt/flink/lib/

# Copy Flink job jar to lib directory
RUN mv /opt/flink/lib/stream-processing-*.jar /opt/flink/lib/stream-processing.jar

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["standalone-job", "--job-classname", "com.threatdetection.stream.StreamProcessingJob"]