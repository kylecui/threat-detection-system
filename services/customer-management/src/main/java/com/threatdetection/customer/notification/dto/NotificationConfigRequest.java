package com.threatdetection.customer.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("email_enabled")
    private Boolean emailEnabled;

    /**
     * 邮件接收人列表
     */
    @JsonProperty("email_recipients")
    private List<@Email(message = "Invalid email format") String> emailRecipients;

    // ========== 短信配置 ==========
    
    /**
     * 是否启用短信通知
     */
    @JsonProperty("sms_enabled")
    private Boolean smsEnabled;

    /**
     * 短信接收人列表 (手机号)
     */
    @JsonProperty("sms_recipients")
    private List<@Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number") String> smsRecipients;

    // ========== Slack配置 ==========
    
    /**
     * 是否启用Slack通知
     */
    @JsonProperty("slack_enabled")
    private Boolean slackEnabled;

    /**
     * Slack Webhook URL
     */
    @JsonProperty("slack_webhook_url")
    private String slackWebhookUrl;

    /**
     * Slack频道
     */
    @JsonProperty("slack_channel")
    private String slackChannel;

    // ========== Webhook配置 ==========
    
    /**
     * 是否启用Webhook通知
     */
    @JsonProperty("webhook_enabled")
    private Boolean webhookEnabled;

    /**
     * Webhook URL
     */
    @JsonProperty("webhook_url")
    private String webhookUrl;

    /**
     * Webhook请求头
     */
    @JsonProperty("webhook_headers")
    private java.util.Map<String, String> webhookHeaders;

    // ========== 告警级别过滤 ==========
    
    /**
     * 最低告警级别
     */
    @JsonProperty("min_severity_level")
    @Pattern(regexp = "INFO|LOW|MEDIUM|HIGH|CRITICAL", message = "Invalid severity level")
    private String minSeverityLevel;

    /**
     * 需要通知的告警级别列表
     */
    @JsonProperty("notify_on_severities")
    private List<@Pattern(regexp = "INFO|LOW|MEDIUM|HIGH|CRITICAL", message = "Invalid severity level") String> notifyOnSeverities;

    // ========== 通知频率控制 ==========
    
    /**
     * 每小时最大通知数量
     */
    @JsonProperty("max_notifications_per_hour")
    @Min(value = 1, message = "Max notifications per hour must be at least 1")
    private Integer maxNotificationsPerHour;

    /**
     * 是否启用频率限制
     */
    @JsonProperty("enable_rate_limiting")
    private Boolean enableRateLimiting;

    // ========== 静默时段配置 ==========
    
    /**
     * 是否启用静默时段
     */
    @JsonProperty("quiet_hours_enabled")
    private Boolean quietHoursEnabled;

    /**
     * 静默时段开始时间
     */
    @JsonProperty("quiet_hours_start")
    private LocalTime quietHoursStart;

    /**
     * 静默时段结束时间
     */
    @JsonProperty("quiet_hours_end")
    private LocalTime quietHoursEnd;

    /**
     * 静默时段时区
     */
    @JsonProperty("quiet_hours_timezone")
    private String quietHoursTimezone;

    // ========== 其他 ==========
    
    /**
     * 是否激活
     */
    @JsonProperty("is_active")
    private Boolean isActive;

    /**
     * 描述信息
     */
    private String description;
}
