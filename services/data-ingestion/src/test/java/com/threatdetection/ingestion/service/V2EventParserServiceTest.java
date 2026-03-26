package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class V2EventParserServiceTest {

    private DevSerialToCustomerMappingService mappingService;
    private V2EventParserService parserService;

    @BeforeEach
    void setUp() {
        mappingService = Mockito.mock(DevSerialToCustomerMappingService.class);
        Mockito.when(mappingService.resolveCustomerId("jz-sniff-001")).thenReturn("customer-001");
        parserService = new V2EventParserService(new ObjectMapper(), mappingService);
    }

    @Test
    void parseAttackEventReturnsMappedAttackEvent() {
        String payload = """
                {
                  "v": 2,
                  "device_id": "jz-sniff-001",
                  "seq": 100,
                  "ts": "2026-03-23T10:15:32+08:00",
                  "type": "attack",
                  "data": {
                    "src_ip": "10.0.1.100",
                    "src_mac": "aa:bb:cc:11:22:33",
                    "guard_ip": "10.0.1.50",
                    "dst_port": 445,
                    "ifindex": 3,
                    "vlan_id": 100,
                    "ethertype": 2048,
                    "ip_proto": 6
                  }
                }
                """;

        Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/attack", payload);

        assertTrue(parsed.isPresent());
        assertInstanceOf(AttackEvent.class, parsed.get());

        AttackEvent attackEvent = (AttackEvent) parsed.get();
        assertEquals("jz-sniff-001", attackEvent.getDevSerial());
        assertEquals("aa:bb:cc:11:22:33", attackEvent.getAttackMac());
        assertEquals("10.0.1.100", attackEvent.getAttackIp());
        assertEquals("10.0.1.50", attackEvent.getResponseIp());
        assertEquals(445, attackEvent.getResponsePort());
        assertEquals(3, attackEvent.getLineId());
        assertEquals(100, attackEvent.getVlanId());
        assertEquals(2048, attackEvent.getEthType());
        assertEquals(6, attackEvent.getIpType());
        assertEquals(1, attackEvent.getLogType());
        assertEquals(1, attackEvent.getSubType());
        assertEquals("customer-001", attackEvent.getCustomerId());
        assertEquals(1774232132L, attackEvent.getLogTime());
    }

    @Test
    void parseHeartbeatEventReturnsHeartbeatEvent() {
        String payload = """
                {
                  "v": 2,
                  "device_id": "jz-sniff-001",
                  "seq": 200,
                  "ts": "2026-03-23T10:30:00+08:00",
                  "type": "heartbeat",
                  "data": {
                    "uptime_sec": 86400,
                    "total_guards": 55,
                    "online_devices": 120,
                    "firmware_version": "2.1.0",
                    "network_interfaces": [
                      {"name": "eth0", "ip": "192.168.1.1", "mac": "00:11:22:33:44:55"}
                    ],
                    "devices": [
                      {"mac": "AA:BB:CC:DD:EE:01", "ip": "192.168.1.100", "vlan_id": 10, "is_decoy": false},
                      {"mac": "AA:BB:CC:DD:EE:02", "ip": "192.168.1.200", "vlan_id": 20, "is_decoy": true}
                    ]
                  }
                }
                """;

        Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/heartbeat", payload);

        assertTrue(parsed.isPresent());
        assertInstanceOf(HeartbeatEvent.class, parsed.get());
        HeartbeatEvent heartbeatEvent = (HeartbeatEvent) parsed.get();
        assertEquals("jz-sniff-001", heartbeatEvent.getDeviceId());
        assertEquals(55, heartbeatEvent.getTotalGuards());
        assertEquals(120, heartbeatEvent.getOnlineDevices());
        assertEquals(86400L, heartbeatEvent.getUptimeSec());
        assertNotNull(heartbeatEvent.getTimestamp());
        assertEquals("customer-001", heartbeatEvent.getCustomerId());
        assertEquals("2.1.0", heartbeatEvent.getFirmwareVersion());
        assertEquals("[{\"name\":\"eth0\",\"ip\":\"192.168.1.1\",\"mac\":\"00:11:22:33:44:55\"}]", heartbeatEvent.getNetworkInterfacesJson());
        assertNotNull(heartbeatEvent.getRawTopologyJson());
        assertTrue(heartbeatEvent.getRawTopologyJson().contains("\"firmware_version\":\"2.1.0\""));
        assertNotNull(heartbeatEvent.getDevices());
        assertEquals(2, heartbeatEvent.getDevices().size());
        assertEquals("AA:BB:CC:DD:EE:01", heartbeatEvent.getDevices().get(0).getMacAddress());
        assertEquals("192.168.1.100", heartbeatEvent.getDevices().get(0).getIpAddress());
        assertEquals(10, heartbeatEvent.getDevices().get(0).getVlanId());
        assertFalse(heartbeatEvent.getDevices().get(0).isDecoy());
        assertTrue(heartbeatEvent.getDevices().get(1).isDecoy());
    }

    @Test
    void parseDeferredTypesReturnEmpty() {
        String[] types = {"sniffer", "threat", "bg", "audit", "policy"};

        for (String type : types) {
            String payload = """
                    {
                      "v": 2,
                      "device_id": "jz-sniff-001",
                      "seq": 1,
                      "ts": "2026-03-23T10:30:00+08:00",
                      "type": "%s",
                      "data": {}
                    }
                    """.formatted(type);

            Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/" + type, payload);
            assertTrue(parsed.isEmpty(), "Expected empty for deferred type=" + type);
        }
    }

    @Test
    void parseInvalidJsonReturnsEmpty() {
        Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/attack", "{not-json");
        assertTrue(parsed.isEmpty());
    }

    @Test
    void parseMissingRequiredFieldsReturnsEmpty() {
        String payloadMissingField = """
                {
                  "v": 2,
                  "device_id": "jz-sniff-001",
                  "seq": 100,
                  "ts": "2026-03-23T10:15:32+08:00",
                  "type": "attack",
                  "data": {
                    "src_ip": "10.0.1.100",
                    "guard_ip": "10.0.1.50",
                    "dst_port": 445,
                    "ifindex": 3,
                    "vlan_id": 100,
                    "ethertype": 2048,
                    "ip_proto": 6
                  }
                }
                """;

        Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/attack", payloadMissingField);
        assertTrue(parsed.isEmpty());
    }

    @Test
    void parseWrongVersionReturnsEmpty() {
        String payload = """
                {
                  "v": 1,
                  "device_id": "jz-sniff-001",
                  "seq": 123,
                  "ts": "2026-03-23T10:30:00+08:00",
                  "type": "attack",
                  "data": {
                    "src_ip": "10.0.1.100",
                    "src_mac": "aa:bb:cc:11:22:33",
                    "guard_ip": "10.0.1.50",
                    "dst_port": 445,
                    "ifindex": 3,
                    "vlan_id": 100,
                    "ethertype": 2048,
                    "ip_proto": 6
                  }
                }
                """;

        Optional<Object> parsed = parserService.parseV2Event("jz/jz-sniff-001/logs/attack", payload);
        assertTrue(parsed.isEmpty());
    }
}
