package com.threatdetection.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.stream.model.AttackEvent;
import com.threatdetection.stream.model.StatusEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class StreamProcessingJob {

    private static final Logger logger = LoggerFactory.getLogger(StreamProcessingJob.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Read configuration from environment variables
    String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") != null ?
        System.getenv("KAFKA_BOOTSTRAP_SERVERS") : "kafka:29092";
    String inputTopic = System.getenv("INPUT_TOPIC") != null ?
        System.getenv("INPUT_TOPIC") : "attack-events";
    String outputTopic = System.getenv("OUTPUT_TOPIC") != null ?
        System.getenv("OUTPUT_TOPIC") : "threat-alerts";
    String statusTopic = System.getenv("STATUS_TOPIC") != null ?
        System.getenv("STATUS_TOPIC") : "status-events";
    String aggregationTopic = System.getenv("AGGREGATION_TOPIC") != null ?
        System.getenv("AGGREGATION_TOPIC") : "minute-aggregations";

        logger.info("Using Kafka bootstrap servers: {}", bootstrapServers);
        logger.info("Using input topic: {}", inputTopic);
        logger.info("Using output topic: {}", outputTopic);
    logger.info("Using status topic: {}", statusTopic);
    logger.info("Using aggregation topic: {}", aggregationTopic);

    ensureTopicsAvailable(bootstrapServers, Arrays.asList(inputTopic, statusTopic, outputTopic, aggregationTopic));

    env.setRestartStrategy(
        RestartStrategies.fixedDelayRestart(5, org.apache.flink.api.common.time.Time.seconds(5))
    );

        // Kafka properties
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", bootstrapServers);
        kafkaProps.setProperty("group.id", "threat-detection-stream");

        // Kafka sources
        KafkaSource<String> attackSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("threat-detection-stream")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setProperty("partition.discovery.interval.ms", "10000")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        KafkaSource<String> statusSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
        .setTopics(statusTopic)
                .setGroupId("threat-detection-stream")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setProperty("partition.discovery.interval.ms", "10000")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Attack events stream
        DataStream<AttackEvent> attackStream = env.fromSource(attackSource, WatermarkStrategy.noWatermarks(), "attack-events")
        .flatMap(new AttackEventDeserializer())
        .name("attack-event-deserializer")
        .filter(StreamProcessingJob::isAttackEventValid)
        .name("attack-event-validation")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<AttackEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withTimestampAssigner((event, timestamp) -> event.getLogTime() * 1000)
                );

        // Status events stream
        DataStream<StatusEvent> statusStream = env.fromSource(statusSource, WatermarkStrategy.noWatermarks(), "status-events")
        .flatMap(new StatusEventDeserializer())
        .name("status-event-deserializer")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<StatusEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withTimestampAssigner((event, timestamp) -> event.getDevStartTime() * 1000)
                );

    // Minute-level aggregation for attack events
    DataStream<String> minuteAggregations = attackStream
        .map(event -> Tuple3.of(buildAggregationKey(event), event.getAttackIp(), 1))
        .returns(org.apache.flink.api.common.typeinfo.Types.TUPLE(
            org.apache.flink.api.common.typeinfo.Types.STRING,
            org.apache.flink.api.common.typeinfo.Types.STRING,
            org.apache.flink.api.common.typeinfo.Types.INT))
                .keyBy(tuple -> tuple.f0) // key by devSerial_port
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .process(new AttackAggregationProcessFunction());

        // 10-minute threat scoring
        DataStream<String> threatScores = minuteAggregations
                .map(new ThreatScoreCalculator())
                .keyBy(score -> score.f0) // key by devSerial
                .window(TumblingEventTimeWindows.of(Time.minutes(10)))
                .apply(new ThreatScoreAggregator());

        // Kafka sinks
        KafkaSink<String> aggregationSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.<String>builder()
            .setTopic(aggregationTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        KafkaSink<String> threatSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.<String>builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        minuteAggregations.sinkTo(aggregationSink);
        threatScores.sinkTo(threatSink);

        env.execute("Threat Detection Stream Processing");
    }

    // Deserializers
    public static class AttackEventDeserializer implements FlatMapFunction<String, AttackEvent> {
        @Override
        public void flatMap(String value, Collector<AttackEvent> out) {
            try {
                AttackEvent event = objectMapper.readValue(value, AttackEvent.class);
                out.collect(event);
            } catch (Exception e) {
                logger.warn("Failed to deserialize attack event: {}", value, e);
            }
        }
    }

    public static class StatusEventDeserializer implements FlatMapFunction<String, StatusEvent> {
        @Override
        public void flatMap(String value, Collector<StatusEvent> out) {
            try {
                StatusEvent event = objectMapper.readValue(value, StatusEvent.class);
                out.collect(event);
            } catch (Exception e) {
                logger.warn("Failed to deserialize status event: {}", value, e);
            }
        }
    }

    // Aggregation process function
    public static class AttackAggregationProcessFunction extends ProcessWindowFunction<
            Tuple3<String, String, Integer>, String, String, TimeWindow> {

        @Override
        public void process(String key, Context context, Iterable<Tuple3<String, String, Integer>> elements, Collector<String> out) throws Exception {
            Set<String> uniqueIps = new HashSet<>();
            int attackCount = 0;

            for (Tuple3<String, String, Integer> element : elements) {
                uniqueIps.add(element.f1);
                attackCount += element.f2;
            }

            // Parse key to get devSerial and port
            String[] parts = key.split("_", 2);
            if (parts.length != 2) {
                logger.warn("Unexpected aggregation key format: {}", key);
                return;
            }

            String devSerial = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                logger.warn("Failed to parse port from aggregation key {}: {}", key, ex.getMessage());
                return;
            }

            String result = String.format("{\"devSerial\":\"%s\",\"port\":%d,\"uniqueIps\":%d,\"attackCount\":%d,\"timestamp\":%d}",
                    devSerial, port, uniqueIps.size(), attackCount, context.window().getEnd());
            out.collect(result);
        }
    }

    // Threat score calculator
    public static class ThreatScoreCalculator implements MapFunction<String, Tuple3<String, Double, Long>> {
        @Override
        public Tuple3<String, Double, Long> map(String value) throws Exception {
            // Parse aggregation JSON
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(value);
            String devSerial = node.get("devSerial").asText();
            int port = node.get("port").asInt();
            int uniqueIps = node.get("uniqueIps").asInt();
            int attackCount = node.get("attackCount").asInt();
            long timestamp = node.get("timestamp").asLong();

            // Calculate port weight
            double portWeight = getPortWeight(port);

            // Calculate time weight
            double timeWeight = getTimeWeight(timestamp);

            // Calculate threat score
            double threatScore = (portWeight * uniqueIps * attackCount) * timeWeight;

            return Tuple3.of(devSerial, threatScore, timestamp);
        }

        private double getPortWeight(int port) {
            // Common vulnerable ports have higher weights
            switch (port) {
                case 22: return 2.0;   // SSH
                case 23: return 1.8;   // Telnet
                case 80: return 1.5;   // HTTP
                case 443: return 1.5;  // HTTPS
                case 3389: return 2.0; // RDP
                case 21: return 1.8;   // FTP
                case 25: return 1.6;   // SMTP
                case 53: return 1.4;   // DNS
                case 110: return 1.6;  // POP3
                case 143: return 1.6;  // IMAP
                case 3306: return 2.0; // MySQL
                case 5432: return 2.0; // PostgreSQL
                default: return 1.0;   // Default weight
            }
        }

        private double getTimeWeight(long timestamp) {
            // Convert timestamp to hour of day
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            int hour = dateTime.getHour();

            if (hour >= 0 && hour < 5) return 1.2;    // 0-5h
            else if (hour >= 5 && hour < 9) return 1.1; // 5-9h
            else if (hour >= 9 && hour < 17) return 1.0; // 9-17h
            else if (hour >= 17 && hour < 21) return 0.9; // 17-21h
            else return 0.8; // 21-24h
        }
    }

    // Threat score aggregator
    public static class ThreatScoreAggregator implements WindowFunction<Tuple3<String, Double, Long>, String, String, TimeWindow> {
        @Override
        public void apply(String key, TimeWindow window, Iterable<Tuple3<String, Double, Long>> input, Collector<String> out) {
            double maxScore = 0.0;
            long latestTimestamp = 0;
            for (Tuple3<String, Double, Long> score : input) {
                maxScore = Math.max(maxScore, score.f1);
                latestTimestamp = Math.max(latestTimestamp, score.f2);
            }
            out.collect(String.format("{\"devSerial\":\"%s\",\"threatScore\":%.2f,\"timestamp\":%d}",
                    key, maxScore, latestTimestamp));
        }
    }
    private static boolean isAttackEventValid(AttackEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getDevSerial() == null || event.getDevSerial().isEmpty()) {
            logger.warn("Dropping attack event with missing devSerial: {}", event);
            return false;
        }
        if (event.getResponsePort() <= 0) {
            logger.warn("Dropping attack event with invalid port: {}", event);
            return false;
        }
        return true;
    }

    private static String buildAggregationKey(AttackEvent event) {
        String devSerial = Optional.ofNullable(event.getDevSerial()).orElse("unknown");
        int port = event.getResponsePort() > 0 ? event.getResponsePort() : 0;
        return devSerial + "_" + port;
    }

    private static void ensureTopicsAvailable(String bootstrapServers, List<String> topics) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (Admin admin = AdminClient.create(adminProps)) {
            Set<String> requiredTopics = new HashSet<>(topics);
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);

            while (System.currentTimeMillis() < deadline) {
                Set<String> existingTopics = admin.listTopics().names().get(5, TimeUnit.SECONDS);
                if (existingTopics.containsAll(requiredTopics)) {
                    logger.info("All required Kafka topics are available: {}", requiredTopics);
                    return;
                }

                Set<String> missing = requiredTopics.stream()
                        .filter(topic -> !existingTopics.contains(topic))
                        .collect(Collectors.toSet());

                logger.warn("Waiting for Kafka topics to become available: {}", missing);
                TimeUnit.SECONDS.sleep(5);
            }

            throw new IllegalStateException("Kafka topics not available within timeout: " + topics);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka topics", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to verify Kafka topics availability", e);
        }
    }
}