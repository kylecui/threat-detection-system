package com.threatdetection.assessment.model;

/**
 * Risk level enumeration for threat assessments
 */
public enum RiskLevel {
    CRITICAL("Critical threat requiring immediate action"),
    HIGH("High-risk threat"),
    MEDIUM("Medium-risk threat"),
    LOW("Low-risk threat"),
    INFO("Informational level");

    private final String description;

    RiskLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}