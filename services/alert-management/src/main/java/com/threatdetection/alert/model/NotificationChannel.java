package com.threatdetection.alert.model;

/**
 * 通知通道枚举
 */
public enum NotificationChannel {
    EMAIL("邮件"),
    SMS("短信"),
    WEBHOOK("Webhook"),
    SLACK("Slack"),
    TEAMS("Teams");

    private final String description;

    NotificationChannel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}