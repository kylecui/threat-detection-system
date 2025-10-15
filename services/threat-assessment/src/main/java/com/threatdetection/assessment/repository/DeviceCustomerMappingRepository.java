package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.DeviceCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DeviceCustomerMapping entity
 */
@Repository
public interface DeviceCustomerMappingRepository extends JpaRepository<DeviceCustomerMapping, Long> {
    
    /**
     * Find active mapping by device serial
     */
    Optional<DeviceCustomerMapping> findByDevSerialAndIsActiveTrue(String devSerial);
    
    /**
     * Check if mapping exists and is active
     */
    boolean existsByDevSerialAndIsActiveTrue(String devSerial);
    
    /**
     * Find all active mappings
     */
    List<DeviceCustomerMapping> findByIsActiveTrue();
    
    /**
     * Find all mappings for a customer
     */
    List<DeviceCustomerMapping> findByCustomerId(String customerId);
    
    /**
     * Query customer ID by device serial
     */
    @Query("SELECT m.customerId FROM DeviceCustomerMapping m WHERE UPPER(m.devSerial) = UPPER(:devSerial) AND m.isActive = true")
    Optional<String> findCustomerIdByDevSerial(@Param("devSerial") String devSerial);
}
