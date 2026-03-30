package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.ConfigAssignment;
import com.threatdetection.gateway.auth.entity.LlmProvider;
import com.threatdetection.gateway.auth.repository.ConfigAssignmentRepository;
import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM provider → customer assignment CRUD with multi-tenant RBAC.
 * <p>SUPER_ADMIN/TENANT_ADMIN: assign providers. CUSTOMER_USER: view own assignment.
 * <p>Routes: GET /api/v1/config-assignments, GET/PUT/DELETE /api/v1/config-assignments/{customerId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config-assignments")
@RequiredArgsConstructor
public class ConfigAssignmentController {

    private final ConfigAssignmentRepository assignmentRepository;
    private final LlmProviderRepository providerRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/{customerId}")
    public Mono<ResponseEntity<Map<String, Object>>> getAssignment(
            @PathVariable String customerId, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canViewCustomer(caller, customerId)) {
                        return Mono.<ResponseEntity<Map<String, Object>>>just(
                                ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return assignmentRepository.findByCustomerId(customerId)
                            .flatMap(assignment -> providerRepository.findById(assignment.getLlmProviderId())
                                    .map(provider -> {
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("customerId", assignment.getCustomerId());
                                        result.put("llmProviderId", assignment.getLlmProviderId());
                                        result.put("assignedBy", assignment.getAssignedBy());
                                        result.put("createdAt", assignment.getCreatedAt());
                                        result.put("updatedAt", assignment.getUpdatedAt());
                                        result.put("providerName", provider.getName());
                                        result.put("providerModel", provider.getModel());
                                        result.put("providerBaseUrl", provider.getBaseUrl());
                                        result.put("providerEnabled", provider.getEnabled());
                                        return ResponseEntity.ok(result);
                                    })
                                    .defaultIfEmpty(ResponseEntity.ok(Map.<String, Object>of(
                                            "customerId", customerId,
                                            "llmProviderId", assignment.getLlmProviderId(),
                                            "error", "Assigned provider not found"
                                    ))))
                            .defaultIfEmpty(ResponseEntity.ok(Map.<String, Object>of(
                                    "customerId", customerId,
                                    "assigned", false
                            )));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{customerId}")
    public Mono<ResponseEntity<Map<String, Object>>> assign(
            @PathVariable String customerId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canAssign(caller)) {
                        return Mono.<ResponseEntity<Map<String, Object>>>just(
                                ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    Object pidObj = body.get("llmProviderId");
                    if (pidObj == null) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Missing llmProviderId")));
                    }
                    Long providerId = ((Number) pidObj).longValue();

                    return providerRepository.findById(providerId)
                            .flatMap(provider -> {
                                if (!canViewProvider(caller, provider)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .body(Map.<String, Object>of("error",
                                                    "Cannot assign a provider outside your scope")));
                                }

                                return assignmentRepository.deleteByCustomerId(customerId)
                                        .then(assignmentRepository.save(ConfigAssignment.builder()
                                                .customerId(customerId)
                                                .llmProviderId(providerId)
                                                .assignedBy(caller.userId)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build()))
                                        .map(saved -> {
                                            log.info("Assigned LLM provider {} to customer {}, by user {}",
                                                    providerId, customerId, caller.userId);
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("customerId", customerId);
                                            result.put("llmProviderId", providerId);
                                            result.put("providerName", provider.getName());
                                            result.put("status", "assigned");
                                            return ResponseEntity.ok(result);
                                        });
                            })
                            .defaultIfEmpty(ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error",
                                            "LLM provider not found: " + providerId)));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{customerId}")
    public Mono<ResponseEntity<Map<String, Object>>> unassign(
            @PathVariable String customerId, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canAssign(caller)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return assignmentRepository.deleteByCustomerId(customerId)
                            .then(Mono.fromCallable(() -> {
                                log.info("Removed LLM provider assignment for customer {}, by user {}",
                                        customerId, caller.userId);
                                Map<String, Object> result = new HashMap<>();
                                result.put("customerId", customerId);
                                result.put("status", "unassigned");
                                return ResponseEntity.ok(result);
                            }));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listAll(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!caller.roles.contains("SUPER_ADMIN") && !caller.roles.contains("TENANT_ADMIN")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<List<Map<String, Object>>>build());
                    }

                    return assignmentRepository.findAll()
                            .map(a -> {
                                Map<String, Object> map = new HashMap<>();
                                map.put("id", a.getId());
                                map.put("customerId", a.getCustomerId());
                                map.put("llmProviderId", a.getLlmProviderId());
                                map.put("assignedBy", a.getAssignedBy());
                                map.put("createdAt", a.getCreatedAt());
                                map.put("updatedAt", a.getUpdatedAt());
                                return map;
                            })
                            .collectList()
                            .map(ResponseEntity::ok);
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    private boolean canViewCustomer(CallerContext caller, String customerId) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        if (caller.roles.contains("TENANT_ADMIN")) return true;
        return caller.roles.contains("CUSTOMER_USER")
                && caller.customerId != null
                && caller.customerId.equals(customerId);
    }

    private boolean canAssign(CallerContext caller) {
        return caller.roles.contains("SUPER_ADMIN") || caller.roles.contains("TENANT_ADMIN");
    }

    private boolean canViewProvider(CallerContext caller, LlmProvider provider) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        if ("SYSTEM".equals(provider.getOwnerType())) return true;
        return "TENANT".equals(provider.getOwnerType()) && caller.tenantId != null
                && caller.tenantId.equals(provider.getOwnerId());
    }

    private Mono<CallerContext> extractCaller(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new SecurityException("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Mono.error(new SecurityException("Invalid JWT token"));
        }

        CallerContext ctx = new CallerContext();
        ctx.userId = jwtTokenProvider.getUserIdFromToken(token);
        ctx.roles = jwtTokenProvider.getRolesFromToken(token);
        ctx.tenantId = jwtTokenProvider.getTenantIdFromToken(token);
        ctx.customerId = jwtTokenProvider.getCustomerIdFromToken(token);
        return Mono.just(ctx);
    }

    private static class CallerContext {
        Long userId;
        List<String> roles;
        Long tenantId;
        String customerId;
    }
}
