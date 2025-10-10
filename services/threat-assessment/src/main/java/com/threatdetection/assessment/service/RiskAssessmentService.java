package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.*;
import com.threatdetection.assessment.repository.ThreatAlertRepository;
import com.threatdetection.assessment.repository.ThreatAssessmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * Core risk assessment service implementing advanced threat evaluation algorithms
 */
@Service
@Transactional
public class RiskAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final ThreatAlertRepository threatAlertRepository;
    private final ThreatAssessmentRepository threatAssessmentRepository;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final RecommendationEngine recommendationEngine;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Metrics
    private final Timer assessmentTimer;
    private final Counter assessmentCounter;
    private final Counter highRiskCounter;

    @Autowired
    public RiskAssessmentService(ThreatAlertRepository threatAlertRepository,
                                ThreatAssessmentRepository threatAssessmentRepository,
                                ThreatIntelligenceService threatIntelligenceService,
                                RecommendationEngine recommendationEngine,
                                KafkaTemplate<String, String> kafkaTemplate,
                                MeterRegistry meterRegistry) {
        this.threatAlertRepository = threatAlertRepository;
        this.threatAssessmentRepository = threatAssessmentRepository;
        this.threatIntelligenceService = threatIntelligenceService;
        this.recommendationEngine = recommendationEngine;
        this.kafkaTemplate = kafkaTemplate;

        // Initialize metrics
        this.assessmentTimer = Timer.builder("assessment.duration")
                .description("Time taken to perform threat assessment")
                .register(meterRegistry);
        this.assessmentCounter = Counter.builder("assessment.requests.total")
                .description("Total number of assessment requests")
                .register(meterRegistry);
        this.highRiskCounter = Counter.builder("assessment.high_risk.total")
                .description("Total number of high-risk assessments")
                .register(meterRegistry);
    }

    /**
     * Perform comprehensive risk assessment for a threat alert
     */
    public AssessmentResponse assessThreat(AssessmentRequest request) {
        logger.info("Starting risk assessment for alert: {}", request.getAlertId());

        long startTime = System.currentTimeMillis();
        assessmentCounter.increment();

        try {
            return assessmentTimer.recordCallable(() -> performAssessment(request));
        } catch (Exception e) {
            logger.error("Error during threat assessment for alert: {}", request.getAlertId(), e);
            throw new RuntimeException("Assessment failed", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Assessment completed for alert: {} in {}ms", request.getAlertId(), duration);
        }
    }

    private AssessmentResponse performAssessment(AssessmentRequest request) {
        // Generate unique assessment ID
        String assessmentId = "assessment-" + UUID.randomUUID().toString().substring(0, 8);

        // Calculate risk score using multi-dimensional algorithm
        double riskScore = calculateRiskScore(request);

        // Determine risk level
        RiskLevel riskLevel = determineRiskLevel(riskScore);

        // Calculate confidence based on available data
        double confidence = calculateConfidence(request);

        // Get threat intelligence
        ThreatIntelligence threatIntelligence = threatIntelligenceService
                .enrichWithIntelligence(request.getAttackMac(), request.getAttackPatterns());

        // Generate recommendations
        List<Recommendation> recommendations = recommendationEngine
                .generateRecommendations(request, riskLevel, riskScore);

        // Create assessment entity
        ThreatAssessment assessment = new ThreatAssessment(assessmentId, request.getAlertId(),
                riskLevel, riskScore, confidence);
        assessment.setRecommendations(recommendations);
        assessment.setThreatIntelligence(threatIntelligence);
        assessment.setAssessmentTimestamp(LocalDateTime.now());

        // Save assessment
        threatAssessmentRepository.save(assessment);

        // Mark alert as processed
        markAlertAsProcessed(request.getAlertId());

        // Track high-risk assessments
        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            highRiskCounter.increment();
        }

        // Publish threat event to Kafka for CRITICAL threats
        if (riskLevel == RiskLevel.CRITICAL) {
            publishThreatEvent(request, assessment);
        }

        // Create response
        AssessmentResponse response = new AssessmentResponse(assessmentId, riskLevel, riskScore,
                confidence, recommendations, threatIntelligence, assessment.getAssessmentTimestamp());
        response.setProcessingDurationMs(System.currentTimeMillis() - System.currentTimeMillis());

        return response;
    }

    /**
     * Calculate risk score using multi-dimensional algorithm
     * riskScore = baseThreatScore × contextMultiplier × trendMultiplier × intelligenceMultiplier
     */
    private double calculateRiskScore(AssessmentRequest request) {
        double baseScore = request.getThreatScore();

        // Context multiplier based on affected assets and attack patterns
        double contextMultiplier = calculateContextMultiplier(request);

        // Trend multiplier based on historical patterns
        double trendMultiplier = calculateTrendMultiplier(request);

        // Intelligence multiplier based on threat intelligence
        double intelligenceMultiplier = calculateIntelligenceMultiplier(request);

        double finalScore = baseScore * contextMultiplier * trendMultiplier * intelligenceMultiplier;

        logger.debug("Risk calculation for alert {}: base={}, context={}, trend={}, intelligence={}, final={}",
                request.getAlertId(), baseScore, contextMultiplier, trendMultiplier, intelligenceMultiplier, finalScore);

        return Math.max(0, Math.min(10000, finalScore)); // Clamp between 0-10000
    }

    private double calculateContextMultiplier(AssessmentRequest request) {
        double multiplier = 1.0;

        // Asset value multiplier
        if (request.getAffectedAssets() != null) {
            for (String asset : request.getAffectedAssets()) {
                if (asset.contains("database") || asset.contains("server")) {
                    multiplier *= 1.5;
                } else if (asset.contains("web")) {
                    multiplier *= 1.3;
                }
            }
        }

        // Attack pattern severity
        if (request.getAttackPatterns() != null) {
            for (String pattern : request.getAttackPatterns()) {
                if (pattern.contains("sql_injection") || pattern.contains("rce")) {
                    multiplier *= 1.8;
                } else if (pattern.contains("brute_force") || pattern.contains("dos")) {
                    multiplier *= 1.4;
                }
            }
        }

        return Math.min(multiplier, 3.0); // Cap at 3x
    }

    private double calculateTrendMultiplier(AssessmentRequest request) {
        // Check recent activity from same attacker
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentAlerts = threatAlertRepository.countRecentAlertsByMac(request.getAttackMac(), oneHourAgo);

        double trendMultiplier = 1.0 + (recentAlerts * 0.1); // +10% per recent alert
        return Math.min(trendMultiplier, 2.0); // Cap at 2x
    }

    private double calculateIntelligenceMultiplier(AssessmentRequest request) {
        // This would integrate with external threat intelligence
        // For now, return base multiplier
        return 1.0;
    }

    /**
     * Determine risk level based on calculated score
     */
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 1000) return RiskLevel.CRITICAL;
        if (riskScore >= 500) return RiskLevel.HIGH;
        if (riskScore >= 100) return RiskLevel.MEDIUM;
        if (riskScore >= 10) return RiskLevel.LOW;
        return RiskLevel.INFO;
    }

    /**
     * Calculate confidence score based on data completeness
     */
    private double calculateConfidence(AssessmentRequest request) {
        double confidence = 0.5; // Base confidence

        // Increase confidence based on available data
        if (request.getAttackPatterns() != null && !request.getAttackPatterns().isEmpty()) {
            confidence += 0.2;
        }
        if (request.getAffectedAssets() != null && !request.getAffectedAssets().isEmpty()) {
            confidence += 0.2;
        }
        if (request.getThreatName() != null && !request.getThreatName().isEmpty()) {
            confidence += 0.1;
        }

        return Math.min(confidence, 1.0);
    }

    /**
     * Mark threat alert as processed
     */
    private void markAlertAsProcessed(String alertId) {
        threatAlertRepository.findByAlertId(alertId).ifPresent(alert -> {
            alert.setProcessed(true);
            threatAlertRepository.save(alert);
        });
    }

    /**
     * Get assessment by ID
     */
    @Cacheable(value = "assessments", key = "#assessmentId")
    public Optional<ThreatAssessment> getAssessment(String assessmentId) {
        return threatAssessmentRepository.findByAssessmentId(assessmentId);
    }

    /**
     * Get threat trends analysis
     */
    public List<TrendAnalysis> getThreatTrends(LocalDateTime startTime, LocalDateTime endTime,
                                              RiskLevel threatLevel, Integer limit) {
        List<ThreatAssessment> assessments = threatAssessmentRepository
                .findAssessmentsInTimeRange(startTime, endTime);

        if (threatLevel != null) {
            assessments = assessments.stream()
                    .filter(a -> a.getRiskLevel() == threatLevel)
                    .collect(Collectors.toList());
        }

        // Group by hour buckets
        Map<String, List<ThreatAssessment>> groupedByHour = assessments.stream()
                .collect(Collectors.groupingBy(a ->
                    a.getAssessmentTimestamp().toLocalDate() + "T" +
                    (a.getAssessmentTimestamp().getHour()) + ":00:00Z"));

        List<TrendAnalysis> trends = groupedByHour.entrySet().stream()
                .map(entry -> {
                    String timeBucket = entry.getKey();
                    List<ThreatAssessment> hourAssessments = entry.getValue();

                    Map<RiskLevel, Integer> levelCounts = hourAssessments.stream()
                            .collect(Collectors.groupingBy(ThreatAssessment::getRiskLevel,
                                    Collectors.summingInt(a -> 1)));

                    double avgRiskScore = hourAssessments.stream()
                            .mapToDouble(ThreatAssessment::getRiskScore)
                            .average()
                            .orElse(0.0);

                    // Get top attack patterns (simplified)
                    List<String> topPatterns = hourAssessments.stream()
                            .filter(a -> a.getThreatIntelligence() != null)
                            .flatMap(a -> a.getRecommendations().stream())
                            .map(r -> r.getAction().toString())
                            .distinct()
                            .limit(3)
                            .collect(Collectors.toList());

                    return new TrendAnalysis(timeBucket, levelCounts, topPatterns,
                            avgRiskScore, hourAssessments.size());
                })
                .collect(Collectors.toList());

        // Sort by time and limit results
        trends.sort(Comparator.comparing(TrendAnalysis::getTimeBucket).reversed());
        if (limit != null && trends.size() > limit) {
            trends = trends.subList(0, limit);
        }

        return trends;
    }

    /**
     * Publish threat event to Kafka for alert management
     */
    private void publishThreatEvent(AssessmentRequest request, ThreatAssessment assessment) {
        try {
            // Create threat event message
            Map<String, Object> threatEvent = new HashMap<>();
            threatEvent.put("id", assessment.getAssessmentId());
            threatEvent.put("alertId", request.getAlertId());
            threatEvent.put("severity", assessment.getRiskLevel().ordinal()); // Convert enum to int
            threatEvent.put("threatScore", assessment.getRiskScore());
            threatEvent.put("title", request.getThreatName() != null ? request.getThreatName() : "Critical Threat Detected");
            threatEvent.put("description", generateThreatDescription(request, assessment));
            threatEvent.put("attackMac", request.getAttackMac());
            threatEvent.put("timestamp", System.currentTimeMillis());
            threatEvent.put("attackPatterns", request.getAttackPatterns());
            threatEvent.put("affectedAssets", request.getAffectedAssets());

            // Convert to JSON
            String message = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(threatEvent);

            // Publish to Kafka
            kafkaTemplate.send("threat-events", assessment.getAssessmentId(), message);

            logger.info("Published CRITICAL threat event to Kafka: {}", assessment.getAssessmentId());

        } catch (Exception e) {
            logger.error("Failed to publish threat event for assessment: {}", assessment.getAssessmentId(), e);
        }
    }

    /**
     * Generate threat description based on assessment
     */
    private String generateThreatDescription(AssessmentRequest request, ThreatAssessment assessment) {
        StringBuilder description = new StringBuilder();
        description.append("Critical threat detected with risk score: ").append(String.format("%.2f", assessment.getRiskScore()));

        if (request.getAttackPatterns() != null && !request.getAttackPatterns().isEmpty()) {
            description.append(". Attack patterns: ").append(String.join(", ", request.getAttackPatterns()));
        }

        if (request.getAffectedAssets() != null && !request.getAffectedAssets().isEmpty()) {
            description.append(". Affected assets: ").append(String.join(", ", request.getAffectedAssets()));
        }

        return description.toString();
    }
}