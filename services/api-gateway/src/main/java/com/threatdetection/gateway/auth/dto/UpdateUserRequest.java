package com.threatdetection.gateway.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private String displayName;
    private String email;
    private String customerId;
    private String password;       // null = no change
    private Boolean enabled;
    private String role;           // null = no change
}
