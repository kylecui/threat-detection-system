package com.threatdetection.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldContainExpectedCoreRouteIds() {
        List<String> routeIds = routeLocator.getRoutes()
            .map(route -> route.getId())
            .collectList()
            .block();

        assertThat(routeIds)
            .isNotNull()
            .contains(
                "customer-management-customers",
                "data-ingestion",
                "threat-assessment",
                "alert-management"
            );
    }

    @Test
    void customerManagementFallbackShouldReturn503() {
        webTestClient.get()
            .uri("/fallback/customer-management")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(message -> assertThat((String) message).contains("Customer Management Service"));
    }

    @Test
    void dataIngestionFallbackShouldReturn503() {
        webTestClient.get()
            .uri("/fallback/data-ingestion")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(message -> assertThat((String) message).contains("Data Ingestion Service"));
    }

    @Test
    void threatAssessmentFallbackShouldReturn503() {
        webTestClient.get()
            .uri("/fallback/threat-assessment")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(message -> assertThat((String) message).contains("Threat Assessment Service"));
    }

    @Test
    void alertManagementFallbackShouldReturn503() {
        webTestClient.get()
            .uri("/fallback/alert-management")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(message -> assertThat((String) message).contains("Alert Management Service"));
    }
}
