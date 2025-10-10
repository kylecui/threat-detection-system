package com.threatdetection.alert.service.escalation;

import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警升级服务
 */
@Service
public class EscalationService {

    private static final Logger logger = LoggerFactory.getLogger(EscalationService.class);

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @Value("${alert.escalation.check-interval:60}")
    private int checkIntervalSeconds;

    @Value("${alert.escalation.critical.unacknowledged-time:300}")
    private int criticalUnacknowledgedTime;

    @Value("${alert.escalation.high.unacknowledged-time:900}")
    private int highUnacknowledgedTime;

    @Value("${alert.escalation.medium.unacknowledged-time:1800}")
    private int mediumUnacknowledgedTime;

    /**
     * 检查并升级需要升级的告警
     * 每分钟执行一次
     */
    @Scheduled(fixedRateString = "${alert.escalation.check-interval:60}000")
    public void checkAndEscalateAlerts() {
        logger.debug("Checking for alerts that need escalation");

        try {
            // 检查不同严重程度的告警
            escalateCriticalAlerts();
            escalateHighAlerts();
            escalateMediumAlerts();

        } catch (Exception e) {
            logger.error("Error during escalation check: {}", e.getMessage(), e);
        }
    }

    /**
     * 升级严重告警
     */
    private void escalateCriticalAlerts() {
        List<Alert> criticalAlerts = alertService.findAlertsNeedingEscalation(criticalUnacknowledgedTime);

        for (Alert alert : criticalAlerts) {
            if (alert.getSeverity() == AlertSeverity.CRITICAL && !alert.isEscalated()) {
                escalateAlert(alert, 1, "Critical alert unacknowledged for " + criticalUnacknowledgedTime + " seconds");
            }
        }
    }

    /**
     * 升级高危告警
     */
    private void escalateHighAlerts() {
        List<Alert> highAlerts = alertService.findAlertsNeedingEscalation(highUnacknowledgedTime);

        for (Alert alert : highAlerts) {
            if (alert.getSeverity() == AlertSeverity.HIGH && !alert.isEscalated()) {
                escalateAlert(alert, 1, "High alert unacknowledged for " + highUnacknowledgedTime + " seconds");
            }
        }
    }

    /**
     * 升级中危告警
     */
    private void escalateMediumAlerts() {
        List<Alert> mediumAlerts = alertService.findAlertsNeedingEscalation(mediumUnacknowledgedTime);

        for (Alert alert : mediumAlerts) {
            if (alert.getSeverity() == AlertSeverity.MEDIUM && !alert.isEscalated()) {
                escalateAlert(alert, 1, "Medium alert unacknowledged for " + mediumUnacknowledgedTime + " seconds");
            }
        }
    }

    /**
     * 升级告警
     */
    private void escalateAlert(Alert alert, int level, String reason) {
        logger.info("Escalating alert {} to level {}: {}", alert.getId(), level, reason);

        alert.setEscalationLevel(level);
        alert.setEscalationReason(reason);

        // 保存升级后的告警
        alertService.updateStatus(alert.getId(), alert.getStatus());

        // 发送升级通知
        sendEscalationNotification(alert, level, reason);
    }

    /**
     * 发送升级通知
     */
    private void sendEscalationNotification(Alert alert, int level, String reason) {
        // 这里应该根据配置发送升级通知给更高权限的用户
        // 暂时记录日志
        logger.warn("ALERT ESCALATION: {} (ID: {}) escalated to level {} - {}",
                   alert.getTitle(), alert.getId(), level, reason);

        // TODO: 实现实际的升级通知逻辑
        // 例如：发送邮件给安全团队领导、创建工单等
    }

    /**
     * 手动升级告警
     */
    public void manuallyEscalateAlert(Long alertId, String reason) {
        Alert alert = alertService.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        int newLevel = (alert.getEscalationLevel() != null) ? alert.getEscalationLevel() + 1 : 1;

        logger.info("Manually escalating alert {} to level {}", alertId, newLevel);
        escalateAlert(alert, newLevel, reason);
    }

    /**
     * 取消告警升级
     */
    public void cancelEscalation(Long alertId) {
        Alert alert = alertService.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        logger.info("Canceling escalation for alert {}", alertId);

        alert.setEscalationLevel(null);
        alert.setEscalationReason(null);

        alertService.updateStatus(alert.getId(), alert.getStatus());
    }

    /**
     * 获取升级统计
     */
    public EscalationStatistics getEscalationStatistics() {
        EscalationStatistics stats = new EscalationStatistics();

        List<Alert> escalatedAlerts = alertService.findAlertsNeedingEscalation(0)
                .stream()
                .filter(Alert::isEscalated)
                .toList();

        stats.setTotalEscalatedAlerts(escalatedAlerts.size());

        // 按升级级别统计
        long level1Count = escalatedAlerts.stream()
                .filter(alert -> alert.getEscalationLevel() != null && alert.getEscalationLevel() == 1)
                .count();
        long level2Count = escalatedAlerts.stream()
                .filter(alert -> alert.getEscalationLevel() != null && alert.getEscalationLevel() == 2)
                .count();
        long level3PlusCount = escalatedAlerts.stream()
                .filter(alert -> alert.getEscalationLevel() != null && alert.getEscalationLevel() >= 3)
                .count();

        stats.setLevel1Escalations(level1Count);
        stats.setLevel2Escalations(level2Count);
        stats.setLevel3PlusEscalations(level3PlusCount);

        return stats;
    }

    /**
     * 设置升级参数
     */
    public void setCriticalUnacknowledgedTime(int criticalUnacknowledgedTime) {
        this.criticalUnacknowledgedTime = criticalUnacknowledgedTime;
    }

    public void setHighUnacknowledgedTime(int highUnacknowledgedTime) {
        this.highUnacknowledgedTime = highUnacknowledgedTime;
    }

    public void setMediumUnacknowledgedTime(int mediumUnacknowledgedTime) {
        this.mediumUnacknowledgedTime = mediumUnacknowledgedTime;
    }
}