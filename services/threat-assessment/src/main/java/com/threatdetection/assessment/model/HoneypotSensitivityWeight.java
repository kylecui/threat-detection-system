package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 蜜罐敏感度权重配置实体 (V4.0)
 * 
 * <p>评估问题: 攻击者尝试访问这个诱饵，意图有多严重？
 * 
 * <p>权重范围: 1.0-3.5
 * - 3.5: 管理区/数据库蜜罐 (尝试控制全网/窃取数据)
 * - 3.0: 核心业务蜜罐 (尝试破坏业务)
 * - 2.5: 文件服务器蜜罐 (勒索软件目标)
 * - 2.0: Web服务器蜜罐 (跳板攻击)
 * - 1.5: 高管办公区蜜罐 (高价值目标)
 * - 1.3: 办公区蜜罐 (横向移动探测)
 * - 1.0: 低价值蜜罐 (基准)
 * 
 * @author ThreatDetection Team
 * @version 4.0
 * @since 2025-10-24
 */
@Entity
@Table(name = "honeypot_sensitivity_weights",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_customer_honeypot",
           columnNames = {"customer_id", "honeypot_name"}
       ),
       indexes = {
           @Index(name = "idx_honeypot_customer_active", columnList = "customer_id, is_active"),
           @Index(name = "idx_honeypot_ip_range", columnList = "customer_id, ip_range_start, ip_range_end"),
           @Index(name = "idx_honeypot_sensitivity_level", columnList = "sensitivity_level"),
           @Index(name = "idx_honeypot_deployment_zone", columnList = "deployment_zone")
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
     * 蜜罐名称 (客户自定义标识)
     */
    @Column(name = "honeypot_name", nullable = false)
    private String honeypotName;
    
    /**
     * 蜜罐IP范围起始地址
     */
    @Column(name = "ip_range_start", nullable = false, length = 15)
    private String ipRangeStart;
    
    /**
     * 蜜罐IP范围结束地址
     */
    @Column(name = "ip_range_end", nullable = false, length = 15)
    private String ipRangeEnd;
    
    /**
     * 蜜罐层级
     * CRITICAL_ASSET, HIGH_VALUE, MEDIUM_VALUE, LOW_VALUE, DECOY
     */
    @Column(name = "honeypot_tier", nullable = false, length = 50)
    private String honeypotTier;
    
    /**
     * 部署区域
     * MANAGEMENT, DATABASE, CORE_SERVER, APP_SERVER, OFFICE, DMZ
     */
    @Column(name = "deployment_zone", nullable = false, length = 50)
    private String deploymentZone;
    
    /**
     * 敏感度等级
     * CRITICAL, HIGH, MEDIUM, LOW, VERY_LOW
     */
    @Column(name = "sensitivity_level", nullable = false, length = 20)
    private String sensitivityLevel;
    
    /**
     * 敏感度权重 (1.0-3.5)
     * 评估攻击者意图的严重程度
     */
    @Column(name = "weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal weight;
    
    /**
     * 蜜罐模拟的服务类型
     * 如: SSH, RDP, Database, FileShare
     */
    @Column(name = "simulated_service", length = 100)
    private String simulatedService;
    
    /**
     * 反映的攻击意图
     * 如: 全网控制, 数据窃取, 横向移动
     */
    @Column(name = "attack_intent", length = 100)
    private String attackIntent;
    
    /**
     * 蜜罐描述信息
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
     * 优先级 (用于IP范围重叠时的匹配顺序)
     * 数值越大优先级越高
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
