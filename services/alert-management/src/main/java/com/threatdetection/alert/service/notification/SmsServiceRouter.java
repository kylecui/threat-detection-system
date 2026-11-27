package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.config.SmsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Selects the configured SMS provider at delivery time. */
@Service
public class SmsServiceRouter {

    private final SmsConfig smsConfig;
    private final SmsService twilioSmsService;
    private final SmsService aliyunSmsService;

    public SmsServiceRouter(
            SmsConfig smsConfig,
            @Qualifier("twilioSmsService") SmsService twilioSmsService,
            @Qualifier("aliyunSmsService") SmsService aliyunSmsService) {
        this.smsConfig = smsConfig;
        this.twilioSmsService = twilioSmsService;
        this.aliyunSmsService = aliyunSmsService;
    }

    public SmsService currentProvider() {
        String provider = smsConfig.getSmsProvider() == null
                ? "twilio"
                : smsConfig.getSmsProvider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "twilio" -> twilioSmsService;
            case "aliyun" -> aliyunSmsService;
            default -> throw new IllegalStateException("Unsupported SMS provider: " + provider);
        };
    }

    public String currentProviderName() {
        return smsConfig.getSmsProvider() == null || smsConfig.getSmsProvider().isBlank()
                ? "twilio"
                : smsConfig.getSmsProvider().trim().toLowerCase(Locale.ROOT);
    }
}
