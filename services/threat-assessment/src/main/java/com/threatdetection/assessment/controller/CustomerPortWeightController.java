package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.model.CustomerPortWeight;
import com.threatdetection.assessment.service.CustomerPortWeightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户端口权重配置Controller
 * 
 * <p>提供端口权重配置的REST API接口
 * <p>支持多租户隔离，每个客户可以自定义端口权重
 * 
 * @author Security Team
 * @version 4.0
 */
@RestController
@RequestMapping("/api/port-weights")
@Tag(name = "Port Weights", description = "客户端口权重配置管理API")
public class CustomerPortWeightController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerPortWeightController.class);

    private final CustomerPortWeightService customerPortWeightService;

    public CustomerPortWeightController(CustomerPortWeightService customerPortWeightService) {
        this.customerPortWeightService = customerPortWeightService;
    }

    /**
     * 获取指定客户的所有端口权重配置
     */
    @GetMapping("/{customerId}")
    @Operation(summary = "获取客户的所有端口权重配置", description = "返回指定客户的所有启用的端口权重配置")
    public ResponseEntity<List<CustomerPortWeight>> getCustomerConfigs(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId) {
        
        logger.info("GET /api/port-weights/{}: Fetching port weight configs", customerId);
        
        List<CustomerPortWeight> configs = customerPortWeightService.getCustomerConfigs(customerId);
        
        logger.info("Found {} port weight configs for customer={}", configs.size(), customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取指定客户的所有配置 (包括禁用的)
     */
    @GetMapping("/{customerId}/all")
    @Operation(summary = "获取客户的所有配置", description = "返回指定客户的所有端口权重配置 (包括禁用的)")
    public ResponseEntity<List<CustomerPortWeight>> getAllCustomerConfigs(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId) {
        
        logger.info("GET /api/port-weights/{}/all: Fetching all port weight configs", customerId);
        
        List<CustomerPortWeight> configs = customerPortWeightService.getAllCustomerConfigs(customerId);
        
        logger.info("Found {} total port weight configs for customer={}", configs.size(), customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取指定端口的权重值
     */
    @GetMapping("/{customerId}/port/{portNumber}")
    @Operation(summary = "获取指定端口的权重", description = "返回指定端口的权重值 (优先级: 自定义 > 全局 > 默认)")
    public ResponseEntity<Map<String, Object>> getPortWeight(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号", required = true) 
            @PathVariable 
            @Min(1) @Max(65535) 
            int portNumber) {
        
        logger.info("GET /api/port-weights/{}/port/{}: Fetching port weight", customerId, portNumber);
        
        double weight = customerPortWeightService.getPortWeight(customerId, portNumber);
        
        Map<String, Object> response = Map.of(
            "customerId", customerId,
            "portNumber", portNumber,
            "weight", weight
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 批量获取端口权重
     */
    @PostMapping("/{customerId}/batch")
    @Operation(summary = "批量获取端口权重", description = "批量查询多个端口的权重值")
    public ResponseEntity<Map<Integer, Double>> getPortWeightsBatch(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号列表", required = true) 
            @RequestBody List<Integer> portNumbers) {
        
        logger.info("POST /api/port-weights/{}/batch: Fetching {} port weights", 
            customerId, portNumbers.size());
        
        Map<Integer, Double> weights = customerPortWeightService.getPortWeightsBatch(
            customerId, portNumbers);
        
        return ResponseEntity.ok(weights);
    }

    /**
     * 创建或更新端口权重配置
     */
    @PostMapping("/{customerId}")
    @Operation(summary = "创建端口权重配置", description = "为指定客户创建新的端口权重配置")
    public ResponseEntity<CustomerPortWeight> createConfig(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口权重配置", required = true) 
            @Valid @RequestBody CustomerPortWeight config) {
        
        logger.info("POST /api/port-weights/{}: Creating port weight config for port={}", 
            customerId, config.getPortNumber());
        
        // 设置客户ID
        config.setCustomerId(customerId);
        
        CustomerPortWeight savedConfig = customerPortWeightService.saveConfig(config);
        
        logger.info("Created port weight config: id={}, customer={}, port={}, weight={}", 
            savedConfig.getId(), customerId, savedConfig.getPortNumber(), savedConfig.getWeight());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedConfig);
    }

    /**
     * 批量导入端口权重配置
     */
    @PostMapping("/{customerId}/import")
    @Operation(summary = "批量导入端口权重配置", description = "批量导入多个端口权重配置")
    public ResponseEntity<List<CustomerPortWeight>> batchImport(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口权重配置列表", required = true) 
            @RequestBody List<CustomerPortWeight> configs) {
        
        logger.info("POST /api/port-weights/{}/import: Importing {} configs", 
            customerId, configs.size());
        
        // 设置客户ID
        configs.forEach(config -> config.setCustomerId(customerId));
        
        List<CustomerPortWeight> savedConfigs = customerPortWeightService.batchImport(configs);
        
        logger.info("Successfully imported {} port weight configs for customer={}", 
            savedConfigs.size(), customerId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedConfigs);
    }

    /**
     * 更新端口权重
     */
    @PutMapping("/{customerId}/port/{portNumber}")
    @Operation(summary = "更新端口权重", description = "更新指定端口的权重值")
    public ResponseEntity<CustomerPortWeight> updateWeight(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号", required = true) 
            @PathVariable @Min(1) @Max(65535) int portNumber,
            
            @Parameter(description = "新权重值", required = true) 
            @RequestParam @DecimalMin("0.5") @DecimalMax("10.0") double weight,
            
            @Parameter(description = "更新人") 
            @RequestParam(required = false, defaultValue = "system") String updatedBy) {
        
        logger.info("PUT /api/port-weights/{}/port/{}: Updating weight to {}", 
            customerId, portNumber, weight);
        
        CustomerPortWeight updatedConfig = customerPortWeightService.updateWeight(
            customerId, portNumber, weight, updatedBy);
        
        return ResponseEntity.ok(updatedConfig);
    }

    /**
     * 删除端口权重配置
     */
    @DeleteMapping("/{customerId}/port/{portNumber}")
    @Operation(summary = "删除端口权重配置", description = "删除指定端口的权重配置")
    public ResponseEntity<Void> deleteConfig(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号", required = true) 
            @PathVariable @Min(1) @Max(65535) int portNumber) {
        
        logger.info("DELETE /api/port-weights/{}/port/{}: Deleting port weight config", 
            customerId, portNumber);
        
        customerPortWeightService.deleteConfig(customerId, portNumber);
        
        logger.info("Successfully deleted port weight config: customer={}, port={}", 
            customerId, portNumber);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除客户的所有配置
     */
    @DeleteMapping("/{customerId}")
    @Operation(summary = "删除客户的所有配置", description = "删除指定客户的所有端口权重配置")
    public ResponseEntity<Void> deleteAllCustomerConfigs(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId) {
        
        logger.info("DELETE /api/port-weights/{}: Deleting all port weight configs", customerId);
        
        customerPortWeightService.deleteAllCustomerConfigs(customerId);
        
        logger.info("Successfully deleted all port weight configs for customer={}", customerId);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用或禁用端口配置
     */
    @PatchMapping("/{customerId}/port/{portNumber}/enabled")
    @Operation(summary = "启用或禁用端口配置", description = "修改端口配置的启用状态")
    public ResponseEntity<CustomerPortWeight> setEnabled(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号", required = true) 
            @PathVariable @Min(1) @Max(65535) int portNumber,
            
            @Parameter(description = "启用状态", required = true) 
            @RequestParam boolean enabled) {
        
        logger.info("PATCH /api/port-weights/{}/port/{}/enabled: Setting enabled={}", 
            customerId, portNumber, enabled);
        
        CustomerPortWeight updatedConfig = customerPortWeightService.setEnabled(
            customerId, portNumber, enabled);
        
        return ResponseEntity.ok(updatedConfig);
    }

    /**
     * 获取客户的统计信息
     */
    @GetMapping("/{customerId}/statistics")
    @Operation(summary = "获取统计信息", description = "获取客户端口权重配置的统计信息")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId) {
        
        logger.info("GET /api/port-weights/{}/statistics: Fetching statistics", customerId);
        
        Map<String, Object> stats = customerPortWeightService.getStatistics(customerId);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取高优先级配置
     */
    @GetMapping("/{customerId}/high-priority")
    @Operation(summary = "获取高优先级配置", description = "获取优先级高于指定值的端口配置")
    public ResponseEntity<List<CustomerPortWeight>> getHighPriorityConfigs(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "最小优先级", required = false) 
            @RequestParam(required = false, defaultValue = "50") 
            @Min(0) @Max(100) int minPriority) {
        
        logger.info("GET /api/port-weights/{}/high-priority: Fetching configs with priority >= {}", 
            customerId, minPriority);
        
        List<CustomerPortWeight> configs = customerPortWeightService.getHighPriorityConfigs(
            customerId, minPriority);
        
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取高权重端口
     */
    @GetMapping("/{customerId}/high-weight")
    @Operation(summary = "获取高权重端口", description = "获取权重高于指定值的端口配置")
    public ResponseEntity<List<CustomerPortWeight>> getHighWeightPorts(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "最小权重", required = false) 
            @RequestParam(required = false, defaultValue = "5.0") 
            @DecimalMin("0.5") @DecimalMax("10.0") double minWeight) {
        
        logger.info("GET /api/port-weights/{}/high-weight: Fetching ports with weight >= {}", 
            customerId, minWeight);
        
        List<CustomerPortWeight> configs = customerPortWeightService.getHighWeightPorts(
            customerId, minWeight);
        
        return ResponseEntity.ok(configs);
    }

    /**
     * 按风险等级查询
     */
    @GetMapping("/{customerId}/risk-level/{riskLevel}")
    @Operation(summary = "按风险等级查询", description = "获取指定风险等级的端口配置")
    public ResponseEntity<List<CustomerPortWeight>> getByRiskLevel(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "风险等级 (LOW/MEDIUM/HIGH/CRITICAL)", required = true) 
            @PathVariable String riskLevel) {
        
        logger.info("GET /api/port-weights/{}/risk-level/{}: Fetching configs", 
            customerId, riskLevel);
        
        List<CustomerPortWeight> configs = customerPortWeightService.getByRiskLevel(
            customerId, riskLevel.toUpperCase());
        
        return ResponseEntity.ok(configs);
    }

    /**
     * 检查配置是否存在
     */
    @GetMapping("/{customerId}/port/{portNumber}/exists")
    @Operation(summary = "检查配置是否存在", description = "检查指定端口是否已有配置")
    public ResponseEntity<Map<String, Object>> configExists(
            @Parameter(description = "客户ID", required = true) 
            @PathVariable String customerId,
            
            @Parameter(description = "端口号", required = true) 
            @PathVariable @Min(1) @Max(65535) int portNumber) {
        
        boolean exists = customerPortWeightService.configExists(customerId, portNumber);
        
        Map<String, Object> response = Map.of(
            "customerId", customerId,
            "portNumber", portNumber,
            "exists", exists
        );
        
        return ResponseEntity.ok(response);
    }
}
