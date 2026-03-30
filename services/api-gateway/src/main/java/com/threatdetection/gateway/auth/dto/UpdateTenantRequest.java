package com.threatdetection.gateway.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequest {
    private String name;
    private String description;
    private String contactEmail;
    private String status;         // ACTIVE, INACTIVE, SUSPENDED
    private Integer maxCustomers;
}
