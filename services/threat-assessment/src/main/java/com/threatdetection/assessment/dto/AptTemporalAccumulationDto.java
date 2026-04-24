package com.threatdetection.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for APT temporal accumulation data
 * Maps to apt_temporal_accumulations table
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AptTemporalAccumulationDto {

    private Long id;

    private String customerId;

    @NotNull(message = "Attack MAC is required")
    private String attackMac;

    @NotNull(message = "Window start is required")
    private Instant windowStart;

    @NotNull(message = "Window end is required")
    private Instant windowEnd;

    @NotNull(message = "Accumulated score is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Accumulated score must be >= 0.0")
    @DecimalMax(value = "10000.0", inclusive = true, message = "Accumulated score must be <= 10000.0")
    private BigDecimal accumulatedScore;

    @NotNull(message = "Decay accumulated score is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Decay accumulated score must be >= 0.0")
    @DecimalMax(value = "10000.0", inclusive = true, message = "Decay accumulated score must be <= 10000.0")
    private BigDecimal decayAccumulatedScore;

    private Instant lastUpdated;

    private Instant createdAt;
}
