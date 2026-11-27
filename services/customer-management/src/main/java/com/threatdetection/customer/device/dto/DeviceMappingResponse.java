package com.threatdetection.customer.device.dto;

import com.threatdetection.customer.device.model.DeviceMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 设备映射响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMappingResponse {

    private Long id;

    private String devSerial;

    private String customerId;

    private Boolean isActive;

    private String description;

    private Instant createdAt;

    private Instant updatedAt;

    private Integer realHostCount;

    /** Latest heartbeat accepted from this device. Null means the platform has never seen one. */
    private Instant lastHeartbeatAt;

    /** Latest raw attack event produced by this device. */
    private Instant lastAttackAt;

    /** Latest time an attack event from this device reached platform storage. */
    private Instant lastIngestedAt;

    /** Raw attack events received from this device during the last 24 hours. */
    private Long attackEvents24h;

    /** Latest derived threat associated with an attacker observed by this device. */
    private Instant lastThreatAt;

    /** Deprecated compatibility alias for heartbeatState. */
    private String connectionState;

    /** DISABLED, NEVER_SEEN, ONLINE, DELAYED or OFFLINE, based only on V1 heartbeats. */
    private String heartbeatState;

    /** DISABLED, NEVER_SEEN, ACTIVE or IDLE, based on any accepted heartbeat or attack event. */
    private String dataActivityState;

    /** Expected V1 Syslog heartbeat period used by the platform, in seconds. */
    private Long expectedHeartbeatIntervalSeconds;

    /** Age at which a heartbeat becomes delayed, in seconds. */
    private Long heartbeatDelayedAfterSeconds;

    /** Age at which a device is considered offline, in seconds. */
    private Long heartbeatOfflineAfterSeconds;

    /** WAITING_FOR_DATA, NO_ATTACK_DATA, RECEIVING, PROCESSED, PROCESSING_DELAY or DISABLED. */
    private String pipelineState;

    public static DeviceMappingResponse fromEntity(DeviceMapping mapping) {
        return fromEntity(mapping, null);
    }

    public static DeviceMappingResponse fromEntity(DeviceMapping mapping, Integer realHostCount) {
        return fromEntity(mapping, realHostCount, null, null, null, 0L, null,
                Boolean.TRUE.equals(mapping.getIsActive()) ? "NEVER_SEEN" : "DISABLED",
                Boolean.TRUE.equals(mapping.getIsActive()) ? "NEVER_SEEN" : "DISABLED",
                Boolean.TRUE.equals(mapping.getIsActive()) ? "NEVER_SEEN" : "DISABLED",
                1800L, 2100L, 3900L,
                Boolean.TRUE.equals(mapping.getIsActive()) ? "WAITING_FOR_DATA" : "DISABLED");
    }

    public static DeviceMappingResponse fromEntity(
            DeviceMapping mapping,
            Integer realHostCount,
            Instant lastHeartbeatAt,
            Instant lastAttackAt,
            Instant lastIngestedAt,
            Long attackEvents24h,
            Instant lastThreatAt,
            String connectionState,
            String heartbeatState,
            String dataActivityState,
            Long expectedHeartbeatIntervalSeconds,
            Long heartbeatDelayedAfterSeconds,
            Long heartbeatOfflineAfterSeconds,
            String pipelineState) {
        return DeviceMappingResponse.builder()
                .id(mapping.getId())
                .devSerial(mapping.getDevSerial())
                .customerId(mapping.getCustomerId())
                .isActive(mapping.getIsActive())
                .description(mapping.getDescription())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .realHostCount(realHostCount)
                .lastHeartbeatAt(lastHeartbeatAt)
                .lastAttackAt(lastAttackAt)
                .lastIngestedAt(lastIngestedAt)
                .attackEvents24h(attackEvents24h == null ? 0L : attackEvents24h)
                .lastThreatAt(lastThreatAt)
                .connectionState(connectionState)
                .heartbeatState(heartbeatState)
                .dataActivityState(dataActivityState)
                .expectedHeartbeatIntervalSeconds(expectedHeartbeatIntervalSeconds)
                .heartbeatDelayedAfterSeconds(heartbeatDelayedAfterSeconds)
                .heartbeatOfflineAfterSeconds(heartbeatOfflineAfterSeconds)
                .pipelineState(pipelineState)
                .build();
    }
}
