package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.SystemConfig;
import com.threatdetection.gateway.auth.repository.SystemConfigRepository;
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
 * System Configuration REST controller
 *
 * <p>Manages TIRE API keys, LLM settings, and other system-level configuration.
 * Only SUPER_ADMIN can read/write system config.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/v1/system-config/category/{category} — list configs by category</li>
 *   <li>PUT  /api/v1/system-config/batch                — batch update configs</li>
 *   <li>GET  /api/v1/system-config/{key}                — get single config</li>
 *   <li>PUT  /api/v1/system-config/{key}                — update single config</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigRepository configRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Get all configs by category.
     * Secret values are masked in the response.
     */
    @GetMapping("/category/{category}")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getByCategory(
            @PathVariable String category,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> configRepository.findByCategory(category)
                        .map(this::toResponseMap)
                        .collectList()
                        .map(ResponseEntity::ok))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * Get single config by key.
     */
    @GetMapping("/{key}")
    public Mono<ResponseEntity<Map<String, Object>>> getByKey(
            @PathVariable String key,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> configRepository.findByConfigKey(key)
                        .map(this::toResponseMap)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * Batch update configs.
     * Body: { "configs": { "KEY1": "value1", "KEY2": "value2" } }
     */
    @PutMapping("/batch")
    public Mono<ResponseEntity<Map<String, Object>>> batchUpdate(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> {
                    @SuppressWarnings("unchecked")
                    Map<String, String> configs = (Map<String, String>) body.get("configs");
                    if (configs == null || configs.isEmpty()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "No configs provided")));
                    }

                    // Upsert each config
                    return reactor.core.publisher.Flux.fromIterable(configs.entrySet())
                            .flatMap(entry -> configRepository.findByConfigKey(entry.getKey())
                                    .flatMap(existing -> {
                                        existing.setConfigValue(entry.getValue());
                                        existing.setUpdatedAt(LocalDateTime.now());
                                        return configRepository.save(existing);
                                    })
                                    .switchIfEmpty(configRepository.save(SystemConfig.builder()
                                            .configKey(entry.getKey())
                                            .configValue(entry.getValue())
                                            .category("custom")
                                            .isSecret(false)
                                            .createdAt(LocalDateTime.now())
                                            .updatedAt(LocalDateTime.now())
                                            .build())))
                            .collectList()
                            .map(saved -> {
                                log.info("Batch updated {} system configs", saved.size());
                                Map<String, Object> result = new HashMap<>();
                                result.put("updated", saved.size());
                                result.put("status", "ok");
                                return ResponseEntity.ok(result);
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * Update single config by key.
     * Body: { "value": "new-value" }
     */
    @PutMapping("/{key}")
    public Mono<ResponseEntity<Map<String, Object>>> updateByKey(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> {
                    String value = body.get("value");
                    if (value == null) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Missing 'value' field")));
                    }
                    return configRepository.findByConfigKey(key)
                            .flatMap(existing -> {
                                existing.setConfigValue(value);
                                existing.setUpdatedAt(LocalDateTime.now());
                                return configRepository.save(existing);
                            })
                            .map(saved -> {
                                log.info("Updated system config: key={}", key);
                                Map<String, Object> result = new HashMap<>();
                                result.put("key", key);
                                result.put("status", "updated");
                                return ResponseEntity.ok(result);
                            })
                            .defaultIfEmpty(ResponseEntity.notFound().build());
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    // ──────── Helpers ────────

    /**
     * Verify caller has SUPER_ADMIN role via JWT.
     */
    private Mono<Boolean> requireSuperAdmin(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new SecurityException("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Mono.error(new SecurityException("Invalid JWT token"));
        }
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        if (roles == null || !roles.contains("SUPER_ADMIN")) {
            return Mono.error(new SecurityException("Requires SUPER_ADMIN role"));
        }
        return Mono.just(true);
    }

    /**
     * Convert entity to response map, masking secret values.
     */
    private Map<String, Object> toResponseMap(SystemConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("key", config.getConfigKey());
        map.put("category", config.getCategory());
        map.put("description", config.getDescription());
        map.put("isSecret", config.getIsSecret());
        map.put("updatedAt", config.getUpdatedAt());

        // Mask secret values — show only if non-empty (as "••••••") or empty
        if (Boolean.TRUE.equals(config.getIsSecret())) {
            String val = config.getConfigValue();
            map.put("value", (val != null && !val.isEmpty()) ? "••••••" : "");
            map.put("hasValue", val != null && !val.isEmpty());
        } else {
            map.put("value", config.getConfigValue());
            map.put("hasValue", config.getConfigValue() != null && !config.getConfigValue().isEmpty());
        }

        return map;
    }
}
