package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.AuthUser;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface AuthUserRepository extends R2dbcRepository<AuthUser, Long> {

    Mono<AuthUser> findByUsername(String username);

    Mono<Boolean> existsByUsername(String username);
}
