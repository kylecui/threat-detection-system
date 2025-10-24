package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka威胁告警消息 - 从threat-alerts主题接收
 * 
 * <p>消息来源: Flink流处理引擎完成威胁评分后发送
 * 
 * @author Security Team
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAlertMessage {
    
    /**
     * 客户ID
     */
    @JsonProperty(value = "customerId", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"customer_id", "customerId"})  // 同时支持下划线和驼峰命名
    private String customerId;
    
    /**
     * 攻击者MAC地址
     */
    @JsonProperty(value = "attackMac", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"attack_mac", "attackMac"})
    private String attackMac;
    
    /**
     * 攻击者IP地址
     */
    @JsonProperty(value = "attackIp", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"attack_ip", "attackIp"})
    private String attackIp;
    
    /**
     * 最常访问的蜜罐IP (V4.0 Phase 2新增)
     */
    @JsonProperty(value = "mostAccessedHoneypotIp", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"most_accessed_honeypot_ip", "mostAccessedHoneypotIp"})
    private String mostAccessedHoneypotIp;
    
    /**
     * 威胁评分 (Flink计算的评分)
     */
    @JsonProperty(value = "threatScore", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"threat_score", "threatScore"})
    private Double threatScore;
    
    /**
     * 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    @JsonProperty(value = "threatLevel", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"threat_level", "threatLevel"})
    private String threatLevel;
    
    /**
     * 攻击次数
     */
    @JsonProperty(value = "attackCount", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"attack_count", "attackCount"})
    private Integer attackCount;
    
    /**
     * 唯一IP数量
     */
    @JsonProperty(value = "uniqueIps", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"unique_ips", "uniqueIps"})
    private Integer uniqueIps;
    
    /**
     * 唯一端口数量
     */
    @JsonProperty(value = "uniquePorts", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"unique_ports", "uniquePorts"})
    private Integer uniquePorts;
    
    /**
     * 唯一设备数量
     */
    @JsonProperty(value = "uniqueDevices", access = JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"unique_devices", "uniqueDevices"})
    private Integer uniqueDevices;
    
    /**
     * 评估时间戳 (ISO8601格式 或 Unix时间戳)
     */
    @JsonProperty(value = "timestamp", access = JsonProperty.Access.WRITE_ONLY)
    private String timestamp;
    
    /**
     * 转换为聚合攻击数据
     */
    public AggregatedAttackData toAggregatedData() {
        return AggregatedAttackData.builder()
            .customerId(customerId)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .mostAccessedHoneypotIp(mostAccessedHoneypotIp)  // V4.0 Phase 2
            .attackCount(attackCount)
            .uniqueIps(uniqueIps)
            .uniquePorts(uniquePorts)
            .uniqueDevices(uniqueDevices)
            .timestamp(parseTimestamp(timestamp))
            .build();
    }
    
    /**
     * 解析时间戳 - 支持多种格式
     * 1. ISO-8601格式: "2024-01-15T10:30:00Z"
     * 2. Unix毫秒时间戳(数字): "1705315800000"
     * 3. Unix秒.纳秒格式: "1705315800.123456789"
     */
    private java.time.Instant parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return java.time.Instant.now();
        }
        
        try {
            // 尝试ISO-8601格式
            if (timestampStr.contains("T") || timestampStr.contains("Z")) {
                return java.time.Instant.parse(timestampStr);
            }
            
            // 尝试Unix时间戳格式（秒.纳秒）
            if (timestampStr.contains(".")) {
                String[] parts = timestampStr.split("\\.");
                long seconds = Long.parseLong(parts[0]);
                long nanos = parts.length > 1 ? Long.parseLong(parts[1].substring(0, Math.min(9, parts[1].length()))) : 0;
                // 补齐纳秒到9位
                while (parts.length > 1 && parts[1].length() < 9) {
                    nanos *= 10;
                }
                return java.time.Instant.ofEpochSecond(seconds, nanos);
            }
            
            // 尝试Unix毫秒时间戳（纯数字）
            long millis = Long.parseLong(timestampStr);
            // 判断是秒还是毫秒（如果值小于10^11认为是秒）
            if (millis < 100000000000L) {
                return java.time.Instant.ofEpochSecond(millis);
            } else {
                return java.time.Instant.ofEpochMilli(millis);
            }
        } catch (Exception e) {
            // 解析失败，使用当前时间
            org.slf4j.LoggerFactory.getLogger(ThreatAlertMessage.class)
                .warn("Failed to parse timestamp '{}', using current time: {}", timestampStr, e.getMessage());
            return java.time.Instant.now();
        }
    }
}
