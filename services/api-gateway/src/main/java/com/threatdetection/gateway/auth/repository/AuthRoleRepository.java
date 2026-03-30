package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.AuthRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface AuthRoleRepository extends R2dbcRepository<AuthRole, Long> {

    @Query("SELECT r.* FROM auth_roles r " +
           "JOIN auth_user_roles ur ON r.id = ur.role_id " +
           "WHERE ur.user_id = :userId")
    Flux<AuthRole> findRolesByUserId(Long userId);
}
