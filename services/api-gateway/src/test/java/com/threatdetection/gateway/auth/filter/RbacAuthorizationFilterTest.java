package com.threatdetection.gateway.auth.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RbacAuthorizationFilterTest {

    private static final List<HttpMethod> METHODS = List.of(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS);

    private final RbacAuthorizationFilter filter =
            new RbacAuthorizationFilter(mock(DatabaseClient.class));

    @Test
    void notificationConfigurationRejectsEveryMethodForNonSuperAdminRoles() {
        for (String role : List.of("CUSTOMER_USER", "CUSTOMER_ADMIN", "TENANT_ADMIN")) {
            for (HttpMethod method : METHODS) {
                AtomicBoolean invoked = new AtomicBoolean(false);
                MockServerWebExchange exchange = exchange(method, role);
                GatewayFilterChain chain = ignored -> {
                    invoked.set(true);
                    return Mono.empty();
                };

                filter.filter(exchange, chain).block();

                assertThat(exchange.getResponse().getStatusCode())
                        .as("%s %s", role, method)
                        .isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(invoked).as("downstream chain for %s %s", role, method).isFalse();
            }
        }
    }

    @Test
    void notificationConfigurationAllowsSuperAdminToReachGatewayRoute() {
        for (HttpMethod method : METHODS) {
            AtomicBoolean invoked = new AtomicBoolean(false);
            MockServerWebExchange exchange = exchange(method, "SUPER_ADMIN");

            filter.filter(exchange, ignored -> {
                invoked.set(true);
                return Mono.empty();
            }).block();

            assertThat(invoked).as("downstream chain for %s", method).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    private MockServerWebExchange exchange(HttpMethod method, String role) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .method(method, "/api/notification-config/customer")
                .header("X-Auth-Roles", role)
                .header("X-Customer-Id", "customer-1")
                .header("X-Tenant-Id", "1")
                .build());
    }
}
