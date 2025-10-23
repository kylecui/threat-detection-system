package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.model.ThreatAssessment;
import com.threatdetection.assessment.service.ThreatAssessmentService;
import com.threatdetection.assessment.service.ThreatQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for threat assessment operations
 * 
 * <p>对齐API文档:
 * <ul>
 *   <li>threat_assessment_query_api.md - 查询和趋势分析</li>
 *   <li>threat_assessment_overview.md - 系统概述</li>
 *   <li>POST /evaluate - 执行威胁评估</li>
 * </ul>
 * 
 * @author ThreatDetection Team
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/assessment")
@Tag(name = "Threat Assessment", description = "Threat assessment query and analysis API")
public class AssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentController.class);

    private final ThreatQueryService threatQueryService;
    private final ThreatAssessmentService threatAssessmentService;

    public AssessmentController(ThreatQueryService threatQueryService,
                               ThreatAssessmentService threatAssessmentService) {
        this.threatQueryService = threatQueryService;
        this.threatAssessmentService = threatAssessmentService;
    }

    /**
     * 执行威胁评估
     * 
     * <p>API文档: POST /api/v1/assessment/evaluate
     * <p>基于蜜罐机制的聚合攻击数据执行实时威胁评估
     * 
     * @param request 评估请求数据
     * @return 评估结果
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Execute threat assessment",
               description = "Evaluate threat based on aggregated attack data from honeypot detection")
    public ResponseEntity<AssessmentResponse> evaluateThreat(
            @Parameter(description = "Assessment request with aggregated attack data", required = true)
            @Valid @RequestBody AssessmentRequest request) {
        
        logger.info("Executing threat assessment: customerId={}, attackMac={}, attackCount={}",
                   request.getCustomerId(), request.getAttackMac(), request.getAttackCount());

        try {
            // 1. 转换请求为聚合数据
            AggregatedAttackData data = request.toAggregatedData();
            
            // 2. 验证数据完整性
            if (!data.isValid()) {
                logger.warn("Invalid assessment request: customerId={}, attackMac={}",
                           request.getCustomerId(), request.getAttackMac());
                return ResponseEntity.badRequest().build();
            }
            
            // 3. 执行威胁评估
            ThreatAssessment assessment = threatAssessmentService.assessThreat(data);
            
            // 4. 转换为响应DTO
            AssessmentResponse response = AssessmentResponse.fromEntity(assessment);
            
            logger.info("✅ Threat assessment completed: assessmentId={}, customerId={}, level={}, score={}",
                       response.getAssessmentId(), request.getCustomerId(),
                       response.getThreatLevel(), response.getThreatScore());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid assessment data: customerId={}, error={}",
                       request.getCustomerId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error executing threat assessment: customerId={}, attackMac={}",
                        request.getCustomerId(), request.getAttackMac(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取评估详情
     * 
     * <p>API文档: threat_assessment_query_api.md § 5.1
     * 
     * @param assessmentId 评估记录ID
     * @return 评估详情
     */
    @GetMapping("/{assessmentId}")
    @Operation(summary = "Get assessment details",
               description = "Retrieve detailed information about a specific threat assessment")
    public ResponseEntity<ThreatAssessmentDetailResponse> getAssessment(
            @Parameter(description = "Assessment ID", required = true)
            @PathVariable Long assessmentId) {
        
        logger.debug("Retrieving assessment: id={}", assessmentId);

        try {
            ThreatAssessmentDetailResponse response = threatQueryService.getAssessmentDetail(assessmentId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Assessment not found: id={}", assessmentId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving assessment: id={}", assessmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查询评估列表 (分页)
     * 
     * <p>API文档: threat_assessment_query_api.md § 5.2
     * 
     * @param customerId 客户ID (必需)
     * @param page 页码 (从0开始,默认0)
     * @param size 每页大小 (默认20)
     * @return 分页结果
     */
    @GetMapping("/assessments")
    @Operation(summary = "Query assessment list",
               description = "Query threat assessments with pagination")
    public ResponseEntity<Page<ThreatAssessmentDetailResponse>> getAssessmentList(
            @Parameter(description = "Customer ID", required = true)
            @RequestParam(name = "customer_id") String customerId,
            
            @Parameter(description = "Page number (0-based)", required = false)
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", required = false)
            @RequestParam(defaultValue = "20") int size) {

        logger.info("Querying assessment list: customerId={}, page={}, size={}", customerId, page, size);

        try {
            Page<ThreatAssessmentDetailResponse> result = threatQueryService.getAssessmentList(customerId, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error querying assessment list: customerId={}", customerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取威胁统计
     * 
     * <p>API文档: threat_assessment_query_api.md § 5.4
     * <p>前端对接: Dashboard统计卡片
     * 
     * @param customerId 客户ID (必需)
     * @return 统计结果
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get threat statistics",
               description = "Get threat statistics including level distribution and average scores")
    public ResponseEntity<ThreatStatisticsResponse> getStatistics(
            @Parameter(description = "Customer ID", required = true)
            @RequestParam(name = "customer_id") String customerId) {

        logger.info("Getting threat statistics: customerId={}", customerId);

        try {
            ThreatStatisticsResponse response = threatQueryService.getStatistics(customerId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting statistics: customerId={}", customerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取威胁趋势 (最近24小时)
     * 
     * <p>API文档: threat_assessment_query_api.md § 5.3
     * <p>前端对接: Dashboard趋势图
     * 
     * @param customerId 客户ID (必需)
     * @return 趋势数据点列表 (按小时聚合)
     */
    @GetMapping("/trend")
    @Operation(summary = "Get threat trend",
               description = "Get threat trend for the last 24 hours (hourly aggregation)")
    public ResponseEntity<List<TrendDataPoint>> getThreatTrend(
            @Parameter(description = "Customer ID", required = true)
            @RequestParam(name = "customer_id") String customerId) {

        logger.info("Getting threat trend: customerId={}", customerId);

        try {
            List<TrendDataPoint> trend = threatQueryService.getThreatTrend(customerId);
            return ResponseEntity.ok(trend);
        } catch (Exception e) {
            logger.error("Error getting threat trend: customerId={}", customerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取端口分布 (TOP 10)
     * 
     * <p>前端对接: Dashboard端口分布饼图
     * 
     * @param customerId 客户ID (必需)
     * @return 端口分布列表
     */
    @GetMapping("/port-distribution")
    @Operation(summary = "Get port distribution",
               description = "Get top 10 attacked ports distribution")
    public ResponseEntity<List<PortDistribution>> getPortDistribution(
            @Parameter(description = "Customer ID", required = true)
            @RequestParam(name = "customer_id") String customerId) {

        logger.info("Getting port distribution: customerId={}", customerId);

        try {
            List<PortDistribution> distribution = threatQueryService.getPortDistribution(customerId);
            return ResponseEntity.ok(distribution);
        } catch (Exception e) {
            logger.error("Error getting port distribution: customerId={}", customerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 健康检查
     * 
     * <p>API文档: threat_assessment_query_api.md § 5.6
     */
    @GetMapping("/health")
    @Operation(summary = "Health check",
               description = "Check if the threat assessment service is healthy")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "threat-assessment");
        return ResponseEntity.ok(status);
    }
}