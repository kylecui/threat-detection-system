package com.threatdetection.customer.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusExceptionKeepsOriginalStatusAndReason() {
        var response = handler.handleResponseStatus(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Select a tenant before creating a customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry(
                "message", "Select a tenant before creating a customer");
    }
}
