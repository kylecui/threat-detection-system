package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.model.CustomerTimeWeight;
import com.threatdetection.assessment.service.CustomerTimeWeightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户时间段权重配置API控制器
 *
 * <p>提供RESTful API用于管理客户的时间权重配置
 * <p>支持多租户隔离和完整的CRUD操作
 *
 * @author Security Team
 * @version 5.0
 */
@Slf4j
@RestController
@RequestMapping("/api/time-weights")
@RequiredArgsConstructor
@Tag(name = "Time Weight Configuration", description = "客户时间段权重配置管理API")
public class CustomerTimeWeightController {

    private final CustomerTimeWeightService service;

    /**
     * 获取客户的所有时间权重配置
     */
    @GetMapping("/customer/{customerId}")
    @Operation(summary = "获取客户时间权重配置", description = "获取指定客户的所有时间权重配置")
    public ResponseEntity<List<CustomerTimeWeight>> getByCustomerId(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        log.info("Getting time weight configs for customer: {}", customerId);
        List<CustomerTimeWeight> configs = service.getAllByCustomerId(customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取客户启用的时间权重配置
     */
    @GetMapping("/customer/{customerId}/enabled")
    @Operation(summary = "获取客户启用的时间权重配置", description = "获取指定客户所有启用的时间权重配置")
    public ResponseEntity<List<CustomerTimeWeight>> getEnabledByCustomerId(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        log.info("Getting enabled time weight configs for customer: {}", customerId);
        List<CustomerTimeWeight> configs = service.getEnabledByCustomerId(customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 创建时间权重配置
     */
    @PostMapping
    @Operation(summary = "创建时间权重配置", description = "为客户创建新的时间权重配置")
    public ResponseEntity<CustomerTimeWeight> create(
            @Parameter(description = "时间权重配置", required = true)
            @Valid @RequestBody CustomerTimeWeight weight) {

        log.info("Creating time weight config for customer: {}", weight.getCustomerId());
        CustomerTimeWeight created = service.create(weight);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 批量创建时间权重配置
     */
    @PostMapping("/batch")
    @Operation(summary = "批量创建时间权重配置", description = "为客户批量创建时间权重配置")
    public ResponseEntity<List<CustomerTimeWeight>> createBatch(
            @Parameter(description = "时间权重配置列表", required = true)
            @Valid @RequestBody List<CustomerTimeWeight> weights) {

        if (weights.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String customerId = weights.get(0).getCustomerId();
        log.info("Batch creating {} time weight configs for customer: {}", weights.size(), customerId);

        List<CustomerTimeWeight> created = service.createBatch(weights);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 更新时间权重配置
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新时间权重配置", description = "更新指定的时间权重配置")
    public ResponseEntity<CustomerTimeWeight> update(
            @Parameter(description = "配置ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "更新的配置", required = true)
            @Valid @RequestBody CustomerTimeWeight update) {

        log.info("Updating time weight config: id={}", id);
        CustomerTimeWeight updated = service.update(id, update);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除时间权重配置
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除时间权重配置", description = "删除指定的时间权重配置")
    public ResponseEntity<Void> delete(
            @Parameter(description = "配置ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting time weight config: id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除客户的所有配置
     */
    @DeleteMapping("/customer/{customerId}")
    @Operation(summary = "删除客户所有配置", description = "删除指定客户的所有时间权重配置")
    public ResponseEntity<Void> deleteByCustomerId(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        log.info("Deleting all time weight configs for customer: {}", customerId);
        service.deleteByCustomerId(customerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用/禁用配置
     */
    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用/禁用配置", description = "启用或禁用指定的时间权重配置")
    public ResponseEntity<CustomerTimeWeight> toggleEnabled(
            @Parameter(description = "配置ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "启用状态", required = true)
            @RequestParam boolean enabled) {

        log.info("Setting time weight config enabled={} for id: {}", enabled, id);
        CustomerTimeWeight updated = service.toggleEnabled(id, enabled);
        return ResponseEntity.ok(updated);
    }

    /**
     * 初始化客户的默认配置
     */
    @PostMapping("/customer/{customerId}/initialize")
    @Operation(summary = "初始化默认配置", description = "为客户初始化默认的时间权重配置")
    public ResponseEntity<Void> initializeDefaults(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        log.info("Initializing default time weight configs for customer: {}", customerId);
        service.initializeDefaultWeights(customerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 获取配置统计信息
     */
    @GetMapping("/customer/{customerId}/statistics")
    @Operation(summary = "获取配置统计", description = "获取指定客户的配置统计信息")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        log.info("Getting statistics for customer: {}", customerId);
        Map<String, Object> stats = service.getStatistics(customerId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 批量更新权重
     */
    @PatchMapping("/batch/weights")
    @Operation(summary = "批量更新权重", description = "批量更新多个配置的权重值")
    public ResponseEntity<List<CustomerTimeWeight>> batchUpdateWeights(
            @Parameter(description = "权重更新请求", required = true)
            @RequestBody List<WeightUpdateRequest> updates) {

        log.info("Batch updating {} time weight configs", updates.size());

        List<CustomerTimeWeight> results = updates.stream()
            .map(update -> {
                CustomerTimeWeight weight = new CustomerTimeWeight();
                weight.setId(update.getId());
                weight.setWeight(update.getWeight());
                weight.setUpdatedBy("system");
                return service.update(update.getId(), weight);
            })
            .toList();

        return ResponseEntity.ok(results);
    }

    /**
     * 批量启用/禁用配置
     */
    @PatchMapping("/batch/enabled")
    @Operation(summary = "批量启用/禁用", description = "批量启用或禁用多个配置")
    public ResponseEntity<List<CustomerTimeWeight>> batchToggleEnabled(
            @Parameter(description = "启用状态更新请求", required = true)
            @RequestBody List<EnabledUpdateRequest> updates) {

        log.info("Batch toggling enabled status for {} configs", updates.size());

        List<CustomerTimeWeight> results = updates.stream()
            .map(update -> service.toggleEnabled(update.getId(), update.isEnabled()))
            .toList();

        return ResponseEntity.ok(results);
    }

    // Inner classes for request DTOs
    public static class WeightUpdateRequest {
        private Long id;
        private Double weight;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
    }

    public static class EnabledUpdateRequest {
        private Long id;
        private boolean enabled;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}