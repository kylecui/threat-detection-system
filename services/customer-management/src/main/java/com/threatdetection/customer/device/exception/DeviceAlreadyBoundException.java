package com.threatdetection.customer.device.exception;

/**
 * 设备已绑定异常
 */
public class DeviceAlreadyBoundException extends RuntimeException {
    
    private final String devSerial;
    private final String boundToCustomerId;

    public DeviceAlreadyBoundException(String devSerial, String boundToCustomerId) {
        super(String.format("Device '%s' is already bound to customer '%s'", 
              devSerial, boundToCustomerId));
        this.devSerial = devSerial;
        this.boundToCustomerId = boundToCustomerId;
    }

    public String getDevSerial() {
        return devSerial;
    }

    public String getBoundToCustomerId() {
        return boundToCustomerId;
    }
}
