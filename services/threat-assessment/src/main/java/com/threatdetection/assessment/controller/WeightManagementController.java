package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.service.*;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 权重管理Controller
 * 
 * <p>提供多租户权重配置管理的REST API接口
 * <p>支持四种权重类型：攻击源权重、蜜罐敏感度权重、攻击阶段端口配置、APT时序累积
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@RestController
@RequestMapping("/api/v1/weights")
@Tag(name = "Weight Management", description = "多租户权重配置管理API")
public class WeightManagementController {

    private static final Logger logger = LoggerFactory.getLogger(WeightManagementController.class);

    private final AttackSourceWeightService attackSourceWeightService;
    private final HoneypotSensitivityWeightService honeypotSensitivityWeightService;
    private final AttackPhasePortConfigService attackPhasePortConfigService;
    private final AptTemporalAccumulationService aptTemporalAccumulationService;

    public WeightManagementController(
            AttackSourceWeightService attackSourceWeightService,
            HoneypotSensitivityWeightService honeypotSensitivityWeightService,
            AttackPhasePortConfigService attackPhasePortConfigService,
            AptTemporalAccumulationService aptTemporalAccumulationService) {
        this.attackSourceWeightService = attackSourceWeightService;
        this.honeypotSensitivityWeightService = honeypotSensitivityWeightService;
        this.attackPhasePortConfigService = attackPhasePortConfigService;
        this.aptTemporalAccumulationService = aptTemporalAccumulationService;
    }

    // ==================== 攻击源权重管理 ====================

