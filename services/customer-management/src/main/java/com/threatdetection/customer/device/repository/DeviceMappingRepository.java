package com.threatdetection.customer.device.repository;

import com.threatdetection.customer.device.model.DeviceMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceMappingRepository extends JpaRepository<DeviceMapping, Long> {

    /**
     * 根据设备序列号查找
     */
    Optional<DeviceMapping> findByDevSerial(String devSerial);

    /**
     * 检查设备序列号是否存在
     */
    boolean existsByDevSerial(String devSerial);

    /**
     * 查找客户的所有设备
     */
    Page<DeviceMapping> findByCustomerId(String customerId, Pageable pageable);

    /**
     * 查找客户的所有激活设备
     */
    Page<DeviceMapping> findByCustomerIdAndIsActive(String customerId, Boolean isActive, Pageable pageable);

    /**
     * 查找客户的所有激活设备 (列表形式)
     */
    List<DeviceMapping> findByCustomerIdAndIsActive(String customerId, Boolean isActive);

    /**
     * 统计客户的激活设备数量
     */
    @Query("SELECT COUNT(d) FROM DeviceMapping d WHERE d.customerId = :customerId AND d.isActive = true")
    long countActiveDevicesByCustomerId(@Param("customerId") String customerId);

    /**
     * 统计客户的所有设备数量 (包括未激活)
     */
    long countByCustomerId(String customerId);

    /**
     * 检查设备是否属于指定客户
     */
    boolean existsByDevSerialAndCustomerId(String devSerial, String customerId);

    /**
     * 批量查找设备
     */
    List<DeviceMapping> findByDevSerialIn(List<String> devSerials);

    void deleteByCustomerId(String customerId);

    /**
     * SUM(real_host_count) across V1 heartbeats (device_status_history) and
     * V2 heartbeats (topology_snapshots) for a customer's active devices.
     *
     * V1: latest real_host_count per dev_serial from device_status_history
     * V2: latest active_decoy_count per device_id from topology_snapshots (only for devices NOT in V1)
     */
    @Query(value =
        "SELECT COALESCE(SUM(latest_count), 0) FROM (" +
        "  SELECT latest_count FROM (" +
        "    SELECT DISTINCT ON (dsh.dev_serial) dsh.real_host_count AS latest_count" +
        "    FROM device_customer_mapping dcm" +
        "    JOIN device_status_history dsh ON UPPER(dcm.dev_serial) = UPPER(dsh.dev_serial)" +
        "    WHERE dcm.customer_id = :customerId AND dcm.is_active = true" +
        "    ORDER BY dsh.dev_serial, dsh.report_time DESC" +
        "  ) v1" +
        "  UNION ALL" +
        "  SELECT latest_count FROM (" +
        "    SELECT DISTINCT ON (ts.device_id) ts.active_decoy_count AS latest_count" +
        "    FROM device_customer_mapping dcm" +
        "    JOIN topology_snapshots ts ON UPPER(dcm.dev_serial) = UPPER(ts.device_id)" +
        "    WHERE dcm.customer_id = :customerId AND dcm.is_active = true" +
        "    AND UPPER(dcm.dev_serial) NOT IN (" +
        "      SELECT DISTINCT UPPER(dev_serial) FROM device_status_history" +
        "    )" +
        "    ORDER BY ts.device_id, ts.snapshot_time DESC" +
        "  ) v2" +
        ") sub",
        nativeQuery = true)
    long sumProtectedHostCountByCustomerId(@Param("customerId") String customerId);

    /**
     * Latest real_host_count for a specific dev_serial from V1 heartbeats.
     * Returns null if no heartbeat exists.
     */
    @Query(value =
        "SELECT dsh.real_host_count FROM device_status_history dsh" +
        " WHERE UPPER(dsh.dev_serial) = UPPER(:devSerial)" +
        " ORDER BY dsh.report_time DESC LIMIT 1",
        nativeQuery = true)
    Integer findLatestRealHostCountByDevSerial(@Param("devSerial") String devSerial);
}
