package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.ThreatAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ThreatAlert entities
 */
@Repository
public interface ThreatAlertRepository extends JpaRepository<ThreatAlert, Long> {

    Optional<ThreatAlert> findByAlertId(String alertId);

    List<ThreatAlert> findByProcessedFalse();

    List<ThreatAlert> findByAttackMacAndTimestampBetween(String attackMac,
                                                         LocalDateTime startTime,
                                                         LocalDateTime endTime);

    @Query("SELECT ta FROM ThreatAlert ta WHERE ta.timestamp BETWEEN :startTime AND :endTime")
    List<ThreatAlert> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(ta) FROM ThreatAlert ta WHERE ta.attackMac = :attackMac AND ta.timestamp >= :since")
    long countRecentAlertsByMac(@Param("attackMac") String attackMac,
                               @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT ta.attackMac FROM ThreatAlert ta WHERE ta.timestamp >= :since")
    List<String> findActiveAttackersSince(@Param("since") LocalDateTime since);
}