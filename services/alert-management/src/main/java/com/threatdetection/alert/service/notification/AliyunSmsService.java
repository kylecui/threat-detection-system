package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.config.SmsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 阿里云SMS服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AliyunSmsService implements SmsService {

    private final SmsConfig smsConfig;
    private final RestTemplate smsRestTemplate;

    private static final String ALIYUN_SMS_API_URL = "https://dysmsapi.aliyuncs.com/";
    private static final String ALIYUN_ACCESS_KEY_ID = "AccessKeyId";
    private static final String ALIYUN_ACCESS_KEY_SECRET = "AccessKeySecret";
    private static final String ALIYUN_SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String ALIYUN_SIGNATURE_VERSION = "1.0";
    private static final String ALIYUN_VERSION = "2017-05-25";
    private static final String ALIYUN_ACTION = "SendSms";

    @Override
    public SmsResult sendSms(String phoneNumber, String message) {
        if (!isAvailable()) {
            return SmsResult.failure("Aliyun SMS service not configured");
        }

        try {
            Map<String, String> params = buildParameters(phoneNumber, message);
            String signature = calculateSignature(params);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String requestBody = buildRequestBody(params, signature);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = smsRestTemplate.postForEntity(
                    ALIYUN_SMS_API_URL,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                // 解析响应
                String responseBody = response.getBody();
                if (responseBody != null && responseBody.contains("\"Code\":\"OK\"")) {
                    String requestId = extractRequestId(responseBody);
                    log.info("SMS sent successfully via Aliyun to {}: {}", phoneNumber, requestId);
                    return SmsResult.success(requestId);
                } else {
                    String errorMessage = extractErrorMessage(responseBody);
                    log.error("Failed to send SMS via Aliyun: {}", errorMessage);
                    return SmsResult.failure(errorMessage);
                }
            } else {
                log.error("Failed to send SMS via Aliyun. Status: {}", response.getStatusCode());
                return SmsResult.failure("HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending SMS via Aliyun to {}: {}", phoneNumber, e.getMessage(), e);
            return SmsResult.failure(e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return smsConfig.getAliyunAccessKeyId() != null && !smsConfig.getAliyunAccessKeyId().isEmpty() &&
               smsConfig.getAliyunAccessKeySecret() != null && !smsConfig.getAliyunAccessKeySecret().isEmpty() &&
               smsConfig.getAliyunSignName() != null && !smsConfig.getAliyunSignName().isEmpty() &&
               smsConfig.getAliyunTemplateCode() != null && !smsConfig.getAliyunTemplateCode().isEmpty();
    }

    /**
     * 构建请求参数
     */
    private Map<String, String> buildParameters(String phoneNumber, String message) {
        Map<String, String> params = new HashMap<>();
        params.put("AccessKeyId", smsConfig.getAliyunAccessKeyId());
        params.put("Action", ALIYUN_ACTION);
        params.put("Version", ALIYUN_VERSION);
        params.put("SignatureMethod", ALIYUN_SIGNATURE_METHOD);
        params.put("SignatureVersion", ALIYUN_SIGNATURE_VERSION);
        params.put("Timestamp", getTimestamp());
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("PhoneNumbers", phoneNumber);
        params.put("SignName", smsConfig.getAliyunSignName());
        params.put("TemplateCode", smsConfig.getAliyunTemplateCode());
        params.put("TemplateParam", String.format("{\"content\":\"%s\"}", message.replace("\"", "\\\"")));
        return params;
    }

    /**
     * 计算签名
     */
    private String calculateSignature(Map<String, String> params) throws Exception {
        // 1. 排序参数
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        // 2. 构建规范化字符串
        StringBuilder canonicalizedQueryString = new StringBuilder();
        for (String key : sortedKeys) {
            canonicalizedQueryString.append("&")
                    .append(percentEncode(key))
                    .append("=")
                    .append(percentEncode(params.get(key)));
        }

        // 3. 构建待签名字符串
        String stringToSign = "POST&%2F&" + percentEncode(canonicalizedQueryString.substring(1));

        // 4. 计算HMAC-SHA1
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKey = new SecretKeySpec(
                (smsConfig.getAliyunAccessKeySecret() + "&").getBytes(StandardCharsets.UTF_8),
                "HmacSHA1"
        );
        mac.init(secretKey);
        byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

        // 5. Base64编码
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(Map<String, String> params, String signature) {
        StringBuilder body = new StringBuilder();
        body.append("Signature=").append(percentEncode(signature));

        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.append("&").append(percentEncode(entry.getKey()))
                .append("=").append(percentEncode(entry.getValue()));
        }

        return body.toString();
    }

    /**
     * URL编码
     */
    private String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 获取时间戳
     */
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    /**
     * 提取请求ID
     */
    private String extractRequestId(String responseBody) {
        if (responseBody != null && responseBody.contains("\"RequestId\"")) {
            int start = responseBody.indexOf("\"RequestId\":\"") + 13;
            int end = responseBody.indexOf("\"", start);
            if (start > 12 && end > start) {
                return responseBody.substring(start, end);
            }
        }
        return "unknown";
    }

    /**
     * 提取错误信息
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody != null && responseBody.contains("\"Message\"")) {
            int start = responseBody.indexOf("\"Message\":\"") + 11;
            int end = responseBody.indexOf("\"", start);
            if (start > 10 && end > start) {
                return responseBody.substring(start, end);
            }
        }
        return "Unknown error";
    }
}