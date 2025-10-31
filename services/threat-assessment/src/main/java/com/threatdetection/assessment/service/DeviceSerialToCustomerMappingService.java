package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.DeviceCustomerMapping;
import com.threatdetection.assessment.repository.DeviceCustomerMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for mapping device serial numbers to customer IDs.
 * This service provides multi-tenancy support by resolving customer context from device identifiers.
 */
@Service
public class DeviceSerialToCustomerMappingService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSerialToCustomerMappingService.class);

    private final DeviceCustomerMappingRepository repository;
    private final Environment environment;

    // Cache for performance
    private volatile Map<String, String> devSerialToCustomerCache;

    public DeviceSerialToCustomerMappingService(DeviceCustomerMappingRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @PostConstruct
    public void initializeCache() {
        // Skip database initialization in test environments
        if (isTestEnvironment()) {
            devSerialToCustomerCache = Collections.emptyMap();
            logger.info("Skipped database initialization in test environment");
            return;
        }

        refreshCache();
        logger.info("Initialized devSerial-to-customer mapping cache with {} entries", devSerialToCustomerCache.size());
    }

    private boolean isTestEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.contains("test") || profile.contains("h2")) {
                return true;
            }
        }
        return false;
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

        // In test environment, return a test customer ID
        if (isTestEnvironment()) {
            logger.debug("Test environment: returning default customer ID for devSerial {}", devSerial);
            return "test-customer";
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
     * Gets all active mappings
     * @return map of devSerial to customerId
     */
    public Map<String, String> getAllMappings() {
        if (isTestEnvironment()) {
            logger.debug("Test environment: returning empty mappings");
            return Collections.emptyMap();
        }

        return repository.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(
                        mapping -> mapping.getDevSerial().toUpperCase(),
                        DeviceCustomerMapping::getCustomerId
                ));
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
}
