package com.threatdetection.assessment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * DTO for attack phase port configurations
 * Maps to attack_phase_port_configs table
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AttackPhasePortConfigDto {

    private Long id;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("phase")
    @NotNull(message = "Phase is required")
    private String phase;

    @JsonProperty("port")
    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be >= 1")
    @Max(value = 65535, message = "Port must be <= 65535")
    private Integer port;

    @JsonProperty("priority")
    @Min(value = 1, message = "Priority must be >= 1")
    @Max(value = 10, message = "Priority must be <= 10")
    private Integer priority;

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("description")
    private String description;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
