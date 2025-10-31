package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.AttackSourceWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 攻击源网段权重配置Repository (V4.0)
 * 
 * <p>支持基于IP范围的查询（使用PostgreSQL INET类型）
 * 
 * @author ThreatDetection Team
 * @version 4.0
 * @since 2025-10-24
 */
@Repository
public interface AttackSourceWeightRepository extends JpaRepository<AttackSourceWeight, Long> {
    
    /**
     * 根据IP地址查询匹配的攻击源权重配置
     * 
     * <p>查询逻辑:
     * 1. IP地址在配置的IP范围内
     * 2. 仅查询启用的配置
     * 3. 按优先级降序排序
     * 4. 返回第一个匹配结果
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @return 匹配的权重配置
     */
    @Query(value = """
        SELECT * FROM attack_source_weights
        WHERE customer_id = :customerId
          AND is_active = TRUE
          AND CAST(:ipAddress AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
        ORDER BY priority DESC, id DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<AttackSourceWeight> findByCustomerIdAndIpAddress(
        @Param("customerId") String customerId,
        @Param("ipAddress") String ipAddress
    );
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 启用的配置列表
     */
    List<AttackSourceWeight> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 根据网段类型查询
     *
     * @param customerId 客户ID
     * @param segmentType 网段类型
     * @return 匹配的配置列表
     */
    @Query("""
        SELECT a FROM AttackSourceWeight a
        WHERE a.customerId = :customerId
          AND a.isActive = TRUE
          AND a.segmentType = :segmentType
        """)
    List<AttackSourceWeight> findByCustomerIdAndSegmentType(@Param("customerId") String customerId, @Param("segmentType") String segmentType);
    
    /**
     * 根据风险等级查询
     *
     * @param customerId 客户ID
     * @param riskLevel 风险等级
     * @return 匹配的配置列表
     */
    @Query("""
        SELECT a FROM AttackSourceWeight a
        WHERE a.customerId = :customerId
          AND a.isActive = TRUE
          AND a.riskLevel = :riskLevel
        """)
    List<AttackSourceWeight> findByCustomerIdAndRiskLevel(@Param("customerId") String customerId, @Param("riskLevel") String riskLevel);
    
    /**
     * 查询权重 >= 阈值的高危配置
     * 
     * @param customerId 客户ID
     * @param threshold 权重阈值
     * @return 高危配置列表
     */
    @Query("SELECT a FROM AttackSourceWeight a WHERE a.customerId = :customerId AND a.isActive = TRUE AND a.weight >= :threshold ORDER BY a.weight DESC")
    List<AttackSourceWeight> findHighRiskConfigs(
        @Param("customerId") String customerId,
        @Param("threshold") BigDecimal threshold
    );
    
    /**
     * 统计客户的配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    long countByCustomerId(String customerId);
    
    /**
     * 按网段类型统计
     * 
     * @param customerId 客户ID
     * @return 统计结果 [segmentType, count]
     */
    @Query("""
        SELECT a.segmentType, COUNT(a)
        FROM AttackSourceWeight a 
        WHERE a.customerId = :customerId AND a.isActive = TRUE 
        GROUP BY a.segmentType
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countBySegmentType(@Param("customerId") String customerId);
}
