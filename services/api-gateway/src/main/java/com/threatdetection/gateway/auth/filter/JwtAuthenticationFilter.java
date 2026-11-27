package com.threatdetection.gateway.auth.filter;

import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.service.AccountLifecycleService;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
import java.util.Optional;
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
    private final AuthUserRepository authUserRepository;
    private final AccountLifecycleService accountLifecycleService;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/bootstrap/status",
            "/api/v1/auth/bootstrap",
            "/api/v1/system/readiness",
            "/internal/v1/runtime-config/llm/effective"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
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
            return unauthorized(exchange.getResponse(), "UNAUTHORIZED",
                    "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);

        // Parse claims and inject downstream headers
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            String username = claims.getSubject();
            Object userIdClaim = claims.get("userId");
            Object rolesClaim = claims.get("roles");
            if (username == null || username.isBlank()
                    || !(userIdClaim instanceof Number)
                    || !(rolesClaim instanceof List<?> rawRoles)
                    || rawRoles.stream().anyMatch(role -> !(role instanceof String))) {
                return unauthorized(exchange.getResponse(), "TOKEN_INVALID",
                        "Token is missing required identity claims");
            }
            Long userId = ((Number) userIdClaim).longValue();
            List<String> roles = rawRoles.stream().map(String.class::cast).toList();
            /*
             * Do not attach switchIfEmpty() after chain.filter(). GatewayFilterChain
             * returns Mono<Void>, which completes without an item even on success. A
             * downstream 200 response would therefore be mistaken for a missing user
             * and a second 401 would be written after the response was committed.
             */
            return authUserRepository.findById(userId)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(currentUser -> {
                        if (currentUser.isEmpty()) {
                            return unauthorized(exchange.getResponse(), "UNAUTHORIZED",
                                    "Account no longer exists");
                        }

                        AuthUser user = currentUser.get();
                        return authorizeCurrentAccount(user, username)
                                .defaultIfEmpty(false)
                                .flatMap(allowed -> {
                                    if (!allowed) {
                                        return unauthorized(exchange.getResponse(), "UNAUTHORIZED",
                                                "Account or customer is no longer active");
                                    }
                                    String tenantId = user.getTenantId() != null
                                            ? String.valueOf(user.getTenantId()) : "";
                                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                            .headers(headers -> {
                                                headers.set("X-Auth-Username", user.getUsername());
                                                headers.set("X-Auth-UserId", String.valueOf(user.getId()));
                                                headers.set("X-Auth-Roles", String.join(",", roles));
                                                headers.set("X-Customer-Id",
                                                        user.getCustomerId() != null ? user.getCustomerId() : "");
                                                headers.set("X-Tenant-Id", tenantId);
                                            })
                                            .build();
                                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                                });
                    });
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT rejected for path {}", path);
            return unauthorized(exchange.getResponse(), "TOKEN_EXPIRED", "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT rejected for path {}: {}", path, e.getMessage());
            return unauthorized(exchange.getResponse(), "TOKEN_INVALID", "Token is invalid");
        } catch (Exception e) {
            log.error("JWT parsing error: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "TOKEN_INVALID", "Token parsing failed");
        }
    }

    private Mono<Boolean> authorizeCurrentAccount(AuthUser user, String tokenUsername) {
        if (!user.getUsername().equals(tokenUsername)) {
            return Mono.just(false);
        }
        return accountLifecycleService.isOperational(user);
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

    private Mono<Void> unauthorized(ServerHttpResponse response, String code, String message) {
        if (response.isCommitted()) {
            log.warn("Cannot write 401 because the response is already committed: {}", message);
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\":\"Unauthorized\",\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                code, message, Instant.now().toString());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // After logging (HIGHEST_PRECEDENCE) and rate-limit (+1)
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
