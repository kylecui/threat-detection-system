package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.model.NotificationChannel;
import com.threatdetection.alert.model.NotificationStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知统计信息
 */
public class NotificationStatistics {

    private long totalNotifications;
    private long successfulNotifications;
    private long failedNotifications;
    private final Map<NotificationChannel, Long> byChannel = new HashMap<>();
    private final Map<NotificationStatus, Long> byStatus = new HashMap<>();

    public long getTotalNotifications() {
        return totalNotifications;
    }

    public void setTotalNotifications(long totalNotifications) {
        this.totalNotifications = totalNotifications;
    }

    public long getSuccessfulNotifications() {
        return successfulNotifications;
    }

    public void setSuccessfulNotifications(long successfulNotifications) {
        this.successfulNotifications = successfulNotifications;
    }

    public long getFailedNotifications() {
        return failedNotifications;
    }

    public void setFailedNotifications(long failedNotifications) {
        this.failedNotifications = failedNotifications;
    }

    public Map<NotificationChannel, Long> getByChannel() {
        return byChannel;
    }

    public Map<NotificationStatus, Long> getByStatus() {
        return byStatus;
    }

    public double getSuccessRate() {
        return totalNotifications > 0 ? (double) successfulNotifications / totalNotifications : 0.0;
    }

    @Override
    public String toString() {
        return "NotificationStatistics{" +
                "totalNotifications=" + totalNotifications +
                ", successfulNotifications=" + successfulNotifications +
                ", failedNotifications=" + failedNotifications +
                ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
                ", byChannel=" + byChannel +
                ", byStatus=" + byStatus +
                '}';
    }
}