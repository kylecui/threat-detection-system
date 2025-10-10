package com.threatdetection.assessment.model;

/**
 * Mitigation action types for threat response
 */
public enum MitigationAction {
    BLOCK_IP("Block source IP address"),
    BLOCK_PORT("Block specific port"),
    BLOCK_MAC("Block source MAC address"),
    INCREASE_MONITORING("Increase monitoring for affected assets"),
    ISOLATE_ASSET("Isolate affected asset from network"),
    QUARANTINE("Quarantine suspicious traffic"),
    ALERT_SECURITY("Alert security team"),
    LOG_ANALYSIS("Perform detailed log analysis"),
    UPDATE_SIGNATURES("Update threat signatures"),
    RATE_LIMIT("Apply rate limiting"),
    MONITOR("Monitor the threat closely");

    private final String description;

    MitigationAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}