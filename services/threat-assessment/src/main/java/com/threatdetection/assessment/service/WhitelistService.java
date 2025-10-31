package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.WhitelistConfig;
import com.threatdetection.assessment.repository.WhitelistConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 白名单服务
 * 
 * <p>Phase 4核心服务: 管理白名单配置和过滤
 * 
 * <p>核心功能:
 * 1. 白名单检查 (IP/MAC/组合)
 * 2. 白名单配置管理 (CRUD)
 * 3. 自动清理过期白名单
 * 4. 白名单过期提醒
 * 5. 多租户隔离
 * 
 * <p>白名单类型:
 * - IP: IP地址白名单 (管理员IP、运维IP)
 * - MAC: MAC地址白名单 (信任设备)
 * - PORT: 端口白名单 (业务端口)
 * - COMBINED: 组合白名单 (IP+MAC绑定)
 * 
 * <p>使用场景:
 * - 管理员工作站白名单
 * - IT运维设备白名单
 * - 监控服务器白名单
 * - 临时访问白名单 (带过期时间)
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhitelistService {
    
    private final WhitelistConfigRepository repository;
    private final Environment environment;
    
    /**
     * 检查IP是否在白名单中 (带缓存)
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @return true表示在白名单中, false表示不在
     */
    @Cacheable(value = "ipWhitelist", key = "#customerId + ':' + #ipAddress")
    public boolean isIpWhitelisted(String customerId, String ipAddress) {
        if (customerId == null || ipAddress == null) {
            return false;
        }
        
        boolean whitelisted = repository.isIpWhitelisted(customerId, ipAddress);
        
        if (whitelisted) {
            log.debug("IP {} is whitelisted for customer {}", ipAddress, customerId);
        }
        
        return whitelisted;
    }
    
    /**
     * 检查MAC是否在白名单中 (带缓存)
     * 
     * @param customerId 客户ID
     * @param macAddress MAC地址
     * @return true表示在白名单中, false表示不在
     */
    @Cacheable(value = "macWhitelist", key = "#customerId + ':' + #macAddress")
    public boolean isMacWhitelisted(String customerId, String macAddress) {
        if (customerId == null || macAddress == null) {
            return false;
        }
        
        boolean whitelisted = repository.isMacWhitelisted(customerId, macAddress);
        
        if (whitelisted) {
            log.debug("MAC {} is whitelisted for customer {}", macAddress, customerId);
        }
        
        return whitelisted;
    }
    
    /**
     * 检查IP+MAC组合是否在白名单中
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @param macAddress MAC地址
     * @return true表示在白名单中, false表示不在
     */
    public boolean isCombinationWhitelisted(String customerId, String ipAddress, String macAddress) {
        if (customerId == null || ipAddress == null || macAddress == null) {
            return false;
        }
        
        boolean whitelisted = repository.isCombinationWhitelisted(customerId, ipAddress, macAddress);
        
        if (whitelisted) {
            log.debug("IP {} + MAC {} combination is whitelisted for customer {}", 
                     ipAddress, macAddress, customerId);
        }
        
        return whitelisted;
    }
    
    /**
     * 综合检查是否在白名单中
     * 
     * <p>检查顺序:
     * 1. 检查IP+MAC组合白名单
     * 2. 检查IP白名单
     * 3. 检查MAC白名单
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @param macAddress MAC地址
     * @return true表示在白名单中, false表示不在
     */
    public boolean isWhitelisted(String customerId, String ipAddress, String macAddress) {
        // 1. 检查组合白名单 (最严格)
        if (isCombinationWhitelisted(customerId, ipAddress, macAddress)) {
            log.info("Whitelisted by combination: customerId={}, ip={}, mac={}", 
                    customerId, ipAddress, macAddress);
            return true;
        }
        
        // 2. 检查IP白名单
        if (isIpWhitelisted(customerId, ipAddress)) {
            log.info("Whitelisted by IP: customerId={}, ip={}", customerId, ipAddress);
            return true;
        }
        
        // 3. 检查MAC白名单
        if (isMacWhitelisted(customerId, macAddress)) {
            log.info("Whitelisted by MAC: customerId={}, mac={}", customerId, macAddress);
            return true;
        }
        
        return false;
    }
    
    /**
     * 查询客户的所有有效白名单
     * 
     * @param customerId 客户ID
     * @return 有效的白名单配置列表
     */
    public List<WhitelistConfig> getActiveWhitelist(String customerId) {
        log.info("Querying active whitelist for customer: {}", customerId);
        return repository.findActiveByCustomerId(customerId);
    }
    
    /**
     * 添加白名单配置
     * 
     * @param config 白名单配置
     * @return 保存后的配置
     */
    @Transactional
    @CacheEvict(value = {"ipWhitelist", "macWhitelist"}, allEntries = true)
    public WhitelistConfig addWhitelist(WhitelistConfig config) {
        log.info("Adding whitelist: customerId={}, type={}, ip={}, mac={}", 
                config.getCustomerId(), config.getWhitelistType(), 
                config.getIpAddress(), config.getMacAddress());
        
        return repository.save(config);
    }
    
    /**
     * 删除白名单配置
     * 
     * @param id 白名单ID
     */
    @Transactional
    @CacheEvict(value = {"ipWhitelist", "macWhitelist"}, allEntries = true)
    public void deleteWhitelist(Long id) {
        log.info("Deleting whitelist: id={}", id);
        repository.deleteById(id);
    }
    
    /**
     * 禁用白名单配置
     * 
     * @param id 白名单ID
     */
    @Transactional
    @CacheEvict(value = {"ipWhitelist", "macWhitelist"}, allEntries = true)
    public void disableWhitelist(Long id) {
        repository.findById(id).ifPresent(config -> {
            config.setIsActive(false);
            repository.save(config);
            log.info("Disabled whitelist: id={}", id);
        });
    }
    
    /**
     * 查询即将过期的白名单 (7天内)
     * 
     * @return 即将过期的白名单列表
     */
    public List<WhitelistConfig> getExpiringSoon() {
        Instant threshold = Instant.now().plus(7, ChronoUnit.DAYS);
        return repository.findExpiringSoon(threshold);
    }
    
    /**
     * 自动清理过期白名单
     * 
     * <p>每天凌晨2点执行,自动禁用已过期的白名单
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    @CacheEvict(value = {"ipWhitelist", "macWhitelist"}, allEntries = true)
    public void cleanupExpiredWhitelist() {
        List<WhitelistConfig> expired = repository.findExpired();
        
        if (expired.isEmpty()) {
            log.info("No expired whitelist found");
            return;
        }
        
        log.info("Cleaning up {} expired whitelist entries", expired.size());
        
        for (WhitelistConfig config : expired) {
            config.setIsActive(false);
            repository.save(config);
            log.info("Disabled expired whitelist: id={}, customerId={}, type={}, expires={}",
                    config.getId(), config.getCustomerId(), config.getWhitelistType(), 
                    config.getExpiresAt());
        }
    }
    
    /**
     * 初始化白名单配置验证
     * 
     * <p>在应用启动时自动调用,验证白名单配置
     */
    @PostConstruct
    public void initializeWhitelist() {
        // Skip database initialization in test environments
        if (isTestEnvironment()) {
            log.info("Skipped database initialization in test environment");
            return;
        }

        long count = repository.count();
        
        if (count > 0) {
            log.info("Whitelist configurations found: {} entries", count);
            
            // 统计各类型数量
            List<WhitelistConfig> all = repository.findAll();
            long ipCount = all.stream().filter(w -> "IP".equals(w.getWhitelistType())).count();
            long macCount = all.stream().filter(w -> "MAC".equals(w.getWhitelistType())).count();
            long portCount = all.stream().filter(w -> "PORT".equals(w.getWhitelistType())).count();
            long combinedCount = all.stream().filter(w -> "COMBINED".equals(w.getWhitelistType())).count();
            
            log.info("Whitelist distribution: IP={}, MAC={}, PORT={}, COMBINED={}", 
                    ipCount, macCount, portCount, combinedCount);
        } else {
            log.warn("⚠️ No whitelist configurations found. Please run init-db.sql.phase4");
        }
    }

    private boolean isTestEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.contains("test") || profile.contains("h2")) {
                return true;
            }
        }
        return false;
    }
}
