package com.threatdetection.customer.device.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 设备-客户映射实体
 * 对应数据库表: device_customer_mapping
 */
@Entity
@Table(name = "device_customer_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 设备序列号 (唯一)
     */
    @Column(name = "dev_serial", nullable = false, unique = true, length = 50)
    private String devSerial;

    /**
     * 客户ID
     */
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    /**
     * 是否激活
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 描述信息
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 创建时自动设置时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    /**
     * 更新时自动设置时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
