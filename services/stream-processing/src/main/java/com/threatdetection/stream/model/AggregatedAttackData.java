package com.threatdetection.stream.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 聚合攻击数据模型
 * 
 * <p>窗口聚合后的数据结构，包含威胁评分所需的所有维度
 */
public class AggregatedAttackData implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String customerId;
    private String attackMac;
    private String attackIp;  // V4.0 Phase 2: 被诱捕者IP
    private String mostAccessedHoneypotIp;  // V4.0 Phase 2: 访问最多的蜜罐IP
    private int attackCount;
    private int uniqueIps;
    private int uniquePorts;
    private int uniqueDevices;
    private double mixedPortWeight;
    private double netWeight;
    private int intelScore;        // 威胁情报置信度 (0-100)
    private double intelWeight;    // 威胁情报权重因子
    private int tier;
    private String windowType;
    private Instant windowStart;
    private Instant windowEnd;
    private Instant timestamp;
    
    // 威胁评分字段
    private double threatScore;
    private String threatLevel;
    
    // V4.0 Phase 3: 时间分布权重相关字段
    private long eventTimeSpan;           // 事件时间跨度（毫秒）
    private double burstIntensity;        // 爆发强度系数 [0, 1]
    private double timeDistributionWeight; // 时间分布权重 [1.0, 3.0]
    
    // V4.0 Phase 4: 端口列表 - 用于端口攻击分布统计
    private List<Integer> portList;       // 窗口内所有被攻击的端口列表
    
    // Constructors
    public AggregatedAttackData() {}
    
    public AggregatedAttackData(String customerId, String attackMac, String attackIp,
                               String mostAccessedHoneypotIp, int attackCount,
                               int uniqueIps, int uniquePorts, int uniqueDevices,
                               double mixedPortWeight, double netWeight,
                               int intelScore, double intelWeight,
                               int tier, String windowType,
                               Instant windowStart, Instant windowEnd, Instant timestamp,
                               double threatScore, String threatLevel,
                               long eventTimeSpan, double burstIntensity, double timeDistributionWeight,
                               List<Integer> portList) {
        this.customerId = customerId;
        this.attackMac = attackMac;
        this.attackIp = attackIp;
        this.mostAccessedHoneypotIp = mostAccessedHoneypotIp;
        this.attackCount = attackCount;
        this.uniqueIps = uniqueIps;
        this.uniquePorts = uniquePorts;
        this.uniqueDevices = uniqueDevices;
        this.mixedPortWeight = mixedPortWeight;
        this.netWeight = netWeight;
        this.intelScore = intelScore;
        this.intelWeight = intelWeight;
        this.tier = tier;
        this.windowType = windowType;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.timestamp = timestamp;
        this.threatScore = threatScore;
        this.threatLevel = threatLevel;
        this.eventTimeSpan = eventTimeSpan;
        this.burstIntensity = burstIntensity;
        this.timeDistributionWeight = timeDistributionWeight;
        this.portList = portList;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String customerId;
        private String attackMac;
        private String attackIp;
        private String mostAccessedHoneypotIp;
        private int attackCount;
        private int uniqueIps;
        private int uniquePorts;
        private int uniqueDevices;
        private double mixedPortWeight;
        private double netWeight;
        private int intelScore;
        private double intelWeight;
        private int tier;
        private String windowType;
        private Instant windowStart;
        private Instant windowEnd;
        private Instant timestamp;
        private double threatScore;
        private String threatLevel;
        private long eventTimeSpan;
        private double burstIntensity;
        private double timeDistributionWeight;
        private List<Integer> portList;
        
        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public Builder attackMac(String attackMac) {
            this.attackMac = attackMac;
            return this;
        }
        
        public Builder attackIp(String attackIp) {
            this.attackIp = attackIp;
            return this;
        }
        
        public Builder mostAccessedHoneypotIp(String mostAccessedHoneypotIp) {
            this.mostAccessedHoneypotIp = mostAccessedHoneypotIp;
            return this;
        }
        
        public Builder attackCount(int attackCount) {
            this.attackCount = attackCount;
            return this;
        }
        
        public Builder uniqueIps(int uniqueIps) {
            this.uniqueIps = uniqueIps;
            return this;
        }
        
        public Builder uniquePorts(int uniquePorts) {
            this.uniquePorts = uniquePorts;
            return this;
        }
        
        public Builder uniqueDevices(int uniqueDevices) {
            this.uniqueDevices = uniqueDevices;
            return this;
        }
        
        public Builder mixedPortWeight(double mixedPortWeight) {
            this.mixedPortWeight = mixedPortWeight;
            return this;
        }

        public Builder netWeight(double netWeight) {
            this.netWeight = netWeight;
            return this;
        }

        public Builder intelScore(int intelScore) {
            this.intelScore = intelScore;
            return this;
        }

        public Builder intelWeight(double intelWeight) {
            this.intelWeight = intelWeight;
            return this;
        }
        
        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }
        
        public Builder windowType(String windowType) {
            this.windowType = windowType;
            return this;
        }
        
        public Builder windowStart(Instant windowStart) {
            this.windowStart = windowStart;
            return this;
        }
        
        public Builder windowEnd(Instant windowEnd) {
            this.windowEnd = windowEnd;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder threatScore(double threatScore) {
            this.threatScore = threatScore;
            return this;
        }
        
        public Builder threatLevel(String threatLevel) {
            this.threatLevel = threatLevel;
            return this;
        }
        
        public Builder eventTimeSpan(long eventTimeSpan) {
            this.eventTimeSpan = eventTimeSpan;
            return this;
        }
        
        public Builder burstIntensity(double burstIntensity) {
            this.burstIntensity = burstIntensity;
            return this;
        }
        
        public Builder timeDistributionWeight(double timeDistributionWeight) {
            this.timeDistributionWeight = timeDistributionWeight;
            return this;
        }
        
        public Builder portList(List<Integer> portList) {
            this.portList = portList;
            return this;
        }
        
        public AggregatedAttackData build() {
            return new AggregatedAttackData(customerId, attackMac, attackIp,
                mostAccessedHoneypotIp, attackCount, uniqueIps, uniquePorts, 
                uniqueDevices, mixedPortWeight, netWeight, intelScore, intelWeight,
                tier, windowType, windowStart,
                windowEnd, timestamp, threatScore, threatLevel,
                eventTimeSpan, burstIntensity, timeDistributionWeight, portList);
        }
    }
    
    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
    
    public String getAttackIp() { return attackIp; }
    public void setAttackIp(String attackIp) { this.attackIp = attackIp; }
    
    public String getMostAccessedHoneypotIp() { return mostAccessedHoneypotIp; }
    public void setMostAccessedHoneypotIp(String mostAccessedHoneypotIp) { 
        this.mostAccessedHoneypotIp = mostAccessedHoneypotIp; 
    }
    
    public int getAttackCount() { return attackCount; }
    public void setAttackCount(int attackCount) { this.attackCount = attackCount; }
    
    public int getUniqueIps() { return uniqueIps; }
    public void setUniqueIps(int uniqueIps) { this.uniqueIps = uniqueIps; }
    
    public int getUniquePorts() { return uniquePorts; }
    public void setUniquePorts(int uniquePorts) { this.uniquePorts = uniquePorts; }
    
    public int getUniqueDevices() { return uniqueDevices; }
    public void setUniqueDevices(int uniqueDevices) { this.uniqueDevices = uniqueDevices; }
    
    public double getMixedPortWeight() { return mixedPortWeight; }
    public void setMixedPortWeight(double mixedPortWeight) { this.mixedPortWeight = mixedPortWeight; }

    public double getNetWeight() { return netWeight; }
    public void setNetWeight(double netWeight) { this.netWeight = netWeight; }

    public int getIntelScore() { return intelScore; }
    public void setIntelScore(int intelScore) { this.intelScore = intelScore; }

    public double getIntelWeight() { return intelWeight; }
    public void setIntelWeight(double intelWeight) { this.intelWeight = intelWeight; }
    
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    
    public String getWindowType() { return windowType; }
    public void setWindowType(String windowType) { this.windowType = windowType; }
    
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public double getThreatScore() { return threatScore; }
    public void setThreatScore(double threatScore) { this.threatScore = threatScore; }
    
    public String getThreatLevel() { return threatLevel; }
    public void setThreatLevel(String threatLevel) { this.threatLevel = threatLevel; }
    
    public long getEventTimeSpan() { return eventTimeSpan; }
    public void setEventTimeSpan(long eventTimeSpan) { this.eventTimeSpan = eventTimeSpan; }
    
    public double getBurstIntensity() { return burstIntensity; }
    public void setBurstIntensity(double burstIntensity) { this.burstIntensity = burstIntensity; }
    
    public double getTimeDistributionWeight() { return timeDistributionWeight; }
    public void setTimeDistributionWeight(double timeDistributionWeight) { 
        this.timeDistributionWeight = timeDistributionWeight; 
    }
    
    public List<Integer> getPortList() { return portList; }
    public void setPortList(List<Integer> portList) { this.portList = portList; }
}