    /**
     * 获取指定客户的攻击源权重配置
     */
    @GetMapping("/attack-source/{customerId}")
    @Operation(summary = "获取攻击源权重配置", description = "返回指定客户的所有攻击源权重配置")
    public ResponseEntity<List<AttackSourceWeightDto>> getAttackSourceWeights(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/attack-source/{}: Fetching attack source weights", customerId);

        List<AttackSourceWeightDto> configs = attackSourceWeightService.findByCustomerId(customerId);

        logger.info("Found {} attack source weight configs for customer={}", configs.size(), customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取指定客户的活跃攻击源权重配置
     */
    @GetMapping("/attack-source/{customerId}/active")
    @Operation(summary = "获取活跃的攻击源权重配置", description = "返回指定客户的所有活跃攻击源权重配置")
    public ResponseEntity<List<AttackSourceWeightDto>> getActiveAttackSourceWeights(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/attack-source/{}/active: Fetching active configs", customerId);

        List<AttackSourceWeightDto> configs = attackSourceWeightService.findByCustomerIdAndActive(customerId);

        return ResponseEntity.ok(configs);
    }

    /**
     * 创建或更新攻击源权重配置
     */
    @PostMapping("/attack-source")
    @Operation(summary = "创建攻击源权重配置", description = "为指定客户创建或更新攻击源权重配置")
    public ResponseEntity<AttackSourceWeightDto> saveAttackSourceWeight(
            @Parameter(description = "攻击源权重配置", required = true)
            @Valid @RequestBody AttackSourceWeightDto dto) {

        logger.info("POST /api/v1/weights/attack-source: Saving config for customer={}, ipSegment={}",
                   dto.getCustomerId(), dto.getIpSegment());

        AttackSourceWeightDto saved = attackSourceWeightService.save(dto);

        logger.info("Saved attack source weight config: id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 删除攻击源权重配置
     */
    @DeleteMapping("/attack-source/{customerId}")
    @Operation(summary = "删除攻击源权重配置", description = "删除指定客户的指定IP段的权重配置")
    public ResponseEntity<Void> deleteAttackSourceWeight(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "IP段标识", required = true)
            @RequestParam String ipSegment) {

        logger.info("DELETE /api/v1/weights/attack-source/{}/{}: Deleting config", customerId, ipSegment);

        boolean deleted = attackSourceWeightService.delete(customerId, ipSegment);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 启用攻击源权重配置
     */
    @PatchMapping("/attack-source/{customerId}/enable")
    @Operation(summary = "启用攻击源权重配置", description = "启用指定客户的指定IP段的权重配置")
    public ResponseEntity<Void> enableAttackSourceWeight(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "IP段标识", required = true)
            @RequestParam String ipSegment) {

        logger.info("PATCH /api/v1/weights/attack-source/{}/{}/enable: Enabling config", customerId, ipSegment);

        boolean enabled = attackSourceWeightService.enable(customerId, ipSegment);

        if (enabled) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用攻击源权重配置
     */
    @PatchMapping("/attack-source/{customerId}/disable")
    @Operation(summary = "禁用攻击源权重配置", description = "禁用指定客户的指定IP段的权重配置")
    public ResponseEntity<Void> disableAttackSourceWeight(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "IP段标识", required = true)
            @RequestParam String ipSegment) {

        logger.info("PATCH /api/v1/weights/attack-source/{}/{}/disable: Disabling config", customerId, ipSegment);

        boolean disabled = attackSourceWeightService.disable(customerId, ipSegment);

        if (disabled) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 蜜罐敏感度权重管理 ====================

    /**
     * 获取指定客户的蜜罐敏感度权重配置
     */
    @GetMapping("/honeypot-sensitivity/{customerId}")
    @Operation(summary = "获取蜜罐敏感度权重配置", description = "返回指定客户的所有蜜罐敏感度权重配置")
    public ResponseEntity<List<HoneypotSensitivityWeightDto>> getHoneypotSensitivityWeights(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/honeypot-sensitivity/{}: Fetching honeypot sensitivity weights", customerId);

        List<HoneypotSensitivityWeightDto> configs = honeypotSensitivityWeightService.findByCustomerId(customerId);

        logger.info("Found {} honeypot sensitivity weight configs for customer={}", configs.size(), customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 创建或更新蜜罐敏感度权重配置
     */
    @PostMapping("/honeypot-sensitivity")
    @Operation(summary = "创建蜜罐敏感度权重配置", description = "为指定客户创建或更新蜜罐敏感度权重配置")
    public ResponseEntity<HoneypotSensitivityWeightDto> saveHoneypotSensitivityWeight(
            @Parameter(description = "蜜罐敏感度权重配置", required = true)
            @Valid @RequestBody HoneypotSensitivityWeightDto dto) {

        logger.info("POST /api/v1/weights/honeypot-sensitivity: Saving config for customer={}, ipSegment={}",
                   dto.getCustomerId(), dto.getIpSegment());

        HoneypotSensitivityWeightDto saved = honeypotSensitivityWeightService.save(dto);

        logger.info("Saved honeypot sensitivity weight config: id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 删除蜜罐敏感度权重配置
     */
    @DeleteMapping("/honeypot-sensitivity/{customerId}")
    @Operation(summary = "删除蜜罐敏感度权重配置", description = "删除指定客户的指定IP段的蜜罐敏感度权重配置")
    public ResponseEntity<Void> deleteHoneypotSensitivityWeight(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "IP段标识", required = true)
            @RequestParam String ipSegment) {

        logger.info("DELETE /api/v1/weights/honeypot-sensitivity/{}/{}: Deleting config", customerId, ipSegment);

        boolean deleted = honeypotSensitivityWeightService.delete(customerId, ipSegment);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 攻击阶段端口配置管理 ====================

    /**
     * 获取指定客户的攻击阶段端口配置
     */
    @GetMapping("/attack-phase/{customerId}")
    @Operation(summary = "获取攻击阶段端口配置", description = "返回指定客户的所有攻击阶段端口配置")
    public ResponseEntity<List<AttackPhasePortConfigDto>> getAttackPhasePortConfigs(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/attack-phase/{}: Fetching attack phase port configs", customerId);

        List<AttackPhasePortConfigDto> configs = attackPhasePortConfigService.findByCustomerId(customerId);

        logger.info("Found {} attack phase port configs for customer={}", configs.size(), customerId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取指定客户的指定阶段配置
     */
    @GetMapping("/attack-phase/{customerId}/{phase}")
    @Operation(summary = "获取指定阶段的端口配置", description = "返回指定客户和阶段的所有端口配置")
    public ResponseEntity<List<AttackPhasePortConfigDto>> getAttackPhasePortConfigsByPhase(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击阶段", required = true)
            @PathVariable String phase) {

        logger.info("GET /api/v1/weights/attack-phase/{}/{}: Fetching configs by phase", customerId, phase);

        List<AttackPhasePortConfigDto> configs = attackPhasePortConfigService.findByCustomerIdAndPhase(customerId, phase);

        return ResponseEntity.ok(configs);
    }

    /**
     * 获取有效的配置（客户自定义 + 全局默认）
     */
    @GetMapping("/attack-phase/{customerId}/{phase}/effective")
    @Operation(summary = "获取有效的端口配置", description = "返回客户自定义配置，不存在时返回全局默认配置")
    public ResponseEntity<List<AttackPhasePortConfigDto>> getEffectiveAttackPhasePortConfigs(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击阶段", required = true)
            @PathVariable String phase) {

        logger.info("GET /api/v1/weights/attack-phase/{}/{}/effective: Fetching effective configs", customerId, phase);

        List<AttackPhasePortConfigDto> configs = attackPhasePortConfigService.getEffectiveConfigs(customerId, phase);

        return ResponseEntity.ok(configs);
    }

    /**
     * 创建或更新攻击阶段端口配置
     */
    @PostMapping("/attack-phase")
    @Operation(summary = "创建攻击阶段端口配置", description = "为指定客户创建或更新攻击阶段端口配置")
    public ResponseEntity<AttackPhasePortConfigDto> saveAttackPhasePortConfig(
            @Parameter(description = "攻击阶段端口配置", required = true)
            @Valid @RequestBody AttackPhasePortConfigDto dto) {

        logger.info("POST /api/v1/weights/attack-phase: Saving config for customer={}, phase={}, port={}",
                   dto.getCustomerId(), dto.getPhase(), dto.getPort());

        AttackPhasePortConfigDto saved = attackPhasePortConfigService.save(dto);

        logger.info("Saved attack phase port config: id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 删除攻击阶段端口配置
     */
    @DeleteMapping("/attack-phase/{customerId}/{phase}/{port}")
    @Operation(summary = "删除攻击阶段端口配置", description = "删除指定客户、阶段和端口的配置")
    public ResponseEntity<Void> deleteAttackPhasePortConfig(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击阶段", required = true)
            @PathVariable String phase,

            @Parameter(description = "端口号", required = true)
            @PathVariable @Min(1) @Max(65535) Integer port) {

        logger.info("DELETE /api/v1/weights/attack-phase/{}/{}/{}: Deleting config", customerId, phase, port);

        boolean deleted = attackPhasePortConfigService.delete(customerId, phase, port);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== APT时序累积管理 ====================

    /**
     * 获取指定客户的APT时序累积数据
     */
    @GetMapping("/apt-temporal/{customerId}")
    @Operation(summary = "获取APT时序累积数据", description = "返回指定客户的所有APT时序累积数据")
    public ResponseEntity<List<AptTemporalAccumulationDto>> getAptTemporalAccumulations(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/apt-temporal/{}: Fetching APT temporal accumulations", customerId);

        List<AptTemporalAccumulationDto> accumulations = aptTemporalAccumulationService.findByCustomerId(customerId);

        logger.info("Found {} APT temporal accumulations for customer={}", accumulations.size(), customerId);
        return ResponseEntity.ok(accumulations);
    }

    /**
     * 获取指定MAC地址的时序累积数据
     */
    @GetMapping("/apt-temporal/{customerId}/{attackMac}")
    @Operation(summary = "获取指定MAC的时序累积数据", description = "返回指定客户和MAC地址的所有时序累积数据")
    public ResponseEntity<List<AptTemporalAccumulationDto>> getAptTemporalAccumulationsByMac(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击者MAC地址", required = true)
            @PathVariable String attackMac) {

        logger.info("GET /api/v1/weights/apt-temporal/{}/{}: Fetching accumulations by MAC", customerId, attackMac);

        List<AptTemporalAccumulationDto> accumulations = aptTemporalAccumulationService.findByCustomerIdAndAttackMac(customerId, attackMac);

        return ResponseEntity.ok(accumulations);
    }

    /**
     * 获取时间范围内的时序累积数据
     */
    @GetMapping("/apt-temporal/{customerId}/range")
    @Operation(summary = "获取时间范围内的时序累积数据", description = "返回指定时间范围内的所有时序累积数据")
    public ResponseEntity<List<AptTemporalAccumulationDto>> getAptTemporalAccumulationsByTimeRange(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "开始时间", required = true)
            @RequestParam Instant startTime,

            @Parameter(description = "结束时间", required = true)
            @RequestParam Instant endTime) {

        logger.info("GET /api/v1/weights/apt-temporal/{}/range: Fetching accumulations by time range {}-{}",
                   customerId, startTime, endTime);

        List<AptTemporalAccumulationDto> accumulations = aptTemporalAccumulationService.findByCustomerIdAndTimeRange(customerId, startTime, endTime);

        return ResponseEntity.ok(accumulations);
    }

    /**
     * 创建或更新APT时序累积数据
     */
    @PostMapping("/apt-temporal")
    @Operation(summary = "创建APT时序累积数据", description = "为指定客户创建或更新APT时序累积数据")
    public ResponseEntity<AptTemporalAccumulationDto> saveAptTemporalAccumulation(
            @Parameter(description = "APT时序累积数据", required = true)
            @Valid @RequestBody AptTemporalAccumulationDto dto) {

        logger.info("POST /api/v1/weights/apt-temporal: Saving accumulation for customer={}, attackMac={}, windowStart={}",
                   dto.getCustomerId(), dto.getAttackMac(), dto.getWindowStart());

        AptTemporalAccumulationDto saved = aptTemporalAccumulationService.save(dto);

        logger.info("Saved APT temporal accumulation: id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 更新累积分数
     */
    @PutMapping("/apt-temporal/{customerId}/{attackMac}")
    @Operation(summary = "更新累积分数", description = "更新指定客户和MAC地址的累积分数")
    public ResponseEntity<AptTemporalAccumulationDto> updateAccumulation(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击者MAC地址", required = true)
            @PathVariable String attackMac,

            @Parameter(description = "窗口开始时间", required = true)
            @RequestParam Instant windowStart,

            @Parameter(description = "累积分数", required = true)
            @RequestParam @DecimalMin("0.0") @DecimalMax("10000.0") BigDecimal accumulatedScore,

            @Parameter(description = "衰减累积分数", required = true)
            @RequestParam @DecimalMin("0.0") @DecimalMax("10000.0") BigDecimal decayAccumulatedScore) {

        logger.info("PUT /api/v1/weights/apt-temporal/{}/{}: Updating accumulation scores", customerId, attackMac);

        AptTemporalAccumulationDto updated = aptTemporalAccumulationService.updateAccumulation(
            customerId, attackMac, windowStart, accumulatedScore, decayAccumulatedScore);

        return ResponseEntity.ok(updated);
    }

    /**
     * 删除APT时序累积数据
     */
    @DeleteMapping("/apt-temporal/{customerId}/{attackMac}")
    @Operation(summary = "删除APT时序累积数据", description = "删除指定客户和MAC地址的时序累积数据")
    public ResponseEntity<Void> deleteAptTemporalAccumulation(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击者MAC地址", required = true)
            @PathVariable String attackMac,

            @Parameter(description = "窗口开始时间", required = true)
            @RequestParam Instant windowStart) {

        logger.info("DELETE /api/v1/weights/apt-temporal/{}/{}: Deleting accumulation", customerId, attackMac);

        boolean deleted = aptTemporalAccumulationService.delete(customerId, attackMac, windowStart);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取当前威胁分数
     */
    @GetMapping("/apt-temporal/{customerId}/{attackMac}/threat-score")
    @Operation(summary = "获取当前威胁分数", description = "计算指定MAC地址的当前累积威胁分数")
    public ResponseEntity<Map<String, Object>> getCurrentThreatScore(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId,

            @Parameter(description = "攻击者MAC地址", required = true)
            @PathVariable String attackMac) {

        logger.info("GET /api/v1/weights/apt-temporal/{}/{}/threat-score: Calculating current threat score", customerId, attackMac);

        BigDecimal threatScore = aptTemporalAccumulationService.calculateCurrentThreatScore(customerId, attackMac);

        Map<String, Object> response = Map.of(
            "customerId", customerId,
            "attackMac", attackMac,
            "threatScore", threatScore,
            "calculatedAt", Instant.now()
        );

        return ResponseEntity.ok(response);
    }

    // ==================== 统计信息 ====================

    /**
     * 获取攻击源权重统计信息
     */
    @GetMapping("/attack-source/{customerId}/statistics")
    @Operation(summary = "获取攻击源权重统计", description = "返回指定客户的攻击源权重配置统计信息")
    public ResponseEntity<Object[]> getAttackSourceWeightStatistics(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/attack-source/{}/statistics: Fetching statistics", customerId);

        Object[] stats = attackSourceWeightService.getStatistics(customerId);

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取蜜罐敏感度权重统计信息
     */
    @GetMapping("/honeypot-sensitivity/{customerId}/statistics")
    @Operation(summary = "获取蜜罐敏感度权重统计", description = "返回指定客户的蜜罐敏感度权重配置统计信息")
    public ResponseEntity<Object[]> getHoneypotSensitivityWeightStatistics(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/honeypot-sensitivity/{}/statistics: Fetching statistics", customerId);

        Object[] stats = honeypotSensitivityWeightService.getStatistics(customerId);

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取攻击阶段端口配置统计信息
     */
    @GetMapping("/attack-phase/{customerId}/statistics")
    @Operation(summary = "获取攻击阶段端口配置统计", description = "返回指定客户的攻击阶段端口配置统计信息")
    public ResponseEntity<Object[]> getAttackPhasePortConfigStatistics(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/attack-phase/{}/statistics: Fetching statistics", customerId);

        Object[] stats = attackPhasePortConfigService.getStatistics(customerId);

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取APT时序累积统计信息
     */
    @GetMapping("/apt-temporal/{customerId}/statistics")
    @Operation(summary = "获取APT时序累积统计", description = "返回指定客户的APT时序累积数据统计信息")
    public ResponseEntity<Object[]> getAptTemporalAccumulationStatistics(
            @Parameter(description = "客户ID", required = true)
            @PathVariable String customerId) {

        logger.info("GET /api/v1/weights/apt-temporal/{}/statistics: Fetching statistics", customerId);

        Object[] stats = aptTemporalAccumulationService.getStatistics(customerId);

        return ResponseEntity.ok(stats);
    }
}
