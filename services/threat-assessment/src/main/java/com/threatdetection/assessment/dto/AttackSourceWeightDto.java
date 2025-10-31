package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for attack source weight configurations
 * Maps to attack_source_weights table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackSourceWeightDto {

    private Long id;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("ip_segment")
    @NotNull(message = "IP segment is required")
    private String ipSegment;

    @JsonProperty("attack_source_weight")
    @NotNull(message = "Attack source weight is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Attack source weight must be >= 0.0")
    @DecimalMax(value = "10.0", inclusive = true, message = "Attack source weight must be <= 10.0")
    private BigDecimal attackSourceWeight;

    @JsonProperty("description")
    private String description;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
