package com.threatdetection.alert.model;

/**
 * 告警状态枚举
 */
public enum AlertStatus {
    NEW("待处置"),
    IN_PROGRESS("处理中"),
    DEDUPLICATED("去重"),
    ENRICHED("已丰富"),
    NOTIFIED("已通知"),
    ESCALATED("已提级"),
    RESOLVED("已处置"),
    ARCHIVED("已归档");

    private final String description;

    AlertStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
