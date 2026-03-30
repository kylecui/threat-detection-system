package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.UserConfig;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserConfigRepository extends R2dbcRepository<UserConfig, Long> {

    Mono<UserConfig> findByUserId(Long userId);

    Mono<Void> deleteByUserId(Long userId);
}
