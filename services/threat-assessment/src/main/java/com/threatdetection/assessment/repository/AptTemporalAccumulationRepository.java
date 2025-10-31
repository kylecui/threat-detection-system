package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.AptTemporalAccumulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * APT时态累积数据Repository
 * 
 * <p>支持长期威胁累积数据管理和指数衰减计算
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Repository
public interface AptTemporalAccumulationRepository extends JpaRepository<AptTemporalAccumulation, Long> {
    
    /**
     * 根据客户ID和攻击MAC查询累积数据
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @return 累积数据 (Optional)
     */
    Optional<AptTemporalAccumulation> findByCustomerIdAndAttackMac(String customerId, String attackMac);
    
    /**
     * 查询客户的所有累积数据
     * 
     * @param customerId 客户ID
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerId(String customerId);
    
    /**
     * 查询客户的所有启用累积数据
     * 
     * @param customerId 客户ID
     * @return 启用的累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 根据攻击MAC查询累积数据 (跨客户)
     * 
     * @param attackMac 攻击者MAC地址
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByAttackMac(String attackMac);
    
    /**
     * 查询指定时间窗口内的累积数据
     * 
     * @param customerId 客户ID
     * @param startTime 窗口开始时间
     * @param endTime 窗口结束时间
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndWindowStartGreaterThanEqualAndWindowEndLessThanEqual(
        String customerId, Instant startTime, Instant endTime
    );
    
    /**
     * 根据推断阶段查询累积数据
     * 
     * @param customerId 客户ID
     * @param inferredPhase 推断的攻击阶段
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndInferredAttackPhaseAndIsActive(
        String customerId, String inferredPhase, Boolean isActive
    );
    
    /**
     * 查询高威胁累积数据 (按评分降序)
     * 
     * @param customerId 客户ID
     * @param minScore 最小评分阈值
     * @return 高威胁累积数据列表
     */
    @Query("SELECT a FROM AptTemporalAccumulation a WHERE a.customerId = :customerId " +
           "AND a.isActive = TRUE AND a.decayAccumulatedScore >= :minScore " +
           "ORDER BY a.decayAccumulatedScore DESC")
    List<AptTemporalAccumulation> findHighThreatAccumulations(
        @Param("customerId") String customerId,
        @Param("minScore") BigDecimal minScore
    );
    
    /**
     * 查询最近更新的累积数据
     * 
     * @param customerId 客户ID
     * @param since 时间点
     * @return 最近更新的累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndLastUpdatedGreaterThanEqual(
        String customerId, Instant since
    );
    
    /**
     * 根据缓存键查询累积数据
     * 
     * @param cacheKey Redis缓存键
     * @return 累积数据 (Optional)
     */
    Optional<AptTemporalAccumulation> findByCacheKey(String cacheKey);
    
    /**
     * 查询即将过期的缓存数据
     * 
     * @param beforeTime 过期时间点
     * @return 即将过期的数据列表
     */
    List<AptTemporalAccumulation> findByCacheExpiryLessThan(Instant beforeTime);
    
    /**
     * 统计客户的累积数据数量
     * 
     * @param customerId 客户ID
     * @return 数据数量
     */
    long countByCustomerId(String customerId);
    
    /**
     * 统计客户的启用数据数量
     * 
     * @param customerId 客户ID
     * @return 启用数据数量
     */
    long countByCustomerIdAndIsActive(String customerId, Boolean isActive);
    
    /**
     * 检查客户和攻击MAC是否已存在累积数据
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @return 是否存在
     */
    boolean existsByCustomerIdAndAttackMac(String customerId, String attackMac);
    
    /**
     * 删除客户的所有累积数据
     * 
     * @param customerId 客户ID
     */
    void deleteByCustomerId(String customerId);
    
    /**
     * 删除指定的累积数据
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     */
    void deleteByCustomerIdAndAttackMac(String customerId, String attackMac);
    
    /**
     * 获取评分统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [count, avg_score, max_score, min_score]
     */
    @Query("SELECT COUNT(a), AVG(a.decayAccumulatedScore), MAX(a.decayAccumulatedScore), MIN(a.decayAccumulatedScore) " +
           "FROM AptTemporalAccumulation a WHERE a.customerId = :customerId AND a.isActive = TRUE")
    Object[] getScoreStatistics(@Param("customerId") String customerId);
    
    /**
     * 获取各阶段的统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [phase, count, avg_score, max_score]
     */
    @Query("SELECT a.inferredAttackPhase, COUNT(a), AVG(a.decayAccumulatedScore), MAX(a.decayAccumulatedScore) " +
           "FROM AptTemporalAccumulation a WHERE a.customerId = :customerId AND a.isActive = TRUE " +
           "AND a.inferredAttackPhase IS NOT NULL GROUP BY a.inferredAttackPhase ORDER BY COUNT(a) DESC")
    List<Object[]> getPhaseStatistics(@Param("customerId") String customerId);
    
