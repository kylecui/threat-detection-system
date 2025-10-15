package com.threatdetection.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.assessment.model.DeviceStatusHistory;
import com.threatdetection.assessment.repository.DeviceStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 设备健康告警消费者
 * 消费 device-health-alerts topic 并存储到数据库
 */
@Service
public class DeviceHealthAlertConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceHealthAlertConsumer.class);
    
    private final DeviceStatusHistoryRepository deviceStatusRepository;
    private final DeviceSerialToCustomerMappingService mappingService;
    private final ObjectMapper objectMapper;
    
    public DeviceHealthAlertConsumer(
            DeviceStatusHistoryRepository deviceStatusRepository,
            DeviceSerialToCustomerMappingService mappingService,
            ObjectMapper objectMapper) {
        this.deviceStatusRepository = deviceStatusRepository;
        this.mappingService = mappingService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 消费设备健康告警消息
     */
    @KafkaListener(
        topics = "device-health-alerts",
        groupId = "device-health-consumer-group",
        concurrency = "2"
    )
    @Transactional
    public void consumeDeviceHealthAlert(String message) {
        try {
            logger.debug("Received device health alert: {}", message);
            
            // 解析JSON消息
            JsonNode jsonNode = objectMapper.readTree(message);
            
            String devSerial = jsonNode.get("devSerial").asText();
            int sentryCount = jsonNode.get("sentryCount").asInt();
            int realHostCount = jsonNode.get("realHostCount").asInt();
            long devStartTime = jsonNode.get("devStartTime").asLong();
            long devEndTime = jsonNode.get("devEndTime").asLong();
            long reportTime = jsonNode.get("reportTime").asLong();
            boolean isExpired = jsonNode.get("isExpired").asBoolean();
            boolean isExpiringSoon = jsonNode.get("isExpiringSoon").asBoolean();
            boolean isHealthy = jsonNode.get("isHealthy").asBoolean();
            String rawLog = jsonNode.has("rawLog") ? jsonNode.get("rawLog").asText() : "";
            
            // 解析customer ID
            String customerId = mappingService.resolveCustomerId(devSerial);
            if (customerId == null) {
                logger.warn("Could not resolve customerId for devSerial: {}, using 'unknown'", devSerial);
                customerId = "unknown";
            }
            
            // 检测状态变化
            Optional<DeviceStatusHistory> lastStatus = 
                deviceStatusRepository.findTopByDevSerialOrderByReportTimeDesc(devSerial);
            
            boolean sentryCountChanged = false;
            boolean realHostCountChanged = false;
            
            if (lastStatus.isPresent()) {
                DeviceStatusHistory last = lastStatus.get();
                sentryCountChanged = !last.getSentryCount().equals(sentryCount);
                realHostCountChanged = !last.getRealHostCount().equals(realHostCount);
                
                if (sentryCountChanged) {
                    logger.info("Device {} sentry count changed: {} -> {}", 
                               devSerial, last.getSentryCount(), sentryCount);
                }
                if (realHostCountChanged) {
                    logger.info("Device {} real host count changed: {} -> {}", 
                               devSerial, last.getRealHostCount(), realHostCount);
                }
            }
            
            // 创建新记录
            DeviceStatusHistory status = new DeviceStatusHistory();
            status.setDevSerial(devSerial);
            status.setCustomerId(customerId);
            status.setSentryCount(sentryCount);
            status.setRealHostCount(realHostCount);
            status.setDevStartTime(devStartTime);
            status.setDevEndTime(devEndTime);
            status.setReportTime(Instant.ofEpochSecond(reportTime));
            status.setIsHealthy(isHealthy);
            status.setIsExpired(isExpired);
            status.setIsExpiringSoon(isExpiringSoon);
            status.setSentryCountChanged(sentryCountChanged);
            status.setRealHostCountChanged(realHostCountChanged);
            status.setRawLog(rawLog);
            
            // 保存到数据库
            deviceStatusRepository.save(status);
            
            logger.info("Saved device status: devSerial={}, customerId={}, healthy={}, expired={}, expiringSoon={}",
                       devSerial, customerId, isHealthy, isExpired, isExpiringSoon);
            
            // 如果有异常状态,记录警告
            if (isExpired) {
                logger.warn("ALERT: Device {} has EXPIRED (devEndTime={})", devSerial, devEndTime);
            } else if (isExpiringSoon) {
                long daysUntilExpiry = (devEndTime - reportTime) / 86400;
                logger.warn("ALERT: Device {} will expire in {} days", devSerial, daysUntilExpiry);
            }
            
            if (sentryCountChanged || realHostCountChanged) {
                logger.warn("ALERT: Device {} configuration changed - sentryCount: {}, realHostCount: {}",
                           devSerial, sentryCount, realHostCount);
            }
            
        } catch (Exception e) {
            logger.error("Error processing device health alert: {}", message, e);
            // 不抛出异常,避免消息重复消费
        }
    }
}
