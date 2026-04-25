package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopAttackerResponse {
    private String attackMac;
    private String attackIp;
    private long totalCount;
    private double maxThreatScore;
    private String maxThreatLevel;
}
