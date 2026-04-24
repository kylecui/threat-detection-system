package com.threatdetection.customer.device.dto;

import com.threatdetection.customer.device.model.DeviceMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 设备映射响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMappingResponse {

    private Long id;

    private String devSerial;

    private String customerId;

    private Boolean isActive;

    private String description;

    private Instant createdAt;

    private Instant updatedAt;

    private Integer realHostCount;

    public static DeviceMappingResponse fromEntity(DeviceMapping mapping) {
        return fromEntity(mapping, null);
    }

    public static DeviceMappingResponse fromEntity(DeviceMapping mapping, Integer realHostCount) {
        return DeviceMappingResponse.builder()
                .id(mapping.getId())
                .devSerial(mapping.getDevSerial())
                .customerId(mapping.getCustomerId())
                .isActive(mapping.getIsActive())
                .description(mapping.getDescription())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .realHostCount(realHostCount)
                .build();
    }
}
