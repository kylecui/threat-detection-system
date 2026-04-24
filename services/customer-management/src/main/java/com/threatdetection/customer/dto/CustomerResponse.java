package com.threatdetection.customer.dto;

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

    private Long id;

    private String customerId;

    private String name;

    private String email;

    private String phone;

    private String address;

    private Customer.CustomerStatus status;

    private Customer.SubscriptionTier subscriptionTier;

    private Integer maxDevices;

    private Integer currentDevices;

    private String description;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdBy;

    private String updatedBy;

    private Instant subscriptionStartDate;

    private Instant subscriptionEndDate;

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
