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
import org.springframework.stereotype.Service;

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

    public void sendSnifferEvent(SnifferEvent event) {
        try {
            kafkaTemplate.send(SNIFFER_EVENTS_TOPIC, event.getDeviceId(), event);
            logger.info("Sent sniffer event to Kafka: deviceId={}, suspectIp={}, customerId={}",
                       event.getDeviceId(), event.getSuspectIp(), event.getCustomerId());
        } catch (Exception e) {
            logger.error("Failed to send sniffer event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public void sendThreatDetectionEvent(ThreatDetectionEvent event) {
        try {
            kafkaTemplate.send(THREAT_DETECTION_EVENTS_TOPIC, event.getDeviceId(), event);
            logger.info("Sent threat detection event to Kafka: deviceId={}, patternId={}, srcIp={}, customerId={}",
                       event.getDeviceId(), event.getPatternId(), event.getSrcIp(), event.getCustomerId());
        } catch (Exception e) {
            logger.error("Failed to send threat detection event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public void sendPolicyEvent(PolicyEvent event) {
        try {
            kafkaTemplate.send(POLICY_EVENTS_TOPIC, event.getDeviceId(), event);
            logger.info("Sent policy event to Kafka: deviceId={}, policyId={}, action={}, customerId={}",
                       event.getDeviceId(), event.getPolicyId(), event.getAction(), event.getCustomerId());
        } catch (Exception e) {
            logger.error("Failed to send policy event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public void sendBgTrafficEvent(BgTrafficEvent event) {
        try {
            kafkaTemplate.send(BG_TRAFFIC_EVENTS_TOPIC, event.getDeviceId(), event);
            logger.info("Sent bg traffic event to Kafka: deviceId={}, periodStart={}, customerId={}",
                       event.getDeviceId(), event.getPeriodStart(), event.getCustomerId());
        } catch (Exception e) {
            logger.error("Failed to send bg traffic event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }

    public void sendAuditEvent(AuditEvent event) {
        try {
            kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.getDeviceId(), event);
            logger.info("Sent audit event to Kafka: deviceId={}, action={}, actor={}, customerId={}",
                       event.getDeviceId(), event.getAction(), event.getActor(), event.getCustomerId());
        } catch (Exception e) {
            logger.error("Failed to send audit event to Kafka: deviceId={}", event.getDeviceId(), e);
            throw e;
        }
    }
}