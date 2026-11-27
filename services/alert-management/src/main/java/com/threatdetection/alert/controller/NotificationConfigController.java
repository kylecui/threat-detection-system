package com.threatdetection.alert.controller;

import com.threatdetection.alert.dto.SmtpConfigRequest;
import com.threatdetection.alert.dto.SmtpConfigResponse;
import com.threatdetection.alert.model.SmtpConfig;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import com.threatdetection.alert.repository.SmtpConfigRepository;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.service.notification.DynamicMailSenderService;
import com.threatdetection.alert.service.notification.NotificationConfigurationTestService;
import com.threatdetection.alert.service.SecretEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知配置管理API
 * 
 * <p><strong>重要更新 (2025-10-16):</strong>
 * 客户通知配置管理功能已迁移至 Customer-Management Service (端口8084)
 * 
 * <p><strong>职责分离:</strong>
 * <ul>
 *   <li><strong>Alert-Management (本服务)</strong>: 负责通知发送和SMTP配置管理，对客户通知配置具有<strong>只读权限</strong></li>
 *   <li><strong>Customer-Management (8084)</strong>: 负责客户通知配置的完整管理 (CRUD)</li>
 * </ul>
 * 
 * <p><strong>废弃的客户配置API:</strong>
 * <ul>
 *   <li>❌ POST /api/notification-config/customer - 已废弃，返回403</li>
 *   <li>❌ PUT /api/notification-config/customer/{customerId} - 已废弃，返回403</li>
 *   <li>❌ DELETE /api/notification-config/customer/{customerId} - 已废弃，返回403</li>
 * </ul>
 * 
 * <p><strong>保留的只读API:</strong>
 * <ul>
 *   <li>✅ GET /api/notification-config/customer - 查询所有配置 (内部使用)</li>
 *   <li>✅ GET /api/notification-config/customer/{customerId} - 查询单个配置 (内部使用)</li>
 * </ul>
 * 
 * <p><strong>新API (Customer-Management):</strong>
 * <ul>
 *   <li>GET/PUT/PATCH/DELETE /api/v1/customers/{customerId}/notification-config</li>
 *   <li>端口: 8084</li>
 *   <li>文档: docs/api/customer_management_api.md</li>
 * </ul>
 * 
 * <p><strong>SMTP配置管理:</strong>
 * 保留在本服务，支持完整的CRUD操作
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

    @Autowired
    private NotificationConfigurationTestService notificationConfigurationTestService;

    @Autowired
    private SecretEncryptionService secretEncryptionService;
    
    // ==================== SMTP配置管理 ====================
    
    /**
     * 获取所有SMTP配置
     */
    @GetMapping("/smtp")
    public ResponseEntity<List<SmtpConfigResponse>> getAllSmtpConfigs() {
        List<SmtpConfigResponse> configs = smtpConfigRepository.findAll().stream()
                .map(SmtpConfigResponse::fromEntity)
                .toList();
        logger.info("Retrieved {} SMTP configurations", configs.size());
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 获取默认SMTP配置
     */
    @GetMapping("/smtp/default")
    public ResponseEntity<SmtpConfigResponse> getDefaultSmtpConfig() {
        Optional<SmtpConfig> config = smtpConfigRepository.findByIsDefaultTrue();
        if (config.isPresent()) {
            logger.info("Retrieved default SMTP config: {}", config.get().getConfigName());
            return ResponseEntity.ok(SmtpConfigResponse.fromEntity(config.get()));
        }
        return ResponseEntity.ok(SmtpConfigResponse.unconfigured());
    }

    /**
     * 创建或更新唯一的默认SMTP配置。更新时密码留空表示保留已有密码。
     */
    @PutMapping("/smtp/default")
    public ResponseEntity<SmtpConfigResponse> upsertDefaultSmtpConfig(
            @Valid @RequestBody SmtpConfigRequest request) {
        SmtpConfig config = smtpConfigRepository.findByIsDefaultTrue()
                .orElseGet(SmtpConfig::new);
        boolean creating = config.getId() == null;
        if (creating && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SMTP password is required when creating the default configuration");
        }

        applyRequest(config, request, true);
        if (creating) {
            config.setCreatedAt(Instant.now());
        }
        config.setUpdatedAt(Instant.now());
        SmtpConfig saved = smtpConfigRepository.save(config);
        dynamicMailSenderService.refreshCache();
        logger.info("Upserted default SMTP config: {} (ID={})", saved.getConfigName(), saved.getId());
        return ResponseEntity.ok(SmtpConfigResponse.fromEntity(saved));
    }
    
    /**
     * 根据ID获取SMTP配置
     */
    @GetMapping("/smtp/{id}")
    public ResponseEntity<SmtpConfigResponse> getSmtpConfigById(@PathVariable Long id) {
        Optional<SmtpConfig> config = smtpConfigRepository.findById(id);
        if (config.isPresent()) {
            logger.info("Retrieved SMTP config: {}", config.get().getConfigName());
            return ResponseEntity.ok(SmtpConfigResponse.fromEntity(config.get()));
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 创建新SMTP配置
     */
    @PostMapping("/smtp")
    public ResponseEntity<SmtpConfigResponse> createSmtpConfig(
            @Valid @RequestBody SmtpConfigRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SMTP password is required when creating a configuration");
        }
        SmtpConfig config = new SmtpConfig();
        applyRequest(config, request, Boolean.TRUE.equals(request.getIsDefault()));
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        
        SmtpConfig savedConfig = smtpConfigRepository.save(config);
        logger.info("Created new SMTP config: {} (ID={})", savedConfig.getConfigName(), savedConfig.getId());
        
        // 清除缓存
        dynamicMailSenderService.refreshCache();
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SmtpConfigResponse.fromEntity(savedConfig));
    }
    
    /**
     * 更新SMTP配置
     */
    @PutMapping("/smtp/{id}")
    public ResponseEntity<SmtpConfigResponse> updateSmtpConfig(
            @PathVariable Long id,
            @Valid @RequestBody SmtpConfigRequest request) {
        
        Optional<SmtpConfig> existingOpt = smtpConfigRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        SmtpConfig existing = existingOpt.get();
        
        applyRequest(existing, request, Boolean.TRUE.equals(existing.getIsDefault()));
        existing.setUpdatedAt(Instant.now());
        
        SmtpConfig updated = smtpConfigRepository.save(existing);
        logger.info("Updated SMTP config: {} (ID={})", updated.getConfigName(), updated.getId());
        
        // 清除缓存
        dynamicMailSenderService.refreshCache();
        
        return ResponseEntity.ok(SmtpConfigResponse.fromEntity(updated));
    }

    private void applyRequest(SmtpConfig config, SmtpConfigRequest request, boolean forceDefault) {
        String encryption = request.getEncryption() == null
                ? "TLS"
                : request.getEncryption().trim().toUpperCase();
        String requestedName = request.getConfigName();
        if (forceDefault) {
            config.setConfigName("default");
        } else if (requestedName != null && !requestedName.isBlank()) {
            config.setConfigName(requestedName.trim());
        } else if (config.getConfigName() == null || config.getConfigName().isBlank()) {
            config.setConfigName("smtp-" + System.currentTimeMillis());
        }
        config.setHost(request.getHost().trim());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername() == null ? "" : request.getUsername().trim());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            config.setPassword(secretEncryptionService.encrypt(request.getPassword()));
        }
        config.setFromAddress(request.getFromAddress().trim());
        config.setFromName(request.getFromName());
        config.setEnableTls("TLS".equals(encryption));
        config.setEnableStarttls("STARTTLS".equals(encryption));
        config.setEnableSsl("SSL".equals(encryption));
        config.setConnectionTimeout(defaultTimeout(request.getConnectionTimeout()));
        config.setTimeout(defaultTimeout(request.getTimeout()));
        config.setWriteTimeout(defaultTimeout(request.getWriteTimeout()));
        config.setIsActive(request.getActive() == null || request.getActive());
        config.setIsDefault(forceDefault || Boolean.TRUE.equals(request.getIsDefault()));
        config.setDescription(request.getDescription());
    }

    private int defaultTimeout(Integer value) {
        return value == null || value < 1 ? 5000 : value;
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

    /** Performs real sends for every enabled channel and returns per-target results. */
    @PostMapping("/customer/{customerId}/test")
    public ResponseEntity<Map<String, Object>> testCustomerConfig(@PathVariable String customerId) {
        return ResponseEntity.ok(notificationConfigurationTestService.test(customerId));
    }
    
    /**
     * 创建客户通知配置
     * 
     * @deprecated 该功能已迁移至 Customer-Management Service
     * 请使用: PUT /api/v1/customers/{customerId}/notification-config (端口8084)
     */
    @Deprecated
    @PostMapping("/customer")
    public ResponseEntity<?> createCustomerConfig() {
        
        logger.warn("DEPRECATED API called: POST /api/notification-config/customer - " +
                   "This endpoint is deprecated. Please use Customer-Management service.");
        
        return ResponseEntity.status(403).body(Map.of(
            "error", "Forbidden",
            "message", "此API已废弃。通知配置管理已迁移至 Customer-Management Service (端口8084)",
            "deprecated", true,
            "newEndpoint", "PUT http://localhost:8084/api/v1/customers/{customerId}/notification-config",
            "documentation", "请参考 Customer-Management API 文档",
            "reason", "职责分离: Alert-Management 只负责通知发送，配置管理由 Customer-Management 负责"
        ));
    }
    
    /**
     * 更新客户通知配置
     * 
     * @deprecated 该功能已迁移至 Customer-Management Service
     * 请使用: PUT /api/v1/customers/{customerId}/notification-config (端口8084)
     */
    @Deprecated
    @PutMapping("/customer/{customerId}")
    public ResponseEntity<?> updateCustomerConfig(
            @PathVariable String customerId,
            @RequestBody CustomerNotificationConfig config) {
        
        logger.warn("DEPRECATED API called: PUT /api/notification-config/customer/{} - " +
                   "This endpoint is deprecated. Please use Customer-Management service.", customerId);
        
        return ResponseEntity.status(403).body(Map.of(
            "error", "Forbidden",
            "message", "此API已废弃。通知配置管理已迁移至 Customer-Management Service (端口8084)",
            "deprecated", true,
            "customerId", customerId,
            "newEndpoint", String.format("PUT http://localhost:8084/api/v1/customers/%s/notification-config", customerId),
            "documentation", "请参考 Customer-Management API 文档",
            "reason", "职责分离: Alert-Management 只负责通知发送，配置管理由 Customer-Management 负责"
        ));
    }
    
    /**
     * 删除客户通知配置
     * 
     * @deprecated 该功能已迁移至 Customer-Management Service
     * 请使用: DELETE /api/v1/customers/{customerId}/notification-config (端口8084)
     */
    @Deprecated
    @DeleteMapping("/customer/{customerId}")
    public ResponseEntity<?> deleteCustomerConfig(@PathVariable String customerId) {
        
        logger.warn("DEPRECATED API called: DELETE /api/notification-config/customer/{} - " +
                   "This endpoint is deprecated. Please use Customer-Management service.", customerId);
        
        return ResponseEntity.status(403).body(Map.of(
            "error", "Forbidden",
            "message", "此API已废弃。通知配置管理已迁移至 Customer-Management Service (端口8084)",
            "deprecated", true,
            "customerId", customerId,
            "newEndpoint", String.format("DELETE http://localhost:8084/api/v1/customers/%s/notification-config", customerId),
            "documentation", "请参考 Customer-Management API 文档",
            "reason", "职责分离: Alert-Management 只负责通知发送，配置管理由 Customer-Management 负责"
        ));
    }
}
