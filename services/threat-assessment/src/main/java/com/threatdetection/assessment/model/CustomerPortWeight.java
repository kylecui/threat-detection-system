package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * 客户端口权重配置实体 (多租户)
 * 
 * <p>支持每个客户自定义端口权重配置，实现多租户隔离
 * <p>对应数据库表: customer_port_weights
 * 
 * <p>设计理念:
 * <ul>
 *   <li>优先级: 客户自定义 > 全局默认 (port_risk_configs)</li>
 *   <li>混合策略: portWeight = max(configWeight, diversityWeight)</li>
 *   <li>支持启用/禁用控制</li>
 *   <li>支持优先级排序</li>
 * </ul>
 * 
 * @author Security Team
 * @version 4.0
 */
@Entity
@Table(name = "customer_port_weights", indexes = {
    @Index(name = "idx_customer_port_weights_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_port_weights_port", columnList = "port_number"),
    @Index(name = "idx_customer_port_weights_enabled", columnList = "enabled"),
    @Index(name = "idx_customer_port_weights_composite", 
           columnList = "customer_id, enabled, priority"),
    @Index(name = "idx_customer_port_weights_risk", columnList = "risk_level")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_customer_port", columnNames = {"customer_id", "port_number"})
})
public class CustomerPortWeight {

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
     * 端口号 (1-65535)
     */
    @NotNull
    @Min(1)
    @Max(65535)
    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    /**
     * 端口名称
     */
    @Size(max = 100)
    @Column(name = "port_name", length = 100)
    private String portName;

    /**
     * 端口权重 (0.5-10.0)
     * <p>0.5: 低风险, 1.0: 正常, 5.0: 高风险, 10.0: 极高风险
     */
    @NotNull
    @DecimalMin("0.5")
    @DecimalMax("10.0")
    @Column(name = "weight", nullable = false)
    private Double weight;

    /**
     * 风险等级
     */
    @Size(max = 20)
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

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
    public CustomerPortWeight() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public CustomerPortWeight(String customerId, Integer portNumber, String portName, 
                            Double weight, String riskLevel) {
        this();
        this.customerId = customerId;
        this.portNumber = portNumber;
        this.portName = portName;
        this.weight = weight;
        this.riskLevel = riskLevel;
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

    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
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

    @Override
    public String toString() {
        return "CustomerPortWeight{" +
                "id=" + id +
                ", customerId='" + customerId + '\'' +
                ", portNumber=" + portNumber +
                ", portName='" + portName + '\'' +
                ", weight=" + weight +
                ", riskLevel='" + riskLevel + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
}
