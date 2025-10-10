package com.threatdetection.alert.service.alert;

import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警统计信息
 */
public class AlertStatistics {

    private long totalAlerts;
    private int unresolvedAlerts;
    private Double averageResolutionTime;
    private final Map<AlertStatus, Long> byStatus = new HashMap<>();
    private final Map<AlertSeverity, Long> bySeverity = new HashMap<>();

    public long getTotalAlerts() {
        return totalAlerts;
    }

    public void setTotalAlerts(long totalAlerts) {
        this.totalAlerts = totalAlerts;
    }

    public int getUnresolvedAlerts() {
        return unresolvedAlerts;
    }

    public void setUnresolvedAlerts(int unresolvedAlerts) {
        this.unresolvedAlerts = unresolvedAlerts;
    }

    public Double getAverageResolutionTime() {
        return averageResolutionTime;
    }

    public void setAverageResolutionTime(Double averageResolutionTime) {
        this.averageResolutionTime = averageResolutionTime;
    }

    public Map<AlertStatus, Long> getByStatus() {
        return byStatus;
    }

    public Map<AlertSeverity, Long> getBySeverity() {
        return bySeverity;
    }

    @Override
    public String toString() {
        return "AlertStatistics{" +
                "totalAlerts=" + totalAlerts +
                ", unresolvedAlerts=" + unresolvedAlerts +
                ", averageResolutionTime=" + averageResolutionTime +
                ", byStatus=" + byStatus +
                ", bySeverity=" + bySeverity +
                '}';
    }
}