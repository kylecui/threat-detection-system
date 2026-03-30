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
 * Tenant entity (R2DBC)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tenants")
public class Tenant {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    private String name;

    private String description;

    @Column("contact_email")
    private String contactEmail;

    private String status;

    @Column("max_customers")
    private Integer maxCustomers;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
