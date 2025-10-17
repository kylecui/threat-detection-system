package com.threatdetection.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.threatdetection.customer.model.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 客户响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("customer_id")
    private String customerId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("status")
    private Customer.CustomerStatus status;
    
    @JsonProperty("subscription_tier")
    private Customer.SubscriptionTier subscriptionTier;
    
    @JsonProperty("max_devices")
    private Integer maxDevices;
    
    @JsonProperty("current_devices")
    private Integer currentDevices;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("updated_at")
    private Instant updatedAt;
    
    @JsonProperty("created_by")
    private String createdBy;
    
    @JsonProperty("updated_by")
    private String updatedBy;
    
    @JsonProperty("subscription_start_date")
    private Instant subscriptionStartDate;
    
    @JsonProperty("subscription_end_date")
    private Instant subscriptionEndDate;
    
    @JsonProperty("alert_enabled")
    private Boolean alertEnabled;

    /**
     * 从实体转换为DTO
     */
    public static CustomerResponse fromEntity(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerId(customer.getCustomerId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .status(customer.getStatus())
                .subscriptionTier(customer.getSubscriptionTier())
                .maxDevices(customer.getMaxDevices())
                .currentDevices(customer.getCurrentDevices())
                .description(customer.getDescription())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .createdBy(customer.getCreatedBy())
                .updatedBy(customer.getUpdatedBy())
                .subscriptionStartDate(customer.getSubscriptionStartDate())
                .subscriptionEndDate(customer.getSubscriptionEndDate())
                .alertEnabled(customer.getAlertEnabled())
                .build();
    }
}
