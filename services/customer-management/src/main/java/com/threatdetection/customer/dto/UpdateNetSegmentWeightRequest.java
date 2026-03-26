package com.threatdetection.customer.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateNetSegmentWeightRequest {

    private BigDecimal weight;

    private String description;
}
