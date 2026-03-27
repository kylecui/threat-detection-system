package com.threatdetection.intelligence.dto;

import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorResponse {
    private Long id;
    private String iocValue;
    private IocType iocType;
    private String iocInet;
    private String indicatorType;
    private String pattern;
    private String patternType;
    private Integer confidence;
    private Instant validFrom;
    private Instant validUntil;
    private Severity severity;
    private String sourceName;
    private String description;
    private List<String> tags;
    private Integer sightingCount;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant createdAt;
    private Instant updatedAt;
}
