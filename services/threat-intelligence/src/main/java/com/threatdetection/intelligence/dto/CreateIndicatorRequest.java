package com.threatdetection.intelligence.dto;

import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateIndicatorRequest {
    @NotBlank
    private String iocValue;

    @NotNull
    private IocType iocType;

    private String indicatorType = "malicious-activity";

    private Integer confidence = 50;

    @NotNull
    private Severity severity;

    @NotBlank
    private String sourceName;

    private String description;

    private List<String> tags;

    private Instant validFrom;

    private Instant validUntil;
}
