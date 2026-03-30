package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.LlmProvider;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmProviderRepository extends R2dbcRepository<LlmProvider, Long> {

    Flux<LlmProvider> findByOwnerTypeAndOwnerIdIsNull(String ownerType);

    Flux<LlmProvider> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);

    Mono<LlmProvider> findByIsDefaultTrueAndOwnerTypeAndOwnerIdIsNull(String ownerType);

    Flux<LlmProvider> findByEnabled(Boolean enabled);
}
