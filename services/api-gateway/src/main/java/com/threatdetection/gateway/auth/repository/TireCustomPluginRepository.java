package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.TireCustomPlugin;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TireCustomPluginRepository extends R2dbcRepository<TireCustomPlugin, Long> {

    Flux<TireCustomPlugin> findByOwnerTypeAndOwnerIdIsNull(String ownerType);

    Flux<TireCustomPlugin> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);

    Flux<TireCustomPlugin> findByEnabled(Boolean enabled);
}
