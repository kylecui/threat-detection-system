package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.Tenant;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TenantRepository extends R2dbcRepository<Tenant, Long> {

    Mono<Tenant> findByTenantId(String tenantId);

    Mono<Boolean> existsByTenantId(String tenantId);

    Flux<Tenant> findByStatus(String status);
}
