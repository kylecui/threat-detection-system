package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.HoneypotSensitivityWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 蜜罐敏感度权重配置Repository (新版本)
 * 
 * <p>支持基于IP段的查询
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Repository
public interface HoneypotSensitivityWeightRepositoryNew extends JpaRepository<HoneypotSensitivityWeight, Long> {
    
    /**
     * 根据客户ID和蜜罐IP查询匹配的权重配置
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP
     * @return 权重配置 (Optional)
     */
    @Query(value = """
        SELECT * FROM honeypot_sensitivity_weights h
        WHERE h.customer_id = :customerId
          AND h.is_active = TRUE
          AND CAST(:honeypotIp AS inet) BETWEEN CAST(h.ip_range_start AS inet) AND CAST(h.ip_range_end AS inet)
        ORDER BY h.priority DESC, h.id DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<HoneypotSensitivityWeight> findByCustomerIdAndHoneypotIp(@Param("customerId") String customerId, @Param("honeypotIp") String honeypotIp);
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 启用的配置列表
     */
    List<HoneypotSensitivityWeight> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 查询客户的所有配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 配置列表
     */
    List<HoneypotSensitivityWeight> findByCustomerId(String customerId);
    
    /**
     * 根据权重阈值查询高敏感度配置
     * 
     * @param customerId 客户ID
     * @param threshold 权重阈值
     * @return 高敏感度配置列表
     */
    @Query("SELECT h FROM HoneypotSensitivityWeight h WHERE h.customerId = :customerId AND h.isActive = TRUE AND h.honeypotSensitivityWeight >= :threshold ORDER BY h.honeypotSensitivityWeight DESC")
    List<HoneypotSensitivityWeight> findHighSensitivityConfigs(
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
     * 统计客户的启用配置数量
     * 
     * @param customerId 客户ID
     * @return 启用配置数量
     */
    long countByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 检查客户和蜜罐IP是否已存在配置
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP
     * @return 是否存在
     */
    @Query(value = """
        SELECT COUNT(*) > 0 FROM honeypot_sensitivity_weights h
        WHERE h.customer_id = :customerId
          AND h.is_active = TRUE
          AND CAST(:honeypotIp AS inet) BETWEEN CAST(h.ip_range_start AS inet) AND CAST(h.ip_range_end AS inet)
        """, nativeQuery = true)
    boolean existsByCustomerIdAndHoneypotIp(@Param("customerId") String customerId, @Param("honeypotIp") String honeypotIp);
    
    /**
     * 删除客户的所有配置
     * 
     * @param customerId 客户ID
     */
    void deleteByCustomerId(String customerId);
    
    /**
     * 删除客户的特定蜜罐IP配置
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP
     */
    @Modifying
    @Query(value = """
        DELETE FROM honeypot_sensitivity_weights h
        WHERE h.customer_id = :customerId
          AND CAST(:honeypotIp AS inet) BETWEEN CAST(h.ip_range_start AS inet) AND CAST(h.ip_range_end AS inet)
        """, nativeQuery = true)
    void deleteByCustomerIdAndHoneypotIp(@Param("customerId") String customerId, @Param("honeypotIp") String honeypotIp);
    
    /**
     * 获取权重统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [count, avg_weight, max_weight, min_weight]
     */
    @Query("SELECT COUNT(h), AVG(h.honeypotSensitivityWeight), MAX(h.honeypotSensitivityWeight), MIN(h.honeypotSensitivityWeight) " +
           "FROM HoneypotSensitivityWeight h WHERE h.customerId = :customerId AND h.isActive = TRUE")
    Object[] getStatistics(@Param("customerId") String customerId);
}