    /**
     * 批量查询多个攻击MAC的累积数据
     * 
     * @param customerId 客户ID
     * @param attackMacs 攻击MAC地址列表
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndAttackMacIn(String customerId, List<String> attackMacs);
    
    /**
     * 查询指定时间范围内的活跃累积数据
     * 
     * @param customerId 客户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 活跃累积数据列表
     */
    @Query("SELECT a FROM AptTemporalAccumulation a WHERE a.customerId = :customerId " +
           "AND a.isActive = TRUE AND a.lastUpdated BETWEEN :startTime AND :endTime " +
           "ORDER BY a.lastUpdated DESC")
    List<AptTemporalAccumulation> findActiveAccumulationsInTimeRange(
        @Param("customerId") String customerId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
    
    /**
     * 根据客户ID、攻击MAC和窗口开始时间查询累积数据
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @param windowStart 窗口开始时间
     * @return 累积数据 (Optional)
     */
    Optional<AptTemporalAccumulation> findByCustomerIdAndAttackMacAndWindowStart(
        String customerId, String attackMac, Instant windowStart
    );
    
    /**
     * 根据客户ID和窗口开始时间范围查询累积数据
     * 
     * @param customerId 客户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndWindowStartBetween(
        String customerId, Instant startTime, Instant endTime
    );
    
    /**
     * 根据客户ID和窗口开始时间大于等于指定时间查询累积数据
     * 
     * @param customerId 客户ID
     * @param windowStart 窗口开始时间
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndWindowStartGreaterThanEqual(
        String customerId, Instant windowStart
    );
    
    /**
     * 查询高风险累积数据
     * 
     * @param customerId 客户ID
     * @param minScore 最小评分
     * @param currentTime 当前时间
     * @return 高风险累积数据列表
     */
    @Query("SELECT a FROM AptTemporalAccumulation a WHERE a.customerId = :customerId " +
           "AND a.isActive = TRUE AND a.decayAccumulatedScore >= :minScore " +
           "AND a.lastUpdated >= :currentTime ORDER BY a.decayAccumulatedScore DESC")
    List<AptTemporalAccumulation> findHighRiskAccumulations(
        @Param("customerId") String customerId,
        @Param("minScore") BigDecimal minScore,
        @Param("currentTime") Instant currentTime
    );
    
    /**
     * 删除客户ID、攻击MAC和窗口开始时间匹配的累积数据
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @param windowStart 窗口开始时间
     */
    void deleteByCustomerIdAndAttackMacAndWindowStart(
        String customerId, String attackMac, Instant windowStart
    );
    
    /**
     * 删除窗口开始时间小于指定时间的累积数据
     * 
     * @param customerId 客户ID
     * @param windowStart 窗口开始时间
     */
    void deleteByCustomerIdAndWindowStartLessThan(String customerId, Instant windowStart);
    
    /**
     * 获取统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息
     */
    @Query("SELECT COUNT(a), AVG(a.decayAccumulatedScore), MAX(a.decayAccumulatedScore), MIN(a.decayAccumulatedScore) " +
           "FROM AptTemporalAccumulation a WHERE a.customerId = :customerId AND a.isActive = TRUE")
    Object[] getStatistics(@Param("customerId") String customerId);
    
    /**
     * 获取累积评分总和
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @param windowStart 窗口开始时间
     * @return 累积评分总和
     */
    @Query("SELECT COALESCE(SUM(a.decayAccumulatedScore), 0) FROM AptTemporalAccumulation a " +
           "WHERE a.customerId = :customerId AND a.attackMac = :attackMac " +
           "AND a.windowStart >= :windowStart AND a.isActive = TRUE")
    BigDecimal getTotalAccumulatedScore(
        @Param("customerId") String customerId,
        @Param("attackMac") String attackMac,
        @Param("windowStart") Instant windowStart
    );
    
    /**
     * 获取最大衰减评分
     * 
     * @param customerId 客户ID
     * @param windowStart 窗口开始时间
     * @return 最大衰减评分
     */
    @Query("SELECT MAX(a.decayAccumulatedScore) FROM AptTemporalAccumulation a " +
           "WHERE a.customerId = :customerId AND a.windowStart >= :windowStart AND a.isActive = TRUE")
    BigDecimal getMaxDecayScore(@Param("customerId") String customerId, @Param("windowStart") Instant windowStart);
    
    /**
     * 根据客户ID和窗口开始时间范围统计数量
     * 
     * @param customerId 客户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 数量
     */
    long countByCustomerIdAndWindowStartBetween(String customerId, Instant startTime, Instant endTime);
    
    /**
     * 根据客户ID、攻击MAC和窗口开始时间检查是否存在
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @param windowStart 窗口开始时间
     * @return 是否存在
     */
    boolean existsByCustomerIdAndAttackMacAndWindowStart(
        String customerId, String attackMac, Instant windowStart
    );
    
    /**
     * 根据客户ID、攻击MAC和窗口开始时间范围查询累积数据（按窗口开始时间排序）
     * 
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 累积数据列表
     */
    List<AptTemporalAccumulation> findByCustomerIdAndAttackMacAndWindowStartBetweenOrderByWindowStart(
        String customerId, String attackMac, Instant startTime, Instant endTime
    );
}
