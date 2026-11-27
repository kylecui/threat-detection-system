package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.AuditEvent;
import com.threatdetection.ingestion.model.BgTrafficEvent;
import com.threatdetection.ingestion.model.PolicyEvent;
import com.threatdetection.ingestion.model.SnifferEvent;
import com.threatdetection.ingestion.model.StatusEvent;
import com.threatdetection.ingestion.model.ThreatDetectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Kafka主题定义
    public static final String ATTACK_EVENTS_TOPIC = "attack-events";
    public static final String STATUS_EVENTS_TOPIC = "status-events";
    public static final String SNIFFER_EVENTS_TOPIC = "sniffer-events";
    public static final String THREAT_DETECTION_EVENTS_TOPIC = "threat-detection-events";
    public static final String POLICY_EVENTS_TOPIC = "policy-events";
    public static final String BG_TRAFFIC_EVENTS_TOPIC = "bg-traffic-events";
    public static final String AUDIT_EVENTS_TOPIC = "audit-events";
    
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    public CompletableFuture<SendResult<String, Object>> sendAttackEvent(AttackEvent event) {
        try {
            return kafkaTemplate.send(ATTACK_EVENTS_TOPIC, event.getDevSerial(), event);
        } catch (Exception e) {
            logger.error("Failed to send attack event to Kafka: devSerial={}", event.getDevSerial(), e);
            throw e;
        }
    }
    
    public CompletableFuture<SendResult<String, Object>> sendStatusEvent(StatusEvent event) {
        try {
            return kafkaTemplate.send(STATUS_EVENTS_TOPIC, event.getDevSerial(), event);
        } catch (Exception e) {
            logger.error("Failed to send status event to Kafka: devSerial={}", event.getDevSerial(), e);
            throw e;
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendSnifferEvent(SnifferEvent event) {
        try {
            return kafkaTemplate.send(SNIFFER_EVENTS_TOPIC, event.getDeviceId(), event);
        } catch (Exception e) {
            logger.error("Failed to send sniffer event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendThreatDetectionEvent(ThreatDetectionEvent event) {
        try {
            return kafkaTemplate.send(THREAT_DETECTION_EVENTS_TOPIC, event.getDeviceId(), event);
        } catch (Exception e) {
            logger.error("Failed to send threat detection event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendPolicyEvent(PolicyEvent event) {
        try {
            return kafkaTemplate.send(POLICY_EVENTS_TOPIC, event.getDeviceId(), event);
        } catch (Exception e) {
            logger.error("Failed to send policy event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendBgTrafficEvent(BgTrafficEvent event) {
        try {
            return kafkaTemplate.send(BG_TRAFFIC_EVENTS_TOPIC, event.getDeviceId(), event);
        } catch (Exception e) {
            logger.error("Failed to send bg traffic event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendAuditEvent(AuditEvent event) {
        try {
            return kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.getDeviceId(), event);
        } catch (Exception e) {
            logger.error("Failed to send audit event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }
}
