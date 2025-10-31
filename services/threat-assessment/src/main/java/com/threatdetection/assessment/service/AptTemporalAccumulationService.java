package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AptTemporalAccumulationDto;
import com.threatdetection.assessment.model.AptTemporalAccumulation;
import com.threatdetection.assessment.repository.AptTemporalAccumulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * APT时序累积服务
 * 
 * <p>提供多租户APT时序累积数据管理功能，支持时间窗口内的威胁累积计算
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AptTemporalAccumulationService {
    
    private final AptTemporalAccumulationRepository repository;
    
    @Transactional
    public AptTemporalAccumulationDto save(AptTemporalAccumulationDto dto) {
        log.info("Saving APT temporal accumulation: customerId={}, attackMac={}, windowStart={}", 
                 dto.getCustomerId(), dto.getAttackMac(), dto.getWindowStart());
        
        AptTemporalAccumulation entity = convertToEntity(dto);
        AptTemporalAccumulation saved = repository.save(entity);
        
        log.info("Successfully saved APT temporal accumulation: id={}", saved.getId());
        return convertToDto(saved);
    }
    
    public Optional<AptTemporalAccumulationDto> findByCustomerIdAndAttackMacAndWindowStart(
            String customerId, String attackMac, Instant windowStart) {
        return repository.findByCustomerIdAndAttackMacAndWindowStart(customerId, attackMac, windowStart)
                .map(this::convertToDto);
    }
    
    public List<AptTemporalAccumulationDto> findByCustomerIdAndAttackMac(String customerId, String attackMac) {
        return repository.findByCustomerIdAndAttackMac(customerId, attackMac)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AptTemporalAccumulationDto> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AptTemporalAccumulationDto> findByCustomerIdAndTimeRange(
            String customerId, Instant startTime, Instant endTime) {
        return repository.findByCustomerIdAndWindowStartBetween(customerId, startTime, endTime)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AptTemporalAccumulationDto> findHighRiskAccumulations(
            String customerId, BigDecimal threshold, Instant since) {
        return repository.findHighRiskAccumulations(customerId, threshold, since)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<AptTemporalAccumulationDto> findRecentAccumulations(String customerId, Instant since) {
        return repository.findByCustomerIdAndWindowStartGreaterThanEqual(customerId, since)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public AptTemporalAccumulationDto updateAccumulation(
            String customerId, String attackMac, Instant windowStart, 
            BigDecimal newAccumulatedScore, BigDecimal newDecayScore) {
        
        Optional<AptTemporalAccumulation> existingOpt = 
            repository.findByCustomerIdAndAttackMacAndWindowStart(customerId, attackMac, windowStart);
        
        AptTemporalAccumulation entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setAccumulatedScore(newAccumulatedScore);
            entity.setDecayAccumulatedScore(newDecayScore);
            entity.setLastUpdated(Instant.now());
        } else {
            entity = new AptTemporalAccumulation();
            entity.setCustomerId(customerId);
            entity.setAttackMac(attackMac);
            entity.setWindowStart(windowStart);
            entity.setWindowEnd(windowStart.plusSeconds(3600)); // Default 1 hour window
            entity.setAccumulatedScore(newAccumulatedScore);
            entity.setDecayAccumulatedScore(newDecayScore);
            entity.setLastUpdated(Instant.now());
        }
        
        AptTemporalAccumulation saved = repository.save(entity);
        log.info("Updated APT temporal accumulation: customerId={}, attackMac={}, score={}", 
                 customerId, attackMac, newAccumulatedScore);
        
        return convertToDto(saved);
    }
    
    @Transactional
    public boolean delete(String customerId, String attackMac, Instant windowStart) {
        try {
            repository.deleteByCustomerIdAndAttackMacAndWindowStart(customerId, attackMac, windowStart);
            log.info("Deleted APT temporal accumulation: customerId={}, attackMac={}, windowStart={}", 
                     customerId, attackMac, windowStart);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete APT temporal accumulation: customerId={}, attackMac={}, windowStart={}", 
                     customerId, attackMac, windowStart, e);
            return false;
        }
    }
    
    @Transactional
    public void cleanupOldData(String customerId, Instant beforeTime) {
        repository.deleteByCustomerIdAndWindowStartLessThan(customerId, beforeTime);
        log.info("Cleaned up old APT temporal accumulation records for customerId={}", customerId);
    }
    
    public Object[] getStatistics(String customerId) {
        return repository.getStatistics(customerId);
    }
    
    public BigDecimal getTotalAccumulatedScore(String customerId, String attackMac, Instant since) {
        return repository.getTotalAccumulatedScore(customerId, attackMac, since);
    }
    
    public BigDecimal getMaxDecayScore(String customerId, Instant since) {
        return repository.getMaxDecayScore(customerId, since);
    }
    
    public long count(String customerId) {
        return repository.countByCustomerId(customerId);
    }
    
    public long countByTimeRange(String customerId, Instant startTime, Instant endTime) {
        return repository.countByCustomerIdAndWindowStartBetween(customerId, startTime, endTime);
    }
    
    public boolean exists(String customerId, String attackMac, Instant windowStart) {
        return repository.existsByCustomerIdAndAttackMacAndWindowStart(customerId, attackMac, windowStart);
    }
    
    /**
     * 获取指定时间窗口内的累积数据，按时间排序
     */
    public List<AptTemporalAccumulationDto> getAccumulationHistory(
            String customerId, String attackMac, Instant startTime, Instant endTime) {
        
        return repository.findByCustomerIdAndAttackMacAndWindowStartBetweenOrderByWindowStart(
                customerId, attackMac, startTime, endTime)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 计算指定MAC地址的当前累积威胁分数
     */
    public BigDecimal calculateCurrentThreatScore(String customerId, String attackMac) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        BigDecimal totalScore = repository.getTotalAccumulatedScore(customerId, attackMac, oneHourAgo);
        return totalScore != null ? totalScore : BigDecimal.ZERO;
    }
    
    private AptTemporalAccumulation convertToEntity(AptTemporalAccumulationDto dto) {
        AptTemporalAccumulation entity = new AptTemporalAccumulation();
        entity.setId(dto.getId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setAttackMac(dto.getAttackMac());
        entity.setWindowStart(dto.getWindowStart());
        entity.setWindowEnd(dto.getWindowEnd());
        entity.setAccumulatedScore(dto.getAccumulatedScore());
        entity.setDecayAccumulatedScore(dto.getDecayAccumulatedScore());
        entity.setLastUpdated(dto.getLastUpdated());
        return entity;
    }
    
    private AptTemporalAccumulationDto convertToDto(AptTemporalAccumulation entity) {
        return AptTemporalAccumulationDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .attackMac(entity.getAttackMac())
                .windowStart(entity.getWindowStart())
                .windowEnd(entity.getWindowEnd())
                .accumulatedScore(entity.getAccumulatedScore())
                .decayAccumulatedScore(entity.getDecayAccumulatedScore())
                .lastUpdated(entity.getLastUpdated())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
