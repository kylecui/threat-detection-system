package com.threatdetection.stream.service;

import com.threatdetection.stream.model.AggregatedAttackData;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * APT时序累积器服务
 *
 * <p>负责聚合30-90天的攻击数据，计算指数衰减累积评分
 * 支持APT攻击的长期行为分析和阶段推断
 *
 * @author Threat Detection Team
 * @version 1.0
 */
public class APTTemporalAccumulator {

    private static final Logger logger = LoggerFactory.getLogger(APTTemporalAccumulator.class);

    // 配置参数
    private static final int DEFAULT_HALF_LIFE_DAYS = 30;
    private static final int DEFAULT_WINDOW_DAYS = 90;

    /**
     * 创建APT时序累积数据Sink
     *
     * @param jdbcUrl JDBC连接URL
     * @param jdbcUser 数据库用户名
     * @param jdbcPassword 数据库密码
     * @return SinkFunction for AggregatedAttackData
     */
    public static SinkFunction<AggregatedAttackData> createSink(
            String jdbcUrl,
            String jdbcUser,
            String jdbcPassword) {

        logger.info("Creating APT Temporal Accumulator JDBC Sink");
        logger.info("JDBC URL: {}", jdbcUrl.replaceAll("password=[^&]*", "password=***"));

        // UPSERT SQL - 插入或更新累积数据
        String upsertSQL =
            "INSERT INTO apt_temporal_accumulations (" +
            "customer_id, attack_mac, attack_ip, " +
            "total_attack_count, unique_ips_count, unique_ports_count, unique_devices_count, " +
            "decay_accumulated_score, half_life_days, " +
            "inferred_attack_phase, phase_confidence, " +
            "window_start, window_end, last_updated) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (customer_id, attack_mac) DO UPDATE SET " +
            "total_attack_count = EXCLUDED.total_attack_count, " +
            "unique_ips_count = GREATEST(apt_temporal_accumulations.unique_ips_count, EXCLUDED.unique_ips_count), " +
            "unique_ports_count = GREATEST(apt_temporal_accumulations.unique_ports_count, EXCLUDED.unique_ports_count), " +
            "unique_devices_count = GREATEST(apt_temporal_accumulations.unique_devices_count, EXCLUDED.unique_devices_count), " +
            "decay_accumulated_score = " +
            "  (apt_temporal_accumulations.decay_accumulated_score * " +
            "   POWER(2.0, -EXTRACT(EPOCH FROM (EXCLUDED.last_updated - apt_temporal_accumulations.last_updated)) / (86400 * apt_temporal_accumulations.half_life_days))) + " +
            "  EXCLUDED.decay_accumulated_score, " +
            "inferred_attack_phase = EXCLUDED.inferred_attack_phase, " +
            "phase_confidence = EXCLUDED.phase_confidence, " +
            "window_start = EXCLUDED.window_start, " +
            "window_end = EXCLUDED.window_end, " +
            "last_updated = EXCLUDED.last_updated";

        return JdbcSink.sink(
            upsertSQL,
            (JdbcStatementBuilder<AggregatedAttackData>) (statement, data) -> {
                try {
                    // 计算累积时间窗口
                    Instant windowEnd = Instant.now();
                    Instant windowStart = windowEnd.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS);

                    // 推断攻击阶段 (简化实现 - 在实际使用中应该传递端口列表)
                    AttackPhasePortConfigService configService = new AttackPhasePortConfigService();
                    AttackPhaseClassifier classifier = new AttackPhaseClassifier(configService);
                    AttackPhaseClassifier.AttackPhaseClassification phaseInfo =
                        classifier.classifyPhase(data.getCustomerId(), data.getAttackMac(), null, data.getAttackCount());

                    // 设置UPSERT参数
                    statement.setString(1, data.getCustomerId());
                    statement.setString(2, data.getAttackMac());
                    statement.setString(3, data.getAttackIp());
                    statement.setLong(4, data.getAttackCount());  // 初始累积或增量
                    statement.setInt(5, data.getUniqueIps());
                    statement.setInt(6, data.getUniquePorts());
                    statement.setInt(7, data.getUniqueDevices());
                    statement.setDouble(8, data.getThreatScore());  // 初始累积评分
                    statement.setInt(9, DEFAULT_HALF_LIFE_DAYS);
                    statement.setString(10, phaseInfo.getPhase());
                    statement.setDouble(11, phaseInfo.getConfidence());
                    statement.setTimestamp(12, Timestamp.from(windowStart));
                    statement.setTimestamp(13, Timestamp.from(windowEnd));
                    statement.setTimestamp(14, Timestamp.from(Instant.now()));

                    logger.debug("Accumulating APT data for customer:{}, attackMac:{}, score:{}, phase:{}",
                        data.getCustomerId(), data.getAttackMac(), data.getThreatScore(), phaseInfo.getPhase());

                } catch (Exception e) {
                    logger.error("Failed to prepare APT accumulation statement for customer:{}, attackMac:{}",
                        data.getCustomerId(), data.getAttackMac(), e);
                    throw new RuntimeException("APT accumulation statement preparation failed", e);
                }
            },
            JdbcExecutionOptions.builder()
                .withBatchSize(50)               // 较小的批量以确保实时性
                .withBatchIntervalMs(5000)       // 每5秒flush一次
                .withMaxRetries(3)               // 失败重试3次
                .build(),
            new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(jdbcUrl)
                .withDriverName("org.postgresql.Driver")
                .withUsername(jdbcUser)
                .withPassword(jdbcPassword)
                .build()
        );
    }

    /**
     * 使用环境变量配置创建Sink
     *
     * @return SinkFunction
     */
    public static SinkFunction<AggregatedAttackData> createSinkWithEnvConfig() {
        String jdbcUrl = System.getenv().getOrDefault(
            "JDBC_URL",
            "jdbc:postgresql://postgres:5432/threat_detection");
        String jdbcUser = System.getenv().getOrDefault(
            "JDBC_USER",
            "threat_user");
        String jdbcPassword = System.getenv().getOrDefault(
            "JDBC_PASSWORD",
            "threat_password");

        return createSink(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * 计算指数衰减权重
     *
     * <p>公式: weight = 2^(-days_since_last_update / half_life)
     *
     * @param lastUpdated 最后更新时间
     * @param halfLifeDays 半衰期天数
     * @return 衰减权重 (0.0-1.0)
     */
    public static double calculateDecayWeight(Instant lastUpdated, int halfLifeDays) {
        long daysSinceUpdate = ChronoUnit.DAYS.between(lastUpdated, Instant.now());
        if (daysSinceUpdate <= 0) {
            return 1.0; // 没有时间间隔，完全权重
        }

        // 指数衰减公式: 2^(-t/half_life)
        double exponent = - (double) daysSinceUpdate / halfLifeDays;
        double weight = Math.pow(2.0, exponent);

        // 确保权重在合理范围内
        return Math.max(0.001, Math.min(1.0, weight));
    }
}