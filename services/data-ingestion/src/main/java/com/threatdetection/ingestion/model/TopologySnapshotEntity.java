package com.threatdetection.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "topology_snapshots")
public class TopologySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    @Column(name = "total_ips_monitored", nullable = false)
    private int totalIpsMonitored;

    @Column(name = "active_decoy_count", nullable = false)
    private int activeDecoyCount;

    @Column(name = "network_interfaces", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String networkInterfaces;

    @Column(name = "raw_topology", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawTopology;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(Instant snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    public int getTotalIpsMonitored() {
        return totalIpsMonitored;
    }

    public void setTotalIpsMonitored(int totalIpsMonitored) {
        this.totalIpsMonitored = totalIpsMonitored;
    }

    public int getActiveDecoyCount() {
        return activeDecoyCount;
    }

    public void setActiveDecoyCount(int activeDecoyCount) {
        this.activeDecoyCount = activeDecoyCount;
    }

    public String getNetworkInterfaces() {
        return networkInterfaces;
    }

    public void setNetworkInterfaces(String networkInterfaces) {
        this.networkInterfaces = networkInterfaces;
    }

    public String getRawTopology() {
        return rawTopology;
    }

    public void setRawTopology(String rawTopology) {
        this.rawTopology = rawTopology;
    }
}
