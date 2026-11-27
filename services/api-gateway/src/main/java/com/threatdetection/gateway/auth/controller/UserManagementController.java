package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.dto.CreateUserRequest;
import com.threatdetection.gateway.auth.dto.UpdateUserRequest;
import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthRoleRepository;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.validation.PasswordPolicy;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * User Management REST controller
 *
 * <p>Access:
 * <ul>
 *   <li>SUPER_ADMIN - manage all users across all tenants</li>
 *   <li>TENANT_ADMIN - manage users within own tenant only</li>
 *   <li>CUSTOMER_ADMIN - manage ordinary users within own customer only</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "TENANT_ADMIN", "CUSTOMER_ADMIN", "CUSTOMER_USER");

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseClient databaseClient;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listUsers(
            @RequestParam(required = false) Long tenantId,
            ServerWebExchange exchange) {
        return resolveCallerContext(exchange)
                .flatMap(ctx -> {
                    if (ctx.isSuperAdmin()) {
                        if (tenantId != null) {
                            return userRepository.findByTenantId(tenantId)
                                    .flatMap(this::enrichUserWithRoles)
                                    .collectList();
                        }
                        return userRepository.findAll()
                                .flatMap(this::enrichUserWithRoles)
                                .collectList();
                    } else if (ctx.isTenantAdmin()) {
                        if (ctx.tenantId == null) {
                            return reactor.core.publisher.Flux.<Map<String, Object>>empty().collectList();
                        }
                        return userRepository.findByTenantId(ctx.tenantId)
                                .flatMap(this::enrichUserWithRoles)
                                .collectList();
                    } else if (ctx.isCustomerAdmin()) {
                        if (ctx.customerId == null || ctx.customerId.isBlank()) {
                            return reactor.core.publisher.Flux.<Map<String, Object>>empty().collectList();
                        }
                        return userRepository.findByTenantIdAndCustomerId(ctx.tenantId, ctx.customerId)
                                .flatMap(this::enrichUserWithRoles)
                                .collectList();
                    }
                    return Mono.just(Collections.<Map<String, Object>>emptyList());
                })
                .map(ResponseEntity::ok)
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getUser(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return resolveCallerContext(exchange)
                .flatMap(ctx -> userRepository.findById(id)
                        .flatMap(user -> {
                            if (!ctx.canAccessUser(user)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            return enrichUserWithRoles(user).map(ResponseEntity::ok);
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createUser(
            @RequestBody CreateUserRequest request,
            ServerWebExchange exchange) {
        return resolveCallerContext(exchange)
                .flatMap(ctx -> {
                    if (request.getUsername() == null || request.getUsername().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "username is required")));
                    }
                    if (request.getPassword() == null || request.getPassword().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "password is required")));
                    }
                    if (!isStrongPassword(request.getPassword())) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error",
                                    "password must be " + PasswordPolicy.DESCRIPTION)));
                    }
                    if (request.getRole() == null || request.getRole().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "role is required")));
                    }
                    if (!ALLOWED_ROLES.contains(request.getRole())) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "unsupported role")));
                    }

                    Long effectiveTenantId;
                    if (ctx.isSuperAdmin()) {
                        effectiveTenantId = request.getTenantId();
                    } else if (ctx.isTenantAdmin()) {
                        effectiveTenantId = ctx.tenantId;
                        if ("SUPER_ADMIN".equals(request.getRole())) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.<String, Object>of("error",
                                            "TENANT_ADMIN cannot create SUPER_ADMIN users")));
                        }
                    } else if (ctx.isCustomerAdmin()) {
                        effectiveTenantId = ctx.tenantId;
                        if (!"CUSTOMER_USER".equals(request.getRole())) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.<String, Object>of("error",
                                            "CUSTOMER_ADMIN can only create CUSTOMER_USER accounts")));
                        }
                        request.setCustomerId(ctx.customerId);
                    } else {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.<String, Object>of("error", "Insufficient permissions")));
                    }

                    boolean customerRole = "CUSTOMER_ADMIN".equals(request.getRole())
                            || "CUSTOMER_USER".equals(request.getRole());
                    if (!"SUPER_ADMIN".equals(request.getRole()) && effectiveTenantId == null) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "tenant is required for this role")));
                    }
                    if (customerRole && (request.getCustomerId() == null || request.getCustomerId().isBlank())) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "customer is required for this role")));
                    }
                    if (!customerRole) {
                        request.setCustomerId(null);
                    }

                    return userRepository.existsByUsername(request.getUsername())
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                            .body(Map.<String, Object>of("error",
                                                    "Username already exists")));
                                }

                                Mono<Boolean> customerValidation = Mono.just(true);
                                if (customerRole) {
                                    customerValidation = effectiveTenantId != null
                                            ? validateCustomerBelongsToTenant(request.getCustomerId(), effectiveTenantId)
                                            : validateCustomerIsActive(request.getCustomerId());
                                }

                                return customerValidation.flatMap(isValidCustomer -> {
                                    if (!Boolean.TRUE.equals(isValidCustomer)) {
                                        return Mono.just(ResponseEntity.badRequest()
                                                .body(Map.<String, Object>of("error",
                                                        "Customer does not exist or does not belong to the specified tenant")));
                                    }

                                    LocalDateTime now = LocalDateTime.now();
                                    AuthUser user = AuthUser.builder()
                                            .username(request.getUsername())
                                            .passwordHash(passwordEncoder.encode(request.getPassword()))
                                            .displayName(request.getDisplayName())
                                            .email(request.getEmail())
                                            .customerId(request.getCustomerId())
                                            .tenantId(effectiveTenantId)
                                            .enabled(true)
                                            .createdAt(now)
                                            .updatedAt(now)
                                            .build();

                                    return userRepository.save(user)
                                            .flatMap(saved -> assignRole(saved.getId(), request.getRole())
                                                    .then(enrichUserWithRoles(saved))
                                                    .onErrorResume(error -> userRepository.deleteById(saved.getId())
                                                            .then(Mono.error(error))))
                                            .map(userMap -> {
                                                log.info("Created user: username={}, tenantId={}, role={}",
                                                        request.getUsername(), effectiveTenantId, request.getRole());
                                                return ResponseEntity.status(HttpStatus.CREATED).body(userMap);
                                            });
                                });
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            ServerWebExchange exchange) {
        return resolveCallerContext(exchange)
                .flatMap(ctx -> userRepository.findById(id)
                        .flatMap(user -> canManageUser(ctx, user).flatMap(canManage -> {
                            if (!canManage) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            if (request.getPassword() != null && !request.getPassword().isBlank()
                                    && !isStrongPassword(request.getPassword())) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error",
                                            "password must be " + PasswordPolicy.DESCRIPTION)));
                            }
                            if (request.getRole() != null && !request.getRole().isBlank()
                                    && !ALLOWED_ROLES.contains(request.getRole())) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error", "unsupported role")));
                            }
                            if (ctx.isCustomerAdmin()) {
                                if (id.equals(ctx.userId)) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .body(Map.<String, Object>of("error", "A customer administrator cannot modify its own administrative account")));
                                }
                                if (request.getRole() != null && !"CUSTOMER_USER".equals(request.getRole())) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                            .body(Map.<String, Object>of("error", "CUSTOMER_ADMIN can only assign CUSTOMER_USER role")));
                                }
                                request.setCustomerId(ctx.customerId);
                            }
                            boolean requestedCustomerRole = "CUSTOMER_ADMIN".equals(request.getRole())
                                    || "CUSTOMER_USER".equals(request.getRole());
                            if (requestedCustomerRole
                                    && (request.getCustomerId() == null || request.getCustomerId().isBlank())) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(Map.<String, Object>of("error",
                                                "customer is required for this role")));
                            }

                            Mono<Boolean> customerValidation = Mono.just(true);
                            if (request.getCustomerId() != null) {
                                customerValidation = validateCustomerBelongsToTenant(
                                        request.getCustomerId(), user.getTenantId());
                            }

                            return customerValidation.flatMap(isValidCustomer -> {
                                if (!Boolean.TRUE.equals(isValidCustomer)) {
                                    return Mono.just(ResponseEntity.badRequest()
                                            .body(Map.<String, Object>of("error",
                                                    "Customer does not exist or does not belong to the specified tenant")));
                                }

                                if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
                                if (request.getEmail() != null) user.setEmail(request.getEmail());
                                if (request.getRole() != null
                                        && !"CUSTOMER_ADMIN".equals(request.getRole())
                                        && !"CUSTOMER_USER".equals(request.getRole())) {
                                    user.setCustomerId(null);
                                } else if (request.getCustomerId() != null) {
                                    user.setCustomerId(request.getCustomerId());
                                }
                                if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
                                if (request.getPassword() != null && !request.getPassword().isBlank()) {
                                    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                                }
                                user.setUpdatedAt(LocalDateTime.now());

                                Mono<Void> roleUpdate = Mono.empty();
                                if (request.getRole() != null && !request.getRole().isBlank()) {
                                    if (!ctx.isSuperAdmin() && "SUPER_ADMIN".equals(request.getRole())) {
                                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(Map.<String, Object>of("error",
                                                        "Cannot assign SUPER_ADMIN role")));
                                    }
                                    roleUpdate = clearRoles(id).then(assignRole(id, request.getRole()));
                                }

                                return userRepository.save(user)
                                        .then(roleUpdate)
                                        .then(userRepository.findById(id))
                                        .flatMap(this::enrichUserWithRoles)
                                        .map(userMap -> {
                                            log.info("Updated user: id={}, username={}", id, user.getUsername());
                                            return ResponseEntity.ok(userMap);
                                        });
                            });
                        }))
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteUser(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return resolveCallerContext(exchange)
                .flatMap(ctx -> userRepository.findById(id)
                        .flatMap(user -> canManageUser(ctx, user).flatMap(canManage -> {
                            if (!canManage) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .<Map<String, Object>>build());
                            }
                            if (ctx.isCustomerAdmin() && id.equals(ctx.userId)) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(Map.<String, Object>of("error",
                                                "A customer administrator cannot delete its own administrative account")));
                            }
                            return clearRoles(id)
                                    .then(userRepository.deleteById(id))
                                    .then(Mono.fromCallable(() -> {
                                        log.info("Deleted user: id={}, username={}", id, user.getUsername());
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("deleted", true);
                                        result.put("id", id);
                                        return ResponseEntity.ok(result);
                                    }));
                        }))
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    // ---- Internal helpers ----

    private Mono<Map<String, Object>> enrichUserWithRoles(AuthUser user) {
        return roleRepository.findRolesByUserId(user.getId())
                .map(r -> r.getName())
                .collectList()
                .map(roles -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", user.getId());
                    m.put("username", user.getUsername());
                    m.put("displayName", user.getDisplayName());
                    m.put("email", user.getEmail());
                    m.put("customerId", user.getCustomerId());
                    m.put("tenantId", user.getTenantId());
                    m.put("enabled", user.getEnabled());
                    m.put("roles", roles);
                    m.put("createdAt", user.getCreatedAt());
                    m.put("updatedAt", user.getUpdatedAt());
                    return m;
                });
    }

    private Mono<Void> assignRole(Long userId, String roleName) {
        return databaseClient.sql(
                "INSERT INTO auth_user_roles (user_id, role_id) " +
                "SELECT :userId, id FROM auth_roles WHERE name = :roleName " +
                "ON CONFLICT DO NOTHING")
                .bind("userId", userId)
                .bind("roleName", roleName)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows > 0
                        ? Mono.empty()
                        : Mono.error(new IllegalArgumentException("Unknown role: " + roleName)));
    }

    private Mono<Void> clearRoles(Long userId) {
        return databaseClient.sql("DELETE FROM auth_user_roles WHERE user_id = :userId")
                .bind("userId", userId)
                .then();
    }

    private Mono<Boolean> validateCustomerBelongsToTenant(String customerId, Long tenantId) {
        if (customerId == null || tenantId == null) {
            return Mono.just(false);
        }

        return databaseClient.sql("SELECT COUNT(*) FROM customers WHERE customer_id = :customerId " +
                        "AND tenant_id = :tenantId AND status = 'ACTIVE'")
                .bind("customerId", customerId)
                .bind("tenantId", tenantId)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count != null && count > 0)
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> canManageUser(CallerContext caller, AuthUser target) {
        if (!caller.canAccessUser(target)) {
            return Mono.just(false);
        }
        if (!caller.isCustomerAdmin()) {
            return Mono.just(true);
        }
        return roleRepository.findRolesByUserId(target.getId())
                .all(role -> "CUSTOMER_USER".equals(role.getName()));
    }

    private Mono<Boolean> validateCustomerIsActive(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Mono.just(false);
        }
        return databaseClient.sql("SELECT COUNT(*) FROM customers " +
                        "WHERE customer_id = :customerId AND status = 'ACTIVE'")
                .bind("customerId", customerId)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count != null && count > 0)
                .defaultIfEmpty(false);
    }

    private boolean isStrongPassword(String password) {
        return PasswordPolicy.isValid(password);
    }

    private Mono<CallerContext> resolveCallerContext(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new SecurityException("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Mono.error(new SecurityException("Invalid JWT token"));
        }
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
        String customerId = jwtTokenProvider.getCustomerIdFromToken(token);
        return Mono.just(new CallerContext(userId, roles, tenantId, customerId));
    }

    private static class CallerContext {
        final Long userId;
        final List<String> roles;
        final Long tenantId;
        final String customerId;

        CallerContext(Long userId, List<String> roles, Long tenantId, String customerId) {
            this.userId = userId;
            this.roles = roles != null ? roles : Collections.emptyList();
            this.tenantId = tenantId;
            this.customerId = customerId;
        }

        boolean isSuperAdmin() { return roles.contains("SUPER_ADMIN"); }
        boolean isTenantAdmin() { return roles.contains("TENANT_ADMIN"); }
        boolean isCustomerAdmin() { return roles.contains("CUSTOMER_ADMIN"); }

        boolean canAccessUser(AuthUser user) {
            if (isSuperAdmin()) return true;
            if (isTenantAdmin() && tenantId != null && tenantId.equals(user.getTenantId())) return true;
            if (isCustomerAdmin() && tenantId != null && tenantId.equals(user.getTenantId())
                    && customerId != null && customerId.equals(user.getCustomerId())) return true;
            return false;
        }
    }
}
