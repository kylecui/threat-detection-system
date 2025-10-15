package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.DeviceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 设备状态历史记录仓库
 */
@Repository
public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, Long> {
    
    /**
     * 根据设备序列号查找最新状态
     */
    Optional<DeviceStatusHistory> findTopByDevSerialOrderByReportTimeDesc(String devSerial);
    
    /**
     * 根据客户ID查找所有设备状态
     */
    List<DeviceStatusHistory> findByCustomerIdOrderByReportTimeDesc(String customerId);
    
    /**
     * 查找所有不健康的设备
     */
    List<DeviceStatusHistory> findByIsHealthyFalseOrderByReportTimeDesc();
    
    /**
     * 查找所有临近到期的设备
     */
    List<DeviceStatusHistory> findByIsExpiringSoonTrueOrderByReportTimeDesc();
    
    /**
     * 查找所有已过期的设备
     */
    List<DeviceStatusHistory> findByIsExpiredTrueOrderByReportTimeDesc();
    
    /**
     * 查找指定时间范围内的设备状态
     */
    @Query("SELECT d FROM DeviceStatusHistory d WHERE d.devSerial = :devSerial " +
           "AND d.reportTime BETWEEN :startTime AND :endTime ORDER BY d.reportTime DESC")
    List<DeviceStatusHistory> findByDevSerialAndTimeRange(
        @Param("devSerial") String devSerial,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
    
    /**
     * 统计客户的设备数量
     */
    @Query("SELECT COUNT(DISTINCT d.devSerial) FROM DeviceStatusHistory d WHERE d.customerId = :customerId")
    Long countDevicesByCustomerId(@Param("customerId") String customerId);
    
    /**
     * 查找诱饵数量变化的记录
     */
    List<DeviceStatusHistory> findBySentryCountChangedTrueOrderByReportTimeDesc();
    
    /**
     * 查找在线设备数量变化的记录
     */
    List<DeviceStatusHistory> findByRealHostCountChangedTrueOrderByReportTimeDesc();
}
