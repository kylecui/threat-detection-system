package com.threatdetection.alert.controller;

import com.threatdetection.alert.service.integration.IntegrationTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 集成测试控制器
 * 提供集成测试相关的API接口
 */
@RestController
@RequestMapping("/api/v1/integration-test")
@RequiredArgsConstructor
public class IntegrationTestController {

    private final IntegrationTestService integrationTestService;

    /**
     * 获取集成测试通知统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getNotificationStats() {
        IntegrationTestService.NotificationStats stats = integrationTestService.getNotificationStats();

        Map<String, Object> response = new HashMap<>();
        response.put("emailsSentInCurrentWindow", stats.getEmailsSentInCurrentWindow());
        response.put("maxEmailsPerWindow", stats.getMaxEmailsPerWindow());
        response.put("remainingEmails", stats.getRemainingEmails());
        response.put("windowMinutes", stats.getWindowMinutes());
        response.put("windowStart", stats.getWindowStart());
        response.put("currentTime", stats.getCurrentTime());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取集成测试状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getIntegrationTestStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", true);
        response.put("emailRecipient", integrationTestService.getTestEmailRecipient());
        response.put("notificationRules", "仅CRITICAL等级告警，每10分钟最多5封邮件");
        response.put("description", "集成测试服务正在运行，监听威胁事件并发送邮件通知");

        return ResponseEntity.ok(response);
    }
}