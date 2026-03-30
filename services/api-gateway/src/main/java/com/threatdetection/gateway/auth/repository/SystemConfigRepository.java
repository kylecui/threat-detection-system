package com.threatdetection.gateway.auth.repository;

import com.threatdetection.gateway.auth.entity.SystemConfig;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SystemConfigRepository extends R2dbcRepository<SystemConfig, Long> {

    Mono<SystemConfig> findByConfigKey(String configKey);

    Flux<SystemConfig> findByCategory(String category);

    @Modifying
    @Query("UPDATE system_config SET config_value = :value, updated_at = NOW() WHERE config_key = :key")
    Mono<Integer> updateValueByKey(String key, String value);
}
