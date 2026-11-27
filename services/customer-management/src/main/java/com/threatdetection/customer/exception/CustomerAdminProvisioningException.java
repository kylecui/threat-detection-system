package com.threatdetection.customer.exception;

/** Customer and its optional login could not be provisioned atomically. */
public class CustomerAdminProvisioningException extends RuntimeException {

    public CustomerAdminProvisioningException(String message) {
        super(message);
    }
}
