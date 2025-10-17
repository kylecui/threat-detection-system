package com.threatdetection.customer.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 客户实体类
 * 
 * <p>表示系统中的客户/租户，每个客户拥有独立的数据隔离和配置
 */
@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id", unique = true),
    @Index(name = "idx_customer_email", columnList = "email"),
    @Index(name = "idx_customer_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 客户唯一标识符 (用于系统内部引用)
     * 格式: customer_xxx 或自定义字符串
     */
    @Column(name = "customer_id", nullable = false, unique = true, length = 100)
    @NotBlank(message = "客户ID不能为空")
    @Size(max = 100, message = "客户ID长度不能超过100")
    private String customerId;

    /**
     * 客户名称/公司名称
     */
    @Column(name = "name", nullable = false, length = 200)
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称长度不能超过200")
    private String name;

    /**
     * 客户联系邮箱
     */
    @Column(name = "email", nullable = false, length = 255)
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 联系电话
     */
    @Column(name = "phone", length = 50)
    @Size(max = 50, message = "电话长度不能超过50")
    private String phone;

    /**
     * 公司地址
     */
    @Column(name = "address", length = 500)
    @Size(max = 500, message = "地址长度不能超过500")
    private String address;

    /**
     * 客户状态
     * ACTIVE: 正常激活
     * SUSPENDED: 暂停服务
     * INACTIVE: 已停用
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    /**
     * 订阅套餐类型
     * FREE: 免费版
     * BASIC: 基础版
     * PROFESSIONAL: 专业版
     * ENTERPRISE: 企业版
     */
    @Column(name = "subscription_tier", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.BASIC;

    /**
     * 最大设备数量限制
     */
    @Column(name = "max_devices")
    @Builder.Default
    private Integer maxDevices = 10;

    /**
     * 当前绑定设备数量 (冗余字段，用于快速查询)
     */
    @Column(name = "current_devices")
    @Builder.Default
    private Integer currentDevices = 0;

    /**
     * 客户描述/备注
     */
    @Column(name = "description", length = 1000)
    @Size(max = 1000, message = "描述长度不能超过1000")
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
     * 订阅开始日期
     */
    @Column(name = "subscription_start_date")
    private Instant subscriptionStartDate;

    /**
     * 订阅结束日期
     */
    @Column(name = "subscription_end_date")
    private Instant subscriptionEndDate;

    /**
     * 是否启用告警通知
     */
    @Column(name = "alert_enabled")
    @Builder.Default
    private Boolean alertEnabled = true;

    /**
     * 更新时间戳
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 客户状态枚举
     */
    public enum CustomerStatus {
        ACTIVE,      // 激活
        SUSPENDED,   // 暂停
        INACTIVE     // 停用
    }

    /**
     * 订阅套餐枚举
     */
    public enum SubscriptionTier {
        FREE,          // 免费版 (最多5设备)
        BASIC,         // 基础版 (最多20设备)
        PROFESSIONAL,  // 专业版 (最多100设备)
        ENTERPRISE     // 企业版 (无限制)
    }
}
