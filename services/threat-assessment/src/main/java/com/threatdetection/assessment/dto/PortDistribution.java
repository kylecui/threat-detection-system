package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 端口分布DTO
 * 
 * <p>对齐前端需求: 原始攻击事件端口分布数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortDistribution {
    
    /**
     * 端口号
     */
    private Integer port;
    
    /**
     * 已知端口的服务名称 (如 "RDP", "SMB")；未知端口为空
     */
    private String portName;
    
    /**
     * 该端口的攻击次数
     */
    private Long count;
    
    /**
     * 占比百分比
     */
    private Double percentage;
}
