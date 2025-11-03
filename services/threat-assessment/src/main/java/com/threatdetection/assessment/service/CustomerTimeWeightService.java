package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.CustomerTimeWeight;
import com.threatdetection.assessment.repository.CustomerTimeWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户时间段权重配置服务
 *
 * <p>提供时间权重配置的业务逻辑和缓存管理
 * <p>混合策略: 客户自定义权重 + 全局默认权重
 *
 * @author Security Team
 * @version 5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerTimeWeightService {

    private final CustomerTimeWeightRepository repository;

    // 默认时间权重配置 (与原系统对齐)
    private static final Map<Integer, Double> DEFAULT_TIME_WEIGHTS = Map.ofEntries(
        Map.entry(0, 1.2), Map.entry(1, 1.2), Map.entry(2, 1.2), Map.entry(3, 1.2), Map.entry(4, 1.2), Map.entry(5, 1.2),  // 深夜 00:00-06:00
        Map.entry(6, 1.1), Map.entry(7, 1.1), Map.entry(8, 1.1),                              // 早晨 06:00-09:00
        Map.entry(9, 1.0), Map.entry(10, 1.0), Map.entry(11, 1.0), Map.entry(12, 1.0), Map.entry(13, 1.0), Map.entry(14, 1.0), Map.entry(15, 1.0), Map.entry(16, 1.0),  // 工作时间 09:00-17:00
        Map.entry(17, 0.9), Map.entry(18, 0.9), Map.entry(19, 0.9), Map.entry(20, 0.9), Map.entry(21, 0.9),        // 傍晚 17:00-22:00
        Map.entry(22, 0.8), Map.entry(23, 0.8)                                      // 夜间 22:00-24:00
    );

    /**
     * 获取客户的时间权重
     *
     * <p>混合策略: 优先使用客户自定义配置，fallback到默认权重
     *
     * @param customerId 客户ID
     * @param timestamp 时间戳
     * @return 时间权重 (0.5-2.0)
     */
    @Cacheable(value = "timeWeights", key = "#customerId + '_' + T(java.time.LocalTime).ofInstant(#timestamp, T(java.time.ZoneId).systemDefault()).getHour()")
    public double getTimeWeight(String customerId, java.time.Instant timestamp) {
        int hour = LocalTime.ofInstant(timestamp, java.time.ZoneId.systemDefault()).getHour();

        // 查找客户自定义配置
        List<CustomerTimeWeight> customWeights = repository.findByCustomerIdAndHour(customerId, hour);

        if (!CollectionUtils.isEmpty(customWeights)) {
            // 使用优先级最高的自定义配置
            CustomerTimeWeight weight = customWeights.get(0);
            log.info("✅ Using CUSTOM time weight for customerId={}, hour={}, weight={}, range={}-{}",
                     customerId, hour, weight.getWeight(), weight.getStartHour(), weight.getEndHour());
            return weight.getWeight();
        }

        // 如果客户没有自定义配置，回退到 default 客户的配置
        if (!"default".equals(customerId)) {
            List<CustomerTimeWeight> defaultWeights = repository.findByCustomerIdAndHour("default", hour);
            if (!CollectionUtils.isEmpty(defaultWeights)) {
                CustomerTimeWeight defaultWeight = defaultWeights.get(0);
                log.info("🔄 Using DEFAULT customer time weight for customerId={}, hour={}, weight={}, range={}-{}",
                         customerId, hour, defaultWeight.getWeight(), defaultWeight.getStartHour(), defaultWeight.getEndHour());
                return defaultWeight.getWeight();
            }
        }

        // 如果连 default 客户都没有配置，使用硬编码默认值
        double fallbackWeight = DEFAULT_TIME_WEIGHTS.getOrDefault(hour, 1.0);
        log.info("⚠️ Using FALLBACK time weight for customerId={}, hour={}, weight={}",
                 customerId, hour, fallbackWeight);
        return fallbackWeight;
    }

    /**
     * 获取客户的所有时间权重配置
     */
    public List<CustomerTimeWeight> getAllByCustomerId(String customerId) {
        return repository.findByCustomerIdOrderByPriorityDesc(customerId);
    }

    /**
     * 获取客户启用的时间权重配置
     */
    public List<CustomerTimeWeight> getEnabledByCustomerId(String customerId) {
        return repository.findByCustomerIdAndEnabledTrueOrderByPriorityDesc(customerId);
    }

    /**
     * 创建时间权重配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public CustomerTimeWeight create(CustomerTimeWeight weight) {
        validateTimeRange(weight);
        checkDuplicateRange(weight);

        weight.setCreatedBy("system");
        weight.setUpdatedBy("system");

        CustomerTimeWeight saved = repository.save(weight);
        log.info("Created time weight config: customerId={}, id={}, range={}:{}-{}:{}",
                saved.getCustomerId(), saved.getId(),
                saved.getStartHour(), saved.getEndHour(),
                saved.getWeight(), saved.getTimeRangeName());
        return saved;
    }

    /**
     * 批量创建时间权重配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public List<CustomerTimeWeight> createBatch(List<CustomerTimeWeight> weights) {
        weights.forEach(weight -> {
            validateTimeRange(weight);
            weight.setCreatedBy("system");
            weight.setUpdatedBy("system");
        });

        List<CustomerTimeWeight> saved = repository.saveAll(weights);
        log.info("Created {} time weight configs for customerId={}",
                saved.size(), saved.get(0).getCustomerId());
        return saved;
    }

    /**
     * 更新时间权重配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public CustomerTimeWeight update(Long id, CustomerTimeWeight update) {
        CustomerTimeWeight existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Time weight config not found: " + id));

        validateTimeRange(update);

        // 检查时间段是否与其他配置冲突
        if (!existing.getStartHour().equals(update.getStartHour()) ||
            !existing.getEndHour().equals(update.getEndHour())) {
            checkDuplicateRange(update, existing.getId());
        }

        existing.setStartHour(update.getStartHour());
        existing.setEndHour(update.getEndHour());
        existing.setTimeRangeName(update.getTimeRangeName());
        existing.setWeight(update.getWeight());
        existing.setRiskDescription(update.getRiskDescription());
        existing.setAttackIntent(update.getAttackIntent());
        existing.setDescription(update.getDescription());
        existing.setPriority(update.getPriority());
        existing.setEnabled(update.getEnabled());
        existing.setUpdatedBy("system");

        CustomerTimeWeight saved = repository.save(existing);
        log.info("Updated time weight config: id={}, customerId={}, weight={}",
                saved.getId(), saved.getCustomerId(), saved.getWeight());
        return saved;
    }

    /**
     * 删除时间权重配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public void delete(Long id) {
        CustomerTimeWeight weight = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Time weight config not found: " + id));

        repository.deleteById(id);
        log.info("Deleted time weight config: id={}, customerId={}",
                id, weight.getCustomerId());
    }

    /**
     * 批量删除客户的所有配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public void deleteByCustomerId(String customerId) {
        long count = repository.countByCustomerId(customerId);
        repository.deleteByCustomerId(customerId);
        log.info("Deleted {} time weight configs for customerId={}", count, customerId);
    }

    /**
     * 启用/禁用配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public CustomerTimeWeight toggleEnabled(Long id, boolean enabled) {
        CustomerTimeWeight weight = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Time weight config not found: " + id));

        weight.setEnabled(enabled);
        weight.setUpdatedBy("system");

        CustomerTimeWeight saved = repository.save(weight);
        log.info("{} time weight config: id={}, customerId={}, enabled={}",
                enabled ? "Enabled" : "Disabled", id, weight.getCustomerId(), enabled);
        return saved;
    }

    /**
     * 获取配置统计信息
     */
    public Map<String, Object> getStatistics(String customerId) {
        List<CustomerTimeWeight> configs = getAllByCustomerId(customerId);
        long enabledCount = configs.stream().filter(CustomerTimeWeight::getEnabled).count();
        long totalCount = configs.size();

        return Map.of(
            "customerId", customerId,
            "totalConfigs", totalCount,
            "enabledConfigs", enabledCount,
            "disabledConfigs", totalCount - enabledCount,
            "hasCustomConfig", totalCount > 0
        );
    }

    /**
     * 初始化客户的默认时间权重配置
     */
    @Transactional
    @CacheEvict(value = "timeWeights", allEntries = true)
    public void initializeDefaultWeights(String customerId) {
        if (repository.existsByCustomerIdAndEnabledTrue(customerId)) {
            log.info("Customer {} already has time weight configs, skipping initialization", customerId);
            return;
        }

        List<CustomerTimeWeight> defaults = createDefaultWeights(customerId);
        repository.saveAll(defaults);

        log.info("Initialized {} default time weight configs for customerId={}",
                defaults.size(), customerId);
    }

    /**
     * 创建默认时间权重配置
     */
    private List<CustomerTimeWeight> createDefaultWeights(String customerId) {
        return List.of(
            new CustomerTimeWeight(customerId, 0, 6, "深夜时段", 1.2, "高风险"),
            new CustomerTimeWeight(customerId, 6, 9, "早晨时段", 1.1, "中等风险"),
            new CustomerTimeWeight(customerId, 9, 17, "工作时段", 1.0, "正常风险"),
            new CustomerTimeWeight(customerId, 17, 22, "傍晚时段", 0.9, "低风险"),
            new CustomerTimeWeight(customerId, 22, 24, "夜间时段", 0.8, "低风险")
        );
    }

    /**
     * 验证时间段范围
     */
    private void validateTimeRange(CustomerTimeWeight weight) {
        if (weight.getStartHour() == null || weight.getEndHour() == null) {
            throw new IllegalArgumentException("Start hour and end hour cannot be null");
        }
        if (weight.getStartHour() < 0 || weight.getStartHour() > 23 ||
            weight.getEndHour() < 1 || weight.getEndHour() > 24) {
            throw new IllegalArgumentException("Hours must be between 0-23 for start and 1-24 for end");
        }
    }

    /**
     * 检查时间段是否重复
     */
    private void checkDuplicateRange(CustomerTimeWeight weight) {
        checkDuplicateRange(weight, null);
    }

    private void checkDuplicateRange(CustomerTimeWeight weight, Long excludeId) {
        Optional<CustomerTimeWeight> existing = repository
            .findByCustomerIdAndStartHourAndEndHour(
                weight.getCustomerId(), weight.getStartHour(), weight.getEndHour());

        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                String.format("Time range %d-%d already exists for customer %s",
                    weight.getStartHour(), weight.getEndHour(), weight.getCustomerId()));
        }
    }
}