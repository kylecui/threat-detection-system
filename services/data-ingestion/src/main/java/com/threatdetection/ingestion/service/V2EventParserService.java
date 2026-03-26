package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class V2EventParserService {

    private static final Logger logger = LoggerFactory.getLogger(V2EventParserService.class);
    private static final Set<String> DEFERRED_TYPES = Set.of("sniffer", "threat", "bg", "audit", "policy");

    private final ObjectMapper objectMapper;
    private final DevSerialToCustomerMappingService mappingService;

    public V2EventParserService(ObjectMapper objectMapper, DevSerialToCustomerMappingService mappingService) {
        this.objectMapper = objectMapper;
        this.mappingService = mappingService;
    }

    public Optional<Object> parseV2Event(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            Integer version = getRequiredInt(root, "v");
            String deviceId = getRequiredText(root, "device_id");
            Long seq = getRequiredLong(root, "seq");
            String type = getRequiredText(root, "type");
            String ts = getRequiredText(root, "ts");
            JsonNode data = root.get("data");

            if (version == null || deviceId == null || seq == null || type == null || ts == null || data == null || !data.isObject()) {
                logger.warn("V2 event missing required envelope fields, topic={}", topic);
                return Optional.empty();
            }

            if (version != 2) {
                logger.warn("Unsupported V2 event version={}, device_id={}, type={}", version, deviceId, type);
                return Optional.empty();
            }

            switch (type) {
                case "attack":
                    return parseAttackEvent(deviceId, ts, payload, data);
                case "heartbeat":
                    return parseHeartbeatEvent(deviceId, ts, data);
                default:
                    if (DEFERRED_TYPES.contains(type)) {
                        logger.info("Deferred V2 event type received: device_id={}, type={}", deviceId, type);
                        return Optional.empty();
                    }
                    logger.warn("Unknown V2 event type received: device_id={}, type={}", deviceId, type);
                    return Optional.empty();
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse V2 JSON payload, topic={}", topic, e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error parsing V2 event, topic={}", topic, e);
            return Optional.empty();
        }
    }

    private Optional<Object> parseAttackEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        String attackMac = getRequiredText(data, "src_mac");
        String attackIp = getRequiredText(data, "src_ip");
        String responseIp = getRequiredText(data, "guard_ip");
        Integer responsePort = getRequiredInt(data, "dst_port");
        Integer lineId = getRequiredInt(data, "ifindex");
        Integer vlanId = getRequiredInt(data, "vlan_id");
        Integer ethType = getRequiredInt(data, "ethertype");
        Integer ipType = getRequiredInt(data, "ip_proto");

        Long logTime = parseEpochSeconds(ts);

        if (attackMac == null || attackIp == null || responseIp == null || responsePort == null
                || lineId == null || vlanId == null || ethType == null || ipType == null || logTime == null) {
            logger.warn("V2 attack event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        AttackEvent event = new AttackEvent(
                deviceId,
                1,
                1,
                attackMac,
                attackIp,
                responseIp,
                responsePort,
                lineId,
                0,
                vlanId,
                logTime,
                ethType,
                ipType,
                rawPayload,
                customerId
        );

        return Optional.of(event);
    }

    private Optional<Object> parseHeartbeatEvent(String deviceId, String ts, JsonNode data) {
        Integer totalGuards = getRequiredInt(data, "total_guards");
        Integer onlineDevices = getRequiredInt(data, "online_devices");
        Long uptimeSec = getRequiredLong(data, "uptime_sec");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (totalGuards == null || onlineDevices == null || uptimeSec == null || timestamp == null) {
            logger.warn("V2 heartbeat event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        HeartbeatEvent heartbeatEvent = new HeartbeatEvent(deviceId, timestamp, totalGuards, onlineDevices, uptimeSec);
        heartbeatEvent.setCustomerId(mappingService.resolveCustomerId(deviceId));
        heartbeatEvent.setFirmwareVersion(getOptionalText(data, "firmware_version"));
        heartbeatEvent.setNetworkInterfacesJson(extractNetworkInterfacesJson(data));
        heartbeatEvent.setRawTopologyJson(extractRawTopologyJson(data));
        heartbeatEvent.setDevices(parseDiscoveredHosts(data));
        return Optional.of(heartbeatEvent);
    }

    private String getOptionalText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asText();
    }

    private String extractNetworkInterfacesJson(JsonNode data) {
        JsonNode interfacesNode = data.get("network_interfaces");
        if (interfacesNode == null || interfacesNode.isNull()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(interfacesNode);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize network_interfaces, defaulting to []");
            return "[]";
        }
    }

    private String extractRawTopologyJson(JsonNode data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize heartbeat data node, defaulting to {}", e);
            return "{}";
        }
    }

    private List<HeartbeatEvent.DiscoveredHostData> parseDiscoveredHosts(JsonNode data) {
        JsonNode devicesNode = data.get("devices");
        List<HeartbeatEvent.DiscoveredHostData> devices = new ArrayList<>();
        if (devicesNode == null || !devicesNode.isArray()) {
            return devices;
        }

        for (JsonNode deviceNode : devicesNode) {
            HeartbeatEvent.DiscoveredHostData hostData = new HeartbeatEvent.DiscoveredHostData();
            hostData.setMacAddress(getOptionalText(deviceNode, "mac"));
            hostData.setIpAddress(getOptionalText(deviceNode, "ip"));
            Integer vlanId = getRequiredInt(deviceNode, "vlan_id");
            hostData.setVlanId(vlanId == null ? 0 : vlanId);
            JsonNode isDecoyNode = deviceNode.get("is_decoy");
            hostData.setDecoy(isDecoyNode != null && !isDecoyNode.isNull() && isDecoyNode.asBoolean(false));
            devices.add(hostData);
        }

        return devices;
    }

    private String getRequiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer getRequiredInt(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.isInt() || field.isLong() || field.isShort()) {
            return field.intValue();
        }
        if (field.isTextual()) {
            try {
                return Integer.parseInt(field.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long getRequiredLong(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.isLong() || field.isInt()) {
            return field.longValue();
        }
        if (field.isTextual()) {
            try {
                return Long.parseLong(field.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long parseEpochSeconds(String ts) {
        try {
            return OffsetDateTime.parse(ts).toInstant().getEpochSecond();
        } catch (Exception e) {
            logger.warn("Invalid V2 timestamp for epoch conversion: {}", ts);
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String ts) {
        try {
            Instant instant = OffsetDateTime.parse(ts).toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            logger.warn("Invalid V2 timestamp for heartbeat conversion: {}", ts);
            return null;
        }
    }
}
