package com.threatdetection.customer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateNetSegmentWeightRequest {

    @NotBlank(message = "CIDR不能为空")
    private String cidr;

    @DecimalMin(value = "0.01", message = "权重不能小于0.01")
    @DecimalMax(value = "10.0", message = "权重不能大于10.0")
    private BigDecimal weight;

    private String description;
}
