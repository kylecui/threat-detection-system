package com.threatdetection.customer.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 通知配置更新请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationConfigRequest {

    // ========== 邮件配置 ==========
    
    /**
     * 是否启用邮件通知
     */
    private Boolean emailEnabled;

    /**
     * 邮件接收人列表
     */
    private List<@Email(message = "Invalid email format") String> emailRecipients;

    // ========== 短信配置 ==========
    
    /**
     * 是否启用短信通知
     */
    private Boolean smsEnabled;

    /**
     * 短信接收人列表 (手机号)
     */
    private List<@Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number") String> smsRecipients;

    // ========== Slack配置 ==========
    
    /**
     * 是否启用Slack通知
     */
    private Boolean slackEnabled;

    /**
     * Slack Webhook URL
     */
    private String slackWebhookUrl;

    /**
     * Slack频道
     */
    private String slackChannel;

    // ========== Webhook配置 ==========
    
    /**
     * 是否启用Webhook通知
     */
    private Boolean webhookEnabled;

    /**
     * Webhook URL
     */
    private String webhookUrl;

    /**
     * Webhook请求头
     */
    private java.util.Map<String, String> webhookHeaders;

    // ========== 告警级别过滤 ==========
    
    /**
     * 最低告警级别
     */
    @Pattern(regexp = "INFO|LOW|MEDIUM|HIGH|CRITICAL", message = "Invalid severity level")
    private String minSeverityLevel;

    /**
     * 需要通知的告警级别列表
     */
    private List<@Pattern(regexp = "INFO|LOW|MEDIUM|HIGH|CRITICAL", message = "Invalid severity level") String> notifyOnSeverities;

    // ========== 通知频率控制 ==========
    
    /**
     * 每小时最大通知数量
     */
    @Min(value = 1, message = "Max notifications per hour must be at least 1")
    private Integer maxNotificationsPerHour;

    /**
     * 是否启用频率限制
     */
    private Boolean enableRateLimiting;

    // ========== 静默时段配置 ==========
    
    /**
     * 是否启用静默时段
     */
    private Boolean quietHoursEnabled;

    /**
     * 静默时段开始时间
     */
    private LocalTime quietHoursStart;

    /**
     * 静默时段结束时间
     */
    private LocalTime quietHoursEnd;

    /**
     * 静默时段时区
     */
    private String quietHoursTimezone;

    // ========== 其他 ==========
    
    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 描述信息
     */
    private String description;
}
