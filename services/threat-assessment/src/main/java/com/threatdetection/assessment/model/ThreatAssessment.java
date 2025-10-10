package com.threatdetection.assessment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Threat assessment entity representing the complete risk evaluation result
 */
@Entity
@Table(name = "threat_assessments")
public class ThreatAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "assessment_id", unique = true)
    private String assessmentId;

    @NotBlank
    @Column(name = "alert_id")
    private String alertId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "10000.0")
    @Column(name = "risk_score")
    private double riskScore;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    @Column(name = "confidence")
    private double confidence;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", referencedColumnName = "assessment_id")
    private List<Recommendation> recommendations;

    @Embedded
    private ThreatIntelligence threatIntelligence;

    @NotNull
    @Column(name = "assessment_timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime assessmentTimestamp;

    @Column(name = "processing_duration_ms")
    private long processingDurationMs;

    // Constructors
    public ThreatAssessment() {}

    public ThreatAssessment(String assessmentId, String alertId, RiskLevel riskLevel,
                           double riskScore, double confidence) {
        this.assessmentId = assessmentId;
        this.alertId = alertId;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.confidence = confidence;
        this.assessmentTimestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<Recommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<Recommendation> recommendations) { this.recommendations = recommendations; }

    public ThreatIntelligence getThreatIntelligence() { return threatIntelligence; }
    public void setThreatIntelligence(ThreatIntelligence threatIntelligence) { this.threatIntelligence = threatIntelligence; }

    public LocalDateTime getAssessmentTimestamp() { return assessmentTimestamp; }
    public void setAssessmentTimestamp(LocalDateTime assessmentTimestamp) { this.assessmentTimestamp = assessmentTimestamp; }

    public long getProcessingDurationMs() { return processingDurationMs; }
    public void setProcessingDurationMs(long processingDurationMs) { this.processingDurationMs = processingDurationMs; }
}