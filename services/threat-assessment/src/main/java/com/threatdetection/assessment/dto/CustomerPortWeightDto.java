package com.threatdetection.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for customer port weight configurations
 * Maps to customer_port_weights table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPortWeightDto {

    private String customerId;

    @NotNull(message = "Port number is required")
    @Min(value = 1, message = "Port number must be >= 1")
    @Max(value = 65535, message = "Port number must be <= 65535")
    private Integer portNumber;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Weight must be >= 0.0")
    @DecimalMax(value = "10.0", inclusive = true, message = "Weight must be <= 10.0")
    private BigDecimal weight;

    private String riskLevel;

    @Min(value = 1, message = "Priority must be >= 1")
    @Max(value = 10, message = "Priority must be <= 10")
    private Integer priority;

    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
