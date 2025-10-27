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
     * 监听威胁检测事件（批量模式，提升性能）
     * 
     * <p>批量处理优势:
     * - 减少数据库往返次数（批量插入）
     * - 减少Kafka offset提交次数
     * - 提升整体吞吐量 3-5倍
     */
    @KafkaListener(
            topics = "${app.kafka.topics.threat-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaManualAckListenerContainerFactory"
    )
    public void consumeThreatEvents(
            @Payload List<Map<String, Object>> events,
            @Header(KafkaHeaders.RECEIVED_TOPIC) List<String> topics,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {

        int batchSize = events.size();
        log.info("接收到 {} 个威胁检测事件批量，分区: {}, 偏移量范围: {}-{}",
                batchSize, partitions.get(0), offsets.get(0), offsets.get(batchSize - 1));

        int successCount = 0;
        int failureCount = 0;
        
        try {
            // 批量处理所有事件
            for (int i = 0; i < events.size(); i++) {
                Map<String, Object> event = events.get(i);
                try {
                    processThreatEvent(event);
                    successCount++;
                    
                    if (log.isDebugEnabled()) {
                        log.debug("成功处理事件 {}/{}: MAC={}, Tier={}", 
                                i + 1, batchSize, event.get("attackMac"), event.get("tier"));
                    }
                } catch (Exception e) {
                    failureCount++;
                    log.error("处理事件 {}/{} 时发生错误: MAC={}, 错误: {}", 
                            i + 1, batchSize, event.get("attackMac"), e.getMessage(), e);
                    // 继续处理下一条，不中断整个批次
                }
            }

            // 批量提交偏移量（所有消息处理完成后统一提交）
            acknowledgment.acknowledge();
            log.info("批量处理完成: 成功={}, 失败={}, 总数={}", successCount, failureCount, batchSize);

        } catch (Exception e) {
            log.error("批量处理威胁检测事件时发生严重错误: 批量大小={}", batchSize, e);
            // 不提交偏移量，让Kafka重新投递整个批次
            throw e;
        }
    }

    /**
     * 处理单个威胁检测事件
     */
    private void processThreatEvent(Map<String, Object> event) {
        try {
            // 从 threat-alerts 主题接收的标准格式: ThreatAlertMessage
            // 包含字段: customerId, attackMac, threatScore, threatLevel, attackCount, uniqueIps, uniquePorts, uniqueDevices, timestamp
            
            String customerId = (String) event.get("customerId");
            String attackMac = (String) event.get("attackMac");
            String attackIp = (String) event.get("attackIp");
            String threatLevel = (String) event.get("threatLevel");
            Integer attackCount = getIntValue(event, "attackCount");
            Integer uniqueIps = getIntValue(event, "uniqueIps");
            Integer uniquePorts = getIntValue(event, "uniquePorts");
            Integer uniqueDevices = getIntValue(event, "uniqueDevices");
            
            // V4.0: 提取3层时间窗口信息
            Integer tier = getIntValue(event, "tier");
            String windowType = (String) event.get("windowType");
            
            // 提取威胁分数
            Double threatScore = getDoubleValue(event, "threatScore");
            
            // 生成时间窗口描述
            String windowDescription = formatWindowDescription(tier, windowType);
            
            // 生成告警信息
            String eventType = "THREAT_DETECTION";
            String source = "stream-processing-service";
            String message = String.format(
                "检测到威胁行为: 攻击源 %s (%s) 在%s发起 %d 次攻击，" +
                "涉及 %d 个诱饵IP、%d 个端口、%d 个检测设备。威胁等级: %s",
                attackIp != null ? attackIp : "未知",
                attackMac,
                windowDescription,
                attackCount != null ? attackCount : 0,
                uniqueIps != null ? uniqueIps : 0,
                uniquePorts != null ? uniquePorts : 0,
                uniqueDevices != null ? uniqueDevices : 0,
                threatLevel
            );

            log.debug("处理威胁事件: 类型={}, 来源={}, 客户ID={}, 攻击MAC={}, 威胁等级={}, 分数={}",
                    eventType, source, customerId, attackMac, threatLevel, threatScore);

            // 根据威胁等级映射告警严重程度
            AlertSeverity alertSeverity = mapThreatLevelToSeverity(threatLevel);

            // 生成告警标题
            String alertTitle = String.format("[%s] %s 威胁检测 - %s",
                source,
                threatLevel != null ? threatLevel : "UNKNOWN",
                attackMac
            );

            // 创建告警对象
            Alert alert = Alert.builder()
                    .title(alertTitle)
                    .description(message)
                    .severity(alertSeverity)
                    .status(AlertStatus.NEW)
                    .source(source)
                    .eventType(eventType)
                    .attackMac(attackMac)
                    .threatScore(threatScore)
                    .metadata(objectMapper.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

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
     * 将威胁等级映射为告警严重程度
     * 根据威胁评分系统的等级分类: INFO, LOW, MEDIUM, HIGH, CRITICAL
     */
    private AlertSeverity mapThreatLevelToSeverity(String threatLevel) {
        if (threatLevel == null) {
            return AlertSeverity.MEDIUM;
        }
        
        switch (threatLevel.toUpperCase()) {
            case "CRITICAL":
                return AlertSeverity.CRITICAL;
            case "HIGH":
                return AlertSeverity.HIGH;
            case "MEDIUM":
                return AlertSeverity.MEDIUM;
            case "LOW":
                return AlertSeverity.LOW;
            case "INFO":
                return AlertSeverity.INFO;
            default:
                log.warn("未知的威胁等级: {}, 使用默认值 MEDIUM", threatLevel);
                return AlertSeverity.MEDIUM;
        }
    }
    
    /**
     * 格式化时间窗口描述
     * 
     * @param tier 窗口层级 (1=30秒, 2=5分钟, 3=15分钟)
     * @param windowType 窗口类型 (RANSOMWARE_DETECTION, MAIN_THREAT_DETECTION, APT_SLOW_SCAN)
     * @return 格式化的窗口描述，如 "30秒窗口(勒索软件检测)"
     */
    private String formatWindowDescription(Integer tier, String windowType) {
        if (tier == null) {
            return "时间窗口内";
        }
        
        String windowSize;
        String detectionType;
        
        // 根据tier确定窗口大小
        switch (tier) {
            case 1:
                windowSize = "30秒窗口";
                detectionType = "勒索软件检测";
                break;
            case 2:
                windowSize = "5分钟窗口";
                detectionType = "主要威胁检测";
                break;
            case 3:
                windowSize = "15分钟窗口";
                detectionType = "APT慢速扫描检测";
                break;
            default:
                windowSize = "未知窗口";
                detectionType = "威胁检测";
        }
        
        // 组合窗口描述
        return String.format("%s(%s)", windowSize, detectionType);
    }
    
    /**
     * 安全地从Map中获取Integer值
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Integer: key={}, value={}", key, value);
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * 安全地从Map中获取Double值
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Double: key={}, value={}", key, value);
                return 0.0;
            }
        }
        return 0.0;
    }
}