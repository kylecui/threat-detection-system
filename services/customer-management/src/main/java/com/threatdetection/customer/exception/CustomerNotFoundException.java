package com.threatdetection.customer.exception;

/**
 * 客户未找到异常
 */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}
