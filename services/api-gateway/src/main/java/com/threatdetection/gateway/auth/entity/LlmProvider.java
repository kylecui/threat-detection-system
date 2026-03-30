package com.threatdetection.gateway.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("llm_providers")
public class LlmProvider {

    @Id
    private Long id;

    private String name;

    @Column("api_key")
    private String apiKey;

    private String model;

    @Column("base_url")
    private String baseUrl;

    @Column("is_default")
    private Boolean isDefault;

    private Boolean enabled;

    @Column("owner_type")
    private String ownerType;

    @Column("owner_id")
    private Long ownerId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
