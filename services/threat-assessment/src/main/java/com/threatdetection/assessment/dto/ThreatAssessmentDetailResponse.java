package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 威胁评估详情响应DTO
 * 
 * <p>对齐API文档: threat_assessment_query_api.md § 5.1
 * <p>对齐数据库表: threat_assessments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAssessmentDetailResponse {
    
    /**
     * 评估记录ID
     */
    private Long id;
    
    /**
     * 客户ID
     */
    private String customerId;
    
    /**
     * 被诱捕者MAC地址
     */
    private String attackMac;
    
    /**
     * 被诱捕者IP地址
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
     * 攻击次数
     */
    private Integer attackCount;
    
    /**
     * 访问的诱饵IP数量 (横向移动范围)
     */
    private Integer uniqueIps;
    
    /**
     * 尝试的端口种类 (攻击意图多样性)
     */
    private Integer uniquePorts;
    
    /**
     * 检测到的蜜罐设备数
     */
    private Integer uniqueDevices;
    
    /**
     * 评估时间
     */
    private Instant assessmentTime;
    
    /**
     * 记录创建时间
     */
    private Instant createdAt;
    
    /**
     * 端口列表 (JSON字符串)
     */
    private String portList;
    
    /**
     * 端口风险评分
     */
    private Double portRiskScore;
    
    /**
     * 检测层级
     */
    private Integer detectionTier;
    
    /**
     * 缓解建议列表 (动态生成)
     */
    private List<String> mitigationRecommendations;

    /**
     * 评分计算分解 (开发者模式)
     */
    private ScoreBreakdown scoreBreakdown;
}
