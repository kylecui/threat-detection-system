package com.threatdetection.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.alert.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka消费者服务测试
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private AlertService alertService;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private CustomerNotificationConfigRepository customerNotificationConfigRepository;

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    private Map<String, Object> threatEvent;

    @BeforeEach
    void setUp() {
        threatEvent = new HashMap<>();
        threatEvent.put("customerId", "customer-a");
        threatEvent.put("attackMac", "AA:BB:CC:DD:EE:FF");
        threatEvent.put("attackIp", "192.168.1.100");
        threatEvent.put("threatLevel", "HIGH");
        threatEvent.put("threatScore", 125.5);
        threatEvent.put("attackCount", 12);
        threatEvent.put("uniqueIps", 3);
        threatEvent.put("uniquePorts", 4);
        threatEvent.put("uniqueDevices", 2);
        threatEvent.put("tier", 2);
        threatEvent.put("windowType", "SLIDING");
    }

    @Test
    void consumeThreatEvents_ShouldCreateAlert_WhenValidEvent() throws Exception {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.saveAllAlerts(any(List.class))).thenReturn(List.of(createTestAlert()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ip\":\"192.168.1.100\"}");

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        verify(alertService).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldUseCompactBusinessTitle() throws Exception {
        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.saveAllAlerts(any(List.class))).thenReturn(List.of(createTestAlert()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Alert>> alertsCaptor = ArgumentCaptor.forClass(List.class);
        verify(alertService).saveAllAlerts(alertsCaptor.capture());
        Alert preparedAlert = alertsCaptor.getValue().get(0);

        assertEquals("5分钟持续检测 · AA:BB:CC:DD:EE:FF · 12次", preparedAlert.getTitle());
        assertEquals(AlertSeverity.HIGH, preparedAlert.getSeverity());
        assertTrue(preparedAlert.getDescription().contains("5分钟持续检测窗口内"));
    }

    @Test
    void consumeThreatEvents_ShouldNotPersistInfoAssessmentByDefault() {
        threatEvent.put("threatLevel", "INFO");
        threatEvent.put("threatScore", 1.0);

        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        verifyNoInteractions(deduplicationService);
        verify(alertService, never()).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldAllowInfoWhenMinimumSeverityIsOverridden() throws Exception {
        kafkaConsumerService.setMinimumIngestionSeverity("info");
        threatEvent.put("threatLevel", "INFO");
        threatEvent.put("threatScore", 1.0);

        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.saveAllAlerts(any(List.class))).thenReturn(List.of(createTestAlert()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        verify(alertService).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void setMinimumIngestionSeverity_ShouldFallBackToLowForUnknownValue() {
        kafkaConsumerService.setMinimumIngestionSeverity("not-a-severity");
        threatEvent.put("threatLevel", "INFO");

        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        verify(alertService, never()).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldSkipDuplicateAlert() {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(true);

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        verify(alertService, never()).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldHandleException() {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenThrow(new RuntimeException("Test exception"));

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then - acknowledgment should still be called even with exception
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldHandleThreatAssessmentFormat() throws Exception {
        // Given - threat-assessment服务发布的消息格式
        Map<String, Object> threatAssessmentEvent = new HashMap<>();
        threatAssessmentEvent.put("customerId", "customer-b");
        threatAssessmentEvent.put("attackMac", "11:22:33:44:55:66");
        threatAssessmentEvent.put("attackIp", "203.0.113.8");
        threatAssessmentEvent.put("threatLevel", "CRITICAL");
        threatAssessmentEvent.put("threatScore", 95.5);
        threatAssessmentEvent.put("attackCount", 20);
        threatAssessmentEvent.put("uniqueIps", 2);
        threatAssessmentEvent.put("uniquePorts", 8);
        threatAssessmentEvent.put("uniqueDevices", 1);
        threatAssessmentEvent.put("tier", 1);
        threatAssessmentEvent.put("windowType", "TUMBLING");

        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.saveAllAlerts(any(List.class))).thenReturn(List.of(createTestAlert()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatAssessmentEvent),
                List.of("threat-events"),
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        // 使用saveAllAlerts而不是createAlert
        verify(alertService).saveAllAlerts(any(List.class));
        verify(acknowledgment).acknowledge();
    }

    private Alert createTestAlert() {
        return Alert.builder()
                .id(1L)
                .title("[web-server] SQL_INJECTION 威胁检测")
                .description("检测到SQL注入攻击")
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.NEW)
                .source("web-server")
                .eventType("SQL_INJECTION")
                .build();
    }
}
