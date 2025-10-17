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

/**
 * 设备映射数据访问层
 */
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

    /**
     * 删除客户的所有设备映射
     */
    void deleteByCustomerId(String customerId);
}
