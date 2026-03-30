package com.threatdetection.gateway.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_configs")
public class UserConfig {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("llm_provider_id")
    private Long llmProviderId;

    @Column("tire_api_keys")
    private Json tireApiKeys;

    @Column("use_own_llm")
    private Boolean useOwnLlm;

    @Column("use_own_tire")
    private Boolean useOwnTire;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
