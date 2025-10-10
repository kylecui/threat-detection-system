package com.threatdetection.assessment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Threat alert entity representing incoming threat alerts from stream processing
 */
@Entity
@Table(name = "threat_alerts")
public class ThreatAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "alert_id", unique = true)
    private String alertId;

    @NotBlank
    @Column(name = "attack_mac")
    private String attackMac;

    @DecimalMin(value = "0.0")
    @Column(name = "threat_score")
    private double threatScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "threat_level")
    private RiskLevel threatLevel;

    @Column(name = "threat_name")
    private String threatName;

    @NotNull
    @Column(name = "timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    @Column(name = "window_start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime windowEnd;

    @Column(name = "total_aggregations")
    private int totalAggregations;

    @ElementCollection
    @CollectionTable(name = "threat_alert_attack_patterns",
                     joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "attack_pattern")
    private List<String> attackPatterns;

    @ElementCollection
    @CollectionTable(name = "threat_alert_affected_assets",
                     joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "affected_asset")
    private List<String> affectedAssets;

    @Column(name = "processed")
    private boolean processed = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public ThreatAlert() {}

    public ThreatAlert(String alertId, String attackMac, double threatScore,
                      RiskLevel threatLevel, LocalDateTime timestamp) {
        this.alertId = alertId;
        this.attackMac = attackMac;
        this.threatScore = threatScore;
        this.threatLevel = threatLevel;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }

    public double getThreatScore() { return threatScore; }
    public void setThreatScore(double threatScore) { this.threatScore = threatScore; }

    public RiskLevel getThreatLevel() { return threatLevel; }
    public void setThreatLevel(RiskLevel threatLevel) { this.threatLevel = threatLevel; }

    public String getThreatName() { return threatName; }
    public void setThreatName(String threatName) { this.threatName = threatName; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    public int getTotalAggregations() { return totalAggregations; }
    public void setTotalAggregations(int totalAggregations) { this.totalAggregations = totalAggregations; }

    public List<String> getAttackPatterns() { return attackPatterns; }
    public void setAttackPatterns(List<String> attackPatterns) { this.attackPatterns = attackPatterns; }

    public List<String> getAffectedAssets() { return affectedAssets; }
    public void setAffectedAssets(List<String> affectedAssets) { this.affectedAssets = affectedAssets; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}