package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.AttackEventEntity;
import com.threatdetection.ingestion.repository.AttackEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 攻击事件持久化服务
 * 
 * <p>监听attack-events topic,将所有攻击事件持久化到数据库
 */
@Service
public class AttackEventPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(AttackEventPersistenceService.class);
    
    private final AttackEventRepository attackEventRepository;
    private final ObjectMapper objectMapper;
    
    public AttackEventPersistenceService(
            AttackEventRepository attackEventRepository,
            ObjectMapper objectMapper) {
        this.attackEventRepository = attackEventRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 消费attack-events topic并持久化
     */
    @KafkaListener(
        topics = "attack-events",
        groupId = "attack-events-persistence-group"
    )
    @Transactional
    public void consumeAttackEvent(String message) {
        try {
            JsonNode eventNode = objectMapper.readTree(message);
            
            // 解析JSON
            String customerId = eventNode.get("customerId").asText();
            String devSerial = eventNode.has("devSerial") ? 
                eventNode.get("devSerial").asText() : 
                eventNode.get("deviceSerial").asText();  // 兼容两种字段名
            String attackMac = eventNode.get("attackMac").asText();
            String attackIp = eventNode.has("attackIp") ? 
                eventNode.get("attackIp").asText() : null;
            String responseIp = eventNode.get("responseIp").asText();
            int responsePort = eventNode.get("responsePort").asInt();
            
            // 解析时间戳
            Instant eventTimestamp;
            if (eventNode.has("timestamp")) {
                long timestampNanos = eventNode.get("timestamp").asLong();
                eventTimestamp = Instant.ofEpochSecond(
                    timestampNanos / 1_000_000_000,
                    timestampNanos % 1_000_000_000
                );
            } else {
                eventTimestamp = Instant.now();
            }
            
            Long logTime = eventNode.has("logTime") ? 
                eventNode.get("logTime").asLong() : null;
            
            // 创建实体
            AttackEventEntity entity = new AttackEventEntity();
            entity.setCustomerId(customerId);
            entity.setDevSerial(devSerial);
            entity.setAttackMac(attackMac);
            entity.setAttackIp(attackIp);
            entity.setResponseIp(responseIp);
            entity.setResponsePort(responsePort);
            entity.setEventTimestamp(eventTimestamp);
            entity.setLogTime(logTime);
            entity.setRawLogData(message);  // 保存完整JSON
            
            // 保存到数据库
            attackEventRepository.save(entity);
            
            log.debug("Persisted attack event: customerId={}, attackMac={}, port={}", 
                customerId, attackMac, responsePort);
            
        } catch (Exception e) {
            log.error("Failed to persist attack event: {}", message, e);
            // 不抛出异常,避免消费者停止
        }
    }
    
    /**
     * 批量保存(用于高吞吐场景)
     */
    @Transactional
    public void saveAll(Iterable<AttackEventEntity> entities) {
        attackEventRepository.saveAll(entities);
    }
}
