package com.threatdetection.assessment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * Threat Assessment Entity
 * 
 * 基于蜜罐机制的威胁评估实体
 * 对应数据库表: threat_assessments
 */
@Entity
@Table(name = "threat_assessments", indexes = {
    @Index(name = "idx_threat_assessments_customer", columnList = "customer_id"),
    @Index(name = "idx_threat_assessments_attack_mac", columnList = "attack_mac"),
    @Index(name = "idx_threat_assessments_customer_mac", columnList = "customer_id, attack_mac"),
    @Index(name = "idx_threat_assessments_assessment_time", columnList = "assessment_time"),
    @Index(name = "idx_threat_assessments_threat_level", columnList = "threat_level")
})
public class ThreatAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @NotBlank
    @Size(max = 17)
    @Column(name = "attack_mac", nullable = false, length = 17)
    private String attackMac;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "threat_score", nullable = false, precision = 12, scale = 2)
    private Double threatScore;

    @NotBlank
    @Size(max = 20)
    @Column(name = "threat_level", nullable = false, length = 20)
    private String threatLevel;

    @Min(0)
    @Column(name = "attack_count", nullable = false)
    private Integer attackCount = 0;

    @Min(0)
    @Column(name = "unique_ips", nullable = false)
    private Integer uniqueIps = 0;

    @Min(0)
    @Column(name = "unique_ports", nullable = false)
    private Integer uniquePorts = 0;

    @Min(0)
    @Column(name = "unique_devices", nullable = false)
    private Integer uniqueDevices = 0;

    @NotNull
    @Column(name = "assessment_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant assessmentTime;

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant createdAt;

    @Column(name = "port_list", columnDefinition = "TEXT")
    private String portList;

    @DecimalMin(value = "0.0")
    @Column(name = "port_risk_score", precision = 10, scale = 2)
    private Double portRiskScore = 0.0;

    @Column(name = "detection_tier")
    private Integer detectionTier = 2;

    // Constructors
    public ThreatAssessment() {}

    public ThreatAssessment(String customerId, String attackMac, Double threatScore,
                           String threatLevel, Integer attackCount, Integer uniqueIps,
                           Integer uniquePorts, Integer uniqueDevices) {
        this.customerId = customerId;
        this.attackMac = attackMac;
        this.threatScore = threatScore;
        this.threatLevel = threatLevel;
        this.attackCount = attackCount;
        this.uniqueIps = uniqueIps;
        this.uniquePorts = uniquePorts;
        this.uniqueDevices = uniqueDevices;
        this.assessmentTime = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }

    public Double getThreatScore() { return threatScore; }
    public void setThreatScore(Double threatScore) { this.threatScore = threatScore; }

    public String getThreatLevel() { return threatLevel; }
    public void setThreatLevel(String threatLevel) { this.threatLevel = threatLevel; }

    public Integer getAttackCount() { return attackCount; }
    public void setAttackCount(Integer attackCount) { this.attackCount = attackCount; }

    public Integer getUniqueIps() { return uniqueIps; }
    public void setUniqueIps(Integer uniqueIps) { this.uniqueIps = uniqueIps; }

    public Integer getUniquePorts() { return uniquePorts; }
    public void setUniquePorts(Integer uniquePorts) { this.uniquePorts = uniquePorts; }

    public Integer getUniqueDevices() { return uniqueDevices; }
    public void setUniqueDevices(Integer uniqueDevices) { this.uniqueDevices = uniqueDevices; }

    public Instant getAssessmentTime() { return assessmentTime; }
    public void setAssessmentTime(Instant assessmentTime) { this.assessmentTime = assessmentTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getPortList() { return portList; }
    public void setPortList(String portList) { this.portList = portList; }

    public Double getPortRiskScore() { return portRiskScore; }
    public void setPortRiskScore(Double portRiskScore) { this.portRiskScore = portRiskScore; }

    public Integer getDetectionTier() { return detectionTier; }
    public void setDetectionTier(Integer detectionTier) { this.detectionTier = detectionTier; }

    @Override
    public String toString() {
        return "ThreatAssessment{" +
                "id=" + id +
                ", customerId='" + customerId + '\'' +
                ", attackMac='" + attackMac + '\'' +
                ", threatScore=" + threatScore +
                ", threatLevel='" + threatLevel + '\'' +
                ", attackCount=" + attackCount +
                ", uniqueIps=" + uniqueIps +
                ", uniquePorts=" + uniquePorts +
                ", uniqueDevices=" + uniqueDevices +
                ", assessmentTime=" + assessmentTime +
                '}';
    }
}