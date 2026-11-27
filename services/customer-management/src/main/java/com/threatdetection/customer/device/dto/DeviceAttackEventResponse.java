package com.threatdetection.customer.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A safe, compact raw-event view used to verify that a bound device is sending data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAttackEventResponse {
    private Long id;
    private String devSerial;
    private String attackMac;
    private String attackIp;
    private String responseIp;
    private Integer responsePort;
    private Instant eventTimestamp;
    private Instant receivedAt;
}
