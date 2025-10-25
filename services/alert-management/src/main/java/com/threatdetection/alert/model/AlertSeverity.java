package com.threatdetection.alert.model;

/**
 * 告警严重程度枚举
 */
public enum AlertSeverity {
    CRITICAL("严重", 200),   // 与ThreatLevel阈值对齐: score >= 200
    HIGH("高危", 100),       // 与ThreatLevel阈值对齐: 100 <= score < 200
    MEDIUM("中危", 50),      // 与ThreatLevel阈值对齐: 50 <= score < 100
    LOW("低危", 10),         // 与ThreatLevel阈值对齐: 10 <= score < 50
    INFO("信息", 0);         // 与ThreatLevel阈值对齐: score < 10

    private final String description;
    private final int threshold;

    AlertSeverity(String description, int threshold) {
        this.description = description;
        this.threshold = threshold;
    }

    public String getDescription() {
        return description;
    }

    public int getThreshold() {
        return threshold;
    }

    /**
     * 根据分数确定严重程度
     */
    public static AlertSeverity fromScore(double score) {
        if (score >= CRITICAL.threshold) {
            return CRITICAL;
        } else if (score >= HIGH.threshold) {
            return HIGH;
        } else if (score >= MEDIUM.threshold) {
            return MEDIUM;
        } else if (score >= LOW.threshold) {
            return LOW;
        } else {
            return INFO;
        }
    }
}