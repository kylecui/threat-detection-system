package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.DeviceInventoryEntity;
import com.threatdetection.ingestion.model.DiscoveredHostEntity;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import com.threatdetection.ingestion.model.TopologySnapshotEntity;
import com.threatdetection.ingestion.repository.DeviceInventoryRepository;
import com.threatdetection.ingestion.repository.DiscoveredHostRepository;
import com.threatdetection.ingestion.repository.TopologySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

@Service
public class HeartbeatPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatPersistenceService.class);

    private final DeviceInventoryRepository deviceInventoryRepository;
    private final TopologySnapshotRepository topologySnapshotRepository;
    private final DiscoveredHostRepository discoveredHostRepository;

    public HeartbeatPersistenceService(
            DeviceInventoryRepository deviceInventoryRepository,
            TopologySnapshotRepository topologySnapshotRepository,
            DiscoveredHostRepository discoveredHostRepository
    ) {
        this.deviceInventoryRepository = deviceInventoryRepository;
        this.topologySnapshotRepository = topologySnapshotRepository;
        this.discoveredHostRepository = discoveredHostRepository;
    }

    @Transactional
    public void persistHeartbeat(HeartbeatEvent heartbeat) {
        Instant now = Instant.now();
        Instant snapshotInstant = heartbeat.getTimestamp().atZone(ZoneId.systemDefault()).toInstant();

        DeviceInventoryEntity inventory = deviceInventoryRepository.findById(heartbeat.getDeviceId())
                .orElseGet(() -> {
                    DeviceInventoryEntity newEntity = new DeviceInventoryEntity();
                    newEntity.setDeviceId(heartbeat.getDeviceId());
                    newEntity.setCreatedAt(now);
                    return newEntity;
                });

        inventory.setCustomerId(heartbeat.getCustomerId());
        inventory.setFirmwareVersion(heartbeat.getFirmwareVersion());
        inventory.setUptime(heartbeat.getUptimeSec());
        inventory.setLastSeen(snapshotInstant);
        inventory.setUpdatedAt(now);
        deviceInventoryRepository.save(inventory);

        TopologySnapshotEntity snapshot = new TopologySnapshotEntity();
        snapshot.setDeviceId(heartbeat.getDeviceId());
        snapshot.setCustomerId(heartbeat.getCustomerId());
        snapshot.setSnapshotTime(snapshotInstant);
        snapshot.setTotalIpsMonitored(heartbeat.getTotalGuards());
        snapshot.setActiveDecoyCount(heartbeat.getOnlineDevices());
        snapshot.setNetworkInterfaces(heartbeat.getNetworkInterfacesJson() == null ? "[]" : heartbeat.getNetworkInterfacesJson());
        snapshot.setRawTopology(heartbeat.getRawTopologyJson() == null ? "{}" : heartbeat.getRawTopologyJson());
        topologySnapshotRepository.save(snapshot);

        int hostsUpserted = 0;
        if (heartbeat.getDevices() != null) {
            for (HeartbeatEvent.DiscoveredHostData hostData : heartbeat.getDevices()) {
                if (hostData.getMacAddress() == null || hostData.getMacAddress().isBlank()) {
                    continue;
                }
                if (hostData.getIpAddress() == null || hostData.getIpAddress().isBlank()) {
                    continue;
                }

                DiscoveredHostEntity hostEntity = discoveredHostRepository
                        .findByDeviceIdAndMacAddress(heartbeat.getDeviceId(), hostData.getMacAddress())
                        .orElseGet(() -> {
                            DiscoveredHostEntity newHost = new DiscoveredHostEntity();
                            newHost.setDeviceId(heartbeat.getDeviceId());
                            newHost.setCustomerId(heartbeat.getCustomerId());
                            newHost.setMacAddress(hostData.getMacAddress());
                            newHost.setFirstSeen(now);
                            return newHost;
                        });

                hostEntity.setCustomerId(heartbeat.getCustomerId());
                hostEntity.setIpAddress(hostData.getIpAddress());
                hostEntity.setVlanId(hostData.getVlanId());
                hostEntity.setDecoy(hostData.isDecoy());
                hostEntity.setLastSeen(now);
                discoveredHostRepository.save(hostEntity);
                hostsUpserted++;
            }
        }

        logger.info("Persisted heartbeat: deviceId={}, customerId={}, hostsUpserted={}",
                heartbeat.getDeviceId(), heartbeat.getCustomerId(), hostsUpserted);
    }
}
