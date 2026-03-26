package com.threatdetection.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NetSegmentWeightAlreadyExistsException extends RuntimeException {
    public NetSegmentWeightAlreadyExistsException(String message) {
        super(message);
    }
}
