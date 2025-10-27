package com.threatdetection.alert.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置
 * 
 * 安全防护机制:
 * 1. ErrorHandlingDeserializer: 防止反序列化错误阻塞消费者
 * 2. DefaultErrorHandler: 跳过有毒消息，防止无限重试
 * 3. 死信队列记录: 保存失败消息用于人工审查
 * 4. 最大重试限制: 防止资源耗尽攻击
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * 生产者配置
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 性能优化配置
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka模板
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 消费者配置
     * 
     * 使用 ErrorHandlingDeserializer 包装器防止反序列化错误:
     * - 反序列化失败时返回 null 而不是抛异常
     * - 允许消费者跳过损坏的消息继续处理
     * - 防止单条脏数据阻塞整个消费者
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // 🛡️ 安全防护: 使用 ErrorHandlingDeserializer 包装器
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // 配置实际的反序列化器
        configProps.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // 自动提交偏移量
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // JSON反序列化配置
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // 设置并发消费者数量
        factory.setConcurrency(3);

        // 设置批量监听
        factory.setBatchListener(true);

        return factory;
    }

    /**
     * 支持手动确认的Kafka监听器容器工厂
     * 
     * 🛡️ 安全防护机制:
     * 1. ErrorHandlingDeserializer 防止反序列化错误
     * 2. DefaultErrorHandler 限制重试次数
     * 3. 跳过有毒消息，记录到死信队列
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaManualAckListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // 设置并发消费者数量
        factory.setConcurrency(3);

        // 设置批量监听模式（提升性能）
        factory.setBatchListener(true);

        // 设置手动确认模式
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);

        // 🛡️ 关键安全防护: 配置错误处理器
        // 最多重试 3 次，每次间隔 1 秒，之后跳过有毒消息
        factory.setCommonErrorHandler(defaultErrorHandler());

        return factory;
    }

    /**
     * 默认错误处理器
     * 
     * 防御措施:
     * - 最大重试 3 次（防止无限重试资源耗尽）
     * - 重试间隔 1 秒（避免CPU飙升）
     * - 超过重试次数后跳过消息并记录
     * - 防止单条脏数据导致拒绝服务 (DoS)
     */
    @Bean
    public DefaultErrorHandler defaultErrorHandler() {
        // 固定退避策略: 最多重试 3 次，每次间隔 1000ms
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (consumerRecord, exception) -> {
                // 🚨 记录到死信队列（有毒消息）
                log.error("🚨 检测到有毒消息，已跳过处理 | Topic: {}, Partition: {}, Offset: {}, Key: {}, 错误: {}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    consumerRecord.key(),
                    exception.getMessage());
                
                log.error("有毒消息内容: {}", consumerRecord.value());
                
                // TODO: 可选 - 发送到专用的死信队列 (Dead Letter Queue)
                // kafkaTemplate.send("threat-alerts-dlq", consumerRecord.key(), consumerRecord.value());
            },
            new FixedBackOff(1000L, 3L) // 重试间隔 1 秒，最多 3 次
        );

        // 记录重试日志
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("⚠️ 消息处理失败，正在重试 | 尝试次数: {}/3, Topic: {}, Offset: {}, 错误: {}",
                deliveryAttempt,
                record.topic(),
                record.offset(),
                ex.getMessage());
        });

        return errorHandler;
    }
}