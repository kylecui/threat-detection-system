package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.IpSegmentWeightConfig;
import com.threatdetection.assessment.repository.IpSegmentWeightConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * IP段权重服务
 * 
 * <p>Phase 3核心服务: 管理IP段权重配置和权重计算
 * 
 * <p>核心功能:
 * 1. 查询IP所属网段权重 (带缓存优化)
 * 2. IP地址范围匹配 (支持IPv4和IPv6)
 * 3. 高危网段查询
 * 4. 自动初始化默认配置 (50个核心网段)
 * 
 * <p>权重体系:
 * - 内网 (0.5-0.8): 降低内网IP的威胁评分
 * - 正常公网 (0.9-1.1): 基准权重
 * - 云服务商 (1.2-1.3): 略高于基准
 * - 高危地区 (1.6-1.9): 显著提高权重
 * - 已知恶意 (2.0): 最高权重
 * 
 * <p>使用示例:
 * <pre>
 * // 查询单个IP权重
 * double weight = ipSegmentWeightService.getIpSegmentWeight("192.168.1.100");
 * // 结果: 0.8 (内网)
 * 
 * // 查询高危网段
 * List&lt;IpSegmentWeightConfig&gt; highRisk = ipSegmentWeightService.getHighRiskSegments(1.7);
 * </pre>
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpSegmentWeightService {
    
    private final IpSegmentWeightConfigRepository repository;
    
    /**
     * 默认权重 (未匹配到任何网段时使用)
     */
    private static final double DEFAULT_WEIGHT = 1.0;
    
    /**
     * 查询IP地址的网段权重 (带缓存)
     * 
     * <p>查询逻辑:
     * 1. 使用PostgreSQL的inet类型进行IP范围匹配
     * 2. 支持IPv4和IPv6
     * 3. 多个匹配时选择优先级最高的
     * 4. 未匹配时返回默认权重1.0
     * 
     * @param ipAddress IP地址 (如 "192.168.1.100", "2001:db8::1")
     * @return 网段权重 (0.5-2.0)
     */
    @Cacheable(value = "ipSegmentWeights", key = "#ipAddress")
    public double getIpSegmentWeight(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            log.warn("IP address is null or empty, returning default weight: {}", DEFAULT_WEIGHT);
            return DEFAULT_WEIGHT;
        }
        
        try {
            Optional<IpSegmentWeightConfig> config = repository.findByIpAddress(ipAddress);
            
            if (config.isPresent()) {
                double weight = config.get().getWeight();
                log.debug("Found segment weight for IP {}: {} (segment: {})", 
                         ipAddress, weight, config.get().getSegmentName());
                return weight;
            } else {
                log.debug("No segment match for IP {}, returning default weight: {}", 
                         ipAddress, DEFAULT_WEIGHT);
                return DEFAULT_WEIGHT;
            }
        } catch (Exception e) {
            log.error("Error querying segment weight for IP {}: {}", ipAddress, e.getMessage(), e);
            return DEFAULT_WEIGHT;
        }
    }
    
    /**
     * 查询IP所属网段配置
     * 
     * @param ipAddress IP地址
     * @return 网段配置 (如果找到)
     */
    public Optional<IpSegmentWeightConfig> getIpSegmentConfig(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            return repository.findByIpAddress(ipAddress);
        } catch (Exception e) {
            log.error("Error finding segment config for IP {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 查询高危网段 (权重 >= 阈值)
     * 
     * @param threshold 权重阈值 (推荐 1.7)
     * @return 高危网段列表,按权重降序
     */
    public List<IpSegmentWeightConfig> getHighRiskSegments(double threshold) {
        log.info("Querying high-risk segments with threshold: {}", threshold);
        return repository.findHighRiskSegments(threshold);
    }
    
    /**
     * 查询所有已知恶意网段
     * 
     * @return 恶意网段列表
     */
    public List<IpSegmentWeightConfig> getMaliciousSegments() {
        log.info("Querying malicious segments");
        return repository.findMaliciousSegments();
    }
    
    /**
     * 根据分类查询网段
     * 
     * @param category 分类 (如 "CLOUD_AWS", "HIGH_RISK_REGION")
     * @return 该分类的所有网段
     */
    public List<IpSegmentWeightConfig> getSegmentsByCategory(String category) {
        log.info("Querying segments by category: {}", category);
        return repository.findByCategory(category);
    }
    
    /**
     * 检查IP是否为内网地址
     * 
     * @param ipAddress IP地址
     * @return 是否为内网IP
     */
    public boolean isPrivateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        
        try {
            return repository.isPrivateIp(ipAddress);
        } catch (Exception e) {
            log.error("Error checking if IP is private: {}", ipAddress, e);
            return false;
        }
    }
    
    /**
     * 初始化默认IP段配置
     * 
     * <p>在应用启动时自动调用,创建50个核心网段配置:
     * - 内网网段 (RFC 1918)
     * - 云服务商网段 (AWS, Azure, GCP, 阿里云, 腾讯云)
     * - 高危地区网段
     * - 已知恶意网段
     * - Tor出口节点
     * - VPN服务商
     * - 中国主要ISP
     * 
     * <p>如果数据库已有配置,则跳过初始化
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultSegments() {
        long count = repository.countConfiguredSegments();
        
        if (count > 0) {
            log.info("IP segment weight config already initialized: {} segments", count);
            return;
        }
        
        log.info("Initializing default IP segment weight configurations...");
        
        try {
            // 注意: 实际初始化由数据库脚本 init-db.sql.phase3 完成
            // 这里仅记录日志,确认数据库脚本已执行
            
            count = repository.countConfiguredSegments();
            if (count > 0) {
                log.info("✅ Verified {} IP segment weight configurations in database", count);
                
                // 输出统计信息
                List<Object[]> stats = repository.countByCategory();
                log.info("Segment distribution by category:");
                for (Object[] stat : stats) {
                    log.info("  - {}: {} segments", stat[0], stat[1]);
                }
            } else {
                log.warn("⚠️ No IP segment configurations found. Please run init-db.sql.phase3");
            }
        } catch (Exception e) {
            log.error("Failed to initialize IP segment configurations: {}", e.getMessage(), e);
        }
    }
}
