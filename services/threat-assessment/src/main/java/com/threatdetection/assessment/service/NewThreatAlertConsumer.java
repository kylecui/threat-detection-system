package com.threatdetection.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.assessment.dto.AggregatedAttackData;
import com.threatdetection.assessment.dto.ThreatAlertMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka威胁告警消费者 - 从threat-alerts主题接收消息
 * 
 * <p>工作流程:
 * 1. 从Kafka接收威胁告警消息
 * 2. 解析为AggregatedAttackData
 * 3. 调用ThreatAssessmentService进行评估
 * 4. 确认消息处理完成
 * 
 * <p>错误处理:
 * - 失败重试 (最多3次)
 * - 重试失败后发送到死信队列
 * 
 * @author Security Team
 * @version 2.0
 */
@Service
public class NewThreatAlertConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(NewThreatAlertConsumer.class);
    
    private final ThreatAssessmentService assessmentService;
    private final ObjectMapper objectMapper;
    
    // Prometheus指标
    private final Counter receivedCounter;
    private final Counter processedCounter;
    private final Counter failedCounter;
    
    @Autowired
    public NewThreatAlertConsumer(
            ThreatAssessmentService assessmentService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.assessmentService = assessmentService;
        this.objectMapper = objectMapper;
        
        // 初始化Prometheus指标
        this.receivedCounter = Counter.builder("kafka.threat_alerts.received")
                .description("Total threat alerts received from Kafka")
                .register(meterRegistry);
        this.processedCounter = Counter.builder("kafka.threat_alerts.processed")
                .description("Successfully processed threat alerts")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("kafka.threat_alerts.failed")
                .description("Failed threat alert processing")
                .register(meterRegistry);
    }
    
    /**
     * 消费威胁告警消息
     * 
     * @param message Kafka消息体 (JSON格式)
     * @param partition Kafka分区
     * @param offset Kafka偏移量
     * @param acknowledgment 手动确认
     */
    @KafkaListener(
        topics = "${kafka.topics.threat-alerts:threat-alerts}",
        groupId = "${kafka.consumer.group-id:threat-assessment-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeThreatAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.debug("📨 Received threat alert: partition={}, offset={}", partition, offset);
        receivedCounter.increment();
        
        try {
            // 1. 解析JSON消息
            ThreatAlertMessage alertMessage = objectMapper.readValue(message, ThreatAlertMessage.class);
            
            logger.info("📨 Processing threat alert: customerId={}, attackMac={}, score={}, level={}",
                       alertMessage.getCustomerId(), 
                       alertMessage.getAttackMac(),
                       alertMessage.getThreatScore(),
                       alertMessage.getThreatLevel());
            
            // 2. 转换为聚合攻击数据
            AggregatedAttackData data = alertMessage.toAggregatedData();
            
            // 3. 验证数据完整性
            if (!data.isValid()) {
                logger.error("❌ Invalid threat alert data: {}", alertMessage);
                failedCounter.increment();
                acknowledgment.acknowledge();  // 确认消息,避免重复处理无效数据
                return;
            }
            
            // 4. 执行威胁评估
            assessmentService.assessThreat(data);
            
            // 5. 确认消息处理完成
            acknowledgment.acknowledge();
            processedCounter.increment();
            
            logger.info("✅ Threat alert processed successfully: customerId={}, attackMac={}",
                       data.getCustomerId(), data.getAttackMac());
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("❌ Failed to parse threat alert JSON: partition={}, offset={}, error={}",
                        partition, offset, e.getMessage());
            failedCounter.increment();
            acknowledgment.acknowledge();  // 确认消息,避免重复处理格式错误的数据
            
        } catch (Exception e) {
            logger.error("❌ Failed to process threat alert: partition={}, offset={}, error={}",
                        partition, offset, e.getMessage(), e);
            failedCounter.increment();
            
            // 不确认消息,触发Kafka重试机制
            // Kafka将根据配置的重试策略重新投递消息
            logger.warn("⚠️ Message will be retried: partition={}, offset={}", partition, offset);
        }
    }
    
    /**
     * 获取消费者指标
     * 
     * @return 消费者统计信息
     */
    public ConsumerMetrics getMetrics() {
        return new ConsumerMetrics(
            (long) receivedCounter.count(),
            (long) processedCounter.count(),
            (long) failedCounter.count()
        );
    }
    
    /**
     * 消费者指标类
     */
    public static class ConsumerMetrics {
        private final long receivedCount;
        private final long processedCount;
        private final long failedCount;
        
        public ConsumerMetrics(long receivedCount, long processedCount, long failedCount) {
            this.receivedCount = receivedCount;
            this.processedCount = processedCount;
            this.failedCount = failedCount;
        }
        
        public long getReceivedCount() { return receivedCount; }
        public long getProcessedCount() { return processedCount; }
        public long getFailedCount() { return failedCount; }
        
        public double getSuccessRate() {
            return receivedCount > 0 ? (double) processedCount / receivedCount * 100 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ConsumerMetrics{received=%d, processed=%d, failed=%d, successRate=%.2f%%}",
                               receivedCount, processedCount, failedCount, getSuccessRate());
        }
    }
}
