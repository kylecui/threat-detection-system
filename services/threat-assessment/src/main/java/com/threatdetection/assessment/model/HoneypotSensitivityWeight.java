package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 蜜罐敏感度权重配置实体
 * 对应数据库表: honeypot_sensitivity_weights
 */
@Entity
@Table(name = "honeypot_sensitivity_weights",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_customer_honeypot_ip_segment",
           columnNames = {"customer_id", "ip_segment"}
       ),
       indexes = {
           @Index(name = "idx_honeypot_customer_active", columnList = "customer_id, is_active"),
           @Index(name = "idx_honeypot_ip_segment", columnList = "ip_segment")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoneypotSensitivityWeight {
    
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
     * 蜜罐敏感度权重 (0.0-10.0)
     */
    @Column(name = "honeypot_sensitivity_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal honeypotSensitivityWeight;
    
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
        return honeypotSensitivityWeight;
    }
    
    /**
     * 设置权重值 (兼容旧接口)
     */
    public void setWeight(BigDecimal weight) {
        this.honeypotSensitivityWeight = weight;
    }
    
    /**
     * 获取蜜罐名称 (兼容旧接口)
     */
    public String getHoneypotName() {
        return ipSegment;
    }
    
    /**
     * 设置蜜罐名称 (兼容旧接口)
     */
    public void setHoneypotName(String honeypotName) {
        this.ipSegment = honeypotName;
    }
    
    /**
     * 获取部署区域 (兼容旧接口)
     */
    public String getDeploymentZone() {
        // 从IP段推断部署区域
        if (ipSegment == null) return "UNKNOWN";
        if (ipSegment.startsWith("192.168.1.") || ipSegment.startsWith("10.0.")) {
            return "MANAGEMENT";
        } else if (ipSegment.startsWith("192.168.2.") || ipSegment.startsWith("10.1.")) {
            return "DATABASE";
        } else if (ipSegment.startsWith("192.168.3.") || ipSegment.startsWith("10.2.")) {
            return "WEB_SERVERS";
        } else if (ipSegment.startsWith("192.168.4.") || ipSegment.startsWith("10.3.")) {
            return "FILE_SERVERS";
        } else {
            return "GENERAL";
        }
    }
    
    /**
     * 设置部署区域 (兼容旧接口)
     */
    public void setDeploymentZone(String deploymentZone) {
        // 这个方法主要用于兼容性，实际不修改数据
    }
    
    /**
     * 获取攻击意图 (兼容旧接口)
     */
    public String getAttackIntent() {
        // 从IP段推断攻击意图
        if (ipSegment == null) return "UNKNOWN";
        if (ipSegment.startsWith("192.168.1.") || ipSegment.startsWith("10.0.")) {
            return "PRIVILEGE_ESCALATION";
        } else if (ipSegment.startsWith("192.168.2.") || ipSegment.startsWith("10.1.")) {
            return "DATA_THEFT";
        } else if (ipSegment.startsWith("192.168.3.") || ipSegment.startsWith("10.2.")) {
            return "PIVOTING";
        } else if (ipSegment.startsWith("192.168.4.") || ipSegment.startsWith("10.3.")) {
            return "RANSOMWARE";
        } else {
            return "RECONNAISSANCE";
        }
    }
    
    /**
     * 设置攻击意图 (兼容旧接口)
     */
    public void setAttackIntent(String attackIntent) {
        // 这个方法主要用于兼容性，实际不修改数据
    }
}
