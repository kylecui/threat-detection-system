package com.threatdetection.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.alert.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    private Map<String, Object> threatEvent;

    @BeforeEach
    void setUp() {
        threatEvent = new HashMap<>();
        threatEvent.put("eventType", "SQL_INJECTION");
        threatEvent.put("source", "web-server");
        threatEvent.put("message", "检测到SQL注入攻击");
        threatEvent.put("severity", 3);
        threatEvent.put("metadata", Map.of("ip", "192.168.1.100", "userAgent", "Mozilla/5.0"));
    }

    @Test
    void consumeThreatEvents_ShouldCreateAlert_WhenValidEvent() throws Exception {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.createAlert(any(Alert.class))).thenReturn(createTestAlert());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ip\":\"192.168.1.100\"}");

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                "threat-events",
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        verify(alertService).createAlert(any(Alert.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldSkipDuplicateAlert() {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(true);

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                "threat-events",
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        verify(alertService, never()).createAlert(any(Alert.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeThreatEvents_ShouldHandleException() {
        // Given
        when(deduplicationService.isDuplicate(any(Alert.class))).thenThrow(new RuntimeException("Test exception"));

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatEvent),
                "threat-events",
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
        threatAssessmentEvent.put("title", "Critical Network Anomaly Detected");
        threatAssessmentEvent.put("description", "Multiple failed login attempts from suspicious IP");
        threatAssessmentEvent.put("threatScore", 95.5);
        threatAssessmentEvent.put("severity", 0); // CRITICAL

        when(deduplicationService.isDuplicate(any(Alert.class))).thenReturn(false);
        when(alertService.createAlert(any(Alert.class))).thenReturn(createTestAlert());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        kafkaConsumerService.consumeThreatEvents(
                List.of(threatAssessmentEvent),
                "threat-events",
                List.of(0),
                List.of(0L),
                acknowledgment
        );

        // Then
        verify(deduplicationService).isDuplicate(any(Alert.class));
        verify(alertService).createAlert(argThat(alert -> {
            // 验证字段映射是否正确
            return alert.getTitle().contains("[threat-assessment-service] Critical Network Anomaly Detected") &&
                   alert.getDescription().equals("Multiple failed login attempts from suspicious IP") &&
                   alert.getSeverity() == AlertSeverity.MEDIUM && // severity=0 映射为 MEDIUM
                   alert.getThreatScore() == 95.5 &&
                   alert.getSource().equals("threat-assessment-service") &&
                   alert.getEventType().equals("THREAT_ASSESSMENT");
        }));
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