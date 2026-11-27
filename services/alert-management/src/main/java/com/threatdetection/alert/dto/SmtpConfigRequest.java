package com.threatdetection.alert.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SmtpConfigRequest {

    private String configName;

    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    @NotNull
    private Integer port;

    private String username;
    private String password;

    @NotBlank
    private String fromAddress;

    private String fromName;

    @Pattern(regexp = "(?i)NONE|TLS|STARTTLS|SSL")
    private String encryption = "TLS";

    private Boolean active = true;
    private Boolean isDefault = true;
    private String description;
    private Integer connectionTimeout = 5000;
    private Integer timeout = 5000;
    private Integer writeTimeout = 5000;
}
