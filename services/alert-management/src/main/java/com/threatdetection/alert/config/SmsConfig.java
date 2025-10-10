package com.threatdetection.alert.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * SMS配置类
 */
@Configuration
public class SmsConfig {

    @Value("${sms.provider:twilio}")
    private String smsProvider;

    @Value("${sms.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${sms.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${sms.twilio.phone-number:}")
    private String twilioPhoneNumber;

    @Value("${sms.aliyun.access-key-id:}")
    private String aliyunAccessKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String aliyunAccessKeySecret;

    @Value("${sms.aliyun.sign-name:}")
    private String aliyunSignName;

    @Value("${sms.aliyun.template-code:}")
    private String aliyunTemplateCode;

    /**
     * RestTemplate用于HTTP请求
     */
    @Bean
    public RestTemplate smsRestTemplate() {
        return new RestTemplate();
    }

    // Getters for configuration values
    public String getSmsProvider() {
        return smsProvider;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public String getTwilioAuthToken() {
        return twilioAuthToken;
    }

    public String getTwilioPhoneNumber() {
        return twilioPhoneNumber;
    }

    public String getAliyunAccessKeyId() {
        return aliyunAccessKeyId;
    }

    public String getAliyunAccessKeySecret() {
        return aliyunAccessKeySecret;
    }

    public String getAliyunSignName() {
        return aliyunSignName;
    }

    public String getAliyunTemplateCode() {
        return aliyunTemplateCode;
    }
}