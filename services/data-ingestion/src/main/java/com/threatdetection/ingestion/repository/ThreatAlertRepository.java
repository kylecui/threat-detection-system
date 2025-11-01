package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.ThreatAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 威胁告警Repository
 */
@Repository
public interface ThreatAlertRepository extends JpaRepository<ThreatAlertEntity, Long> {
    
    /**
     * 根据客户ID查询告警
     */
    List<ThreatAlertEntity> findByCustomerIdOrderByAlertTimestampDesc(String customerId);
    
    /**
     * 根据威胁等级查询告警
     */
    List<ThreatAlertEntity> findByThreatLevelOrderByAlertTimestampDesc(String threatLevel);
    
    /**
     * 根据客户ID和威胁等级查询告警
     */
    List<ThreatAlertEntity> findByCustomerIdAndThreatLevelOrderByAlertTimestampDesc(
        String customerId, String threatLevel);
    
    /**
     * 根据客户ID和状态查询告警
     */
    List<ThreatAlertEntity> findByCustomerIdAndStatusOrderByAlertTimestampDesc(
        String customerId, String status);
    
    /**
     * 查询最近的N条告警
     */
    @Query("SELECT a FROM ThreatAlertEntity a " +
           "WHERE a.customerId = :customerId " +
           "ORDER BY a.alertTimestamp DESC " +
           "LIMIT :limit")
    List<ThreatAlertEntity> findRecentAlerts(
        @Param("customerId") String customerId,
        @Param("limit") int limit);
    
    /**
     * 统计客户的告警数量(按等级)
     */
    @Query("SELECT a.threatLevel, COUNT(a) FROM ThreatAlertEntity a " +
           "WHERE a.customerId = :customerId " +
           "AND a.alertTimestamp >= :since " +
           "GROUP BY a.threatLevel")
    List<Object[]> countAlertsByLevel(
        @Param("customerId") String customerId,
        @Param("since") Instant since);
}
