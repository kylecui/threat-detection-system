package com.threatdetection.alert.repository;

import com.threatdetection.alert.model.CustomerNotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 客户通知配置数据访问层
 */
@Repository
public interface CustomerNotificationConfigRepository extends JpaRepository<CustomerNotificationConfig, Long> {
    
    /**
     * 根据客户ID查找通知配置
     * @param customerId 客户ID
     * @return 客户通知配置
     */
    Optional<CustomerNotificationConfig> findByCustomerId(String customerId);
    
    /**
     * 根据客户ID和激活状态查找
     * @param customerId 客户ID
     * @param isActive 是否激活
     * @return 客户通知配置
     */
    Optional<CustomerNotificationConfig> findByCustomerIdAndIsActive(String customerId, Boolean isActive);
}
