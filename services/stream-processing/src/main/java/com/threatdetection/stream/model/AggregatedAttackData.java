package com.threatdetection.stream.model;

import java.io.Serializable;
import java.time.Instant;

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
    private int tier;
    private String windowType;
    private Instant windowStart;
    private Instant windowEnd;
    private Instant timestamp;
    
    // 威胁评分字段
    private double threatScore;
    private String threatLevel;
    
    // Constructors
    public AggregatedAttackData() {}
    
    public AggregatedAttackData(String customerId, String attackMac, String attackIp,
                               String mostAccessedHoneypotIp, int attackCount,
                               int uniqueIps, int uniquePorts, int uniqueDevices,
                               double mixedPortWeight, int tier, String windowType,
                               Instant windowStart, Instant windowEnd, Instant timestamp,
                               double threatScore, String threatLevel) {
        this.customerId = customerId;
        this.attackMac = attackMac;
        this.attackIp = attackIp;
        this.mostAccessedHoneypotIp = mostAccessedHoneypotIp;
        this.attackCount = attackCount;
        this.uniqueIps = uniqueIps;
        this.uniquePorts = uniquePorts;
        this.uniqueDevices = uniqueDevices;
        this.mixedPortWeight = mixedPortWeight;
        this.tier = tier;
        this.windowType = windowType;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.timestamp = timestamp;
        this.threatScore = threatScore;
        this.threatLevel = threatLevel;
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
        private int tier;
        private String windowType;
        private Instant windowStart;
        private Instant windowEnd;
        private Instant timestamp;
        private double threatScore;
        private String threatLevel;
        
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
        
        public AggregatedAttackData build() {
            return new AggregatedAttackData(customerId, attackMac, attackIp,
                mostAccessedHoneypotIp, attackCount, uniqueIps, uniquePorts, 
                uniqueDevices, mixedPortWeight, tier, windowType, windowStart, 
                windowEnd, timestamp, threatScore, threatLevel);
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
}

