package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 威胁评估请求DTO
 * 
 * <p>用于POST /api/v1/assessment/evaluate端点
 * <p>基于蜜罐机制的聚合攻击数据输入
 * 
 * @author ThreatDetection Team
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRequest {

    /**
     * 客户ID (必需)
     */
    @JsonProperty("customer_id")
    @NotBlank(message = "客户ID不能为空")
    @Size(max = 100, message = "客户ID长度不能超过100")
    private String customerId;

    /**
     * 被诱捕者MAC地址 (必需)
     */
    @JsonProperty("attack_mac")
    @NotBlank(message = "攻击者MAC地址不能为空")
    @Size(max = 17, message = "MAC地址长度不能超过17")
    private String attackMac;

    /**
     * 被诱捕者IP地址 (可选)
     */
    @JsonProperty("attack_ip")
    @Size(max = 45, message = "IP地址长度不能超过45")
    private String attackIp;

    /**
     * 探测次数 (必需, >= 1)
     */
    @JsonProperty("attack_count")
    @NotNull(message = "攻击次数不能为空")
    @Min(value = 1, message = "攻击次数必须大于0")
    private Integer attackCount;

    /**
     * 访问的诱饵IP数量 (必需, >= 1)
     */
    @JsonProperty("unique_ips")
    @NotNull(message = "唯一IP数量不能为空")
    @Min(value = 1, message = "唯一IP数量必须大于0")
    private Integer uniqueIps;

    /**
     * 尝试的端口种类 (必需, >= 1)
     */
    @JsonProperty("unique_ports")
    @NotNull(message = "唯一端口数量不能为空")
    @Min(value = 1, message = "唯一端口数量必须大于0")
    private Integer uniquePorts;

    /**
     * 检测到该攻击者的蜜罐设备数 (必需, >= 1)
     */
    @JsonProperty("unique_devices")
    @NotNull(message = "唯一设备数量不能为空")
    @Min(value = 1, message = "唯一设备数量必须大于0")
    private Integer uniqueDevices;

    /**
     * 聚合窗口时间戳 (可选, 默认为当前时间)
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * 端口列表 (可选, 用于端口权重计算)
     * V4.0 Phase 3新增字段
     */
    @JsonProperty("port_list")
    private java.util.List<Integer> portList;

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
            .timestamp(timestamp != null ? timestamp : Instant.now())
            .portList(portList)
            .build();
    }
}
