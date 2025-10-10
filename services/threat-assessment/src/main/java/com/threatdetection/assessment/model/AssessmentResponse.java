package com.threatdetection.assessment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for threat assessment evaluation responses
 */
public class AssessmentResponse {

    private String assessmentId;
    private RiskLevel riskLevel;
    private double riskScore;
    private double confidence;
    private List<Recommendation> recommendations;
    private ThreatIntelligence threatIntelligence;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime assessmentTimestamp;

    private long processingDurationMs;

    // Constructors
    public AssessmentResponse() {}

    public AssessmentResponse(String assessmentId, RiskLevel riskLevel, double riskScore,
                             double confidence, List<Recommendation> recommendations,
                             ThreatIntelligence threatIntelligence, LocalDateTime assessmentTimestamp) {
        this.assessmentId = assessmentId;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.confidence = confidence;
        this.recommendations = recommendations;
        this.threatIntelligence = threatIntelligence;
        this.assessmentTimestamp = assessmentTimestamp;
    }

    // Getters and Setters
    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

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