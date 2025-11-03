package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.AttackSourceWeight;
import com.threatdetection.assessment.model.HoneypotSensitivityWeight;
import com.threatdetection.assessment.repository.AttackSourceWeightRepository;
import com.threatdetection.assessment.repository.HoneypotSensitivityWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * IP网段权重服务 V4.0 - 双维度权重设计
 * 
 * <p>核心改进: 从单一维度（攻击源权重）升级到双维度（攻击源权重 × 蜜罐敏感度权重）
 * 
 * <p>使用场景:
 * <pre>
 * // 场景1: IoT设备扫描管理区蜜罐
 * double sourceWeight = service.getAttackSourceWeight("customer-001", "192.168.50.10");  // 0.9 (IoT设备)
 * double honeypotWeight = service.getHoneypotSensitivityWeight("customer-001", "10.0.100.50");  // 3.5 (管理区蜜罐)
 * double combinedWeight = sourceWeight * honeypotWeight;  // 3.15 → CRITICAL威胁！
 * 
 * // 场景2: 办公区设备扫描办公区蜜罐
 * double sourceWeight = service.getAttackSourceWeight("customer-001", "192.168.10.100");  // 1.0
 * double honeypotWeight = service.getHoneypotSensitivityWeight("customer-001", "192.168.10.50");  // 1.3
 * double combinedWeight = sourceWeight * honeypotWeight;  // 1.3 → MEDIUM威胁
 * </pre>
 * 
 * <p>权重说明:
 * - attackSourceWeight (0.5-3.0): 评估失陷设备被攻陷的后果
 * - honeypotSensitivityWeight (1.0-3.5): 评估攻击者访问诱饵的意图
 * - combinedWeight = attackSourceWeight × honeypotSensitivityWeight
 * 
 * @author ThreatDetection Team
 * @version 4.0
 * @since 2025-10-24
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpSegmentWeightServiceV4 {
    
    private final AttackSourceWeightRepository attackSourceWeightRepository;
    private final HoneypotSensitivityWeightRepository honeypotSensitivityWeightRepository;
    
    /**
     * 默认攻击源权重（未匹配时使用）
     */
    private static final double DEFAULT_ATTACK_SOURCE_WEIGHT = 1.0;
    
    /**
     * 默认蜜罐敏感度权重（未匹配时使用）
     */
    private static final double DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT = 1.0;
    
    /**
     * 获取攻击源权重（带缓存，支持fallback到default配置）
     * 
     * <p>评估问题: 这个设备被攻陷后，能造成多大危害？
     * 
     * <p>查询策略（Fallback机制）:
     * 1. 优先查询客户专属配置 (customerId)
     * 2. 如果没有匹配，回退到默认配置 (customer_id='default')
     * 3. 如果仍然没有匹配，返回 1.0
     * 
     * <p>权重范围:
     * - 3.0: 数据库服务器/管理网段 (极严重)
     * - 2.5: 应用服务器 (严重)
     * - 2.0: Web服务器/DMZ (高危)
     * - 1.0: 普通办公区 (基准)
     * - 0.9: IoT设备 (权限有限)
     * - 0.6: 访客网络 (物理隔离)
     * 
     * @param customerId 客户ID
     * @param attackIp 攻击源IP（失陷设备IP）
     * @return 攻击源权重
     */
    @Cacheable(value = "attackSourceWeights", key = "#customerId + ':' + #attackIp")
    public double getAttackSourceWeight(String customerId, String attackIp) {
        if (customerId == null || attackIp == null || attackIp.isEmpty()) {
            log.warn("Invalid parameters: customerId={}, attackIp={}, returning default weight: {}",
                     customerId, attackIp, DEFAULT_ATTACK_SOURCE_WEIGHT);
            return DEFAULT_ATTACK_SOURCE_WEIGHT;
        }

        try {
            // 1. 先查询客户专属配置
            Optional<AttackSourceWeight> config = attackSourceWeightRepository
                .findByCustomerIdAndIpAddress(customerId, attackIp);

            if (config.isPresent()) {
                double weight = config.get().getWeight().doubleValue();
                log.info("Found customer-specific attack source weight for customerId={}, attackIp={}: {} (segment: {}, type: {})",
                         customerId, attackIp, weight,
                         config.get().getSegmentName(), config.get().getSegmentType());
                return weight;
            }

            // 2. 如果客户专属配置不存在，回退到 default 配置
            if (!"default".equals(customerId)) {
                log.debug("No customer-specific config for customerId={}, attackIp={}, trying fallback to 'default'",
                         customerId, attackIp);

                Optional<AttackSourceWeight> defaultConfig = attackSourceWeightRepository
                    .findByCustomerIdAndIpAddress("default", attackIp);

                if (defaultConfig.isPresent()) {
                    double weight = defaultConfig.get().getWeight().doubleValue();
                    log.info("Using fallback attack source weight from 'default' for customerId={}, attackIp={}: {} (segment: {}, type: {})",
                             customerId, attackIp, weight,
                             defaultConfig.get().getSegmentName(), defaultConfig.get().getSegmentType());
                    return weight;
                }
            }

            // 3. 如果 default 也没有匹配，返回默认权重
            log.debug("No attack source weight match (customer-specific or default) for customerId={}, attackIp={}, returning default: {}",
                     customerId, attackIp, DEFAULT_ATTACK_SOURCE_WEIGHT);
            return DEFAULT_ATTACK_SOURCE_WEIGHT;

        } catch (Exception e) {
            log.error("Error querying attack source weight for customerId={}, attackIp={}: {}",
                     customerId, attackIp, e.getMessage(), e);
            return DEFAULT_ATTACK_SOURCE_WEIGHT;
        }
    }
    
    /**
     * 获取蜜罐敏感度权重（带缓存，支持fallback到default配置）
     * 
     * <p>评估问题: 攻击者尝试访问这个诱饵，意图有多严重？
     * 
     * <p>查询策略（Fallback机制）:
     * 1. 优先查询客户专属配置 (customerId)
     * 2. 如果没有匹配，回退到默认配置 (customer_id='default')
     * 3. 如果仍然没有匹配，返回 1.0
     * 
     * <p>权重范围:
     * - 3.5: 管理区/数据库蜜罐 (尝试控制全网/窃取数据)
     * - 3.0: 核心业务蜜罐 (尝试破坏业务)
     * - 2.5: 文件服务器蜜罐 (勒索软件目标)
     * - 2.0: Web服务器蜜罐 (跳板攻击)
     * - 1.5: 高管办公区蜜罐 (高价值目标)
     * - 1.3: 办公区蜜罐 (横向移动探测)
     * - 1.0: 低价值蜜罐 (基准)
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP（response_ip）
     * @return 蜜罐敏感度权重
     */
    @Cacheable(value = "honeypotSensitivityWeights", key = "#customerId + ':' + #honeypotIp")
    public double getHoneypotSensitivityWeight(String customerId, String honeypotIp) {
        if (customerId == null || honeypotIp == null || honeypotIp.isEmpty()) {
            log.warn("Invalid parameters: customerId={}, honeypotIp={}, returning default weight: {}", 
                     customerId, honeypotIp, DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT);
            return DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT;
        }
        
        try {
            // 1. 先查询客户专属配置
            Optional<HoneypotSensitivityWeight> config = honeypotSensitivityWeightRepository
                .findByCustomerIdAndHoneypotIp(customerId, honeypotIp);
            
            if (config.isPresent()) {
                double weight = config.get().getWeight().doubleValue();
                log.info("Found customer-specific honeypot sensitivity weight for customerId={}, honeypotIp={}: {} (honeypot: {}, zone: {}, intent: {})", 
                         customerId, honeypotIp, weight,
                         config.get().getHoneypotName(), config.get().getDeploymentZone(), config.get().getAttackIntent());
                return weight;
            }
            
            // 2. 如果客户专属配置不存在，回退到 default 配置
            if (!"default".equals(customerId)) {
                log.debug("No customer-specific honeypot config for customerId={}, honeypotIp={}, trying fallback to 'default'", 
                         customerId, honeypotIp);
                
                Optional<HoneypotSensitivityWeight> defaultConfig = honeypotSensitivityWeightRepository
                    .findByCustomerIdAndHoneypotIp("default", honeypotIp);
                
                if (defaultConfig.isPresent()) {
                    double weight = defaultConfig.get().getWeight().doubleValue();
                    log.info("Using fallback honeypot sensitivity weight from 'default' for customerId={}, honeypotIp={}: {} (honeypot: {}, zone: {}, intent: {})", 
                             customerId, honeypotIp, weight,
                             defaultConfig.get().getHoneypotName(), defaultConfig.get().getDeploymentZone(), defaultConfig.get().getAttackIntent());
                    return weight;
                }
            }
            
            // 3. 如果 default 也没有匹配，返回默认权重
            log.debug("No honeypot sensitivity weight match (customer-specific or default) for customerId={}, honeypotIp={}, returning default: {}", 
                     customerId, honeypotIp, DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT);
            return DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT;
            
        } catch (Exception e) {
            log.error("Error querying honeypot sensitivity weight for customerId={}, honeypotIp={}: {}", 
                     customerId, honeypotIp, e.getMessage(), e);
            return DEFAULT_HONEYPOT_SENSITIVITY_WEIGHT;
        }
    }
    
    /**
     * 获取综合网段权重（双维度）
     * 
     * <p>综合权重 = attackSourceWeight × honeypotSensitivityWeight
     * 
     * <p>示例:
     * - IoT设备(0.9) × 管理区蜜罐(3.5) = 3.15 (CRITICAL)
     * - 数据库服务器(3.0) × 办公区蜜罐(1.3) = 3.9 (CRITICAL)
     * - 办公区(1.0) × 办公区蜜罐(1.3) = 1.3 (MEDIUM)
     * - 访客网络(0.6) × 数据库蜜罐(3.5) = 2.1 (HIGH - 隔离失效!)
     * 
     * @param customerId 客户ID
     * @param attackIp 攻击源IP
     * @param honeypotIp 蜜罐IP
     * @return 综合权重
     */
    public double getCombinedSegmentWeight(String customerId, String attackIp, String honeypotIp) {
        double attackSourceWeight = getAttackSourceWeight(customerId, attackIp);
        double honeypotSensitivityWeight = getHoneypotSensitivityWeight(customerId, honeypotIp);
        double combinedWeight = attackSourceWeight * honeypotSensitivityWeight;
        
        log.info("Combined segment weight for customerId={}, attackIp={}, honeypotIp={}: " +
                "attackSourceWeight={}, honeypotSensitivityWeight={}, combinedWeight={}",
                customerId, attackIp, honeypotIp,
                attackSourceWeight, honeypotSensitivityWeight, combinedWeight);
        
        return combinedWeight;
    }
    
    /**
     * 获取攻击源权重详细信息
     * 
     * @param customerId 客户ID
     * @param attackIp 攻击源IP
     * @return 权重配置详情
     */
    public Optional<AttackSourceWeight> getAttackSourceWeightConfig(String customerId, String attackIp) {
        if (customerId == null || attackIp == null || attackIp.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            return attackSourceWeightRepository.findByCustomerIdAndIpAddress(customerId, attackIp);
        } catch (Exception e) {
            log.error("Error finding attack source weight config for customerId={}, attackIp={}: {}", 
                     customerId, attackIp, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 获取蜜罐敏感度权重详细信息
     * 
     * @param customerId 客户ID
     * @param honeypotIp 蜜罐IP
     * @return 权重配置详情
     */
    public Optional<HoneypotSensitivityWeight> getHoneypotSensitivityWeightConfig(String customerId, String honeypotIp) {
        if (customerId == null || honeypotIp == null || honeypotIp.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            return honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(customerId, honeypotIp);
        } catch (Exception e) {
            log.error("Error finding honeypot sensitivity weight config for customerId={}, honeypotIp={}: {}", 
                     customerId, honeypotIp, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 统计客户的攻击源权重配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    public long countAttackSourceWeights(String customerId) {
        return attackSourceWeightRepository.countByCustomerId(customerId);
    }
    
    /**
     * 统计客户的蜜罐敏感度配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    public long countHoneypotSensitivityWeights(String customerId) {
        return honeypotSensitivityWeightRepository.countByCustomerId(customerId);
    }
}
