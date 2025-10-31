package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 攻击源网段权重配置实体
 * 对应数据库表: attack_source_weights
 */
@Entity
@Table(name = "attack_source_weights",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_customer_source_segment",
           columnNames = {"customer_id", "segment_name"}
       ),
       indexes = {
           @Index(name = "idx_attack_source_customer_active", columnList = "customer_id, is_active"),
           @Index(name = "idx_attack_source_ip_range", columnList = "customer_id, ip_range_start, ip_range_end"),
           @Index(name = "idx_attack_source_risk_level", columnList = "risk_level")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackSourceWeight {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 客户ID (多租户隔离)
     */
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;
    
    /**
     * 网段名称
     */
    @Column(name = "segment_name", nullable = false, length = 255)
    private String segmentName;
    
    /**
     * IP范围起始地址
     */
    @Column(name = "ip_range_start", nullable = false, length = 15)
    private String ipRangeStart;
    
    /**
     * IP范围结束地址
     */
    @Column(name = "ip_range_end", nullable = false, length = 15)
    private String ipRangeEnd;
    
    /**
     * 网段类型
     */
    @Column(name = "segment_type", nullable = false, length = 50)
    private String segmentType;
    
    /**
     * 风险等级
     */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;
    
    /**
     * 攻击源权重 (0.5-3.0)
     */
    @Column(name = "weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal weight;
    
    /**
     * 描述信息
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 是否启用 (软删除标记)
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * 优先级
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 50;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (isActive == null) {
            isActive = true;
        }
        if (priority == null) {
            priority = 50;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
