package com.threatdetection.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.alert.DeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kafka消费者服务
 * 监听威胁检测事件并创建告警
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final AlertService alertService;
    private final DeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * 监听威胁检测事件
     */
    @KafkaListener(
            topics = "${app.kafka.topics.threat-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaManualAckListenerContainerFactory"
    )
    public void consumeThreatEvents(
            @Payload List<Map<String, Object>> events,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {

        log.info("接收到 {} 个威胁检测事件，主题: {}, 分区: {}, 偏移量: {}",
                events.size(), topic, partitions, offsets);

        try {
            for (Map<String, Object> event : events) {
                processThreatEvent(event);
            }

            // 手动提交偏移量
            acknowledgment.acknowledge();
            log.info("成功处理 {} 个威胁检测事件", events.size());

        } catch (Exception e) {
            log.error("处理威胁检测事件时发生错误", e);
            // 不提交偏移量，让Kafka重新投递
        }
    }

    /**
     * 处理单个威胁检测事件
     */
    private void processThreatEvent(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            String source = (String) event.get("source");
            String message = (String) event.get("message");
            Integer severity = (Integer) event.get("severity");
            Map<String, Object> metadata = (Map<String, Object>) event.get("metadata");

            // 如果是threat-assessment服务发布的threat-events，使用不同的字段映射
            if (eventType == null && event.containsKey("title")) {
                eventType = "THREAT_ASSESSMENT";
                source = "threat-assessment-service";
                message = (String) event.get("description");
            }

            log.debug("处理威胁事件: 类型={}, 来源={}, 消息={}, 严重程度={}",
                    eventType, source, message, severity);

            // 转换为告警严重程度
            AlertSeverity alertSeverity = mapToAlertSeverity(severity);

            // 创建告警对象
            Alert alert = Alert.builder()
                    .title(generateAlertTitle(eventType, source))
                    .description(message)
                    .severity(alertSeverity)
                    .status(AlertStatus.NEW)
                    .source(source)
                    .eventType(eventType)
                    .metadata(objectMapper.writeValueAsString(metadata))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 如果有threatScore字段，设置威胁分数
            if (event.containsKey("threatScore")) {
                Double threatScore = null;
                if (event.get("threatScore") instanceof Double) {
                    threatScore = (Double) event.get("threatScore");
                } else if (event.get("threatScore") instanceof Integer) {
                    threatScore = ((Integer) event.get("threatScore")).doubleValue();
                }
                if (threatScore != null) {
                    alert.setThreatScore(threatScore);
                }
            }

            // 如果有title字段，使用它来生成更好的标题
            if (event.containsKey("title")) {
                String threatTitle = (String) event.get("title");
                if (threatTitle != null && !threatTitle.isEmpty()) {
                    alert.setTitle(String.format("[%s] %s", source, threatTitle));
                }
            }

            // 检查是否为重复告警
            if (deduplicationService.isDuplicate(alert)) {
                log.info("检测到重复告警，已跳过: {}", alert.getTitle());
                return;
            }

            // 保存告警
            Alert savedAlert = alertService.createAlert(alert);
            log.info("成功创建告警: ID={}, 标题={}, 严重程度={}",
                    savedAlert.getId(), savedAlert.getTitle(), savedAlert.getSeverity());

        } catch (Exception e) {
            log.error("处理威胁事件时发生错误: {}", event, e);
        }
    }

    /**
     * 生成告警标题
     */
    private String generateAlertTitle(String eventType, String source) {
        return String.format("[%s] %s 威胁检测", source, eventType);
    }

    /**
     * 将整数严重程度映射为告警严重程度枚举
     */
    private AlertSeverity mapToAlertSeverity(Integer severity) {
        if (severity == null) {
            return AlertSeverity.MEDIUM;
        }

        switch (severity) {
            case 1:
                return AlertSeverity.LOW;
            case 2:
                return AlertSeverity.MEDIUM;
            case 3:
                return AlertSeverity.HIGH;
            case 4:
                return AlertSeverity.CRITICAL;
            default:
                return AlertSeverity.MEDIUM;
        }
    }
}