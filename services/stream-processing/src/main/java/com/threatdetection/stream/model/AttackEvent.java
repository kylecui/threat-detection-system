package com.threatdetection.stream.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttackEvent {
    private String id;

    @JsonAlias("deviceSerial")
    private String devSerial;

    @JsonAlias("log_type")
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

    @JsonAlias("company_obj_id")
    private String customerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp;

    // Constructors
    public AttackEvent() {}

    public AttackEvent(String devSerial, int logType, int subType, String attackMac,
                      String attackIp, String responseIp, int responsePort, int lineId,
                      int ifaceType, int vlanId, long logTime, int ethType, int ipType,
                      String rawLog, String customerId) {
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
        this.customerId = customerId;
        // 将Unix时间戳(秒)转换为Instant
        this.timestamp = logTime > 0 ? Instant.ofEpochSecond(logTime) : Instant.now();
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
        // 蜜罐机制：responseIp是诱饵IP(虚拟哨兵)，responsePort反映攻击意图
        // attackIp是被诱捕的内网失陷主机，尝试访问不存在的诱饵服务
        return String.format("蜜罐检测: 内网主机%s尝试访问诱饵%s:%d (攻击意图:%s)", 
                           attackIp, responseIp, responsePort, getAttackIntentByPort(responsePort));
    }
    
    /**
     * 根据端口推断攻击意图
     */
    private String getAttackIntentByPort(int port) {
        switch (port) {
            case 22: return "SSH远程控制";
            case 23: return "Telnet远程控制";
            case 445: case 139: return "SMB横向移动";
            case 3389: return "RDP远程桌面";
            case 3306: return "MySQL数据窃取";
            case 1433: return "SQLServer数据窃取";
            case 135: return "RPC服务探测";
            case 80: case 443: return "Web服务探测";
            case 21: return "FTP文件传输";
            case 25: return "SMTP邮件服务";
            default: return port > 1000 ? "应用服务探测" : "系统服务探测";
        }
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

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    /**
     * 获取本地时间（便捷方法）
     * @return LocalDateTime 使用系统默认时区
     */
    public LocalDateTime getLocalDateTime() {
        return timestamp != null ? LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()) : null;
    }

    /**
     * 获取本地时间（指定时区）
     * @param zoneId 时区
     * @return LocalDateTime
     */
    public LocalDateTime getLocalDateTime(ZoneId zoneId) {
        return timestamp != null ? LocalDateTime.ofInstant(timestamp, zoneId) : null;
    }
}