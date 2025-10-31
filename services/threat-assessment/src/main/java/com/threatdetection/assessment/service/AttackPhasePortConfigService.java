package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AttackPhasePortConfigDto;
import com.threatdetection.assessment.model.AttackPhasePortConfig;
import com.threatdetection.assessment.repository.AttackPhasePortConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 攻击阶段端口配置服务
 * 
 * <p>提供多租户攻击阶段端口配置管理功能，支持客户自定义配置和全局默认配置
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackPhasePortConfigService {
    
    private final AttackPhasePortConfigRepository repository;
    
    @Transactional
    public AttackPhasePortConfigDto save(AttackPhasePortConfigDto dto) {
        log.info("Saving attack phase port config: customerId={}, phase={}, port={}", 
                 dto.getCustomerId(), dto.getPhase(), dto.getPort());
        
        AttackPhasePortConfig entity = convertToEntity(dto);
        AttackPhasePortConfig saved = repository.save(entity);
        
        log.info("Successfully saved attack phase port config: id={}", saved.getId());
        return convertToDto(saved);
    }
    
    public Optional<AttackPhasePortConfigDto> findByCustomerIdAndPhaseAndPort(String customerId, String phase, Integer port) {
        return repository.findByCustomerIdAndPhaseAndPortNumber(customerId, phase, port)
                .map(this::convertToDto);
    }
    
    public List<AttackPhasePortConfigDto> findByCustomerIdAndPhase(String customerId, String phase) {
        return repository.findByCustomerIdAndPhase(customerId, phase)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AttackPhasePortConfigDto> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AttackPhasePortConfigDto> findCombinedConfigsByCustomerAndPhase(String customerId, String phase) {
        return repository.findCombinedConfigsByCustomerAndPhase(customerId, phase)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AttackPhasePortConfigDto> findGlobalDefaultsByPhase(String phase) {
        return repository.findByCustomerIdIsNullAndPhase(phase)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AttackPhasePortConfigDto> findAllPhases() {
        return repository.findDistinctPhases()
                .stream()
                .map(phase -> AttackPhasePortConfigDto.builder().phase(phase).build())
                .collect(Collectors.toList());
    }
    
    @Transactional
    public boolean enable(String customerId, String phase, Integer port) {
        Optional<AttackPhasePortConfig> entityOpt = repository.findByCustomerIdAndPhaseAndPortNumber(customerId, phase, port);
        if (entityOpt.isPresent()) {
            AttackPhasePortConfig entity = entityOpt.get();
            entity.setEnabled(true);
            repository.save(entity);
            log.info("Enabled attack phase port config: customerId={}, phase={}, port={}", customerId, phase, port);
            return true;
        }
        return false;
    }
    
    @Transactional
    public boolean disable(String customerId, String phase, Integer port) {
        Optional<AttackPhasePortConfig> entityOpt = repository.findByCustomerIdAndPhaseAndPortNumber(customerId, phase, port);
        if (entityOpt.isPresent()) {
            AttackPhasePortConfig entity = entityOpt.get();
            entity.setEnabled(false);
            repository.save(entity);
            log.info("Disabled attack phase port config: customerId={}, phase={}, port={}", customerId, phase, port);
            return true;
        }
        return false;
    }
    
    @Transactional
    public boolean delete(String customerId, String phase, Integer port) {
        try {
            repository.deleteByCustomerIdAndPhaseAndPortNumber(customerId, phase, port);
            log.info("Deleted attack phase port config: customerId={}, phase={}, port={}", customerId, phase, port);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete attack phase port config: customerId={}, phase={}, port={}", 
                     customerId, phase, port, e);
            return false;
        }
    }
    
    public Object[] getStatistics(String customerId) {
        return repository.getStatistics(customerId);
    }
    
    public boolean exists(String customerId, String phase, Integer port) {
        return repository.existsByCustomerIdAndPhaseAndPortNumber(customerId, phase, port);
    }
    
    public long count(String customerId) {
        return repository.countByCustomerId(customerId);
    }
    
    public long countByPhase(String customerId, String phase) {
        return repository.countByCustomerIdAndPhase(customerId, phase);
    }
    
    public long countEnabled(String customerId, String phase) {
        return repository.countByCustomerIdAndPhaseAndIsEnabled(customerId, phase, true);
    }
    
    /**
     * 获取客户配置，如果不存在则返回全局默认配置
     */
    public List<AttackPhasePortConfigDto> getEffectiveConfigs(String customerId, String phase) {
        List<AttackPhasePortConfigDto> customerConfigs = findByCustomerIdAndPhase(customerId, phase);
        if (!customerConfigs.isEmpty()) {
            return customerConfigs;
        }
        
        // 返回全局默认配置
        log.debug("No customer-specific config found for customerId={}, phase={}, using global defaults", customerId, phase);
        return findGlobalDefaultsByPhase(phase);
    }
    
    private AttackPhasePortConfig convertToEntity(AttackPhasePortConfigDto dto) {
        AttackPhasePortConfig entity = new AttackPhasePortConfig();
        entity.setId(dto.getId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setPhase(dto.getPhase());
        entity.setPort(dto.getPort());
        entity.setPriority(dto.getPriority());
        entity.setEnabled(dto.getEnabled());
        entity.setDescription(dto.getDescription());
        return entity;
    }
    
    private AttackPhasePortConfigDto convertToDto(AttackPhasePortConfig entity) {
        return AttackPhasePortConfigDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .phase(entity.getPhase())
                .port(entity.getPort())
                .priority(entity.getPriority())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
