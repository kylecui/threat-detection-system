package com.threatdetection.customer.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Delegates real channel tests to the service that owns notification delivery. */
@Component
public class NotificationDeliveryClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestTemplate restTemplate;

    public NotificationDeliveryClient(
            RestTemplateBuilder builder,
            @Value("${services.alert-management.base-url:http://localhost:8082}") String baseUrl,
            @Value("${internal.alert-service-token}") String alertServiceToken) {
        if (alertServiceToken == null || alertServiceToken.length() < 64) {
            throw new IllegalStateException(
                    "ALERT_INTERNAL_SERVICE_TOKEN must contain at least 64 characters");
        }
        this.restTemplate = builder
                .rootUri(baseUrl)
                .defaultHeader("X-Internal-Service-Token", alertServiceToken)
                .build();
    }

    public Map<String, Object> testCustomerConfig(String customerId) {
        try {
            Map<String, Object> response = restTemplate.exchange(
                    "/api/notification-config/customer/{customerId}/test",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    RESPONSE_TYPE,
                    customerId).getBody();
            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(502), "Alert service returned an empty test result");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    exception.getStatusCode(), "Notification delivery test failed: " + exception.getStatusText(), exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(502), "Alert service is unavailable", exception);
        }
    }
}
