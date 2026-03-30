package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.DeviceStatusHistoryEntity;
import com.threatdetection.ingestion.repository.DeviceStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StatusEventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(StatusEventPersistenceService.class);

    private final DeviceStatusHistoryRepository repository;
    private final DevSerialToCustomerMappingService customerMappingService;
    private final ObjectMapper objectMapper;

    public StatusEventPersistenceService(
            DeviceStatusHistoryRepository repository,
            DevSerialToCustomerMappingService customerMappingService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.customerMappingService = customerMappingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "status-events",
        groupId = "status-events-persistence-group"
    )
    @Transactional
    public void consumeStatusEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            String devSerial = node.get("devSerial").asText();
            int sentryCount = node.has("sentryCount") ? node.get("sentryCount").asInt() : 0;
            int realHostCount = node.has("realHostCount") ? node.get("realHostCount").asInt() : 0;
            long devStartTime = node.has("devStartTime") ? node.get("devStartTime").asLong() : 0;
            long devEndTime = node.has("devEndTime") ? node.get("devEndTime").asLong() : 0;

            String customerId = customerMappingService.resolveCustomerId(devSerial);

            DeviceStatusHistoryEntity entity = new DeviceStatusHistoryEntity();
            entity.setDevSerial(devSerial.toUpperCase());
            entity.setCustomerId(customerId);
            entity.setSentryCount(sentryCount);
            entity.setRealHostCount(realHostCount);
            entity.setDevStartTime(devStartTime != 0 ? devStartTime : null);
            entity.setDevEndTime(devEndTime != 0 ? devEndTime : null);
            entity.setReportTime(Instant.now());
            entity.setCreatedAt(Instant.now());

            repository.save(entity);

            log.info("Persisted V1 heartbeat: devSerial={}, customerId={}, realHostCount={}, sentryCount={}",
                devSerial, customerId, realHostCount, sentryCount);

        } catch (Exception e) {
            log.error("Failed to persist status event: {}", message, e);
        }
    }
}
