package com.threatdetection.alert.service.escalation;

/**
 * 升级统计信息
 */
public class EscalationStatistics {

    private long totalEscalatedAlerts;
    private long level1Escalations;
    private long level2Escalations;
    private long level3PlusEscalations;

    public long getTotalEscalatedAlerts() {
        return totalEscalatedAlerts;
    }

    public void setTotalEscalatedAlerts(long totalEscalatedAlerts) {
        this.totalEscalatedAlerts = totalEscalatedAlerts;
    }

    public long getLevel1Escalations() {
        return level1Escalations;
    }

    public void setLevel1Escalations(long level1Escalations) {
        this.level1Escalations = level1Escalations;
    }

    public long getLevel2Escalations() {
        return level2Escalations;
    }

    public void setLevel2Escalations(long level2Escalations) {
        this.level2Escalations = level2Escalations;
    }

    public long getLevel3PlusEscalations() {
        return level3PlusEscalations;
    }

    public void setLevel3PlusEscalations(long level3PlusEscalations) {
        this.level3PlusEscalations = level3PlusEscalations;
    }

    @Override
    public String toString() {
        return "EscalationStatistics{" +
                "totalEscalatedAlerts=" + totalEscalatedAlerts +
                ", level1Escalations=" + level1Escalations +
                ", level2Escalations=" + level2Escalations +
                ", level3PlusEscalations=" + level3PlusEscalations +
                '}';
    }
}