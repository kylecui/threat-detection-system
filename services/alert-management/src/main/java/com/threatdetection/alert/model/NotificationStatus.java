package com.threatdetection.alert.model;

/**
 * 通知状态枚举
 */
public enum NotificationStatus {
    PENDING("待发送"),
    SENT("已发送"),
    FAILED("发送失败"),
    RETRYING("重试中");

    private final String description;

    NotificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}