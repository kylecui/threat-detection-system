package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.CustomerTimeWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户时间段权重配置数据访问层
 *
 * <p>支持复杂的时间段查询和多租户隔离
 * <p>关键查询: 按小时查找权重配置，支持跨天时间段
 *
 * @author Security Team
 * @version 5.0
 */
@Repository
public interface CustomerTimeWeightRepository extends JpaRepository<CustomerTimeWeight, Long> {

    /**
     * 根据客户ID查找所有时间权重配置
     */
    List<CustomerTimeWeight> findByCustomerIdOrderByPriorityDesc(String customerId);

    /**
     * 根据客户ID查找启用的时间权重配置
     */
    List<CustomerTimeWeight> findByCustomerIdAndEnabledTrueOrderByPriorityDesc(String customerId);

    /**
     * 根据客户ID和时间段查找配置
     */
    Optional<CustomerTimeWeight> findByCustomerIdAndStartHourAndEndHour(
        String customerId, Integer startHour, Integer endHour);

    /**
     * 根据客户ID和小时查找适用的权重配置
     *
     * <p>支持跨天时间段查询:
     * - 正常时间段: startHour <= endHour (如 9:00-17:00)
     * - 跨天时间段: startHour > endHour (如 22:00-06:00)
     *
     * @param customerId 客户ID
     * @param hour 小时 (0-23)
     * @return 适用的权重配置列表，按优先级排序
     */
    @Query("SELECT w FROM CustomerTimeWeight w WHERE w.customerId = :customerId " +
           "AND w.enabled = true AND (" +
           // 正常时间段: hour 在 [startHour, endHour) 范围内
           "(w.startHour <= w.endHour AND :hour >= w.startHour AND :hour < w.endHour) OR " +
           // 跨天时间段: hour >= startHour 或 hour < endHour
           "(w.startHour > w.endHour AND (:hour >= w.startHour OR :hour < w.endHour))" +
           ") ORDER BY w.priority DESC")
    List<CustomerTimeWeight> findByCustomerIdAndHour(@Param("customerId") String customerId,
                                                    @Param("hour") int hour);

    /**
     * 查找所有启用的时间权重配置 (用于缓存预热)
     */
    @Query("SELECT w FROM CustomerTimeWeight w WHERE w.enabled = true ORDER BY w.customerId, w.priority DESC")
    List<CustomerTimeWeight> findAllEnabled();

    /**
     * 根据客户ID删除所有配置
     */
    void deleteByCustomerId(String customerId);

    /**
     * 检查客户是否有自定义时间权重配置
     */
    boolean existsByCustomerIdAndEnabledTrue(String customerId);

    /**
     * 统计客户的配置数量
     */
    long countByCustomerId(String customerId);

    /**
     * 查找优先级最高的配置
     */
    Optional<CustomerTimeWeight> findFirstByCustomerIdAndEnabledTrueOrderByPriorityDesc(String customerId);
}