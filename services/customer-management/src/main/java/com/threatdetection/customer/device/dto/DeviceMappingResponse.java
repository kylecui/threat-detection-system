package com.threatdetection.customer.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("dev_serial")
    private String devSerial;
    
    @JsonProperty("customer_id")
    private String customerId;
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("real_host_count")
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
