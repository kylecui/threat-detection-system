package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.model.*;
import com.threatdetection.assessment.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST API controller for threat assessment operations
 */
@RestController
@RequestMapping("/api/v1/assessment")
@Tag(name = "Threat Assessment", description = "Threat assessment and risk evaluation API")
public class AssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentController.class);

    private final RiskAssessmentService riskAssessmentService;

    @Autowired
    public AssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    /**
     * Evaluate a threat alert and provide risk assessment
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate threat alert",
               description = "Perform comprehensive risk assessment for a threat alert")
    public ResponseEntity<AssessmentResponse> evaluateThreat(@Valid @RequestBody AssessmentRequest request) {
        logger.info("Received assessment request for alert: {}", request.getAlertId());

        try {
            AssessmentResponse response = riskAssessmentService.assessThreat(request);
            logger.info("Assessment completed for alert: {} with risk level: {}",
                       request.getAlertId(), response.getRiskLevel());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing assessment request for alert: {}", request.getAlertId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieve detailed assessment information
     */
    @GetMapping("/{assessmentId}")
    @Operation(summary = "Get assessment details",
               description = "Retrieve detailed information about a specific threat assessment")
    public ResponseEntity<ThreatAssessment> getAssessment(@PathVariable String assessmentId) {
        logger.debug("Retrieving assessment: {}", assessmentId);

        Optional<ThreatAssessment> assessment = riskAssessmentService.getAssessment(assessmentId);
        if (assessment.isPresent()) {
            return ResponseEntity.ok(assessment.get());
        } else {
            logger.warn("Assessment not found: {}", assessmentId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get threat trends and statistics
     */
    @GetMapping("/trends")
    @Operation(summary = "Get threat trends",
               description = "Retrieve threat trends and statistical analysis")
    public ResponseEntity<List<TrendAnalysis>> getThreatTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) RiskLevel threatLevel,
            @RequestParam(defaultValue = "100") Integer limit) {

        logger.info("Retrieving threat trends from {} to {} with limit {}", startTime, endTime, limit);

        try {
            List<TrendAnalysis> trends = riskAssessmentService.getThreatTrends(startTime, endTime, threatLevel, limit);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            logger.error("Error retrieving threat trends", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Execute mitigation actions for an assessment
     */
    @PostMapping("/mitigation/{assessmentId}")
    @Operation(summary = "Execute mitigation",
               description = "Execute mitigation actions for a threat assessment")
    public ResponseEntity<String> executeMitigation(@PathVariable String assessmentId) {
        logger.info("Executing mitigation for assessment: {}", assessmentId);

        try {
            // In a real implementation, this would trigger actual mitigation actions
            // For now, just return success
            return ResponseEntity.ok("Mitigation actions initiated for assessment: " + assessmentId);
        } catch (Exception e) {
            logger.error("Error executing mitigation for assessment: {}", assessmentId, e);
            return ResponseEntity.internalServerError().body("Failed to execute mitigation");
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check",
               description = "Check if the threat assessment service is healthy")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Threat Assessment Service is healthy");
    }
}