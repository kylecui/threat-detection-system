package com.threatdetection.gateway.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * System configuration entity (R2DBC)
 *
 * <p>Key-value store for TIRE API keys, LLM settings,
 * and other system-level configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("system_config")
public class SystemConfig {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("config_value")
    private String configValue;

    private String category;

    private String description;

    @Column("is_secret")
    private Boolean isSecret;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
