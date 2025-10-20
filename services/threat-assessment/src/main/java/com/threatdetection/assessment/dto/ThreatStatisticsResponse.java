package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 威胁统计响应DTO
 * 
 * <p>对齐API文档: threat_assessment_query_api.md § 5.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatStatisticsResponse {
    
    /**
     * 客户ID
     */
    private String customerId;
    
    /**
     * 统计总数
     */
    private Long totalCount;
    
    /**
     * 严重威胁数量 (CRITICAL)
     */
    private Long criticalCount;
    
    /**
     * 高危威胁数量 (HIGH)
     */
    private Long highCount;
    
    /**
     * 中危威胁数量 (MEDIUM)
     */
    private Long mediumCount;
    
    /**
     * 低危威胁数量 (LOW)
     */
    private Long lowCount;
    
    /**
     * 信息级数量 (INFO)
     */
    private Long infoCount;
    
    /**
     * 平均威胁评分
     */
    private Double averageThreatScore;
    
    /**
     * 最高威胁评分
     */
    private Double maxThreatScore;
    
    /**
     * 最低威胁评分
     */
    private Double minThreatScore;
    
    /**
     * 威胁等级分布 (扩展字段)
     */
    private Map<String, Long> levelDistribution;
}
