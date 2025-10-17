package com.threatdetection.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.threatdetection.customer.model.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 更新客户请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomerRequest {

    @JsonProperty("name")
    @Size(max = 200, message = "客户名称长度不能超过200")
    private String name;

    @JsonProperty("email")
    @Email(message = "邮箱格式不正确")
    private String email;

    @JsonProperty("phone")
    @Size(max = 50, message = "电话长度不能超过50")
    private String phone;

    @JsonProperty("address")
    @Size(max = 500, message = "地址长度不能超过500")
    private String address;

    @JsonProperty("status")
    private Customer.CustomerStatus status;

    @JsonProperty("subscription_tier")
    private Customer.SubscriptionTier subscriptionTier;

    @JsonProperty("max_devices")
    private Integer maxDevices;

    @JsonProperty("description")
    @Size(max = 1000, message = "描述长度不能超过1000")
    private String description;

    @JsonProperty("subscription_end_date")
    private Instant subscriptionEndDate;

    @JsonProperty("alert_enabled")
    private Boolean alertEnabled;
}
