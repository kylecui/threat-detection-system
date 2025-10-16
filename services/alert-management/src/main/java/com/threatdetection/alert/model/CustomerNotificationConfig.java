package com.threatdetection.alert.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;

/**
 * 客户通知配置实体
 * 为每个客户配置独立的通知渠道和规则
 */
@Entity
@Table(name = "customer_notification_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerNotificationConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 客户ID (唯一标识)
     */
    @Column(name = "customer_id", nullable = false, unique = true, length = 100)
    private String customerId;
    
    // ==================== 邮件配置 ====================
    
    /**
     * 是否启用邮件通知
     */
    @Column(name = "email_enabled")
    @Builder.Default
    private Boolean emailEnabled = true;
    
    /**
     * 邮件接收人列表 (JSON数组格式)
     * 例如: ["email1@example.com", "email2@example.com"]
     */
    @Column(name = "email_recipients", columnDefinition = "TEXT", nullable = false)
    private String emailRecipients;
    
    // ==================== 短信配置 ====================
    
    /**
     * 是否启用短信通知
     */
    @Column(name = "sms_enabled")
    @Builder.Default
    private Boolean smsEnabled = false;
    
    /**
     * 短信接收人列表 (JSON数组格式)
     */
    @Column(name = "sms_recipients", columnDefinition = "TEXT")
    private String smsRecipients;
    
    // ==================== Slack配置 ====================
    
    /**
     * 是否启用Slack通知
     */
    @Column(name = "slack_enabled")
    @Builder.Default
    private Boolean slackEnabled = false;
    
    /**
     * Slack Webhook URL
     */
    @Column(name = "slack_webhook_url", length = 500)
    private String slackWebhookUrl;
    
    /**
     * Slack频道名称
     */
    @Column(name = "slack_channel", length = 100)
    private String slackChannel;
    
    // ==================== Webhook配置 ====================
    
    /**
     * 是否启用Webhook通知
     */
    @Column(name = "webhook_enabled")
    @Builder.Default
    private Boolean webhookEnabled = false;
    
    /**
     * Webhook URL
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    
    /**
     * Webhook请求头 (JSON对象格式)
     */
    @Column(name = "webhook_headers", columnDefinition = "TEXT")
    private String webhookHeaders;
    
    // ==================== 告警级别过滤 ====================
    
    /**
     * 最低通知告警级别
     */
    @Column(name = "min_severity_level", length = 20)
    @Builder.Default
    private String minSeverityLevel = "MEDIUM";
    
    /**
     * 触发通知的告警级别列表 (JSON数组格式)
     * 例如: ["MEDIUM","HIGH","CRITICAL"]
     */
    @Column(name = "notify_on_severities", columnDefinition = "TEXT")
    @Builder.Default
    private String notifyOnSeverities = "[\"MEDIUM\",\"HIGH\",\"CRITICAL\"]";
    
    // ==================== 频率控制 ====================
    
    /**
     * 每小时最大通知数量
     */
    @Column(name = "max_notifications_per_hour")
    @Builder.Default
    private Integer maxNotificationsPerHour = 100;
    
    /**
     * 是否启用频率限制
     */
    @Column(name = "enable_rate_limiting")
    @Builder.Default
    private Boolean enableRateLimiting = true;
    
    // ==================== 静默时段 ====================
    
    /**
     * 是否启用静默时段
     */
    @Column(name = "quiet_hours_enabled")
    @Builder.Default
    private Boolean quietHoursEnabled = false;
    
    /**
     * 静默时段开始时间
     */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;
    
    /**
     * 静默时段结束时间
     */
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
    
    /**
     * 静默时段时区
     */
    @Column(name = "quiet_hours_timezone", length = 50)
    @Builder.Default
    private String quietHoursTimezone = "Asia/Shanghai";
    
    // ==================== 状态 ====================
    
    /**
     * 是否激活
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * 配置描述
     */
    @Column(name = "description", length = 500)
    private String description;
    
    // ==================== 元数据 ====================
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * 创建人
     */
    @Column(name = "created_by", length = 100)
    @Builder.Default
    private String createdBy = "system";
    
    /**
     * 更新人
     */
    @Column(name = "updated_by", length = 100)
    @Builder.Default
    private String updatedBy = "system";
    
    /**
     * 更新时间戳
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
