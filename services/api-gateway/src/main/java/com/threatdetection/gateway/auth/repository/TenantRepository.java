package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.Tenant;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TenantRepository extends R2dbcRepository<Tenant, Long> {

    Mono<Tenant> findByTenantId(String tenantId);

    Mono<Boolean> existsByTenantId(String tenantId);

    Flux<Tenant> findByStatus(String status);

    @Query("SELECT * FROM tenants WHERE " +
            "LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(tenant_id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(contact_email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, name, id " +
            "LIMIT :limit OFFSET :offset")
    Flux<Tenant> search(@Param("keyword") String keyword,
                        @Param("limit") int limit,
                        @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM tenants WHERE " +
            "LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(tenant_id) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(contact_email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Mono<Long> countSearch(@Param("keyword") String keyword);
}
