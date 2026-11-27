package com.threatdetection.alert.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Applies the per-customer delivery policy before notifications are queued.
 * Severity filtering, quiet hours and rate limiting deliberately live in one
 * service so Kafka ingestion and future notification entry points use the same
 * rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPolicyService {

    private static final String ACQUIRE_RATE_LIMIT_SQL = """
            WITH acquired AS (
                INSERT INTO notification_rate_limits(customer_id, notification_hour, notification_count)
                VALUES (:customerId, :notificationHour, 1)
                ON CONFLICT (customer_id, notification_hour)
                DO UPDATE SET notification_count = notification_rate_limits.notification_count + 1
                WHERE notification_rate_limits.notification_count < :maximum
                RETURNING notification_count
            )
            SELECT notification_count FROM acquired
            """;

    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean shouldNotify(CustomerNotificationConfig config, AlertSeverity severity) {
        if (config == null || severity == null || !Boolean.TRUE.equals(config.getIsActive())) {
            return false;
        }

        AlertSeverity minimum = parseSeverity(config.getMinSeverityLevel(), AlertSeverity.MEDIUM);
        if (severity.getThreshold() < minimum.getThreshold()) {
            return false;
        }

        String configuredSeverities = config.getNotifyOnSeverities();
        if (configuredSeverities == null || configuredSeverities.isBlank()) {
            return true;
        }

        try {
            List<String> severities = objectMapper.readValue(
                    configuredSeverities, new TypeReference<List<String>>() { });
            return severities.stream().anyMatch(value -> severity.name().equalsIgnoreCase(value));
        } catch (Exception exception) {
            // A malformed allow-list must not unexpectedly widen delivery.
            log.error("Invalid notifyOnSeverities for customer {}: {}",
                    config.getCustomerId(), configuredSeverities, exception);
            return false;
        }
    }

    public boolean isQuietHours(CustomerNotificationConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getQuietHoursEnabled())) {
            return false;
        }

        LocalTime start = config.getQuietHoursStart();
        LocalTime end = config.getQuietHoursEnd();
        if (start == null || end == null) {
            log.warn("Quiet hours are enabled for customer {} but start/end is incomplete",
                    config.getCustomerId());
            return false;
        }

        ZoneId zone = resolveZone(config.getQuietHoursTimezone());
        LocalTime now = ZonedDateTime.now(zone).toLocalTime();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Cross-midnight interval, e.g. 22:00-07:00.
        return !now.isBefore(start) || now.isBefore(end);
    }

    /**
     * Atomically reserves one delivery slot in PostgreSQL. Counting queued
     * deliveries (rather than successful sends) prevents a failing endpoint
     * from creating an unbounded retry/notification storm.
     */
    @Transactional
    public boolean tryAcquireDeliverySlot(CustomerNotificationConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnableRateLimiting())) {
            return true;
        }

        int maximum = config.getMaxNotificationsPerHour() == null
                ? 100
                : config.getMaxNotificationsPerHour();
        if (maximum < 1) {
            return false;
        }

        LocalDateTime hour = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("customerId", config.getCustomerId())
                .addValue("notificationHour", Timestamp.valueOf(hour))
                .addValue("maximum", maximum);

        Integer acquired = jdbcTemplate.query(ACQUIRE_RATE_LIMIT_SQL, parameters,
                resultSet -> resultSet.next() ? resultSet.getInt(1) : null);
        return acquired != null;
    }

    private AlertSeverity parseSeverity(String value, AlertSeverity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return AlertSeverity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            log.warn("Unknown minimum severity '{}'; using {}", value, fallback);
            return fallback;
        }
    }

    private ZoneId resolveZone(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(value);
        } catch (DateTimeException exception) {
            log.warn("Invalid quiet-hours timezone '{}'; using Asia/Shanghai", value);
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
