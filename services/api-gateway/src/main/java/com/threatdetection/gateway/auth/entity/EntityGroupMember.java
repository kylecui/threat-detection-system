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
@Table("entity_group_members")
public class EntityGroupMember {

    @Id
    private Long id;

    @Column("group_id")
    private Long groupId;

    @Column("entity_id")
    private Long entityId;

    @Column("entity_type")
    private String entityType;

    @Column("added_at")
    private LocalDateTime addedAt;
}
