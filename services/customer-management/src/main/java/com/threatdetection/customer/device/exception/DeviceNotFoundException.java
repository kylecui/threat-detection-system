package com.threatdetection.customer.device.exception;

/**
 * 设备未找到异常
 */
public class DeviceNotFoundException extends RuntimeException {
    
    private final String devSerial;

    public DeviceNotFoundException(String devSerial) {
        super(String.format("Device not found: %s", devSerial));
        this.devSerial = devSerial;
    }

    public String getDevSerial() {
        return devSerial;
    }
}
