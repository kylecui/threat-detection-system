package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * V2 Threat Detection Event (type="threat")
 *
 * Emitted by the BPF threat_detect module when a packet matches
 * a known threat pattern (header/payload signatures).
 */
public class ThreatDetectionEvent {

    private String deviceId;
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    private int patternId;
    private int threatLevel;
    private String actionTaken;
    private String description;
    private String srcIp;
    private String dstIp;
    private int dstPort;
    private String protocol;
    private String interfaceName;
    private int ifindex;
    private int vlanId;
    private String rawPayload;

    public ThreatDetectionEvent() {}

    public ThreatDetectionEvent(String deviceId, String customerId, LocalDateTime timestamp,
                                int patternId, int threatLevel, String actionTaken,
                                String description, String srcIp, String dstIp,
                                int dstPort, String protocol, String interfaceName,
                                int ifindex, int vlanId, String rawPayload) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.timestamp = timestamp;
        this.patternId = patternId;
        this.threatLevel = threatLevel;
        this.actionTaken = actionTaken;
        this.description = description;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.dstPort = dstPort;
        this.protocol = protocol;
        this.interfaceName = interfaceName;
        this.ifindex = ifindex;
        this.vlanId = vlanId;
        this.rawPayload = rawPayload;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getPatternId() { return patternId; }
    public void setPatternId(int patternId) { this.patternId = patternId; }

    public int getThreatLevel() { return threatLevel; }
    public void setThreatLevel(int threatLevel) { this.threatLevel = threatLevel; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSrcIp() { return srcIp; }
    public void setSrcIp(String srcIp) { this.srcIp = srcIp; }

    public String getDstIp() { return dstIp; }
    public void setDstIp(String dstIp) { this.dstIp = dstIp; }

    public int getDstPort() { return dstPort; }
    public void setDstPort(int dstPort) { this.dstPort = dstPort; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public int getIfindex() { return ifindex; }
    public void setIfindex(int ifindex) { this.ifindex = ifindex; }

    public int getVlanId() { return vlanId; }
    public void setVlanId(int vlanId) { this.vlanId = vlanId; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
