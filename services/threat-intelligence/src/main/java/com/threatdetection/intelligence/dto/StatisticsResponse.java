package com.threatdetection.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponse {
    private long totalIndicators;
    private long activeIndicators;
    private Map<String, Long> bySource;
    private Map<String, Long> bySeverity;
}
