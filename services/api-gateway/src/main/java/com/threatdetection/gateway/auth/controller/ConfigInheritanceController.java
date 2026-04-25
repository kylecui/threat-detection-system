package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.ConfigInheritance;
import com.threatdetection.gateway.auth.repository.ConfigInheritanceRepository;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/config-inheritance")
@RequiredArgsConstructor
public class ConfigInheritanceController {

    private final ConfigInheritanceRepository configInheritanceRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/{entityType}/{entityId}")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getEntityLocks(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!isValidEntityType(entityType)) {
                        return Mono.just(ResponseEntity.badRequest().<List<Map<String, Object>>>build());
                    }
                    if (!canManageEntity(caller, entityType)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<List<Map<String, Object>>>build());
                    }

                    return configInheritanceRepository.findByEntityTypeAndEntityId(entityType, entityId)
                            .map(ci -> Map.<String, Object>of(
                                    "configType", ci.getConfigType(),
                                    "lockMode", ci.getLockMode()
                            ))
                            .collectList()
                            .map(ResponseEntity::ok);
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/{entityType}/{entityId}/{configType}")
    public Mono<ResponseEntity<Map<String, Object>>> getLockMode(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable String configType,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!isValidEntityType(entityType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Invalid entityType")));
                    }
                    if (!canManageEntity(caller, entityType)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return configInheritanceRepository.findByEntityTypeAndEntityIdAndConfigType(entityType, entityId, configType)
                            .map(this::toLockMap)
                            .map(ResponseEntity::ok)
                            .defaultIfEmpty(ResponseEntity.ok(Map.<String, Object>of(
                                    "entityType", entityType,
                                    "entityId", entityId,
                                    "configType", configType,
                                    "lockMode", "default"
                            )));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{entityType}/{entityId}/{configType}")
    public Mono<ResponseEntity<Map<String, Object>>> upsertLockMode(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable String configType,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!isValidEntityType(entityType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Invalid entityType")));
                    }
                    if (!canManageEntity(caller, entityType)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    String lockMode = body.get("lockMode") instanceof String ? (String) body.get("lockMode") : null;
                    if (!isValidLockMode(lockMode)) {
                        return Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of(
                                "error", "Invalid lockMode. Allowed: default, inherit_only, independent_only"
                        )));
                    }

                    LocalDateTime now = LocalDateTime.now();
                    return configInheritanceRepository.findByEntityTypeAndEntityIdAndConfigType(entityType, entityId, configType)
                            .flatMap(existing -> {
                                existing.setLockMode(lockMode);
                                existing.setLockedBy(caller.userId);
                                existing.setLockedAt(now);
                                existing.setUpdatedAt(now);
                                return configInheritanceRepository.save(existing);
                            })
                            .switchIfEmpty(configInheritanceRepository.save(ConfigInheritance.builder()
                                    .entityType(entityType)
                                    .entityId(entityId)
                                    .configType(configType)
                                    .lockMode(lockMode)
                                    .lockedBy(caller.userId)
                                    .lockedAt(now)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build()))
                            .map(saved -> {
                                log.info("Set config inheritance lock: entityType={}, entityId={}, configType={}, lockMode={}, by user {}",
                                        entityType, entityId, configType, lockMode, caller.userId);
                                return ResponseEntity.ok(toLockMap(saved));
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{entityType}/{entityId}/{configType}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteLockMode(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable String configType,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!isValidEntityType(entityType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Invalid entityType")));
                    }
                    if (!canManageEntity(caller, entityType)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return configInheritanceRepository.deleteByEntityTypeAndEntityIdAndConfigType(entityType, entityId, configType)
                            .then(Mono.fromCallable(() -> {
                                log.info("Deleted config inheritance lock: entityType={}, entityId={}, configType={}, by user {}",
                                        entityType, entityId, configType, caller.userId);
                                Map<String, Object> result = new HashMap<>();
                                result.put("entityType", entityType);
                                result.put("entityId", entityId);
                                result.put("configType", configType);
                                result.put("lockMode", "default");
                                result.put("status", "deleted");
                                return ResponseEntity.ok(result);
                            }));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{entityType}/{entityId}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAllEntityLocks(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (!isValidEntityType(entityType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Invalid entityType")));
                    }
                    if (!canManageEntity(caller, entityType)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, Object>>build());
                    }

                    return configInheritanceRepository.deleteByEntityTypeAndEntityId(entityType, entityId)
                            .then(Mono.fromCallable(() -> {
                                log.info("Deleted all config inheritance locks: entityType={}, entityId={}, by user {}",
                                        entityType, entityId, caller.userId);
                                Map<String, Object> result = new HashMap<>();
                                result.put("entityType", entityType);
                                result.put("entityId", entityId);
                                result.put("status", "deleted_all");
                                return ResponseEntity.ok(result);
                            }));
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    private boolean isValidEntityType(String entityType) {
        return "tenant".equals(entityType) || "customer".equals(entityType);
    }

    private boolean isValidLockMode(String lockMode) {
        return "default".equals(lockMode)
                || "inherit_only".equals(lockMode)
                || "independent_only".equals(lockMode);
    }

    private boolean canManageEntity(CallerContext caller, String entityType) {
        if (caller.roles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (caller.roles.contains("TENANT_ADMIN")) {
            return "customer".equals(entityType);
        }
        return false;
    }

    private Map<String, Object> toLockMap(ConfigInheritance ci) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ci.getId());
        map.put("entityType", ci.getEntityType());
        map.put("entityId", ci.getEntityId());
        map.put("configType", ci.getConfigType());
        map.put("lockMode", ci.getLockMode());
        map.put("lockedBy", ci.getLockedBy());
        map.put("lockedAt", ci.getLockedAt());
        map.put("createdAt", ci.getCreatedAt());
        map.put("updatedAt", ci.getUpdatedAt());
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
