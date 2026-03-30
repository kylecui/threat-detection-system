package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * V2 Audit Event (type="audit")
 *
 * Administrative action log — records configuration changes, guard table
 * modifications, API operations, etc. on the sentinel device.
 */
public class AuditEvent {

    private String deviceId;
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    private String action;
    private String actor;
    private String target;
    private String result;
    /** Serialized JSON object of action-specific details */
    private String detailsJson;
    private String rawPayload;

    public AuditEvent() {}

    public AuditEvent(String deviceId, String customerId, LocalDateTime timestamp,
                      String action, String actor, String target,
                      String result, String detailsJson, String rawPayload) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.timestamp = timestamp;
        this.action = action;
        this.actor = actor;
        this.target = target;
        this.result = result;
        this.detailsJson = detailsJson;
        this.rawPayload = rawPayload;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
