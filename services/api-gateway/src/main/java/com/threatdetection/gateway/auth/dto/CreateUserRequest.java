package com.threatdetection.gateway.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String username;
    private String password;
    private String displayName;
    private String email;
    private String customerId;     // which customer this user belongs to
    private Long tenantId;         // which tenant (resolved from JWT for TENANT_ADMIN)
    private String role;           // TENANT_ADMIN or CUSTOMER_USER
}
