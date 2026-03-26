package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.DiscoveredHostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredHostRepository extends JpaRepository<DiscoveredHostEntity, Long> {
    Optional<DiscoveredHostEntity> findByDeviceIdAndMacAddress(String deviceId, String macAddress);
    List<DiscoveredHostEntity> findByDeviceId(String deviceId);
    List<DiscoveredHostEntity> findByCustomerId(String customerId);
}
