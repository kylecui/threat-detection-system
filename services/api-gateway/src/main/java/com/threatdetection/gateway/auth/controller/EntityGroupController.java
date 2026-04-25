package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.ConfigInheritance;
import com.threatdetection.gateway.auth.entity.EntityGroup;
import com.threatdetection.gateway.auth.entity.EntityGroupMember;
import com.threatdetection.gateway.auth.repository.ConfigInheritanceRepository;
import com.threatdetection.gateway.auth.repository.EntityGroupMemberRepository;
import com.threatdetection.gateway.auth.repository.EntityGroupRepository;
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
@RequestMapping("/api/v1/entity-groups")
@RequiredArgsConstructor
public class EntityGroupController {

    private final EntityGroupRepository entityGroupRepository;
    private final EntityGroupMemberRepository entityGroupMemberRepository;
    private final ConfigInheritanceRepository configInheritanceRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listGroups(ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    if (caller.roles.contains("SUPER_ADMIN")) {
                        return entityGroupRepository.findAll()
                                .map(this::toGroupMap)
                                .collectList()
                                .map(ResponseEntity::ok);
                    }
                    if (caller.roles.contains("TENANT_ADMIN") && caller.tenantId != null) {
                        return entityGroupRepository.findByTenantId(caller.tenantId)
                                .map(this::toGroupMap)
                                .collectList()
                                .map(ResponseEntity::ok);
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .<List<Map<String, Object>>>build());
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createGroup(
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> {
                    String groupName = body.get("groupName") instanceof String ? (String) body.get("groupName") : null;
                    String groupType = body.get("groupType") instanceof String ? (String) body.get("groupType") : null;
                    String description = body.get("description") instanceof String ? (String) body.get("description") : null;

                    if (groupName == null || groupName.isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "groupName is required")));
                    }
                    if (!isValidGroupType(groupType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "groupType must be tenant_group or customer_group")));
                    }

                    if (caller.roles.contains("TENANT_ADMIN")) {
                        if (!"customer_group".equals(groupType)) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.<String, Object>of("error", "TENANT_ADMIN can only create customer_group")));
                        }
                        if (caller.tenantId == null) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.<String, Object>of("error", "Missing tenant scope")));
                        }

                        EntityGroup group = EntityGroup.builder()
                                .groupName(groupName)
                                .groupType(groupType)
                                .description(description)
                                .tenantId(caller.tenantId)
                                .createdBy(caller.userId)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        return entityGroupRepository.save(group)
                                .map(saved -> {
                                    log.info("Created entity group id={}, type={}, tenantId={}, by user {}",
                                            saved.getId(), saved.getGroupType(), saved.getTenantId(), caller.userId);
                                    return ResponseEntity.status(HttpStatus.CREATED).body(toGroupMap(saved));
                                });
                    }

                    if (caller.roles.contains("SUPER_ADMIN")) {
                        Long tenantId = null;
                        if (body.get("tenantId") instanceof Number) {
                            tenantId = ((Number) body.get("tenantId")).longValue();
                        }

                        EntityGroup group = EntityGroup.builder()
                                .groupName(groupName)
                                .groupType(groupType)
                                .description(description)
                                .tenantId(tenantId)
                                .createdBy(caller.userId)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        return entityGroupRepository.save(group)
                                .map(saved -> {
                                    log.info("Created entity group id={}, type={}, tenantId={}, by user {}",
                                            saved.getId(), saved.getGroupType(), saved.getTenantId(), caller.userId);
                                    return ResponseEntity.status(HttpStatus.CREATED).body(toGroupMap(saved));
                                });
                    }

                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .<Map<String, Object>>build());
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{groupId}")
    public Mono<ResponseEntity<Map<String, Object>>> updateGroup(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canManageGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            if (body.get("groupName") instanceof String) {
                                group.setGroupName((String) body.get("groupName"));
                            }
                            if (body.get("description") instanceof String) {
                                group.setDescription((String) body.get("description"));
                            }
                            group.setUpdatedAt(LocalDateTime.now());

                            return entityGroupRepository.save(group)
                                    .map(saved -> {
                                        log.info("Updated entity group id={}, by user {}", saved.getId(), caller.userId);
                                        return ResponseEntity.ok(toGroupMap(saved));
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{groupId}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteGroup(
            @PathVariable Long groupId,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canManageGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            return entityGroupMemberRepository.deleteByGroupId(groupId)
                                    .then(entityGroupRepository.delete(group))
                                    .then(Mono.fromCallable(() -> {
                                        log.info("Deleted entity group id={} and members, by user {}", groupId, caller.userId);
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("groupId", groupId);
                                        result.put("status", "deleted");
                                        return ResponseEntity.ok(result);
                                    }));
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/{groupId}/members")
    public Mono<ResponseEntity<List<Map<String, Object>>>> listMembers(
            @PathVariable Long groupId,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canAccessGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<List<Map<String, Object>>>build());
                            }

                            return entityGroupMemberRepository.findByGroupId(groupId)
                                    .map(this::toMemberMap)
                                    .collectList()
                                    .map(ResponseEntity::ok);
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping("/{groupId}/members")
    public Mono<ResponseEntity<Map<String, Object>>> addMember(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canAccessGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            if (!(body.get("entityId") instanceof Number)) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "entityId is required")));
                            }
                            String entityType = body.get("entityType") instanceof String ? (String) body.get("entityType") : null;
                            if (!isValidEntityType(entityType)) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "entityType must be tenant or customer")));
                            }

                            Long entityId = ((Number) body.get("entityId")).longValue();
                            EntityGroupMember member = EntityGroupMember.builder()
                                    .groupId(groupId)
                                    .entityId(entityId)
                                    .entityType(entityType)
                                    .addedAt(LocalDateTime.now())
                                    .build();

                            return entityGroupMemberRepository.save(member)
                                    .map(saved -> {
                                        log.info("Added member to group id={}: entityType={}, entityId={}, by user {}",
                                                groupId, entityType, entityId, caller.userId);
                                        return ResponseEntity.status(HttpStatus.CREATED).body(toMemberMap(saved));
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{groupId}/members/{entityId}")
    public Mono<ResponseEntity<Map<String, Object>>> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long entityId,
            @RequestParam String entityType,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canAccessGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            if (!isValidEntityType(entityType)) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "entityType must be tenant or customer")));
                            }

                            return entityGroupMemberRepository.deleteByGroupIdAndEntityIdAndEntityType(groupId, entityId, entityType)
                                    .then(Mono.fromCallable(() -> {
                                        log.info("Removed member from group id={}: entityType={}, entityId={}, by user {}",
                                                groupId, entityType, entityId, caller.userId);
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("groupId", groupId);
                                        result.put("entityId", entityId);
                                        result.put("entityType", entityType);
                                        result.put("status", "removed");
                                        return ResponseEntity.ok(result);
                                    }));
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping("/{groupId}/batch-assign")
    public Mono<ResponseEntity<Map<String, Object>>> batchAssign(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            ServerWebExchange exchange) {
        return extractCaller(exchange)
                .flatMap(caller -> entityGroupRepository.findById(groupId)
                        .flatMap(group -> {
                            if (!canAccessGroup(caller, group)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }

                            String configType = body.get("configType") instanceof String ? (String) body.get("configType") : null;
                            String lockMode = body.get("lockMode") instanceof String ? (String) body.get("lockMode") : null;

                            if (configType == null || configType.isBlank()) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "configType is required")));
                            }
                            if (!isValidLockMode(lockMode)) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "lockMode must be default, inherit_only or independent_only")));
                            }

                            LocalDateTime now = LocalDateTime.now();
                            return entityGroupMemberRepository.findByGroupId(groupId)
                                    .flatMap(member -> upsertInheritance(member, configType, lockMode, caller.userId, now))
                                    .collectList()
                                    .map(updated -> {
                                        log.info("Batch assigned config inheritance groupId={}, configType={}, lockMode={}, count={}, by user {}",
                                                groupId, configType, lockMode, updated.size(), caller.userId);
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("groupId", groupId);
                                        result.put("configType", configType);
                                        result.put("lockMode", lockMode);
                                        result.put("affectedCount", updated.size());
                                        result.put("status", "assigned");
                                        return ResponseEntity.ok(result);
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    private Mono<ConfigInheritance> upsertInheritance(
            EntityGroupMember member,
            String configType,
            String lockMode,
            Long userId,
            LocalDateTime now) {
        return configInheritanceRepository.findByEntityTypeAndEntityIdAndConfigType(
                        member.getEntityType(), member.getEntityId(), configType)
                .flatMap(existing -> {
                    existing.setLockMode(lockMode);
                    existing.setLockedBy(userId);
                    existing.setLockedAt(now);
                    existing.setUpdatedAt(now);
                    return configInheritanceRepository.save(existing);
                })
                .switchIfEmpty(configInheritanceRepository.save(ConfigInheritance.builder()
                        .entityType(member.getEntityType())
                        .entityId(member.getEntityId())
                        .configType(configType)
                        .lockMode(lockMode)
                        .lockedBy(userId)
                        .lockedAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    private boolean canManageGroup(CallerContext caller, EntityGroup group) {
        if (caller.roles.contains("SUPER_ADMIN")) {
            return true;
        }
        return group.getCreatedBy() != null && group.getCreatedBy().equals(caller.userId);
    }

    private boolean canAccessGroup(CallerContext caller, EntityGroup group) {
        if (caller.roles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (caller.roles.contains("TENANT_ADMIN")
                && caller.tenantId != null
                && group.getTenantId() != null
                && caller.tenantId.equals(group.getTenantId())) {
            return true;
        }
        return group.getCreatedBy() != null && group.getCreatedBy().equals(caller.userId);
    }

    private boolean isValidGroupType(String groupType) {
        return "tenant_group".equals(groupType) || "customer_group".equals(groupType);
    }

    private boolean isValidEntityType(String entityType) {
        return "tenant".equals(entityType) || "customer".equals(entityType);
    }

    private boolean isValidLockMode(String lockMode) {
        return "default".equals(lockMode)
                || "inherit_only".equals(lockMode)
                || "independent_only".equals(lockMode);
    }

    private Map<String, Object> toGroupMap(EntityGroup group) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", group.getId());
        map.put("groupName", group.getGroupName());
        map.put("groupType", group.getGroupType());
        map.put("description", group.getDescription());
        map.put("tenantId", group.getTenantId());
        map.put("createdBy", group.getCreatedBy());
        map.put("createdAt", group.getCreatedAt());
        map.put("updatedAt", group.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toMemberMap(EntityGroupMember member) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", member.getId());
        map.put("groupId", member.getGroupId());
        map.put("entityId", member.getEntityId());
        map.put("entityType", member.getEntityType());
        map.put("addedAt", member.getAddedAt());
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
