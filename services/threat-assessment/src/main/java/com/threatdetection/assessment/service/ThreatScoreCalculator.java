package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AggregatedAttackData;
import com.threatdetection.assessment.dto.ScoreBreakdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 威胁评分计算器 - 基于蜜罐机制的多维度评分算法
 * 
 * <p>核心公式 (V5.1攻击速率增强 - 爆发性检测):
 * threatScore = (attackCount × uniqueIps × uniquePorts) 
 *             × timeWeight × ipWeight × portWeight × deviceWeight 
 *             × (attackSourceWeight × honeypotSensitivityWeight)
 *             × attackRateWeight  // V5.1新增: 攻击速率权重
 * 
 * <p>V5.1 攻击速率权重说明:
 * - attackRate = attackCount / timeWindowSeconds (次/秒)
 * - 高速率爆发 (>1次/秒): 权重 1.5-2.5x → 快速提升评分
 * - 中等速率 (0.1-1次/秒): 权重 1.0-1.5x → 标准评分
 * - 低速率持续 (<0.1次/秒): 权重 0.5-1.0x → 降低评分
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
    private final CustomerTimeWeightService customerTimeWeightService;  // V5.0时间段权重服务
    private final MlWeightService mlWeightService;

    @Value("${ml.weight.enabled:false}")
    private boolean mlWeightEnabled;
    
    @Autowired
    public ThreatScoreCalculator(PortRiskService portRiskService, 
                                 IpSegmentWeightService ipSegmentWeightService,
                                 IpSegmentWeightServiceV4 ipSegmentWeightServiceV4,
                                 CustomerPortWeightService customerPortWeightService,
                                 CustomerTimeWeightService customerTimeWeightService,
                                 MlWeightService mlWeightService) {
        this.portRiskService = portRiskService;
        this.ipSegmentWeightService = ipSegmentWeightService;
        this.ipSegmentWeightServiceV4 = ipSegmentWeightServiceV4;
        this.customerPortWeightService = customerPortWeightService;
        this.customerTimeWeightService = customerTimeWeightService;
        this.mlWeightService = mlWeightService;
    }
    
    /**
     * 计算威胁评分
     * 
     * <p>V5.1 - 攻击速率权重实施 (爆发性检测):
     * 
     * <p>V5.1 攻击速率权重:
     * - attackRateWeight: 基于攻击速率 (attackCount / timeWindowSeconds)
     * - 高速率 (>1次/秒): 权重 1.5-2.5x → 爆发性攻击
     * - 中等速率 (0.1-1次/秒): 权重 1.0-1.5x → 标准扫描
     * - 低速率 (<0.1次/秒): 权重 0.5-1.0x → 慢速扫描
     * 
     * <p>V4.0 双维度权重说明:
     * - attackSourceWeight: 被诱捕设备的严重性 (IoT=0.9, 数据库服务器=3.0)
     * - honeypotSensitivityWeight: 攻击者意图的严重性 (管理蜜罐=3.5, 办公蜜罐=1.3)
     * - combinedSegmentWeight = attackSourceWeight × honeypotSensitivityWeight
     * 
     * <p>关键案例验证:
     * - IoT设备访问管理蜜罐 → 0.9 × 3.5 = 3.15 → CRITICAL级别 ✅
     * - 数据库服务器访问办公蜜罐 → 3.0 × 1.3 = 3.9 → CRITICAL级别
     * - 办公设备访问办公蜜罐 → 1.0 × 1.3 = 1.3 → MEDIUM级别
     * - 爆发性攻击 (30次/30秒=1次/秒) → attackRateWeight=1.8 → 评分提升80%
     * - 慢速扫描 (30次/3600秒=0.008次/秒) → attackRateWeight=0.6 → 评分降低40%
     * 
     * @param data 聚合攻击数据 (包含 attackIp, mostAccessedHoneypotIp, timeWindowSeconds)
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
        double timeWeight = calculateEnhancedTimeWeight(data.getCustomerId(), data.getTimestamp());
        double ipWeight = calculateIpWeight(data.getUniqueIps());
        // V4.0 Phase 3: 使用增强端口权重 (集成customer_port_weights)
        double portWeight = calculateEnhancedPortWeight(data.getCustomerId(), data.getPortList(), data.getUniquePorts());
        double deviceWeight = calculateDeviceWeight(data.getUniqueDevices());
        // V5.1: 计算攻击速率权重 (爆发性检测)
        double attackRateWeight = calculateAttackRateWeight(data.getAttackCount(), data.getTimeWindowSeconds());
        
        logger.info("📈 Threat score weights calculated: customerId={}, timeWeight={}, ipWeight={}, portWeight={}, deviceWeight={}, attackRateWeight={}",
                   data.getCustomerId(), timeWeight, ipWeight, portWeight, deviceWeight, attackRateWeight);
        
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
        
        // 最终评分 (V5.1 - 攻击速率权重)
        double rawScore = baseScore * timeWeight * ipWeight * portWeight * deviceWeight 
                         * combinedSegmentWeight * attackRateWeight;

        // ML Weight (Phase 2 - advisory multiplier from autoencoder anomaly detection)
        double mlWeight = 1.0;  // neutral default
        if (mlWeightEnabled) {
            Integer tier = data.getDetectionTier();
            mlWeight = mlWeightService.getMlWeight(data.getCustomerId(), data.getAttackMac(), tier);
            rawScore *= mlWeight;
            logger.info("ML weight applied: customerId={}, attackMac={}, mlWeight={}, mlEnabled=true",
                    data.getCustomerId(), data.getAttackMac(), mlWeight);
        } else {
            logger.debug("ML weight disabled, using default 1.0");
        }

        // 标准化到 (0,100) 范围 - 使用对数变换
        double normalizedScore = normalizeThreatScore(rawScore);
        
        logger.debug("Threat score calculation: customerId={}, attackMac={}, attackIp={}, honeypotIp={}, " +
                    "baseScore={}, timeWeight={}, ipWeight={}, portWeight={}, deviceWeight={}, " +
                    "attackSourceWeight={}, honeypotSensitivityWeight={}, combinedSegmentWeight={}, " +
                    "attackRateWeight={}, mlWeight={}, timeWindowSeconds={}, rawScore={}, normalizedScore={}",
                     data.getCustomerId(), data.getAttackMac(), data.getAttackIp(), data.getMostAccessedHoneypotIp(),
                     baseScore, timeWeight, ipWeight, portWeight, deviceWeight, 
                     attackSourceWeight, honeypotSensitivityWeight, combinedSegmentWeight, 
                     attackRateWeight, mlWeight, data.getTimeWindowSeconds(), rawScore, normalizedScore);
        
        return normalizedScore;
    }

    /**
     * 计算威胁评分并返回完整分解数据
     *
     * @param data 聚合攻击数据
     * @return 评分分解信息
     */
    public ScoreBreakdown calculateScoreWithBreakdown(AggregatedAttackData data) {
        if (!data.isValid()) {
            logger.warn("Invalid aggregated data: {}", data);
            return ScoreBreakdown.builder()
                    .baseScore(0.0)
                    .timeWeight(1.0)
                    .timeWeightNote("default")
                    .ipWeight(1.0)
                    .portWeight(1.0)
                    .portWeightNote("diversity")
                    .deviceWeight(1.0)
                    .attackSourceWeight(1.0)
                    .honeypotSensitivityWeight(1.0)
                    .combinedSegmentWeight(1.0)
                    .attackRateWeight(1.0)
                    .attackRate(0.0)
                    .mlWeight(1.0)
                    .mlEnabled(this.mlWeightEnabled)
                    .rawScore(0.0)
                    .normalizedScore(0.0)
                    .formula("log₁₀(baseScore × timeW × ipW × portW × deviceW × segmentW × rateW × mlW + 1) × 25")
                    .build();
        }

        double baseScore = (double) data.getAttackCount()
                * data.getUniqueIps()
                * data.getUniquePorts();

        double defaultTimeWeight = calculateTimeWeight(data.getTimestamp());
        double timeWeight = calculateEnhancedTimeWeight(data.getCustomerId(), data.getTimestamp());
        String timeWeightNote = Math.abs(timeWeight - defaultTimeWeight) > 0.001 ? "customer override" : "default";

        double ipWeight = calculateIpWeight(data.getUniqueIps());
        double diversityWeight = calculatePortWeight(data.getUniquePorts());
        double avgConfigWeight = 1.0;
        boolean hasCustomerPortConfigPath = data.getPortList() != null
                && !data.getPortList().isEmpty()
                && data.getCustomerId() != null;
        if (hasCustomerPortConfigPath) {
            Map<Integer, Double> portWeights = customerPortWeightService
                    .getPortWeightsBatch(data.getCustomerId(), data.getPortList());
            avgConfigWeight = portWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(1.0);
        }
        double portWeight = hasCustomerPortConfigPath
                ? Math.max(avgConfigWeight, diversityWeight)
                : diversityWeight;
        String portWeightNote = hasCustomerPortConfigPath && avgConfigWeight >= diversityWeight
                ? "customer config avg"
                : "diversity";

        double deviceWeight = calculateDeviceWeight(data.getUniqueDevices());
        double attackRateWeight = calculateAttackRateWeight(data.getAttackCount(), data.getTimeWindowSeconds());
        int windowSeconds = (data.getTimeWindowSeconds() != null && data.getTimeWindowSeconds() > 0)
                ? data.getTimeWindowSeconds() : 300;
        double attackRate = (double) data.getAttackCount() / windowSeconds;

        double attackSourceWeight = 1.0;
        double honeypotSensitivityWeight = 1.0;

        if (data.getAttackIp() != null && !data.getAttackIp().isEmpty()) {
            attackSourceWeight = ipSegmentWeightServiceV4.getAttackSourceWeight(data.getCustomerId(), data.getAttackIp());
            logger.info("V4.0 attack source weight applied: customerId={}, attackIp={}, weight={}",
                    data.getCustomerId(), data.getAttackIp(), attackSourceWeight);
        } else {
            logger.debug("Missing attackIp for V4.0 attack source weight, using default (1.0)");
        }

        if (data.getMostAccessedHoneypotIp() != null && !data.getMostAccessedHoneypotIp().isEmpty()) {
            honeypotSensitivityWeight = ipSegmentWeightServiceV4
                    .getHoneypotSensitivityWeight(data.getCustomerId(), data.getMostAccessedHoneypotIp());
            logger.info("V4.0 honeypot sensitivity weight applied: customerId={}, honeypotIp={}, weight={}",
                    data.getCustomerId(), data.getMostAccessedHoneypotIp(), honeypotSensitivityWeight);
        } else {
            logger.debug("Missing mostAccessedHoneypotIp for V4.0 honeypot weight, using default (1.0)");
        }

        double combinedSegmentWeight = attackSourceWeight * honeypotSensitivityWeight;
        double rawScore = baseScore * timeWeight * ipWeight * portWeight * deviceWeight
                * combinedSegmentWeight * attackRateWeight;

        double mlWeight = 1.0;
        if (mlWeightEnabled) {
            Integer tier = data.getDetectionTier();
            mlWeight = mlWeightService.getMlWeight(data.getCustomerId(), data.getAttackMac(), tier);
            rawScore *= mlWeight;
            logger.info("ML weight applied: customerId={}, attackMac={}, mlWeight={}, mlEnabled=true",
                    data.getCustomerId(), data.getAttackMac(), mlWeight);
        } else {
            logger.debug("ML weight disabled, using default 1.0");
        }

        double normalizedScore = normalizeThreatScore(rawScore);

        return ScoreBreakdown.builder()
                .baseScore(baseScore)
                .timeWeight(timeWeight)
                .timeWeightNote(timeWeightNote)
                .ipWeight(ipWeight)
                .portWeight(portWeight)
                .portWeightNote(portWeightNote)
                .deviceWeight(deviceWeight)
                .attackSourceWeight(attackSourceWeight)
                .honeypotSensitivityWeight(honeypotSensitivityWeight)
                .combinedSegmentWeight(combinedSegmentWeight)
                .attackRateWeight(attackRateWeight)
                .attackRate(attackRate)
                .mlWeight(mlWeight)
                .mlEnabled(this.mlWeightEnabled)
                .rawScore(rawScore)
                .normalizedScore(normalizedScore)
                .formula("log₁₀(baseScore × timeW × ipW × portW × deviceW × segmentW × rateW × mlW + 1) × 25")
                .build();
    }
    
    /**
     * 将原始威胁评分标准化到 (0,100) 范围
     * 
     * <p>使用对数变换将大范围的评分压缩到标准范围:
     * - 小威胁 (1-10): 映射到 1-25 左右
     * - 中等威胁 (100-1000): 映射到 25-50 左右  
     * - 高威胁 (1000+): 映射到 50-75 左右
     * - 极高威胁 (10000+): 映射到 75-100
     * 
     * <p>公式: normalizedScore = min(100, max(1, log10(rawScore + 1) * 25))
     * 
     * @param rawScore 原始威胁评分 (可能很大)
     * @return 标准化后的评分 (1-100)
     */
    private double normalizeThreatScore(double rawScore) {
        if (rawScore <= 0) {
            return 1.0;  // 最小评分
        }
        
        // 使用对数变换压缩范围: log10(rawScore + 1) * 25
        // +1 避免 log(0) 或 log(小数)
        // *25 是经验系数，使结果在合理范围内
        double normalized = Math.log10(rawScore + 1) * 25;
        
        // 确保在 (0,100) 范围内
        normalized = Math.max(1.0, Math.min(99.0, normalized));
        
        return normalized;
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
     * 计算增强的时间权重 (集成customer_time_weights)
     * 
     * <p>V5.0新方法: 支持客户自定义时间段权重
     * 1. 首先查询客户自定义配置
     * 2. 如果没有自定义配置，使用默认权重
     * 
     * @param customerId 客户ID
     * @param timestamp 攻击时间戳
     * @return 时间权重 (0.5-2.0)
     */
    public double calculateEnhancedTimeWeight(String customerId, Instant timestamp) {
        if (customerId == null || customerId.isEmpty()) {
            logger.debug("No customerId provided, using default time weight");
            return calculateTimeWeight(timestamp);
        }
        
        LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
        int hour = time.getHour();
        
        double customWeight = customerTimeWeightService.getTimeWeight(customerId, timestamp);
        
        // 如果自定义权重与默认权重不同，说明使用了自定义配置
        double defaultWeight = calculateTimeWeight(timestamp);
        if (Math.abs(customWeight - defaultWeight) > 0.001) {
            logger.info("🎯 Using CUSTOM time weight: customerId={}, hour={}, weight={}, default={}",
                        customerId, hour, customWeight, defaultWeight);
        } else {
            logger.info("📊 Using DEFAULT time weight: customerId={}, hour={}, weight={}",
                        customerId, hour, customWeight);
        }
        
        return customWeight;
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
     * 判定威胁等级 (基于标准化评分 0-100)
     * 
     * @param threatScore 标准化后的威胁评分 (1-100)
     * @return 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
     */
    public String determineThreatLevel(double threatScore) {
        if (threatScore >= 80) {
            return "CRITICAL";  // 严重威胁 (80-100)
        } else if (threatScore >= 60) {
            return "HIGH";      // 高危威胁 (60-79)
        } else if (threatScore >= 40) {
            return "MEDIUM";    // 中危威胁 (40-59)
        } else if (threatScore >= 20) {
            return "LOW";       // 低危威胁 (20-39)
        } else {
            return "INFO";      // 信息级别 (1-19)
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
    
    /**
     * 计算攻击速率权重 (V5.1新增 - 爆发性检测)
     * 
     * <p>基于攻击速率 (次/秒) 调整权重:
     * - 极高速率 (>10次/秒): 权重 2.5x → DDoS级别爆发
     * - 高速率 (1-10次/秒): 权重 1.5-2.0x → 爆发性攻击
     * - 中等速率 (0.1-1次/秒): 权重 1.0-1.5x → 标准扫描
     * - 低速率 (0.01-0.1次/秒): 权重 0.7-1.0x → 慢速扫描
     * - 极低速率 (<0.01次/秒): 权重 0.5-0.7x → 超慢速扫描
     * 
     * <p>公式: attackRate = attackCount / timeWindowSeconds
     * 
     * <p>案例:
     * - 30次/30秒 = 1次/秒 → 权重 1.8x (爆发性)
     * - 30次/300秒 = 0.1次/秒 → 权重 1.0x (标准)
     * - 30次/900秒 = 0.033次/秒 → 权重 0.8x (慢速)
     * - 30次/3600秒 = 0.008次/秒 → 权重 0.6x (超慢速)
     * 
     * @param attackCount 攻击次数
     * @param timeWindowSeconds 时间窗口 (秒), null或<=0时使用默认值300秒
     * @return 攻击速率权重 (0.5-2.5)
     */
    public double calculateAttackRateWeight(int attackCount, Integer timeWindowSeconds) {
        // 默认时间窗口: 300秒 (5分钟)
        int windowSeconds = (timeWindowSeconds != null && timeWindowSeconds > 0) 
                           ? timeWindowSeconds : 300;
        
        // 计算攻击速率 (次/秒)
        double attackRate = (double) attackCount / windowSeconds;
        
        double weight;
        
        if (attackRate >= 10.0) {
            // 极高速率: >10次/秒 → DDoS级别
            weight = 2.5;
            logger.info("⚡ BURST ATTACK detected: rate={}/s, window={}s, count={}, weight={}",
                       String.format("%.2f", attackRate), windowSeconds, attackCount, weight);
        } else if (attackRate >= 1.0) {
            // 高速率: 1-10次/秒 → 爆发性攻击
            // 线性映射: 1次/秒=1.5, 10次/秒=2.0
            weight = 1.5 + (attackRate - 1.0) / 9.0 * 0.5;
            logger.info("🔥 High-rate attack: rate={}/s, window={}s, count={}, weight={}",
                       String.format("%.2f", attackRate), windowSeconds, attackCount, weight);
        } else if (attackRate >= 0.1) {
            // 中等速率: 0.1-1次/秒 → 标准扫描
            // 线性映射: 0.1次/秒=1.0, 1次/秒=1.5
            weight = 1.0 + (attackRate - 0.1) / 0.9 * 0.5;
            logger.debug("📊 Medium-rate attack: rate={}/s, window={}s, count={}, weight={}",
                        String.format("%.3f", attackRate), windowSeconds, attackCount, weight);
        } else if (attackRate >= 0.01) {
            // 低速率: 0.01-0.1次/秒 → 慢速扫描
            // 线性映射: 0.01次/秒=0.7, 0.1次/秒=1.0
            weight = 0.7 + (attackRate - 0.01) / 0.09 * 0.3;
            logger.debug("🐌 Low-rate attack: rate={}/s, window={}s, count={}, weight={}",
                        String.format("%.4f", attackRate), windowSeconds, attackCount, weight);
        } else {
            // 极低速率: <0.01次/秒 → 超慢速扫描
            // 线性映射: 0次/秒=0.5, 0.01次/秒=0.7
            weight = Math.max(0.5, 0.5 + attackRate / 0.01 * 0.2);
            logger.debug("🦥 Very-low-rate attack: rate={}/s, window={}s, count={}, weight={}",
                        String.format("%.5f", attackRate), windowSeconds, attackCount, weight);
        }
        
        return weight;
    }
}
