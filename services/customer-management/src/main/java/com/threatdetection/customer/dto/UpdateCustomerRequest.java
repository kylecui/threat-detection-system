package com.threatdetection.customer.dto;

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

    @Size(max = 200, message = "客户名称长度不能超过200")
    private String name;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 50, message = "电话长度不能超过50")
    private String phone;

    @Size(max = 500, message = "地址长度不能超过500")
    private String address;

    private Customer.CustomerStatus status;

    private Customer.SubscriptionTier subscriptionTier;

    private Integer maxDevices;

    @Size(max = 1000, message = "描述长度不能超过1000")
    private String description;

    private Instant subscriptionEndDate;

    private Boolean alertEnabled;
}
