package com.threatdetection.customer.device.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceTelemetryPolicyTest {

    private final DeviceTelemetryPolicy policy = new DeviceTelemetryPolicy(1800, 2100, 3900, 2100);
    private final Instant now = Instant.parse("2026-09-24T02:30:00Z");

    @Test
    void thirtyMinuteSyslogHeartbeatRemainsOnlineThroughGraceWindow() {
        assertEquals("ONLINE", policy.heartbeatState(true, now.minusSeconds(1800), now));
        assertEquals("ONLINE", policy.heartbeatState(true, now.minusSeconds(2100), now));
    }

    @Test
    void missedHeartbeatBecomesDelayedBeforeItBecomesOffline() {
        assertEquals("DELAYED", policy.heartbeatState(true, now.minusSeconds(2101), now));
        assertEquals("DELAYED", policy.heartbeatState(true, now.minusSeconds(3900), now));
        assertEquals("OFFLINE", policy.heartbeatState(true, now.minusSeconds(3901), now));
    }

    @Test
    void dataActivityIsIndependentFromHeartbeatState() {
        assertEquals("OFFLINE", policy.heartbeatState(true, now.minusSeconds(4000), now));
        assertEquals("ACTIVE", policy.dataActivityState(true, now.minusSeconds(60), now));
        assertEquals("IDLE", policy.dataActivityState(true, now.minusSeconds(2101), now));
    }

    @Test
    void inactiveAndNeverSeenStatesAreExplicit() {
        assertEquals("DISABLED", policy.heartbeatState(false, now, now));
        assertEquals("DISABLED", policy.dataActivityState(false, now, now));
        assertEquals("NEVER_SEEN", policy.heartbeatState(true, null, now));
        assertEquals("NEVER_SEEN", policy.dataActivityState(true, null, now));
    }
}
