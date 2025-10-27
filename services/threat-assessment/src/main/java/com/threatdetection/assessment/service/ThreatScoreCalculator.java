package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AggregatedAttackData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 威胁评分计算器 - 基于蜜罐机制的多维度评分算法
 * 
 * <p>核心公式 (V4.0双维度 - Phase 2已实施):
 * threatScore = (attackCount × uniqueIps × uniquePorts) 
 *             × timeWeight × ipWeight × portWeight × deviceWeight 
 *             × (attackSourceWeight × honeypotSensitivityWeight)
 * 
 * <p>V4.0 双维度权重说明:
 * - attackSourceWeight (0.5-3.0): 评估"被诱捕设备的严重性" (IoT=0.9, DB服务器=3.0)
 * - honeypotSensitivityWeight (1.0-3.5): 评估"攻击者意图的严重性" (管理蜜罐=3.5, 办公蜜罐=1.3)
 * - 关键案例: IoT(0.9) × 管理蜜罐(3.5) = 3.15 → 评分提升3.15倍 → CRITICAL威胁
 * 
 * <p>对齐原系统:
 * total_score = count_port × sum_ip × count_attack × score_weighting
 * 
 * @author Security Team
 * @version 4.0-Phase2
 */
@Component
public class ThreatScoreCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatScoreCalculator.class);
    
    private final PortRiskService portRiskService;
    private final IpSegmentWeightService ipSegmentWeightService;  // 保留用于兼容性
    private final IpSegmentWeightServiceV4 ipSegmentWeightServiceV4;  // V4.0双维度服务
    private final CustomerPortWeightService customerPortWeightService;  // V4.0 Phase3端口权重服务
    
    @Autowired
    public ThreatScoreCalculator(PortRiskService portRiskService, 
                                 IpSegmentWeightService ipSegmentWeightService,
                                 IpSegmentWeightServiceV4 ipSegmentWeightServiceV4,
                                 CustomerPortWeightService customerPortWeightService) {
        this.portRiskService = portRiskService;
        this.ipSegmentWeightService = ipSegmentWeightService;
        this.ipSegmentWeightServiceV4 = ipSegmentWeightServiceV4;
        this.customerPortWeightService = customerPortWeightService;
    }
    
    /**
     * 计算威胁评分
     * 
     * <p>V4.0 Phase 2 - 双维度权重完整实施:
     * 
     * <p>双维度权重说明:
     * - attackSourceWeight: 被诱捕设备的严重性 (IoT=0.9, 数据库服务器=3.0)
     * - honeypotSensitivityWeight: 攻击者意图的严重性 (管理蜜罐=3.5, 办公蜜罐=1.3)
     * - combinedSegmentWeight = attackSourceWeight × honeypotSensitivityWeight
     * 
     * <p>关键案例验证:
     * - IoT设备访问管理蜜罐 → 0.9 × 3.5 = 3.15 → CRITICAL级别 ✅
     * - 数据库服务器访问办公蜜罐 → 3.0 × 1.3 = 3.9 → CRITICAL级别
     * - 办公设备访问办公蜜罐 → 1.0 × 1.3 = 1.3 → MEDIUM级别
     * 
     * @param data 聚合攻击数据 (包含 attackIp 和 mostAccessedHoneypotIp)
     * @return 威胁评分 (0.0 - 无限大)
     */
    public double calculateThreatScore(AggregatedAttackData data) {
        if (!data.isValid()) {
            logger.warn("Invalid aggregated data: {}", data);
            return 0.0;
        }
        
        // 基础评分 = 攻击次数 × 唯一IP数 × 唯一端口数
        double baseScore = (double) data.getAttackCount() 
                         * data.getUniqueIps() 
                         * data.getUniquePorts();
        
        // 计算各维度权重
        double timeWeight = calculateTimeWeight(data.getTimestamp());
        double ipWeight = calculateIpWeight(data.getUniqueIps());
        // V4.0 Phase 3: 使用增强端口权重 (集成customer_port_weights)
        double portWeight = calculateEnhancedPortWeight(data.getCustomerId(), data.getPortList(), data.getUniquePorts());
        double deviceWeight = calculateDeviceWeight(data.getUniqueDevices());
        
        // V4.0双维度权重计算
        double attackSourceWeight = 1.0;  // 默认值 (向后兼容)
        double honeypotSensitivityWeight = 1.0;  // 默认值 (向后兼容)
        
        if (data.getAttackIp() != null && !data.getAttackIp().isEmpty()) {
            String customerId = data.getCustomerId();
            String attackIp = data.getAttackIp();
            
            // 第一维度: 攻击源权重 (被诱捕设备的严重性)
            attackSourceWeight = ipSegmentWeightServiceV4.getAttackSourceWeight(customerId, attackIp);
            
            logger.info("V4.0 attack source weight applied: customerId={}, attackIp={}, weight={}",
                       customerId, attackIp, attackSourceWeight);
        } else {
            logger.debug("Missing attackIp for V4.0 attack source weight, using default (1.0)");
        }
        
        // V4.0 Phase 2: 蜜罐敏感度权重
        if (data.getMostAccessedHoneypotIp() != null && !data.getMostAccessedHoneypotIp().isEmpty()) {
            String customerId = data.getCustomerId();
            String honeypotIp = data.getMostAccessedHoneypotIp();
            
            // 第二维度: 蜜罐敏感度权重 (攻击者意图的严重性)
            honeypotSensitivityWeight = ipSegmentWeightServiceV4
                .getHoneypotSensitivityWeight(customerId, honeypotIp);
            
            logger.info("V4.0 honeypot sensitivity weight applied: customerId={}, honeypotIp={}, weight={}",
                       customerId, honeypotIp, honeypotSensitivityWeight);
        } else {
            logger.debug("Missing mostAccessedHoneypotIp for V4.0 honeypot weight, using default (1.0)");
        }
        
        // 计算综合IP段权重 (双维度)
        double combinedSegmentWeight = attackSourceWeight * honeypotSensitivityWeight;
        
        logger.info("V4.0 combined segment weight: customerId={}, attackIp={}, honeypotIp={}, " +
                   "attackSourceWeight={}, honeypotSensitivityWeight={}, combinedWeight={}",
                   data.getCustomerId(), data.getAttackIp(), data.getMostAccessedHoneypotIp(),
                   attackSourceWeight, honeypotSensitivityWeight, combinedSegmentWeight);
        
        // 最终评分 (V4.0 Phase 2 - 双维度权重)
        double finalScore = baseScore * timeWeight * ipWeight * portWeight * deviceWeight 
                          * combinedSegmentWeight;
        
        logger.debug("Threat score calculation: customerId={}, attackMac={}, attackIp={}, honeypotIp={}, " +
                    "baseScore={}, timeWeight={}, ipWeight={}, portWeight={}, deviceWeight={}, " +
                    "attackSourceWeight={}, honeypotSensitivityWeight={}, combinedSegmentWeight={}, finalScore={}",
                    data.getCustomerId(), data.getAttackMac(), data.getAttackIp(), data.getMostAccessedHoneypotIp(),
                    baseScore, timeWeight, ipWeight, portWeight, deviceWeight, 
                    attackSourceWeight, honeypotSensitivityWeight, combinedSegmentWeight, finalScore);
        
        return finalScore;
    }
    
    /**
     * 计算时间权重 (基于攻击发生时段)
     * 
     * <p>深夜时段权重更高,因为深夜攻击更可能是APT行为
     * 
     * @param timestamp 攻击时间戳
     * @return 时间权重 (0.8-1.2)
     */
    public double calculateTimeWeight(Instant timestamp) {
        LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
        int hour = time.getHour();
        
        if (hour >= 0 && hour < 6) {
            return 1.2;  // 深夜异常行为 (00:00-06:00)
        } else if (hour >= 6 && hour < 9) {
            return 1.1;  // 早晨时段 (06:00-09:00)
        } else if (hour >= 9 && hour < 17) {
            return 1.0;  // 工作时间基准 (09:00-17:00)
        } else if (hour >= 17 && hour < 21) {
            return 0.9;  // 傍晚时段 (17:00-21:00)
        } else {
            return 0.8;  // 夜间时段 (21:00-24:00)
        }
    }
    
    /**
     * 计算IP权重 (基于横向移动范围)
     * 
     * <p>访问的诱饵IP越多,说明横向移动范围越广,威胁越大
     * 
     * @param uniqueIps 唯一诱饵IP数量
     * @return IP权重 (1.0-2.0)
     */
    public double calculateIpWeight(int uniqueIps) {
        if (uniqueIps >= 10) {
            return 2.0;  // 大规模横向移动 (10+个IP)
        } else if (uniqueIps >= 6) {
            return 1.7;  // 广泛扫描 (6-10个IP)
        } else if (uniqueIps >= 4) {
            return 1.5;  // 中等扫描 (4-5个IP)
        } else if (uniqueIps >= 2) {
            return 1.3;  // 小范围扫描 (2-3个IP)
        } else {
            return 1.0;  // 单一目标 (1个IP)
        }
    }
    
    /**
     * 计算端口权重 (基于攻击意图多样性)
     * 
     * <p>尝试的端口种类越多,说明攻击手段越多样,威胁越大
     * 
     * <p>Phase 2增强: 基于多样性的基础权重
     * 
     * @param uniquePorts 唯一端口数量
     * @return 端口权重 (1.0-2.0)
     */
    public double calculatePortWeight(int uniquePorts) {
        if (uniquePorts >= 20) {
            return 2.0;  // 全端口扫描 (20+个端口)
        } else if (uniquePorts >= 11) {
            return 1.8;  // 大规模扫描 (11-20个端口)
        } else if (uniquePorts >= 6) {
            return 1.6;  // 广泛扫描 (6-10个端口)
        } else if (uniquePorts >= 4) {
            return 1.4;  // 中等扫描 (4-5个端口)
        } else if (uniquePorts >= 2) {
            return 1.2;  // 小范围探测 (2-3个端口)
        } else {
            return 1.0;  // 单端口攻击 (1个端口)
        }
    }
    
    /**
     * 计算增强的端口权重 (集成customer_port_weights)
     * 
     * <p>V4.0 Phase 3新方法: 混合策略
     * 1. 如果提供端口列表和customerId,使用CustomerPortWeightService查询配置权重
     * 2. 同时考虑端口多样性权重
     * 3. 取两者最大值: portWeight = max(avgConfigWeight, diversityWeight)
     * 
     * @param customerId 客户ID (用于查询客户配置)
     * @param portNumbers 端口号列表 (可选)
     * @param uniquePorts 唯一端口数量
     * @return 端口权重 (1.0-2.0+)
     */
    public double calculateEnhancedPortWeight(String customerId, List<Integer> portNumbers, int uniquePorts) {
        // 计算基于多样性的权重
        double diversityWeight = calculatePortWeight(uniquePorts);
        
        if (portNumbers == null || portNumbers.isEmpty() || customerId == null) {
            logger.debug("No port list or customerId provided, using diversity weight: {}", diversityWeight);
            return diversityWeight;
        }
        
        // 批量查询端口权重配置
        Map<Integer, Double> portWeights = customerPortWeightService.getPortWeightsBatch(customerId, portNumbers);
        
        // 计算平均配置权重
        double avgConfigWeight = portWeights.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(1.0);
        
        // 取两者最大值
        double finalWeight = Math.max(avgConfigWeight, diversityWeight);
        
        logger.info("Enhanced port weight: customerId={}, ports={}, avgConfig={}, diversity={}, final={}",
                   customerId, portNumbers, avgConfigWeight, diversityWeight, finalWeight);
        
        return finalWeight;
    }
    
    /**
     * 计算设备权重 (基于影响范围)
     * 
     * <p>多个蜜罐设备检测到同一攻击者,说明影响范围更广
     * 
     * @param uniqueDevices 唯一蜜罐设备数量
     * @return 设备权重 (1.0-1.5)
     */
    public double calculateDeviceWeight(int uniqueDevices) {
        if (uniqueDevices >= 2) {
            return 1.5;  // 多设备检测 (跨网络段)
        } else {
            return 1.0;  // 单设备检测
        }
    }
    
    /**
     * 判定威胁等级
     * 
     * @param threatScore 威胁评分
     * @return 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    public String determineThreatLevel(double threatScore) {
        if (threatScore > 200) {
            return "CRITICAL";  // 严重威胁
        } else if (threatScore > 100) {
            return "HIGH";      // 高危威胁
        } else if (threatScore > 50) {
            return "MEDIUM";    // 中危威胁
        } else if (threatScore > 10) {
            return "LOW";       // 低危威胁
        } else {
            return "INFO";      // 信息级别
        }
    }
    
    /**
     * 计算IP段权重 (Phase 3新增方法)
     * 
     * <p>基于攻击源IP所属网段调整权重:
     * - 内网IP (0.5-0.8): 降低权重,内网失陷主机风险较低
     * - 正常公网 (0.9-1.1): 基准权重
     * - 云服务商 (1.2-1.3): 可能是云主机被入侵
     * - 高危地区 (1.6-1.9): 显著提高权重
     * - 已知恶意 (2.0): 最高权重
     * 
     * @param ipAddress 攻击源IP地址
     * @return IP段权重 (0.5-2.0)
     */
    public double calculateIpSegmentWeight(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 1.0;  // 默认权重
        }
        return ipSegmentWeightService.getIpSegmentWeight(ipAddress);
    }
}
