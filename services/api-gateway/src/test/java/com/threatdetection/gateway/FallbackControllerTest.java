package com.threatdetection.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.threatdetection.gateway.controller.FallbackController;

class FallbackControllerTest {

    private final FallbackController fallbackController = new FallbackController();

    @Test
    void customerManagementFallbackShouldReturnStructured503Response() {
        var response = fallbackController.customerManagementFallback(exchange());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Customer Management Service");
    }

    @Test
    void dataIngestionFallbackShouldReturnStructured503Response() {
        var response = fallbackController.dataIngestionFallback(exchange());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Data Ingestion Service");
    }

    @Test
    void threatAssessmentFallbackShouldReturnStructured503Response() {
        var response = fallbackController.threatAssessmentFallback(exchange());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Threat Assessment Service");
    }

    @Test
    void alertManagementFallbackShouldReturnStructured503Response() {
        var response = fallbackController.alertManagementFallback(exchange());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertFallbackBody(response.getBody(), "Alert Management Service");
    }

    private void assertFallbackBody(Map<String, Object> body, String expectedServiceName) {
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("timestamp", "status", "error", "message", "requestId");
        assertThat(body.get("status")).isEqualTo(503);
        assertThat(body.get("error")).isEqualTo("Service Unavailable");
        assertThat((String) body.get("message"))
            .contains(expectedServiceName)
            .contains("temporarily unavailable");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/fallback/test").build());
    }
}
