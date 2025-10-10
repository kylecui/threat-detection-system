package com.threatdetection.assessment.model;

import java.util.Map;

/**
 * DTO for threat trend analysis responses
 */
public class TrendAnalysis {

    private String timeBucket;
    private Map<RiskLevel, Integer> threatLevels;
    private java.util.List<String> topAttackTypes;
    private double riskScoreAverage;
    private int totalAssessments;

    // Constructors
    public TrendAnalysis() {}

    public TrendAnalysis(String timeBucket, Map<RiskLevel, Integer> threatLevels,
                        java.util.List<String> topAttackTypes, double riskScoreAverage,
                        int totalAssessments) {
        this.timeBucket = timeBucket;
        this.threatLevels = threatLevels;
        this.topAttackTypes = topAttackTypes;
        this.riskScoreAverage = riskScoreAverage;
        this.totalAssessments = totalAssessments;
    }

    // Getters and Setters
    public String getTimeBucket() { return timeBucket; }
    public void setTimeBucket(String timeBucket) { this.timeBucket = timeBucket; }

    public Map<RiskLevel, Integer> getThreatLevels() { return threatLevels; }
    public void setThreatLevels(Map<RiskLevel, Integer> threatLevels) { this.threatLevels = threatLevels; }

    public java.util.List<String> getTopAttackTypes() { return topAttackTypes; }
    public void setTopAttackTypes(java.util.List<String> topAttackTypes) { this.topAttackTypes = topAttackTypes; }

    public double getRiskScoreAverage() { return riskScoreAverage; }
    public void setRiskScoreAverage(double riskScoreAverage) { this.riskScoreAverage = riskScoreAverage; }

    public int getTotalAssessments() { return totalAssessments; }
    public void setTotalAssessments(int totalAssessments) { this.totalAssessments = totalAssessments; }
}