package com.threatdetection.customer.device.dto;

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
    private String customerId;

    /**
     * 当前激活设备数
     */
    private long currentDevices;

    /**
     * 最大设备数
     */
    private int maxDevices;

    /**
     * 剩余可用设备数
     */
    private int availableDevices;

    /**
     * 使用率 (0.0-1.0)
     */
    private double usageRate;

    /**
     * 是否已达上限
     */
    private boolean quotaExceeded;

    private long protectedHostCount;

    public static DeviceQuotaResponse calculate(String customerId, long currentDevices, int maxDevices) {
        return calculate(customerId, currentDevices, maxDevices, 0);
    }

    public static DeviceQuotaResponse calculate(String customerId, long currentDevices, int maxDevices, long protectedHostCount) {
        int available = Math.max(0, maxDevices - (int) currentDevices);
        double usageRate = maxDevices > 0 ? (double) protectedHostCount / maxDevices : 0.0;
        boolean exceeded = protectedHostCount >= maxDevices;

        return DeviceQuotaResponse.builder()
                .customerId(customerId)
                .currentDevices(currentDevices)
                .maxDevices(maxDevices)
                .availableDevices(available)
                .usageRate(Math.min(1.0, usageRate))
                .quotaExceeded(exceeded)
                .protectedHostCount(protectedHostCount)
                .build();
    }
}
