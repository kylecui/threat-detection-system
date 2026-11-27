package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.dto.LoginRequest;
import com.threatdetection.gateway.auth.dto.LoginResponse;
import com.threatdetection.gateway.auth.dto.BootstrapRequest;
import com.threatdetection.gateway.auth.service.AuthService;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Authentication REST controller
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/login  — authenticate & return JWT</li>
 *   <li>POST /api/v1/auth/refresh — refresh access token</li>
 *   <li>GET  /api/v1/auth/me     — current user info (requires valid JWT)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/bootstrap/status")
    public Mono<ResponseEntity<Map<String, Object>>> bootstrapStatus() {
        return authService.needsBootstrap()
                .map(required -> ResponseEntity.ok(Map.of(
                        "bootstrapRequired", required,
                        "ready", true)));
    }

    @PostMapping("/bootstrap")
    public Mono<ResponseEntity<Map<String, Object>>> bootstrap(@RequestBody BootstrapRequest request) {
        return authService.bootstrap(request)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(Map.<String, Object>of(
                        "created", true,
                        "username", user.getUsername())))
                .onErrorResume(IllegalArgumentException.class, error ->
                        Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of(
                                "error", error.getMessage()))))
                .onErrorResume(AuthService.BootstrapAlreadyCompletedException.class, error ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of(
                                "error", error.getMessage()))));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request) {
        log.info("Login attempt: user={}", request.getUsername());
        return authService.login(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.warn("Login failed: user={}, reason={}", request.getUsername(), e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(LoginResponse.builder().build()));
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<LoginResponse>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return authService.refreshToken(refreshToken)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.warn("Token refresh failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<LoginResponse.UserInfo>> me(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String username = jwtTokenProvider.getUsernameFromToken(token);
        return authService.getUserInfo(username)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.warn("Get user info failed: user={}, reason={}", username, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }
}
