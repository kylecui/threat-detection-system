package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Kafka主题定义
    public static final String ATTACK_EVENTS_TOPIC = "attack-events";
    public static final String STATUS_EVENTS_TOPIC = "status-events";
    
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    public void sendAttackEvent(AttackEvent event) {
        try {
            kafkaTemplate.send(ATTACK_EVENTS_TOPIC, event.getDevSerial(), event);
            logger.info("Sent attack event to Kafka: devSerial={}, attackIp={}, responseIp={}", 
                       event.getDevSerial(), event.getAttackIp(), event.getResponseIp());
        } catch (Exception e) {
            logger.error("Failed to send attack event to Kafka: {}", event, e);
            throw e;
        }
    }
    
    public void sendStatusEvent(StatusEvent event) {
        try {
            kafkaTemplate.send(STATUS_EVENTS_TOPIC, event.getDevSerial(), event);
            logger.info("Sent status event to Kafka: devSerial={}, sentryCount={}, realHostCount={}", 
                       event.getDevSerial(), event.getSentryCount(), event.getRealHostCount());
        } catch (Exception e) {
            logger.error("Failed to send status event to Kafka: {}", event, e);
            throw e;
        }
    }
}