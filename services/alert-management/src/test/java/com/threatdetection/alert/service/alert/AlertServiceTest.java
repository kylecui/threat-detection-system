package com.threatdetection.alert.service.alert;

import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import com.threatdetection.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private AlertRepository alertRepository;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        alertService = new AlertService();
        ReflectionTestUtils.setField(alertService, "alertRepository", alertRepository);
        ReflectionTestUtils.setField(alertService, "deduplicationService", mock(DeduplicationService.class));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void assigningAnOpenAlertMovesItIntoProgress() {
        Alert alert = openAlert();
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));

        Alert assigned = alertService.assignAlert(7L, "  analyst  ");

        assertEquals("analyst", assigned.getAssignedTo());
        assertEquals(AlertStatus.IN_PROGRESS, assigned.getStatus());
    }

    @Test
    void assigningAResolvedAlertDoesNotReopenIt() {
        Alert alert = openAlert();
        alert.setStatus(AlertStatus.RESOLVED);
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));

        Alert assigned = alertService.assignAlert(7L, "analyst");

        assertEquals(AlertStatus.RESOLVED, assigned.getStatus());
    }

    @Test
    void escalationMarkerIsPersistedAndCanBeCleared() {
        Alert alert = openAlert();
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));

        Alert escalated = alertService.setEscalation(7L, 2, "  SLA timeout  ");
        assertEquals(2, escalated.getEscalationLevel());
        assertEquals("SLA timeout", escalated.getEscalationReason());
        assertEquals(AlertStatus.NEW, escalated.getStatus());

        Alert cleared = alertService.clearEscalation(7L);
        assertEquals(0, cleared.getEscalationLevel());
        assertNull(cleared.getEscalationReason());
    }

    private Alert openAlert() {
        return Alert.builder()
                .id(7L)
                .title("test alert")
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.NEW)
                .escalationLevel(0)
                .build();
    }
}
