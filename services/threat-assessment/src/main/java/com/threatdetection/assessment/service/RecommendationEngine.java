package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Recommendation engine for generating mitigation strategies based on threat assessment
 */
@Service
public class RecommendationEngine {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationEngine.class);

    /**
     * Generate mitigation recommendations based on assessment results
     */
    public List<Recommendation> generateRecommendations(AssessmentRequest request,
                                                       RiskLevel riskLevel,
                                                       double riskScore) {
        logger.debug("Generating recommendations for risk level: {}, score: {}", riskLevel, riskScore);

        List<Recommendation> recommendations = new ArrayList<>();

        // Add recommendations based on risk level
        switch (riskLevel) {
            case CRITICAL:
                recommendations.addAll(generateCriticalRecommendations(request));
                break;
            case HIGH:
                recommendations.addAll(generateHighRiskRecommendations(request));
                break;
            case MEDIUM:
                recommendations.addAll(generateMediumRiskRecommendations(request));
                break;
            case LOW:
                recommendations.addAll(generateLowRiskRecommendations(request));
                break;
            case INFO:
                recommendations.addAll(generateInfoRecommendations(request));
                break;
        }

        // Add pattern-specific recommendations
        if (request.getAttackPatterns() != null) {
            recommendations.addAll(generatePatternSpecificRecommendations(request.getAttackPatterns()));
        }

        // Add asset-specific recommendations
        if (request.getAffectedAssets() != null) {
            recommendations.addAll(generateAssetSpecificRecommendations(request.getAffectedAssets()));
        }

        // Sort by priority
        recommendations.sort((a, b) -> Integer.compare(getPriorityOrder(a.getPriority()), getPriorityOrder(b.getPriority())));

        logger.debug("Generated {} recommendations", recommendations.size());
        return recommendations;
    }

    private List<Recommendation> generateCriticalRecommendations(AssessmentRequest request) {
        List<Recommendation> recommendations = new ArrayList<>();

        // Immediate blocking actions
        recommendations.add(createRecommendation(
            MitigationAction.BLOCK_IP,
            Priority.CRITICAL,
            "Immediately block the attacking IP address to prevent further damage",
            Map.of("ip", extractIpFromMac(request.getAttackMac()))
        ));

        recommendations.add(createRecommendation(
            MitigationAction.ISOLATE_ASSET,
            Priority.CRITICAL,
            "Isolate affected assets from the network to contain the breach"
        ));

        recommendations.add(createRecommendation(
            MitigationAction.ALERT_SECURITY,
            Priority.CRITICAL,
            "Alert security team for immediate incident response"
        ));

        return recommendations;
    }

    private List<Recommendation> generateHighRiskRecommendations(AssessmentRequest request) {
        List<Recommendation> recommendations = new ArrayList<>();

        recommendations.add(createRecommendation(
            MitigationAction.BLOCK_IP,
            Priority.HIGH,
            "Block the attacking IP address",
            Map.of("ip", extractIpFromMac(request.getAttackMac()))
        ));

        recommendations.add(createRecommendation(
            MitigationAction.INCREASE_MONITORING,
            Priority.HIGH,
            "Increase monitoring for affected systems and similar attack patterns"
        ));

        recommendations.add(createRecommendation(
            MitigationAction.ALERT_SECURITY,
            Priority.HIGH,
            "Notify security operations center"
        ));

        return recommendations;
    }

    private List<Recommendation> generateMediumRiskRecommendations(AssessmentRequest request) {
        List<Recommendation> recommendations = new ArrayList<>();

        recommendations.add(createRecommendation(
            MitigationAction.INCREASE_MONITORING,
            Priority.MEDIUM,
            "Enhance monitoring for the attacking source"
        ));

        recommendations.add(createRecommendation(
            MitigationAction.LOG_ANALYSIS,
            Priority.MEDIUM,
            "Perform detailed log analysis to understand attack scope"
        ));

        return recommendations;
    }

    private List<Recommendation> generateLowRiskRecommendations(AssessmentRequest request) {
        List<Recommendation> recommendations = new ArrayList<>();

        recommendations.add(createRecommendation(
            MitigationAction.LOG_ANALYSIS,
            Priority.LOW,
            "Review logs for any unusual patterns"
        ));

        recommendations.add(createRecommendation(
            MitigationAction.UPDATE_SIGNATURES,
            Priority.LOW,
            "Ensure threat signatures are up to date"
        ));

        return recommendations;
    }

    private List<Recommendation> generateInfoRecommendations(AssessmentRequest request) {
        List<Recommendation> recommendations = new ArrayList<>();

        recommendations.add(createRecommendation(
            MitigationAction.MONITOR,
            Priority.INFO,
            "Continue normal monitoring - informational level threat"
        ));

        return recommendations;
    }

    private List<Recommendation> generatePatternSpecificRecommendations(List<String> attackPatterns) {
        List<Recommendation> recommendations = new ArrayList<>();

        for (String pattern : attackPatterns) {
            switch (pattern.toLowerCase()) {
                case "brute_force":
                    recommendations.add(createRecommendation(
                        MitigationAction.BLOCK_IP,
                        Priority.HIGH,
                        "Implement account lockout policies and rate limiting for brute force attacks"
                    ));
                    break;

                case "port_scan":
                    recommendations.add(createRecommendation(
                        MitigationAction.BLOCK_PORT,
                        Priority.MEDIUM,
                        "Configure firewall to drop port scanning attempts"
                    ));
                    break;

                case "sql_injection":
                    recommendations.add(createRecommendation(
                        MitigationAction.UPDATE_SIGNATURES,
                        Priority.HIGH,
                        "Review and update input validation, use prepared statements"
                    ));
                    break;

                case "dos":
                    recommendations.add(createRecommendation(
                        MitigationAction.RATE_LIMIT,
                        Priority.CRITICAL,
                        "Implement rate limiting and DDoS protection measures"
                    ));
                    break;
            }
        }

        return recommendations;
    }

    private List<Recommendation> generateAssetSpecificRecommendations(List<String> affectedAssets) {
        List<Recommendation> recommendations = new ArrayList<>();

        for (String asset : affectedAssets) {
            if (asset.toLowerCase().contains("database")) {
                recommendations.add(createRecommendation(
                    MitigationAction.LOG_ANALYSIS,
                    Priority.HIGH,
                    "Review database access logs and audit trails"
                ));
            } else if (asset.toLowerCase().contains("web")) {
                recommendations.add(createRecommendation(
                    MitigationAction.UPDATE_SIGNATURES,
                    Priority.MEDIUM,
                    "Update web application firewall rules"
                ));
            }
        }

        return recommendations;
    }

    private Recommendation createRecommendation(MitigationAction action, Priority priority,
                                              String description) {
        return createRecommendation(action, priority, description, null);
    }

    private Recommendation createRecommendation(MitigationAction action, Priority priority,
                                              String description, Map<String, String> parameters) {
        Recommendation recommendation = new Recommendation(action, priority, description);
        if (parameters != null) {
            recommendation.setParameters(parameters);
        }
        return recommendation;
    }

    private int getPriorityOrder(Priority priority) {
        switch (priority) {
            case CRITICAL: return 1;
            case HIGH: return 2;
            case MEDIUM: return 3;
            case LOW: return 4;
            case INFO: return 5;
            default: return 6;
        }
    }

    /**
     * Extract IP address from attack MAC (simplified - in reality would look up from logs)
     */
    private String extractIpFromMac(String attackMac) {
        // This is a placeholder - in real implementation, you'd look up the IP
        // from the original threat alert data or network logs
        return "192.168.1.100"; // Placeholder
    }
}