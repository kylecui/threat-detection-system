package com.threatdetection.customer.controller;

import com.threatdetection.customer.dto.CreateCustomerRequest;
import com.threatdetection.customer.dto.CustomerResponse;
import com.threatdetection.customer.dto.UpdateCustomerRequest;
import com.threatdetection.customer.model.Customer;
import com.threatdetection.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客户管理REST API控制器
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 创建客户
     * 
     * POST /api/v1/customers
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        
        log.info("API: Creating customer: {}", request.getCustomerId());
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 检查客户ID是否存在
     * 
     * GET /api/v1/customers/{customerId}/exists
     */
    @GetMapping("/{customerId}/exists")
    public ResponseEntity<Map<String, Object>> checkCustomerExists(
            @PathVariable("customerId") String customerId) {
        
        log.info("API: Checking if customer exists: {}", customerId);
        boolean exists = customerService.existsById(customerId);
        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "exists", exists
        ));
    }

    /**
     * 获取单个客户的统计信息
     * 
     * GET /api/v1/customers/{customerId}/stats
     */
    @GetMapping("/{customerId}/stats")
    public ResponseEntity<Map<String, Object>> getCustomerStats(
            @PathVariable("customerId") String customerId) {
        
        log.info("API: Fetching stats for customer: {}", customerId);
        Map<String, Object> stats = customerService.getCustomerStats(customerId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取单个客户信息
     * 
     * GET /api/v1/customers/{customerId}
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(
            @PathVariable("customerId") String customerId) {
        
        log.info("API: Fetching customer: {}", customerId);
        CustomerResponse response = customerService.getCustomer(customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有客户列表 (分页)
     * 
     * GET /api/v1/customers?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        log.info("API: Fetching all customers, page={}, size={}", page, size);
        
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        
        Page<CustomerResponse> customers = customerService.getAllCustomers(pageable);
        return ResponseEntity.ok(customers);
    }

    /**
     * 搜索客户
     * 
     * GET /api/v1/customers/search?keyword=xxx&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<Page<CustomerResponse>> searchCustomers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("API: Searching customers with keyword: {}", keyword);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerResponse> customers = customerService.searchCustomers(keyword, pageable);
        return ResponseEntity.ok(customers);
    }

    /**
     * 按状态查询客户
     * 
     * GET /api/v1/customers/status/{status}?page=0&size=20
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<CustomerResponse>> getCustomersByStatus(
            @PathVariable Customer.CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("API: Fetching customers by status: {}", status);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerResponse> customers = customerService.getCustomersByStatus(status, pageable);
        return ResponseEntity.ok(customers);
    }

    /**
     * 更新客户信息
     * 
     * PUT /api/v1/customers/{customerId}
     */
    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody UpdateCustomerRequest request) {
        
        log.info("API: Updating customer: {}", customerId);
        CustomerResponse response = customerService.updateCustomer(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除客户 (软删除)
     * 
     * DELETE /api/v1/customers/{customerId}
     */
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> deleteCustomer(
            @PathVariable("customerId") String customerId,
            @RequestParam(required = false) Boolean permanent) {
        
        log.info("API: Deleting customer: {}, permanent={}", customerId, permanent);
        
        if (Boolean.TRUE.equals(permanent)) {
            customerService.hardDeleteCustomer(customerId);
        } else {
            customerService.deleteCustomer(customerId);
        }
        
        return ResponseEntity.noContent().build();
    }

    /**
     * 硬删除客户 (物理删除)
     * 
     * DELETE /api/v1/customers/{customerId}/hard
     */
    @DeleteMapping("/{customerId}/hard")
    public ResponseEntity<Void> hardDeleteCustomer(
            @PathVariable("customerId") String customerId) {
        
        log.warn("API: Hard deleting customer: {}", customerId);
        customerService.hardDeleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 部分更新客户信息
     * 
     * PATCH /api/v1/customers/{customerId}
     */
    @PatchMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> patchCustomer(
            @PathVariable("customerId") String customerId,
            @RequestBody Map<String, Object> updates) {
        
        log.info("API: Partially updating customer: {}", customerId);
        CustomerResponse response = customerService.patchCustomer(customerId, updates);
        return ResponseEntity.ok(response);
    }
}
