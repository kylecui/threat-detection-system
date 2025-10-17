package com.threatdetection.customer.notification.repository;

import com.threatdetection.customer.notification.model.NotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 通知配置数据访问层
 */
@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Long> {

    /**
     * 根据客户ID查找通知配置
     */
    Optional<NotificationConfig> findByCustomerId(String customerId);

    /**
     * 检查客户是否已有通知配置
     */
    boolean existsByCustomerId(String customerId);

    /**
     * 删除客户的通知配置
     */
    void deleteByCustomerId(String customerId);
}
