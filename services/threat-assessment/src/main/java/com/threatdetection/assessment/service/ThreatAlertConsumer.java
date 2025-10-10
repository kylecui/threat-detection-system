package com.threatdetection.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.assessment.model.AssessmentRequest;
import com.threatdetection.assessment.model.RiskLevel;
import com.threatdetection.assessment.model.ThreatAlert;
import com.threatdetection.assessment.repository.ThreatAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer service for processing threat alerts from stream processing
 */
@Service
public class ThreatAlertConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ThreatAlertConsumer.class);

    private final ThreatAlertRepository threatAlertRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final ObjectMapper objectMapper;

    // Metrics
    private final Counter alertsReceivedCounter;
    private final Counter alertsProcessedCounter;
    private final Counter alertsFailedCounter;

    @Autowired
    public ThreatAlertConsumer(ThreatAlertRepository threatAlertRepository,
                              RiskAssessmentService riskAssessmentService,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.threatAlertRepository = threatAlertRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.objectMapper = objectMapper;

        // Initialize metrics
        this.alertsReceivedCounter = Counter.builder("kafka.alerts.received.total")
                .description("Total threat alerts received from Kafka")
                .register(meterRegistry);
        this.alertsProcessedCounter = Counter.builder("kafka.alerts.processed.total")
                .description("Total threat alerts successfully processed")
                .register(meterRegistry);
        this.alertsFailedCounter = Counter.builder("kafka.alerts.failed.total")
                .description("Total threat alerts that failed processing")
                .register(meterRegistry);
    }

    /**
     * Consume threat alerts from Kafka topic
     */
    @KafkaListener(topics = "${kafka.topics.threat-alerts:threat-alerts}",
                   groupId = "${kafka.consumer.group-id:threat-assessment-group}",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeThreatAlert(String message, Acknowledgment acknowledgment) {
        logger.debug("Received threat alert message: {}", message);
        alertsReceivedCounter.increment();

        try {
            // Parse the threat alert message
            ThreatAlertMessage alertMessage = parseThreatAlertMessage(message);

            // Convert to ThreatAlert entity
            ThreatAlert threatAlert = convertToThreatAlert(alertMessage);

            // Save to database
            threatAlertRepository.save(threatAlert);

            // Perform risk assessment
            AssessmentRequest assessmentRequest = createAssessmentRequest(threatAlert);
            riskAssessmentService.assessThreat(assessmentRequest);

            // Acknowledge successful processing
            acknowledgment.acknowledge();
            alertsProcessedCounter.increment();

            logger.info("Successfully processed threat alert: {}", threatAlert.getAlertId());

        } catch (Exception e) {
            logger.error("Failed to process threat alert message: {}", message, e);
            alertsFailedCounter.increment();

            // In a production system, you might want to send failed messages to a dead letter queue
            // or implement retry logic with exponential backoff
        }
    }

    /**
     * Parse Kafka message into ThreatAlertMessage
     */
    private ThreatAlertMessage parseThreatAlertMessage(String message) throws Exception {
        return objectMapper.readValue(message, ThreatAlertMessage.class);
    }

    /**
     * Convert parsed message to ThreatAlert entity
     */
    private ThreatAlert convertToThreatAlert(ThreatAlertMessage message) {
        ThreatAlert threatAlert = new ThreatAlert();
        threatAlert.setAlertId(message.getAlertId() != null ? message.getAlertId() :
                              "alert-" + System.currentTimeMillis());
        threatAlert.setAttackMac(message.getAttackMac());
        threatAlert.setThreatScore(message.getThreatScore());
        threatAlert.setThreatLevel(parseRiskLevel(message.getThreatLevel()));
        threatAlert.setThreatName(message.getThreatName());

        // Convert timestamp
        if (message.getTimestamp() != null) {
            threatAlert.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(message.getTimestamp()), ZoneOffset.UTC));
        } else {
            threatAlert.setTimestamp(LocalDateTime.now());
        }

        threatAlert.setWindowStart(message.getWindowStart() != null ?
            LocalDateTime.ofInstant(Instant.ofEpochMilli(message.getWindowStart()), ZoneOffset.UTC) : null);
        threatAlert.setWindowEnd(message.getWindowEnd() != null ?
            LocalDateTime.ofInstant(Instant.ofEpochMilli(message.getWindowEnd()), ZoneOffset.UTC) : null);

        threatAlert.setTotalAggregations(message.getTotalAggregations());

        return threatAlert;
    }

    /**
     * Create assessment request from threat alert
     */
    private AssessmentRequest createAssessmentRequest(ThreatAlert threatAlert) {
        AssessmentRequest request = new AssessmentRequest();
        request.setAlertId(threatAlert.getAlertId());
        request.setAttackMac(threatAlert.getAttackMac());
        request.setThreatScore(threatAlert.getThreatScore());
        request.setThreatLevel(threatAlert.getThreatLevel());
        request.setThreatName(threatAlert.getThreatName());
        request.setTimestamp(threatAlert.getTimestamp());

        // Set default attack patterns if none available
        if (threatAlert.getAttackPatterns() == null || threatAlert.getAttackPatterns().isEmpty()) {
            request.setAttackPatterns(List.of("unknown_attack"));
        } else {
            request.setAttackPatterns(threatAlert.getAttackPatterns());
        }

        // Set default affected assets if none available
        if (threatAlert.getAffectedAssets() == null || threatAlert.getAffectedAssets().isEmpty()) {
            request.setAffectedAssets(List.of("network_infrastructure"));
        } else {
            request.setAffectedAssets(threatAlert.getAffectedAssets());
        }

        return request;
    }

    /**
     * Parse risk level string to enum
     */
    private RiskLevel parseRiskLevel(String level) {
        if (level == null) return RiskLevel.INFO;

        try {
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown risk level: {}, defaulting to INFO", level);
            return RiskLevel.INFO;
        }
    }

    /**
     * Inner class for parsing Kafka messages
     */
    public static class ThreatAlertMessage {
        private String alertId;
        private String attackMac;
        private double threatScore;
        private String threatLevel;
        private String threatName;
        private Long timestamp;
        private Long windowStart;
        private Long windowEnd;
        private int totalAggregations;

        // Getters and setters
        public String getAlertId() { return alertId; }
        public void setAlertId(String alertId) { this.alertId = alertId; }

        public String getAttackMac() { return attackMac; }
        public void setAttackMac(String attackMac) { this.attackMac = attackMac; }

        public double getThreatScore() { return threatScore; }
        public void setThreatScore(double threatScore) { this.threatScore = threatScore; }

        public String getThreatLevel() { return threatLevel; }
        public void setThreatLevel(String threatLevel) { this.threatLevel = threatLevel; }

        public String getThreatName() { return threatName; }
        public void setThreatName(String threatName) { this.threatName = threatName; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

        public Long getWindowStart() { return windowStart; }
        public void setWindowStart(Long windowStart) { this.windowStart = windowStart; }

        public Long getWindowEnd() { return windowEnd; }
        public void setWindowEnd(Long windowEnd) { this.windowEnd = windowEnd; }

        public int getTotalAggregations() { return totalAggregations; }
        public void setTotalAggregations(int totalAggregations) { this.totalAggregations = totalAggregations; }
    }
}