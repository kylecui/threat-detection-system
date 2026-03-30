package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * V2 Sniffer Detection Event (type="sniffer")
 *
 * Emitted when a network device is detected responding to ARP probes
 * for non-existent IPs, indicating promiscuous mode (potential sniffer).
 */
public class SnifferEvent {

    private String deviceId;
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    private String suspectMac;
    private String suspectIp;
    private String probeIp;
    private String interfaceName;
    private int ifindex;
    private int responseCount;
    private String firstSeen;
    private String lastSeen;
    private String rawPayload;

    public SnifferEvent() {}

    public SnifferEvent(String deviceId, String customerId, LocalDateTime timestamp,
                        String suspectMac, String suspectIp, String probeIp,
                        String interfaceName, int ifindex, int responseCount,
                        String firstSeen, String lastSeen, String rawPayload) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.timestamp = timestamp;
        this.suspectMac = suspectMac;
        this.suspectIp = suspectIp;
        this.probeIp = probeIp;
        this.interfaceName = interfaceName;
        this.ifindex = ifindex;
        this.responseCount = responseCount;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.rawPayload = rawPayload;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getSuspectMac() { return suspectMac; }
    public void setSuspectMac(String suspectMac) { this.suspectMac = suspectMac; }

    public String getSuspectIp() { return suspectIp; }
    public void setSuspectIp(String suspectIp) { this.suspectIp = suspectIp; }

    public String getProbeIp() { return probeIp; }
    public void setProbeIp(String probeIp) { this.probeIp = probeIp; }

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public int getIfindex() { return ifindex; }
    public void setIfindex(int ifindex) { this.ifindex = ifindex; }

    public int getResponseCount() { return responseCount; }
    public void setResponseCount(int responseCount) { this.responseCount = responseCount; }

    public String getFirstSeen() { return firstSeen; }
    public void setFirstSeen(String firstSeen) { this.firstSeen = firstSeen; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
