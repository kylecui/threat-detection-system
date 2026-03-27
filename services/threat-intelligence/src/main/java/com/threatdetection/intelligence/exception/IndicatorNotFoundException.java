package com.threatdetection.intelligence.exception;

public class IndicatorNotFoundException extends RuntimeException {

    public IndicatorNotFoundException(String message) {
        super(message);
    }
}
