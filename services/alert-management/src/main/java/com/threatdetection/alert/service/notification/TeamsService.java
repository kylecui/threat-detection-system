package com.threatdetection.alert.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Microsoft Teams通知服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamsService {

    private final RestTemplate smsRestTemplate; // 重用SMS配置中的RestTemplate

    @Value("${teams.webhook.url:}")
    private String teamsWebhookUrl;

    /**
     * 发送Teams消息
     * @param webhookUrl Teams webhook URL
     * @param title 消息标题
     * @param message 消息内容
     * @return 发送结果
     */
    public TeamsResult sendMessage(String webhookUrl, String title, String message) {
        try {
            String payload = createTeamsPayload(title, message);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(payload, headers);

            org.springframework.http.ResponseEntity<String> response = smsRestTemplate.postForEntity(
                    webhookUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Teams message sent successfully to webhook");
                return TeamsResult.success();
            } else {
                log.error("Failed to send Teams message. Status: {}", response.getStatusCode());
                return TeamsResult.failure("HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending Teams message: {}", e.getMessage(), e);
            return TeamsResult.failure(e.getMessage());
        }
    }

    /**
     * 发送Teams消息（使用配置的webhook URL）
     * @param title 消息标题
     * @param message 消息内容
     * @return 发送结果
     */
    public TeamsResult sendMessage(String title, String message) {
        if (!isAvailable()) {
            return TeamsResult.failure("Teams webhook URL not configured");
        }
        return sendMessage(teamsWebhookUrl, title, message);
    }

    /**
     * 检查服务是否可用
     * @return true if available
     */
    public boolean isAvailable() {
        return teamsWebhookUrl != null && !teamsWebhookUrl.isEmpty();
    }

    /**
     * 创建Teams消息负载
     */
    private String createTeamsPayload(String title, String message) {
        return String.format(
            "{\"@type\":\"MessageCard\",\"@context\":\"http://schema.org/extensions\",\"summary\":\"%s\",\"title\":\"%s\",\"text\":\"%s\"}",
            title.replace("\"", "\\\""),
            title.replace("\"", "\\\""),
            message.replace("\"", "\\\"").replace("\n", "\\n")
        );
    }

    /**
     * Teams发送结果
     */
    public static class TeamsResult {
        private final boolean success;
        private final String errorMessage;

        public TeamsResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static TeamsResult success() {
            return new TeamsResult(true, null);
        }

        public static TeamsResult failure(String errorMessage) {
            return new TeamsResult(false, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}