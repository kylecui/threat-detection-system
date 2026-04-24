package com.threatdetection.customer.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备绑定请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMappingRequest {

    /**
     * 设备序列号
     */
    @NotBlank(message = "Device serial cannot be blank")
    @Size(max = 50, message = "Device serial cannot exceed 50 characters")
    private String devSerial;

    /**
     * 描述信息
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
