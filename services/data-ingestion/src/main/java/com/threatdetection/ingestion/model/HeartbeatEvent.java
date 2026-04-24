package com.threatdetection.ingestion.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatEvent {

    private String deviceId;
    private LocalDateTime timestamp;
    private int totalGuards;
    private int onlineDevices;
    private long uptimeSec;
    private String customerId;
    private String firmwareVersion;
    private String networkInterfacesJson;
    private String rawTopologyJson;
    private List<DiscoveredHostData> devices;
    private String schemaVersion = "1.0";

    public HeartbeatEvent() {
        this.devices = new ArrayList<>();
        this.networkInterfacesJson = "[]";
    }

    public HeartbeatEvent(String deviceId, LocalDateTime timestamp, int totalGuards, int onlineDevices, long uptimeSec) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.totalGuards = totalGuards;
        this.onlineDevices = onlineDevices;
        this.uptimeSec = uptimeSec;
        this.devices = new ArrayList<>();
        this.networkInterfacesJson = "[]";
    }

    public HeartbeatEvent(
            String deviceId,
            LocalDateTime timestamp,
            int totalGuards,
            int onlineDevices,
            long uptimeSec,
            String customerId,
            String firmwareVersion,
            String networkInterfacesJson,
            String rawTopologyJson,
            List<DiscoveredHostData> devices
    ) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.totalGuards = totalGuards;
        this.onlineDevices = onlineDevices;
        this.uptimeSec = uptimeSec;
        this.customerId = customerId;
        this.firmwareVersion = firmwareVersion;
        this.networkInterfacesJson = networkInterfacesJson;
        this.rawTopologyJson = rawTopologyJson;
        this.devices = devices;
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

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getNetworkInterfacesJson() {
        return networkInterfacesJson;
    }

    public void setNetworkInterfacesJson(String networkInterfacesJson) {
        this.networkInterfacesJson = networkInterfacesJson;
    }

    public String getRawTopologyJson() {
        return rawTopologyJson;
    }

    public void setRawTopologyJson(String rawTopologyJson) {
        this.rawTopologyJson = rawTopologyJson;
    }

    public List<DiscoveredHostData> getDevices() {
        return devices;
    }

    public void setDevices(List<DiscoveredHostData> devices) {
        this.devices = devices;
    }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public static class DiscoveredHostData {
        private String macAddress;
        private String ipAddress;
        private int vlanId;
        private boolean isDecoy;

        public DiscoveredHostData() {
        }

        public DiscoveredHostData(String macAddress, String ipAddress, int vlanId, boolean isDecoy) {
            this.macAddress = macAddress;
            this.ipAddress = ipAddress;
            this.vlanId = vlanId;
            this.isDecoy = isDecoy;
        }

        public String getMacAddress() {
            return macAddress;
        }

        public void setMacAddress(String macAddress) {
            this.macAddress = macAddress;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public int getVlanId() {
            return vlanId;
        }

        public void setVlanId(int vlanId) {
            this.vlanId = vlanId;
        }

        public boolean isDecoy() {
            return isDecoy;
        }

        public void setDecoy(boolean decoy) {
            isDecoy = decoy;
        }
    }
}
