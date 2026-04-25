package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.ConfigInheritance;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConfigInheritanceRepository extends R2dbcRepository<ConfigInheritance, Long> {

    Flux<ConfigInheritance> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Mono<ConfigInheritance> findByEntityTypeAndEntityIdAndConfigType(String entityType, Long entityId, String configType);

    Mono<Void> deleteByEntityTypeAndEntityIdAndConfigType(String entityType, Long entityId, String configType);

    Mono<Void> deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
