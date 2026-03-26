package com.threatdetection.ingestion.model;

import java.time.LocalDateTime;

public class HeartbeatEvent {

    private String deviceId;
    private LocalDateTime timestamp;
    private int totalGuards;
    private int onlineDevices;
    private long uptimeSec;

    public HeartbeatEvent() {
    }

    public HeartbeatEvent(String deviceId, LocalDateTime timestamp, int totalGuards, int onlineDevices, long uptimeSec) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.totalGuards = totalGuards;
        this.onlineDevices = onlineDevices;
        this.uptimeSec = uptimeSec;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getTotalGuards() {
        return totalGuards;
    }

    public void setTotalGuards(int totalGuards) {
        this.totalGuards = totalGuards;
    }

    public int getOnlineDevices() {
        return onlineDevices;
    }

    public void setOnlineDevices(int onlineDevices) {
        this.onlineDevices = onlineDevices;
    }

    public long getUptimeSec() {
        return uptimeSec;
    }

    public void setUptimeSec(long uptimeSec) {
        this.uptimeSec = uptimeSec;
    }
}
