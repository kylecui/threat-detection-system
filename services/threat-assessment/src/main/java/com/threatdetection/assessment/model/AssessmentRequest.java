package com.threatdetection.assessment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for threat assessment evaluation requests
 */
public class AssessmentRequest {

    @NotBlank
    private String alertId;

    @NotBlank
    private String attackMac;

    @DecimalMin(value = "0.0")
    private double threatScore;

    @NotNull
    private RiskLevel threatLevel;

    private String threatName;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private List<String> attackPatterns;
    private List<String> affectedAssets;

    // Constructors
    public AssessmentRequest() {}

    public AssessmentRequest(String alertId, String attackMac, double threatScore,
                           RiskLevel threatLevel, LocalDateTime timestamp) {
        this.alertId = alertId;
        this.attackMac = attackMac;
        this.threatScore = threatScore;
        this.threatLevel = threatLevel;
        this.timestamp = timestamp;
    }

    // Getters and Setters
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

    public List<String> getAttackPatterns() { return attackPatterns; }
    public void setAttackPatterns(List<String> attackPatterns) { this.attackPatterns = attackPatterns; }

    public List<String> getAffectedAssets() { return affectedAssets; }
    public void setAffectedAssets(List<String> affectedAssets) { this.affectedAssets = affectedAssets; }
}