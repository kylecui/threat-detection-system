package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.config.SmsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Twilio SMS服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsService implements SmsService {

    private final SmsConfig smsConfig;
    private final RestTemplate smsRestTemplate;

    private static final String TWILIO_API_BASE_URL = "https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/Messages.json";

    @Override
    public SmsResult sendSms(String phoneNumber, String message) {
        if (!isAvailable()) {
            return SmsResult.failure("Twilio SMS service not configured");
        }

        try {
            HttpHeaders headers = createHeaders();
            MultiValueMap<String, String> body = createBody(phoneNumber, message);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = smsRestTemplate.postForEntity(
                    TWILIO_API_BASE_URL.replace("{AccountSid}", smsConfig.getTwilioAccountSid()),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                // 解析响应获取messageId
                String messageId = extractMessageId(response.getBody());
                log.info("SMS sent successfully via Twilio to {}: {}", phoneNumber, messageId);
                return SmsResult.success(messageId);
            } else {
                log.error("Failed to send SMS via Twilio. Status: {}, Response: {}",
                         response.getStatusCode(), response.getBody());
                return SmsResult.failure("HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending SMS via Twilio to {}: {}", phoneNumber, e.getMessage(), e);
            return SmsResult.failure(e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return smsConfig.getTwilioAccountSid() != null && !smsConfig.getTwilioAccountSid().isEmpty() &&
               smsConfig.getTwilioAuthToken() != null && !smsConfig.getTwilioAuthToken().isEmpty() &&
               smsConfig.getTwilioPhoneNumber() != null && !smsConfig.getTwilioPhoneNumber().isEmpty();
    }

    /**
     * 创建HTTP请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Basic Authentication
        String auth = smsConfig.getTwilioAccountSid() + ":" + smsConfig.getTwilioAuthToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);

        return headers;
    }

    /**
     * 创建请求体
     */
    private MultiValueMap<String, String> createBody(String phoneNumber, String message) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("From", smsConfig.getTwilioPhoneNumber());
        body.add("To", phoneNumber);
        body.add("Body", message);
        return body;
    }

    /**
     * 从Twilio响应中提取消息ID
     */
    private String extractMessageId(String responseBody) {
        // 简单的JSON解析，实际项目中应该使用JSON库
        if (responseBody != null && responseBody.contains("\"sid\"")) {
            int sidStart = responseBody.indexOf("\"sid\":\"") + 7;
            int sidEnd = responseBody.indexOf("\"", sidStart);
            if (sidStart > 6 && sidEnd > sidStart) {
                return responseBody.substring(sidStart, sidEnd);
            }
        }
        return "unknown";
    }
}