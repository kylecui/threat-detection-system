package com.threatdetection.assessment.dto;

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
    private Long assessmentId;

    /**
     * 客户ID
     */
    private String customerId;

    /**
     * 攻击者MAC地址
     */
    private String attackMac;

    /**
     * 攻击者IP地址
     */
    private String attackIp;

    /**
     * 威胁评分
     */
    private Double threatScore;

    /**
     * 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    private String threatLevel;

    /**
     * 风险因子
     */
    private RiskFactors riskFactors;

    /**
     * 缓解建议列表
     */
    private List<String> mitigationRecommendations;

    /**
     * 评估时间
     */
    private Instant assessmentTime;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 风险因子嵌套对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactors {
        private Integer attackCount;

        private Integer uniqueIps;

        private Integer uniquePorts;

        private Integer uniqueDevices;

        private Double timeWeight;

        private Double ipWeight;

        private Double portWeight;

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
