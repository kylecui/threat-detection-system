package com.threatdetection.ingestion.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 威胁告警持久化实体
 * 
 * <p>存储所有3层窗口产生的威胁评分和告警
 */
@Entity
@Table(name = "threat_alerts", indexes = {
    @Index(name = "idx_threat_alerts_customer", columnList = "customer_id"),
    @Index(name = "idx_threat_alerts_mac", columnList = "attack_mac"),
    @Index(name = "idx_threat_alerts_level", columnList = "threat_level"),
    @Index(name = "idx_threat_alerts_tier", columnList = "tier"),
    @Index(name = "idx_threat_alerts_timestamp", columnList = "alert_timestamp")
})
public class ThreatAlertEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    
    @Column(name = "attack_mac", nullable = false, length = 17)
    private String attackMac;
    
    @Column(name = "threat_score", nullable = false, precision = 12, scale = 2)
    private BigDecimal threatScore;
    
    @Column(name = "threat_level", nullable = false, length = 20)
    private String threatLevel;
    
    @Column(name = "attack_count", nullable = false)
    private Integer attackCount;
    
    @Column(name = "unique_ips", nullable = false)
    private Integer uniqueIps;
    
    @Column(name = "unique_ports", nullable = false)
    private Integer uniquePorts;
    
    @Column(name = "unique_devices", nullable = false)
    private Integer uniqueDevices;
    
    @Column(name = "mixed_port_weight", precision = 10, scale = 2)
    private BigDecimal mixedPortWeight;
    
    @Column(name = "tier", nullable = false)
    private Integer tier;
    
    @Column(name = "window_type", length = 50)
    private String windowType;
    
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    
    @Column(name = "alert_timestamp", nullable = false)
    private Instant alertTimestamp;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "status", length = 20)
    private String status = "NEW";
    
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "raw_alert_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawAlertData;  // JSON string - will be properly converted to JSONB
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "NEW";
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
    
    public BigDecimal getThreatScore() { return threatScore; }
    public void setThreatScore(BigDecimal threatScore) { this.threatScore = threatScore; }
    
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
    
    public BigDecimal getMixedPortWeight() { return mixedPortWeight; }
    public void setMixedPortWeight(BigDecimal mixedPortWeight) { this.mixedPortWeight = mixedPortWeight; }
    
    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }
    
    public String getWindowType() { return windowType; }
    public void setWindowType(String windowType) { this.windowType = windowType; }
    
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    
    public Instant getAlertTimestamp() { return alertTimestamp; }
    public void setAlertTimestamp(Instant alertTimestamp) { this.alertTimestamp = alertTimestamp; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getRawAlertData() { return rawAlertData; }
    public void setRawAlertData(String rawAlertData) { this.rawAlertData = rawAlertData; }
}
