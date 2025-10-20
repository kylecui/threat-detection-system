package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 趋势数据点DTO
 * 
 * <p>对齐API文档: threat_assessment_query_api.md § ThreatTrendResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataPoint {
    
    /**
     * 时间戳
     */
    private Instant timestamp;
    
    /**
     * 威胁数量
     */
    private Long count;
    
    /**
     * 平均威胁评分
     */
    private Double averageScore;
    
    /**
     * 最高威胁评分
     */
    private Double maxScore;
    
    /**
     * CRITICAL级别数量
     */
    private Long criticalCount;
    
    /**
     * HIGH级别数量
     */
    private Long highCount;
    
    /**
     * MEDIUM级别数量
     */
    private Long mediumCount;
}
