package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.ThreatAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface ThreatAssessmentRepository extends JpaRepository<ThreatAssessment, Long>, JpaSpecificationExecutor<ThreatAssessment> {

    @Query(value = "SELECT EXISTS (SELECT 1 FROM threat_assessments ta " +
            "JOIN customers c ON c.customer_id = ta.customer_id " +
            "WHERE ta.id = :assessmentId AND c.tenant_id = :tenantId)",
            nativeQuery = true)
    boolean existsByIdAndTenantId(@Param("assessmentId") Long assessmentId,
                                  @Param("tenantId") Long tenantId);

    java.util.Optional<ThreatAssessment> findFirstByCustomerIdAndAttackMacIgnoreCaseAndDetectionTierAndWindowStart(
            String customerId, String attackMac, Integer detectionTier, Instant windowStart);

    /**
     * Return all overview counters in one database scan. The column order is:
     * total, critical, high, medium, low, info, average, maximum, minimum.
     */
    @Query(value = "SELECT " +
            "COUNT(*) AS total_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'CRITICAL') AS critical_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'HIGH') AS high_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'MEDIUM') AS medium_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'LOW') AS low_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'INFO') AS info_count, " +
            "COALESCE(AVG(threat_score), 0) AS average_score, " +
            "COALESCE(MAX(threat_score), 0) AS maximum_score, " +
            "COALESCE(MIN(threat_score), 0) AS minimum_score " +
            "FROM threat_assessments WHERE customer_id = :customerId",
            nativeQuery = true)
    Object[] getStatisticsAggregate(@Param("customerId") String customerId);

    @Query(value = "SELECT " +
            "COUNT(*) AS total_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'CRITICAL') AS critical_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'HIGH') AS high_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'MEDIUM') AS medium_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'LOW') AS low_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'INFO') AS info_count, " +
            "COALESCE(AVG(threat_score), 0) AS average_score, " +
            "COALESCE(MAX(threat_score), 0) AS maximum_score, " +
            "COALESCE(MIN(threat_score), 0) AS minimum_score " +
            "FROM threat_assessments WHERE customer_id IN (:customerIds)",
            nativeQuery = true)
    Object[] getStatisticsAggregateForCustomers(@Param("customerIds") List<String> customerIds);

    @Query(value = "SELECT " +
            "COUNT(*) AS total_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'CRITICAL') AS critical_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'HIGH') AS high_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'MEDIUM') AS medium_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'LOW') AS low_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'INFO') AS info_count, " +
            "COALESCE(AVG(threat_score), 0) AS average_score, " +
            "COALESCE(MAX(threat_score), 0) AS maximum_score, " +
            "COALESCE(MIN(threat_score), 0) AS minimum_score " +
            "FROM threat_assessments ta " +
            "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId)",
            nativeQuery = true)
    Object[] getStatisticsAggregateForTenant(@Param("tenantId") long tenantId);

    @Query(value = "SELECT COUNT(*) AS total_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'CRITICAL') AS critical_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'HIGH') AS high_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'MEDIUM') AS medium_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'LOW') AS low_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'INFO') AS info_count, " +
            "COALESCE(AVG(threat_score), 0) AS average_score, " +
            "COALESCE(MAX(threat_score), 0) AS maximum_score, " +
            "COALESCE(MIN(threat_score), 0) AS minimum_score " +
            "FROM threat_assessments WHERE customer_id = :customerId AND assessment_time >= :since",
            nativeQuery = true)
    Object[] getStatisticsAggregateSince(@Param("customerId") String customerId,
                                         @Param("since") Instant since);

    @Query(value = "SELECT COUNT(*) AS total_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'CRITICAL') AS critical_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'HIGH') AS high_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'MEDIUM') AS medium_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'LOW') AS low_count, " +
            "COUNT(*) FILTER (WHERE threat_level = 'INFO') AS info_count, " +
            "COALESCE(AVG(threat_score), 0) AS average_score, " +
            "COALESCE(MAX(threat_score), 0) AS maximum_score, " +
            "COALESCE(MIN(threat_score), 0) AS minimum_score " +
            "FROM threat_assessments ta WHERE EXISTS " +
            "(SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            "AND assessment_time >= :since",
            nativeQuery = true)
    Object[] getStatisticsAggregateForTenantSince(@Param("tenantId") long tenantId,
                                                  @Param("since") Instant since);

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

    @Query(value = "SELECT DATE_TRUNC('hour', assessment_time) AS timestamp, " +
            "COUNT(*) AS count, AVG(threat_score) AS avg_score, MAX(threat_score) AS max_score, " +
            "SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count, " +
            "SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) AS medium_count " +
            "FROM threat_assessments ta " +
            "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            "AND assessment_time BETWEEN :startTime AND :endTime " +
            "GROUP BY DATE_TRUNC('hour', assessment_time) ORDER BY timestamp",
            nativeQuery = true)
    List<Object[]> getHourlyTrendForTenant(@Param("tenantId") long tenantId,
                                            @Param("startTime") Instant startTime,
                                            @Param("endTime") Instant endTime);

    @Query(value = "SELECT DATE_TRUNC('day', assessment_time) AS timestamp, " +
            "COUNT(*) AS count, AVG(threat_score) AS avg_score, MAX(threat_score) AS max_score, " +
            "SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count, " +
            "SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) AS medium_count " +
            "FROM threat_assessments WHERE customer_id = :customerId " +
            "AND assessment_time BETWEEN :startTime AND :endTime " +
            "GROUP BY DATE_TRUNC('day', assessment_time) ORDER BY timestamp",
            nativeQuery = true)
    List<Object[]> getDailyTrend(@Param("customerId") String customerId,
                                 @Param("startTime") Instant startTime,
                                 @Param("endTime") Instant endTime);

    @Query(value = "SELECT DATE_TRUNC('day', assessment_time) AS timestamp, " +
            "COUNT(*) AS count, AVG(threat_score) AS avg_score, MAX(threat_score) AS max_score, " +
            "SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count, " +
            "SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) AS medium_count " +
            "FROM threat_assessments ta " +
            "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            "AND assessment_time BETWEEN :startTime AND :endTime " +
            "GROUP BY DATE_TRUNC('day', assessment_time) ORDER BY timestamp",
            nativeQuery = true)
    List<Object[]> getDailyTrendForTenant(@Param("tenantId") long tenantId,
                                          @Param("startTime") Instant startTime,
                                          @Param("endTime") Instant endTime);

    @Query(value = "SELECT grouped.port, grouped.port_count, SUM(grouped.port_count) OVER () AS total_count " +
            "FROM (" +
            "  SELECT ae.response_port AS port, " +
            "         COUNT(*) AS port_count " +
            "  FROM attack_events ae " +
            "  WHERE ae.customer_id = :customerId AND ae.event_timestamp >= :since " +
            "    AND ae.response_port BETWEEN 1 AND 65535 " +
            "  GROUP BY ae.response_port" +
            ") grouped ORDER BY grouped.port_count DESC, grouped.port ASC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> getPortDistributionRows(@Param("customerId") String customerId,
                                            @Param("since") Instant since,
                                            @Param("limit") int limit);

    @Query(value = "SELECT grouped.port, grouped.port_count, SUM(grouped.port_count) OVER () AS total_count " +
            "FROM (" +
            "  SELECT ae.response_port AS port, " +
            "         COUNT(*) AS port_count " +
            "  FROM attack_events ae " +
            "  WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ae.customer_id AND c.tenant_id = :tenantId) " +
            "    AND ae.event_timestamp >= :since " +
            "    AND ae.response_port BETWEEN 1 AND 65535 " +
            "  GROUP BY ae.response_port" +
            ") grouped ORDER BY grouped.port_count DESC, grouped.port ASC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> getPortDistributionRowsForTenant(@Param("tenantId") long tenantId,
                                                     @Param("since") Instant since,
                                                     @Param("limit") int limit);

    @Query(value = "SELECT grouped.port, grouped.port_count, SUM(grouped.port_count) OVER () AS total_count " +
            "FROM (" +
            "  SELECT ae.response_port AS port, " +
            "         COUNT(*) AS port_count " +
            "  FROM attack_events ae " +
            "  WHERE ae.customer_id IN (:customerIds) AND ae.event_timestamp >= :since " +
            "    AND ae.response_port BETWEEN 1 AND 65535 " +
            "  GROUP BY ae.response_port" +
            ") grouped ORDER BY grouped.port_count DESC, grouped.port ASC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> getPortDistributionRowsForCustomers(@Param("customerIds") List<String> customerIds,
                                                        @Param("since") Instant since,
                                                        @Param("limit") int limit);

    @Query(value = "SELECT protocol_name, COUNT(*) AS protocol_count FROM (" +
            " SELECT CASE " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'eth_type', ae.raw_log_data->>'ethType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'eth_type', ae.raw_log_data->>'ethType') AS integer) END, 0) = 2054 THEN 'ARP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 1 THEN 'ICMP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 58 THEN 'ICMPv6' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 47 THEN 'GRE' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 50 THEN 'ESP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 51 THEN 'AH' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) IN (6,17) THEN 'PORT_ANOMALY' " +
            "  ELSE 'OTHER' END AS protocol_name " +
            " FROM attack_events ae WHERE ae.customer_id = :customerId " +
            " AND ae.event_timestamp >= :since AND COALESCE(ae.response_port, 0) NOT BETWEEN 1 AND 65535" +
            ") classified GROUP BY protocol_name ORDER BY protocol_count DESC, protocol_name",
            nativeQuery = true)
    List<Object[]> getNonPortProtocolRows(@Param("customerId") String customerId,
                                          @Param("since") Instant since);

    @Query(value = "SELECT protocol_name, COUNT(*) AS protocol_count FROM (" +
            " SELECT CASE " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'eth_type', ae.raw_log_data->>'ethType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'eth_type', ae.raw_log_data->>'ethType') AS integer) END, 0) = 2054 THEN 'ARP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 1 THEN 'ICMP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 58 THEN 'ICMPv6' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 47 THEN 'GRE' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 50 THEN 'ESP' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) = 51 THEN 'AH' " +
            "  WHEN COALESCE(CASE WHEN COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType', '') ~ '^[0-9]+$' THEN CAST(COALESCE(ae.raw_log_data->>'ip_type', ae.raw_log_data->>'ipType') AS integer) END, 0) IN (6,17) THEN 'PORT_ANOMALY' " +
            "  ELSE 'OTHER' END AS protocol_name " +
            " FROM attack_events ae WHERE EXISTS " +
            " (SELECT 1 FROM customers c WHERE c.customer_id = ae.customer_id AND c.tenant_id = :tenantId) " +
            " AND ae.event_timestamp >= :since AND COALESCE(ae.response_port, 0) NOT BETWEEN 1 AND 65535" +
            ") classified GROUP BY protocol_name ORDER BY protocol_count DESC, protocol_name",
            nativeQuery = true)
    List<Object[]> getNonPortProtocolRowsForTenant(@Param("tenantId") long tenantId,
                                                   @Param("since") Instant since);

    @Query(value = "SELECT latest.* FROM (" +
            " SELECT DISTINCT ON (UPPER(ta.attack_mac)) ta.* FROM threat_assessments ta " +
            " WHERE ta.customer_id = :customerId AND ta.assessment_time >= :since " +
            " ORDER BY UPPER(ta.attack_mac), ta.assessment_time DESC, ta.id DESC" +
            ") latest ORDER BY latest.assessment_time DESC, latest.id DESC LIMIT :limit",
            nativeQuery = true)
    List<ThreatAssessment> findDistinctRecentByCustomer(@Param("customerId") String customerId,
                                                        @Param("since") Instant since,
                                                        @Param("limit") int limit);

    @Query(value = "SELECT latest.* FROM (" +
            " SELECT DISTINCT ON (UPPER(ta.attack_mac)) ta.* FROM threat_assessments ta " +
            " WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            " AND ta.assessment_time >= :since " +
            " ORDER BY UPPER(ta.attack_mac), ta.assessment_time DESC, ta.id DESC" +
            ") latest ORDER BY latest.assessment_time DESC, latest.id DESC LIMIT :limit",
            nativeQuery = true)
    List<ThreatAssessment> findDistinctRecentByTenant(@Param("tenantId") long tenantId,
                                                      @Param("since") Instant since,
                                                      @Param("limit") int limit);

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

    @Query(value = "SELECT attack_mac, " +
            "MAX(attack_ip) as attack_ip, " +
            "COUNT(*) as total_count, " +
            "MAX(threat_score) as max_threat_score, " +
            "MAX(threat_level) as max_threat_level " +
            "FROM threat_assessments " +
            "WHERE customer_id = :customerId " +
            "AND assessment_time >= :since " +
            "GROUP BY attack_mac " +
            "ORDER BY total_count DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopAttackers(@Param("customerId") String customerId,
                                    @Param("since") Instant since,
                                    @Param("limit") int limit);

    @Query(value = "SELECT attack_mac, " +
            "MAX(attack_ip) as attack_ip, " +
            "COUNT(*) as total_count, " +
            "MAX(threat_score) as max_threat_score, " +
            "MAX(threat_level) as max_threat_level " +
            "FROM threat_assessments " +
            "WHERE customer_id IN (:customerIds) " +
            "AND assessment_time >= :since " +
            "GROUP BY attack_mac " +
            "ORDER BY total_count DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopAttackersForCustomers(@Param("customerIds") List<String> customerIds,
                                                @Param("since") Instant since,
                                                @Param("limit") int limit);

    @Query(value = "SELECT attack_mac, MAX(attack_ip) AS attack_ip, COUNT(*) AS total_count, " +
            "MAX(threat_score) AS max_threat_score, MAX(threat_level) AS max_threat_level " +
            "FROM threat_assessments ta " +
            "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            "AND assessment_time >= :since GROUP BY attack_mac ORDER BY total_count DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopAttackersForTenant(@Param("tenantId") long tenantId,
                                              @Param("since") Instant since,
                                              @Param("limit") int limit);

    @Query(value = "SELECT ta.* FROM threat_assessments ta " +
            "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId) " +
            "ORDER BY ta.assessment_time DESC",
            countQuery = "SELECT COUNT(*) FROM threat_assessments ta " +
                    "WHERE EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ta.customer_id AND c.tenant_id = :tenantId)",
            nativeQuery = true)
    Page<ThreatAssessment> findRecentByTenant(@Param("tenantId") long tenantId, Pageable pageable);

    @Query(value = "SELECT attack_mac, customer_id, " +
            "MAX(threat_level) as max_threat_level, " +
            "MAX(threat_score) as max_threat_score, " +
            "COUNT(*) as assessment_count, " +
            "MAX(assessment_time) as latest_assessment_time, " +
            "SUM(attack_count) as total_attack_count, " +
            "COUNT(DISTINCT detection_tier) as tier_count " +
            "FROM threat_assessments " +
            "WHERE customer_id = :customerId " +
            "GROUP BY attack_mac, customer_id " +
            "ORDER BY MAX(threat_score) DESC",
            countQuery = "SELECT COUNT(DISTINCT attack_mac) FROM threat_assessments WHERE customer_id = :customerId",
            nativeQuery = true)
    Page<Object[]> findGroupedByCustomerId(@Param("customerId") String customerId, Pageable pageable);

    @Query(value = "SELECT attack_mac, customer_id, " +
            "MAX(threat_level) as max_threat_level, " +
            "MAX(threat_score) as max_threat_score, " +
            "COUNT(*) as assessment_count, " +
            "MAX(assessment_time) as latest_assessment_time, " +
            "SUM(attack_count) as total_attack_count, " +
            "COUNT(DISTINCT detection_tier) as tier_count " +
            "FROM threat_assessments " +
            "WHERE customer_id IN (:customerIds) " +
            "GROUP BY attack_mac, customer_id " +
            "ORDER BY MAX(threat_score) DESC",
            countQuery = "SELECT COUNT(DISTINCT attack_mac) FROM threat_assessments WHERE customer_id IN (:customerIds)",
            nativeQuery = true)
    Page<Object[]> findGroupedByCustomerIds(@Param("customerIds") List<String> customerIds, Pageable pageable);
}
