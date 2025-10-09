package com.threatdetection.ingestion.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for device to customer mapping.
 * This entity represents the relationship between device serial numbers and customer IDs.
 */
@Entity
@Table(name = "device_customer_mapping")
public class DeviceCustomerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_serial", nullable = false, unique = true, length = 50)
    private String devSerial;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;

    // Constructors
    public DeviceCustomerMapping() {}

    public DeviceCustomerMapping(String devSerial, String customerId) {
        this.devSerial = devSerial;
        this.customerId = customerId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isActive = true;
    }

    public DeviceCustomerMapping(String devSerial, String customerId, String description) {
        this(devSerial, customerId);
        this.description = description;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
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

    @Override
    public String toString() {
        return "DeviceCustomerMapping{" +
                "id=" + id +
                ", devSerial='" + devSerial + '\'' +
                ", customerId='" + customerId + '\'' +
                ", isActive=" + isActive +
                ", description='" + description + '\'' +
                '}';
    }
}