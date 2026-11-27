package com.threatdetection.alert.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NotificationPolicyServiceTest {

    private final NotificationPolicyService service = new NotificationPolicyService(
            new ObjectMapper(), mock(NamedParameterJdbcTemplate.class));

    @Test
    void severityMustSatisfyMinimumAndExplicitAllowList() {
        CustomerNotificationConfig config = CustomerNotificationConfig.builder()
                .customerId("customer-a")
                .isActive(true)
                .minSeverityLevel("HIGH")
                .notifyOnSeverities("[\"HIGH\",\"CRITICAL\"]")
                .build();

        assertFalse(service.shouldNotify(config, AlertSeverity.MEDIUM));
        assertTrue(service.shouldNotify(config, AlertSeverity.HIGH));
        assertTrue(service.shouldNotify(config, AlertSeverity.CRITICAL));
    }

    @Test
    void malformedAllowListFailsClosed() {
        CustomerNotificationConfig config = CustomerNotificationConfig.builder()
                .customerId("customer-a")
                .isActive(true)
                .minSeverityLevel("LOW")
                .notifyOnSeverities("not-json")
                .build();

        assertFalse(service.shouldNotify(config, AlertSeverity.CRITICAL));
    }

    @Test
    void equalQuietHourBoundariesMeanAllDayQuiet() {
        CustomerNotificationConfig config = CustomerNotificationConfig.builder()
                .customerId("customer-a")
                .quietHoursEnabled(true)
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(22, 0))
                .quietHoursTimezone("Asia/Shanghai")
                .build();

        assertTrue(service.isQuietHours(config));
    }

    @Test
    void inactiveConfigurationNeverNotifies() {
        CustomerNotificationConfig config = CustomerNotificationConfig.builder()
                .customerId("customer-a")
                .isActive(false)
                .minSeverityLevel("LOW")
                .build();

        assertFalse(service.shouldNotify(config, AlertSeverity.CRITICAL));
    }
}
