package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.RiskLevel;
import com.threatdetection.assessment.model.ThreatAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ThreatAssessment entities
 */
@Repository
public interface ThreatAssessmentRepository extends JpaRepository<ThreatAssessment, Long> {

    Optional<ThreatAssessment> findByAssessmentId(String assessmentId);

    Optional<ThreatAssessment> findByAlertId(String alertId);

    List<ThreatAssessment> findByRiskLevel(RiskLevel riskLevel);

    List<ThreatAssessment> findByAssessmentTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT ta FROM ThreatAssessment ta WHERE ta.assessmentTimestamp >= :since ORDER BY ta.assessmentTimestamp DESC")
    List<ThreatAssessment> findRecentAssessments(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ta) FROM ThreatAssessment ta WHERE ta.riskLevel = :riskLevel AND ta.assessmentTimestamp >= :since")
    long countByRiskLevelSince(@Param("riskLevel") RiskLevel riskLevel, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(ta.riskScore) FROM ThreatAssessment ta WHERE ta.assessmentTimestamp >= :since")
    Double getAverageRiskScoreSince(@Param("since") LocalDateTime since);

    @Query("SELECT ta FROM ThreatAssessment ta WHERE ta.assessmentTimestamp BETWEEN :startTime AND :endTime ORDER BY ta.assessmentTimestamp")
    List<ThreatAssessment> findAssessmentsInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);
}