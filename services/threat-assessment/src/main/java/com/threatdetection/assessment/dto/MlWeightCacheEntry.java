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
public class MlWeightCacheEntry {
    private double mlWeight;
    private double mlScore;
    private double mlConfidence;
    private String anomalyType;
    private String modelVersion;
    private Instant timestamp;
    private Instant cachedAt;

    public boolean isExpired(long ttlSeconds) {
        if (cachedAt == null) {
            return true;
        }
        return Instant.now().isAfter(cachedAt.plusSeconds(ttlSeconds));
    }
}
