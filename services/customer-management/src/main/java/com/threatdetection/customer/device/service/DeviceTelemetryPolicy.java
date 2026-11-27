package com.threatdetection.customer.device.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Central policy for interpreting the V1 Syslog device heartbeat and data activity.
 *
 * <p>The JZ client sends V1 Syslog heartbeats every 1,800 seconds by default. The
 * platform therefore must not use a five-minute fixed timeout. Defaults below mark
 * the first missed heartbeat as delayed after 35 minutes and mark the device offline
 * only after two expected intervals plus a five-minute grace period.</p>
 */
@Component
public class DeviceTelemetryPolicy {

    private final long expectedHeartbeatIntervalSeconds;
    private final long heartbeatDelayedAfterSeconds;
    private final long heartbeatOfflineAfterSeconds;
    private final long dataActiveWindowSeconds;

    public DeviceTelemetryPolicy(
            @Value("${device.telemetry.expected-heartbeat-interval-seconds:1800}")
            long expectedHeartbeatIntervalSeconds,
            @Value("${device.telemetry.heartbeat-delayed-after-seconds:2100}")
            long heartbeatDelayedAfterSeconds,
            @Value("${device.telemetry.heartbeat-offline-after-seconds:3900}")
            long heartbeatOfflineAfterSeconds,
            @Value("${device.telemetry.data-active-window-seconds:2100}")
            long dataActiveWindowSeconds) {
        if (expectedHeartbeatIntervalSeconds <= 0) {
            throw new IllegalArgumentException("expected heartbeat interval must be positive");
        }
        if (heartbeatDelayedAfterSeconds < expectedHeartbeatIntervalSeconds) {
            throw new IllegalArgumentException("heartbeat delayed threshold must cover one heartbeat interval");
        }
        if (heartbeatOfflineAfterSeconds <= heartbeatDelayedAfterSeconds) {
            throw new IllegalArgumentException("heartbeat offline threshold must be greater than delayed threshold");
        }
        if (dataActiveWindowSeconds <= 0) {
            throw new IllegalArgumentException("data active window must be positive");
        }
        this.expectedHeartbeatIntervalSeconds = expectedHeartbeatIntervalSeconds;
        this.heartbeatDelayedAfterSeconds = heartbeatDelayedAfterSeconds;
        this.heartbeatOfflineAfterSeconds = heartbeatOfflineAfterSeconds;
        this.dataActiveWindowSeconds = dataActiveWindowSeconds;
    }

    public String heartbeatState(boolean bindingActive, Instant lastHeartbeatAt, Instant now) {
        if (!bindingActive) {
            return "DISABLED";
        }
        if (lastHeartbeatAt == null) {
            return "NEVER_SEEN";
        }
        long ageSeconds = ageSeconds(lastHeartbeatAt, now);
        if (ageSeconds <= heartbeatDelayedAfterSeconds) {
            return "ONLINE";
        }
        if (ageSeconds <= heartbeatOfflineAfterSeconds) {
            return "DELAYED";
        }
        return "OFFLINE";
    }

    public String dataActivityState(boolean bindingActive, Instant lastDataAt, Instant now) {
        if (!bindingActive) {
            return "DISABLED";
        }
        if (lastDataAt == null) {
            return "NEVER_SEEN";
        }
        return ageSeconds(lastDataAt, now) <= dataActiveWindowSeconds ? "ACTIVE" : "IDLE";
    }

    private long ageSeconds(Instant timestamp, Instant now) {
        return Math.max(0, Duration.between(timestamp, now).getSeconds());
    }

    public long getExpectedHeartbeatIntervalSeconds() {
        return expectedHeartbeatIntervalSeconds;
    }

    public long getHeartbeatDelayedAfterSeconds() {
        return heartbeatDelayedAfterSeconds;
    }

    public long getHeartbeatOfflineAfterSeconds() {
        return heartbeatOfflineAfterSeconds;
    }

    public long getDataActiveWindowSeconds() {
        return dataActiveWindowSeconds;
    }
}
