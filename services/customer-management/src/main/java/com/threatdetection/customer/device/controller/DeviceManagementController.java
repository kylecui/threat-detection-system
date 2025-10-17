package com.threatdetection.customer.device.controller;

import com.threatdetection.customer.device.dto.*;
import com.threatdetection.customer.device.service.DeviceManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备绑定管理控制器
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceManagementController {

    private final DeviceManagementService deviceManagementService;

    /**
     * 绑定单个设备
     *
     * @param customerId 客户ID
     * @param request 设备绑定请求
     * @return 设备映射信息
     */
    @PostMapping
    public ResponseEntity<DeviceMappingResponse> bindDevice(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody DeviceMappingRequest request) {
        
        log.info("POST /api/v1/customers/{}/devices - Binding device: {}", customerId, request.getDevSerial());
        DeviceMappingResponse response = deviceManagementService.bindDevice(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 批量绑定设备
     *
     * @param customerId 客户ID
     * @param request 批量绑定请求
     * @return 批量操作结果
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchOperationResponse> bindDevices(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody BatchDeviceMappingRequest request) {
        
        log.info("POST /api/v1/customers/{}/devices/batch - Binding {} devices", 
                customerId, request.getDevices().size());
        BatchOperationResponse response = deviceManagementService.bindDevices(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 获取客户的所有设备
     *
     * @param customerId 客户ID
     * @param isActive 是否只查询激活设备 (可选)
     * @param pageable 分页参数
     * @return 设备列表
     */
    @GetMapping
    public ResponseEntity<Page<DeviceMappingResponse>> getDevices(
            @PathVariable("customerId") String customerId,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        log.info("GET /api/v1/customers/{}/devices - isActive: {}", customerId, isActive);
        Page<DeviceMappingResponse> devices = deviceManagementService.getCustomerDevices(customerId, isActive, pageable);
        return ResponseEntity.ok(devices);
    }

    /**
     * 检查设备绑定状态
     *
     * @param customerId 客户ID
     * @param devSerial 设备序列号
     * @return 绑定状态
     */
    @GetMapping("/{devSerial}/bound")
    public ResponseEntity<Map<String, Object>> checkDeviceBound(
            @PathVariable("customerId") String customerId,
            @PathVariable("devSerial") String devSerial) {
        
        log.info("GET /api/v1/customers/{}/devices/{}/bound", customerId, devSerial);
        boolean bound = deviceManagementService.isDeviceBound(customerId, devSerial);
        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "devSerial", devSerial,
            "bound", bound
        ));
    }

    /**
     * 获取单个设备详情
     *
     * @param customerId 客户ID
     * @param devSerial 设备序列号
     * @return 设备信息
     */
    @GetMapping("/{devSerial}")
    public ResponseEntity<DeviceMappingResponse> getDevice(
            @PathVariable("customerId") String customerId,
            @PathVariable("devSerial") String devSerial) {
        
        log.info("GET /api/v1/customers/{}/devices/{}", customerId, devSerial);
        DeviceMappingResponse device = deviceManagementService.getDevice(customerId, devSerial);
        return ResponseEntity.ok(device);
    }

    /**
     * 解绑单个设备
     *
     * @param customerId 客户ID
     * @param devSerial 设备序列号
     * @return 无内容
     */
    @DeleteMapping("/{devSerial}")
    public ResponseEntity<Void> unbindDevice(
            @PathVariable("customerId") String customerId,
            @PathVariable("devSerial") String devSerial) {
        
        log.info("DELETE /api/v1/customers/{}/devices/{}", customerId, devSerial);
        deviceManagementService.unbindDevice(customerId, devSerial);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量解绑设备
     *
     * @param customerId 客户ID
     * @param devSerials 设备序列号列表
     * @return 批量操作结果
     */
    @DeleteMapping("/batch")
    public ResponseEntity<BatchOperationResponse> unbindDevices(
            @PathVariable("customerId") String customerId,
            @RequestBody List<String> devSerials) {
        
        log.info("DELETE /api/v1/customers/{}/devices/batch - Unbinding {} devices", 
                customerId, devSerials.size());
        BatchOperationResponse response = deviceManagementService.unbindDevices(customerId, devSerials);
        return ResponseEntity.ok(response);
    }

    /**
     * 同步设备计数
     *
     * @param customerId 客户ID
     * @return 设备配额信息
     */
    @PostMapping("/sync")
    public ResponseEntity<DeviceQuotaResponse> syncDeviceCount(
            @PathVariable("customerId") String customerId) {
        
        log.info("POST /api/v1/customers/{}/devices/sync", customerId);
        DeviceQuotaResponse quota = deviceManagementService.syncDeviceCount(customerId);
        return ResponseEntity.ok(quota);
    }

    /**
     * 获取设备配额信息
     *
     * @param customerId 客户ID
     * @return 配额信息
     */
    @GetMapping("/quota")
    public ResponseEntity<DeviceQuotaResponse> getDeviceQuota(
            @PathVariable("customerId") String customerId) {
        
        log.info("GET /api/v1/customers/{}/devices/quota", customerId);
        DeviceQuotaResponse quota = deviceManagementService.getDeviceQuota(customerId);
        return ResponseEntity.ok(quota);
    }

    /**
     * 激活/停用设备
     *
     * @param customerId 客户ID
     * @param devSerial 设备序列号
     * @param isActive 是否激活
     * @return 更新后的设备信息
     */
    @PatchMapping("/{devSerial}/status")
    public ResponseEntity<DeviceMappingResponse> toggleDeviceStatus(
            @PathVariable("customerId") String customerId,
            @PathVariable("devSerial") String devSerial,
            @RequestParam boolean isActive) {
        
        log.info("PATCH /api/v1/customers/{}/devices/{}/status - isActive: {}", 
                customerId, devSerial, isActive);
        DeviceMappingResponse device = deviceManagementService.toggleDeviceStatus(customerId, devSerial, isActive);
        return ResponseEntity.ok(device);
    }

    /**
     * 更新设备信息
     *
     * @param customerId 客户ID
     * @param devSerial 设备序列号
     * @param request 更新请求
     * @return 更新后的设备信息
     */
    @PutMapping("/{devSerial}")
    public ResponseEntity<DeviceMappingResponse> updateDevice(
            @PathVariable("customerId") String customerId,
            @PathVariable("devSerial") String devSerial,
            @Valid @RequestBody DeviceMappingRequest request) {
        
        log.info("PUT /api/v1/customers/{}/devices/{}", customerId, devSerial);
        DeviceMappingResponse device = deviceManagementService.updateDevice(customerId, devSerial, request);
        return ResponseEntity.ok(device);
    }
}
