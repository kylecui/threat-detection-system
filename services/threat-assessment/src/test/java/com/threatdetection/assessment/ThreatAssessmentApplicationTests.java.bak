package com.threatdetection.assessment;

import com.threatdetection.assessment.model.RiskLevel;
import com.threatdetection.assessment.model.AssessmentRequest;
import com.threatdetection.assessment.model.AssessmentResponse;
import com.threatdetection.assessment.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for threat assessment service
 */
@SpringBootTest
@ActiveProfiles("test")
class ThreatAssessmentApplicationTests {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Test
    void contextLoads() {
        assertThat(riskAssessmentService).isNotNull();
    }

    @Test
    void testRiskAssessment() {
        // Create test request
        AssessmentRequest request = new AssessmentRequest();
        request.setAlertId("test-alert-123");
        request.setAttackMac("00:11:22:33:44:55");
        request.setThreatScore(25.0);
        request.setThreatLevel(RiskLevel.MEDIUM);
        request.setTimestamp(LocalDateTime.now());
        request.setAttackPatterns(List.of("brute_force"));
        request.setAffectedAssets(List.of("web_server"));

        // Perform assessment
        AssessmentResponse response = riskAssessmentService.assessThreat(request);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.getAssessmentId()).isNotNull();
        assertThat(response.getRiskLevel()).isNotNull();
        assertThat(response.getRiskScore()).isGreaterThan(0);
        assertThat(response.getConfidence()).isBetween(0.0, 1.0);
        assertThat(response.getRecommendations()).isNotEmpty();
    }

    @Test
    void testHighRiskAssessment() {
        // Create high-risk request
        AssessmentRequest request = new AssessmentRequest();
        request.setAlertId("high-risk-alert");
        request.setAttackMac("00:11:22:33:44:55");
        request.setThreatScore(750.0); // High score
        request.setThreatLevel(RiskLevel.HIGH);
        request.setTimestamp(LocalDateTime.now());
        request.setAttackPatterns(List.of("sql_injection", "rce"));
        request.setAffectedAssets(List.of("database", "web_server"));

        AssessmentResponse response = riskAssessmentService.assessThreat(request);

        assertThat(response.getRiskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
        assertThat(response.getRecommendations()).isNotEmpty();
    }
}