package com.threatdetection.stream.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

/**
 * 攻击事件JDBC Sink - 持久化到PostgreSQL
 * 
 * <p>功能: 将Flink处理的攻击事件持久化到attack_events表
 * <p>替代: data-ingestion中的AttackEventPersistenceService
 * <p>优势: 
 * - 单一消费点,避免Kafka offset竞争
 * - Exactly-once语义保证 (Flink checkpoint)
 * - 批量写入,性能更优 (100条/批)
 * - 与聚合处理在同一流中,数据一致性更好
 * 
 * @author Threat Detection System
 * @version 2.0
 * @since 2025-10-27
 */
public class AttackEventJdbcSink {
    
    private static final Logger logger = LoggerFactory.getLogger(AttackEventJdbcSink.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    /**
     * 创建攻击事件持久化Sink
     * 
     * @param jdbcUrl JDBC连接URL
     * @param jdbcUser 数据库用户名
     * @param jdbcPassword 数据库密码
     * @return SinkFunction
     */
    public static SinkFunction<AttackEvent> createSink(
            String jdbcUrl, 
            String jdbcUser, 
            String jdbcPassword) {
        
        logger.info("Creating JDBC Sink for attack events persistence");
        logger.info("JDBC URL: {}", jdbcUrl.replaceAll("password=[^&]*", "password=***"));
        
        // SQL插入语句
        String insertSQL = 
            "INSERT INTO attack_events " +
            "(customer_id, dev_serial, attack_mac, attack_ip, response_ip, " +
            " response_port, event_timestamp, log_time, raw_log_data) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) " +
            "ON CONFLICT DO NOTHING";  // 防止重复插入 (幂等性保证)
        
        return JdbcSink.sink(
            insertSQL,
            (JdbcStatementBuilder<AttackEvent>) (statement, event) -> {
                try {
                    // 设置参数
                    statement.setString(1, event.getCustomerId());
                    statement.setString(2, event.getDevSerial());
                    statement.setString(3, event.getAttackMac());
                    statement.setString(4, event.getAttackIp());
                    statement.setString(5, event.getResponseIp());
                    statement.setInt(6, event.getResponsePort());
                    
                    // 时间戳转换
                    statement.setTimestamp(7, Timestamp.from(event.getTimestamp()));
                    statement.setLong(8, event.getLogTime());
                    
                    // 将事件序列化为JSON存储在raw_log_data字段
                    String jsonData = objectMapper.writeValueAsString(event);
                    statement.setString(9, jsonData);
                    
                    // 调试日志 (每100条打印一次,避免日志过多)
                    if (Math.random() < 0.01) {  // 1% 采样
                        logger.debug("Persisting attack event: customerId={}, attackMac={}, port={}", 
                            event.getCustomerId(), event.getAttackMac(), event.getResponsePort());
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to prepare statement for attack event: customerId={}, error={}", 
                        event.getCustomerId(), e.getMessage(), e);
                    throw new RuntimeException("Statement preparation failed", e);
                }
            },
            JdbcExecutionOptions.builder()
                .withBatchSize(100)              // 批量插入100条 (性能优化)
                .withBatchIntervalMs(1000)       // 或每1秒flush一次
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
     * 使用默认配置创建Sink (从环境变量读取)
     * 
     * @return SinkFunction
     */
    public static SinkFunction<AttackEvent> createSinkWithEnvConfig() {
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
}
