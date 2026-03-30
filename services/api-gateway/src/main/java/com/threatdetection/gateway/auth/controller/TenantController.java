package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.dto.CreateTenantRequest;
import com.threatdetection.gateway.auth.dto.UpdateTenantRequest;
import com.threatdetection.gateway.auth.entity.Tenant;
import com.threatdetection.gateway.auth.repository.TenantRepository;
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
 * Tenant management REST controller — SUPER_ADMIN only
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/v1/tenants         — list all tenants</li>
 *   <li>GET    /api/v1/tenants/{id}    — get tenant by DB id</li>
 *   <li>POST   /api/v1/tenants         — create tenant</li>
 *   <li>PUT    /api/v1/tenants/{id}    — update tenant</li>
 *   <li>DELETE /api/v1/tenants/{id}    — delete tenant</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listTenants(ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> tenantRepository.findAll()
                        .map(this::toMap)
                        .collectList()
                        .map(ResponseEntity::ok))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getTenant(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> tenantRepository.findById(id)
                        .map(this::toMap)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createTenant(
            @RequestBody CreateTenantRequest request,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> {
                    if (request.getTenantId() == null || request.getTenantId().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "tenantId is required")));
                    }
                    if (request.getName() == null || request.getName().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "name is required")));
                    }

                    return tenantRepository.existsByTenantId(request.getTenantId())
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                            .body(Map.<String, Object>of("error",
                                                    "Tenant ID '" + request.getTenantId() + "' already exists")));
                                }

                                Tenant tenant = Tenant.builder()
                                        .tenantId(request.getTenantId())
                                        .name(request.getName())
                                        .description(request.getDescription())
                                        .contactEmail(request.getContactEmail())
                                        .maxCustomers(request.getMaxCustomers() != null
                                                ? request.getMaxCustomers() : 100)
                                        .status("ACTIVE")
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();

                                return tenantRepository.save(tenant)
                                        .map(saved -> {
                                            log.info("Tenant created: tenantId={}, name={}",
                                                    saved.getTenantId(), saved.getName());
                                            return ResponseEntity.status(HttpStatus.CREATED)
                                                    .body(toMap(saved));
                                        });
                            });
                })
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateTenant(
            @PathVariable Long id,
            @RequestBody UpdateTenantRequest request,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> tenantRepository.findById(id)
                        .flatMap(existing -> {
                            if (request.getName() != null) existing.setName(request.getName());
                            if (request.getDescription() != null) existing.setDescription(request.getDescription());
                            if (request.getContactEmail() != null) existing.setContactEmail(request.getContactEmail());
                            if (request.getStatus() != null) existing.setStatus(request.getStatus());
                            if (request.getMaxCustomers() != null) existing.setMaxCustomers(request.getMaxCustomers());
                            existing.setUpdatedAt(LocalDateTime.now());

                            return tenantRepository.save(existing)
                                    .map(saved -> {
                                        log.info("Tenant updated: id={}, tenantId={}", id, saved.getTenantId());
                                        return ResponseEntity.ok(toMap(saved));
                                    });
                        })
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteTenant(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return requireSuperAdmin(exchange)
                .flatMap(ok -> tenantRepository.findById(id)
                        .flatMap(existing -> tenantRepository.delete(existing)
                                .then(Mono.just(ResponseEntity.ok(
                                        Map.<String, Object>of("status", "deleted",
                                                "tenantId", existing.getTenantId())))))
                        .defaultIfEmpty(ResponseEntity.notFound().build()))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    // ──────── Helpers ────────

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

    private Map<String, Object> toMap(Tenant t) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("tenantId", t.getTenantId());
        map.put("name", t.getName());
        map.put("description", t.getDescription());
        map.put("contactEmail", t.getContactEmail());
        map.put("status", t.getStatus());
        map.put("maxCustomers", t.getMaxCustomers());
        map.put("createdAt", t.getCreatedAt());
        map.put("updatedAt", t.getUpdatedAt());
        return map;
    }
}
