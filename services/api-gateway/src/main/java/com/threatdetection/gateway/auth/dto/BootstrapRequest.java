package com.threatdetection.gateway.auth.dto;

import lombok.Data;

/** Request used exactly once to create the first platform administrator. */
@Data
public class BootstrapRequest {
    private String username;
    private String password;
    private String displayName;
    private String email;
}
