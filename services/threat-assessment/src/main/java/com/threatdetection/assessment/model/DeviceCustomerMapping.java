package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for device to customer mapping with temporal support.
 * This entity represents the relationship between device serial numbers and customer IDs
 * with time-based validity windows to support device circulation between customers.
 */
@Entity
@Table(name = "device_customer_mapping")
public class DeviceCustomerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_serial", nullable = false, length = 50)
    private String devSerial;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "bind_time", nullable = false)
    private Instant bindTime;

    @Column(name = "unbind_time")
    private Instant unbindTime;

    @Column(name = "bind_reason", length = 100)
    private String bindReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;

    // Constructors
    public DeviceCustomerMapping() {}

    public DeviceCustomerMapping(String devSerial, String customerId) {
        this.devSerial = devSerial;
        this.customerId = customerId;
        this.bindTime = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.isActive = true;
    }

    public DeviceCustomerMapping(String devSerial, String customerId, String description) {
        this(devSerial, customerId);
        this.description = description;
    }

    public DeviceCustomerMapping(String devSerial, String customerId, Instant bindTime, String bindReason) {
        this.devSerial = devSerial;
        this.customerId = customerId;
        this.bindTime = bindTime;
        this.bindReason = bindReason;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.isActive = true;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // 新增的时效性相关方法
    public Instant getBindTime() {
        return bindTime;
    }

    public void setBindTime(Instant bindTime) {
        this.bindTime = bindTime;
    }

    public Instant getUnbindTime() {
        return unbindTime;
    }

    public void setUnbindTime(Instant unbindTime) {
        this.unbindTime = unbindTime;
    }

    public String getBindReason() {
        return bindReason;
    }

    public void setBindReason(String bindReason) {
        this.bindReason = bindReason;
    }

    /**
     * 检查映射在指定时间点是否有效
     */
    public boolean isActiveAt(Instant timestamp) {
        return bindTime != null && bindTime.isBefore(timestamp) &&
               (unbindTime == null || unbindTime.isAfter(timestamp));
    }

    /**
     * 检查映射当前是否有效
     */
    public boolean isCurrentlyActive() {
        return unbindTime == null;
    }
}
