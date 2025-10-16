package com.threatdetection.alert.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SMTP服务器配置实体
 * 支持动态配置SMTP服务器，无需重启应用
 */
@Entity
@Table(name = "smtp_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 配置名称 (唯一标识)
     */
    @Column(name = "config_name", nullable = false, unique = true, length = 100)
    private String configName;
    
    /**
     * SMTP服务器地址
     */
    @Column(name = "host", nullable = false)
    private String host;
    
    /**
     * SMTP端口
     */
    @Column(name = "port", nullable = false)
    private Integer port;
    
    /**
     * SMTP用户名
     */
    @Column(name = "username", nullable = false)
    private String username;
    
    /**
     * SMTP密码 (应加密存储)
     */
    @Column(name = "password", nullable = false, length = 500)
    private String password;
    
    /**
     * 发件人邮箱地址
     */
    @Column(name = "from_address", nullable = false)
    private String fromAddress;
    
    /**
     * 发件人显示名称
     */
    @Column(name = "from_name")
    private String fromName;
    
    /**
     * 是否启用TLS
     */
    @Column(name = "enable_tls")
    @Builder.Default
    private Boolean enableTls = false;
    
    /**
     * 是否启用SSL
     */
    @Column(name = "enable_ssl")
    @Builder.Default
    private Boolean enableSsl = false;
    
    /**
     * 是否启用STARTTLS
     */
    @Column(name = "enable_starttls")
    @Builder.Default
    private Boolean enableStarttls = false;
    
    /**
     * 连接超时时间(毫秒)
     */
    @Column(name = "connection_timeout")
    @Builder.Default
    private Integer connectionTimeout = 5000;
    
    /**
     * 读取超时时间(毫秒)
     */
    @Column(name = "timeout")
    @Builder.Default
    private Integer timeout = 5000;
    
    /**
     * 写入超时时间(毫秒)
     */
    @Column(name = "write_timeout")
    @Builder.Default
    private Integer writeTimeout = 5000;
    
    /**
     * 是否激活
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * 是否为默认配置
     */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;
    
    /**
     * 配置描述
     */
    @Column(name = "description", length = 500)
    private String description;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * 创建人
     */
    @Column(name = "created_by", length = 100)
    @Builder.Default
    private String createdBy = "system";
    
    /**
     * 更新人
     */
    @Column(name = "updated_by", length = 100)
    @Builder.Default
    private String updatedBy = "system";
    
    /**
     * 更新时间戳
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
