package com.threatdetection.alert.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import com.threatdetection.alert.model.Notification;
import com.threatdetection.alert.model.NotificationChannel;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes a real, synchronous delivery test for every enabled channel. */
@Service
@RequiredArgsConstructor
public class NotificationConfigurationTestService {

    private final CustomerNotificationConfigRepository configRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> test(String customerId) {
        CustomerNotificationConfig config = configRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification configuration not found"));
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Notification configuration is disabled");
        }

        String content = "威胁检测系统通知通道测试，客户：" + customerId
                + "，时间：" + OffsetDateTime.now();
        List<Map<String, Object>> results = new ArrayList<>();

        if (Boolean.TRUE.equals(config.getEmailEnabled())) {
            addRecipientTests(results, customerId, NotificationChannel.EMAIL,
                    parseRecipients(config.getEmailRecipients()), "通知配置测试", content);
        }
        if (Boolean.TRUE.equals(config.getSmsEnabled())) {
            addRecipientTests(results, customerId, NotificationChannel.SMS,
                    parseRecipients(config.getSmsRecipients()), "通知配置测试", content);
        }
        if (Boolean.TRUE.equals(config.getSlackEnabled())) {
            addSingleTest(results, customerId, NotificationChannel.SLACK,
                    config.getSlackWebhookUrl(), "通知配置测试", content);
        }
        if (Boolean.TRUE.equals(config.getWebhookEnabled())) {
            addSingleTest(results, customerId, NotificationChannel.WEBHOOK,
                    config.getWebhookUrl(), "通知配置测试", content);
        }

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No notification channel is enabled");
        }

        long succeeded = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("success")))
                .count();
        boolean success = succeeded == results.size();
        String status = success ? "SUCCESS" : succeeded == 0 ? "FAILED" : "PARTIAL_FAILURE";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("success", success);
        response.put("status", status);
        response.put("succeeded", succeeded);
        response.put("failed", results.size() - succeeded);
        response.put("message", success
                ? "All enabled notification channels delivered successfully"
                : "One or more notification channel deliveries failed");
        response.put("results", results);
        return response;
    }

    private void addRecipientTests(
            List<Map<String, Object>> results,
            String customerId,
            NotificationChannel channel,
            List<String> recipients,
            String subject,
            String content) {
        if (recipients.isEmpty()) {
            results.add(result(channel, "未配置", false, "Channel is enabled but has no recipient"));
            return;
        }
        for (String recipient : recipients) {
            addSingleTest(results, customerId, channel, recipient, subject, content);
        }
    }

    private void addSingleTest(
            List<Map<String, Object>> results,
            String customerId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String content) {
        if (recipient == null || recipient.isBlank()) {
            results.add(result(channel, "未配置", false, "Channel is enabled but its target is empty"));
            return;
        }

        Notification notification = new Notification();
        notification.setCustomerId(customerId);
        notification.setChannel(channel);
        notification.setRecipient(recipient.trim());
        notification.setSubject(subject);
        notification.setContent(content);
        NotificationService.NotificationDeliveryResult delivery =
                notificationService.testNotification(notification);
        results.add(result(channel, maskTarget(recipient), delivery.success(), delivery.message()));
    }

    private Map<String, Object> result(
            NotificationChannel channel, String target, boolean success, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", channel.name());
        result.put("target", target);
        result.put("success", success);
        result.put("message", message);
        return result;
    }

    private List<String> parseRecipients(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Recipient configuration is invalid", exception);
        }
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 5) {
            return "***";
        }
        int at = target.indexOf('@');
        if (at > 1) {
            return target.substring(0, 1) + "***" + target.substring(at);
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            int schemeEnd = target.indexOf("//") + 2;
            int pathStart = target.indexOf('/', schemeEnd);
            return pathStart > 0 ? target.substring(0, pathStart) + "/***" : target;
        }
        return "***" + target.substring(target.length() - 4);
    }
}
