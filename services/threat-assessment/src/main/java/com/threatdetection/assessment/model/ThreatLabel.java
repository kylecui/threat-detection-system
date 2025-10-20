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
 * 威胁标签实体类
 * 
 * <p>用于定义和管理各类威胁标签:
 * - APT攻击标签 (横向移动、侦察扫描、C2通信等)
 * - 勒索软件标签 (SMB传播、RDP入侵、加密行为等)
 * - 扫描行为标签 (端口扫描、服务识别、漏洞扫描等)
 * - 横向移动标签 (RDP、SMB、SSH等)
 * - 暴力破解标签 (RDP、SSH、数据库等)
 * - 数据窃取标签 (数据库、文件、大量传输等)
 * 
 * <p>严重程度:
 * - INFO: 信息级别
 * - LOW: 低危
 * - MEDIUM: 中危
 * - HIGH: 高危
 * - CRITICAL: 严重
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Entity
@Table(name = "threat_labels", indexes = {
    @Index(name = "idx_label_code", columnList = "label_code", unique = true),
    @Index(name = "idx_label_category", columnList = "category"),
    @Index(name = "idx_label_severity", columnList = "severity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatLabel {
    
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 标签代码 (唯一标识)
     * 
     * <p>示例:
     * - "APT_LATERAL_MOVE" (APT横向移动)
     * - "RANSOMWARE_SMB" (勒索软件SMB传播)
     * - "SCAN_PORT_FULL" (全端口扫描)
     */
    @NotNull(message = "标签代码不能为空")
    @Size(max = 50, message = "标签代码长度不能超过50")
    @Column(name = "label_code", nullable = false, unique = true, length = 50)
    private String labelCode;
    
    /**
     * 标签名称
     * 
     * <p>示例: "APT横向移动", "勒索软件SMB传播"
     */
    @NotNull(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
    @Column(name = "label_name", nullable = false, length = 100)
    private String labelName;
    
    /**
     * 标签分类
     * 
     * <p>可选值:
     * - APT: APT攻击
     * - RANSOMWARE: 勒索软件
     * - SCANNING: 扫描行为
     * - LATERAL_MOVEMENT: 横向移动
     * - BRUTE_FORCE: 暴力破解
     * - DATA_EXFILTRATION: 数据窃取
     * - MALWARE: 恶意软件
     * - NETWORK_ANOMALY: 网络异常
     * - INSIDER_THREAT: 内部威胁
     * - EXPLOITATION: 漏洞利用
     * - DOS: 拒绝服务攻击
     * - WEB: Web攻击
     */
    @NotNull(message = "标签分类不能为空")
    @Size(max = 50, message = "标签分类长度不能超过50")
    @Column(name = "category", nullable = false, length = 50)
    private String category;
    
    /**
     * 严重程度
     * 
     * <p>可选值:
     * - INFO: 信息级别
     * - LOW: 低危
     * - MEDIUM: 中危
     * - HIGH: 高危
     * - CRITICAL: 严重
     */
    @NotNull(message = "严重程度不能为空")
    @Size(max = 20, message = "严重程度长度不能超过20")
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;
    
    /**
     * 描述信息
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 自动打标签规则 (JSON格式)
     * 
     * <p>可选,用于定义自动匹配规则
     */
    @Column(name = "auto_tag_rules", columnDefinition = "TEXT")
    private String autoTagRules;
    
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
