package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.DeviceCustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DeviceCustomerMapping entity.
 * Provides database operations for device to customer mappings.
 */
@Repository
public interface DeviceCustomerMappingRepository extends JpaRepository<DeviceCustomerMapping, Long> {

    /**
     * Find active mapping by device serial number
     * @param devSerial the device serial number
     * @return Optional containing the mapping if found and active
     */
    Optional<DeviceCustomerMapping> findByDevSerialAndIsActiveTrue(String devSerial);

    /**
     * Find all active mappings for a customer
     * @param customerId the customer ID
     * @return list of active mappings for the customer
     */
    List<DeviceCustomerMapping> findByCustomerIdAndIsActiveTrue(String customerId);

    /**
     * Find all active mappings
     * @return list of all active mappings
     */
    List<DeviceCustomerMapping> findByIsActiveTrue();

    /**
     * Check if a device serial exists and is active
     * @param devSerial the device serial number
     * @return true if the device serial has an active mapping
     */
    boolean existsByDevSerialAndIsActiveTrue(String devSerial);

    /**
     * Find mappings by customer ID (including inactive ones)
     * @param customerId the customer ID
     * @return list of all mappings for the customer
     */
    List<DeviceCustomerMapping> findByCustomerId(String customerId);

    /**
     * Custom query to get customer ID by device serial (only active mappings)
     * @param devSerial the device serial number
     * @return customer ID if found
     */
    @Query("SELECT m.customerId FROM DeviceCustomerMapping m WHERE m.devSerial = :devSerial AND m.isActive = true")
    Optional<String> findCustomerIdByDevSerial(@Param("devSerial") String devSerial);

    /**
     * Count active devices for a customer
     * @param customerId the customer ID
     * @return number of active devices for the customer
     */
    long countByCustomerIdAndIsActiveTrue(String customerId);
}