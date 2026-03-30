package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.ConfigAssignment;
import com.threatdetection.gateway.auth.entity.LlmProvider;
import com.threatdetection.gateway.auth.repository.ConfigAssignmentRepository;
import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import io.r2dbc.postgresql.codec.Json;
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
                            .flatMap(assignment -> {
                                Mono<LlmProvider> providerMono = assignment.getLlmProviderId() != null
                                        ? providerRepository.findById(assignment.getLlmProviderId())
                                        : Mono.empty();

                                return providerMono
                                        .map(provider -> buildAssignmentResponse(assignment, provider))
                                        .defaultIfEmpty(buildAssignmentResponse(assignment, null));
                            })
                            .map(ResponseEntity::ok)
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

                    Long providerId = null;
                    Object pidObj = body.get("llmProviderId");
                    if (pidObj != null) {
                        providerId = ((Number) pidObj).longValue();
                    }

                    String tireApiKeysStr = "{}";
                    Object tireObj = body.get("tireApiKeys");
                    if (tireObj instanceof Map) {
                        tireApiKeysStr = toJson((Map<?, ?>) tireObj);
                    } else if (tireObj instanceof String) {
                        tireApiKeysStr = (String) tireObj;
                    }

                    Boolean lockLlm = body.get("lockLlm") instanceof Boolean
                            ? (Boolean) body.get("lockLlm") : false;
                    Boolean lockTire = body.get("lockTire") instanceof Boolean
                            ? (Boolean) body.get("lockTire") : false;

                    final Long fProviderId = providerId;
                    final Json fTireKeys = Json.of(tireApiKeysStr);

                    Mono<LlmProvider> providerCheck = fProviderId != null
                            ? providerRepository.findById(fProviderId)
                            : Mono.just(LlmProvider.builder().build());

                    return providerCheck
                            .flatMap(provider -> {
                                if (fProviderId != null && provider.getId() == null) {
                                    return Mono.just(ResponseEntity.badRequest()
                                            .body(Map.<String, Object>of("error",
                                                    "LLM provider not found: " + fProviderId)));
                                }
                                if (fProviderId != null && !canViewProvider(caller, provider)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .body(Map.<String, Object>of("error",
                                                    "Cannot assign a provider outside your scope")));
                                }

                                return assignmentRepository.deleteByCustomerId(customerId)
                                        .then(assignmentRepository.save(ConfigAssignment.builder()
                                                .customerId(customerId)
                                                .llmProviderId(fProviderId)
                                                .tireApiKeys(fTireKeys)
                                                .lockLlm(lockLlm)
                                                .lockTire(lockTire)
                                                .assignedBy(caller.userId)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build()))
                                        .map(saved -> {
                                            log.info("Assigned config to customer {}: llm={}, lockLlm={}, lockTire={}, by user {}",
                                                    customerId, fProviderId, lockLlm, lockTire, caller.userId);
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("customerId", customerId);
                                            result.put("llmProviderId", fProviderId);
                                            result.put("lockLlm", lockLlm);
                                            result.put("lockTire", lockTire);
                                            result.put("hasTireApiKeys", !fTireKeys.asString().equals("{}"));
                                            result.put("status", "assigned");
                                            if (fProviderId != null && provider.getName() != null) {
                                                result.put("providerName", provider.getName());
                                            }
                                            return ResponseEntity.ok(result);
                                        });
                            })
                            .defaultIfEmpty(ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error",
                                            "LLM provider not found: " + fProviderId)));
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
                                log.info("Removed config assignment for customer {}, by user {}",
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
                                map.put("lockLlm", a.getLockLlm() != null && a.getLockLlm());
                                map.put("lockTire", a.getLockTire() != null && a.getLockTire());
                                String keysJson = a.getTireApiKeys() != null ? a.getTireApiKeys().asString() : "{}";
                                map.put("hasTireApiKeys", !keysJson.equals("{}") && !keysJson.isEmpty());
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

    private Map<String, Object> buildAssignmentResponse(ConfigAssignment a, LlmProvider provider) {
        Map<String, Object> result = new HashMap<>();
        result.put("customerId", a.getCustomerId());
        result.put("llmProviderId", a.getLlmProviderId());
        result.put("lockLlm", a.getLockLlm() != null && a.getLockLlm());
        result.put("lockTire", a.getLockTire() != null && a.getLockTire());
        String keysStr = a.getTireApiKeys() != null ? a.getTireApiKeys().asString() : "{}";
        result.put("tireApiKeys", maskTireApiKeys(keysStr));
        result.put("hasTireApiKeys", a.getTireApiKeys() != null
                && !keysStr.equals("{}") && !keysStr.isEmpty());
        result.put("assignedBy", a.getAssignedBy());
        result.put("createdAt", a.getCreatedAt());
        result.put("updatedAt", a.getUpdatedAt());
        if (provider != null) {
            result.put("providerName", provider.getName());
            result.put("providerModel", provider.getModel());
            result.put("providerBaseUrl", provider.getBaseUrl());
            result.put("providerEnabled", provider.getEnabled());
        }
        return result;
    }

    private Map<String, String> maskTireApiKeys(String json) {
        Map<String, String> masked = new HashMap<>();
        if (json == null || json.equals("{}") || json.isEmpty()) return masked;
        try {
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
                for (String pair : content.split(",")) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("\"", "");
                        String val = kv[1].trim().replaceAll("\"", "");
                        masked.put(key, val.isEmpty() ? "" : "••••••");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse tire_api_keys JSON: {}", e.getMessage());
        }
        return masked;
    }

    private String toJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue() != null ? entry.getValue() : "").append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
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
