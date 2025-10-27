package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 聚合攻击数据 - 来自Flink流处理引擎的1分钟聚合结果
 * 
 * <p>蜜罐机制数据说明:
 * - uniqueIps: 攻击者访问的诱饵IP数量 (横向移动范围)
 * - uniquePorts: 攻击者尝试的端口种类 (攻击意图多样性)
 * - attackCount: 对诱饵的探测次数 (所有访问都是恶意的)
 * - uniqueDevices: 检测到该攻击者的蜜罐设备数
 * 
 * @author Security Team
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedAttackData {
    
    /**
     * 客户ID (租户隔离)
     */
    private String customerId;
    
    /**
     * 被诱捕者MAC地址 (内网失陷主机)
     */
    private String attackMac;
    
    /**
     * 被诱捕者IP地址 (内网地址)
     */
    private String attackIp;
    
    /**
     * 访问最多的蜜罐IP (用于计算蜜罐敏感度权重)
     * V4.0 Phase 2新增字段
     */
    private String mostAccessedHoneypotIp;
    
    /**
     * 探测次数 (1分钟窗口内的攻击次数)
     */
    private Integer attackCount;
    
    /**
     * 访问的诱饵IP数量 (横向移动范围指标)
     */
    private Integer uniqueIps;
    
    /**
     * 尝试的端口种类 (攻击意图多样性指标)
     */
    private Integer uniquePorts;
    
    /**
     * 检测到该攻击者的蜜罐设备数量 (影响范围)
     */
    private Integer uniqueDevices;
    
    /**
     * 聚合窗口时间戳
     */
    private Instant timestamp;
    
    /**
     * 端口列表 (用于端口权重计算)
     * V4.0 Phase 3新增字段
     */
    private java.util.List<Integer> portList;
    
    /**
     * 验证数据完整性
     */
    public boolean isValid() {
        return customerId != null && !customerId.isBlank()
            && attackMac != null && !attackMac.isBlank()
            && attackIp != null && !attackIp.isBlank()
            && attackCount != null && attackCount > 0
            && uniqueIps != null && uniqueIps > 0
            && uniquePorts != null && uniquePorts > 0
            && uniqueDevices != null && uniqueDevices > 0
            && timestamp != null;
    }
}
