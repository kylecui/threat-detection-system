package com.threatdetection.alert.service.alert;

import com.threatdetection.alert.dto.GroupedAlertResponse;
import com.threatdetection.alert.model.*;
import com.threatdetection.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 告警管理核心服务
 */
@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DeduplicationService deduplicationService;

    /**
     * 创建新告警
     */
    @Transactional
    public Alert createAlert(Alert alert) {
        logger.info("Creating new alert: {}", alert.getTitle());

        // 检查是否为重复告警
        if (deduplicationService.isDuplicate(alert)) {
            logger.info("Alert is duplicate, marking as deduplicated");
            alert.setStatus(AlertStatus.DEDUPLICATED);
        } else {
            alert.setStatus(AlertStatus.NEW);
        }

        // 注意: severity已在KafkaConsumerService中从threatLevel正确映射，不再重新计算
        // 如果未设置severity但有威胁分数，则作为fallback自动确定严重程度
        if (alert.getSeverity() == null && alert.getThreatScore() != null) {
            alert.setSeverity(AlertSeverity.fromScore(alert.getThreatScore()));
            logger.warn("Severity not set, calculated from score: {}", alert.getSeverity());
        }

        Alert savedAlert = alertRepository.save(alert);
        logger.info("Alert created with ID: {}", savedAlert.getId());

        return savedAlert;
    }

    /**
     * 批量保存告警（性能优化）
     * 使用JPA的saveAll批量插入，减少数据库往返次数
     */
    @Transactional
    public List<Alert> saveAllAlerts(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Batch saving {} alerts", alerts.size());

        // 设置默认状态和严重程度
        for (Alert alert : alerts) {
            if (alert.getStatus() == null) {
                alert.setStatus(AlertStatus.NEW);
            }
            
            // 注意: severity已在KafkaConsumerService中从threatLevel正确映射
            // 这里只作为fallback处理
            if (alert.getSeverity() == null && alert.getThreatScore() != null) {
                alert.setSeverity(AlertSeverity.fromScore(alert.getThreatScore()));
                logger.warn("Severity not set for alert {}, calculated from score: {}", 
                           alert.getTitle(), alert.getSeverity());
            }
        }

        // 批量保存
        List<Alert> savedAlerts = alertRepository.saveAll(alerts);
        logger.info("Successfully saved {} alerts in batch", savedAlerts.size());

        return savedAlerts;
    }

    /**
     * 根据ID查找告警
     */
    public Optional<Alert> findById(Long id) {
        return alertRepository.findById(id);
    }

    /**
     * 更新告警状态
     */
    @Transactional
    public Alert updateStatus(Long id, AlertStatus newStatus) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        logger.info("Updating alert {} status from {} to {}", id, alert.getStatus(), newStatus);
        alert.setStatus(newStatus);

        return alertRepository.save(alert);
    }

    /**
     * 解决告警
     */
    @Transactional
    public Alert resolveAlert(Long id, String resolution, String resolvedBy) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        logger.info("Resolving alert {} by {}", id, resolvedBy);
        alert.resolve(resolution, resolvedBy);

        return alertRepository.save(alert);
    }

    /**
     * 分配告警
     */
    @Transactional
    public Alert assignAlert(Long id, String assignedTo) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        logger.info("Assigning alert {} to {}", id, assignedTo);
        alert.setAssignedTo(assignedTo);

        return alertRepository.save(alert);
    }

    /**
     * 查找告警（分页）
     */
    public Page<Alert> findAlerts(String customerId, AlertStatus status, AlertSeverity severity,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 Pageable pageable) {
        if (customerId != null) {
            // 有customerId过滤
            String statusStr = status != null ? status.name() : null;
            String severityStr = severity != null ? severity.name() : null;

            if (statusStr != null && severityStr != null) {
                return alertRepository.findByCustomerIdAndStatusAndSeverity(customerId, statusStr, severityStr, pageable);
            } else if (statusStr != null) {
                return alertRepository.findByCustomerIdAndStatus(customerId, statusStr, pageable);
            } else if (severityStr != null) {
                return alertRepository.findByCustomerIdAndSeverity(customerId, severityStr, pageable);
            } else {
                return alertRepository.findByCustomerId(customerId, pageable);
            }
        } else {
            // 无customerId过滤，使用原有逻辑
            if (status != null && severity != null) {
                return alertRepository.findByStatusAndSeverity(status.name(), severity.name(), pageable);
            } else if (status != null) {
                return alertRepository.findByStatus(status.name(), pageable);
            } else if (severity != null) {
                return alertRepository.findBySeverity(severity.name(), pageable);
            } else if (startTime != null && endTime != null) {
                return alertRepository.findByCreatedAtBetween(startTime, endTime, pageable);
            } else {
                return alertRepository.findAllAlerts(pageable);
            }
        }
    }

    /**
     * 查找需要升级的告警
     */
    public List<Alert> findAlertsNeedingEscalation(int unacknowledgedMinutes) {
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(unacknowledgedMinutes);
        return alertRepository.findAlertsNeedingEscalation(thresholdTime);
    }

    /**
     * 获取告警统计信息
     */
    public AlertStatistics getAlertStatistics() {
        AlertStatistics stats = new AlertStatistics();

        // 按状态统计
        List<Object[]> statusStats = alertRepository.countAlertsByStatus();
        for (Object[] stat : statusStats) {
            AlertStatus status = (AlertStatus) stat[0];
            Long count = (Long) stat[1];
            stats.getByStatus().put(status, count);
        }

        // 按严重程度统计
        List<Object[]> severityStats = alertRepository.countAlertsBySeverity();
        for (Object[] stat : severityStats) {
            AlertSeverity severity = (AlertSeverity) stat[0];
            Long count = (Long) stat[1];
            stats.getBySeverity().put(severity, count);
        }

        // 其他统计
        stats.setTotalAlerts(alertRepository.count());
        stats.setUnresolvedAlerts(alertRepository.findUnresolvedAlerts().size());
        stats.setAverageResolutionTime(alertRepository.calculateAverageResolutionTime());

        return stats;
    }

    /**
     * 归档旧告警
     */
    public int archiveOldAlerts(int daysOld) {
        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(daysOld);

        List<Alert> oldAlerts = alertRepository.findByCreatedAtBetween(
                LocalDateTime.MIN, thresholdDate, Pageable.unpaged()).getContent();

        int archivedCount = 0;
        for (Alert alert : oldAlerts) {
            if (alert.getStatus() == AlertStatus.RESOLVED) {
                alert.setStatus(AlertStatus.ARCHIVED);
                alertRepository.save(alert);
                archivedCount++;
            }
        }

        logger.info("Archived {} old alerts", archivedCount);
        return archivedCount;
    }

    /**
     * 获取按设备分组的告警列表
     */
    public Page<GroupedAlertResponse> findGroupedAlerts(String customerId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> rawPage;

        if (customerId != null && !customerId.isBlank()) {
            rawPage = alertRepository.findGroupedAlertsByCustomerId(customerId, pageable);
        } else if (status != null && !status.isBlank()) {
            rawPage = alertRepository.findGroupedAlertsByStatus(status, pageable);
        } else {
            rawPage = alertRepository.findGroupedAlerts(pageable);
        }

        return rawPage.map(this::convertToGroupedResponse);
    }

    private GroupedAlertResponse convertToGroupedResponse(Object[] row) {
        String attackMac = row[0] != null ? String.valueOf(row[0]) : null;
        String maxSeverity = row[1] != null ? String.valueOf(row[1]) : null;

        double maxThreatScore;
        if (row[2] instanceof java.math.BigDecimal bd) {
            maxThreatScore = bd.doubleValue();
        } else if (row[2] instanceof Number n) {
            maxThreatScore = n.doubleValue();
        } else {
            maxThreatScore = 0.0;
        }

        long alertCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
        long unresolvedCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;

        LocalDateTime latestAlertTime = null;
        if (row[5] instanceof java.sql.Timestamp ts) {
            latestAlertTime = ts.toLocalDateTime();
        } else if (row[5] instanceof LocalDateTime ldt) {
            latestAlertTime = ldt;
        }

        return GroupedAlertResponse.builder()
                .attackMac(attackMac)
                .maxSeverity(maxSeverity)
                .maxThreatScore(maxThreatScore)
                .alertCount(alertCount)
                .unresolvedCount(unresolvedCount)
                .latestAlertTime(latestAlertTime)
                .build();
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        logger.info("Cleared alert cache");
    }
}
