package com.threatdetection.customer.service;

import com.threatdetection.customer.dto.CreateCustomerRequest;
import com.threatdetection.customer.dto.CustomerResponse;
import com.threatdetection.customer.dto.UpdateCustomerRequest;
import com.threatdetection.customer.exception.CustomerAlreadyExistsException;
import com.threatdetection.customer.exception.CustomerNotFoundException;
import com.threatdetection.customer.model.Customer;
import com.threatdetection.customer.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户管理服务
 */
@Service
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * 创建客户
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer: {}", request.getCustomerId());

        // 检查customerId是否已存在
        if (customerRepository.existsByCustomerId(request.getCustomerId())) {
            throw new CustomerAlreadyExistsException(
                "Customer with ID '" + request.getCustomerId() + "' already exists"
            );
        }

        // 构建客户实体
        Customer customer = Customer.builder()
                .customerId(request.getCustomerId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status(Customer.CustomerStatus.ACTIVE)
                .subscriptionTier(request.getSubscriptionTier() != null ? 
                    request.getSubscriptionTier() : Customer.SubscriptionTier.BASIC)
                .maxDevices(calculateMaxDevices(request.getSubscriptionTier(), request.getMaxDevices()))
                .currentDevices(0)
                .description(request.getDescription())
                .subscriptionStartDate(request.getSubscriptionStartDate() != null ? 
                    request.getSubscriptionStartDate() : Instant.now())
                .subscriptionEndDate(request.getSubscriptionEndDate())
                .alertEnabled(request.getAlertEnabled() != null ? request.getAlertEnabled() : true)
                .createdBy("admin")
                .updatedBy("admin")
                .build();

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created successfully: {}", savedCustomer.getCustomerId());

        return CustomerResponse.fromEntity(savedCustomer);
    }

    /**
     * 根据customerId获取客户信息
     */
    public CustomerResponse getCustomer(String customerId) {
        log.debug("Fetching customer: {}", customerId);
        
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + customerId
                ));

        return CustomerResponse.fromEntity(customer);
    }

    /**
     * 获取所有客户 (分页)
     */
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        log.debug("Fetching all customers, page: {}", pageable.getPageNumber());
        
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::fromEntity);
    }

    /**
     * 搜索客户 (支持名称、ID、邮箱模糊匹配)
     */
    public Page<CustomerResponse> searchCustomers(String keyword, Pageable pageable) {
        log.debug("Searching customers with keyword: {}", keyword);
        
        return customerRepository.searchCustomers(keyword, pageable)
                .map(CustomerResponse::fromEntity);
    }

    /**
     * 根据状态查询客户
     */
    public Page<CustomerResponse> getCustomersByStatus(Customer.CustomerStatus status, Pageable pageable) {
        log.debug("Fetching customers by status: {}", status);
        
        return customerRepository.findByStatus(status, pageable)
                .map(CustomerResponse::fromEntity);
    }

    public List<CustomerResponse> getCustomersByTenant(Long tenantId) {
        log.debug("Fetching customers by tenantId: {}", tenantId);
        return customerRepository.findByTenantId(tenantId)
                .stream()
                .map(CustomerResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 更新客户信息
     */
    @Transactional
    public CustomerResponse updateCustomer(String customerId, UpdateCustomerRequest request) {
        log.info("Updating customer: {}", customerId);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + customerId
                ));

        // 更新字段 (只更新非null值)
        if (request.getName() != null) {
            customer.setName(request.getName());
        }
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            customer.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            customer.setAddress(request.getAddress());
        }
        if (request.getStatus() != null) {
            customer.setStatus(request.getStatus());
        }
        if (request.getSubscriptionTier() != null) {
            customer.setSubscriptionTier(request.getSubscriptionTier());
            // 更新套餐时自动调整maxDevices
            if (request.getMaxDevices() == null) {
                customer.setMaxDevices(calculateMaxDevices(request.getSubscriptionTier(), null));
            }
        }
        if (request.getMaxDevices() != null) {
            customer.setMaxDevices(request.getMaxDevices());
        }
        if (request.getDescription() != null) {
            customer.setDescription(request.getDescription());
        }
        if (request.getSubscriptionEndDate() != null) {
            customer.setSubscriptionEndDate(request.getSubscriptionEndDate());
        }
        if (request.getAlertEnabled() != null) {
            customer.setAlertEnabled(request.getAlertEnabled());
        }

        customer.setUpdatedBy("admin");

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated successfully: {}", customerId);

        return CustomerResponse.fromEntity(updatedCustomer);
    }

    /**
     * 删除客户 (软删除 - 设置为INACTIVE状态)
     */
    @Transactional
    public void deleteCustomer(String customerId) {
        log.info("Deleting (deactivating) customer: {}", customerId);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + customerId
                ));

        customer.setStatus(Customer.CustomerStatus.INACTIVE);
        customer.setUpdatedBy("admin");
        customerRepository.save(customer);

        log.info("Customer deactivated successfully: {}", customerId);
    }

    /**
     * 硬删除客户 (物理删除)
     */
    @Transactional
    public void hardDeleteCustomer(String customerId) {
        log.warn("Hard deleting customer: {}", customerId);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + customerId
                ));

        customerRepository.delete(customer);
        log.info("Customer permanently deleted: {}", customerId);
    }

    /**
     * 更新客户设备数量
     */
    @Transactional
    public void updateDeviceCount(String customerId, int deviceCount) {
        log.debug("Updating device count for customer {}: {}", customerId, deviceCount);

        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + customerId
                ));

        customer.setCurrentDevices(deviceCount);
        customerRepository.save(customer);
    }

    /**
     * 获取客户统计信息
     */
    public Map<String, Object> getCustomerStatistics() {
        log.debug("Fetching customer statistics");

        long totalCustomers = customerRepository.count();
        
        Map<String, Long> statusCounts = customerRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                    arr -> ((Customer.CustomerStatus) arr[0]).name(),
                    arr -> (Long) arr[1]
                ));

        Map<String, Long> tierCounts = customerRepository.countBySubscriptionTier().stream()
                .collect(Collectors.toMap(
                    arr -> ((Customer.SubscriptionTier) arr[0]).name(),
                    arr -> (Long) arr[1]
                ));

        return Map.of(
            "totalCustomers", totalCustomers,
            "statusDistribution", statusCounts,
            "tierDistribution", tierCounts
        );
    }

    /**
     * 检查客户ID是否存在
     */
    public boolean existsById(String customerId) {
        log.debug("Checking if customer exists: {}", customerId);
        return customerRepository.existsByCustomerId(customerId);
    }

    /**
     * 获取客户实体 (供内部服务使用)
     */
    public Customer getCustomerEntity(String customerId) {
        return customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    /**
     * 获取单个客户的统计信息
     */
    public Map<String, Object> getCustomerStats(String customerId) {
        log.debug("Fetching stats for customer: {}", customerId);
        
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        return Map.of(
            "customerId", customer.getCustomerId(),
            "name", customer.getName(),
            "status", customer.getStatus().name(),
            "subscriptionTier", customer.getSubscriptionTier() != null ? customer.getSubscriptionTier().name() : "BASIC",
            "currentDevices", customer.getCurrentDevices(),
            "maxDevices", customer.getMaxDevices(),
            "createdAt", customer.getCreatedAt().toString(),
            "updatedAt", customer.getUpdatedAt().toString()
        );
    }

    /**
     * 部分更新客户信息
     */
    public CustomerResponse patchCustomer(String customerId, Map<String, Object> updates) {
        log.info("Partially updating customer: {} with {} fields", customerId, updates.size());
        
        Customer customer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // 更新允许的字段
        if (updates.containsKey("name") && updates.get("name") != null) {
            customer.setName(String.valueOf(updates.get("name")));
        }
        if (updates.containsKey("email") && updates.get("email") != null) {
            customer.setEmail(String.valueOf(updates.get("email")));
        }
        if (updates.containsKey("phone") && updates.get("phone") != null) {
            customer.setPhone(String.valueOf(updates.get("phone")));
        }
        if (updates.containsKey("address") && updates.get("address") != null) {
            customer.setAddress(String.valueOf(updates.get("address")));
        }
        if (updates.containsKey("description") && updates.get("description") != null) {
            customer.setDescription(String.valueOf(updates.get("description")));
        }
        if (updates.containsKey("alertEnabled") && updates.get("alertEnabled") != null) {
            customer.setAlertEnabled(Boolean.valueOf(String.valueOf(updates.get("alertEnabled"))));
        }

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer partially updated: {}", customerId);
        
        return CustomerResponse.fromEntity(savedCustomer);
    }

    /**
     * 根据订阅套餐计算最大设备数
     */
    private int calculateMaxDevices(Customer.SubscriptionTier tier, Integer customValue) {
        if (customValue != null) {
            return customValue;
        }

        if (tier == null) {
            tier = Customer.SubscriptionTier.BASIC;
        }

        return switch (tier) {
            case FREE -> 5;
            case BASIC -> 20;
            case PROFESSIONAL -> 100;
            case ENTERPRISE -> 10000; // 实际无限制,用大数表示
        };
    }
}
