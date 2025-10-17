package com.threatdetection.customer.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备配额响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceQuotaResponse {

    /**
     * 客户ID
     */
    @JsonProperty("customer_id")
    private String customerId;

    /**
     * 当前激活设备数
     */
    @JsonProperty("current_devices")
    private long currentDevices;

    /**
     * 最大设备数
     */
    @JsonProperty("max_devices")
    private int maxDevices;

    /**
     * 剩余可用设备数
     */
    @JsonProperty("available_devices")
    private int availableDevices;

    /**
     * 使用率 (0.0-1.0)
     */
    @JsonProperty("usage_rate")
    private double usageRate;

    /**
     * 是否已达上限
     */
    @JsonProperty("quota_exceeded")
    private boolean quotaExceeded;

    /**
     * 计算剩余可用设备数和使用率
     */
    public static DeviceQuotaResponse calculate(String customerId, long currentDevices, int maxDevices) {
        int available = Math.max(0, maxDevices - (int) currentDevices);
        double usageRate = maxDevices > 0 ? (double) currentDevices / maxDevices : 0.0;
        boolean exceeded = currentDevices >= maxDevices;

        return DeviceQuotaResponse.builder()
                .customerId(customerId)
                .currentDevices(currentDevices)
                .maxDevices(maxDevices)
                .availableDevices(available)
                .usageRate(Math.min(1.0, usageRate))
                .quotaExceeded(exceeded)
                .build();
    }
}
