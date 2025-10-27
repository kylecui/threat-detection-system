package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.CustomerPortWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户端口权重配置Repository
 * 
 * <p>提供端口权重配置的数据访问功能
 * 
 * @author Security Team
 * @version 4.0
 */
@Repository
public interface CustomerPortWeightRepository extends JpaRepository<CustomerPortWeight, Long> {

    /**
     * 查询指定客户的所有启用的端口权重配置
     * 
     * @param customerId 客户ID
     * @return 端口权重配置列表
     */
    List<CustomerPortWeight> findByCustomerIdAndEnabledTrue(String customerId);

    /**
     * 查询指定客户的所有端口权重配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 端口权重配置列表
     */
    List<CustomerPortWeight> findByCustomerId(String customerId);

    /**
     * 查询指定客户的特定端口权重配置
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 端口权重配置 (Optional)
     */
    Optional<CustomerPortWeight> findByCustomerIdAndPortNumber(String customerId, Integer portNumber);

    /**
     * 查询指定客户的特定端口权重配置 (仅启用的)
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 端口权重配置 (Optional)
     */
    Optional<CustomerPortWeight> findByCustomerIdAndPortNumberAndEnabledTrue(
        String customerId, Integer portNumber);

    /**
     * 批量查询指定客户的多个端口权重配置
     * 
     * @param customerId 客户ID
     * @param portNumbers 端口号列表
     * @return 端口权重配置列表
     */
    List<CustomerPortWeight> findByCustomerIdAndPortNumberInAndEnabledTrue(
        String customerId, List<Integer> portNumbers);

    /**
     * 查询指定客户和风险等级的端口配置
     * 
     * @param customerId 客户ID
     * @param riskLevel 风险等级
     * @return 端口权重配置列表
     */
    List<CustomerPortWeight> findByCustomerIdAndRiskLevelAndEnabledTrue(
        String customerId, String riskLevel);

    /**
     * 查询指定客户的端口配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    long countByCustomerId(String customerId);

    /**
     * 查询指定客户的启用配置数量
     * 
     * @param customerId 客户ID
     * @return 启用配置数量
     */
    long countByCustomerIdAndEnabledTrue(String customerId);

    /**
     * 检查指定客户和端口是否已存在配置
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 是否存在
     */
    boolean existsByCustomerIdAndPortNumber(String customerId, Integer portNumber);

    /**
     * 删除指定客户的所有配置
     * 
     * @param customerId 客户ID
     */
    void deleteByCustomerId(String customerId);

    /**
     * 删除指定客户的特定端口配置
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     */
    void deleteByCustomerIdAndPortNumber(String customerId, Integer portNumber);

    /**
     * 查询指定客户的高优先级配置
     * 
     * @param customerId 客户ID
     * @param minPriority 最小优先级
     * @return 端口权重配置列表
     */
    @Query("SELECT c FROM CustomerPortWeight c WHERE c.customerId = :customerId " +
           "AND c.priority >= :minPriority AND c.enabled = true " +
           "ORDER BY c.priority DESC, c.weight DESC")
    List<CustomerPortWeight> findHighPriorityConfigs(
        @Param("customerId") String customerId,
        @Param("minPriority") Integer minPriority);

    /**
     * 查询指定客户的高权重端口
     * 
     * @param customerId 客户ID
     * @param minWeight 最小权重
     * @return 端口权重配置列表
     */
    @Query("SELECT c FROM CustomerPortWeight c WHERE c.customerId = :customerId " +
           "AND c.weight >= :minWeight AND c.enabled = true " +
           "ORDER BY c.weight DESC")
    List<CustomerPortWeight> findHighWeightPorts(
        @Param("customerId") String customerId,
        @Param("minWeight") Double minWeight);

    /**
     * 获取指定客户的统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息对象
     */
    @Query("SELECT COUNT(c), AVG(c.weight), MAX(c.weight), MIN(c.weight) " +
           "FROM CustomerPortWeight c WHERE c.customerId = :customerId AND c.enabled = true")
    Object[] getStatistics(@Param("customerId") String customerId);
}
