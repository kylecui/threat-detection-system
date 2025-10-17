package com.threatdetection.customer.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.customer.exception.CustomerNotFoundException;
import com.threatdetection.customer.notification.dto.NotificationConfigRequest;
import com.threatdetection.customer.notification.dto.NotificationConfigResponse;
import com.threatdetection.customer.notification.model.NotificationConfig;
import com.threatdetection.customer.notification.repository.NotificationConfigRepository;
import com.threatdetection.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通知配置管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConfigService {

    private final NotificationConfigRepository notificationConfigRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取客户的通知配置
     */
    @Transactional(readOnly = true)
    public NotificationConfigResponse getConfig(String customerId) {
        log.debug("Getting notification config for customer: {}", customerId);

        // 检查客户是否存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        // 查找配置，如果不存在则返回默认配置
        NotificationConfig config = notificationConfigRepository.findByCustomerId(customerId)
                .orElseGet(() -> createDefaultConfig(customerId));

        return NotificationConfigResponse.fromEntity(config);
    }

    /**
     * 创建或更新通知配置
     */
    @Transactional
    public NotificationConfigResponse createOrUpdateConfig(String customerId, NotificationConfigRequest request) {
        log.info("Creating/Updating notification config for customer: {}", customerId);

        // 检查客户是否存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        // 查找现有配置或创建新配置
        NotificationConfig config = notificationConfigRepository.findByCustomerId(customerId)
                .orElse(NotificationConfig.builder()
                        .customerId(customerId)
                        .emailRecipients("[]")
                        .build());

        // 更新字段
        updateConfigFromRequest(config, request);

        // 保存
        NotificationConfig saved = notificationConfigRepository.save(config);

        log.info("Successfully saved notification config for customer: {}", customerId);
        return NotificationConfigResponse.fromEntity(saved);
    }

    /**
     * 删除通知配置（恢复为默认）
     */
    @Transactional
    public void deleteConfig(String customerId) {
        log.info("Deleting notification config for customer: {}", customerId);

        // 检查客户是否存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        notificationConfigRepository.deleteByCustomerId(customerId);
        log.info("Successfully deleted notification config for customer: {}", customerId);
    }

    /**
     * 测试通知配置
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testConfig(String customerId) {
        log.info("Testing notification config for customer: {}", customerId);

        NotificationConfig config = notificationConfigRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new RuntimeException("Notification config not found for customer: " + customerId));

        // 模拟测试结果
        return Map.of(
                "customerId", customerId,
                "emailEnabled", config.getEmailEnabled(),
                "emailRecipientsCount", parseJsonArray(config.getEmailRecipients()).size(),
                "smsEnabled", config.getSmsEnabled(),
                "slackEnabled", config.getSlackEnabled(),
                "webhookEnabled", config.getWebhookEnabled(),
                "isActive", config.getIsActive(),
                "testStatus", "SUCCESS",
                "message", "Notification configuration is valid"
        );
    }

    /**
     * 从请求更新配置实体
     */
    private void updateConfigFromRequest(NotificationConfig config, NotificationConfigRequest request) {
        // 邮件配置
        if (request.getEmailEnabled() != null) {
            config.setEmailEnabled(request.getEmailEnabled());
        }
        if (request.getEmailRecipients() != null) {
            config.setEmailRecipients(toJson(request.getEmailRecipients()));
        }

        // 短信配置
        if (request.getSmsEnabled() != null) {
            config.setSmsEnabled(request.getSmsEnabled());
        }
        if (request.getSmsRecipients() != null) {
            config.setSmsRecipients(toJson(request.getSmsRecipients()));
        }

        // Slack配置
        if (request.getSlackEnabled() != null) {
            config.setSlackEnabled(request.getSlackEnabled());
        }
        if (request.getSlackWebhookUrl() != null) {
            config.setSlackWebhookUrl(request.getSlackWebhookUrl());
        }
        if (request.getSlackChannel() != null) {
            config.setSlackChannel(request.getSlackChannel());
        }

        // Webhook配置
        if (request.getWebhookEnabled() != null) {
            config.setWebhookEnabled(request.getWebhookEnabled());
        }
        if (request.getWebhookUrl() != null) {
            config.setWebhookUrl(request.getWebhookUrl());
        }
        if (request.getWebhookHeaders() != null) {
            config.setWebhookHeaders(toJson(request.getWebhookHeaders()));
        }

        // 告警级别过滤
        if (request.getMinSeverityLevel() != null) {
            config.setMinSeverityLevel(request.getMinSeverityLevel());
        }
        if (request.getNotifyOnSeverities() != null) {
            config.setNotifyOnSeverities(toJson(request.getNotifyOnSeverities()));
        }

        // 通知频率控制
        if (request.getMaxNotificationsPerHour() != null) {
            config.setMaxNotificationsPerHour(request.getMaxNotificationsPerHour());
        }
        if (request.getEnableRateLimiting() != null) {
            config.setEnableRateLimiting(request.getEnableRateLimiting());
        }

        // 静默时段配置
        if (request.getQuietHoursEnabled() != null) {
            config.setQuietHoursEnabled(request.getQuietHoursEnabled());
        }
        if (request.getQuietHoursStart() != null) {
            config.setQuietHoursStart(request.getQuietHoursStart());
        }
        if (request.getQuietHoursEnd() != null) {
            config.setQuietHoursEnd(request.getQuietHoursEnd());
        }
        if (request.getQuietHoursTimezone() != null) {
            config.setQuietHoursTimezone(request.getQuietHoursTimezone());
        }

        // 其他
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
    }

    /**
     * 创建默认配置
     */
    private NotificationConfig createDefaultConfig(String customerId) {
        return NotificationConfig.builder()
                .customerId(customerId)
                .emailEnabled(true)
                .emailRecipients("[]")
                .smsEnabled(false)
                .slackEnabled(false)
                .webhookEnabled(false)
                .minSeverityLevel("MEDIUM")
                .notifyOnSeverities("[\"MEDIUM\",\"HIGH\",\"CRITICAL\"]")
                .maxNotificationsPerHour(100)
                .enableRateLimiting(true)
                .quietHoursEnabled(false)
                .quietHoursTimezone("Asia/Shanghai")
                .isActive(true)
                .build();
    }

    /**
     * 对象转JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert object to JSON: {}", obj, e);
            return "[]";
        }
    }

    /**
     * 解析JSON数组
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 启用/禁用邮件通知
     */
    @Transactional
    public NotificationConfigResponse toggleEmailEnabled(String customerId, boolean enabled) {
        log.info("Toggling email notification for customer: {}, enabled: {}", customerId, enabled);
        
        // 验证客户存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException("Customer not found: " + customerId);
        }

        NotificationConfig config = notificationConfigRepository.findByCustomerId(customerId)
                .orElseGet(() -> createDefaultConfig(customerId));

        config.setEmailEnabled(enabled);
        NotificationConfig savedConfig = notificationConfigRepository.save(config);
        
        return NotificationConfigResponse.fromEntity(savedConfig);
    }

    /**
     * 启用/禁用短信通知
     */
    @Transactional
    public NotificationConfigResponse toggleSmsEnabled(String customerId, boolean enabled) {
        log.info("Toggling SMS notification for customer: {}, enabled: {}", customerId, enabled);
        
        // 验证客户存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException("Customer not found: " + customerId);
        }

        NotificationConfig config = notificationConfigRepository.findByCustomerId(customerId)
                .orElseGet(() -> createDefaultConfig(customerId));

        config.setSmsEnabled(enabled);
        NotificationConfig savedConfig = notificationConfigRepository.save(config);
        
        return NotificationConfigResponse.fromEntity(savedConfig);
    }

    /**
     * 检查配置是否存在
     */
    @Transactional(readOnly = true)
    public boolean configExists(String customerId) {
        log.debug("Checking if notification config exists for customer: {}", customerId);
        return notificationConfigRepository.findByCustomerId(customerId).isPresent();
    }
}
