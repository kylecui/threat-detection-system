package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.DeviceInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceInventoryRepository extends JpaRepository<DeviceInventoryEntity, String> {
    List<DeviceInventoryEntity> findByCustomerId(String customerId);
}
