package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.DeviceInventoryEntity;
import com.threatdetection.ingestion.model.DiscoveredHostEntity;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import com.threatdetection.ingestion.model.TopologySnapshotEntity;
import com.threatdetection.ingestion.repository.DeviceInventoryRepository;
import com.threatdetection.ingestion.repository.DiscoveredHostRepository;
import com.threatdetection.ingestion.repository.TopologySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HeartbeatPersistenceServiceTest {

    @Autowired
    private HeartbeatPersistenceService heartbeatPersistenceService;

    @Autowired
    private DeviceInventoryRepository deviceInventoryRepository;

    @Autowired
    private TopologySnapshotRepository topologySnapshotRepository;

    @Autowired
    private DiscoveredHostRepository discoveredHostRepository;

    @BeforeEach
    void setUp() {
        discoveredHostRepository.deleteAll();
        topologySnapshotRepository.deleteAll();
        deviceInventoryRepository.deleteAll();
    }

    @Test
    void testPersistHeartbeat_NewDevice() {
        HeartbeatEvent heartbeat = buildHeartbeat(
                "jz-sniff-001",
                "customer-001",
                "2.1.0",
                86400L,
                25,
                3,
                List.of(
                        host("AA:BB:CC:DD:EE:01", "192.168.1.100", 10, false),
                        host("AA:BB:CC:DD:EE:02", "192.168.1.200", 10, true)
                )
        );

        heartbeatPersistenceService.persistHeartbeat(heartbeat);

        DeviceInventoryEntity inventory = deviceInventoryRepository.findById("jz-sniff-001").orElseThrow();
        assertEquals("customer-001", inventory.getCustomerId());
        assertEquals("2.1.0", inventory.getFirmwareVersion());
        assertEquals(86400L, inventory.getUptime());
        assertNotNull(inventory.getCreatedAt());
        assertNotNull(inventory.getUpdatedAt());
        assertNotNull(inventory.getLastSeen());

        List<TopologySnapshotEntity> snapshots = topologySnapshotRepository.findByDeviceIdOrderBySnapshotTimeDesc("jz-sniff-001");
        assertEquals(1, snapshots.size());
        assertEquals(25, snapshots.get(0).getTotalIpsMonitored());
        assertEquals(3, snapshots.get(0).getActiveDecoyCount());
        assertNotNull(snapshots.get(0).getNetworkInterfaces());
        assertNotNull(snapshots.get(0).getRawTopology());

        List<DiscoveredHostEntity> hosts = discoveredHostRepository.findByDeviceId("jz-sniff-001");
        assertEquals(2, hosts.size());
    }

    @Test
    void testPersistHeartbeat_ExistingDevice() {
        HeartbeatEvent initial = buildHeartbeat(
                "jz-sniff-001",
                "customer-001",
                "2.1.0",
                1000L,
                10,
                2,
                List.of(host("AA:BB:CC:DD:EE:01", "192.168.1.100", 1, false))
        );
        heartbeatPersistenceService.persistHeartbeat(initial);

        HeartbeatEvent updated = buildHeartbeat(
                "jz-sniff-001",
                "customer-001",
                "2.2.0",
                2000L,
                12,
                3,
                List.of(host("AA:BB:CC:DD:EE:01", "192.168.1.101", 1, false))
        );
        heartbeatPersistenceService.persistHeartbeat(updated);

        assertEquals(1, deviceInventoryRepository.count());
        DeviceInventoryEntity inventory = deviceInventoryRepository.findById("jz-sniff-001").orElseThrow();
        assertEquals("2.2.0", inventory.getFirmwareVersion());
        assertEquals(2000L, inventory.getUptime());

        List<TopologySnapshotEntity> snapshots = topologySnapshotRepository.findByDeviceIdOrderBySnapshotTimeDesc("jz-sniff-001");
        assertEquals(2, snapshots.size());
        assertEquals(12, snapshots.get(0).getTotalIpsMonitored());
    }

    @Test
    void testPersistHeartbeat_UpsertDiscoveredHosts() {
        HeartbeatEvent first = buildHeartbeat(
                "jz-sniff-001",
                "customer-001",
                "2.1.0",
                1000L,
                10,
                2,
                List.of(host("AA:BB:CC:DD:EE:01", "192.168.1.100", 1, false))
        );
        heartbeatPersistenceService.persistHeartbeat(first);

        DiscoveredHostEntity existingBefore = discoveredHostRepository
                .findByDeviceIdAndMacAddress("jz-sniff-001", "AA:BB:CC:DD:EE:01")
                .orElseThrow();

        HeartbeatEvent second = buildHeartbeat(
                "jz-sniff-001",
                "customer-001",
                "2.1.0",
                1200L,
                11,
                2,
                List.of(
                        host("AA:BB:CC:DD:EE:01", "192.168.1.150", 20, true),
                        host("AA:BB:CC:DD:EE:02", "192.168.1.200", 30, false)
                )
        );
        heartbeatPersistenceService.persistHeartbeat(second);

        List<DiscoveredHostEntity> allHosts = discoveredHostRepository.findByDeviceId("jz-sniff-001");
        assertEquals(2, allHosts.size());

        DiscoveredHostEntity updatedHost = discoveredHostRepository
                .findByDeviceIdAndMacAddress("jz-sniff-001", "AA:BB:CC:DD:EE:01")
                .orElseThrow();
        assertEquals("192.168.1.150", updatedHost.getIpAddress());
        assertEquals(20, updatedHost.getVlanId());
        assertTrue(updatedHost.isDecoy());
        assertEquals(existingBefore.getFirstSeen(), updatedHost.getFirstSeen());

        DiscoveredHostEntity newHost = discoveredHostRepository
                .findByDeviceIdAndMacAddress("jz-sniff-001", "AA:BB:CC:DD:EE:02")
                .orElseThrow();
        assertEquals("192.168.1.200", newHost.getIpAddress());
        assertFalse(newHost.isDecoy());
    }

    private HeartbeatEvent buildHeartbeat(
            String deviceId,
            String customerId,
            String firmwareVersion,
            long uptimeSec,
            int totalGuards,
            int onlineDevices,
            List<HeartbeatEvent.DiscoveredHostData> hosts
    ) {
        HeartbeatEvent heartbeat = new HeartbeatEvent(deviceId, LocalDateTime.now(), totalGuards, onlineDevices, uptimeSec);
        heartbeat.setCustomerId(customerId);
        heartbeat.setFirmwareVersion(firmwareVersion);
        heartbeat.setNetworkInterfacesJson("[{\"name\":\"eth0\",\"ip\":\"192.168.1.1\",\"mac\":\"00:11:22:33:44:55\"}]");
        heartbeat.setRawTopologyJson("{\"total_guards\":" + totalGuards + ",\"online_devices\":" + onlineDevices + "}");
        heartbeat.setDevices(new ArrayList<>(hosts));
        return heartbeat;
    }

    private HeartbeatEvent.DiscoveredHostData host(String mac, String ip, int vlanId, boolean isDecoy) {
        HeartbeatEvent.DiscoveredHostData data = new HeartbeatEvent.DiscoveredHostData();
        data.setMacAddress(mac);
        data.setIpAddress(ip);
        data.setVlanId(vlanId);
        data.setDecoy(isDecoy);
        return data;
    }
}
