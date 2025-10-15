FROM flink:1.17.1-scala_2.12-java11

WORKDIR /opt/flink

COPY target/stream-processing-*.jar /opt/flink/usrlib/

# Copy Flink job jar to usrlib directory (Application Mode requirement)
RUN mv /opt/flink/usrlib/stream-processing-*.jar /opt/flink/usrlib/stream-processing.jar

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["standalone-job", "--job-classname", "com.threatdetection.stream.StreamProcessingJob"]