package com.threatdetection.alert.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Slack通知服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackService {

    private final RestTemplate smsRestTemplate; // 重用SMS配置中的RestTemplate

    @Value("${slack.webhook.url:}")
    private String slackWebhookUrl;

    /**
     * 发送Slack消息
     * @param webhookUrl Slack webhook URL
     * @param message 消息内容
     * @return 发送结果
     */
    public SlackResult sendMessage(String webhookUrl, String message) {
        try {
            String payload = createSlackPayload(message);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(payload, headers);

            org.springframework.http.ResponseEntity<String> response = smsRestTemplate.postForEntity(
                    webhookUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Slack message sent successfully to webhook");
                return SlackResult.success();
            } else {
                log.error("Failed to send Slack message. Status: {}", response.getStatusCode());
                return SlackResult.failure("HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending Slack message: {}", e.getMessage(), e);
            return SlackResult.failure(e.getMessage());
        }
    }

    /**
     * 发送Slack消息（使用配置的webhook URL）
     * @param message 消息内容
     * @return 发送结果
     */
    public SlackResult sendMessage(String message) {
        if (!isAvailable()) {
            return SlackResult.failure("Slack webhook URL not configured");
        }
        return sendMessage(slackWebhookUrl, message);
    }

    /**
     * 检查服务是否可用
     * @return true if available
     */
    public boolean isAvailable() {
        return slackWebhookUrl != null && !slackWebhookUrl.isEmpty();
    }

    /**
     * 创建Slack消息负载
     */
    private String createSlackPayload(String message) {
        return String.format("{\"text\":\"%s\"}", message.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    /**
     * Slack发送结果
     */
    public static class SlackResult {
        private final boolean success;
        private final String errorMessage;

        public SlackResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static SlackResult success() {
            return new SlackResult(true, null);
        }

        public static SlackResult failure(String errorMessage) {
            return new SlackResult(false, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}