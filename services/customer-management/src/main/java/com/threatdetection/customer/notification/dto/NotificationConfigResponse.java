package com.threatdetection.customer.notification.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.customer.notification.model.NotificationConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通知配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class NotificationConfigResponse {

    private Long id;
    
    private String customerId;

    // ========== 邮件配置 ==========
    
    private Boolean emailEnabled;

    private List<String> emailRecipients;

    // ========== 短信配置 ==========
    
    private Boolean smsEnabled;

    private List<String> smsRecipients;

    // ========== Slack配置 ==========
    
    private Boolean slackEnabled;

    private String slackWebhookUrl;

    private String slackChannel;

    // ========== Webhook配置 ==========
    
    private Boolean webhookEnabled;

    private String webhookUrl;

    private Map<String, String> webhookHeaders;

    // ========== 告警级别过滤 ==========
    
    private String minSeverityLevel;

    private List<String> notifyOnSeverities;

    // ========== 通知频率控制 ==========
    
    private Integer maxNotificationsPerHour;

    private Boolean enableRateLimiting;

    // ========== 静默时段配置 ==========
    
    private Boolean quietHoursEnabled;

    private LocalTime quietHoursStart;

    private LocalTime quietHoursEnd;

    private String quietHoursTimezone;

    // ========== 元数据 ==========
    
    private Boolean isActive;

    private String description;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 从实体转换为DTO
     */
    public static NotificationConfigResponse fromEntity(NotificationConfig config) {
        ObjectMapper mapper = new ObjectMapper();

        return NotificationConfigResponse.builder()
                .id(config.getId())
                .customerId(config.getCustomerId())
                // 邮件配置
                .emailEnabled(config.getEmailEnabled())
                .emailRecipients(parseJsonArray(mapper, config.getEmailRecipients()))
                // 短信配置
                .smsEnabled(config.getSmsEnabled())
                .smsRecipients(parseJsonArray(mapper, config.getSmsRecipients()))
                // Slack配置
                .slackEnabled(config.getSlackEnabled())
                .slackWebhookUrl(config.getSlackWebhookUrl())
                .slackChannel(config.getSlackChannel())
                // Webhook配置
                .webhookEnabled(config.getWebhookEnabled())
                .webhookUrl(config.getWebhookUrl())
                .webhookHeaders(parseJsonMap(mapper, config.getWebhookHeaders()))
                // 告警级别过滤
                .minSeverityLevel(config.getMinSeverityLevel())
                .notifyOnSeverities(parseJsonArray(mapper, config.getNotifyOnSeverities()))
                // 通知频率控制
                .maxNotificationsPerHour(config.getMaxNotificationsPerHour())
                .enableRateLimiting(config.getEnableRateLimiting())
                // 静默时段配置
                .quietHoursEnabled(config.getQuietHoursEnabled())
                .quietHoursStart(config.getQuietHoursStart())
                .quietHoursEnd(config.getQuietHoursEnd())
                .quietHoursTimezone(config.getQuietHoursTimezone())
                // 元数据
                .isActive(config.getIsActive())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    /**
     * 解析JSON数组字符串
     */
    private static List<String> parseJsonArray(ObjectMapper mapper, String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析JSON对象字符串
     */
    private static Map<String, String> parseJsonMap(ObjectMapper mapper, String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON map: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
