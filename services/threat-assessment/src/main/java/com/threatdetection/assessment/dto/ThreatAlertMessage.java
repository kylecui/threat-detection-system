package com.threatdetection.assessment.dto;

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
    @JsonProperty("customer_id")
    private String customerId;
    
    /**
     * 攻击者MAC地址
     */
    @JsonProperty("attack_mac")
    private String attackMac;
    
    /**
     * 攻击者IP地址
     */
    @JsonProperty("attack_ip")
    private String attackIp;
    
    /**
     * 威胁评分 (Flink计算的评分)
     */
    @JsonProperty("threat_score")
    private Double threatScore;
    
    /**
     * 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    @JsonProperty("threat_level")
    private String threatLevel;
    
    /**
     * 攻击次数
     */
    @JsonProperty("attack_count")
    private Integer attackCount;
    
    /**
     * 唯一IP数量
     */
    @JsonProperty("unique_ips")
    private Integer uniqueIps;
    
    /**
     * 唯一端口数量
     */
    @JsonProperty("unique_ports")
    private Integer uniquePorts;
    
    /**
     * 唯一设备数量
     */
    @JsonProperty("unique_devices")
    private Integer uniqueDevices;
    
    /**
     * 评估时间戳 (ISO8601格式)
     */
    @JsonProperty("timestamp")
    private String timestamp;
    
    /**
     * 转换为聚合攻击数据
     */
    public AggregatedAttackData toAggregatedData() {
        return AggregatedAttackData.builder()
            .customerId(customerId)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .attackCount(attackCount)
            .uniqueIps(uniqueIps)
            .uniquePorts(uniquePorts)
            .uniqueDevices(uniqueDevices)
            .timestamp(java.time.Instant.parse(timestamp))
            .build();
    }
}
