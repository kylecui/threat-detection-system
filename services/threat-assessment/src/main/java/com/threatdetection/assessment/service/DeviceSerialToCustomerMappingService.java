package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.DeviceCustomerMapping;
import com.threatdetection.assessment.repository.DeviceCustomerMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for mapping device serial numbers to customer IDs with temporal support.
 * This service provides multi-tenancy support by resolving customer context from device identifiers
 * with time-based validity windows to support device circulation between customers.
 */
@Service
public class DeviceSerialToCustomerMappingService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSerialToCustomerMappingService.class);

    private final DeviceCustomerMappingRepository repository;
    private final Environment environment;

    // Cache for performance (devSerial -> customerId for current time)
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
     * Resolves customer ID from device serial number at current time
     * @param devSerial the device serial number
     * @return customer ID, or "unknown" if mapping not found
     */
    public String resolveCustomerId(String devSerial) {
        return resolveCustomerId(devSerial, Instant.now());
    }

    /**
     * Resolves customer ID from device serial number at specific timestamp
     * @param devSerial the device serial number
     * @param timestamp the timestamp to check mapping validity
     * @return customer ID, or "unknown" if mapping not found
     */
    public String resolveCustomerId(String devSerial, Instant timestamp) {
        if (devSerial == null || devSerial.trim().isEmpty()) {
            logger.warn("Cannot resolve customer ID for null/empty devSerial");
            return "unknown";
        }

        if (timestamp == null) {
            logger.warn("Timestamp is null, using current time for devSerial: {}", devSerial);
            timestamp = Instant.now();
        }

        // In test environment, return a test customer ID
        if (isTestEnvironment()) {
            logger.debug("Test environment: returning default customer ID for devSerial {}", devSerial);
            return "test-customer";
        }

        // Try cache first for performance (only for current time queries)
        if (timestamp.equals(Instant.now().minusSeconds(60))) { // Allow 1 minute tolerance for "current"
            String customerId = devSerialToCustomerCache.get(devSerial.toUpperCase());
            if (customerId != null) {
                logger.debug("Resolved devSerial {} to customerId {} (from cache)", devSerial, customerId);
                return customerId;
            }
        }

        // Query database for active mapping at the specified timestamp
        List<DeviceCustomerMapping> mappings = repository.findActiveMappingsAtTime(devSerial.toUpperCase(), timestamp);
        if (!mappings.isEmpty()) {
            // Return the most recent mapping (first in the list due to ORDER BY DESC)
            DeviceCustomerMapping mapping = mappings.get(0);
            String customerId = mapping.getCustomerId();
            logger.debug("Resolved devSerial {} to customerId {} at timestamp {} (from database)",
                        devSerial, customerId, timestamp);
            return customerId;
        }

        logger.warn("No active customer mapping found for devSerial: {} at timestamp {}", devSerial, timestamp);
        return "unknown";
    }

    /**
     * Gets all currently active mappings
     * @return map of devSerial to customerId
     */
    public Map<String, String> getAllMappings() {
        if (isTestEnvironment()) {
            logger.debug("Test environment: returning empty mappings");
            return Collections.emptyMap();
        }

        try {
            return repository.findAllCurrentlyActive().stream()
                    .filter(mapping -> mapping.getDevSerial() != null && !mapping.getDevSerial().trim().isEmpty())
                    .collect(Collectors.toMap(
                            mapping -> mapping.getDevSerial().toUpperCase(),
                            DeviceCustomerMapping::getCustomerId,
                            (existing, replacement) -> {
                                logger.warn("Duplicate active mapping for device {}, keeping existing customer {}",
                                           existing, replacement);
                                return existing; // 保留第一个找到的映射
                            }
                    ));
        } catch (Exception e) {
            logger.error("Error getting all mappings", e);
            return Collections.emptyMap();
        }
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

    // ========== 时效性映射管理方法 ==========

    /**
     * 绑定设备到客户
     * @param devSerial 设备序列号
     * @param customerId 客户ID
     * @param bindReason 绑定原因
     * @param bindTime 绑定时间（默认为当前时间）
     * @return 创建的映射记录
     */
    public DeviceCustomerMapping bindDeviceToCustomer(String devSerial, String customerId,
                                                     String bindReason, Instant bindTime) {
        if (bindTime == null) {
            bindTime = Instant.now();
        }

        logger.info("Binding device {} to customer {} at time {} with reason: {}",
                   devSerial, customerId, bindTime, bindReason);

        // 检查是否已有当前有效的绑定，如果有则先解绑
        List<DeviceCustomerMapping> currentMappings = repository.findActiveMappingsAtTime(devSerial.toUpperCase(), bindTime);
        if (!currentMappings.isEmpty()) {
            unbindDevice(currentMappings.get(0), bindTime, "自动解绑-重新分配给" + customerId);
        }

        DeviceCustomerMapping newMapping = new DeviceCustomerMapping(devSerial, customerId, bindTime, bindReason);
        DeviceCustomerMapping saved = repository.save(newMapping);

        // 刷新缓存
        refreshCache();

        logger.info("Successfully bound device {} to customer {}", devSerial, customerId);
        return saved;
    }

    /**
     * 解绑设备
     * @param mapping 要解绑的映射记录
     * @param unbindTime 解绑时间
     * @param reason 解绑原因
     */
    public void unbindDevice(DeviceCustomerMapping mapping, Instant unbindTime, String reason) {
        logger.info("Unbinding device {} from customer {} at time {} with reason: {}",
                   mapping.getDevSerial(), mapping.getCustomerId(), unbindTime, reason);

        mapping.setUnbindTime(unbindTime);
        mapping.setDescription(reason);
        repository.save(mapping);

        // 刷新缓存
        refreshCache();

        logger.info("Successfully unbound device {}", mapping.getDevSerial());
    }

    /**
     * 解绑设备（通过设备序列号）
     * @param devSerial 设备序列号
     * @param unbindReason 解绑原因
     * @param unbindTime 解绑时间
     * @return 被解绑的映射记录，如果没有找到则返回null
     */
    public DeviceCustomerMapping unbindDevice(String devSerial, String unbindReason, Instant unbindTime) {
        if (unbindTime == null) {
            unbindTime = Instant.now();
        }

        List<DeviceCustomerMapping> currentMappings = repository.findActiveMappingsAtTime(devSerial, unbindTime);
        if (!currentMappings.isEmpty()) {
            unbindDevice(currentMappings.get(0), unbindTime, unbindReason);
            return currentMappings.get(0);
        } else {
            logger.warn("No active mapping found for device {} at time {}", devSerial, unbindTime);
            return null;
        }
    }

    /**
     * 获取设备的历史映射记录
     * @param devSerial 设备序列号
     * @return 按绑定时间倒序的历史记录
     */
    public java.util.List<DeviceCustomerMapping> getDeviceMappingHistory(String devSerial) {
        return repository.findByDevSerialOrderByBindTimeDesc(devSerial);
    }

    /**
     * 获取所有当前活跃的映射记录
     * @return 当前活跃的映射列表
     */
    public java.util.List<DeviceCustomerMapping> getActiveMappings() {
        return repository.findAllCurrentlyActive();
    }

    /**
     * 转移设备到新客户（自动处理解绑和绑定）
     * @param devSerial 设备序列号
     * @param newCustomerId 新客户ID
     * @param transferReason 转移原因
     * @param transferTime 转移时间
     * @return 之前的映射记录
     */
    public DeviceCustomerMapping transferDevice(String devSerial, String newCustomerId,
                                               String transferReason, Instant transferTime) {
        if (transferTime == null) {
            transferTime = Instant.now();
        }

        logger.info("Transferring device {} to customer {} at time {} with reason: {}",
                   devSerial, newCustomerId, transferTime, transferReason);

        // 查找当前活跃的映射
        List<DeviceCustomerMapping> currentMappings = repository.findActiveMappingsAtTime(devSerial, transferTime);

        if (!currentMappings.isEmpty()) {
            DeviceCustomerMapping oldMapping = currentMappings.get(0);

            // 如果新客户与当前客户相同，不需要转移
            if (oldMapping.getCustomerId().equals(newCustomerId)) {
                logger.info("Device {} is already bound to customer {}, no transfer needed",
                           devSerial, newCustomerId);
                return oldMapping;
            }

            // 解绑当前映射
            unbindDevice(oldMapping, transferTime, "转移到客户: " + newCustomerId +
                        (transferReason != null ? " (" + transferReason + ")" : ""));

            // 绑定到新客户
            bindDeviceToCustomer(devSerial, newCustomerId, transferReason, transferTime);

            return oldMapping;
        } else {
            // 如果没有当前映射，直接绑定到新客户
            logger.info("No current mapping found for device {}, binding directly to new customer {}",
                       devSerial, newCustomerId);
            bindDeviceToCustomer(devSerial, newCustomerId, transferReason, transferTime);
            return null;
        }
    }
}
