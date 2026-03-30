package com.threatdetection.gateway.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {
    private String tenantId;       // slug: "acme-corp"
    private String name;           // display: "Acme Corporation"
    private String description;
    private String contactEmail;
    private Integer maxCustomers;  // defaults to 100 if null
}
