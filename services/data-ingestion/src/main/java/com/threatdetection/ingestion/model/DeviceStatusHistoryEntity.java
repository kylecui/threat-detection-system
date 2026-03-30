package com.threatdetection.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * V1哨兵心跳状态历史实体
 * 
 * <p>对应 device_status_history 表，记录每次V1心跳(log_type=2)的设备状态
 */
@Entity
@Table(name = "device_status_history")
public class DeviceStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_serial", nullable = false, length = 64)
    private String devSerial;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "sentry_count", nullable = false)
    private int sentryCount;

    @Column(name = "real_host_count", nullable = false)
    private int realHostCount;

    @Column(name = "dev_start_time")
    private Long devStartTime;

    @Column(name = "dev_end_time")
    private Long devEndTime;

    @Column(name = "report_time", nullable = false)
    private Instant reportTime;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDevSerial() { return devSerial; }
    public void setDevSerial(String devSerial) { this.devSerial = devSerial; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public int getSentryCount() { return sentryCount; }
    public void setSentryCount(int sentryCount) { this.sentryCount = sentryCount; }

    public int getRealHostCount() { return realHostCount; }
    public void setRealHostCount(int realHostCount) { this.realHostCount = realHostCount; }

    public Long getDevStartTime() { return devStartTime; }
    public void setDevStartTime(Long devStartTime) { this.devStartTime = devStartTime; }

    public Long getDevEndTime() { return devEndTime; }
    public void setDevEndTime(Long devEndTime) { this.devEndTime = devEndTime; }

    public Instant getReportTime() { return reportTime; }
    public void setReportTime(Instant reportTime) { this.reportTime = reportTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
