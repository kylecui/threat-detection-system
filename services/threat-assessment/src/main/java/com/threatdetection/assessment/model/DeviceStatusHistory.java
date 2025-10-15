package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 设备状态历史记录实体
 * 存储设备心跳数据 (log_type=2)
 */
@Entity
@Table(name = "device_status_history", indexes = {
    @Index(name = "idx_device_status_dev_serial", columnList = "dev_serial"),
    @Index(name = "idx_device_status_customer_id", columnList = "customer_id"),
    @Index(name = "idx_device_status_report_time", columnList = "report_time"),
    @Index(name = "idx_device_status_dev_serial_time", columnList = "dev_serial,report_time")
})
public class DeviceStatusHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "dev_serial", nullable = false, length = 50)
    private String devSerial;
    
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    
    // 心跳核心数据
    @Column(name = "sentry_count", nullable = false)
    private Integer sentryCount;
    
    @Column(name = "real_host_count", nullable = false)
    private Integer realHostCount;
    
    @Column(name = "dev_start_time", nullable = false)
    private Long devStartTime;
    
    @Column(name = "dev_end_time", nullable = false)
    private Long devEndTime;  // -1 表示长期有效
    
    // 时间戳
    @Column(name = "report_time", nullable = false)
    private Instant reportTime;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 状态分析字段
    @Column(name = "is_healthy")
    private Boolean isHealthy = true;
    
    @Column(name = "is_expiring_soon")
    private Boolean isExpiringSoon = false;
    
    @Column(name = "is_expired")
    private Boolean isExpired = false;
    
    // 变化检测字段
    @Column(name = "sentry_count_changed")
    private Boolean sentryCountChanged = false;
    
    @Column(name = "real_host_count_changed")
    private Boolean realHostCountChanged = false;
    
    // 原始日志
    @Column(name = "raw_log", columnDefinition = "TEXT")
    private String rawLog;
    
    // Constructors
    public DeviceStatusHistory() {
        this.createdAt = LocalDateTime.now();
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDevSerial() {
        return devSerial;
    }
    
    public void setDevSerial(String devSerial) {
        this.devSerial = devSerial;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public Integer getSentryCount() {
        return sentryCount;
    }
    
    public void setSentryCount(Integer sentryCount) {
        this.sentryCount = sentryCount;
    }
    
    public Integer getRealHostCount() {
        return realHostCount;
    }
    
    public void setRealHostCount(Integer realHostCount) {
        this.realHostCount = realHostCount;
    }
    
    public Long getDevStartTime() {
        return devStartTime;
    }
    
    public void setDevStartTime(Long devStartTime) {
        this.devStartTime = devStartTime;
    }
    
    public Long getDevEndTime() {
        return devEndTime;
    }
    
    public void setDevEndTime(Long devEndTime) {
        this.devEndTime = devEndTime;
    }
    
    public Instant getReportTime() {
        return reportTime;
    }
    
    public void setReportTime(Instant reportTime) {
        this.reportTime = reportTime;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Boolean getIsHealthy() {
        return isHealthy;
    }
    
    public void setIsHealthy(Boolean isHealthy) {
        this.isHealthy = isHealthy;
    }
    
    public Boolean getIsExpiringSoon() {
        return isExpiringSoon;
    }
    
    public void setIsExpiringSoon(Boolean isExpiringSoon) {
        this.isExpiringSoon = isExpiringSoon;
    }
    
    public Boolean getIsExpired() {
        return isExpired;
    }
    
    public void setIsExpired(Boolean isExpired) {
        this.isExpired = isExpired;
    }
    
    public Boolean getSentryCountChanged() {
        return sentryCountChanged;
    }
    
    public void setSentryCountChanged(Boolean sentryCountChanged) {
        this.sentryCountChanged = sentryCountChanged;
    }
    
    public Boolean getRealHostCountChanged() {
        return realHostCountChanged;
    }
    
    public void setRealHostCountChanged(Boolean realHostCountChanged) {
        this.realHostCountChanged = realHostCountChanged;
    }
    
    public String getRawLog() {
        return rawLog;
    }
    
    public void setRawLog(String rawLog) {
        this.rawLog = rawLog;
    }
    
    @Override
    public String toString() {
        return String.format(
            "DeviceStatusHistory[id=%d, devSerial=%s, customerId=%s, sentryCount=%d, realHostCount=%d, " +
            "isHealthy=%b, isExpired=%b, isExpiringSoon=%b, reportTime=%s]",
            id, devSerial, customerId, sentryCount, realHostCount, 
            isHealthy, isExpired, isExpiringSoon, reportTime
        );
    }
}
