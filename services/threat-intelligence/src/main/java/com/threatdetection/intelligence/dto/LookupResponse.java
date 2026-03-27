package com.threatdetection.intelligence.dto;

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
public class LookupResponse {
    private String ip;
    private boolean found;
    private int confidence;
    private String severity;
    private double intelWeight;
    private List<String> sources;
    private int indicatorCount;
    private Instant lastSeenAt;
}
