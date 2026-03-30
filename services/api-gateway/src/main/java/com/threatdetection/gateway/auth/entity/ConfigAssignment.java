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
@Table("config_assignments")
public class ConfigAssignment {

    @Id
    private Long id;

    @Column("customer_id")
    private String customerId;

    @Column("llm_provider_id")
    private Long llmProviderId;

    @Column("assigned_by")
    private Long assignedBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
