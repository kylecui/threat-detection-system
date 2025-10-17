package com.threatdetection.customer.device.exception;

/**
 * 设备配额超限异常
 */
public class DeviceQuotaExceededException extends RuntimeException {
    
    private final String customerId;
    private final int currentDevices;
    private final int maxDevices;

    public DeviceQuotaExceededException(String customerId, int currentDevices, int maxDevices) {
        super(String.format("Device quota exceeded for customer '%s': %d/%d devices used", 
              customerId, currentDevices, maxDevices));
        this.customerId = customerId;
        this.currentDevices = currentDevices;
        this.maxDevices = maxDevices;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getCurrentDevices() {
        return currentDevices;
    }

    public int getMaxDevices() {
        return maxDevices;
    }
}
