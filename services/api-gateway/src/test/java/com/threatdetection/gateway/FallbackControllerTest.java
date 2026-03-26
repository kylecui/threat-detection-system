package com.threatdetection.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.threatdetection.gateway.controller.FallbackController;

class FallbackControllerTest {

    private final FallbackController fallbackController = new FallbackController();

    @Test
    void customerManagementFallbackShouldReturnStructured503Response() {
        var response = fallbackController.customerManagementFallback();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Customer Management Service");
    }

    @Test
    void dataIngestionFallbackShouldReturnStructured503Response() {
        var response = fallbackController.dataIngestionFallback();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Data Ingestion Service");
    }

    @Test
    void threatAssessmentFallbackShouldReturnStructured503Response() {
        var response = fallbackController.threatAssessmentFallback();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Threat Assessment Service");
    }

    @Test
    void alertManagementFallbackShouldReturnStructured503Response() {
        var response = fallbackController.alertManagementFallback();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Alert Management Service");
    }

    private void assertFallbackBody(Map<String, Object> body, String expectedServiceName) {
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("timestamp", "status", "error", "message");
        assertThat(body.get("status")).isEqualTo(503);
        assertThat(body.get("error")).isEqualTo("Service Unavailable");
        assertThat((String) body.get("message"))
            .contains(expectedServiceName)
            .contains("temporarily unavailable");
    }
}
