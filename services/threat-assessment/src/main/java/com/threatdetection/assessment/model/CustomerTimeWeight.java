package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * 客户时间段权重配置实体 (多租户)
 *
 * <p>支持每个客户自定义时间段权重配置，实现多租户隔离
 * <p>对应数据库表: customer_time_weights
 *
 * <p>设计理念:
 * <ul>
 *   <li>优先级: 客户自定义 > 全局默认</li>
 *   <li>混合策略: timeWeight = max(configWeight, defaultWeight)</li>
 *   <li>支持启用/禁用控制</li>
 *   <li>支持优先级排序</li>
 * </ul>
 *
 * @author Security Team
 * @version 5.0
 */
@Entity
@Table(name = "customer_time_weights", indexes = {
    @Index(name = "idx_customer_time_weights_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_time_weights_range", columnList = "start_hour, end_hour"),
    @Index(name = "idx_customer_time_weights_enabled", columnList = "enabled"),
    @Index(name = "idx_customer_time_weights_composite",
           columnList = "customer_id, enabled, priority")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_customer_time_range", columnNames = {"customer_id", "start_hour", "end_hour"})
})
public class CustomerTimeWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 客户ID (租户标识)
     */
    @NotBlank
    @Size(max = 50)
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    /**
     * 时间段开始小时 (0-23)
     */
    @NotNull
    @Min(0)
    @Max(23)
    @Column(name = "start_hour", nullable = false)
    private Integer startHour;

    /**
     * 时间段结束小时 (0-23)
     */
    @NotNull
    @Min(0)
    @Max(23)
    @Column(name = "end_hour", nullable = false)
    private Integer endHour;

    /**
     * 时间段名称
     */
    @Size(max = 100)
    @Column(name = "time_range_name", length = 100)
    private String timeRangeName;

    /**
     * 时间权重 (0.5-2.0)
     * <p>0.5: 低风险时段, 1.0: 正常时段, 1.5: 中等风险时段, 2.0: 高风险时段
     */
    @NotNull
    @DecimalMin("0.5")
    @DecimalMax("2.0")
    @Column(name = "weight", nullable = false)
    private Double weight;

    /**
     * 风险等级描述
     */
    @Size(max = 50)
    @Column(name = "risk_description", length = 50)
    private String riskDescription;

    /**
     * 攻击意图描述
     */
    @Size(max = 200)
    @Column(name = "attack_intent", length = 200)
    private String attackIntent;

    /**
     * 详细描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 优先级 (0-100, 数字越大优先级越高)
     */
    @Min(0)
    @Max(100)
    @Column(name = "priority")
    private Integer priority = 0;

    /**
     * 是否启用
     */
    @Column(name = "enabled")
    private Boolean enabled = true;

    /**
     * 创建人
     */
    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 更新人
     */
    @Size(max = 100)
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public CustomerTimeWeight() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public CustomerTimeWeight(String customerId, Integer startHour, Integer endHour,
                            String timeRangeName, Double weight, String riskDescription) {
        this();
        this.customerId = customerId;
        this.startHour = startHour;
        this.endHour = endHour;
        this.timeRangeName = timeRangeName;
        this.weight = weight;
        this.riskDescription = riskDescription;
    }

    // Lifecycle callbacks
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Integer getStartHour() {
        return startHour;
    }

    public void setStartHour(Integer startHour) {
        this.startHour = startHour;
    }

    public Integer getEndHour() {
        return endHour;
    }

    public void setEndHour(Integer endHour) {
        this.endHour = endHour;
    }

    public String getTimeRangeName() {
        return timeRangeName;
    }

    public void setTimeRangeName(String timeRangeName) {
        this.timeRangeName = timeRangeName;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getRiskDescription() {
        return riskDescription;
    }

    public void setRiskDescription(String riskDescription) {
        this.riskDescription = riskDescription;
    }

    public String getAttackIntent() {
        return attackIntent;
    }

    public void setAttackIntent(String attackIntent) {
        this.attackIntent = attackIntent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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

    /**
     * 检查给定的小时是否在此时间段内
     */
    public boolean containsHour(int hour) {
        if (startHour <= endHour) {
            // 正常时间段，如 9:00-17:00
            return hour >= startHour && hour < endHour;
        } else {
            // 跨天时间段，如 22:00-06:00
            return hour >= startHour || hour < endHour;
        }
    }

    @Override
    public String toString() {
        return "CustomerTimeWeight{" +
                "id=" + id +
                ", customerId='" + customerId + '\'' +
                ", startHour=" + startHour +
                ", endHour=" + endHour +
                ", timeRangeName='" + timeRangeName + '\'' +
                ", weight=" + weight +
                ", riskDescription='" + riskDescription + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
}