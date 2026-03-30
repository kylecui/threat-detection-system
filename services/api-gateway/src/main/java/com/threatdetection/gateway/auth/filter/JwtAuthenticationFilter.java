package com.threatdetection.gateway.auth.filter;

import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * JWT Authentication GlobalFilter.
 *
 * <p>Runs after logging (HIGHEST_PRECEDENCE) and rate-limit (+1).
 * Extracts Bearer token from Authorization header, validates it, and
 * injects identity headers for downstream services:
 * <ul>
 *   <li>X-Auth-Username</li>
 *   <li>X-Auth-UserId</li>
 *   <li>X-Auth-Roles (comma-separated)</li>
 *   <li>X-Customer-Id</li>
 * </ul>
 *
 * <p>Public paths (auth, health, actuator) are allowed without a token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;

    /** Paths that do NOT require a valid JWT. */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/health",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange.getResponse(), "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return unauthorized(exchange.getResponse(), "Invalid or expired token");
        }

        // Parse claims and inject downstream headers
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            String username = claims.getSubject();
            Long userId = ((Number) claims.get("userId")).longValue();
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            String customerId = (String) claims.get("customerId");

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Auth-Username", username)
                    .header("X-Auth-UserId", String.valueOf(userId))
                    .header("X-Auth-Roles", String.join(",", roles))
                    .header("X-Customer-Id", customerId != null ? customerId : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.error("JWT parsing error: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "Token parsing failed");
        }
    }

    private boolean isPublicPath(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, Instant.now().toString());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // After logging (HIGHEST_PRECEDENCE) and rate-limit (+1)
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
