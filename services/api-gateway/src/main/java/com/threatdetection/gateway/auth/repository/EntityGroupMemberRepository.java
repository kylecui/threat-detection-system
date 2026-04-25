package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.EntityGroupMember;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EntityGroupMemberRepository extends R2dbcRepository<EntityGroupMember, Long> {

    Flux<EntityGroupMember> findByGroupId(Long groupId);

    Flux<EntityGroupMember> findByEntityIdAndEntityType(Long entityId, String entityType);

    Mono<Void> deleteByGroupId(Long groupId);

    Mono<Void> deleteByGroupIdAndEntityIdAndEntityType(Long groupId, Long entityId, String entityType);
}
