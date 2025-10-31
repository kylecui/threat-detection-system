package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * APT时态累积数据实体
 * 对应数据库表: apt_temporal_accumulations
 */
@Entity
@Table(name = "apt_temporal_accumulations",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_apt_temporal_customer_mac_window",
           columnNames = {"customer_id", "attack_mac", "window_start"}
       ),
       indexes = {
           @Index(name = "idx_apt_temporal_customer_mac", columnList = "customer_id, attack_mac"),
           @Index(name = "idx_apt_temporal_attack_ip", columnList = "attack_ip"),
           @Index(name = "idx_apt_temporal_window", columnList = "window_start, window_end"),
           @Index(name = "idx_apt_temporal_phase", columnList = "inferred_attack_phase"),
           @Index(name = "idx_apt_temporal_updated", columnList = "last_updated DESC"),
           @Index(name = "idx_apt_temporal_cache", columnList = "cache_key"),
           @Index(name = "idx_apt_temporal_score_threshold", columnList = "decay_accumulated_score DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptTemporalAccumulation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 客户ID (多租户隔离)
     */
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    
    /**
     * 被诱捕者MAC地址 (攻击者标识)
     */
    @Column(name = "attack_mac", nullable = false, length = 17)
    private String attackMac;
    
    /**
     * 被诱捕者IP地址 (可选，用于关联)
     */
    @Column(name = "attack_ip", length = 45)
    private String attackIp;
    
    /**
     * 累积威胁评分
     */
    @Column(name = "accumulated_score", nullable = false, precision = 12, scale = 4)
    private BigDecimal accumulatedScore;
    
    /**
     * 指数衰减累积威胁评分
     */
    @Column(name = "decay_accumulated_score", nullable = false, precision = 12, scale = 4)
    private BigDecimal decayAccumulatedScore;
    
    /**
     * 半衰期天数 (默认30天)
     */
    @Column(name = "half_life_days", nullable = false)
    @Builder.Default
    private Integer halfLifeDays = 30;
    
    /**
     * 推断的攻击阶段
     */
    @Column(name = "inferred_attack_phase", length = 30)
    private String inferredAttackPhase;
    
    /**
     * 阶段推断置信度 (0.0-1.0)
     */
    @Column(name = "phase_confidence", precision = 3, scale = 2)
    private BigDecimal phaseConfidence;
    
    /**
     * 累积时间窗口开始时间
     */
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    
    /**
     * 累积时间窗口结束时间
     */
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    
    /**
     * 最后更新时间
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
    
    /**
     * Redis缓存键
     */
    @Column(name = "cache_key", length = 255)
    private String cacheKey;
    
    /**
     * 缓存过期时间
     */
    @Column(name = "cache_expiry")
    private Instant cacheExpiry;
    
    /**
     * 是否启用 (软删除标记)
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
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
        lastUpdated = Instant.now();
        if (isActive == null) {
            isActive = true;
        }
        if (halfLifeDays == null) {
            halfLifeDays = 30;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        lastUpdated = Instant.now();
    }
}
