package com.threatdetection.customer.device.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Runs telemetry ownership repair outside the interactive bind transaction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceBackfillService {

    private final JdbcTemplate jdbcTemplate;

    @Async
    public void backfillCustomerId(String devSerial, String customerId) {
        try {
            String normalizedSerial = devSerial.trim().toUpperCase(Locale.ROOT);
            int updated = jdbcTemplate.update(
                    "UPDATE attack_events SET customer_id = ? " +
                    "WHERE UPPER(BTRIM(dev_serial)) = ? AND customer_id = 'unknown'",
                    customerId,
                    normalizedSerial);
            if (updated > 0) {
                log.info("Backfilled {} attack event rows for device {}", updated, normalizedSerial);
            }
        } catch (Exception ex) {
            log.warn("Failed to backfill attack events for device {}: {}", devSerial, ex.getMessage());
        }
    }
}
