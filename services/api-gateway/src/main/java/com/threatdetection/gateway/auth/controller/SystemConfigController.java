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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Validate LLM connection by listing available models from the provider.
     * Body: { "api_key": "sk-...", "base_url": "https://api.openai.com/v1" }
     * Returns: { "ok": true/false, "models": [...], "error": null/string }
     */
    @PostMapping("/llm/validate")
    public Mono<ResponseEntity<Map<String, Object>>> validateLlmConnection(
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> {
                    String apiKey = body.get("api_key");
                    String baseUrl = body.get("base_url");
                    if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("ok", false);
                        err.put("models", List.of());
                        err.put("error", "api_key and base_url are required");
                        return Mono.just(ResponseEntity.badRequest().body(err));
                    }

                    String effectiveBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

                    return WebClient.create()
                            .get()
                            .uri(effectiveBase + "/models")
                            .header("Authorization", "Bearer " + apiKey)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofSeconds(15))
                            .map(data -> {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> modelList = (List<Map<String, Object>>) data.get("data");
                                List<String> models = modelList == null ? List.of() :
                                        modelList.stream()
                                                .map(m -> (String) m.get("id"))
                                                .filter(id -> id != null)
                                                .sorted()
                                                .collect(Collectors.toList());

                                Map<String, Object> result = new HashMap<>();
                                result.put("ok", true);
                                result.put("models", models);
                                result.put("error", null);
                                log.info("LLM connection validated: {} models found at {}", models.size(), effectiveBase);
                                return ResponseEntity.ok(result);
                            })
                            .onErrorResume(e -> {
                                Map<String, Object> errResult = new HashMap<>();
                                errResult.put("ok", false);
                                errResult.put("models", List.of());
                                errResult.put("error", e.getMessage());
                                log.warn("LLM connection validation failed for {}: {}", effectiveBase, e.getMessage());
                                return Mono.just(ResponseEntity.ok(errResult));
                            });
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
