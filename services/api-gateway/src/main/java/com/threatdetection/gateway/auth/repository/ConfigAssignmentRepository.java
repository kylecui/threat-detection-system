package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.ConfigAssignment;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface ConfigAssignmentRepository extends R2dbcRepository<ConfigAssignment, Long> {

    Mono<ConfigAssignment> findByCustomerId(String customerId);

    Mono<Void> deleteByCustomerId(String customerId);
}
