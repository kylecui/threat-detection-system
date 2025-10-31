package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 攻击阶段端口配置实体
 * 对应数据库表: attack_phase_port_configs
 */
@Entity
@Table(name = "attack_phase_port_configs",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_customer_phase_port",
           columnNames = {"customer_id", "phase", "port_number"}
       ),
       indexes = {
           @Index(name = "idx_attack_phase_customer", columnList = "customer_id"),
           @Index(name = "idx_attack_phase_phase", columnList = "phase"),
           @Index(name = "idx_attack_phase_port", columnList = "port_number"),
           @Index(name = "idx_attack_phase_enabled", columnList = "is_active"),
           @Index(name = "idx_attack_phase_composite", columnList = "customer_id, phase, is_active, priority DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackPhasePortConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 客户ID (多租户隔离，NULL表示全局默认)
     */
    @Column(name = "customer_id", length = 50)
    private String customerId;
    
    /**
     * 攻击阶段 (RECON, EXPLOITATION, PERSISTENCE)
     */
    @Column(name = "phase", nullable = false, length = 20)
    private String phase;
    
    /**
     * 端口号 (1-65535)
     */
    @Column(name = "port_number", nullable = false)
    private Integer portNumber;
    
    /**
     * 优先级 (1-10)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 5;
    
    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;
    
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
        if (isEnabled == null) {
            isEnabled = true;
        }
        if (priority == null) {
            priority = 5;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * 设置启用状态 (兼容旧接口)
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
    
    /**
     * 设置启用状态 (兼容旧接口)
     */
    public void setEnabled(Boolean enabled) {
        this.isEnabled = enabled;
    }
    
    /**
     * 获取启用状态 (兼容旧接口)
     */
    public Boolean getEnabled() {
        return isEnabled;
    }
    
    /**
     * 获取端口号 (兼容旧接口)
     */
    public Integer getPort() {
        return portNumber;
    }
    
    /**
     * 设置端口号 (兼容旧接口)
     */
    public void setPort(Integer port) {
        this.portNumber = port;
    }
}
