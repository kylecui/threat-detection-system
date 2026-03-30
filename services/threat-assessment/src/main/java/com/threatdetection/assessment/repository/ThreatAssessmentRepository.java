package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.ThreatAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for ThreatAssessment entities
 * 
 * <p>对齐云原生威胁评估系统数据模型
 * <p>支持按客户ID、攻击MAC、威胁等级、时间范围查询
 */
@Repository
public interface ThreatAssessmentRepository extends JpaRepository<ThreatAssessment, Long> {

    /**
     * 按客户ID分页查询 (降序)
     */
    Page<ThreatAssessment> findByCustomerIdOrderByAssessmentTimeDesc(String customerId, Pageable pageable);

    Page<ThreatAssessment> findByCustomerIdInOrderByAssessmentTimeDesc(List<String> customerIds, Pageable pageable);

    /**
     * 按客户ID和威胁等级分页查询
     */
    Page<ThreatAssessment> findByCustomerIdAndThreatLevelOrderByAssessmentTimeDesc(
            String customerId, String threatLevel, Pageable pageable);

    /**
     * 按客户ID和攻击MAC分页查询
     */
    Page<ThreatAssessment> findByCustomerIdAndAttackMacOrderByAssessmentTimeDesc(
            String customerId, String attackMac, Pageable pageable);

    /**
     * 按客户ID和时间范围查询
     */
    List<ThreatAssessment> findByCustomerIdAndAssessmentTimeBetween(
            String customerId, Instant startTime, Instant endTime);

    /**
     * 按客户ID、威胁等级和时间范围查询
     */
    List<ThreatAssessment> findByCustomerIdAndThreatLevelAndAssessmentTimeBetween(
            String customerId, String threatLevel, Instant startTime, Instant endTime);

    /**
     * 统计指定客户的总威胁数
     */
    @Query("SELECT COUNT(ta) FROM ThreatAssessment ta WHERE ta.customerId = :customerId")
    long countByCustomerId(@Param("customerId") String customerId);

    @Query("SELECT COUNT(ta) FROM ThreatAssessment ta WHERE ta.customerId IN :customerIds")
    long countByCustomerIdIn(@Param("customerIds") List<String> customerIds);

    /**
     * 统计指定客户的特定等级威胁数
     */
    @Query("SELECT COUNT(ta) FROM ThreatAssessment ta WHERE ta.customerId = :customerId AND ta.threatLevel = :level")
    long countByCustomerIdAndLevel(@Param("customerId") String customerId, @Param("level") String level);

    @Query("SELECT COUNT(ta) FROM ThreatAssessment ta WHERE ta.customerId IN :customerIds AND ta.threatLevel = :level")
    long countByCustomerIdInAndLevel(@Param("customerIds") List<String> customerIds, @Param("level") String level);

    /**
     * 获取指定客户的平均威胁评分
     */
    @Query("SELECT AVG(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId = :customerId")
    Double getAverageThreatScore(@Param("customerId") String customerId);

    @Query("SELECT AVG(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId IN :customerIds")
    Double getAverageThreatScoreForCustomers(@Param("customerIds") List<String> customerIds);

    /**
     * 获取指定客户的最高威胁评分
     */
    @Query("SELECT MAX(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId = :customerId")
    Double getMaxThreatScore(@Param("customerId") String customerId);

    @Query("SELECT MAX(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId IN :customerIds")
    Double getMaxThreatScoreForCustomers(@Param("customerIds") List<String> customerIds);

    @Query("SELECT MIN(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId IN :customerIds")
    Double getMinThreatScoreForCustomers(@Param("customerIds") List<String> customerIds);

    /**
     * 获取指定客户的最低威胁评分
     */
    @Query("SELECT MIN(ta.threatScore) FROM ThreatAssessment ta WHERE ta.customerId = :customerId")
    Double getMinThreatScore(@Param("customerId") String customerId);

    /**
     * 按小时分组统计威胁趋势
     * 
     * <p>返回格式: [timestamp, count, avgScore, maxScore, criticalCount, highCount, mediumCount]
     */
    @Query(value = "SELECT " +
            "DATE_TRUNC('hour', assessment_time) as timestamp, " +
            "COUNT(*) as count, " +
            "AVG(threat_score) as avg_score, " +
            "MAX(threat_score) as max_score, " +
            "SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) as critical_count, " +
            "SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) as high_count, " +
            "SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) as medium_count " +
            "FROM threat_assessments " +
            "WHERE customer_id = :customerId " +
            "AND assessment_time BETWEEN :startTime AND :endTime " +
            "GROUP BY DATE_TRUNC('hour', assessment_time) " +
            "ORDER BY timestamp", 
            nativeQuery = true)
    List<Object[]> getHourlyTrend(@Param("customerId") String customerId, 
                                  @Param("startTime") Instant startTime, 
                                  @Param("endTime") Instant endTime);

    @Query(value = "SELECT " +
            "DATE_TRUNC('hour', assessment_time) as timestamp, " +
            "COUNT(*) as count, " +
            "AVG(threat_score) as avg_score, " +
            "MAX(threat_score) as max_score, " +
            "SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) as critical_count, " +
            "SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) as high_count, " +
            "SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) as medium_count " +
            "FROM threat_assessments " +
            "WHERE customer_id IN (:customerIds) " +
            "AND assessment_time BETWEEN :startTime AND :endTime " +
            "GROUP BY DATE_TRUNC('hour', assessment_time) " +
            "ORDER BY timestamp",
            nativeQuery = true)
    List<Object[]> getHourlyTrendForCustomers(@Param("customerIds") List<String> customerIds,
                                              @Param("startTime") Instant startTime,
                                              @Param("endTime") Instant endTime);

    /**
     * 获取最近24小时的威胁列表
     */
    @Query("SELECT ta FROM ThreatAssessment ta " +
           "WHERE ta.customerId = :customerId " +
           "AND ta.assessmentTime >= :since " +
           "ORDER BY ta.assessmentTime DESC")
    List<ThreatAssessment> findRecent24Hours(@Param("customerId") String customerId, 
                                             @Param("since") Instant since);

    @Query("SELECT ta FROM ThreatAssessment ta " +
           "WHERE ta.customerId IN :customerIds " +
           "AND ta.assessmentTime >= :since " +
           "ORDER BY ta.assessmentTime DESC")
    List<ThreatAssessment> findRecent24HoursForCustomers(@Param("customerIds") List<String> customerIds,
                                                          @Param("since") Instant since);
}
