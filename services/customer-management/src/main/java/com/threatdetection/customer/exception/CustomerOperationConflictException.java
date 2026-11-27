package com.threatdetection.customer.exception;

import java.util.Map;

/** A lifecycle transition cannot be completed without losing related data. */
public class CustomerOperationConflictException extends RuntimeException {

    private final Map<String, Long> dependencies;

    public CustomerOperationConflictException(String message, Map<String, Long> dependencies) {
        super(message);
        this.dependencies = Map.copyOf(dependencies);
    }

    public Map<String, Long> getDependencies() {
        return dependencies;
    }
}
