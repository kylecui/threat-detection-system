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
@Table("tire_custom_plugins")
public class TireCustomPlugin {

    @Id
    private Long id;

    private String name;
    private String slug;
    private String description;

    @Column("plugin_url")
    private String pluginUrl;

    @Column("api_key")
    private String apiKey;

    @Column("auth_type")
    private String authType;

    @Column("auth_header")
    private String authHeader;

    @Column("parser_type")
    private String parserType;

    @Column("request_method")
    private String requestMethod;

    @Column("request_body")
    private String requestBody;

    @Column("response_path")
    private String responsePath;

    private Boolean enabled;
    private Integer priority;
    private Integer timeout;

    @Column("owner_type")
    private String ownerType;

    @Column("owner_id")
    private Long ownerId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
