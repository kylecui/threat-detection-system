package com.threatdetection.gateway.auth.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Role-based authorization and authoritative customer/tenant scoping.
 * Identity headers are populated by {@link JwtAuthenticationFilter}; caller
 * supplied query parameters never widen that authenticated scope.
 */
@Component
public class RbacAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RbacAuthorizationFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/bootstrap/status",
            "/api/v1/auth/bootstrap",
            "/api/v1/system/readiness",
            "/internal/v1/runtime-config/llm/effective"
    );
    private static final Set<String> PUBLIC_PREFIXES = Set.of("/health", "/actuator");
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Pattern CUSTOMER_RESOURCE_PATH = Pattern.compile(
            "^/api/v1/customers/([^/]+)(?:/.*)?$");
    private static final Pattern TENANT_CUSTOMERS_PATH = Pattern.compile(
            "^/api/v1/customers/by-tenant/(\\d+)$");
    private static final Set<String> NON_RESOURCE_SEGMENTS = Set.of(
            "search", "status", "by-tenant");

    private static final List<PathMethodRule> SUPER_ADMIN_ONLY_RULES = List.of(
            new PathMethodRule("/api/v1/tenants/**", HttpMethod.POST),
            new PathMethodRule("/api/v1/tenants/**", HttpMethod.PUT),
            new PathMethodRule("/api/v1/tenants/**", HttpMethod.DELETE),
            new PathMethodRule("/api/v1/system-config/**", HttpMethod.PUT),
            new PathMethodRule("/api/v1/system-config/**", HttpMethod.DELETE),
            new PathMethodRule("/api/v1/llm-providers/**", HttpMethod.POST),
            new PathMethodRule("/api/v1/llm-providers/**", HttpMethod.PUT),
            new PathMethodRule("/api/v1/llm-providers/**", HttpMethod.DELETE),
            new PathMethodRule("/api/v1/customers/*/hard", HttpMethod.DELETE)
    );
    private static final List<String> SUPER_ADMIN_ONLY_PATHS = List.of(
            // SMTP configuration and the cross-customer read/test endpoints
            // contain platform-wide data and can trigger real delivery.
            "/api/notification-config/**"
    );
    private static final List<String> CUSTOMER_ADMIN_WRITE_PATHS = List.of(
            "/api/v1/users/**",
            "/api/v1/user-config/**",
            "/api/v1/customers/*/notification-config/**"
    );
    private static final Set<String> INTERNAL_ONLY_PATHS = Set.of("/api/v1/logs/ingest");

    private final DatabaseClient databaseClient;

    public RbacAuthorizationFilter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String rolesHeader = exchange.getRequest().getHeaders().getFirst("X-Auth-Roles");
        String tokenCustomerId = exchange.getRequest().getHeaders().getFirst("X-Customer-Id");
        String tenantIdHeader = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return chain.filter(exchange);
        }

        List<String> roles = Arrays.asList(rolesHeader.split(","));
        if (roles.contains("SUPER_ADMIN")) {
            return chain.filter(exchange);
        }

        if (SUPER_ADMIN_ONLY_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return forbidden(exchange.getResponse(), "This endpoint requires SUPER_ADMIN role");
        }

        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.DELETE.equals(method)
                && "true".equalsIgnoreCase(exchange.getRequest().getQueryParams().getFirst("permanent"))) {
            return forbidden(exchange.getResponse(),
                    "Permanent customer deletion requires SUPER_ADMIN role");
        }
        if (isSuperAdminOnlyPath(path, method)) {
            return forbidden(exchange.getResponse(), "This endpoint requires SUPER_ADMIN role");
        }
        if (INTERNAL_ONLY_PATHS.contains(path)
                && !"true".equals(exchange.getRequest().getHeaders().getFirst("X-Internal-Service"))) {
            return forbidden(exchange.getResponse(), "Internal-only endpoint");
        }
        if (roles.contains("CUSTOMER_USER") && !roles.contains("TENANT_ADMIN")
                && method != null && WRITE_METHODS.contains(method)) {
            return forbidden(exchange.getResponse(), "CUSTOMER_USER role has read-only access");
        }

        if (roles.contains("TENANT_ADMIN")) {
            Long tenantId = parsePositiveLong(tenantIdHeader);
            if (tenantId == null) {
                return forbidden(exchange.getResponse(), "TENANT_ADMIN token is missing tenant scope");
            }
            Matcher tenantMatcher = TENANT_CUSTOMERS_PATH.matcher(path);
            if (tenantMatcher.matches() && !tenantMatcher.group(1).equals(String.valueOf(tenantId))) {
                return forbidden(exchange.getResponse(), "Tenant scope mismatch");
            }

            String pathCustomerId = extractCustomerResourceId(path);
            if (pathCustomerId != null) {
                return customerBelongsToTenant(pathCustomerId, tenantId)
                        .flatMap(allowed -> allowed
                                ? chain.filter(scopeRequest(exchange, null, tenantId))
                                : forbidden(exchange.getResponse(),
                                        "Customer is outside the caller tenant"));
            }
            return chain.filter(scopeRequest(exchange, null, tenantId));
        }

        if (roles.contains("CUSTOMER_ADMIN")) {
            Long tenantId = parsePositiveLong(tenantIdHeader);
            if (tenantId == null || tokenCustomerId == null || tokenCustomerId.isBlank()) {
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_ADMIN token is missing tenant or customer scope");
            }
            if (TENANT_CUSTOMERS_PATH.matcher(path).matches()) {
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_ADMIN cannot enumerate tenant customers");
            }
            String pathCustomerId = extractCustomerResourceId(path);
            if (pathCustomerId != null && !tokenCustomerId.equals(pathCustomerId)) {
                return forbidden(exchange.getResponse(), "Customer scope mismatch");
            }
            if (method != null && WRITE_METHODS.contains(method)
                    && CUSTOMER_ADMIN_WRITE_PATHS.stream()
                    .noneMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_ADMIN cannot modify resources outside its customer administration scope");
            }
            return chain.filter(scopeRequest(exchange, tokenCustomerId, tenantId));
        }

        if (roles.contains("CUSTOMER_USER")) {
            if (tokenCustomerId == null || tokenCustomerId.isBlank()) {
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_USER token is missing customer scope");
            }
            if (TENANT_CUSTOMERS_PATH.matcher(path).matches()) {
                return forbidden(exchange.getResponse(),
                        "CUSTOMER_USER cannot enumerate tenant customers");
            }
            String pathCustomerId = extractCustomerResourceId(path);
            if (pathCustomerId != null && !tokenCustomerId.equals(pathCustomerId)) {
                return forbidden(exchange.getResponse(), "Customer scope mismatch");
            }
            return chain.filter(scopeRequest(exchange, tokenCustomerId, null));
        }

        return chain.filter(exchange);
    }

    private ServerWebExchange scopeRequest(ServerWebExchange exchange, String customerId, Long tenantId) {
        ServerHttpRequest request = exchange.getRequest();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(request.getURI());
        if (customerId != null) {
            uriBuilder.replaceQueryParam("customer_id", customerId);
            uriBuilder.replaceQueryParam("customerId", customerId);
        }
        if (tenantId != null) {
            uriBuilder.replaceQueryParam("tenantId", tenantId);
            uriBuilder.replaceQueryParam("tenant_id", tenantId);
        }

        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .uri(uriBuilder.build(true).toUri());
        requestBuilder.headers(headers -> {
            headers.set("X-Customer-Id", customerId != null ? customerId : "");
            headers.set("X-Tenant-Id", tenantId != null ? String.valueOf(tenantId) : "");
        });
        return exchange.mutate().request(requestBuilder.build()).build();
    }

    private String extractCustomerResourceId(String path) {
        Matcher matcher = CUSTOMER_RESOURCE_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        String firstSegment = matcher.group(1);
        return NON_RESOURCE_SEGMENTS.contains(firstSegment) ? null : firstSegment;
    }

    private Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Mono<Boolean> customerBelongsToTenant(String customerId, Long tenantId) {
        return databaseClient.sql(
                        "SELECT EXISTS (SELECT 1 FROM customers " +
                        "WHERE customer_id = :customerId AND tenant_id = :tenantId) AS allowed")
                .bind("customerId", customerId)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("allowed", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isSuperAdminOnlyPath(String path, HttpMethod method) {
        if (method == null) {
            return false;
        }
        return SUPER_ADMIN_ONLY_RULES.stream()
                .anyMatch(rule -> rule.method().equals(method)
                        && PATH_MATCHER.match(rule.pathPattern(), path));
    }

    private record PathMethodRule(String pathPattern, HttpMethod method) {
    }

    private Mono<Void> forbidden(ServerHttpResponse response, String message) {
        log.warn("Authorization denied: {}", message);
        if (response.isCommitted()) {
            log.warn("Cannot write 403 because the response is already committed");
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\":\"Forbidden\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, Instant.now());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 6;
    }
}
