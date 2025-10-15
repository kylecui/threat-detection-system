package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.ThreatAlertEntity;
import com.threatdetection.ingestion.repository.ThreatAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 威胁告警持久化服务
 * 
 * <p>监听threat-alerts topic,将所有威胁告警持久化到数据库
 */
@Service
public class ThreatAlertPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(ThreatAlertPersistenceService.class);
    
    private final ThreatAlertRepository threatAlertRepository;
    private final ObjectMapper objectMapper;
    
    public ThreatAlertPersistenceService(
            ThreatAlertRepository threatAlertRepository,
            ObjectMapper objectMapper) {
        this.threatAlertRepository = threatAlertRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 消费threat-alerts topic并持久化
     */
    @KafkaListener(
        topics = "threat-alerts",
        groupId = "threat-alerts-persistence-group"
    )
    @Transactional
    public void consumeThreatAlert(String message) {
        try {
            JsonNode alertNode = objectMapper.readTree(message);
            
            // 解析JSON
            String customerId = alertNode.get("customerId").asText();
            String attackMac = alertNode.get("attackMac").asText();
            
            double threatScore = alertNode.has("threatScore") ? 
                alertNode.get("threatScore").asDouble() : 0.0;
            String threatLevel = alertNode.has("threatLevel") ? 
                alertNode.get("threatLevel").asText() : "UNKNOWN";
            
            int attackCount = alertNode.get("attackCount").asInt();
            int uniqueIps = alertNode.get("uniqueIps").asInt();
            int uniquePorts = alertNode.get("uniquePorts").asInt();
            int uniqueDevices = alertNode.get("uniqueDevices").asInt();
            
            double mixedPortWeight = alertNode.has("mixedPortWeight") ?
                alertNode.get("mixedPortWeight").asDouble() : 0.0;
            
            int tier = alertNode.get("tier").asInt();
            String windowType = alertNode.has("windowType") ?
                alertNode.get("windowType").asText() : null;
            
            // 解析时间戳
            Instant windowStart = parseInstant(alertNode.get("windowStart"));
            Instant windowEnd = parseInstant(alertNode.get("windowEnd"));
            Instant alertTimestamp = alertNode.has("timestamp") ?
                parseInstant(alertNode.get("timestamp")) : Instant.now();
            
            // 创建实体
            ThreatAlertEntity entity = new ThreatAlertEntity();
            entity.setCustomerId(customerId);
            entity.setAttackMac(attackMac);
            entity.setThreatScore(BigDecimal.valueOf(threatScore));
            entity.setThreatLevel(threatLevel);
            entity.setAttackCount(attackCount);
            entity.setUniqueIps(uniqueIps);
            entity.setUniquePorts(uniquePorts);
            entity.setUniqueDevices(uniqueDevices);
            entity.setMixedPortWeight(BigDecimal.valueOf(mixedPortWeight));
            entity.setTier(tier);
            entity.setWindowType(windowType);
            entity.setWindowStart(windowStart);
            entity.setWindowEnd(windowEnd);
            entity.setAlertTimestamp(alertTimestamp);
            entity.setRawAlertData(message);  // 保存完整JSON
            
            // 保存到数据库
            threatAlertRepository.save(entity);
            
            log.info("Persisted threat alert: customerId={}, attackMac={}, level={}, score={}, tier={}", 
                customerId, attackMac, threatLevel, threatScore, tier);
            
        } catch (Exception e) {
            log.error("Failed to persist threat alert: {}", message, e);
            // 不抛出异常,避免消费者停止
        }
    }
    
    /**
     * 解析Instant时间戳 (支持纳秒和秒格式)
     */
    private Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull()) {
            return Instant.now();
        }
        
        if (node.isNumber()) {
            long value = node.asLong();
            // 判断是纳秒还是秒 (纳秒通常大于10^15)
            if (value > 1_000_000_000_000_000L) {
                return Instant.ofEpochSecond(
                    value / 1_000_000_000,
                    value % 1_000_000_000
                );
            } else {
                return Instant.ofEpochSecond(value);
            }
        } else if (node.isTextual()) {
            return Instant.parse(node.asText());
        }
        
        return Instant.now();
    }
}
