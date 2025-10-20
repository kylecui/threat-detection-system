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
@Table(name = "port_risk_config", indexes = {
    @Index(name = "idx_port_number", columnList = "port_number", unique = true),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_risk_score", columnList = "risk_score")
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
    @DecimalMin("0.0")
    @DecimalMax("5.0")
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @NotBlank
    @Size(max = 50)
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public PortRiskConfig() {}

    public PortRiskConfig(Integer portNumber, String portName, Double riskScore, 
                         String category, String description) {
        this.portNumber = portNumber;
        this.portName = portName;
        this.riskScore = riskScore;
        this.category = category;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPortNumber() { return portNumber; }
    public void setPortNumber(Integer portNumber) { this.portNumber = portNumber; }

    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

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
                ", riskScore=" + riskScore +
                ", category='" + category + '\'' +
                '}';
    }
}
