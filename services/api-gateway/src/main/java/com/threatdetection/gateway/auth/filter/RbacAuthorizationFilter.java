package com.threatdetection.gateway.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Role-Based Access Control GlobalFilter.
 *
 * <p>Runs immediately after {@link JwtAuthenticationFilter}.
 * Reads X-Auth-Roles and X-Customer-Id headers injected by the JWT filter
 * and enforces:
 * <ul>
 *   <li>SUPER_ADMIN — full access (all customers, all operations)</li>
 *   <li>TENANT_ADMIN — full CRUD but scoped to own customerId</li>
 *   <li>CUSTOMER_USER — read-only, scoped to own customerId</li>
 * </ul>
 *
 * <p>Customer scoping works by overwriting the {@code customer_id} query
 * parameter with the JWT's customerId, preventing privilege escalation.
 */
@Slf4j
@Component
public class RbacAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/health",
            "/actuator"
    );

    private static final Set<HttpMethod> WRITE_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip public endpoints (no roles injected yet)
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Read identity headers (set by JwtAuthenticationFilter)
        String rolesHeader = exchange.getRequest().getHeaders().getFirst("X-Auth-Roles");
        String tokenCustomerId = exchange.getRequest().getHeaders().getFirst("X-Customer-Id");

        // If no roles header, the request was not authenticated (should have been rejected
        // by JwtAuthenticationFilter already, but guard here for safety)
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return chain.filter(exchange);  // let JwtAuthenticationFilter handle 401
        }

        List<String> roles = Arrays.asList(rolesHeader.split(","));

        // SUPER_ADMIN — full access, no scoping
        if (roles.contains("SUPER_ADMIN")) {
            return chain.filter(exchange);
        }

        // CUSTOMER_USER — read-only enforcement
        if (roles.contains("CUSTOMER_USER") && !roles.contains("TENANT_ADMIN")) {
            HttpMethod method = exchange.getRequest().getMethod();
            if (method != null && WRITE_METHODS.contains(method)) {
                log.warn("CUSTOMER_USER attempted write: user={}, path={}, method={}",
                        exchange.getRequest().getHeaders().getFirst("X-Auth-Username"),
                        path, method);
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_USER role has read-only access");
            }
        }

        // Scope customer_id for non-SUPER_ADMIN users
        if (tokenCustomerId != null && !tokenCustomerId.isBlank()) {
            String tenantIdHeader = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");

            // Overwrite customer_id param to enforce tenant isolation
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Customer-Id", tokenCustomerId)
                    .header("X-Tenant-Id", tenantIdHeader != null ? tenantIdHeader : "")
                    .build();

            // Replace query param customer_id with the JWT value
            java.net.URI originalUri = mutatedRequest.getURI();
            String query = originalUri.getRawQuery();
            if (query != null) {
                // Remove existing customer_id param and replace
                query = query.replaceAll("customer_id=[^&]*&?", "");
                if (query.endsWith("&")) {
                    query = query.substring(0, query.length() - 1);
                }
                query = query.isEmpty()
                        ? "customer_id=" + tokenCustomerId
                        : query + "&customer_id=" + tokenCustomerId;
            } else {
                query = "customer_id=" + tokenCustomerId;
            }

            try {
                java.net.URI newUri = new java.net.URI(
                        originalUri.getScheme(),
                        originalUri.getAuthority(),
                        originalUri.getPath(),
                        query,
                        originalUri.getFragment());
                mutatedRequest = mutatedRequest.mutate().uri(newUri).build();
            } catch (java.net.URISyntaxException e) {
                log.error("Failed to rewrite URI for customer scoping: {}", e.getMessage());
            }

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> forbidden(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\":\"Forbidden\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, Instant.now().toString());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Immediately after JwtAuthenticationFilter (+5)
        return Ordered.HIGHEST_PRECEDENCE + 6;
    }
}
