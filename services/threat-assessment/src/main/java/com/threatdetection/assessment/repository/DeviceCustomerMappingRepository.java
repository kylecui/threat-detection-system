package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.DeviceCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DeviceCustomerMapping entity with temporal support
 */
@Repository
public interface DeviceCustomerMappingRepository extends JpaRepository<DeviceCustomerMapping, Long> {

    /**
     * 查找设备在特定时间点的有效映射
     */
    @Query("SELECT m FROM DeviceCustomerMapping m WHERE UPPER(m.devSerial) = UPPER(:devSerial) " +
           "AND m.bindTime <= :timestamp AND (m.unbindTime IS NULL OR m.unbindTime > :timestamp)")
    Optional<DeviceCustomerMapping> findActiveMappingAtTime(@Param("devSerial") String devSerial,
                                                           @Param("timestamp") Instant timestamp);

    /**
     * 查找设备的历史映射记录（按绑定时间倒序）
     */
    List<DeviceCustomerMapping> findByDevSerialOrderByBindTimeDesc(String devSerial);

    /**
     * 查找当前所有有效映射（unbind_time为NULL）
     */
    @Query("SELECT m FROM DeviceCustomerMapping m WHERE m.unbindTime IS NULL")
    List<DeviceCustomerMapping> findAllCurrentlyActive();

    /**
     * 查找客户的所有设备映射（包括历史，按绑定时间倒序）
     */
    List<DeviceCustomerMapping> findByCustomerIdOrderByBindTimeDesc(String customerId);

    /**
     * 查找设备当前有效映射（向后兼容）
     */
    @Query("SELECT m.customerId FROM DeviceCustomerMapping m WHERE UPPER(m.devSerial) = UPPER(:devSerial) AND m.unbindTime IS NULL")
    Optional<String> findCustomerIdByDevSerial(@Param("devSerial") String devSerial);

    // ========== 向后兼容的方法 ==========

    /**
     * Find active mapping by device serial (legacy method)
     */
    Optional<DeviceCustomerMapping> findByDevSerialAndIsActiveTrue(String devSerial);

    /**
     * Check if mapping exists and is active (legacy method)
     */
    boolean existsByDevSerialAndIsActiveTrue(String devSerial);

    /**
     * Find all active mappings (legacy method)
     */
    List<DeviceCustomerMapping> findByIsActiveTrue();

    /**
     * Find all mappings for a customer (legacy method)
     */
    List<DeviceCustomerMapping> findByCustomerId(String customerId);
}
