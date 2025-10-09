package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class AttackEvent {
    private String id;
    private String devSerial;
    private int logType;
    private int subType;
    private String attackMac;
    private String attackIp;
    private String responseIp;
    private int responsePort;
    private int lineId;
    private int ifaceType;
    private int vlanId;
    private long logTime;
    private int ethType;
    private int ipType;
    private String severity;
    private String description;
    private String rawLog;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    // Constructors
    public AttackEvent() {}
    
    public AttackEvent(String devSerial, int logType, int subType, String attackMac, 
                      String attackIp, String responseIp, int responsePort, int lineId,
                      int ifaceType, int vlanId, long logTime, int ethType, int ipType, 
                      String rawLog) {
        this.devSerial = devSerial;
        this.logType = logType;
        this.subType = subType;
        this.attackMac = attackMac;
        this.attackIp = attackIp;
        this.responseIp = responseIp;
        this.responsePort = responsePort;
        this.lineId = lineId;
        this.ifaceType = ifaceType;
        this.vlanId = vlanId;
        this.logTime = logTime;
        this.ethType = ethType;
        this.ipType = ipType;
        this.rawLog = rawLog;
        this.timestamp = LocalDateTime.now();
        this.severity = determineSeverity(subType);
        this.description = generateDescription();
        this.id = devSerial + "_" + logTime + "_" + lineId;
    }

    private String determineSeverity(int subType) {
        // 根据攻击子类型确定严重程度
        switch (subType) {
            case 1: return "HIGH";    // 嗅探攻击
            case 2: return "MEDIUM";  // 可疑行为
            default: return "LOW";
        }
    }

    private String generateDescription() {
        return String.format("Detected potential sniffing attack from %s to %s:%d", 
                           attackIp, responseIp, responsePort);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getDevSerial() { return devSerial; }
    public void setDevSerial(String devSerial) { this.devSerial = devSerial; }
    
    public int getLogType() { return logType; }
    public void setLogType(int logType) { this.logType = logType; }
    
    public int getSubType() { return subType; }
    public void setSubType(int subType) { this.subType = subType; }
    
    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
    
    public String getAttackIp() { return attackIp; }
    public void setAttackIp(String attackIp) { this.attackIp = attackIp; }
    
    public String getResponseIp() { return responseIp; }
    public void setResponseIp(String responseIp) { this.responseIp = responseIp; }
    
    public int getResponsePort() { return responsePort; }
    public void setResponsePort(int responsePort) { this.responsePort = responsePort; }
    
    public int getLineId() { return lineId; }
    public void setLineId(int lineId) { this.lineId = lineId; }
    
    public int getIfaceType() { return ifaceType; }
    public void setIfaceType(int ifaceType) { this.ifaceType = ifaceType; }
    
    public int getVlanId() { return vlanId; }
    public void setVlanId(int vlanId) { this.vlanId = vlanId; }
    
    public long getLogTime() { return logTime; }
    public void setLogTime(long logTime) { this.logTime = logTime; }
    
    public int getEthType() { return ethType; }
    public void setEthType(int ethType) { this.ethType = ethType; }
    
    public int getIpType() { return ipType; }
    public void setIpType(int ipType) { this.ipType = ipType; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getRawLog() { return rawLog; }
    public void setRawLog(String rawLog) { this.rawLog = rawLog; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}