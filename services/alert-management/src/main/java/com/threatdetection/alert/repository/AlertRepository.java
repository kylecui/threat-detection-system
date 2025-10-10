package com.threatdetection.alert.repository;

import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警数据访问层
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 根据状态查找告警
     */
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    /**
     * 根据严重程度查找告警
     */
    Page<Alert> findBySeverity(AlertSeverity severity, Pageable pageable);

    /**
     * 根据状态和严重程度查找告警
     */
    Page<Alert> findByStatusAndSeverity(AlertStatus status, AlertSeverity severity, Pageable pageable);

    /**
     * 根据攻击MAC地址查找告警
     */
    List<Alert> findByAttackMac(String attackMac);

    /**
     * 根据时间范围查找告警
     */
    Page<Alert> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据状态和时间范围查找告警
     */
    Page<Alert> findByStatusAndCreatedAtBetween(AlertStatus status, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据严重程度和时间范围查找告警
     */
    Page<Alert> findBySeverityAndCreatedAtBetween(AlertSeverity severity, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据源系统查找告警
     */
    Page<Alert> findBySource(String source, Pageable pageable);

    /**
     * 查找需要升级的告警（未解决且未通知超过指定时间）
     */
    @Query("SELECT a FROM Alert a WHERE a.status != 'RESOLVED' AND a.status != 'ARCHIVED' " +
           "AND a.lastNotifiedAt IS NOT NULL AND a.lastNotifiedAt < :thresholdTime")
    List<Alert> findAlertsNeedingEscalation(@Param("thresholdTime") LocalDateTime thresholdTime);

    /**
     * 查找未解决的告警
     */
    @Query("SELECT a FROM Alert a WHERE a.status NOT IN ('RESOLVED', 'ARCHIVED')")
    List<Alert> findUnresolvedAlerts();

    /**
     * 统计告警数量按状态分组
     */
    @Query("SELECT a.status, COUNT(a) FROM Alert a GROUP BY a.status")
    List<Object[]> countAlertsByStatus();

    /**
     * 统计告警数量按严重程度分组
     */
    @Query("SELECT a.severity, COUNT(a) FROM Alert a GROUP BY a.severity")
    List<Object[]> countAlertsBySeverity();

    /**
     * 统计告警数量按源系统分组
     */
    @Query("SELECT a.source, COUNT(a) FROM Alert a GROUP BY a.source")
    List<Object[]> countAlertsBySource();

    /**
     * 计算平均解决时间（分钟）
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, a.createdAt, a.resolvedAt)) FROM Alert a WHERE a.resolvedAt IS NOT NULL")
    Double calculateAverageResolutionTime();

    /**
     * 查找最近的告警（用于去重检查）
     */
    @Query("SELECT a FROM Alert a WHERE a.attackMac = :attackMac AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<Alert> findRecentAlertsByAttackMac(@Param("attackMac") String attackMac,
                                           @Param("since") LocalDateTime since);

    /**
     * 查找相似告警（用于智能去重）
     */
    @Query("SELECT a FROM Alert a WHERE a.title LIKE %:titleKeyword% AND a.createdAt > :since")
    List<Alert> findSimilarAlerts(@Param("titleKeyword") String titleKeyword,
                                 @Param("since") LocalDateTime since);
}