package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.EntityGroup;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface EntityGroupRepository extends R2dbcRepository<EntityGroup, Long> {

    Flux<EntityGroup> findByGroupType(String groupType);

    Flux<EntityGroup> findByTenantId(Long tenantId);

    Flux<EntityGroup> findByCreatedBy(Long createdBy);
}
