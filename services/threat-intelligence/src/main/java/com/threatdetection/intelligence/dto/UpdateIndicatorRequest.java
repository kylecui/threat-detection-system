package com.threatdetection.intelligence.dto;

import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UpdateIndicatorRequest {
    private String iocValue;
    private IocType iocType;
    private String indicatorType;
    private Integer confidence;
    private Severity severity;
    private String sourceName;
    private String description;
    private List<String> tags;
    private Instant validFrom;
    private Instant validUntil;
    private String pattern;
    private String patternType;
}
