package com.threatdetection.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupedAlertResponse {
    private String attackMac;
    private String maxSeverity;
    private Double maxThreatScore;
    private Long alertCount;
    private Long unresolvedCount;
    private LocalDateTime latestAlertTime;
}
