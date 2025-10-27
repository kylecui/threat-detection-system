package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.CustomerPortWeight;
import com.threatdetection.assessment.model.PortRiskConfig;
import com.threatdetection.assessment.repository.CustomerPortWeightRepository;
import com.threatdetection.assessment.repository.PortRiskConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户端口权重服务
 * 
 * <p>提供端口权重的管理和查询功能，支持多租户隔离
 * <p>核心功能:
 * <ul>
 *   <li>多租户端口权重配置管理</li>
 *   <li>优先级匹配: 客户自定义 > 全局默认</li>
 *   <li>混合权重策略: max(configWeight, diversityWeight)</li>
 *   <li>批量导入和更新</li>
 *   <li>缓存优化查询性能</li>
 * </ul>
 * 
 * @author Security Team
 * @version 4.0
 */
@Service
public class CustomerPortWeightService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerPortWeightService.class);

    private final CustomerPortWeightRepository customerPortWeightRepository;
    private final PortRiskConfigRepository portRiskConfigRepository;

    public CustomerPortWeightService(
            CustomerPortWeightRepository customerPortWeightRepository,
            PortRiskConfigRepository portRiskConfigRepository) {
        this.customerPortWeightRepository = customerPortWeightRepository;
        this.portRiskConfigRepository = portRiskConfigRepository;
    }

    /**
     * 获取指定客户的端口权重 (带缓存)
     * <p>优先级: 客户自定义 > 全局默认 > 默认值(1.0)
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 端口权重值
     */
    @Cacheable(value = "portWeights", key = "#customerId + ':' + #portNumber")
    public double getPortWeight(String customerId, int portNumber) {
        // 1. 优先查询客户自定义配置
        Optional<CustomerPortWeight> customerConfig = 
            customerPortWeightRepository.findByCustomerIdAndPortNumberAndEnabledTrue(
                customerId, portNumber);
        
        if (customerConfig.isPresent()) {
            logger.debug("Found custom port weight for customer={}, port={}: weight={}", 
                customerId, portNumber, customerConfig.get().getWeight());
            return customerConfig.get().getWeight();
        }

        // 2. 查询全局默认配置
        Optional<PortRiskConfig> globalConfig = 
            portRiskConfigRepository.findByPortNumber(portNumber);
        
        if (globalConfig.isPresent()) {
            logger.debug("Using global port weight for customer={}, port={}: weight={}", 
                customerId, portNumber, globalConfig.get().getRiskWeight());
            return globalConfig.get().getRiskWeight();
        }

        // 3. 返回默认权重
        logger.debug("No port weight config found for customer={}, port={}, using default 1.0", 
            customerId, portNumber);
        return 1.0;
    }

    /**
     * 批量获取端口权重
     * 
     * @param customerId 客户ID
     * @param portNumbers 端口号列表
     * @return 端口号到权重的映射
     */
    @Cacheable(value = "portWeightsBatch", key = "#customerId + ':' + #portNumbers.hashCode()")
    public Map<Integer, Double> getPortWeightsBatch(String customerId, List<Integer> portNumbers) {
        if (portNumbers == null || portNumbers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, Double> weightMap = new HashMap<>();

        // 1. 批量查询客户自定义配置
        List<CustomerPortWeight> customerConfigs = 
            customerPortWeightRepository.findByCustomerIdAndPortNumberInAndEnabledTrue(
                customerId, portNumbers);
        
        Map<Integer, Double> customerWeightMap = customerConfigs.stream()
            .collect(Collectors.toMap(
                CustomerPortWeight::getPortNumber,
                CustomerPortWeight::getWeight
            ));

        // 2. 查找没有客户配置的端口
        List<Integer> portsWithoutCustomConfig = portNumbers.stream()
            .filter(port -> !customerWeightMap.containsKey(port))
            .collect(Collectors.toList());

        // 3. 批量查询全局配置
        Map<Integer, Double> globalWeightMap = new HashMap<>();
        if (!portsWithoutCustomConfig.isEmpty()) {
            List<PortRiskConfig> globalConfigs = 
                portRiskConfigRepository.findByPortNumberIn(portsWithoutCustomConfig);
            
            globalWeightMap = globalConfigs.stream()
                .collect(Collectors.toMap(
                    PortRiskConfig::getPortNumber,
                    PortRiskConfig::getRiskWeight
                ));
        }

        // 4. 合并结果
        for (Integer port : portNumbers) {
            if (customerWeightMap.containsKey(port)) {
                weightMap.put(port, customerWeightMap.get(port));
            } else if (globalWeightMap.containsKey(port)) {
                weightMap.put(port, globalWeightMap.get(port));
            } else {
                weightMap.put(port, 1.0);  // 默认权重
            }
        }

        logger.debug("Batch retrieved {} port weights for customer={}: {} custom, {} global, {} default",
            portNumbers.size(), customerId, customerWeightMap.size(), 
            globalWeightMap.size(), portNumbers.size() - customerWeightMap.size() - globalWeightMap.size());

        return weightMap;
    }

    /**
     * 计算混合端口权重
     * <p>策略: portWeight = max(configWeight, diversityWeight)
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @param diversityWeight 多样性权重 (基于uniquePorts计算)
     * @return 混合权重值
     */
    public double calculateHybridPortWeight(String customerId, int portNumber, double diversityWeight) {
        double configWeight = getPortWeight(customerId, portNumber);
        double hybridWeight = Math.max(configWeight, diversityWeight);
        
        logger.debug("Hybrid port weight for customer={}, port={}: " +
            "config={}, diversity={}, hybrid={}", 
            customerId, portNumber, configWeight, diversityWeight, hybridWeight);
        
        return hybridWeight;
    }

    /**
     * 获取指定客户的所有端口权重配置
     * 
     * @param customerId 客户ID
     * @return 端口权重配置列表
     */
    public List<CustomerPortWeight> getCustomerConfigs(String customerId) {
        return customerPortWeightRepository.findByCustomerIdAndEnabledTrue(customerId);
    }

    /**
     * 获取指定客户的所有配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 端口权重配置列表
     */
    public List<CustomerPortWeight> getAllCustomerConfigs(String customerId) {
        return customerPortWeightRepository.findByCustomerId(customerId);
    }

    /**
     * 创建或更新端口权重配置
     * 
     * @param config 端口权重配置
     * @return 保存后的配置
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public CustomerPortWeight saveConfig(CustomerPortWeight config) {
        logger.info("Saving port weight config: customer={}, port={}, weight={}", 
            config.getCustomerId(), config.getPortNumber(), config.getWeight());
        
        return customerPortWeightRepository.save(config);
    }

    /**
     * 批量导入端口权重配置
     * 
     * @param configs 配置列表
     * @return 保存后的配置列表
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public List<CustomerPortWeight> batchImport(List<CustomerPortWeight> configs) {
        logger.info("Batch importing {} port weight configs", configs.size());
        
        List<CustomerPortWeight> savedConfigs = customerPortWeightRepository.saveAll(configs);
        
        logger.info("Successfully imported {} port weight configs", savedConfigs.size());
        return savedConfigs;
    }

    /**
     * 更新端口权重
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @param weight 新权重值
     * @param updatedBy 更新人
     * @return 更新后的配置
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public CustomerPortWeight updateWeight(String customerId, int portNumber, 
                                          double weight, String updatedBy) {
        Optional<CustomerPortWeight> existingConfig = 
            customerPortWeightRepository.findByCustomerIdAndPortNumber(customerId, portNumber);
        
        CustomerPortWeight config;
        if (existingConfig.isPresent()) {
            config = existingConfig.get();
            config.setWeight(weight);
            config.setUpdatedBy(updatedBy);
        } else {
            // 创建新配置
            config = new CustomerPortWeight();
            config.setCustomerId(customerId);
            config.setPortNumber(portNumber);
            config.setWeight(weight);
            config.setCreatedBy(updatedBy);
            config.setUpdatedBy(updatedBy);
        }
        
        logger.info("Updated port weight: customer={}, port={}, weight={}, updatedBy={}", 
            customerId, portNumber, weight, updatedBy);
        
        return customerPortWeightRepository.save(config);
    }

    /**
     * 删除端口权重配置
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public void deleteConfig(String customerId, int portNumber) {
        logger.info("Deleting port weight config: customer={}, port={}", customerId, portNumber);
        customerPortWeightRepository.deleteByCustomerIdAndPortNumber(customerId, portNumber);
    }

    /**
     * 删除客户的所有配置
     * 
     * @param customerId 客户ID
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public void deleteAllCustomerConfigs(String customerId) {
        logger.info("Deleting all port weight configs for customer={}", customerId);
        customerPortWeightRepository.deleteByCustomerId(customerId);
    }

    /**
     * 启用或禁用端口配置
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @param enabled 启用状态
     * @return 更新后的配置
     */
    @Transactional
    @CacheEvict(value = {"portWeights", "portWeightsBatch"}, allEntries = true)
    public CustomerPortWeight setEnabled(String customerId, int portNumber, boolean enabled) {
        Optional<CustomerPortWeight> configOpt = 
            customerPortWeightRepository.findByCustomerIdAndPortNumber(customerId, portNumber);
        
        if (configOpt.isEmpty()) {
            throw new IllegalArgumentException(
                "Port weight config not found: customer=" + customerId + ", port=" + portNumber);
        }
        
        CustomerPortWeight config = configOpt.get();
        config.setEnabled(enabled);
        
        logger.info("Set port weight config enabled={}: customer={}, port={}", 
            enabled, customerId, portNumber);
        
        return customerPortWeightRepository.save(config);
    }

    /**
     * 获取客户的统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息
     */
    public Map<String, Object> getStatistics(String customerId) {
        long totalCount = customerPortWeightRepository.countByCustomerId(customerId);
        long enabledCount = customerPortWeightRepository.countByCustomerIdAndEnabledTrue(customerId);
        
        Object[] stats = customerPortWeightRepository.getStatistics(customerId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("enabledCount", enabledCount);
        result.put("disabledCount", totalCount - enabledCount);
        
        if (stats != null && stats.length >= 4) {
            result.put("avgWeight", stats[1]);
            result.put("maxWeight", stats[2]);
            result.put("minWeight", stats[3]);
        }
        
        return result;
    }

    /**
     * 获取高优先级配置
     * 
     * @param customerId 客户ID
     * @param minPriority 最小优先级
     * @return 高优先级配置列表
     */
    public List<CustomerPortWeight> getHighPriorityConfigs(String customerId, int minPriority) {
        return customerPortWeightRepository.findHighPriorityConfigs(customerId, minPriority);
    }

    /**
     * 获取高权重端口
     * 
     * @param customerId 客户ID
     * @param minWeight 最小权重
     * @return 高权重端口列表
     */
    public List<CustomerPortWeight> getHighWeightPorts(String customerId, double minWeight) {
        return customerPortWeightRepository.findHighWeightPorts(customerId, minWeight);
    }

    /**
     * 按风险等级查询
     * 
     * @param customerId 客户ID
     * @param riskLevel 风险等级
     * @return 端口权重配置列表
     */
    public List<CustomerPortWeight> getByRiskLevel(String customerId, String riskLevel) {
        return customerPortWeightRepository.findByCustomerIdAndRiskLevelAndEnabledTrue(
            customerId, riskLevel);
    }

    /**
     * 检查配置是否存在
     * 
     * @param customerId 客户ID
     * @param portNumber 端口号
     * @return 是否存在
     */
    public boolean configExists(String customerId, int portNumber) {
        return customerPortWeightRepository.existsByCustomerIdAndPortNumber(customerId, portNumber);
    }
}
