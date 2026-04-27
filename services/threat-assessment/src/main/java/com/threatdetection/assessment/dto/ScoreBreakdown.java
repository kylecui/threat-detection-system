package com.threatdetection.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBreakdown {
    private double baseScore;
    private double timeWeight;
    private String timeWeightNote;
    private double ipWeight;
    private double portWeight;
    private String portWeightNote;
    private double deviceWeight;
    private double attackSourceWeight;
    private double honeypotSensitivityWeight;
    private double combinedSegmentWeight;
    private double attackRateWeight;
    private double attackRate;
    private double mlWeight;
    private boolean mlEnabled;
    private double rawScore;
    private double normalizedScore;
    private String formula;
}
