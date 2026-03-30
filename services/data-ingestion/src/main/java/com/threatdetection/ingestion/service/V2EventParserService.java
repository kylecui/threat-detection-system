package com.threatdetection.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.AuditEvent;
import com.threatdetection.ingestion.model.BgTrafficEvent;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import com.threatdetection.ingestion.model.PolicyEvent;
import com.threatdetection.ingestion.model.SnifferEvent;
import com.threatdetection.ingestion.model.ThreatDetectionEvent;
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

@Service
public class V2EventParserService {

    private static final Logger logger = LoggerFactory.getLogger(V2EventParserService.class);

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
                case "sniffer":
                    return parseSnifferEvent(deviceId, ts, payload, data);
                case "threat":
                    return parseThreatEvent(deviceId, ts, payload, data);
                case "policy":
                    return parsePolicyEvent(deviceId, ts, payload, data);
                case "bg":
                    return parseBgEvent(deviceId, ts, payload, data);
                case "audit":
                    return parseAuditEvent(deviceId, ts, payload, data);
                default:
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

    private Optional<Object> parseSnifferEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        String suspectMac = getRequiredText(data, "suspect_mac");
        String suspectIp = getRequiredText(data, "suspect_ip");
        String probeIp = getRequiredText(data, "probe_ip");
        String interfaceName = getOptionalText(data, "interface");
        Integer ifindex = getRequiredInt(data, "ifindex");
        Integer responseCount = getRequiredInt(data, "response_count");
        String firstSeen = getOptionalText(data, "first_seen");
        String lastSeen = getOptionalText(data, "last_seen");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (suspectMac == null || suspectIp == null || probeIp == null
                || ifindex == null || responseCount == null || timestamp == null) {
            logger.warn("V2 sniffer event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        SnifferEvent event = new SnifferEvent(deviceId, customerId, timestamp,
                suspectMac, suspectIp, probeIp, interfaceName,
                ifindex, responseCount, firstSeen, lastSeen, rawPayload);
        return Optional.of(event);
    }

    private Optional<Object> parseThreatEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        Integer patternId = getRequiredInt(data, "pattern_id");
        Integer threatLevel = getRequiredInt(data, "threat_level");
        String actionTaken = getRequiredText(data, "action_taken");
        String description = getOptionalText(data, "description");
        String srcIp = getRequiredText(data, "src_ip");
        String dstIp = getRequiredText(data, "dst_ip");
        Integer dstPort = getRequiredInt(data, "dst_port");
        String protocol = getOptionalText(data, "protocol");
        String interfaceName = getOptionalText(data, "interface");
        Integer ifindex = getRequiredInt(data, "ifindex");
        Integer vlanId = getRequiredInt(data, "vlan_id");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (patternId == null || threatLevel == null || actionTaken == null
                || srcIp == null || dstIp == null || dstPort == null
                || ifindex == null || vlanId == null || timestamp == null) {
            logger.warn("V2 threat event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        ThreatDetectionEvent event = new ThreatDetectionEvent(deviceId, customerId, timestamp,
                patternId, threatLevel, actionTaken, description,
                srcIp, dstIp, dstPort, protocol != null ? protocol : "unknown",
                interfaceName, ifindex, vlanId, rawPayload);
        return Optional.of(event);
    }

    private Optional<Object> parsePolicyEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        Integer policyId = getRequiredInt(data, "policy_id");
        String action = getRequiredText(data, "action");
        String srcIp = getRequiredText(data, "src_ip");
        String dstIp = getRequiredText(data, "dst_ip");
        Integer srcPort = getRequiredInt(data, "src_port");
        Integer dstPort = getRequiredInt(data, "dst_port");
        String protocol = getOptionalText(data, "protocol");
        String redirectTo = getOptionalText(data, "redirect_to");
        String mirrorTo = getOptionalText(data, "mirror_to");
        String trigger = getRequiredText(data, "trigger");
        String reason = getOptionalText(data, "reason");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (policyId == null || action == null || srcIp == null || dstIp == null
                || srcPort == null || dstPort == null || trigger == null || timestamp == null) {
            logger.warn("V2 policy event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        PolicyEvent event = new PolicyEvent(deviceId, customerId, timestamp,
                policyId, action, srcIp, dstIp, srcPort, dstPort,
                protocol != null ? protocol : "unknown",
                redirectTo, mirrorTo, trigger, reason, rawPayload);
        return Optional.of(event);
    }

    private Optional<Object> parseBgEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        String periodStart = getRequiredText(data, "period_start");
        String periodEnd = getRequiredText(data, "period_end");
        JsonNode protocolsNode = data.get("protocols");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (periodStart == null || periodEnd == null || protocolsNode == null || timestamp == null) {
            logger.warn("V2 bg event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String protocolsJson;
        try {
            protocolsJson = objectMapper.writeValueAsString(protocolsNode);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize bg protocols, device_id={}", deviceId);
            protocolsJson = "{}";
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        BgTrafficEvent event = new BgTrafficEvent(deviceId, customerId, timestamp,
                periodStart, periodEnd, protocolsJson, rawPayload);
        return Optional.of(event);
    }

    private Optional<Object> parseAuditEvent(String deviceId, String ts, String rawPayload, JsonNode data) {
        String action = getRequiredText(data, "action");
        String actor = getRequiredText(data, "actor");
        String target = getRequiredText(data, "target");
        String result = getRequiredText(data, "result");
        JsonNode detailsNode = data.get("details");
        LocalDateTime timestamp = parseLocalDateTime(ts);

        if (action == null || actor == null || target == null || result == null || timestamp == null) {
            logger.warn("V2 audit event missing required fields: device_id={}", deviceId);
            return Optional.empty();
        }

        String detailsJson = null;
        if (detailsNode != null && !detailsNode.isNull()) {
            try {
                detailsJson = objectMapper.writeValueAsString(detailsNode);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize audit details, device_id={}", deviceId);
            }
        }

        String customerId = mappingService.resolveCustomerId(deviceId);
        AuditEvent event = new AuditEvent(deviceId, customerId, timestamp,
                action, actor, target, result, detailsJson, rawPayload);
        return Optional.of(event);
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
