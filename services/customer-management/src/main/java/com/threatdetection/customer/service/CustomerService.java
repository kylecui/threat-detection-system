package com.threatdetection.customer.service;

import com.threatdetection.customer.dto.CreateCustomerRequest;
import com.threatdetection.customer.dto.CustomerResponse;
import com.threatdetection.customer.dto.UpdateCustomerRequest;
import com.threatdetection.customer.exception.CustomerAlreadyExistsException;
import com.threatdetection.customer.exception.CustomerAdminProvisioningException;
import com.threatdetection.customer.exception.CustomerNotFoundException;
import com.threatdetection.customer.exception.CustomerOperationConflictException;
import com.threatdetection.customer.model.Customer;
import com.threatdetection.customer.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> CUSTOMER_DEPENDENCY_TABLES = List.of(
            "auth_users", "device_customer_mapping", "attack_events", "alerts",
            "threat_alerts", "threat_assessments", "apt_temporal_accumulations",
            "customer_notification_configs", "notification_rate_limits",
            "net_segment_weights", "customer_port_weights", "customer_time_weights",
            "attack_phase_port_configs", "attack_source_weights", "whitelist_config",
            "honeypot_sensitivity_weights", "config_assignments", "device_inventory",
            "device_status_history", "discovered_hosts", "topology_snapshots",
            "ml_customer_stats", "ml_predictions");

    public CustomerService(CustomerRepository customerRepository, JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建客户
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer: {}", request.getCustomerId());

        validateTenantCapacity(request.getTenantId());

        // 检查customerId是否已存在
        if (customerRepository.existsByCustomerId(request.getCustomerId())) {
            throw new CustomerAlreadyExistsException(
                "Customer with ID '" + request.getCustomerId() + "' already exists"
            );
        }

        // 构建客户实体
        Customer customer = Customer.builder()
                .customerId(request.getCustomerId())
                .tenantId(request.getTenantId())
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

        Customer savedCustomer = customerRepository.saveAndFlush(customer);

        if (Boolean.TRUE.equals(request.getCreateAdmin())) {
            provisionCustomerLogin(request, savedCustomer);
        }
        log.info("Customer created successfully: {}", savedCustomer.getCustomerId());

        return CustomerResponse.fromEntity(savedCustomer);
    }

    private void provisionCustomerLogin(CreateCustomerRequest request, Customer customer) {
        String expectedUsername = customer.getCustomerId() + "-admin";
        String username = request.getAdminUsername() == null
                ? "" : request.getAdminUsername().trim();
        String password = request.getAdminPassword();

        if (!expectedUsername.equals(username)) {
            throw new CustomerAdminProvisioningException(
                    "Customer administrator username must be '" + expectedUsername + "'");
        }
        if (!isValidCustomerPassword(password)) {
            throw new CustomerAdminProvisioningException(
                    "Customer administrator password must be 8-16 characters");
        }

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE lower(username) = lower(?)",
                Integer.class, username);
        if (existing != null && existing > 0) {
            throw new CustomerAdminProvisioningException(
                    "Customer administrator username already exists: " + username);
        }

        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO auth_users " +
                "(username, password_hash, display_name, email, customer_id, tenant_id, enabled, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                username,
                passwordEncoder.encode(password),
                customer.getName() + " Administrator",
                customer.getEmail(),
                customer.getCustomerId(),
                customer.getTenantId());
        if (userId == null) {
            throw new CustomerAdminProvisioningException(
                    "Customer administrator account was not created");
        }

        int assigned = jdbcTemplate.update(
                "INSERT INTO auth_user_roles (user_id, role_id) " +
                "SELECT ?, id FROM auth_roles WHERE name = 'CUSTOMER_ADMIN'",
                userId);
        if (assigned != 1) {
            throw new CustomerAdminProvisioningException(
                    "CUSTOMER_ADMIN role is unavailable; customer creation was rolled back");
        }
        log.info("Customer-scoped login provisioned atomically: customerId={}, username={}",
                customer.getCustomerId(), username);
    }

    private boolean isValidCustomerPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 16;
    }

    private void validateTenantCapacity(Long tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A tenant is required when creating a customer");
        }
        List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                "SELECT status, max_customers FROM tenants WHERE id = ? FOR UPDATE", tenantId);
        if (tenants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The selected tenant does not exist");
        }
        Map<String, Object> tenant = tenants.get(0);
        if (!"ACTIVE".equals(String.valueOf(tenant.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Customers can only be created under an active tenant");
        }
        int maxCustomers = ((Number) tenant.get("max_customers")).intValue();
        Long currentCustomers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE tenant_id = ? AND status <> 'INACTIVE'",
                Long.class, tenantId);
        if (currentCustomers != null && currentCustomers >= maxCustomers) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The selected tenant has reached its customer limit");
        }
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
    public Page<CustomerResponse> getAllCustomers(
            Long tenantId, String customerId, Pageable pageable) {
        log.debug("Fetching customers, tenantId: {}, customerId: {}, page: {}",
                tenantId, customerId, pageable.getPageNumber());

        if (customerId != null && !customerId.isBlank()) {
            return customerRepository.findByCustomerId(customerId, pageable)
                    .map(CustomerResponse::fromEntity);
        }

        if (tenantId != null) {
            return customerRepository.findByTenantId(tenantId, pageable)
                    .map(CustomerResponse::fromEntity);
        }

        return customerRepository.findAll(pageable)
                .map(CustomerResponse::fromEntity);
    }

    /**
     * 搜索客户 (支持名称、ID、邮箱模糊匹配)
     */
    public Page<CustomerResponse> searchCustomers(
            String keyword, Long tenantId, String customerId, Pageable pageable) {
        log.debug("Searching customers with keyword: {}, tenantId: {}, customerId: {}",
                keyword, tenantId, customerId);

        if (customerId != null && !customerId.isBlank()) {
            return customerRepository.searchCustomerById(
                            keyword, customerId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        if (tenantId != null) {
            return customerRepository.searchCustomersByTenant(
                            keyword, tenantId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.searchCustomers(keyword, pageable)
                .map(CustomerResponse::fromEntity);
    }

    /**
     * 根据状态查询客户
     */
    public Page<CustomerResponse> getCustomersByStatus(
            Customer.CustomerStatus status, Long tenantId, String customerId, Pageable pageable) {
        log.debug("Fetching customers by status: {}", status);

        if (customerId != null && !customerId.isBlank()) {
            return customerRepository.findByCustomerIdAndStatus(customerId, status, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        if (tenantId != null) {
            return customerRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.findByStatus(status, pageable)
                .map(CustomerResponse::fromEntity);
    }

    public List<CustomerResponse> getCustomersByTenant(Long tenantId) {
        log.debug("Fetching customers by tenantId: {}", tenantId);
        return customerRepository.findByTenantIdAndStatusNot(
                        tenantId, Customer.CustomerStatus.INACTIVE)
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

        if (customer.getStatus() != Customer.CustomerStatus.INACTIVE) {
            throw new CustomerOperationConflictException(
                    "Customer must be archived before permanent deletion", Map.of());
        }

        Map<String, Long> dependencies = findDependencies(customerId);
        if (!dependencies.isEmpty()) {
            throw new CustomerOperationConflictException(
                    "Customer still owns related data; archive or remove those records first",
                    dependencies);
        }

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

        long totalCustomers = customerRepository.countByStatusNot(Customer.CustomerStatus.INACTIVE);
        
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

    private Map<String, Long> findDependencies(String customerId) {
        Map<String, Long> dependencies = new LinkedHashMap<>();
        for (String table : CUSTOMER_DEPENDENCY_TABLES) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table);
            if (!Boolean.TRUE.equals(exists)) {
                continue;
            }
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE customer_id = ?",
                    Long.class, customerId);
            if (count != null && count > 0) {
                dependencies.put(table, count);
            }
        }
        return dependencies;
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
