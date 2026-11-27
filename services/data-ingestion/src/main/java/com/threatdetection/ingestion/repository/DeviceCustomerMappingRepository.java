package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.DeviceCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DeviceCustomerMapping entity with temporal support.
 * Provides database operations for device to customer mappings with time-based validity.
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
    @Query(value = "SELECT m.customer_id FROM device_customer_mapping m " +
            "JOIN customers c ON c.customer_id = m.customer_id " +
            "WHERE UPPER(m.dev_serial) = UPPER(:devSerial) " +
            "AND m.unbind_time IS NULL AND m.is_active = TRUE AND c.status = 'ACTIVE'",
            nativeQuery = true)
    Optional<String> findCustomerIdByDevSerial(@Param("devSerial") String devSerial);

    @Query(value = "SELECT m.* FROM device_customer_mapping m " +
            "JOIN customers c ON c.customer_id = m.customer_id " +
            "WHERE m.is_active = TRUE AND m.unbind_time IS NULL AND c.status = 'ACTIVE'",
            nativeQuery = true)
    List<DeviceCustomerMapping> findAllOperationalMappings();

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customers " +
            "WHERE customer_id = :customerId AND status = 'ACTIVE')",
            nativeQuery = true)
    boolean isCustomerOperational(@Param("customerId") String customerId);

    // ========== 向后兼容的方法 ==========

    /**
     * Find active mapping by device serial number (legacy method)
     */
    Optional<DeviceCustomerMapping> findByDevSerialAndIsActiveTrue(String devSerial);

    /**
     * Find all active mappings for a customer (legacy method)
     */
    List<DeviceCustomerMapping> findByCustomerIdAndIsActiveTrue(String customerId);

    /**
     * Find all active mappings (legacy method)
     */
    List<DeviceCustomerMapping> findByIsActiveTrue();

    /**
     * Check if a device serial exists and is active (legacy method)
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM device_customer_mapping m " +
            "JOIN customers c ON c.customer_id = m.customer_id " +
            "WHERE UPPER(m.dev_serial) = UPPER(:devSerial) " +
            "AND m.is_active = TRUE AND m.unbind_time IS NULL AND c.status = 'ACTIVE')",
            nativeQuery = true)
    boolean existsByDevSerialAndIsActiveTrue(@Param("devSerial") String devSerial);

    /**
     * Find mappings by customer ID (including inactive ones, legacy method)
     */
    List<DeviceCustomerMapping> findByCustomerId(String customerId);

    /**
     * Count active devices for a customer (legacy method)
     */
    long countByCustomerIdAndIsActiveTrue(String customerId);
}
