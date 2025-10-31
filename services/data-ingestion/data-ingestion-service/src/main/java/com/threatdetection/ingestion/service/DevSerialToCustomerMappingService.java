package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.DeviceCustomerMapping;
import com.threatdetection.ingestion.repository.DeviceCustomerMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for mapping device serial numbers to customer IDs.
 * This service provides multi-tenancy support by resolving customer context from device identifiers.
 * Now uses database-backed storage instead of in-memory mapping.
 */
@Service
public class DevSerialToCustomerMappingService {

    private static final Logger logger = LoggerFactory.getLogger(DevSerialToCustomerMappingService.class);

    private final DeviceCustomerMappingRepository repository;

    // Cache for performance (optional - can be removed if real-time consistency is required)
    private volatile Map<String, String> devSerialToCustomerCache;

    public DevSerialToCustomerMappingService(DeviceCustomerMappingRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initializeCache() {
        refreshCache();
        logger.info("Initialized devSerial-to-customer mapping cache with {} entries", devSerialToCustomerCache.size());
    }

    /**
     * Resolves customer ID from device serial number
     * @param devSerial the device serial number
     * @return customer ID, or "unknown" if mapping not found
     */
    public String resolveCustomerId(String devSerial) {
        if (devSerial == null || devSerial.trim().isEmpty()) {
            logger.warn("Cannot resolve customer ID for null/empty devSerial");
            return "unknown";
        }

        // Try cache first for performance
        String customerId = devSerialToCustomerCache.get(devSerial.toUpperCase());
        if (customerId != null) {
            logger.debug("Resolved devSerial {} to customerId {} (from cache)", devSerial, customerId);
            return customerId;
        }

        // Fallback to database query
        Optional<String> dbResult = repository.findCustomerIdByDevSerial(devSerial.toUpperCase());
        if (dbResult.isPresent()) {
            customerId = dbResult.get();
            // Update cache
            devSerialToCustomerCache.put(devSerial.toUpperCase(), customerId);
            logger.debug("Resolved devSerial {} to customerId {} (from database)", devSerial, customerId);
            return customerId;
        }

        logger.warn("No customer mapping found for devSerial: {}, using 'unknown'", devSerial);
        return "unknown";
    }

    /**
     * Adds or updates a devSerial to customer mapping
     * @param devSerial the device serial number
     * @param customerId the customer ID
     * @return true if successful, false otherwise
     */
    public boolean addMapping(String devSerial, String customerId) {
        return addMapping(devSerial, customerId, null);
    }

    /**
     * Adds or updates a devSerial to customer mapping with description
     * @param devSerial the device serial number
     * @param customerId the customer ID
     * @param description optional description
     * @return true if successful, false otherwise
     */
    public boolean addMapping(String devSerial, String customerId, String description) {
        if (devSerial == null || customerId == null) {
            logger.warn("Cannot add mapping with null devSerial or customerId");
            return false;
        }

        try {
            Optional<DeviceCustomerMapping> existing = repository.findByDevSerialAndIsActiveTrue(devSerial.toUpperCase());
            DeviceCustomerMapping mapping;

            if (existing.isPresent()) {
                // Update existing mapping
                mapping = existing.get();
                mapping.setCustomerId(customerId);
                if (description != null) {
                    mapping.setDescription(description);
                }
                logger.info("Updated mapping: devSerial {} -> customerId {}", devSerial, customerId);
            } else {
                // Create new mapping
                mapping = new DeviceCustomerMapping(devSerial.toUpperCase(), customerId, description);
                logger.info("Created new mapping: devSerial {} -> customerId {}", devSerial, customerId);
            }

            repository.save(mapping);
            refreshCache(); // Refresh cache after changes
            return true;
        } catch (Exception e) {
            logger.error("Failed to add mapping for devSerial: {}", devSerial, e);
            return false;
        }
    }

    /**
     * Removes a devSerial mapping (soft delete - marks as inactive)
     * @param devSerial the device serial number to remove
     * @return true if successful, false otherwise
     */
    public boolean removeMapping(String devSerial) {
        if (devSerial == null) {
            return false;
        }

        try {
            Optional<DeviceCustomerMapping> mapping = repository.findByDevSerialAndIsActiveTrue(devSerial.toUpperCase());
            if (mapping.isPresent()) {
                DeviceCustomerMapping deviceMapping = mapping.get();
                deviceMapping.setIsActive(false);
                repository.save(deviceMapping);
                refreshCache(); // Refresh cache after changes
                logger.info("Soft deleted mapping for devSerial: {}", devSerial);
                return true;
            } else {
                logger.warn("No active mapping found for devSerial: {}", devSerial);
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to remove mapping for devSerial: {}", devSerial, e);
            return false;
        }
    }

    /**
     * Gets all active mappings
     * @return map of devSerial to customerId
     */
    public Map<String, String> getAllMappings() {
        return repository.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(
                        mapping -> mapping.getDevSerial().toUpperCase(),
                        DeviceCustomerMapping::getCustomerId,
                        (existing, replacement) -> {
                            // Handle duplicate keys by keeping the most recent record
                            logger.warn("Duplicate mapping found for devSerial: {}, keeping existing customerId: {}, ignoring: {}",
                                       existing, replacement);
                            return existing; // Keep the first one encountered
                        }
                ));
    }

    /**
     * Gets all device mappings for a customer
     * @param customerId the customer ID
     * @return list of device mappings for the customer
     */
    public List<DeviceCustomerMapping> getMappingsForCustomer(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    /**
     * Refreshes the in-memory cache from database
     */
    public void refreshCache() {
        devSerialToCustomerCache = getAllMappings();
        logger.debug("Refreshed cache with {} mappings", devSerialToCustomerCache.size());
    }

    /**
     * Gets cache statistics
     * @return number of cached mappings
     */
    public int getCacheSize() {
        return devSerialToCustomerCache.size();
    }

    /**
     * Checks if a device serial has an active mapping
     * @param devSerial the device serial number
     * @return true if mapping exists and is active
     */
    public boolean hasMapping(String devSerial) {
        return devSerial != null && repository.existsByDevSerialAndIsActiveTrue(devSerial.toUpperCase());
    }
}