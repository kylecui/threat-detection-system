package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.TopologySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopologySnapshotRepository extends JpaRepository<TopologySnapshotEntity, Long> {
    List<TopologySnapshotEntity> findByDeviceIdOrderBySnapshotTimeDesc(String deviceId);
    List<TopologySnapshotEntity> findByCustomerIdOrderBySnapshotTimeDesc(String customerId);
}
