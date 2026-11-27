package com.threatdetection.assessment.dto;

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
public class OverviewSnapshotResponse {
    private Instant generatedAt;
    private ThreatStatisticsResponse statistics;
    private List<TrendDataPoint> trend;
    private List<PortDistribution> portDistribution;
    private List<NonPortProtocolDistribution> nonPortProtocols;
    private List<TopAttackerResponse> topAttackers;
    private List<ThreatAssessmentDetailResponse> recentThreats;
}
