# MQTT Ingestion Architecture Design (V2)

**Version**: 1.0
**Date**: 2026-03-26
**Status**: Design — defines the MQTT-based ingestion path for V2 sentinels.

---

## 1. Overview

This document outlines the architecture for ingesting V2 event data from sentinels using the MQTT protocol. The system supports dual-protocol coexistence, allowing V1 sentinels to continue using Syslog (UDP) while V2 sentinels transition to MQTT for richer, structured JSON data.

## 2. Architecture Diagram

```text
+----------------+      +-------------------+      +-----------------------+
| V1 Sentinel    | ---->| Rsyslog (9080)    | ---->|                       |
+----------------+      +-------------------+      |                       |
                                                   | Data Ingestion Service|
+----------------+      +-------------------+      | (Port 8080)           |
| V2 Sentinel    | ---->| EMQX Broker       | ---->|                       |
| (Paho C Client)|      | (Port 1883/8883)  |      | +-------------------+ |
+----------------+      +-------------------+      | |MqttMessageListener| |
                                                   | +---------+---------+ |
                                                   +-----------|-----------+
                                                               |
                                                               v
                                                   +-----------------------+
                                                   | Kafka (attack-events) |
                                                   +-----------------------+
                                                               |
                                                               v
                                                   +-----------------------+
                                                   | Flink Stream Processor|
                                                   +-----------------------+
```

## 3. Broker Selection

### Production: EMQX
- **Justification**: EMQX is a highly scalable, K8s-native MQTT broker. It provides built-in clustering, high availability, and a powerful rule engine. Its ability to bridge directly to Kafka (Enterprise version) or handle massive concurrent connections makes it ideal for large-scale sentinel deployments.
- **Features**: Supports MQTT 5.0, shared subscriptions, and robust security ACLs.

### Development: Mosquitto
- **Justification**: Lightweight and easy to deploy for local development and CI/CD pipelines. It provides a standard-compliant implementation of MQTT without the overhead of a full cluster.

## 4. Topic Structure

The system uses a hierarchical topic structure to allow for granular subscriptions and efficient routing.

| Topic Pattern | Description |
|---------------|-------------|
| `jz/{device_id}/logs/{type}` | Event publication topic. `type` is one of: `attack`, `sniffer`, `threat`, `bg`, `heartbeat`, `audit`, `policy`. |
| `jz/+/logs/#` | Wildcard subscription used by the Data Ingestion service to consume all logs. |
| `jz/{device_id}/status` | LWT and connection status topic. |

## 5. QoS Strategy

- **Log Messages (QoS 1)**: All log events (attack, threat, etc.) must use QoS 1 (at-least-once delivery). This ensures that no critical security events are lost during transient network failures.
- **Heartbeat (QoS 0)**: Heartbeat messages can use QoS 0 (at-most-once) as they are periodic and the loss of a single heartbeat is non-critical.
- **Why not QoS 2?**: QoS 2 (exactly-once) introduces significant overhead and latency. Since the downstream Kafka pipeline handles deduplication via `(device_id, seq)`, QoS 1 provides the best balance of reliability and performance.

## 6. Client Libraries

- **Cloud Side (Java)**: **HiveMQ MQTT Client**.
  - **Reason**: High-performance, Netty-based asynchronous API. Excellent support for MQTT 5.0 features and reactive streams.
  - **Alternative**: Eclipse Paho Java Client (widely used but less performant in high-concurrency scenarios).
- **Device Side (C)**: **Eclipse Paho Embedded C**.
  - **Reason**: Optimized for resource-constrained environments (V2 sentinel), providing a stable and lightweight implementation.

## 7. Integration Flow

1. **Ingestion**: The `data-ingestion` service initializes an `MqttMessageListener` using the HiveMQ client, subscribing to `jz/+/logs/#`.
2. **Normalization**: Upon receiving a JSON message, the listener parses the common envelope and maps the V2 fields to the internal `AttackEvent` DTO.
3. **Kafka Publishing**: Normalized events are published to the `attack-events` Kafka topic.
4. **Unified Downstream**: The existing Flink stream processing jobs consume from `attack-events`, remaining agnostic to whether the source was Syslog V1 or MQTT V2.

## 8. LWT and Monitoring

Device online/offline status is tracked using the MQTT Last Will and Testament (LWT) feature.

- **On Connect**: The sentinel publishes a retained message to `jz/{device_id}/status` with payload `{"online": true}`.
- **Last Will**: The sentinel sets an LWT on `jz/{device_id}/status` with payload `{"online": false}` and `retain=true`.
- **Monitoring**: The platform monitors this topic to update the device status in the `customer-management` service.

## 9. Infrastructure

### Docker Compose (Dev)
```yaml
services:
  emqx:
    image: emqx/emqx:5.3.0
    ports:
      - "1883:1883"     # MQTT TCP
      - "8083:8083"     # MQTT WebSocket
      - "8883:8883"     # MQTT SSL
      - "18083:18083"   # Dashboard
    environment:
      - "EMQX_DASHBOARD__ADMIN_PASSWORD=public"
```

### Kubernetes StatefulSet (Prod Snippet)
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: emqx
spec:
  replicas: 3
  serviceName: emqx
  template:
    spec:
      containers:
      - name: emqx
        image: emqx/emqx:5.3.0
        ports:
        - containerPort: 1883
        - containerPort: 8883
        - containerPort: 18083
```

## 10. Security

- **Transport**: TLS/SSL encryption on port 8883 for all production traffic.
- **Authentication**: Username/password authentication required for all clients.
- **Authorization (ACL)**: Sentinels are restricted to publishing only to `jz/{device_id}/#` and cannot subscribe to other devices' topics. The `data-ingestion` service has administrative read access to `jz/#`.
