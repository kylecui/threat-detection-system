package com.threatdetection.customer.notification.controller;

import com.threatdetection.customer.notification.dto.NotificationConfigRequest;
import com.threatdetection.customer.notification.dto.NotificationConfigResponse;
import com.threatdetection.customer.notification.service.NotificationConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知配置管理控制器
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/notification-config")
@RequiredArgsConstructor
@Slf4j
public class NotificationConfigController {

    private final NotificationConfigService notificationConfigService;

    /**
     * 获取客户的通知配置
     *
     * @param customerId 客户ID
     * @return 通知配置
     */
    @GetMapping
    public ResponseEntity<NotificationConfigResponse> getConfig(
            @PathVariable("customerId") String customerId) {
        
        log.info("GET /api/v1/customers/{}/notification-config", customerId);
        NotificationConfigResponse response = notificationConfigService.getConfig(customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * 创建或更新通知配置
     *
     * @param customerId 客户ID
     * @param request 通知配置请求
     * @return 更新后的通知配置
     */
    @PutMapping
    public ResponseEntity<NotificationConfigResponse> createOrUpdateConfig(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody NotificationConfigRequest request) {
        
        log.info("PUT /api/v1/customers/{}/notification-config", customerId);
        NotificationConfigResponse response = notificationConfigService.createOrUpdateConfig(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 部分更新通知配置
     *
     * @param customerId 客户ID
     * @param request 通知配置请求（部分字段）
     * @return 更新后的通知配置
     */
    @PatchMapping
    public ResponseEntity<NotificationConfigResponse> patchConfig(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody NotificationConfigRequest request) {
        
        log.info("PATCH /api/v1/customers/{}/notification-config", customerId);
        NotificationConfigResponse response = notificationConfigService.createOrUpdateConfig(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除通知配置（恢复为默认）
     *
     * @param customerId 客户ID
     * @return 删除结果
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteConfig(
            @PathVariable("customerId") String customerId) {
        
        log.info("DELETE /api/v1/customers/{}/notification-config", customerId);
        notificationConfigService.deleteConfig(customerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 测试通知配置
     *
     * @param customerId 客户ID
     * @return 测试结果
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(
            @PathVariable("customerId") String customerId) {
        
        log.info("POST /api/v1/customers/{}/notification-config/test", customerId);
        Map<String, Object> testResult = notificationConfigService.testConfig(customerId);
        return ResponseEntity.ok(testResult);
    }

    /**
     * 启用/停用邮件通知
     *
     * @param customerId 客户ID
     * @param enabled 是否启用
     * @return 更新后的配置
     */
    @PatchMapping("/email/toggle")
    public ResponseEntity<NotificationConfigResponse> toggleEmail(
            @PathVariable("customerId") String customerId,
            @RequestParam boolean enabled) {
        
        log.info("PATCH /api/v1/customers/{}/notification-config/email/toggle?enabled={}", 
                customerId, enabled);
        
        NotificationConfigRequest request = new NotificationConfigRequest();
        request.setEmailEnabled(enabled);
        NotificationConfigResponse response = notificationConfigService.createOrUpdateConfig(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 启用/停用Slack通知
     *
     * @param customerId 客户ID
     * @param enabled 是否启用
     * @return 更新后的配置
     */
    @PatchMapping("/slack/toggle")
    public ResponseEntity<NotificationConfigResponse> toggleSlack(
            @PathVariable("customerId") String customerId,
            @RequestParam boolean enabled) {
        
        log.info("PATCH /api/v1/customers/{}/notification-config/slack/toggle?enabled={}", 
                customerId, enabled);
        
        NotificationConfigRequest request = new NotificationConfigRequest();
        request.setSlackEnabled(enabled);
        NotificationConfigResponse response = notificationConfigService.createOrUpdateConfig(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 启用/停用Webhook通知
     *
     * @param customerId 客户ID
     * @param enabled 是否启用
     * @return 更新后的配置
     */
    @PatchMapping("/webhook/toggle")
    public ResponseEntity<NotificationConfigResponse> toggleWebhook(
            @PathVariable("customerId") String customerId,
            @RequestParam boolean enabled) {
        
        log.info("PATCH /api/v1/customers/{}/notification-config/webhook/toggle?enabled={}", 
                customerId, enabled);
        
        NotificationConfigRequest request = new NotificationConfigRequest();
        request.setWebhookEnabled(enabled);
        NotificationConfigResponse response = notificationConfigService.createOrUpdateConfig(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 启用邮件通知
     *
     * @param customerId 客户ID
     * @return 更新后的配置
     */
    @PutMapping("/email/enable")
    public ResponseEntity<NotificationConfigResponse> enableEmail(
            @PathVariable("customerId") String customerId) {
        
        log.info("PUT /api/v1/customers/{}/notification-config/email/enable", customerId);
        NotificationConfigResponse response = notificationConfigService.toggleEmailEnabled(customerId, true);
        return ResponseEntity.ok(response);
    }

    /**
     * 禁用邮件通知
     *
     * @param customerId 客户ID
     * @return 更新后的配置
     */
    @PutMapping("/email/disable")
    public ResponseEntity<NotificationConfigResponse> disableEmail(
            @PathVariable("customerId") String customerId) {
        
        log.info("PUT /api/v1/customers/{}/notification-config/email/disable", customerId);
        NotificationConfigResponse response = notificationConfigService.toggleEmailEnabled(customerId, false);
        return ResponseEntity.ok(response);
    }

    /**
     * 启用短信通知
     *
     * @param customerId 客户ID
     * @return 更新后的配置
     */
    @PutMapping("/sms/enable")
    public ResponseEntity<NotificationConfigResponse> enableSms(
            @PathVariable("customerId") String customerId) {
        
        log.info("PUT /api/v1/customers/{}/notification-config/sms/enable", customerId);
        NotificationConfigResponse response = notificationConfigService.toggleSmsEnabled(customerId, true);
        return ResponseEntity.ok(response);
    }

    /**
     * 禁用短信通知
     *
     * @param customerId 客户ID
     * @return 更新后的配置
     */
    @PutMapping("/sms/disable")
    public ResponseEntity<NotificationConfigResponse> disableSms(
            @PathVariable("customerId") String customerId) {
        
        log.info("PUT /api/v1/customers/{}/notification-config/sms/disable", customerId);
        NotificationConfigResponse response = notificationConfigService.toggleSmsEnabled(customerId, false);
        return ResponseEntity.ok(response);
    }

    /**
     * 检查通知配置是否存在
     *
     * @param customerId 客户ID
     * @return 是否存在
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> checkConfigExists(
            @PathVariable("customerId") String customerId) {
        
        log.info("GET /api/v1/customers/{}/notification-config/exists", customerId);
        boolean exists = notificationConfigService.configExists(customerId);
        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "exists", exists
        ));
    }
}
