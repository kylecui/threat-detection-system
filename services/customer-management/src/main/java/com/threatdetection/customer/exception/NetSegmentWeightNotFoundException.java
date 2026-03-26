package com.threatdetection.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NetSegmentWeightNotFoundException extends RuntimeException {
    public NetSegmentWeightNotFoundException(String message) {
        super(message);
    }
}
