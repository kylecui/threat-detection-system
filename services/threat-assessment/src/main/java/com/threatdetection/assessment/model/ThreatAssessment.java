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
    @Column(name = "threat_score", nullable = false)
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

    @Column(name = "port_risk_score")
    private Double portRiskScore = 0.0;

    @Column(name = "detection_tier")
    private Integer detectionTier = 2;

    // 新增字段 - 权重因子 (用于审计和调试)
    @Column(name = "time_weight")
    private Double timeWeight;

    @Column(name = "ip_weight")
    private Double ipWeight;

    @Column(name = "port_weight")
    private Double portWeight;

    @Column(name = "device_weight")
    private Double deviceWeight;

    // 新增字段 - 攻击源IP
    @Size(max = 45)
    @Column(name = "attack_ip", length = 45)
    private String attackIp;

    // 新增字段 - 缓解建议 (JSON数组存储为TEXT)
    @Column(name = "mitigation_recommendations", columnDefinition = "TEXT")
    private String mitigationRecommendations;

    // 新增字段 - 缓解状态
    @Size(max = 50)
    @Column(name = "mitigation_status", length = 50)
    private String mitigationStatus;

    // 新增字段 - 更新时间
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant updatedAt;

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

    public Double getTimeWeight() { return timeWeight; }
    public void setTimeWeight(Double timeWeight) { this.timeWeight = timeWeight; }

    public Double getIpWeight() { return ipWeight; }
    public void setIpWeight(Double ipWeight) { this.ipWeight = ipWeight; }

    public Double getPortWeight() { return portWeight; }
    public void setPortWeight(Double portWeight) { this.portWeight = portWeight; }

    public Double getDeviceWeight() { return deviceWeight; }
    public void setDeviceWeight(Double deviceWeight) { this.deviceWeight = deviceWeight; }

    public String getAttackIp() { return attackIp; }
    public void setAttackIp(String attackIp) { this.attackIp = attackIp; }

    public String getMitigationRecommendations() { return mitigationRecommendations; }
    public void setMitigationRecommendations(String mitigationRecommendations) { 
        this.mitigationRecommendations = mitigationRecommendations; 
    }

    public String getMitigationStatus() { return mitigationStatus; }
    public void setMitigationStatus(String mitigationStatus) { this.mitigationStatus = mitigationStatus; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

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