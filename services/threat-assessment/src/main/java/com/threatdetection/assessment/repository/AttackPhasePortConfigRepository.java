package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.AttackPhasePortConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 攻击阶段端口配置Repository
 * 
 * <p>支持多租户配置管理，优先级: 客户自定义 > 全局默认
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Repository
public interface AttackPhasePortConfigRepository extends JpaRepository<AttackPhasePortConfig, Long> {
    
    /**
     * 根据客户ID、阶段和端口号查询配置
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @param portNumber 端口号
     * @return 配置 (Optional)
     */
    Optional<AttackPhasePortConfig> findByCustomerIdAndPhaseAndPortNumber(String customerId, String phase, Integer portNumber);
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 启用的配置列表
     */
    List<AttackPhasePortConfig> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 查询客户的所有配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 配置列表
     */
    List<AttackPhasePortConfig> findByCustomerId(String customerId);
    
    /**
     * 查询全局默认配置 (customer_id IS NULL)
     * 
     * @param phase 攻击阶段
     * @return 全局默认配置列表
     */
    List<AttackPhasePortConfig> findByCustomerIdIsNullAndPhaseAndIsActive(String phase, Boolean isActive);
    
    /**
     * 查询指定阶段的所有配置 (客户自定义 + 全局默认)
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @return 配置列表 (按优先级降序)
     */
    @Query("SELECT a FROM AttackPhasePortConfig a WHERE " +
           "(a.customerId = :customerId OR a.customerId IS NULL) " +
           "AND a.phase = :phase AND a.isActive = TRUE AND a.isEnabled = TRUE " +
           "ORDER BY a.customerId DESC NULLS LAST, a.priority DESC, a.portNumber")
    List<AttackPhasePortConfig> findCombinedConfigsByCustomerAndPhase(
        @Param("customerId") String customerId,
        @Param("phase") String phase
    );
    
    /**
     * 查询客户指定阶段的配置 (仅客户自定义)
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @return 客户配置列表
     */
    List<AttackPhasePortConfig> findByCustomerIdAndPhaseAndIsActiveAndIsEnabled(
        String customerId, String phase, Boolean isActive, Boolean isEnabled
    );
    
    /**
     * 根据端口号查询所属阶段 (多租户版本)
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 阶段列表
     */
    @Query("SELECT DISTINCT a.phase FROM AttackPhasePortConfig a WHERE " +
           "(a.customerId = :customerId OR a.customerId IS NULL) " +
           "AND a.portNumber = :portNumber AND a.isActive = TRUE AND a.isEnabled = TRUE " +
           "ORDER BY CASE WHEN a.customerId = :customerId THEN 1 ELSE 2 END, a.priority DESC")
    List<String> findPhasesByCustomerAndPort(
        @Param("customerId") String customerId,
        @Param("portNumber") Integer portNumber
    );
    
    /**
     * 检查端口是否属于客户的指定阶段
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @param phase 攻击阶段
     * @return 是否属于
     */
    @Query("SELECT COUNT(a) > 0 FROM AttackPhasePortConfig a WHERE " +
           "(a.customerId = :customerId OR a.customerId IS NULL) " +
           "AND a.portNumber = :portNumber AND a.phase = :phase " +
           "AND a.isActive = TRUE AND a.isEnabled = TRUE")
    boolean isPortInCustomerPhase(
        @Param("customerId") String customerId,
        @Param("portNumber") Integer portNumber,
        @Param("phase") String phase
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
     * 检查客户、阶段和端口是否已存在配置
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @param portNumber 端口号
     * @return 是否存在
     */
    boolean existsByCustomerIdAndPhaseAndPortNumber(String customerId, String phase, Integer portNumber);
    
    /**
     * 删除客户的所有配置
     * 
     * @param customerId 客户ID
     */
    void deleteByCustomerId(String customerId);
    
    /**
     * 删除客户的特定阶段和端口配置
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @param portNumber 端口号
     */
    void deleteByCustomerIdAndPhaseAndPortNumber(String customerId, String phase, Integer portNumber);
    
    /**
     * 获取客户各阶段的统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [phase, count, avg_priority, max_priority, min_priority]
     */
    @Query("SELECT a.phase, COUNT(a), AVG(a.priority), MAX(a.priority), MIN(a.priority) " +
           "FROM AttackPhasePortConfig a WHERE a.customerId = :customerId AND a.isActive = TRUE " +
           "GROUP BY a.phase ORDER BY a.phase")
    List<Object[]> getPhaseStatistics(@Param("customerId") String customerId);
    
    /**
     * 查询高优先级配置
     * 
     * @param customerId 客户ID
     * @param minPriority 最小优先级
     * @return 高优先级配置列表
     */
    @Query("SELECT a FROM AttackPhasePortConfig a WHERE " +
           "(a.customerId = :customerId OR a.customerId IS NULL) " +
           "AND a.priority >= :minPriority AND a.isActive = TRUE AND a.isEnabled = TRUE " +
           "ORDER BY a.customerId DESC NULLS LAST, a.priority DESC")
    List<AttackPhasePortConfig> findHighPriorityConfigs(
        @Param("customerId") String customerId,
        @Param("minPriority") Integer minPriority
    );
    
    /**
     * 根据客户ID和阶段查询配置
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @return 配置列表
     */
    List<AttackPhasePortConfig> findByCustomerIdAndPhase(String customerId, String phase);
    
    /**
     * 查询全局默认配置 (customer_id IS NULL)
     * 
     * @param phase 攻击阶段
     * @return 全局默认配置列表
     */
    List<AttackPhasePortConfig> findByCustomerIdIsNullAndPhase(String phase);
    
    /**
     * 查询所有不同的阶段
     * 
     * @return 阶段列表
     */
    @Query("SELECT DISTINCT a.phase FROM AttackPhasePortConfig a WHERE a.isActive = TRUE ORDER BY a.phase")
    List<String> findDistinctPhases();
    
    /**
     * 获取统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息
     */
    @Query("SELECT COUNT(a), AVG(a.priority), MAX(a.priority), MIN(a.priority) " +
           "FROM AttackPhasePortConfig a WHERE a.customerId = :customerId AND a.isActive = TRUE")
    Object[] getStatistics(@Param("customerId") String customerId);
    
    /**
     * 根据客户ID和阶段统计数量
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @return 数量
     */
    long countByCustomerIdAndPhase(String customerId, String phase);
    
    /**
     * 根据客户ID、阶段和启用状态统计数量
     * 
     * @param customerId 客户ID
     * @param phase 攻击阶段
     * @param isEnabled 是否启用
     * @return 数量
     */
    long countByCustomerIdAndPhaseAndIsEnabled(String customerId, String phase, Boolean isEnabled);
}
