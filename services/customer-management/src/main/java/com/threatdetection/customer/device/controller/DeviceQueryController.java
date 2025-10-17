package com.threatdetection.customer.device.controller;

import com.threatdetection.customer.dto.CustomerResponse;
import com.threatdetection.customer.device.service.DeviceManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 设备查询控制器 (全局设备查询)
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceQueryController {

    private final DeviceManagementService deviceManagementService;

    /**
     * 根据设备序列号查找客户
     *
     * GET /api/v1/devices/{devSerial}/customer
     */
    @GetMapping("/{devSerial}/customer")
    public ResponseEntity<CustomerResponse> findCustomerByDevice(
            @PathVariable("devSerial") String devSerial) {
        
        log.info("GET /api/v1/devices/{}/customer", devSerial);
        CustomerResponse customer = deviceManagementService.findCustomerByDevice(devSerial);
        return ResponseEntity.ok(customer);
    }
}
