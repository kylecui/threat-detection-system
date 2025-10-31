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
           name = "uk_customer_ip_segment",
           columnNames = {"customer_id", "ip_segment"}
       ),
       indexes = {
           @Index(name = "idx_attack_source_customer_active", columnList = "customer_id, is_active"),
           @Index(name = "idx_attack_source_ip_segment", columnList = "ip_segment")
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
     * IP段标识 (如: "192.168.1.0/24", "10.0.0.0/8")
     */
    @Column(name = "ip_segment", nullable = false, length = 50)
    private String ipSegment;
    
    /**
     * 攻击源权重 (0.0-10.0)
     */
    @Column(name = "attack_source_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal attackSourceWeight;
    
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
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * 获取权重值 (兼容旧接口)
     */
    public BigDecimal getWeight() {
        return attackSourceWeight;
    }
    
    /**
     * 设置权重值 (兼容旧接口)
     */
    public void setWeight(BigDecimal weight) {
        this.attackSourceWeight = weight;
    }
    
    /**
     * 获取网段名称 (兼容旧接口)
     */
    public String getSegmentName() {
        return ipSegment;
    }
    
    /**
     * 设置网段名称 (兼容旧接口)
     */
    public void setSegmentName(String segmentName) {
        this.ipSegment = segmentName;
    }
    
    /**
     * 获取网段类型 (兼容旧接口)
     */
    public String getSegmentType() {
        // 从IP段推断类型
        if (ipSegment == null) return "UNKNOWN";
        if (ipSegment.startsWith("192.168.") || ipSegment.startsWith("10.") || ipSegment.startsWith("172.")) {
            return "PRIVATE";
        } else if (ipSegment.startsWith("127.")) {
            return "LOOPBACK";
        } else {
            return "PUBLIC";
        }
    }
}
