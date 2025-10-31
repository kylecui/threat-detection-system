package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.HoneypotSensitivityWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 蜜罐敏感度权重配置Repository (V4.0)
 * 
 * <p>支持基于IP范围的查询（使用PostgreSQL INET类型）
 * 
 * @author ThreatDetection Team
 * @version 4.0
 * @since 2025-10-24
 */
@Repository
public interface HoneypotSensitivityWeightRepository extends JpaRepository<HoneypotSensitivityWeight, Long> {
    
    /**
     * 根据蜜罐IP地址查询匹配的敏感度权重配置
     * 
     * <p>查询逻辑:
     * 1. IP地址在配置的IP范围内
     * 2. 仅查询启用的配置
     * 3. 按优先级降序排序
     * 4. 返回第一个匹配结果
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP地址
     * @return 匹配的权重配置
     */
    @Query(value = """
        SELECT * FROM honeypot_sensitivity_weights
        WHERE customer_id = :customerId
          AND is_active = TRUE
          AND CAST(:honeypotIp AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
        ORDER BY priority DESC, id DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<HoneypotSensitivityWeight> findByCustomerIdAndHoneypotIp(
        @Param("customerId") String customerId,
        @Param("honeypotIp") String honeypotIp
    );
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 启用的配置列表
     */
    List<HoneypotSensitivityWeight> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 根据部署区域查询
     *
     * @param customerId 客户ID
     * @param deploymentZone 部署区域
     * @return 匹配的配置列表
     */
    @Query("""
        SELECT h FROM HoneypotSensitivityWeight h
        WHERE h.customerId = :customerId
          AND h.isActive = TRUE
          AND h.deploymentZone = :deploymentZone
        """)
    List<HoneypotSensitivityWeight> findByCustomerIdAndDeploymentZone(@Param("customerId") String customerId, @Param("deploymentZone") String deploymentZone);
    
    /**
     * 根据蜜罐层级查询
     * 
     * @param customerId 客户ID
     * @param honeypotTier 蜜罐层级 (HIGH/MEDIUM/LOW)
     * @return 匹配的配置列表
     */
    @Query("""
        SELECT h FROM HoneypotSensitivityWeight h
        WHERE h.customerId = :customerId
          AND h.isActive = TRUE
          AND (
              (:honeypotTier = 'HIGH' AND h.honeypotSensitivityWeight >= 2.5) OR
              (:honeypotTier = 'MEDIUM' AND h.honeypotSensitivityWeight >= 1.8 AND h.honeypotSensitivityWeight < 2.5) OR
              (:honeypotTier = 'LOW' AND h.honeypotSensitivityWeight < 1.8)
          )
        """)
    List<HoneypotSensitivityWeight> findByCustomerIdAndHoneypotTier(@Param("customerId") String customerId, @Param("honeypotTier") String honeypotTier);
    
    /**
     * 查询敏感度 >= 阈值的高敏感蜜罐
     * 
     * @param customerId 客户ID
     * @param threshold 敏感度阈值
     * @return 高敏感蜜罐列表
     */
    @Query("SELECT h FROM HoneypotSensitivityWeight h WHERE h.customerId = :customerId AND h.isActive = TRUE AND h.honeypotSensitivityWeight >= :threshold ORDER BY h.honeypotSensitivityWeight DESC")
    List<HoneypotSensitivityWeight> findHighSensitivityHoneypots(
        @Param("customerId") String customerId,
        @Param("threshold") BigDecimal threshold
    );
    
    /**
     * 统计客户的蜜罐配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    long countByCustomerId(String customerId);
    
    /**
     * 按部署区域统计
     * 
     * @param customerId 客户ID
     * @return 统计结果 [deploymentZone, count]
     */
    @Query("""
        SELECT 
            h.deploymentZone,
            COUNT(h)
        FROM HoneypotSensitivityWeight h 
        WHERE h.customerId = :customerId AND h.isActive = TRUE 
        GROUP BY h.deploymentZone
        ORDER BY COUNT(h) DESC
        """)
    List<Object[]> countByDeploymentZone(@Param("customerId") String customerId);
}
