package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.DeviceStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * V1哨兵心跳状态历史数据访问层
 */
@Repository
public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistoryEntity, Long> {

    List<DeviceStatusHistoryEntity> findByDevSerialOrderByReportTimeDesc(String devSerial);

    List<DeviceStatusHistoryEntity> findByCustomerIdOrderByReportTimeDesc(String customerId);
}
