package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupedThreatResponse {
    private String attackMac;
    private String customerId;
    private String maxThreatLevel;
    private Double maxThreatScore;
    private Long assessmentCount;
    private Instant latestAssessmentTime;
    private Long totalAttackCount;
    private Integer tierCount;
}
