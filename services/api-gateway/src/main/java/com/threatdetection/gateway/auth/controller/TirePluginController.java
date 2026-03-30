package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.TireCustomPlugin;
import com.threatdetection.gateway.auth.repository.TireCustomPluginRepository;
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
 * Custom TIRE Plugin CRUD with multi-tenant RBAC.
 * <p>SUPER_ADMIN: all scopes. TENANT_ADMIN: own tenant. CUSTOMER_USER: read-only.
 * <p>Routes: GET/POST /api/v1/tire-plugins, GET/PUT/DELETE /api/v1/tire-plugins/{id}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tire-plugins")
@RequiredArgsConstructor
public class TirePluginController {

    private final TireCustomPluginRepository pluginRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> list(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (caller.roles.contains("SUPER_ADMIN")) {
                        return pluginRepository.findAll()
                                .map(this::toResponseMap)
                                .collectList();
                    }

                    Long tenantId = caller.tenantId;
                    return pluginRepository.findByOwnerTypeAndOwnerIdIsNull("SYSTEM")
                            .concatWith(tenantId != null
                                    ? pluginRepository.findByOwnerTypeAndOwnerId("TENANT", tenantId)
                                    : reactor.core.publisher.Flux.empty())
                            .map(this::toResponseMap)
                            .collectList();
                })
                .map(ResponseEntity::ok)
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getById(
            @PathVariable Long id, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> pluginRepository.findById(id)
                        .flatMap(plugin -> {
                            if (!canView(caller, plugin)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            return Mono.just(ResponseEntity.ok(toResponseMap(plugin)));
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(
            @RequestBody Map<String, Object> body, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canWrite(caller)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    TireCustomPlugin plugin = buildFromBody(body, caller);
                    plugin.setCreatedAt(LocalDateTime.now());
                    plugin.setUpdatedAt(LocalDateTime.now());

                    return pluginRepository.save(plugin)
                            .map(saved -> {
                                log.info("Created custom TIRE plugin: id={}, slug={}, ownerType={}, ownerId={}",
                                        saved.getId(), saved.getSlug(), saved.getOwnerType(), saved.getOwnerId());
                                return ResponseEntity.status(HttpStatus.CREATED).body(toResponseMap(saved));
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canWrite(caller)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return pluginRepository.findById(id)
                            .flatMap(existing -> {
                                if (!canModify(caller, existing)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .<Map<String, Object>>build());
                                }

                                applyUpdates(existing, body);
                                existing.setUpdatedAt(LocalDateTime.now());

                                return pluginRepository.save(existing)
                                        .map(saved -> {
                                            log.info("Updated custom TIRE plugin: id={}, slug={}",
                                                    saved.getId(), saved.getSlug());
                                            return ResponseEntity.ok(toResponseMap(saved));
                                        });
                            })
                            .defaultIfEmpty(ResponseEntity.notFound().build());
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(
            @PathVariable Long id, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!canWrite(caller)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return pluginRepository.findById(id)
                            .flatMap(existing -> {
                                if (!canModify(caller, existing)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .<Map<String, Object>>build());
                                }

                                return pluginRepository.deleteById(id)
                                        .then(Mono.fromCallable(() -> {
                                            log.info("Deleted custom TIRE plugin: id={}, slug={}",
                                                    existing.getId(), existing.getSlug());
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("id", id);
                                            result.put("status", "deleted");
                                            return ResponseEntity.ok(result);
                                        }));
                            })
                            .defaultIfEmpty(ResponseEntity.notFound().build());
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    private boolean canView(CallerContext caller, TireCustomPlugin plugin) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        if ("SYSTEM".equals(plugin.getOwnerType())) return true;
        return "TENANT".equals(plugin.getOwnerType()) && caller.tenantId != null
                && caller.tenantId.equals(plugin.getOwnerId());
    }

    private boolean canWrite(CallerContext caller) {
        return caller.roles.contains("SUPER_ADMIN") || caller.roles.contains("TENANT_ADMIN");
    }

    private boolean canModify(CallerContext caller, TireCustomPlugin plugin) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        return caller.roles.contains("TENANT_ADMIN") && "TENANT".equals(plugin.getOwnerType())
                && caller.tenantId != null && caller.tenantId.equals(plugin.getOwnerId());
    }

    private TireCustomPlugin buildFromBody(Map<String, Object> body, CallerContext caller) {
        String ownerType;
        Long ownerId;
        if (caller.roles.contains("SUPER_ADMIN")) {
            ownerType = "SYSTEM";
            ownerId = null;
        } else {
            ownerType = "TENANT";
            ownerId = caller.tenantId;
        }

        return TireCustomPlugin.builder()
                .name((String) body.get("name"))
                .slug((String) body.get("slug"))
                .description((String) body.get("description"))
                .pluginUrl((String) body.get("pluginUrl"))
                .apiKey((String) body.get("apiKey"))
                .authType((String) body.getOrDefault("authType", "bearer"))
                .authHeader((String) body.getOrDefault("authHeader", "Authorization"))
                .parserType((String) body.getOrDefault("parserType", "json"))
                .requestMethod((String) body.getOrDefault("requestMethod", "GET"))
                .requestBody((String) body.get("requestBody"))
                .responsePath((String) body.get("responsePath"))
                .enabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true)
                .priority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 50)
                .timeout(body.get("timeout") != null ? ((Number) body.get("timeout")).intValue() : 30)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .build();
    }

    private void applyUpdates(TireCustomPlugin existing, Map<String, Object> body) {
        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("slug")) existing.setSlug((String) body.get("slug"));
        if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
        if (body.containsKey("pluginUrl")) existing.setPluginUrl((String) body.get("pluginUrl"));
        if (body.containsKey("apiKey")) existing.setApiKey((String) body.get("apiKey"));
        if (body.containsKey("authType")) existing.setAuthType((String) body.get("authType"));
        if (body.containsKey("authHeader")) existing.setAuthHeader((String) body.get("authHeader"));
        if (body.containsKey("parserType")) existing.setParserType((String) body.get("parserType"));
        if (body.containsKey("requestMethod")) existing.setRequestMethod((String) body.get("requestMethod"));
        if (body.containsKey("requestBody")) existing.setRequestBody((String) body.get("requestBody"));
        if (body.containsKey("responsePath")) existing.setResponsePath((String) body.get("responsePath"));
        if (body.containsKey("enabled")) existing.setEnabled((Boolean) body.get("enabled"));
        if (body.containsKey("priority")) existing.setPriority(((Number) body.get("priority")).intValue());
        if (body.containsKey("timeout")) existing.setTimeout(((Number) body.get("timeout")).intValue());
    }

    private Map<String, Object> toResponseMap(TireCustomPlugin plugin) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", plugin.getId());
        map.put("name", plugin.getName());
        map.put("slug", plugin.getSlug());
        map.put("description", plugin.getDescription());
        map.put("pluginUrl", plugin.getPluginUrl());
        map.put("authType", plugin.getAuthType());
        map.put("authHeader", plugin.getAuthHeader());
        map.put("parserType", plugin.getParserType());
        map.put("requestMethod", plugin.getRequestMethod());
        map.put("requestBody", plugin.getRequestBody());
        map.put("responsePath", plugin.getResponsePath());
        map.put("enabled", plugin.getEnabled());
        map.put("priority", plugin.getPriority());
        map.put("timeout", plugin.getTimeout());
        map.put("ownerType", plugin.getOwnerType());
        map.put("ownerId", plugin.getOwnerId());
        map.put("createdAt", plugin.getCreatedAt());
        map.put("updatedAt", plugin.getUpdatedAt());
        String apiKey = plugin.getApiKey();
        map.put("hasApiKey", apiKey != null && !apiKey.isEmpty());
        map.put("apiKey", (apiKey != null && !apiKey.isEmpty()) ? "••••••" : "");
        return map;
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
