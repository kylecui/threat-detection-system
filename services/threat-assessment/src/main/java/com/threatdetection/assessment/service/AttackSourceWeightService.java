package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AttackSourceWeightDto;
import com.threatdetection.assessment.model.AttackSourceWeight;
import com.threatdetection.assessment.repository.AttackSourceWeightRepositoryNew;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 攻击源网段权重配置服务
 * 
 * <p>提供多租户攻击源权重配置管理功能
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackSourceWeightService {
    
    private final AttackSourceWeightRepositoryNew repository;
    
    /**
     * 保存配置 (upsert操作)
     * 如果配置已存在则更新，否则创建新配置
     * 
     * @param dto 配置DTO
     * @return 保存的配置DTO
     */
    @Transactional
    public AttackSourceWeightDto save(AttackSourceWeightDto dto) {
        log.info("Saving attack source weight config: customerId={}, ipSegment={}", 
                 dto.getCustomerId(), dto.getIpSegment());
        
        // 检查是否已存在
        Optional<AttackSourceWeight> existingOpt = repository.findByCustomerIdAndIpSegment(
            dto.getCustomerId(), dto.getIpSegment());
        
        AttackSourceWeight entity;
        if (existingOpt.isPresent()) {
            // 更新现有记录
            entity = existingOpt.get();
            entity.setAttackSourceWeight(dto.getAttackSourceWeight());
            entity.setDescription(dto.getDescription());
            if (dto.getIsActive() != null) {
                entity.setIsActive(dto.getIsActive());
            }
            log.info("Updating existing attack source weight config: id={}", entity.getId());
        } else {
            // 创建新记录
            entity = convertToEntity(dto);
            log.info("Creating new attack source weight config");
        }
        
        AttackSourceWeight saved = repository.save(entity);
        
        log.info("Successfully saved attack source weight config: id={}", saved.getId());
        return convertToDto(saved);
    }
    
    /**
     * 根据客户ID和IP段查询配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 配置DTO (Optional)
     */
    public Optional<AttackSourceWeightDto> findByCustomerIdAndIpSegment(String customerId, String ipSegment) {
        return repository.findByCustomerIdAndIpSegment(customerId, ipSegment)
                .map(this::convertToDto);
    }
    
    /**
     * 查询客户的所有启用配置
     * 
     * @param customerId 客户ID
     * @return 配置DTO列表
     */
    public List<AttackSourceWeightDto> findByCustomerIdAndActive(String customerId) {
        return repository.findByCustomerIdAndIsActive(customerId, true)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 查询客户的所有配置 (包括禁用的)
     * 
     * @param customerId 客户ID
     * @return 配置DTO列表
     */
    public List<AttackSourceWeightDto> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 查询高危配置
     * 
     * @param customerId 客户ID
     * @param threshold 权重阈值
     * @return 高危配置DTO列表
     */
    public List<AttackSourceWeightDto> findHighRiskConfigs(String customerId, BigDecimal threshold) {
        return repository.findHighRiskConfigs(customerId, threshold)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 启用配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 是否成功
     */
    @Transactional
    public boolean enable(String customerId, String ipSegment) {
        Optional<AttackSourceWeight> entityOpt = repository.findByCustomerIdAndIpSegment(customerId, ipSegment);
        if (entityOpt.isPresent()) {
            AttackSourceWeight entity = entityOpt.get();
            entity.setIsActive(true);
            repository.save(entity);
            log.info("Enabled attack source weight config: customerId={}, ipSegment={}", customerId, ipSegment);
            return true;
        }
        return false;
    }
    
    /**
     * 禁用配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 是否成功
     */
    @Transactional
    public boolean disable(String customerId, String ipSegment) {
        Optional<AttackSourceWeight> entityOpt = repository.findByCustomerIdAndIpSegment(customerId, ipSegment);
        if (entityOpt.isPresent()) {
            AttackSourceWeight entity = entityOpt.get();
            entity.setIsActive(false);
            repository.save(entity);
            log.info("Disabled attack source weight config: customerId={}, ipSegment={}", customerId, ipSegment);
            return true;
        }
        return false;
    }
    
    /**
     * 删除配置
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 是否成功
     */
    @Transactional
    public boolean delete(String customerId, String ipSegment) {
        try {
            repository.deleteByCustomerIdAndIpSegment(customerId, ipSegment);
            log.info("Deleted attack source weight config: customerId={}, ipSegment={}", customerId, ipSegment);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete attack source weight config: customerId={}, ipSegment={}", 
                     customerId, ipSegment, e);
            return false;
        }
    }
    
    /**
     * 获取统计信息
     * 
     * @param customerId 客户ID
     * @return 统计信息 [count, avg_weight, max_weight, min_weight]
     */
    public Object[] getStatistics(String customerId) {
        return repository.getStatistics(customerId);
    }
    
    /**
     * 检查配置是否存在
     * 
     * @param customerId 客户ID
     * @param ipSegment IP段标识
     * @return 是否存在
     */
    public boolean exists(String customerId, String ipSegment) {
        return repository.existsByCustomerIdAndIpSegment(customerId, ipSegment);
    }
    
    /**
     * 获取配置数量
     * 
     * @param customerId 客户ID
     * @return 配置数量
     */
    public long count(String customerId) {
        return repository.countByCustomerId(customerId);
    }
    
    /**
     * 获取启用配置数量
     * 
     * @param customerId 客户ID
     * @return 启用配置数量
     */
    public long countActive(String customerId) {
        return repository.countByCustomerIdAndIsActive(customerId, true);
    }
    
    /**
     * DTO转换为实体
     */
    private AttackSourceWeight convertToEntity(AttackSourceWeightDto dto) {
        AttackSourceWeight entity = new AttackSourceWeight();
        entity.setId(dto.getId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setIpSegment(dto.getIpSegment());
        entity.setAttackSourceWeight(dto.getAttackSourceWeight());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }
    
    /**
     * 实体转换为DTO
     */
    private AttackSourceWeightDto convertToDto(AttackSourceWeight entity) {
        return AttackSourceWeightDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .ipSegment(entity.getIpSegment())
                .attackSourceWeight(entity.getAttackSourceWeight())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
