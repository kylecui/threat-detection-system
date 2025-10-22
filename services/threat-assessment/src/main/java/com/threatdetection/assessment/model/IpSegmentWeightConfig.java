package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * IP段权重配置实体类
 * 
 * <p>用于存储不同IP段的风险权重配置,支持:
 * - 内网网段 (RFC 1918)
 * - 云服务商网段
 * - 高危地区网段
 * - 已知恶意网段
 * - VPN/Tor出口节点
 * 
 * <p>权重范围: 0.5 - 2.0
 * - 0.5-0.8: 内网/本地 (低风险)
 * - 0.9-1.1: 正常公网 (基准)
 * - 1.2-1.5: 云服务商/VPN (中等)
 * - 1.6-1.9: 高危地区/Tor (高风险)
 * - 2.0: 已知恶意网段 (极高风险)
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 3
 */
@Entity
@Table(name = "ip_segment_weight_config", indexes = {
    @Index(name = "idx_segment_start", columnList = "ip_range_start"),
    @Index(name = "idx_segment_end", columnList = "ip_range_end"),
    @Index(name = "idx_segment_category", columnList = "category"),
    @Index(name = "idx_segment_weight", columnList = "weight"),
    @Index(name = "idx_segment_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpSegmentWeightConfig {
    
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 网段名称
     * 
     * <p>示例:
     * - "Private-10.0.0.0/8" (内网)
     * - "AWS-US-East-1a" (云服务商)
     * - "Russia-Moscow" (高危地区)
     * - "Malicious-Botnet-1" (恶意网段)
     */
    @NotNull(message = "网段名称不能为空")
    @Size(max = 100, message = "网段名称长度不能超过100")
    @Column(name = "segment_name", nullable = false, length = 100)
    private String segmentName;
    
    /**
     * 网段起始IP
     * 
     * <p>支持IPv4和IPv6格式
     * 示例: "10.0.0.0", "2001:db8::"
     */
    @NotNull(message = "起始IP不能为空")
    @Size(max = 45, message = "IP地址长度不能超过45")
    @Column(name = "ip_range_start", nullable = false, length = 45)
    private String ipRangeStart;
    
    /**
     * 网段结束IP
     * 
     * <p>示例: "10.255.255.255", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff"
     */
    @NotNull(message = "结束IP不能为空")
    @Size(max = 45, message = "IP地址长度不能超过45")
    @Column(name = "ip_range_end", nullable = false, length = 45)
    private String ipRangeEnd;
    
    /**
     * 权重系数
     * 
     * <p>范围: 0.5 - 2.0
     * - 内网: 0.5-0.8
     * - 正常公网: 0.9-1.1
     * - 云服务商: 1.2-1.3
     * - 高危地区: 1.6-1.9
     * - 恶意网段: 2.0
     */
    @NotNull(message = "权重不能为空")
    @DecimalMin(value = "0.5", message = "权重不能小于0.5")
    @DecimalMax(value = "2.0", message = "权重不能大于2.0")
    @Column(name = "weight", nullable = false, columnDefinition = "DECIMAL(3,2)")
    private BigDecimal weight;
    
    /**
     * 网段分类
     * 
     * <p>可选值:
     * - PRIVATE: 内网
     * - LOCALHOST: 本地回环
     * - LINK_LOCAL: 链路本地
     * - CLOUD_AWS: AWS云
     * - CLOUD_AZURE: Azure云
     * - CLOUD_GCP: Google云
     * - CLOUD_ALIYUN: 阿里云
     * - CLOUD_TENCENT: 腾讯云
     * - HIGH_RISK_REGION: 高危地区
     * - MALICIOUS: 已知恶意
     * - TOR_EXIT: Tor出口
     * - VPN_PROVIDER: VPN服务商
     * - ISP_CHINA: 中国ISP
     * - SPECIAL: 特殊用途
     */
    @NotNull(message = "分类不能为空")
    @Size(max = 50, message = "分类长度不能超过50")
    @Column(name = "category", nullable = false, length = 50)
    private String category;
    
    /**
     * 描述信息
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 优先级
     * 
     * <p>用于IP匹配冲突时选择 (值越大优先级越高)
     * - 1-10: 特殊地址 (本地、保留)
     * - 20-30: ISP网段
     * - 50: 云服务商
     * - 60-70: VPN/Tor
     * - 80-90: 高危地区
     * - 100: 已知恶意
     */
    @Builder.Default
    @Column(name = "priority")
    private Integer priority = 100;
    
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
}
