package com.threatdetection.alert.dto;

import com.threatdetection.alert.model.SmtpConfig;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SmtpConfigResponse {
    private boolean configured;
    private Long id;
    private String configName;
    private String host;
    private Integer port;
    private String username;
    private boolean passwordConfigured;
    private String fromAddress;
    private String fromName;
    private String encryption;
    private Boolean active;
    private Boolean isDefault;
    private String description;
    private Integer connectionTimeout;
    private Integer timeout;
    private Integer writeTimeout;
    private Instant createdAt;
    private Instant updatedAt;

    public static SmtpConfigResponse unconfigured() {
        return SmtpConfigResponse.builder()
                .configured(false)
                .passwordConfigured(false)
                .active(true)
                .isDefault(true)
                .encryption("TLS")
                .port(587)
                .build();
    }

    public static SmtpConfigResponse fromEntity(SmtpConfig config) {
        return SmtpConfigResponse.builder()
                .configured(true)
                .id(config.getId())
                .configName(config.getConfigName())
                .host(config.getHost())
                .port(config.getPort())
                .username(config.getUsername())
                .passwordConfigured(config.getPassword() != null && !config.getPassword().isBlank())
                .fromAddress(config.getFromAddress())
                .fromName(config.getFromName())
                .encryption(resolveEncryption(config))
                .active(config.getIsActive())
                .isDefault(config.getIsDefault())
                .description(config.getDescription())
                .connectionTimeout(config.getConnectionTimeout())
                .timeout(config.getTimeout())
                .writeTimeout(config.getWriteTimeout())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private static String resolveEncryption(SmtpConfig config) {
        if (Boolean.TRUE.equals(config.getEnableSsl())) {
            return "SSL";
        }
        if (Boolean.TRUE.equals(config.getEnableStarttls())) {
            return "STARTTLS";
        }
        if (Boolean.TRUE.equals(config.getEnableTls())) {
            return "TLS";
        }
        return "NONE";
    }
}
