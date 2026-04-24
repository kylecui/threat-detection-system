package com.threatdetection.customer.device.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量设备绑定请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeviceMappingRequest {

    /**
     * 设备列表
     */
    @NotEmpty(message = "Device list cannot be empty")
    @Size(max = 100, message = "Cannot bind more than 100 devices at once")
    @Valid
    private List<DeviceMappingRequest> devices;
}
