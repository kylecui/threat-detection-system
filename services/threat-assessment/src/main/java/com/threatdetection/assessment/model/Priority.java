package com.threatdetection.assessment.model;

/**
 * Priority levels for mitigation recommendations
 */
public enum Priority {
    CRITICAL("Execute immediately"),
    HIGH("Execute within 1 hour"),
    MEDIUM("Execute within 24 hours"),
    LOW("Execute when convenient"),
    INFO("Monitor only");

    private final String description;

    Priority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}