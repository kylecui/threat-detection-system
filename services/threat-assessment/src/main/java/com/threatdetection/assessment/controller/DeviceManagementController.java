package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.model.DeviceCustomerMapping;
import com.threatdetection.assessment.service.DeviceSerialToCustomerMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 设备管理API控制器
 * 提供设备绑定、解绑和查询功能，支持时效性设备客户映射
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "设备管理API - 支持时效性设备客户映射")
public class DeviceManagementController {

    private final DeviceSerialToCustomerMappingService mappingService;

    /**
     * 绑定设备到客户
     */
    @PostMapping("/bind")
    @Operation(summary = "绑定设备到客户", description = "将设备绑定到指定客户，支持时效性映射")
    public ResponseEntity<DeviceCustomerMapping> bindDeviceToCustomer(
            @Parameter(description = "设备序列号") @RequestParam String deviceSerial,
            @Parameter(description = "客户ID") @RequestParam String customerId,
            @Parameter(description = "绑定原因") @RequestParam(required = false) String bindReason,
            @Parameter(description = "绑定时间，默认当前时间")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant bindTime) {

        log.info("Binding device {} to customer {} at time {}", deviceSerial, customerId, bindTime);

        DeviceCustomerMapping mapping = mappingService.bindDeviceToCustomer(
                deviceSerial, customerId, bindReason, bindTime);

        return ResponseEntity.ok(mapping);
    }

    /**
     * 解绑设备
     */
    @PostMapping("/unbind")
    @Operation(summary = "解绑设备", description = "从当前客户解绑设备")
    public ResponseEntity<DeviceCustomerMapping> unbindDevice(
            @Parameter(description = "设备序列号") @RequestParam String deviceSerial,
            @Parameter(description = "解绑原因") @RequestParam(required = false) String unbindReason,
            @Parameter(description = "解绑时间，默认当前时间")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant unbindTime) {

        log.info("Unbinding device {} at time {}", deviceSerial, unbindTime);

        DeviceCustomerMapping mapping = mappingService.unbindDevice(deviceSerial, unbindReason, unbindTime);

        return ResponseEntity.ok(mapping);
    }

    /**
     * 查询设备在指定时间点的客户
     */
    @GetMapping("/customer")
    @Operation(summary = "查询设备客户映射", description = "查询设备在指定时间点属于哪个客户")
    public ResponseEntity<Map<String, Object>> getDeviceCustomer(
            @Parameter(description = "设备序列号") @RequestParam String deviceSerial,
            @Parameter(description = "查询时间点，默认当前时间")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant timestamp) {

        log.info("Querying customer for device {} at time {}", deviceSerial, timestamp);

        String customerId = mappingService.resolveCustomerId(deviceSerial, timestamp);

        return ResponseEntity.ok(Map.of(
                "deviceSerial", deviceSerial,
                "customerId", customerId,
                "timestamp", timestamp != null ? timestamp : Instant.now(),
                "found", customerId != null
        ));
    }

    /**
     * 查询设备的映射历史
     */
    @GetMapping("/history/{deviceSerial}")
    @Operation(summary = "查询设备映射历史", description = "查询设备的完整客户映射历史")
    public ResponseEntity<List<DeviceCustomerMapping>> getDeviceHistory(
            @Parameter(description = "设备序列号") @PathVariable String deviceSerial) {

        log.info("Querying mapping history for device {}", deviceSerial);

        List<DeviceCustomerMapping> history = mappingService.getDeviceMappingHistory(deviceSerial);

        return ResponseEntity.ok(history);
    }

    /**
     * 查询当前活跃的设备映射
     */
    @GetMapping("/active")
    @Operation(summary = "查询活跃映射", description = "查询所有当前活跃的设备客户映射")
    public ResponseEntity<List<DeviceCustomerMapping>> getActiveMappings() {

        log.info("Querying all active device mappings");

        List<DeviceCustomerMapping> activeMappings = mappingService.getActiveMappings();

        return ResponseEntity.ok(activeMappings);
    }

    /**
     * 转移设备到新客户
     */
    @PostMapping("/transfer")
    @Operation(summary = "转移设备", description = "将设备从当前客户转移到新客户")
    public ResponseEntity<Map<String, Object>> transferDevice(
            @Parameter(description = "设备序列号") @RequestParam String deviceSerial,
            @Parameter(description = "新客户ID") @RequestParam String newCustomerId,
            @Parameter(description = "转移原因") @RequestParam(required = false) String transferReason,
            @Parameter(description = "转移时间，默认当前时间")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant transferTime) {

        log.info("Transferring device {} to customer {} at time {}", deviceSerial, newCustomerId, transferTime);

        DeviceCustomerMapping result = mappingService.transferDevice(
                deviceSerial, newCustomerId, transferReason, transferTime);

        return ResponseEntity.ok(Map.of(
                "deviceSerial", deviceSerial,
                "newCustomerId", newCustomerId,
                "transferTime", transferTime != null ? transferTime : Instant.now(),
                "previousMapping", result
        ));
    }
}