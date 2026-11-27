package com.threatdetection.customer.device.service;

import com.threatdetection.customer.exception.CustomerNotFoundException;
import com.threatdetection.customer.exception.CustomerOperationConflictException;
import com.threatdetection.customer.model.Customer;
import com.threatdetection.customer.repository.CustomerRepository;
import com.threatdetection.customer.service.CustomerService;
import com.threatdetection.customer.device.dto.*;
import com.threatdetection.customer.device.exception.DeviceAlreadyBoundException;
import com.threatdetection.customer.device.exception.DeviceNotFoundException;
import com.threatdetection.customer.device.exception.DeviceQuotaExceededException;
import com.threatdetection.customer.device.model.DeviceMapping;
import com.threatdetection.customer.device.repository.DeviceMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 设备绑定管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceManagementService {

    private final DeviceMappingRepository deviceMappingRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final JdbcTemplate jdbcTemplate;
    private final DeviceTelemetryPolicy telemetryPolicy;
    private final DeviceBackfillService deviceBackfillService;

    /**
     * 绑定单个设备到客户
     */
    @Transactional
    public DeviceMappingResponse bindDevice(String customerId, DeviceMappingRequest request) {
        String devSerial = normalizeSerial(request.getDevSerial());
        log.info("Binding device {} to customer {}", devSerial, customerId);

        acquireBindingLock("customer:" + customerId);
        acquireBindingLock("device:" + devSerial);

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        requireOperationalCustomer(customer);

        // 2. 检查设备是否已被绑定
        Optional<DeviceMapping> existing = findCurrentMapping(devSerial);
        if (existing.isPresent()) {
            throw new DeviceAlreadyBoundException(devSerial, existing.get().getCustomerId());
        }

        // 3. 检查设备配额
        long currentDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        if (currentDeviceCount >= customer.getMaxDevices()) {
            throw new DeviceQuotaExceededException(customerId, (int) currentDeviceCount, customer.getMaxDevices());
        }

        // 4. 创建设备映射
        DeviceMapping mapping = DeviceMapping.builder()
                .devSerial(devSerial)
                .customerId(customerId)
                .description(request.getDescription())
                .isActive(true)
                .bindTime(Instant.now())
                .unbindTime(null)
                .bindReason("manual_bind")
                .build();

        DeviceMapping saved = deviceMappingRepository.saveAndFlush(mapping);

        // 5. 更新客户的当前设备数
        customerService.updateDeviceCount(customerId, (int) (currentDeviceCount + 1));

        log.info("Successfully bound device {} to customer {}", devSerial, customerId);
        deviceBackfillService.backfillCustomerId(devSerial, customerId);
        return DeviceMappingResponse.fromEntity(saved);
    }

    /**
     * 批量绑定设备
     */
    @Transactional
    public BatchOperationResponse bindDevices(String customerId, BatchDeviceMappingRequest request) {
        log.info("Batch binding {} devices to customer {}", request.getDevices().size(), customerId);

        acquireBindingLock("customer:" + customerId);
        request.getDevices().stream()
                .map(DeviceMappingRequest::getDevSerial)
                .map(this::normalizeSerial)
                .distinct()
                .sorted()
                .forEach(serial -> acquireBindingLock("device:" + serial));

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        requireOperationalCustomer(customer);

        // 2. 获取当前设备数
        long currentDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .total(request.getDevices().size())
                .succeeded(0)
                .failed(0)
                .successfulDevices(new ArrayList<>())
                .failures(new ArrayList<>())
                .build();

        // 3. 逐个绑定设备
        Set<String> requestSerials = new HashSet<>();
        for (DeviceMappingRequest deviceRequest : request.getDevices()) {
            String devSerial = normalizeSerial(deviceRequest.getDevSerial());
            try {
                if (!requestSerials.add(devSerial)) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(devSerial)
                            .reason("Duplicate serial in the same request")
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }
                // 检查设备是否已绑定
                Optional<DeviceMapping> existing = findCurrentMapping(devSerial);
                if (existing.isPresent()) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(devSerial)
                            .reason("Already bound to customer: " + existing.get().getCustomerId())
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                // 检查配额
                if (currentDeviceCount + response.getSucceeded() >= customer.getMaxDevices()) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(devSerial)
                            .reason(String.format("Quota exceeded: %d/%d devices", 
                                    currentDeviceCount + response.getSucceeded(), customer.getMaxDevices()))
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                // 创建绑定
                DeviceMapping mapping = DeviceMapping.builder()
                        .devSerial(devSerial)
                        .customerId(customerId)
                        .description(deviceRequest.getDescription())
                        .isActive(true)
                        .bindTime(Instant.now())
                        .unbindTime(null)
                        .bindReason("manual_bind")
                        .build();

                deviceMappingRepository.saveAndFlush(mapping);
                response.getSuccessfulDevices().add(devSerial);
                response.setSucceeded(response.getSucceeded() + 1);

            } catch (Exception e) {
                log.error("Failed to bind device {}: {}", devSerial, e.getMessage());
                response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                        .devSerial(devSerial)
                        .reason(e.getMessage())
                        .build());
                response.setFailed(response.getFailed() + 1);
            }
        }

        // 4. 更新客户的当前设备数
        if (response.getSucceeded() > 0) {
            customerService.updateDeviceCount(customerId, (int) (currentDeviceCount + response.getSucceeded()));
            for (String devSerial : response.getSuccessfulDevices()) {
                deviceBackfillService.backfillCustomerId(devSerial, customerId);
            }
        }

        log.info("Batch binding completed: {}/{} succeeded, {}/{} failed", 
                response.getSucceeded(), response.getTotal(), response.getFailed(), response.getTotal());

        return response;
    }

    /**
     * 获取客户的所有设备
     */
    @Transactional(readOnly = true)
    public Page<DeviceMappingResponse> getCustomerDevices(String customerId, Boolean isActive, Pageable pageable) {
        log.debug("Getting devices for customer {}, isActive: {}", customerId, isActive);

        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        Page<DeviceMapping> devices;
        if (Boolean.TRUE.equals(isActive)) {
            devices = deviceMappingRepository.findByCustomerIdAndIsActiveTrueAndUnbindTimeIsNull(
                    customerId, pageable);
        } else if (Boolean.FALSE.equals(isActive)) {
            devices = deviceMappingRepository.findByCustomerIdAndIsActive(customerId, isActive, pageable);
        } else {
            devices = deviceMappingRepository.findByCustomerIdAndIsActiveTrueAndUnbindTimeIsNull(
                    customerId, pageable);
        }

        Map<Long, DeviceTelemetrySnapshot> telemetry = loadTelemetry(devices.getContent());
        return devices.map(mapping -> toResponse(mapping, telemetry.get(mapping.getId())));
    }

    /**
     * 获取设备详情
     */
    @Transactional(readOnly = true)
    public DeviceMappingResponse getDevice(String customerId, String devSerial) {
        log.debug("Getting device {} for customer {}", devSerial, customerId);

        DeviceMapping mapping = findCurrentMapping(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        // 验证设备是否属于该客户
        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        DeviceTelemetrySnapshot telemetry = loadTelemetry(List.of(mapping)).get(mapping.getId());
        return toResponse(mapping, telemetry);
    }

    /**
     * Paginated raw events for one device. Binding is checked first so callers cannot use
     * a serial number to read another customer's data.
     */
    @Transactional(readOnly = true)
    public Page<DeviceAttackEventResponse> getDeviceAttackEvents(
            String customerId, String devSerial, Pageable pageable) {
        DeviceMapping mapping = findCurrentMapping(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));
        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attack_events " +
                "WHERE customer_id = ? AND UPPER(dev_serial) = UPPER(?)",
                Long.class, customerId, devSerial);

        List<DeviceAttackEventResponse> content = jdbcTemplate.query(
                "SELECT id, dev_serial, attack_mac, attack_ip, response_ip, response_port, " +
                "event_timestamp, received_at FROM attack_events " +
                "WHERE customer_id = ? AND UPPER(dev_serial) = UPPER(?) " +
                "ORDER BY event_timestamp DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> DeviceAttackEventResponse.builder()
                        .id(rs.getLong("id"))
                        .devSerial(rs.getString("dev_serial"))
                        .attackMac(rs.getString("attack_mac"))
                        .attackIp(rs.getString("attack_ip"))
                        .responseIp(rs.getString("response_ip"))
                        .responsePort(rs.getInt("response_port"))
                        .eventTimestamp(toInstant(rs.getTimestamp("event_timestamp")))
                        .receivedAt(toInstant(rs.getTimestamp("received_at")))
                        .build(),
                customerId,
                devSerial,
                pageable.getPageSize(),
                pageable.getOffset());

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /**
     * 解绑单个设备
     */
    @Transactional
    public void unbindDevice(String customerId, String devSerial) {
        log.info("Unbinding device {} from customer {}", devSerial, customerId);

        String normalizedSerial = normalizeSerial(devSerial);
        acquireBindingLock("customer:" + customerId);
        acquireBindingLock("device:" + normalizedSerial);

        // 1. 查找设备映射
        DeviceMapping mapping = findCurrentMapping(normalizedSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        // 2. 验证设备是否属于该客户
        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        // 3. 删除映射
        mapping.setIsActive(false);
        mapping.setUnbindTime(Instant.now());
        mapping.setBindReason("manual_unbind");
        deviceMappingRepository.saveAndFlush(mapping);

        // 4. 更新客户的当前设备数
        long currentDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        customerService.updateDeviceCount(customerId, (int) currentDeviceCount);

        log.info("Successfully unbound device {} from customer {}", devSerial, customerId);
    }

    /**
     * 批量解绑设备
     */
    @Transactional
    public BatchOperationResponse unbindDevices(String customerId, List<String> devSerials) {
        log.info("Batch unbinding {} devices from customer {}", devSerials.size(), customerId);

        acquireBindingLock("customer:" + customerId);
        devSerials.stream()
                .map(this::normalizeSerial)
                .distinct()
                .sorted()
                .forEach(serial -> acquireBindingLock("device:" + serial));

        BatchOperationResponse response = BatchOperationResponse.builder()
                .total(devSerials.size())
                .succeeded(0)
                .failed(0)
                .successfulDevices(new ArrayList<>())
                .failures(new ArrayList<>())
                .build();

        for (String devSerial : devSerials) {
            String normalizedSerial = normalizeSerial(devSerial);
            try {
                DeviceMapping mapping = findCurrentMapping(normalizedSerial)
                        .orElseThrow(() -> new DeviceNotFoundException(devSerial));

                if (!mapping.getCustomerId().equals(customerId)) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(devSerial)
                            .reason("Device does not belong to customer")
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                mapping.setIsActive(false);
                mapping.setUnbindTime(Instant.now());
                mapping.setBindReason("manual_unbind");
                deviceMappingRepository.saveAndFlush(mapping);
                response.getSuccessfulDevices().add(normalizedSerial);
                response.setSucceeded(response.getSucceeded() + 1);

            } catch (Exception e) {
                log.error("Failed to unbind device {}: {}", devSerial, e.getMessage());
                response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                        .devSerial(devSerial)
                        .reason(e.getMessage())
                        .build());
                response.setFailed(response.getFailed() + 1);
            }
        }

        // 更新客户的当前设备数
        if (response.getSucceeded() > 0) {
            long currentDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
            customerService.updateDeviceCount(customerId, (int) currentDeviceCount);
        }

        log.info("Batch unbinding completed: {}/{} succeeded, {}/{} failed",
                response.getSucceeded(), response.getTotal(), response.getFailed(), response.getTotal());

        return response;
    }

    /**
     * 同步客户的设备计数
     */
    @Transactional
    public DeviceQuotaResponse syncDeviceCount(String customerId) {
        log.info("Syncing device count for customer {}", customerId);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        long activeDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        long protectedHostCount = deviceMappingRepository.sumProtectedHostCountByCustomerId(customerId);

        customerService.updateDeviceCount(customerId, (int) activeDeviceCount);

        log.info("Synced device count for customer {}: devices={}/{}, protectedHosts={}",
            customerId, activeDeviceCount, customer.getMaxDevices(), protectedHostCount);

        return DeviceQuotaResponse.calculate(customerId, activeDeviceCount, customer.getMaxDevices(), protectedHostCount);
    }

    /**
     * 获取设备配额信息
     */
    @Transactional(readOnly = true)
    public DeviceQuotaResponse getDeviceQuota(String customerId) {
        log.debug("Getting device quota for customer {}", customerId);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        long activeDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        long protectedHostCount = deviceMappingRepository.sumProtectedHostCountByCustomerId(customerId);

        return DeviceQuotaResponse.calculate(customerId, activeDeviceCount, customer.getMaxDevices(), protectedHostCount);
    }

    /**
     * 切换设备激活状态
     */
    @Transactional
    public DeviceMappingResponse toggleDeviceStatus(String customerId, String devSerial, boolean isActive) {
        log.info("Toggling device {} status to {} for customer {}", devSerial, isActive, customerId);

        String normalizedSerial = normalizeSerial(devSerial);
        acquireBindingLock("customer:" + customerId);
        acquireBindingLock("device:" + normalizedSerial);

        DeviceMapping mapping = findCurrentMapping(normalizedSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        mapping.setIsActive(isActive);
        if (!isActive) {
            mapping.setUnbindTime(Instant.now());
            mapping.setBindReason("status_disabled");
        }
        DeviceMapping updated = deviceMappingRepository.saveAndFlush(mapping);

        // 更新设备计数
        long activeDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        customerService.updateDeviceCount(customerId, (int) activeDeviceCount);

        return DeviceMappingResponse.fromEntity(updated);
    }

    /**
     * 更新设备信息
     */
    @Transactional
    public DeviceMappingResponse updateDevice(String customerId, String devSerial, DeviceMappingRequest request) {
        log.info("Updating device {} for customer {}", devSerial, customerId);

        DeviceMapping mapping = findCurrentMapping(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        // 更新描述信息
        if (request.getDescription() != null) {
            mapping.setDescription(request.getDescription());
        }

        DeviceMapping updated = deviceMappingRepository.save(mapping);
        log.info("Device {} updated for customer {}", devSerial, customerId);

        return DeviceMappingResponse.fromEntity(updated);
    }

    /**
     * 检查设备是否已绑定到指定客户
     */
    public boolean isDeviceBound(String customerId, String devSerial) {
        log.debug("Checking if device {} is bound to customer {}", devSerial, customerId);
        
        return findCurrentMapping(devSerial)
                .map(mapping -> mapping.getCustomerId().equals(customerId) && Boolean.TRUE.equals(mapping.getIsActive()))
                .orElse(false);
    }

    /**
     * 根据设备序列号查找客户
     */
    public com.threatdetection.customer.dto.CustomerResponse findCustomerByDevice(String devSerial) {
        log.info("Finding customer for device: {}", devSerial);

        DeviceMapping mapping = findCurrentMapping(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        com.threatdetection.customer.model.Customer customer = customerService.getCustomerEntity(mapping.getCustomerId());
        return com.threatdetection.customer.dto.CustomerResponse.fromEntity(customer);
    }

    private Optional<DeviceMapping> findCurrentMapping(String devSerial) {
        return deviceMappingRepository
                .findFirstByDevSerialIgnoreCaseAndIsActiveTrueAndUnbindTimeIsNullOrderByBindTimeDescIdDesc(
                        normalizeSerial(devSerial));
    }

    private String normalizeSerial(String devSerial) {
        if (devSerial == null || devSerial.isBlank()) {
            throw new IllegalArgumentException("Device serial cannot be blank");
        }
        return devSerial.trim().toUpperCase(Locale.ROOT);
    }

    private void acquireBindingLock(String lockKey) {
        jdbcTemplate.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                lockKey);
    }

    private void requireOperationalCustomer(Customer customer) {
        if (customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
            throw new CustomerOperationConflictException(
                    "Devices can only be bound to an ACTIVE customer", Map.of());
        }
    }

    private Map<Long, DeviceTelemetrySnapshot> loadTelemetry(List<DeviceMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = mappings.stream().map(item -> "?").collect(Collectors.joining(","));
        Object[] ids = mappings.stream().map(DeviceMapping::getId).toArray();
        String sql = "SELECT dcm.id, hb.report_time AS last_heartbeat_at, hb.real_host_count, " +
                "att.last_attack_at, att.last_ingested_at, COALESCE(att.attack_events_24h, 0) AS attack_events_24h, " +
                "thr.last_threat_at " +
                "FROM device_customer_mapping dcm " +
                "LEFT JOIN LATERAL (" +
                "  SELECT report_time, real_host_count FROM device_status_history " +
                "  WHERE UPPER(dev_serial) = UPPER(dcm.dev_serial) " +
                "  ORDER BY report_time DESC LIMIT 1" +
                ") hb ON TRUE " +
                "LEFT JOIN LATERAL (" +
                "  SELECT MAX(event_timestamp) AS last_attack_at, MAX(received_at) AS last_ingested_at, " +
                "         COUNT(*) FILTER (WHERE received_at >= NOW() - INTERVAL '24 hours') AS attack_events_24h " +
                "  FROM attack_events ae WHERE ae.customer_id = dcm.customer_id " +
                "    AND UPPER(ae.dev_serial) = UPPER(dcm.dev_serial)" +
                ") att ON TRUE " +
                "LEFT JOIN LATERAL (" +
                "  SELECT MAX(ta.assessment_time) AS last_threat_at FROM threat_assessments ta " +
                "  WHERE ta.customer_id = dcm.customer_id AND EXISTS (" +
                "    SELECT 1 FROM attack_events ae2 WHERE ae2.customer_id = dcm.customer_id " +
                "      AND UPPER(ae2.dev_serial) = UPPER(dcm.dev_serial) AND ae2.attack_mac = ta.attack_mac" +
                "  )" +
                ") thr ON TRUE WHERE dcm.id IN (" + placeholders + ")";

        Map<Long, DeviceTelemetrySnapshot> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long id = rs.getLong("id");
            result.put(id, new DeviceTelemetrySnapshot(
                    toInstant(rs.getTimestamp("last_heartbeat_at")),
                    (Integer) rs.getObject("real_host_count"),
                    toInstant(rs.getTimestamp("last_attack_at")),
                    toInstant(rs.getTimestamp("last_ingested_at")),
                    rs.getLong("attack_events_24h"),
                    toInstant(rs.getTimestamp("last_threat_at"))));
        }, ids);
        return result;
    }

    private DeviceMappingResponse toResponse(DeviceMapping mapping, DeviceTelemetrySnapshot telemetry) {
        DeviceTelemetrySnapshot data = telemetry == null ? DeviceTelemetrySnapshot.empty() : telemetry;
        boolean active = Boolean.TRUE.equals(mapping.getIsActive());
        Instant now = Instant.now();

        Instant lastIngestedAt = latest(data.lastHeartbeatAt(), data.lastIngestedAt());
        String heartbeatState = telemetryPolicy.heartbeatState(active, data.lastHeartbeatAt(), now);
        String dataActivityState = telemetryPolicy.dataActivityState(active, lastIngestedAt, now);
        // Backward-compatible alias for existing API consumers. New clients should use heartbeatState.
        String connectionState = heartbeatState;

        String pipelineState;
        if (!active) {
            pipelineState = "DISABLED";
        } else if (data.lastHeartbeatAt() == null && data.lastAttackAt() == null) {
            pipelineState = "WAITING_FOR_DATA";
        } else if (data.lastAttackAt() == null) {
            pipelineState = "NO_ATTACK_DATA";
        } else if (data.lastThreatAt() != null && !data.lastThreatAt().isBefore(data.lastAttackAt())) {
            pipelineState = "PROCESSED";
        } else if (data.lastAttackAt().isBefore(now.minus(Duration.ofMinutes(10)))) {
            pipelineState = "PROCESSING_DELAY";
        } else {
            pipelineState = "RECEIVING";
        }

        return DeviceMappingResponse.fromEntity(
                mapping,
                data.realHostCount(),
                data.lastHeartbeatAt(),
                data.lastAttackAt(),
                lastIngestedAt,
                data.attackEvents24h(),
                data.lastThreatAt(),
                connectionState,
                heartbeatState,
                dataActivityState,
                telemetryPolicy.getExpectedHeartbeatIntervalSeconds(),
                telemetryPolicy.getHeartbeatDelayedAfterSeconds(),
                telemetryPolicy.getHeartbeatOfflineAfterSeconds(),
                pipelineState);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Instant latest(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private record DeviceTelemetrySnapshot(
            Instant lastHeartbeatAt,
            Integer realHostCount,
            Instant lastAttackAt,
            Instant lastIngestedAt,
            long attackEvents24h,
            Instant lastThreatAt) {
        private static DeviceTelemetrySnapshot empty() {
            return new DeviceTelemetrySnapshot(null, null, null, null, 0L, null);
        }
    }
}
