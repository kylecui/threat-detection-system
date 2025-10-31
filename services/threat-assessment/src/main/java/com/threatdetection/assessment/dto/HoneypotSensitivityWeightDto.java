package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for honeypot sensitivity weight configurations
 * Maps to honeypot_sensitivity_weights table
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HoneypotSensitivityWeightDto {

    private Long id;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("ip_segment")
    @NotNull(message = "IP segment is required")
    private String ipSegment;

    @JsonProperty("honeypot_sensitivity_weight")
    @NotNull(message = "Honeypot sensitivity weight is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Honeypot sensitivity weight must be >= 0.0")
    @DecimalMax(value = "10.0", inclusive = true, message = "Honeypot sensitivity weight must be <= 10.0")
    private BigDecimal honeypotSensitivityWeight;

    @JsonProperty("description")
    private String description;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
