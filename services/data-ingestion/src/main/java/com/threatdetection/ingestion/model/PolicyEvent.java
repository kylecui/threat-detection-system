package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * V2 Policy Match Event (type="policy")
 *
 * Emitted when a traffic flow matches a flow policy rule in the
 * traffic_weaver BPF module, triggering a redirect or mirror action.
 */
public class PolicyEvent {

    private String deviceId;
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    private int policyId;
    private String action;
    private String srcIp;
    private String dstIp;
    private int srcPort;
    private int dstPort;
    private String protocol;
    private String redirectTo;
    private String mirrorTo;
    private String trigger;
    private String reason;
    private String rawPayload;

    public PolicyEvent() {}

    public PolicyEvent(String deviceId, String customerId, LocalDateTime timestamp,
                       int policyId, String action, String srcIp, String dstIp,
                       int srcPort, int dstPort, String protocol,
                       String redirectTo, String mirrorTo,
                       String trigger, String reason, String rawPayload) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.timestamp = timestamp;
        this.policyId = policyId;
        this.action = action;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.protocol = protocol;
        this.redirectTo = redirectTo;
        this.mirrorTo = mirrorTo;
        this.trigger = trigger;
        this.reason = reason;
        this.rawPayload = rawPayload;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getPolicyId() { return policyId; }
    public void setPolicyId(int policyId) { this.policyId = policyId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSrcIp() { return srcIp; }
    public void setSrcIp(String srcIp) { this.srcIp = srcIp; }

    public String getDstIp() { return dstIp; }
    public void setDstIp(String dstIp) { this.dstIp = dstIp; }

    public int getSrcPort() { return srcPort; }
    public void setSrcPort(int srcPort) { this.srcPort = srcPort; }

    public int getDstPort() { return dstPort; }
    public void setDstPort(int dstPort) { this.dstPort = dstPort; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getRedirectTo() { return redirectTo; }
    public void setRedirectTo(String redirectTo) { this.redirectTo = redirectTo; }

    public String getMirrorTo() { return mirrorTo; }
    public void setMirrorTo(String mirrorTo) { this.mirrorTo = mirrorTo; }

    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
