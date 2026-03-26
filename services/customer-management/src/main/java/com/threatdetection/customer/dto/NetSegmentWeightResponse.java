package com.threatdetection.customer.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class NetSegmentWeightResponse {

    private Long id;

    private String customerId;

    private String cidr;

    private BigDecimal weight;

    private String description;

    private Instant createdAt;

    private Instant updatedAt;
}
