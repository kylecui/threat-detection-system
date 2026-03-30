package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.ConfigAssignment;
import com.threatdetection.gateway.auth.entity.LlmProvider;
import com.threatdetection.gateway.auth.entity.UserConfig;
import com.threatdetection.gateway.auth.repository.ConfigAssignmentRepository;
import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
import com.threatdetection.gateway.auth.repository.UserConfigRepository;
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

/**
 * End-user self-service config controller.
 *
 * CUSTOMER_USER can configure their own LLM provider + TI API keys,
 * subject to admin lock flags on their customer's config_assignments.
 *
 * Resolution order (enforced in GET /resolved):
 *   1. If admin locked (lock_llm/lock_tire=true) → admin assignment only
 *   2. If user has use_own_*=true AND has configured keys → user's own config
 *   3. If admin assigned (config_assignments) → admin assignment
 *   4. Fallback → global system_config
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user-config")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigRepository userConfigRepository;
    private final ConfigAssignmentRepository assignmentRepository;
    private final LlmProviderRepository providerRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getMyConfig(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!caller.roles.contains("CUSTOMER_USER")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    Mono<UserConfig> ucMono = userConfigRepository.findByUserId(caller.userId)
                            .defaultIfEmpty(UserConfig.builder().userId(caller.userId).build());

                    Mono<ConfigAssignment> asMono = caller.customerId != null
                            ? assignmentRepository.findByCustomerId(caller.customerId)
                                    .defaultIfEmpty(ConfigAssignment.builder().build())
                            : Mono.just(ConfigAssignment.builder().build());

                    return Mono.zip(ucMono, asMono)
                            .map(tuple -> {
                                UserConfig uc = tuple.getT1();
                                ConfigAssignment as = tuple.getT2();

                                boolean lockLlm = as.getLockLlm() != null && as.getLockLlm();
                                boolean lockTire = as.getLockTire() != null && as.getLockTire();

                                Map<String, Object> result = new HashMap<>();
                                result.put("userId", caller.userId);
                                result.put("lockLlm", lockLlm);
                                result.put("lockTire", lockTire);

                                result.put("llmProviderId", uc.getLlmProviderId());
                                String ucKeysStr = uc.getTireApiKeys() != null ? uc.getTireApiKeys().asString() : "{}";
                                result.put("tireApiKeys", maskTireApiKeys(ucKeysStr));
                                result.put("adminLlmProviderId", as.getLlmProviderId());
                                String asKeysStr = as.getTireApiKeys() != null ? as.getTireApiKeys().asString() : "{}";
                                result.put("adminHasTireApiKeys",
                                        !asKeysStr.equals("{}") && !asKeysStr.isEmpty());

                                return ResponseEntity.ok(result);
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping
    public Mono<ResponseEntity<Map<String, Object>>> saveMyConfig(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!caller.roles.contains("CUSTOMER_USER")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    Mono<ConfigAssignment> asMono = caller.customerId != null
                            ? assignmentRepository.findByCustomerId(caller.customerId)
                                    .defaultIfEmpty(ConfigAssignment.builder().build())
                            : Mono.just(ConfigAssignment.builder().build());

                    return asMono.flatMap(as -> {
                        boolean lockLlm = as.getLockLlm() != null && as.getLockLlm();
                        boolean lockTire = as.getLockTire() != null && as.getLockTire();

                        Long llmProviderId = null;
                        Object pidObj = body.get("llmProviderId");
                        if (pidObj != null) {
                            llmProviderId = ((Number) pidObj).longValue();
                        }

                        String tireApiKeysJson = "{}";
                        Object tireObj = body.get("tireApiKeys");
                        if (tireObj instanceof Map) {
                            tireApiKeysJson = toJson((Map<?, ?>) tireObj);
                        } else if (tireObj instanceof String) {
                            tireApiKeysJson = (String) tireObj;
                        }

                        Boolean useOwnLlm = body.get("useOwnLlm") instanceof Boolean
                                ? (Boolean) body.get("useOwnLlm") : false;
                        Boolean useOwnTire = body.get("useOwnTire") instanceof Boolean
                                ? (Boolean) body.get("useOwnTire") : false;

                        if (lockLlm && (llmProviderId != null || Boolean.TRUE.equals(useOwnLlm))) {
                            return Mono.just(ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error",
                                            "Admin has locked LLM configuration. You cannot set your own LLM provider.")));
                        }
                        if (lockTire && (!tireApiKeysJson.equals("{}") || Boolean.TRUE.equals(useOwnTire))) {
                            return Mono.just(ResponseEntity.badRequest()
                                    .body(Map.<String, Object>of("error",
                                            "Admin has locked TI API keys configuration. You cannot set your own TI keys.")));
                        }

                        final Long fProviderId = llmProviderId;
                        final Json fTireKeys = Json.of(tireApiKeysJson);

                        Mono<Boolean> providerValid = fProviderId != null
                                ? providerRepository.findById(fProviderId)
                                        .map(p -> true)
                                        .defaultIfEmpty(false)
                                : Mono.just(true);

                        return providerValid.flatMap(valid -> {
                            if (!valid) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error",
                                                "LLM provider not found: " + fProviderId)));
                            }

                            return userConfigRepository.findByUserId(caller.userId)
                                    .flatMap(existing -> {
                                        existing.setLlmProviderId(fProviderId);
                                        existing.setTireApiKeys(fTireKeys);
                                        existing.setUseOwnLlm(useOwnLlm);
                                        existing.setUseOwnTire(useOwnTire);
                                        existing.setUpdatedAt(LocalDateTime.now());
                                        return userConfigRepository.save(existing);
                                    })
                                    .switchIfEmpty(
                                        userConfigRepository.save(UserConfig.builder()
                                                .userId(caller.userId)
                                                .llmProviderId(fProviderId)
                                                .tireApiKeys(fTireKeys)
                                                .useOwnLlm(useOwnLlm)
                                                .useOwnTire(useOwnTire)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build())
                                    )
                                    .map(saved -> {
                                        log.info("User {} saved own config: llm={}, useOwnLlm={}, useOwnTire={}",
                                                caller.userId, fProviderId, useOwnLlm, useOwnTire);
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("userId", caller.userId);
                                        result.put("llmProviderId", fProviderId);
                                        result.put("useOwnLlm", useOwnLlm);
                                        result.put("useOwnTire", useOwnTire);
                                        result.put("hasTireApiKeys", !fTireKeys.asString().equals("{}"));
                                        result.put("status", "saved");
                                        return ResponseEntity.ok(result);
                                    });
                        });
                    });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * GET /api/v1/user-config/resolved — resolved config after applying resolution order.
     *
     * Resolution:
     *   1. locked → admin assignment
     *   2. use_own_*=true + has keys → user's own
     *   3. admin assignment exists → admin assignment
     *   4. fallback → empty (frontend falls back to system_config)
     */
    @GetMapping("/resolved")
    public Mono<ResponseEntity<Map<String, Object>>> getResolvedConfig(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!caller.roles.contains("CUSTOMER_USER")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    Mono<UserConfig> ucMono = userConfigRepository.findByUserId(caller.userId)
                            .defaultIfEmpty(UserConfig.builder().userId(caller.userId).build());

                    Mono<ConfigAssignment> asMono = caller.customerId != null
                            ? assignmentRepository.findByCustomerId(caller.customerId)
                                    .defaultIfEmpty(ConfigAssignment.builder().build())
                            : Mono.just(ConfigAssignment.builder().build());

                    return Mono.zip(ucMono, asMono)
                            .flatMap(tuple -> {
                                UserConfig uc = tuple.getT1();
                                ConfigAssignment as = tuple.getT2();

                                boolean lockLlm = as.getLockLlm() != null && as.getLockLlm();
                                boolean lockTire = as.getLockTire() != null && as.getLockTire();
                                boolean useOwnLlm = uc.getUseOwnLlm() != null && uc.getUseOwnLlm();
                                boolean useOwnTire = uc.getUseOwnTire() != null && uc.getUseOwnTire();

                                Map<String, Object> result = new HashMap<>();
                                result.put("userId", caller.userId);
                                result.put("lockLlm", lockLlm);
                                result.put("lockTire", lockTire);

                                Long resolvedLlmProviderId;
                                String llmSource;
                                if (lockLlm) {
                                    resolvedLlmProviderId = as.getLlmProviderId();
                                    llmSource = "admin_locked";
                                } else if (useOwnLlm && uc.getLlmProviderId() != null) {
                                    resolvedLlmProviderId = uc.getLlmProviderId();
                                    llmSource = "user_own";
                                } else if (as.getLlmProviderId() != null) {
                                    resolvedLlmProviderId = as.getLlmProviderId();
                                    llmSource = "admin_assigned";
                                } else {
                                    resolvedLlmProviderId = null;
                                    llmSource = "system_default";
                                }
                                result.put("llmProviderId", resolvedLlmProviderId);
                                result.put("llmSource", llmSource);

                                String resolvedTireKeys;
                                String tireSource;
                                String ucTireStr = uc.getTireApiKeys() != null ? uc.getTireApiKeys().asString() : "{}";
                                String asTireStr = as.getTireApiKeys() != null ? as.getTireApiKeys().asString() : "{}";
                                boolean userHasTireKeys = !ucTireStr.equals("{}") && !ucTireStr.isEmpty();
                                boolean adminHasTireKeys = !asTireStr.equals("{}") && !asTireStr.isEmpty();

                                if (lockTire) {
                                    resolvedTireKeys = asTireStr;
                                    tireSource = "admin_locked";
                                } else if (useOwnTire && userHasTireKeys) {
                                    resolvedTireKeys = ucTireStr;
                                    tireSource = "user_own";
                                } else if (adminHasTireKeys) {
                                    resolvedTireKeys = asTireStr;
                                    tireSource = "admin_assigned";
                                } else {
                                    resolvedTireKeys = null;
                                    tireSource = "system_default";
                                }
                                result.put("tireApiKeys", resolvedTireKeys != null
                                        ? maskTireApiKeys(resolvedTireKeys) : Map.of());
                                result.put("tireSource", tireSource);

                                if (resolvedLlmProviderId != null) {
                                    return providerRepository.findById(resolvedLlmProviderId)
                                            .map(provider -> {
                                                result.put("providerName", provider.getName());
                                                result.put("providerModel", provider.getModel());
                                                return ResponseEntity.ok(result);
                                            })
                                            .defaultIfEmpty(ResponseEntity.ok(result));
                                }
                                return Mono.just(ResponseEntity.ok(result));
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
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
