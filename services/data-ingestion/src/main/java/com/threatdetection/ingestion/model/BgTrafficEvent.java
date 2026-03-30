package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * V2 Background Traffic Event (type="bg")
 *
 * Periodic summary of broadcast/multicast protocol traffic captured by the
 * bg_collector BPF module. Used for network baseline building and anomaly detection.
 */
public class BgTrafficEvent {

    private String deviceId;
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    private String periodStart;
    private String periodEnd;
    /** Serialized JSON object of per-protocol statistics */
    private String protocolsJson;
    private String rawPayload;

    public BgTrafficEvent() {}

    public BgTrafficEvent(String deviceId, String customerId, LocalDateTime timestamp,
                          String periodStart, String periodEnd,
                          String protocolsJson, String rawPayload) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.timestamp = timestamp;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.protocolsJson = protocolsJson;
        this.rawPayload = rawPayload;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getPeriodStart() { return periodStart; }
    public void setPeriodStart(String periodStart) { this.periodStart = periodStart; }

    public String getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(String periodEnd) { this.periodEnd = periodEnd; }

    public String getProtocolsJson() { return protocolsJson; }
    public void setProtocolsJson(String protocolsJson) { this.protocolsJson = protocolsJson; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
