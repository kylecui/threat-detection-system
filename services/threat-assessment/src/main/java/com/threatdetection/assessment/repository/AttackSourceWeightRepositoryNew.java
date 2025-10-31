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
 * 攻击源网段权重配置Repository (新版本)
 * 
 * <p>支持基于IP段的查询
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Repository
public interface AttackSourceWeightRepositoryNew extends JpaRepository<AttackSourceWeight, Long> {
    
    /**
     * 根据客户ID和IP段查询权重配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 权重配置 (Optional)
     */
    Optional<AttackSourceWeight> findByCustomerIdAndIpSegment(String customerId, String ipSegment);
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 启用的配置列表
     */
    List<AttackSourceWeight> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 查询客户的所有配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 配置列表
     */
    List<AttackSourceWeight> findByCustomerId(String customerId);
    
    /**
     * 根据权重阈值查询高危配置
     * 
     * @param customerId 客户ID
     * @param threshold 权重阈值
     * @return 高危配置列表
     */
    @Query("SELECT a FROM AttackSourceWeight a WHERE a.customerId = :customerId AND a.isActive = TRUE AND a.attackSourceWeight >= :threshold ORDER BY a.attackSourceWeight DESC")
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
     * 统计客户的启用配置数量
     * 
     * @param customerId 客户ID
     * @return 启用配置数量
     */
    long countByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 检查客户和IP段是否已存在配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 是否存在
     */
    boolean existsByCustomerIdAndIpSegment(String customerId, String ipSegment);
    
    /**
     * 删除客户的所有配置
     * 
     * @param customerId 客户ID
     */
    void deleteByCustomerId(String customerId);
    
    /**
     * 删除客户的特定IP段配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     */
    void deleteByCustomerIdAndIpSegment(String customerId, String ipSegment);
    
    /**
     * 获取权重统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [count, avg_weight, max_weight, min_weight]
     */
    @Query("SELECT COUNT(a), AVG(a.attackSourceWeight), MAX(a.attackSourceWeight), MIN(a.attackSourceWeight) " +
           "FROM AttackSourceWeight a WHERE a.customerId = :customerId AND a.isActive = TRUE")
    Object[] getStatistics(@Param("customerId") String customerId);
}
