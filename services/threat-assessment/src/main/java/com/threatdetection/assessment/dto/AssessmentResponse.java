package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 威胁评估响应DTO
 * 
 * <p>用于POST /api/v1/assessment/evaluate端点的响应
 * <p>包含完整的评估结果和缓解建议
 * 
 * @author ThreatDetection Team
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponse {

    /**
     * 评估记录ID
     */
    @JsonProperty("assessment_id")
    private Long assessmentId;

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
     * 威胁评分
     */
    @JsonProperty("threat_score")
    private Double threatScore;

    /**
     * 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    @JsonProperty("threat_level")
    private String threatLevel;

    /**
     * 风险因子
     */
    @JsonProperty("risk_factors")
    private RiskFactors riskFactors;

    /**
     * 缓解建议列表
     */
    @JsonProperty("mitigation_recommendations")
    private List<String> mitigationRecommendations;

    /**
     * 评估时间
     */
    @JsonProperty("assessment_time")
    private Instant assessmentTime;

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private Instant createdAt;

    /**
     * 风险因子嵌套对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactors {
        @JsonProperty("attack_count")
        private Integer attackCount;

        @JsonProperty("unique_ips")
        private Integer uniqueIps;

        @JsonProperty("unique_ports")
        private Integer uniquePorts;

        @JsonProperty("unique_devices")
        private Integer uniqueDevices;

        @JsonProperty("time_weight")
        private Double timeWeight;

        @JsonProperty("ip_weight")
        private Double ipWeight;

        @JsonProperty("port_weight")
        private Double portWeight;

        @JsonProperty("device_weight")
        private Double deviceWeight;
    }

    /**
     * 从ThreatAssessment实体转换
     */
    public static AssessmentResponse fromEntity(com.threatdetection.assessment.model.ThreatAssessment entity) {
        // 解析缓解建议 (从分号分隔字符串转为列表)
        List<String> recommendations = entity.getMitigationRecommendations() != null
            ? Arrays.stream(entity.getMitigationRecommendations().split(";\\s*"))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList())
            : List.of();

        return AssessmentResponse.builder()
            .assessmentId(entity.getId())
            .customerId(entity.getCustomerId())
            .attackMac(entity.getAttackMac())
            .attackIp(entity.getAttackIp())
            .threatScore(entity.getThreatScore())
            .threatLevel(entity.getThreatLevel())
            .riskFactors(RiskFactors.builder()
                .attackCount(entity.getAttackCount())
                .uniqueIps(entity.getUniqueIps())
                .uniquePorts(entity.getUniquePorts())
                .uniqueDevices(entity.getUniqueDevices())
                .timeWeight(entity.getTimeWeight())
                .ipWeight(entity.getIpWeight())
                .portWeight(entity.getPortWeight())
                .deviceWeight(entity.getDeviceWeight())
                .build())
            .mitigationRecommendations(recommendations)
            .assessmentTime(entity.getAssessmentTime())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
