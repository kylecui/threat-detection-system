package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 白名单配置实体类
 * 
 * <p>用于配置和管理各类白名单:
 * - IP白名单: 信任的IP地址
 * - MAC白名单: 信任的MAC地址
 * - 端口白名单: 信任的端口号
 * - 组合白名单: IP+MAC组合
 * 
 * <p>使用场景:
 * - 管理员工作站白名单
 * - IT运维设备白名单
 * - 监控服务器白名单
 * - 业务服务端口白名单
 * - 临时访问白名单 (带过期时间)
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Entity
@Table(name = "whitelist_config", indexes = {
    @Index(name = "idx_whitelist_customer", columnList = "customer_id"),
    @Index(name = "idx_whitelist_type", columnList = "whitelist_type"),
    @Index(name = "idx_whitelist_ip", columnList = "ip_address"),
    @Index(name = "idx_whitelist_mac", columnList = "mac_address"),
    @Index(name = "idx_whitelist_active", columnList = "is_active"),
    @Index(name = "idx_whitelist_expires", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistConfig {
    
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 客户ID (多租户隔离)
     */
    @NotNull(message = "客户ID不能为空")
    @Size(max = 50, message = "客户ID长度不能超过50")
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;
    
    /**
     * 白名单类型
     * 
     * <p>可选值:
     * - IP: IP地址白名单
     * - MAC: MAC地址白名单
     * - PORT: 端口白名单
     * - COMBINED: 组合白名单 (IP+MAC)
     */
    @NotNull(message = "白名单类型不能为空")
    @Size(max = 20, message = "白名单类型长度不能超过20")
    @Column(name = "whitelist_type", nullable = false, length = 20)
    private String whitelistType;
    
    /**
     * IP地址
     * 
     * <p>仅当whitelistType为IP或COMBINED时有效
     * 支持IPv4和IPv6
     */
    @Size(max = 45, message = "IP地址长度不能超过45")
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    /**
     * MAC地址
     * 
     * <p>仅当whitelistType为MAC或COMBINED时有效
     * 格式: 00:11:22:33:44:55
     */
    @Size(max = 17, message = "MAC地址长度不能超过17")
    @Column(name = "mac_address", length = 17)
    private String macAddress;
    
    /**
     * 端口号
     * 
     * <p>仅当whitelistType为PORT时有效
     * 范围: 1-65535
     */
    @Column(name = "port_number")
    private Integer portNumber;
    
    /**
     * 白名单原因/说明
     */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    /**
     * 创建人
     */
    @Size(max = 100, message = "创建人长度不能超过100")
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    /**
     * 过期时间
     * 
     * <p>NULL表示永久有效
     * 非NULL表示临时白名单,到期后自动失效
     */
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    /**
     * 是否激活
     * 
     * <p>true: 激活 (生效中)
     * false: 禁用 (不生效)
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
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
    
    /**
     * 持久化前自动设置时间戳
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    /**
     * 更新前自动更新时间戳
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * 检查白名单是否已过期
     * 
     * @return true表示已过期, false表示未过期或永久有效
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // 永久有效
        }
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * 检查白名单是否有效 (激活且未过期)
     * 
     * @return true表示有效, false表示无效
     */
    public boolean isValid() {
        return isActive != null && isActive && !isExpired();
    }
}
