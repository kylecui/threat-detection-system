package com.threatdetection.customer.device.service;

import com.threatdetection.customer.exception.CustomerNotFoundException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 绑定单个设备到客户
     */
    @Transactional
    public DeviceMappingResponse bindDevice(String customerId, DeviceMappingRequest request) {
        log.info("Binding device {} to customer {}", request.getDevSerial(), customerId);

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // 2. 检查设备是否已被绑定
        if (deviceMappingRepository.existsByDevSerial(request.getDevSerial())) {
            DeviceMapping existingMapping = deviceMappingRepository.findByDevSerial(request.getDevSerial())
                    .orElseThrow();
            throw new DeviceAlreadyBoundException(request.getDevSerial(), existingMapping.getCustomerId());
        }

        // 3. 检查设备配额
        long currentDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);
        if (currentDeviceCount >= customer.getMaxDevices()) {
            throw new DeviceQuotaExceededException(customerId, (int) currentDeviceCount, customer.getMaxDevices());
        }

        // 4. 创建设备映射
        DeviceMapping mapping = DeviceMapping.builder()
                .devSerial(request.getDevSerial())
                .customerId(customerId)
                .description(request.getDescription())
                .isActive(true)
                .build();

        DeviceMapping saved = deviceMappingRepository.save(mapping);

        // 5. 更新客户的当前设备数
        customerService.updateDeviceCount(customerId, (int) (currentDeviceCount + 1));

        log.info("Successfully bound device {} to customer {}", request.getDevSerial(), customerId);
        return DeviceMappingResponse.fromEntity(saved);
    }

    /**
     * 批量绑定设备
     */
    @Transactional
    public BatchOperationResponse bindDevices(String customerId, BatchDeviceMappingRequest request) {
        log.info("Batch binding {} devices to customer {}", request.getDevices().size(), customerId);

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

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
        for (DeviceMappingRequest deviceRequest : request.getDevices()) {
            try {
                // 检查设备是否已绑定
                if (deviceMappingRepository.existsByDevSerial(deviceRequest.getDevSerial())) {
                    DeviceMapping existingMapping = deviceMappingRepository.findByDevSerial(deviceRequest.getDevSerial())
                            .orElseThrow();
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(deviceRequest.getDevSerial())
                            .reason("Already bound to customer: " + existingMapping.getCustomerId())
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                // 检查配额
                if (currentDeviceCount + response.getSucceeded() >= customer.getMaxDevices()) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(deviceRequest.getDevSerial())
                            .reason(String.format("Quota exceeded: %d/%d devices", 
                                    currentDeviceCount + response.getSucceeded(), customer.getMaxDevices()))
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                // 创建绑定
                DeviceMapping mapping = DeviceMapping.builder()
                        .devSerial(deviceRequest.getDevSerial())
                        .customerId(customerId)
                        .description(deviceRequest.getDescription())
                        .isActive(true)
                        .build();

                deviceMappingRepository.save(mapping);
                response.getSuccessfulDevices().add(deviceRequest.getDevSerial());
                response.setSucceeded(response.getSucceeded() + 1);

            } catch (Exception e) {
                log.error("Failed to bind device {}: {}", deviceRequest.getDevSerial(), e.getMessage());
                response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                        .devSerial(deviceRequest.getDevSerial())
                        .reason(e.getMessage())
                        .build());
                response.setFailed(response.getFailed() + 1);
            }
        }

        // 4. 更新客户的当前设备数
        if (response.getSucceeded() > 0) {
            customerService.updateDeviceCount(customerId, (int) (currentDeviceCount + response.getSucceeded()));
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

        // 检查客户是否存在
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        Page<DeviceMapping> devices;
        if (isActive != null) {
            devices = deviceMappingRepository.findByCustomerIdAndIsActive(customerId, isActive, pageable);
        } else {
            devices = deviceMappingRepository.findByCustomerId(customerId, pageable);
        }

        return devices.map(DeviceMappingResponse::fromEntity);
    }

    /**
     * 获取设备详情
     */
    @Transactional(readOnly = true)
    public DeviceMappingResponse getDevice(String customerId, String devSerial) {
        log.debug("Getting device {} for customer {}", devSerial, customerId);

        DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        // 验证设备是否属于该客户
        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        return DeviceMappingResponse.fromEntity(mapping);
    }

    /**
     * 解绑单个设备
     */
    @Transactional
    public void unbindDevice(String customerId, String devSerial) {
        log.info("Unbinding device {} from customer {}", devSerial, customerId);

        // 1. 查找设备映射
        DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        // 2. 验证设备是否属于该客户
        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        // 3. 删除映射
        deviceMappingRepository.delete(mapping);

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

        BatchOperationResponse response = BatchOperationResponse.builder()
                .total(devSerials.size())
                .succeeded(0)
                .failed(0)
                .successfulDevices(new ArrayList<>())
                .failures(new ArrayList<>())
                .build();

        for (String devSerial : devSerials) {
            try {
                DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
                        .orElseThrow(() -> new DeviceNotFoundException(devSerial));

                if (!mapping.getCustomerId().equals(customerId)) {
                    response.getFailures().add(BatchOperationResponse.FailureDetail.builder()
                            .devSerial(devSerial)
                            .reason("Device does not belong to customer")
                            .build());
                    response.setFailed(response.getFailed() + 1);
                    continue;
                }

                deviceMappingRepository.delete(mapping);
                response.getSuccessfulDevices().add(devSerial);
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

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // 2. 统计激活设备数
        long activeDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);

        // 3. 更新客户记录
        customerService.updateDeviceCount(customerId, (int) activeDeviceCount);

        log.info("Synced device count for customer {}: {}/{}", customerId, activeDeviceCount, customer.getMaxDevices());

        return DeviceQuotaResponse.calculate(customerId, activeDeviceCount, customer.getMaxDevices());
    }

    /**
     * 获取设备配额信息
     */
    @Transactional(readOnly = true)
    public DeviceQuotaResponse getDeviceQuota(String customerId) {
        log.debug("Getting device quota for customer {}", customerId);

        // 1. 检查客户是否存在
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // 2. 统计激活设备数
        long activeDeviceCount = deviceMappingRepository.countActiveDevicesByCustomerId(customerId);

        return DeviceQuotaResponse.calculate(customerId, activeDeviceCount, customer.getMaxDevices());
    }

    /**
     * 切换设备激活状态
     */
    @Transactional
    public DeviceMappingResponse toggleDeviceStatus(String customerId, String devSerial, boolean isActive) {
        log.info("Toggling device {} status to {} for customer {}", devSerial, isActive, customerId);

        DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        if (!mapping.getCustomerId().equals(customerId)) {
            throw new DeviceNotFoundException(devSerial);
        }

        mapping.setIsActive(isActive);
        DeviceMapping updated = deviceMappingRepository.save(mapping);

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

        DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
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
        
        return deviceMappingRepository.findByDevSerial(devSerial)
                .map(mapping -> mapping.getCustomerId().equals(customerId) && Boolean.TRUE.equals(mapping.getIsActive()))
                .orElse(false);
    }

    /**
     * 根据设备序列号查找客户
     */
    public com.threatdetection.customer.dto.CustomerResponse findCustomerByDevice(String devSerial) {
        log.info("Finding customer for device: {}", devSerial);

        DeviceMapping mapping = deviceMappingRepository.findByDevSerial(devSerial)
                .orElseThrow(() -> new DeviceNotFoundException(devSerial));

        com.threatdetection.customer.model.Customer customer = customerService.getCustomerEntity(mapping.getCustomerId());
        return com.threatdetection.customer.dto.CustomerResponse.fromEntity(customer);
    }
}
