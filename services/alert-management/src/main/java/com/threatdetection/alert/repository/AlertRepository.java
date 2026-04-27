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
    @Query(value = "SELECT * FROM alerts WHERE status = :status ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findByStatus(@Param("status") String status, Pageable pageable);

    /**
     * 根据严重程度查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE severity = :severity ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findBySeverity(@Param("severity") String severity, Pageable pageable);

    /**
     * 根据状态和严重程度查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE status = :status AND severity = :severity ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findByStatusAndSeverity(@Param("status") String status, @Param("severity") String severity, Pageable pageable);

    /**
     * 根据攻击MAC地址查找告警
     */
    List<Alert> findByAttackMac(String attackMac);

    /**
     * 根据时间范围查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE created_at BETWEEN :startTime AND :endTime ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);

    /**
     * 根据状态和时间范围查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE status = :status AND created_at BETWEEN :startTime AND :endTime ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findByStatusAndCreatedAtBetween(@Param("status") String status, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);

    /**
     * 根据严重程度和时间范围查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE severity = :severity AND created_at BETWEEN :startTime AND :endTime ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findBySeverityAndCreatedAtBetween(@Param("severity") String severity, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);

    /**
     * 根据源系统查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE source = :source ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findBySource(@Param("source") String source, Pageable pageable);

    /**
     * 查找所有告警（分页）
     */
    @Query(value = "SELECT * FROM alerts ORDER BY created_at DESC",
           nativeQuery = true)
    Page<Alert> findAllAlerts(Pageable pageable);

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
     * 根据客户ID查找告警（通过metadata中的customer_id）
     */
    @Query(value = "SELECT * FROM alerts WHERE (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId",
           nativeQuery = true)
    Page<Alert> findByCustomerId(@Param("customerId") String customerId, Pageable pageable);

    /**
     * 根据客户ID和状态查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId AND status = :status",
           nativeQuery = true)
    Page<Alert> findByCustomerIdAndStatus(@Param("customerId") String customerId,
                                         @Param("status") String status, Pageable pageable);

    /**
     * 根据客户ID和严重程度查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId AND severity = :severity",
           nativeQuery = true)
    Page<Alert> findByCustomerIdAndSeverity(@Param("customerId") String customerId,
                                           @Param("severity") String severity, Pageable pageable);

    /**
     * 根据客户ID、状态和严重程度查找告警
     */
    @Query(value = "SELECT * FROM alerts WHERE (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId AND status = :status AND severity = :severity",
           nativeQuery = true)
    Page<Alert> findByCustomerIdAndStatusAndSeverity(@Param("customerId") String customerId,
                                                     @Param("status") String status,
                                                     @Param("severity") String severity, Pageable pageable);

    /**
     * 按攻击MAC分组查询告警 (全局)
     */
    @Query(value = "SELECT attack_mac, " +
            "(CASE MAX(CASE severity WHEN 'CRITICAL' THEN 5 WHEN 'HIGH' THEN 4 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 2 WHEN 'INFO' THEN 1 ELSE 0 END) " +
            "WHEN 5 THEN 'CRITICAL' WHEN 4 THEN 'HIGH' WHEN 3 THEN 'MEDIUM' WHEN 2 THEN 'LOW' WHEN 1 THEN 'INFO' ELSE 'INFO' END) as max_severity, " +
            "MAX(threat_score) as max_threat_score, " +
            "COUNT(*) as alert_count, " +
            "COUNT(*) FILTER (WHERE status NOT IN ('RESOLVED', 'ARCHIVED')) as unresolved_count, " +
            "MAX(created_at) as latest_alert_time " +
            "FROM alerts " +
            "WHERE attack_mac IS NOT NULL " +
            "GROUP BY attack_mac " +
            "ORDER BY MAX(threat_score) DESC",
            countQuery = "SELECT COUNT(DISTINCT attack_mac) FROM alerts WHERE attack_mac IS NOT NULL",
            nativeQuery = true)
    Page<Object[]> findGroupedAlerts(Pageable pageable);

    /**
     * 按攻击MAC分组查询告警 (带状态过滤)
     */
    @Query(value = "SELECT attack_mac, " +
            "(CASE MAX(CASE severity WHEN 'CRITICAL' THEN 5 WHEN 'HIGH' THEN 4 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 2 WHEN 'INFO' THEN 1 ELSE 0 END) " +
            "WHEN 5 THEN 'CRITICAL' WHEN 4 THEN 'HIGH' WHEN 3 THEN 'MEDIUM' WHEN 2 THEN 'LOW' WHEN 1 THEN 'INFO' ELSE 'INFO' END) as max_severity, " +
            "MAX(threat_score) as max_threat_score, " +
            "COUNT(*) as alert_count, " +
            "COUNT(*) FILTER (WHERE status NOT IN ('RESOLVED', 'ARCHIVED')) as unresolved_count, " +
            "MAX(created_at) as latest_alert_time " +
            "FROM alerts " +
            "WHERE attack_mac IS NOT NULL AND status = :status " +
            "GROUP BY attack_mac " +
            "ORDER BY MAX(threat_score) DESC",
            countQuery = "SELECT COUNT(DISTINCT attack_mac) FROM alerts WHERE attack_mac IS NOT NULL AND status = :status",
            nativeQuery = true)
    Page<Object[]> findGroupedAlertsByStatus(@Param("status") String status, Pageable pageable);

    /**
     * 按攻击MAC分组查询告警 (按客户ID)
     */
    @Query(value = "SELECT attack_mac, " +
            "(CASE MAX(CASE severity WHEN 'CRITICAL' THEN 5 WHEN 'HIGH' THEN 4 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 2 WHEN 'INFO' THEN 1 ELSE 0 END) " +
            "WHEN 5 THEN 'CRITICAL' WHEN 4 THEN 'HIGH' WHEN 3 THEN 'MEDIUM' WHEN 2 THEN 'LOW' WHEN 1 THEN 'INFO' ELSE 'INFO' END) as max_severity, " +
            "MAX(threat_score) as max_threat_score, " +
            "COUNT(*) as alert_count, " +
            "COUNT(*) FILTER (WHERE status NOT IN ('RESOLVED', 'ARCHIVED')) as unresolved_count, " +
            "MAX(created_at) as latest_alert_time " +
            "FROM alerts " +
            "WHERE attack_mac IS NOT NULL AND (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId " +
            "GROUP BY attack_mac " +
            "ORDER BY MAX(threat_score) DESC",
            countQuery = "SELECT COUNT(DISTINCT attack_mac) FROM alerts WHERE attack_mac IS NOT NULL AND (CAST(metadata AS jsonb) ->> 'customer_id') = :customerId",
            nativeQuery = true)
    Page<Object[]> findGroupedAlertsByCustomerId(@Param("customerId") String customerId, Pageable pageable);

    /**
     * 查找相似告警（用于智能去重）
     */
    @Query("SELECT a FROM Alert a WHERE a.title LIKE %:titleKeyword% AND a.createdAt > :since")
    List<Alert> findSimilarAlerts(@Param("titleKeyword") String titleKeyword,
                                 @Param("since") LocalDateTime since);
}
