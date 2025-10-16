package com.threatdetection.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.*;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.alert.DeduplicationService;
import com.threatdetection.alert.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kafka消费者服务
 * 监听威胁检测事件并创建告警
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final AlertService alertService;
    private final NotificationService notificationService;
    private final DeduplicationService deduplicationService;
    private final CustomerNotificationConfigRepository customerNotificationConfigRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${integration-test.email.recipient:kylecui@outlook.com}")
    private String defaultEmailRecipient;

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
            String customerId = (String) event.get("customerId");  // 提取客户ID
            Map<String, Object> metadata = (Map<String, Object>) event.get("metadata");

            // 如果是threat-assessment服务发布的threat-events，使用不同的字段映射
            if (eventType == null && event.containsKey("title")) {
                eventType = "THREAT_ASSESSMENT";
                source = "threat-assessment-service";
                message = (String) event.get("description");
            }

            log.debug("处理威胁事件: 类型={}, 来源={}, 消息={}, 严重程度={}, 客户ID={}",
                    eventType, source, message, severity, customerId);

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

            // 对于CRITICAL级别的告警，自动发送邮件通知
            if (savedAlert.getSeverity() == AlertSeverity.CRITICAL) {
                try {
                    sendCriticalAlertNotification(savedAlert, customerId);
                } catch (Exception e) {
                    log.error("发送CRITICAL告警邮件通知失败: alertId={}", savedAlert.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("处理威胁事件时发生错误: {}", event, e);
        }
    }
    
    /**
     * 发送CRITICAL告警通知
     * 根据客户配置发送到指定的邮箱地址
     */
    private void sendCriticalAlertNotification(Alert alert, String customerId) {
        // 获取客户通知配置
        Optional<CustomerNotificationConfig> configOpt = 
            customerNotificationConfigRepository.findByCustomerIdAndIsActive(customerId, true);
        
        if (configOpt.isEmpty()) {
            log.warn("客户 {} 没有激活的通知配置，使用默认邮箱", customerId);
            sendEmailNotification(alert, defaultEmailRecipient);
            return;
        }
        
        CustomerNotificationConfig config = configOpt.get();
        
        // 检查邮件通知是否启用
        if (!Boolean.TRUE.equals(config.getEmailEnabled())) {
            log.info("客户 {} 的邮件通知已禁用", customerId);
            return;
        }
        
        // 检查告警级别是否需要通知
        if (!shouldNotify(config, alert.getSeverity())) {
            log.info("告警级别 {} 不在客户 {} 的通知范围内", alert.getSeverity(), customerId);
            return;
        }
        
        // 解析邮件接收人列表
        List<String> recipients = parseEmailRecipients(config.getEmailRecipients());
        
        if (recipients.isEmpty()) {
            log.warn("客户 {} 没有配置邮件接收人", customerId);
            return;
        }
        
        // 发送给所有接收人
        for (String recipient : recipients) {
            sendEmailNotification(alert, recipient);
        }
        
        log.info("已为CRITICAL告警发送邮件通知: alertId={}, customerId={}, recipients={}", 
                 alert.getId(), customerId, recipients);
    }
    
    /**
     * 发送单个邮件通知
     */
    private void sendEmailNotification(Alert alert, String recipient) {
        Notification notification = new Notification();
        notification.setAlert(alert);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipient(recipient);
        notification.setSubject(String.format("【严重威胁】%s", alert.getTitle()));
        notification.setContent(String.format(
            "检测到严重威胁！\n\n" +
            "告警ID: %d\n" +
            "标题: %s\n" +
            "描述: %s\n" +
            "威胁分数: %.2f\n" +
            "来源: %s\n" +
            "时间: %s\n\n" +
            "请立即处理！",
            alert.getId(),
            alert.getTitle(),
            alert.getDescription(),
            alert.getThreatScore() != null ? alert.getThreatScore() : 0.0,
            alert.getSource(),
            alert.getCreatedAt()
        ));
        
        notificationService.sendNotification(notification);
    }
    
    /**
     * 检查是否应该发送通知
     */
    private boolean shouldNotify(CustomerNotificationConfig config, AlertSeverity severity) {
        String severitiesJson = config.getNotifyOnSeverities();
        if (severitiesJson == null || severitiesJson.isEmpty()) {
            return true; // 默认通知所有级别
        }
        
        try {
            List<String> severities = objectMapper.readValue(
                severitiesJson, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return severities.contains(severity.toString());
        } catch (Exception e) {
            log.error("解析通知级别配置失败: {}", severitiesJson, e);
            return true; // 解析失败时默认通知
        }
    }
    
    /**
     * 解析邮件接收人列表（JSON数组格式）
     */
    private List<String> parseEmailRecipients(String recipientsJson) {
        if (recipientsJson == null || recipientsJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(
                recipientsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception e) {
            log.error("解析邮件接收人列表失败: {}", recipientsJson, e);
            return new ArrayList<>();
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