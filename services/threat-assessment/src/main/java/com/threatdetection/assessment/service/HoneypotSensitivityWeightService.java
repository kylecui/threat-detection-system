package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.HoneypotSensitivityWeightDto;
import com.threatdetection.assessment.model.HoneypotSensitivityWeight;
import com.threatdetection.assessment.repository.HoneypotSensitivityWeightRepositoryNew;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 蜜罐敏感度权重配置服务
 * 
 * <p>提供多租户蜜罐敏感度权重配置管理功能
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoneypotSensitivityWeightService {
    
    private final HoneypotSensitivityWeightRepositoryNew repository;
    
    @Transactional
    public HoneypotSensitivityWeightDto save(HoneypotSensitivityWeightDto dto) {
        log.info("Saving honeypot sensitivity weight config: customerId={}, ipSegment={}", 
                 dto.getCustomerId(), dto.getIpSegment());
        
        HoneypotSensitivityWeight entity = convertToEntity(dto);
        HoneypotSensitivityWeight saved = repository.save(entity);
        
        log.info("Successfully saved honeypot sensitivity weight config: id={}", saved.getId());
        return convertToDto(saved);
    }
    
    public Optional<HoneypotSensitivityWeightDto> findByCustomerIdAndHoneypotIp(String customerId, String honeypotIp) {
        return repository.findByCustomerIdAndHoneypotIp(customerId, honeypotIp)
                .map(this::convertToDto);
    }
    
    public List<HoneypotSensitivityWeightDto> findByCustomerIdAndActive(String customerId) {
        return repository.findByCustomerIdAndIsActive(customerId, true)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<HoneypotSensitivityWeightDto> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<HoneypotSensitivityWeightDto> findHighSensitivityConfigs(String customerId, BigDecimal threshold) {
        return repository.findHighSensitivityConfigs(customerId, threshold)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public boolean enable(String customerId, String honeypotIp) {
        Optional<HoneypotSensitivityWeight> entityOpt = repository.findByCustomerIdAndHoneypotIp(customerId, honeypotIp);
        if (entityOpt.isPresent()) {
            HoneypotSensitivityWeight entity = entityOpt.get();
            entity.setIsActive(true);
            repository.save(entity);
            log.info("Enabled honeypot sensitivity weight config: customerId={}, honeypotIp={}", customerId, honeypotIp);
            return true;
        }
        return false;
    }
    
    @Transactional
    public boolean disable(String customerId, String honeypotIp) {
        Optional<HoneypotSensitivityWeight> entityOpt = repository.findByCustomerIdAndHoneypotIp(customerId, honeypotIp);
        if (entityOpt.isPresent()) {
            HoneypotSensitivityWeight entity = entityOpt.get();
            entity.setIsActive(false);
            repository.save(entity);
            log.info("Disabled honeypot sensitivity weight config: customerId={}, honeypotIp={}", customerId, honeypotIp);
            return true;
        }
        return false;
    }
    
    @Transactional
    public boolean delete(String customerId, String honeypotIp) {
        try {
            repository.deleteByCustomerIdAndHoneypotIp(customerId, honeypotIp);
            log.info("Deleted honeypot sensitivity weight config: customerId={}, honeypotIp={}", customerId, honeypotIp);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete honeypot sensitivity weight config: customerId={}, honeypotIp={}", 
                     customerId, honeypotIp, e);
            return false;
        }
    }
    
    public Object[] getStatistics(String customerId) {
        return repository.getStatistics(customerId);
    }
    
    public boolean exists(String customerId, String honeypotIp) {
        return repository.existsByCustomerIdAndHoneypotIp(customerId, honeypotIp);
    }
    
    public long count(String customerId) {
        return repository.countByCustomerId(customerId);
    }
    
    public long countActive(String customerId) {
        return repository.countByCustomerIdAndIsActive(customerId, true);
    }
    
    private HoneypotSensitivityWeight convertToEntity(HoneypotSensitivityWeightDto dto) {
        HoneypotSensitivityWeight entity = new HoneypotSensitivityWeight();
        entity.setId(dto.getId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setIpSegment(dto.getIpSegment());
        entity.setHoneypotSensitivityWeight(dto.getHoneypotSensitivityWeight());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }
    
    private HoneypotSensitivityWeightDto convertToDto(HoneypotSensitivityWeight entity) {
        return HoneypotSensitivityWeightDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .ipSegment(entity.getIpSegment())
                .honeypotSensitivityWeight(entity.getHoneypotSensitivityWeight())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
