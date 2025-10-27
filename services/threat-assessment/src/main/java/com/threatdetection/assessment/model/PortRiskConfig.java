package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * 端口风险配置实体
 * 
 * <p>存储219个常见端口的风险评分配置
 * 对应数据库表: port_risk_config
 * 
 * @author Security Team
 * @version 2.0
 */
@Entity
@Table(name = "port_risk_configs", indexes = {
    @Index(name = "idx_port_risk_configs_port", columnList = "port_number"),
    @Index(name = "idx_port_risk_configs_risk_level", columnList = "risk_level"),
    @Index(name = "idx_port_risk_configs_enabled", columnList = "enabled")
})
public class PortRiskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Min(1)
    @Max(65535)
    @Column(name = "port_number", nullable = false, unique = true)
    private Integer portNumber;

    @NotBlank
    @Size(max = 100)
    @Column(name = "port_name", nullable = false, length = 100)
    private String portName;

    @NotNull
    @DecimalMin("1.0")
    @DecimalMax("10.0")
    @Column(name = "risk_weight", nullable = false)
    private Double riskWeight;

    @NotBlank
    @Size(max = 20)
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Size(max = 200)
    @Column(name = "attack_intent", length = 200)
    private String attackIntent;

    @Size(max = 20)
    @Column(name = "config_source", length = 20)
    private String configSource = "LEGACY";

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    // Constructors
    public PortRiskConfig() {}

    public PortRiskConfig(Integer portNumber, String portName, Double riskWeight, 
                         String riskLevel, String description) {
        this.portNumber = portNumber;
        this.portName = portName;
        this.riskWeight = riskWeight;
        this.riskLevel = riskLevel;
        this.description = description;
        this.enabled = true;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPortNumber() { return portNumber; }
    public void setPortNumber(Integer portNumber) { this.portNumber = portNumber; }

    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }

    public Double getRiskWeight() { return riskWeight; }
    public void setRiskWeight(Double riskWeight) { this.riskWeight = riskWeight; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getAttackIntent() { return attackIntent; }
    public void setAttackIntent(String attackIntent) { this.attackIntent = attackIntent; }

    public String getConfigSource() { return configSource; }
    public void setConfigSource(String configSource) { this.configSource = configSource; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "PortRiskConfig{" +
                "id=" + id +
                ", portNumber=" + portNumber +
                ", portName='" + portName + '\'' +
                ", riskWeight=" + riskWeight +
                ", riskLevel='" + riskLevel + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
