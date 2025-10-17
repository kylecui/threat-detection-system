package com.threatdetection.customer.exception;

/**
 * 客户已存在异常
 */
public class CustomerAlreadyExistsException extends RuntimeException {
    public CustomerAlreadyExistsException(String message) {
        super(message);
    }
}
