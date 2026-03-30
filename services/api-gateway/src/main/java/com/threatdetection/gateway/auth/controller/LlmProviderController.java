package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.LlmProvider;
import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
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
 * Multi-LLM Provider CRUD with multi-tenant RBAC.
 * <p>Resolution: USER → TENANT → SYSTEM. All roles can create within their scope.
 * <p>Routes: GET/POST /api/v1/llm-providers, GET/PUT/DELETE /api/v1/llm-providers/{id},
 *           POST /api/v1/llm-providers/{id}/validate
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/llm-providers")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderRepository providerRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> list(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (caller.roles.contains("SUPER_ADMIN")) {
                        return providerRepository.findAll()
                                .map(this::toResponseMap)
                                .collectList();
                    }

                    Long tenantId = caller.tenantId;
                    Long userId = caller.userId;

                    var systemFlux = providerRepository.findByOwnerTypeAndOwnerIdIsNull("SYSTEM");
                    var tenantFlux = tenantId != null
                            ? providerRepository.findByOwnerTypeAndOwnerId("TENANT", tenantId)
                            : reactor.core.publisher.Flux.<LlmProvider>empty();
                    var userFlux = caller.roles.contains("CUSTOMER_USER")
                            ? providerRepository.findByOwnerTypeAndOwnerId("USER", userId)
                            : reactor.core.publisher.Flux.<LlmProvider>empty();

                    return systemFlux.concatWith(tenantFlux).concatWith(userFlux)
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
                .flatMap(caller -> providerRepository.findById(id)
                        .flatMap(provider -> {
                            if (!canView(caller, provider)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            return Mono.just(ResponseEntity.ok(toResponseMap(provider)));
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
                    LlmProvider provider = buildFromBody(body, caller);
                    provider.setCreatedAt(LocalDateTime.now());
                    provider.setUpdatedAt(LocalDateTime.now());

                    return providerRepository.save(provider)
                            .map(saved -> {
                                log.info("Created LLM provider: id={}, name={}, ownerType={}, ownerId={}",
                                        saved.getId(), saved.getName(), saved.getOwnerType(), saved.getOwnerId());
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
                .flatMap(caller -> providerRepository.findById(id)
                        .flatMap(existing -> {
                            if (!canModify(caller, existing)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            applyUpdates(existing, body);
                            existing.setUpdatedAt(LocalDateTime.now());

                            return providerRepository.save(existing)
                                    .map(saved -> {
                                        log.info("Updated LLM provider: id={}, name={}",
                                                saved.getId(), saved.getName());
                                        return ResponseEntity.ok(toResponseMap(saved));
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(
            @PathVariable Long id, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> providerRepository.findById(id)
                        .flatMap(existing -> {
                            if (!canModify(caller, existing)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            return providerRepository.deleteById(id)
                                    .then(Mono.fromCallable(() -> {
                                        log.info("Deleted LLM provider: id={}, name={}",
                                                existing.getId(), existing.getName());
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("id", id);
                                        result.put("status", "deleted");
                                        return ResponseEntity.ok(result);
                                    }));
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping("/{id}/validate")
    @SuppressWarnings("unchecked")
    public Mono<ResponseEntity<Map<String, Object>>> validate(
            @PathVariable Long id, ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> providerRepository.findById(id)
                        .flatMap(provider -> {
                            if (!canView(caller, provider)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            String apiKey = provider.getApiKey();
                            String baseUrl = provider.getBaseUrl();
                            if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("ok", false);
                                err.put("models", List.of());
                                err.put("error", "Provider missing api_key or base_url");
                                return Mono.just(ResponseEntity.badRequest().body(err));
                            }

                            String effectiveBase = baseUrl.endsWith("/")
                                    ? baseUrl.substring(0, baseUrl.length() - 1)
                                    : baseUrl;

                            return WebClient.create()
                                    .get()
                                    .uri(effectiveBase + "/models")
                                    .header("Authorization", "Bearer " + apiKey)
                                    .retrieve()
                                    .bodyToMono(Map.class)
                                    .timeout(Duration.ofSeconds(15))
                                    .map(data -> {
                                        List<Map<String, Object>> modelList =
                                                (List<Map<String, Object>>) data.get("data");
                                        List<String> models = modelList == null ? List.of() :
                                                modelList.stream()
                                                        .map(m -> (String) m.get("id"))
                                                        .filter(mid -> mid != null)
                                                        .sorted()
                                                        .collect(Collectors.toList());

                                        Map<String, Object> result = new HashMap<>();
                                        result.put("ok", true);
                                        result.put("models", models);
                                        result.put("error", null);
                                        log.info("LLM provider validated: id={}, {} models at {}",
                                                id, models.size(), effectiveBase);
                                        return ResponseEntity.ok(result);
                                    })
                                    .onErrorResume(e -> {
                                        Map<String, Object> errResult = new HashMap<>();
                                        errResult.put("ok", false);
                                        errResult.put("models", List.of());
                                        errResult.put("error", e.getMessage());
                                        log.warn("LLM provider validation failed: id={}, {}: {}",
                                                id, effectiveBase, e.getMessage());
                                        return Mono.just(ResponseEntity.ok(errResult));
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    private boolean canView(CallerContext caller, LlmProvider provider) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        if ("SYSTEM".equals(provider.getOwnerType())) return true;
        if ("TENANT".equals(provider.getOwnerType()) && caller.tenantId != null
                && caller.tenantId.equals(provider.getOwnerId())) return true;
        return "USER".equals(provider.getOwnerType()) && caller.userId != null
                && caller.userId.equals(provider.getOwnerId());
    }

    private boolean canModify(CallerContext caller, LlmProvider provider) {
        if (caller.roles.contains("SUPER_ADMIN")) return true;
        if (caller.roles.contains("TENANT_ADMIN") && "TENANT".equals(provider.getOwnerType())
                && caller.tenantId != null && caller.tenantId.equals(provider.getOwnerId())) return true;
        return caller.roles.contains("CUSTOMER_USER") && "USER".equals(provider.getOwnerType())
                && caller.userId != null && caller.userId.equals(provider.getOwnerId());
    }

    private LlmProvider buildFromBody(Map<String, Object> body, CallerContext caller) {
        String ownerType;
        Long ownerId;
        if (caller.roles.contains("SUPER_ADMIN")) {
            ownerType = "SYSTEM";
            ownerId = null;
        } else if (caller.roles.contains("TENANT_ADMIN")) {
            ownerType = "TENANT";
            ownerId = caller.tenantId;
        } else {
            ownerType = "USER";
            ownerId = caller.userId;
        }

        return LlmProvider.builder()
                .name((String) body.get("name"))
                .apiKey((String) body.get("apiKey"))
                .model((String) body.get("model"))
                .baseUrl((String) body.get("baseUrl"))
                .isDefault(body.get("isDefault") != null ? (Boolean) body.get("isDefault") : false)
                .enabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .build();
    }

    private void applyUpdates(LlmProvider existing, Map<String, Object> body) {
        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("apiKey")) existing.setApiKey((String) body.get("apiKey"));
        if (body.containsKey("model")) existing.setModel((String) body.get("model"));
        if (body.containsKey("baseUrl")) existing.setBaseUrl((String) body.get("baseUrl"));
        if (body.containsKey("isDefault")) existing.setIsDefault((Boolean) body.get("isDefault"));
        if (body.containsKey("enabled")) existing.setEnabled((Boolean) body.get("enabled"));
    }

    private Map<String, Object> toResponseMap(LlmProvider provider) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", provider.getId());
        map.put("name", provider.getName());
        map.put("model", provider.getModel());
        map.put("baseUrl", provider.getBaseUrl());
        map.put("isDefault", provider.getIsDefault());
        map.put("enabled", provider.getEnabled());
        map.put("ownerType", provider.getOwnerType());
        map.put("ownerId", provider.getOwnerId());
        map.put("createdAt", provider.getCreatedAt());
        map.put("updatedAt", provider.getUpdatedAt());
        String apiKey = provider.getApiKey();
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
