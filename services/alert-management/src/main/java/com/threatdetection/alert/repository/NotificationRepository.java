package com.threatdetection.alert.repository;

import com.threatdetection.alert.model.Notification;
import com.threatdetection.alert.model.NotificationChannel;
import com.threatdetection.alert.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知数据访问层
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 根据告警ID查找通知
     */
    List<Notification> findByAlertId(Long alertId);

    /**
     * 根据状态查找通知
     */
    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    /**
     * 根据通道查找通知
     */
    Page<Notification> findByChannel(NotificationChannel channel, Pageable pageable);

    /**
     * 根据状态和通道查找通知
     */
    Page<Notification> findByStatusAndChannel(NotificationStatus status, NotificationChannel channel, Pageable pageable);

    /**
     * 根据接收者查找通知
     */
    Page<Notification> findByRecipient(String recipient, Pageable pageable);

    /**
     * 根据时间范围查找通知
     */
    Page<Notification> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 查找待发送的通知
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * 查找可以重试的通知
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<Notification> findNotificationsForRetry();

    /**
     * 统计通知数量按状态分组
     */
    @Query("SELECT n.status, COUNT(n) FROM Notification n GROUP BY n.status")
    List<Object[]> countNotificationsByStatus();

    /**
     * 统计通知数量按通道分组
     */
    @Query("SELECT n.channel, COUNT(n) FROM Notification n GROUP BY n.channel")
    List<Object[]> countNotificationsByChannel();

    /**
     * 计算通知成功率
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'SENT'")
    Long countSuccessfulNotifications();

    /**
     * 计算通知失败率
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'FAILED'")
    Long countFailedNotifications();

    /**
     * 查找告警的最新通知
     */
    @Query("SELECT n FROM Notification n WHERE n.alert.id = :alertId ORDER BY n.createdAt DESC")
    List<Notification> findLatestNotificationsByAlertId(@Param("alertId") Long alertId, Pageable pageable);

    /**
     * 删除旧的通知记录（用于清理）
     */
    @Query("DELETE FROM Notification n WHERE n.createdAt < :beforeDate")
    int deleteOldNotifications(@Param("beforeDate") LocalDateTime beforeDate);
}