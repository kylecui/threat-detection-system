package com.threatdetection.alert.controller;

import com.threatdetection.alert.model.SmtpConfig;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import com.threatdetection.alert.repository.SmtpConfigRepository;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.service.notification.DynamicMailSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知配置管理API
 * 支持动态管理SMTP配置和客户通知配置
 */
@RestController
@RequestMapping("/api/notification-config")
public class NotificationConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationConfigController.class);
    
    @Autowired
    private SmtpConfigRepository smtpConfigRepository;
    
    @Autowired
    private CustomerNotificationConfigRepository customerNotificationConfigRepository;
    
    @Autowired
    private DynamicMailSenderService dynamicMailSenderService;
    
    // ==================== SMTP配置管理 ====================
    
    /**
     * 获取所有SMTP配置
     */
    @GetMapping("/smtp")
    public ResponseEntity<List<SmtpConfig>> getAllSmtpConfigs() {
        List<SmtpConfig> configs = smtpConfigRepository.findAll();
        logger.info("Retrieved {} SMTP configurations", configs.size());
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 获取默认SMTP配置
     */
    @GetMapping("/smtp/default")
    public ResponseEntity<SmtpConfig> getDefaultSmtpConfig() {
        Optional<SmtpConfig> config = smtpConfigRepository.findByIsDefaultTrue();
        if (config.isPresent()) {
            logger.info("Retrieved default SMTP config: {}", config.get().getConfigName());
            return ResponseEntity.ok(config.get());
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 根据ID获取SMTP配置
     */
    @GetMapping("/smtp/{id}")
    public ResponseEntity<SmtpConfig> getSmtpConfigById(@PathVariable Long id) {
        Optional<SmtpConfig> config = smtpConfigRepository.findById(id);
        if (config.isPresent()) {
            logger.info("Retrieved SMTP config: {}", config.get().getConfigName());
            return ResponseEntity.ok(config.get());
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 创建新SMTP配置
     */
    @PostMapping("/smtp")
    public ResponseEntity<SmtpConfig> createSmtpConfig(@RequestBody SmtpConfig config) {
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        
        SmtpConfig savedConfig = smtpConfigRepository.save(config);
        logger.info("Created new SMTP config: {} (ID={})", savedConfig.getConfigName(), savedConfig.getId());
        
        // 清除缓存
        dynamicMailSenderService.refreshCache();
        
        return ResponseEntity.ok(savedConfig);
    }
    
    /**
     * 更新SMTP配置
     */
    @PutMapping("/smtp/{id}")
    public ResponseEntity<SmtpConfig> updateSmtpConfig(
            @PathVariable Long id,
            @RequestBody SmtpConfig config) {
        
        Optional<SmtpConfig> existingOpt = smtpConfigRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        SmtpConfig existing = existingOpt.get();
        
        // 更新字段
        existing.setHost(config.getHost());
        existing.setPort(config.getPort());
        existing.setUsername(config.getUsername());
        
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            existing.setPassword(config.getPassword());
        }
        
        existing.setFromAddress(config.getFromAddress());
        existing.setFromName(config.getFromName());
        existing.setEnableTls(config.getEnableTls());
        existing.setEnableSsl(config.getEnableSsl());
        existing.setEnableStarttls(config.getEnableStarttls());
        existing.setConnectionTimeout(config.getConnectionTimeout());
        existing.setTimeout(config.getTimeout());
        existing.setWriteTimeout(config.getWriteTimeout());
        existing.setIsActive(config.getIsActive());
        existing.setDescription(config.getDescription());
        existing.setUpdatedAt(Instant.now());
        
        SmtpConfig updated = smtpConfigRepository.save(existing);
        logger.info("Updated SMTP config: {} (ID={})", updated.getConfigName(), updated.getId());
        
        // 清除缓存
        dynamicMailSenderService.refreshCache();
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * 测试SMTP连接
     */
    @PostMapping("/smtp/{id}/test")
    public ResponseEntity<Map<String, Object>> testSmtpConnection(@PathVariable Long id) {
        logger.info("Testing SMTP connection for config ID: {}", id);
        
        boolean success = dynamicMailSenderService.testConnection(id);
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success ? "SMTP连接测试成功" : "SMTP连接测试失败"
        ));
    }
    
    /**
     * 刷新SMTP配置缓存
     */
    @PostMapping("/smtp/refresh-cache")
    public ResponseEntity<Map<String, String>> refreshSmtpCache() {
        logger.info("Refreshing SMTP configuration cache");
        dynamicMailSenderService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "SMTP配置缓存已刷新"));
    }
    
    // ==================== 客户通知配置管理 ====================
    
    /**
     * 获取所有客户通知配置
     */
    @GetMapping("/customer")
    public ResponseEntity<List<CustomerNotificationConfig>> getAllCustomerConfigs() {
        List<CustomerNotificationConfig> configs = customerNotificationConfigRepository.findAll();
        logger.info("Retrieved {} customer notification configurations", configs.size());
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 根据客户ID获取通知配置
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerNotificationConfig> getCustomerConfig(@PathVariable String customerId) {
        Optional<CustomerNotificationConfig> config = customerNotificationConfigRepository.findByCustomerId(customerId);
        if (config.isPresent()) {
            logger.info("Retrieved notification config for customer: {}", customerId);
            return ResponseEntity.ok(config.get());
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 创建客户通知配置
     */
    @PostMapping("/customer")
    public ResponseEntity<CustomerNotificationConfig> createCustomerConfig(
            @RequestBody CustomerNotificationConfig config) {
        
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        
        CustomerNotificationConfig savedConfig = customerNotificationConfigRepository.save(config);
        logger.info("Created notification config for customer: {}", savedConfig.getCustomerId());
        
        return ResponseEntity.ok(savedConfig);
    }
    
    /**
     * 更新客户通知配置
     */
    @PutMapping("/customer/{customerId}")
    public ResponseEntity<CustomerNotificationConfig> updateCustomerConfig(
            @PathVariable String customerId,
            @RequestBody CustomerNotificationConfig config) {
        
        Optional<CustomerNotificationConfig> existingOpt = 
            customerNotificationConfigRepository.findByCustomerId(customerId);
        
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CustomerNotificationConfig existing = existingOpt.get();
        
        // 更新字段
        existing.setEmailEnabled(config.getEmailEnabled());
        existing.setEmailRecipients(config.getEmailRecipients());
        existing.setSmsEnabled(config.getSmsEnabled());
        existing.setSmsRecipients(config.getSmsRecipients());
        existing.setSlackEnabled(config.getSlackEnabled());
        existing.setSlackWebhookUrl(config.getSlackWebhookUrl());
        existing.setSlackChannel(config.getSlackChannel());
        existing.setWebhookEnabled(config.getWebhookEnabled());
        existing.setWebhookUrl(config.getWebhookUrl());
        existing.setWebhookHeaders(config.getWebhookHeaders());
        existing.setMinSeverityLevel(config.getMinSeverityLevel());
        existing.setNotifyOnSeverities(config.getNotifyOnSeverities());
        existing.setMaxNotificationsPerHour(config.getMaxNotificationsPerHour());
        existing.setEnableRateLimiting(config.getEnableRateLimiting());
        existing.setQuietHoursEnabled(config.getQuietHoursEnabled());
        existing.setQuietHoursStart(config.getQuietHoursStart());
        existing.setQuietHoursEnd(config.getQuietHoursEnd());
        existing.setQuietHoursTimezone(config.getQuietHoursTimezone());
        existing.setIsActive(config.getIsActive());
        existing.setDescription(config.getDescription());
        existing.setUpdatedAt(Instant.now());
        
        CustomerNotificationConfig updated = customerNotificationConfigRepository.save(existing);
        logger.info("Updated notification config for customer: {}", customerId);
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * 删除客户通知配置
     */
    @DeleteMapping("/customer/{customerId}")
    public ResponseEntity<Map<String, String>> deleteCustomerConfig(@PathVariable String customerId) {
        Optional<CustomerNotificationConfig> config = 
            customerNotificationConfigRepository.findByCustomerId(customerId);
        
        if (config.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        customerNotificationConfigRepository.delete(config.get());
        logger.info("Deleted notification config for customer: {}", customerId);
        
        return ResponseEntity.ok(Map.of("message", "配置已删除"));
    }
}
