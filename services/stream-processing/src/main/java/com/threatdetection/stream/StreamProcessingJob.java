package com.threatdetection.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.threatdetection.stream.model.AttackEvent;
import com.threatdetection.stream.model.StatusEvent;
import com.threatdetection.stream.service.APTTemporalAccumulator;
import com.threatdetection.stream.sink.AttackEventJdbcSink;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * MVP Phase 0: 云原生威胁检测流处理作业
 * 
 * 重要更新: 集成3层时间窗口架构
 * - 替换单一窗口为30s/5min/15min多层窗口
 * - 支持不同类型威胁检测(勒索软件/主要威胁/APT)
 * - 保持与原系统的完全对齐
 */
public class StreamProcessingJob {

    private static final Logger logger = LoggerFactory.getLogger(StreamProcessingJob.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // 可配置的窗口大小参数
    private static final int AGGREGATION_WINDOW_SECONDS = Integer.parseInt(
        System.getenv().getOrDefault("AGGREGATION_WINDOW_SECONDS", "30"));
    private static final int THREAT_SCORING_WINDOW_MINUTES = Integer.parseInt(
        System.getenv().getOrDefault("THREAT_SCORING_WINDOW_MINUTES", "2"));

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ===== Checkpoint配置 - 保证Exactly-Once语义 =====
        logger.info("Configuring Flink checkpointing for exactly-once semantics");
        env.enableCheckpointing(60000);  // 每60秒checkpoint
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);  // 最小间隔30秒
        env.getCheckpointConfig().setCheckpointTimeout(600000);  // 超时10分钟
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);  // 同时只有1个checkpoint
        
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
        kafkaProps.setProperty("auto.offset.reset", "earliest");
        kafkaProps.setProperty("enable.auto.commit", "false");

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
                        WatermarkStrategy.<AttackEvent>forMonotonousTimestamps()
                                .withTimestampAssigner(new AttackEventTimestampAssigner()) // 使用显式类替代lambda
                )
                .name("attack-event-preprocessor");

        // ===== 分支1: 持久化到PostgreSQL (替代AttackEventPersistenceService) =====
        logger.info("Configuring PostgreSQL persistence sink for attack events");
        attackStream
            .addSink(AttackEventJdbcSink.createSinkWithEnvConfig())
            .name("attack-events-persistence")
            .uid("attack-events-persistence-sink")  // 用于checkpoint恢复
            .setParallelism(2);  // 并行度2,提高写入性能
        
        logger.info("PostgreSQL persistence sink configured successfully");

        // ===== 分支2: APT时序累积 (新增功能) =====
        logger.info("Configuring APT temporal accumulation for long-term threat analysis");
        // 注意: APT累积将在MultiTierWindowProcessor处理后添加

        // Status events stream - enabled for device health monitoring
        logger.info("Starting Status Event Processing for Device Health Monitoring");
        DataStream<StatusEvent> statusStream = env.fromSource(statusSource, WatermarkStrategy.noWatermarks(), "status-events")
        .flatMap(new StatusEventDeserializer())
        .name("status-event-deserializer")
        .filter(StreamProcessingJob::isStatusEventValid)
        .name("status-event-validation")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<StatusEvent>forMonotonousTimestamps()
                                .withTimestampAssigner(new StatusEventTimestampAssigner())
                );

        // Process status events for device health monitoring
        statusStream
                .map(new DeviceHealthAnalyzer())
                .name("device-health-analyzer")
                .sinkTo(createDeviceStatusKafkaSink(bootstrapServers))
                .name("device-health-kafka-sink");

        // ===== MVP PHASE 0: 3层时间窗口架构 =====
        // 替换单一窗口聚合为3层时间窗口 (30s/5min/15min)
        logger.info("Starting Multi-Tier Window Processing for MVP Phase 0");
        DataStream<String> threatAlerts = MultiTierWindowProcessor.processMultiTierWindows(
            attackStream, bootstrapServers);

        // ===== 分支2: APT时序累积 (新增功能) =====
        logger.info("Adding APT temporal accumulation sink");
        DataStream<com.threatdetection.stream.model.AggregatedAttackData> aggregatedData =
            MultiTierWindowProcessor.processMultiTierWindowsInternal(attackStream, bootstrapServers);
        aggregatedData.addSink(APTTemporalAccumulator.createSinkWithEnvConfig())
            .name("apt-temporal-accumulation")
            .uid("apt-temporal-accumulation-sink")
            .setParallelism(1); // APT累积使用单并行度保证顺序性
        logger.info("APT temporal accumulation sink configured successfully");

        // 备份: 保留原有逻辑用于比较和验证 - 已注释禁用避免序列化问题
        // logger.info("Starting legacy single-window processing for comparison");
        /*
        // 原有单窗口聚合 - 仅用于对比验证
        DataStream<String> legacyAggregations = attackStream
            .map(event -> {
                // 使用客户ID + 攻击MAC地址作为复合聚合键，确保多租户隔离
                String customerId = Optional.ofNullable(event.getCustomerId()).orElse("unknown");
                String attackMac = Optional.ofNullable(event.getAttackMac()).orElse("unknown");
                String aggregationKey = customerId + ":" + attackMac; // 复合键格式: customerId:attackMac
                
                // 包含端口信息用于威胁评分
                Tuple5<String, String, Integer, String, String> result = Tuple5.of(
                    aggregationKey,    // customerId:attackMac
                    event.getAttackIp(), // attackIp
                    event.getResponsePort(), // responsePort
                    event.getDevSerial(),   // devSerial
                    customerId             // customerId (for output)
                );
                logger.debug("Legacy: Mapping attack event {} to aggregation key: {}", event.getId(), result.f0);
                return result;
            })
            .returns(org.apache.flink.api.common.typeinfo.Types.TUPLE(
                org.apache.flink.api.common.typeinfo.Types.STRING,  // aggregationKey (customerId:attackMac)
                org.apache.flink.api.common.typeinfo.Types.STRING,  // attackIp
                org.apache.flink.api.common.typeinfo.Types.INT,     // responsePort
                org.apache.flink.api.common.typeinfo.Types.STRING,  // devSerial
                org.apache.flink.api.common.typeinfo.Types.STRING   // customerId
            ))
            .keyBy(tuple -> tuple.f0) // key by composite key (customerId:attackMac)
            .window(TumblingProcessingTimeWindows.of(Time.seconds(AGGREGATION_WINDOW_SECONDS)))
            .process(new AttackAggregationProcessFunction());
        */

        // 原有威胁评分 - 仅用于对比 (注释掉避免未使用警告)
        // DataStream<String> legacyThreatScores = legacyAggregations
        //         .map(new ThreatScoreCalculator())
        //         .keyBy(score -> score.f0) // key by attack source (MAC)
        //         .window(TumblingProcessingTimeWindows.of(Time.minutes(THREAT_SCORING_WINDOW_MINUTES)))
        //         .apply(new ThreatScoreAggregator());

        // Kafka sinks - MVP优先使用3层窗口结果
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

        // MVP Phase 0: 使用3层时间窗口结果作为主要输出
        threatAlerts.sinkTo(threatSink);
        
        // 备份: 保留原有流用于对比 (可选，用于验证) - 已禁用
        // legacyAggregations.sinkTo(aggregationSink);

        env.execute("Threat Detection Stream Processing");
    }

    // Deserializers
    public static class AttackEventDeserializer implements FlatMapFunction<String, AttackEvent> {
        @Override
        public void flatMap(String value, Collector<AttackEvent> out) {
            try {
                logger.info("Attempting to deserialize attack event: {}", value);
                AttackEvent event = objectMapper.readValue(value, AttackEvent.class);
                logger.info("Successfully deserialized attack event: {}", event.getId());
                out.collect(event);
            } catch (Exception e) {
                logger.error("Failed to deserialize attack event: {}", value, e);
            }
        }
    }

    public static class StatusEventDeserializer implements FlatMapFunction<String, StatusEvent> {
        @Override
        public void flatMap(String value, Collector<StatusEvent> out) {
            try {
                logger.info("Attempting to deserialize status event: {}", value);
                StatusEvent event = objectMapper.readValue(value, StatusEvent.class);
                logger.info("Successfully deserialized status event: {}", event.getDevSerial());
                out.collect(event);
            } catch (Exception e) {
                logger.error("Failed to deserialize status event: {}", value, e);
            }
        }
    }

    // Aggregation process function - 按客户和攻击来源聚合
    public static class AttackAggregationProcessFunction extends ProcessWindowFunction<
            Tuple5<String, String, Integer, String, String>, String, String, TimeWindow> {

        @Override
        public void process(String key, Context context, Iterable<Tuple5<String, String, Integer, String, String>> elements, Collector<String> out) throws Exception {
            logger.info("Processing aggregation window for customer:source: {}, window: {} to {}", key, context.window().getStart(), context.window().getEnd());
            Set<String> uniqueIps = new HashSet<>();
            Set<Integer> uniquePorts = new HashSet<>();
            Set<String> uniqueDevSerials = new HashSet<>();
            String customerId = null;
            int attackCount = 0;

            for (Tuple5<String, String, Integer, String, String> element : elements) {
                uniqueIps.add(element.f1); // attack IP
                uniquePorts.add(element.f2); // response port
                uniqueDevSerials.add(element.f3); // devSerial
                if (customerId == null) {
                    customerId = element.f4; // customerId (should be same for all elements in this window)
                }
                attackCount++;
            }

            logger.info("Aggregation for customer:source {}: {} unique IPs, {} unique ports, {} unique devices, {} attacks",
                key, uniqueIps.size(), uniquePorts.size(), uniqueDevSerials.size(), attackCount);

            // Convert port set to JSON array string for portList field
            StringBuilder portListJson = new StringBuilder("[");
            int i = 0;
            for (Integer port : uniquePorts) {
                if (i > 0) portListJson.append(",");
                portListJson.append(port);
                i++;
            }
            portListJson.append("]");

            // 增强的聚合输出：包含客户ID、端口多样性、设备信息、端口列表（用于端口权重计算）
            String result = String.format("{\"customerId\":\"%s\",\"attackMac\":\"%s\",\"uniqueIps\":%d,\"uniquePorts\":%d,\"uniqueDevices\":%d,\"attackCount\":%d,\"timestamp\":%d,\"windowStart\":%d,\"windowEnd\":%d,\"portList\":%s}",
                    customerId, key.substring(key.indexOf(":") + 1), uniqueIps.size(), uniquePorts.size(), uniqueDevSerials.size(), attackCount,
                    context.window().getEnd(),
                    context.window().getStart(),
                    context.window().getEnd(),
                    portListJson.toString());
            out.collect(result);
        }
    }

    // Threat score calculator - 按客户和攻击来源评分
    public static class ThreatScoreCalculator implements MapFunction<String, Tuple4<String, String, Double, Long>> {
        @Override
        public Tuple4<String, String, Double, Long> map(String value) throws Exception {
            // Parse aggregation JSON
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(value);
            String customerId = node.get("customerId").asText();
            String attackMac = node.get("attackMac").asText();
            String aggregationKey = customerId + ":" + attackMac; // 复合键
            int uniqueIps = node.get("uniqueIps").asInt();
            int uniquePorts = node.get("uniquePorts").asInt();
            int uniqueDevices = node.get("uniqueDevices").asInt();
            int attackCount = node.get("attackCount").asInt();
            long timestamp = node.get("timestamp").asLong();

            // Calculate time weight (from MySQL logic - score_weighting)
            double timeWeight = getTimeWeight(timestamp);

            // Calculate IP diversity weight (sum_ip from MySQL)
            double ipWeight = uniqueIps > 1 ? 2.0 : 1.0;

            // Calculate port diversity weight (new feature)
            double portWeight = calculatePortWeight(uniquePorts);

            // Calculate device diversity weight (for multi-device scenarios)
            double deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0;

            // Enhanced threat score calculation
            // Total_Score = (attackCount * uniqueIps * uniquePorts) * timeWeight * ipWeight * portWeight * deviceWeight
            double threatScore = (attackCount * uniqueIps * uniquePorts) * timeWeight * ipWeight * portWeight * deviceWeight;

            logger.info("Calculated threat score for customer:source {}: count={}, ips={}, ports={}, devices={}, timeWeight={}, score={}",
                aggregationKey, attackCount, uniqueIps, uniquePorts, uniqueDevices, timeWeight, threatScore);

            return Tuple4.of(aggregationKey, customerId, threatScore, timestamp);
        }

        /**
         * Calculate port diversity weight based on number of unique ports
         * Higher port diversity indicates more sophisticated attacks
         */
        private double calculatePortWeight(int uniquePorts) {
            if (uniquePorts <= 1) return 1.0;      // Single port - basic scan
            else if (uniquePorts <= 5) return 1.2;  // Few ports - targeted scan
            else if (uniquePorts <= 10) return 1.5; // Moderate ports - broader scan
            else if (uniquePorts <= 20) return 1.8; // Many ports - comprehensive scan
            else return 2.0; // Very high port diversity - sophisticated attack
        }

        private double getTimeWeight(long timestamp) {
            // Convert timestamp to hour of day
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            int hour = dateTime.getHour();

            if (hour >= 0 && hour < 5) return 1.2;    // 0-5h - suspicious timing
            else if (hour >= 5 && hour < 9) return 1.1; // 5-9h - early morning
            else if (hour >= 9 && hour < 17) return 1.0; // 9-17h - business hours
            else if (hour >= 17 && hour < 21) return 0.9; // 17-21h - evening
            else return 0.8; // 21-24h - night time
        }
    }

    // Threat score aggregator - 按客户和攻击来源聚合威胁评分
    public static class ThreatScoreAggregator implements WindowFunction<Tuple4<String, String, Double, Long>, String, String, TimeWindow> {
        @Override
        public void apply(String key, TimeWindow window, Iterable<Tuple4<String, String, Double, Long>> input, Collector<String> out) {
            logger.info("Processing threat scoring window for customer:source: {}, window: {} to {}", key, window.getStart(), window.getEnd());
            double maxScore = 0.0;
            long latestTimestamp = 0;
            String customerId = null;
            int totalAttacks = 0;

            for (Tuple4<String, String, Double, Long> score : input) {
                maxScore = Math.max(maxScore, score.f2);
                latestTimestamp = Math.max(latestTimestamp, score.f3);
                if (customerId == null) {
                    customerId = score.f1; // customerId
                }
                totalAttacks++;
            }

            logger.info("Threat score for customer:source {}: {} (from {} aggregations)", key, maxScore, totalAttacks);

            // Determine threat level based on score ranges
            String threatLevel = determineThreatLevel(maxScore);
            String threatName = getThreatLevelName(threatLevel);

            // Output format for customer-isolated threats
            String attackMac = key.substring(key.indexOf(":") + 1);
            out.collect(String.format("{\"customerId\":\"%s\",\"attackMac\":\"%s\",\"threatScore\":%.2f,\"threatLevel\":\"%s\",\"threatName\":\"%s\",\"timestamp\":%d,\"windowStart\":%d,\"windowEnd\":%d,\"totalAggregations\":%d}",
                    customerId, attackMac, maxScore, threatLevel, threatName, latestTimestamp, window.getStart(), window.getEnd(), totalAttacks));
        }

        private String determineThreatLevel(double score) {
            // Enhanced threat level determination based on comprehensive scoring
            // Considering attack frequency, IP diversity, port diversity, time patterns, and device coverage
            if (score >= 1000.0) return "CRITICAL";    // Very high threat - immediate action required
            else if (score >= 500.0) return "HIGH";     // High threat - urgent attention needed
            else if (score >= 200.0) return "MEDIUM";   // Medium threat - monitor closely
            else if (score >= 50.0) return "LOW";       // Low threat - routine monitoring
            else return "INFO";                         // Informational - minimal threat
        }

        private String getThreatLevelName(String level) {
            switch (level) {
                case "CRITICAL": return "严重威胁";
                case "HIGH": return "高危";
                case "MEDIUM": return "中危";
                case "LOW": return "低危";
                case "INFO": return "信息";
                default: return "未知";
            }
        }
    }
    
    /**
     * 验证状态事件的有效性
     */
    private static boolean isStatusEventValid(StatusEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getDevSerial() == null || event.getDevSerial().isEmpty()) {
            logger.warn("Dropping status event with missing devSerial: {}", event);
            return false;
        }
        // 验证计数字段必须非负
        if (event.getSentryCount() < 0 || event.getRealHostCount() < 0) {
            logger.warn("Dropping status event with negative counts: sentryCount={}, realHostCount={}", 
                       event.getSentryCount(), event.getRealHostCount());
            return false;
        }
        // 验证时间戳合理性
        if (event.getDevStartTime() < 0 || (event.getDevEndTime() != -1 && event.getDevEndTime() < event.getDevStartTime())) {
            logger.warn("Dropping status event with invalid timestamps: devStartTime={}, devEndTime={}", 
                       event.getDevStartTime(), event.getDevEndTime());
            return false;
        }
        return true;
    }
    
    private static boolean isAttackEventValid(AttackEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getDevSerial() == null || event.getDevSerial().isEmpty()) {
            logger.warn("Dropping attack event with missing devSerial: {}", event);
            return false;
        }
        // Phase 1A: 同步端口验证逻辑 - 支持特殊数据传递
        // 允许超出标准端口范围的值，包括负数和超过65535的值
        // 这些特殊值在威胁检测场景中可能表示特定的状态或异常情况
        if (!isValidPort(event.getResponsePort())) {
            logger.warn("Dropping attack event with invalid port: {} (allowed range: -65536 to 999999)", event.getResponsePort());
            return false;
        }
        return true;
    }

    /**
     * 检查端口是否在可接受范围内
     * 允许-65536到999999范围内的值，包括特殊值
     */
    private static boolean isValidPort(int port) {
        return port >= -65536 && port <= 999999;
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

    /**
     * 时间戳分配器 - 替代lambda避免序列化问题
     */
    public static class AttackEventTimestampAssigner implements SerializableTimestampAssigner<AttackEvent> {
        private static final long serialVersionUID = 1L;
        
        @Override
        public long extractTimestamp(AttackEvent event, long recordTimestamp) {
            return System.currentTimeMillis(); // 使用当前时间作为处理时间
        }
    }

    /**
     * 状态事件时间戳分配器
     */
    public static class StatusEventTimestampAssigner implements SerializableTimestampAssigner<StatusEvent> {
        private static final long serialVersionUID = 1L;
        
        @Override
        public long extractTimestamp(StatusEvent event, long recordTimestamp) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 状态事件格式化器
     */
    public static class StatusEventFormatter implements MapFunction<StatusEvent, String> {
        private static final long serialVersionUID = 1L;
        
        @Override
        public String map(StatusEvent event) throws Exception {
            return String.format("{\"devSerial\":\"%s\",\"logType\":%d,\"sentryCount\":%d,\"realHostCount\":%d,\"timestamp\":%d}",
                    event.getDevSerial(), event.getLogType(), event.getSentryCount(),
                    event.getRealHostCount(), event.getDevStartTime());
        }
    }
    
    /**
     * 设备健康分析器 - 分析状态事件并生成健康报告
     */
    public static class DeviceHealthAnalyzer implements MapFunction<StatusEvent, String> {
        private static final long serialVersionUID = 1L;
        private static final long SECONDS_IN_7_DAYS = 7 * 24 * 60 * 60;
        
        @Override
        public String map(StatusEvent event) throws Exception {
            long currentEpoch = System.currentTimeMillis() / 1000;
            
            // 分析设备到期状态
            boolean isExpired = false;
            boolean isExpiringSoon = false;
            long daysUntilExpiry = -1;
            
            if (event.getDevEndTime() != -1) {
                if (event.getDevEndTime() < currentEpoch) {
                    isExpired = true;
                } else {
                    long secondsUntilExpiry = event.getDevEndTime() - currentEpoch;
                    daysUntilExpiry = secondsUntilExpiry / 86400;
                    if (secondsUntilExpiry <= SECONDS_IN_7_DAYS) {
                        isExpiringSoon = true;
                    }
                }
            }
            
            // 构建JSON消息 (将发送到 device-health-alerts topic)
            return String.format(
                "{\"devSerial\":\"%s\"," +
                "\"sentryCount\":%d," +
                "\"realHostCount\":%d," +
                "\"devStartTime\":%d," +
                "\"devEndTime\":%d," +
                "\"reportTime\":%d," +
                "\"isExpired\":%b," +
                "\"isExpiringSoon\":%b," +
                "\"daysUntilExpiry\":%d," +
                "\"isHealthy\":%b," +
                "\"rawLog\":\"%s\"}",
                event.getDevSerial(),
                event.getSentryCount(),
                event.getRealHostCount(),
                event.getDevStartTime(),
                event.getDevEndTime(),
                currentEpoch,
                isExpired,
                isExpiringSoon,
                daysUntilExpiry,
                !isExpired,  // 未过期则认为健康
                event.getRawLog() != null ? event.getRawLog().replace("\"", "\\\"") : ""
            );
        }
    }
    
    /**
     * 创建设备状态Kafka Sink
     */
    private static KafkaSink<String> createDeviceStatusKafkaSink(String bootstrapServers) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("device-health-alerts")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .build();
    }
}