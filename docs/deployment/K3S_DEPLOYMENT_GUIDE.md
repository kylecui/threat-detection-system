# Threat Detection System — K3s Single-Node Deployment Guide

> Tested and verified on Ubuntu 24.04 LTS, K3s v1.34.x, 8 cores / 16GB RAM.
> Last updated: 2026-03-29

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Server Preparation](#2-server-preparation)
3. [Install K3s](#3-install-k3s)
4. [Clone Repository](#4-clone-repository)
5. [Build Docker Images](#5-build-docker-images)
6. [Deploy Infrastructure](#6-deploy-infrastructure)
7. [Deploy Application Services](#7-deploy-application-services)
8. [Verify Deployment](#8-verify-deployment)
9. [Smoke Test](#9-smoke-test)
10. [Maintenance](#10-maintenance)

---

## 1. Prerequisites

### Hardware (Minimum)

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU      | 4 cores | 8 cores     |
| RAM      | 8 GB    | 16 GB       |
| Disk     | 40 GB   | 80 GB       |

### Software

- Ubuntu 22.04 / 24.04 LTS (other Linux distros OK, tested on Ubuntu)
- `curl`, `git`, `docker` (or containerd via K3s)
- Internet access for pulling base images

### Network Ports

| Port  | Protocol | Purpose                    |
|-------|----------|----------------------------|
| 6443  | TCP      | K3s API server             |
| 32318 | TCP      | Logstash syslog (NodePort) |
| 30000-32767 | TCP | K8s NodePort range        |

---

## 2. Server Preparation

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install essential tools
sudo apt install -y curl git wget jq mosquitto-clients

# Install Docker (for building images)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# Log out and log back in for group changes to take effect

# Verify
docker --version
```

---

## 3. Install K3s

```bash
# Install K3s (single-node, with embedded etcd)
curl -sfL https://get.k3s.io | sh -

# Verify K3s is running
sudo kubectl get nodes
# Expected: 1 node, STATUS=Ready

# (Optional) Allow non-root kubectl access
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
export KUBECONFIG=~/.kube/config
```

> **Note**: If you skip the non-root setup, prefix all `kubectl` commands with `sudo`.

---

## 4. Clone Repository

```bash
cd ~
git clone https://github.com/kylecui/threat-detection-system.git
cd threat-detection-system
```

---

## 5. Build Docker Images

All microservices must be built as local Docker images. K3s uses containerd, which can import from Docker.

```bash
cd ~/threat-detection-system

# Build all Java services (requires JDK 17 & Maven, or use the Docker multi-stage builds)
# Each service has a Dockerfile in its directory.

SERVICES=(
  "config-server"
  "api-gateway"
  "data-ingestion"
  "customer-management"
  "threat-assessment"
  "alert-management"
  "threat-intelligence"
  "stream-processing"
)

for svc in "${SERVICES[@]}"; do
  echo "=== Building $svc ==="
  docker build -t "threat-detection/$svc:latest" "services/$svc/"
done

# Build ML detection (Python)
docker build -t "threat-detection/ml-detection:latest" services/ml-detection/

# Import images into K3s containerd
for svc in "${SERVICES[@]}"; do
  echo "=== Importing $svc ==="
  docker save "threat-detection/$svc:latest" | sudo k3s ctr images import -
done
docker save "threat-detection/ml-detection:latest" | sudo k3s ctr images import -

# Verify images are available
sudo k3s ctr images ls | grep threat-detection
```

> **CRITICAL**: Ensure all K8s manifests use `imagePullPolicy: Never` or `imagePullPolicy: IfNotPresent` for local images. Our manifests already set `imagePullPolicy: Never`.

### Special: Rebuild stream-processing with correct Flink image

The stream-processing service requires Flink 1.18 base image:

```bash
cd services/stream-processing
docker build -t threat-detection/stream-processing:1.0 .
docker save threat-detection/stream-processing:1.0 | sudo k3s ctr images import -
```

---

## 6. Deploy Infrastructure

Deploy in strict order — each layer depends on the previous one.

### Layer 1: Namespace

```bash
sudo kubectl apply -f k8s/base/namespace.yaml
# Verify:
sudo kubectl get ns threat-detection
```

### Layer 2: Databases & Message Brokers

```bash
# PostgreSQL (StatefulSet + Secret + Init Scripts)
sudo kubectl apply -f k8s/base/postgres.yaml

# Redis
sudo kubectl apply -f k8s/base/redis.yaml

# Zookeeper (required before Kafka)
sudo kubectl apply -f k8s/base/zookeeper.yaml

# EMQX (MQTT Broker)
sudo kubectl apply -f k8s/base/emqx.yaml

# Wait for all to be Ready
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=postgres --timeout=120s
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=redis --timeout=60s
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=zookeeper --timeout=60s
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=emqx --timeout=60s
```

### Layer 3: Kafka

```bash
# Kafka (depends on Zookeeper)
sudo kubectl apply -f k8s/base/kafka.yaml

# Wait for Kafka to be Ready
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=kafka --timeout=120s
```

> **IMPORTANT**: Kafka must start AFTER Zookeeper is fully ready. If Kafka fails, delete the pod and let it restart.

### Layer 4: Init Jobs

```bash
# Create Kafka topics
sudo kubectl apply -f k8s/base/kafka-topic-init.yaml

# Apply PostgreSQL schema
sudo kubectl apply -f k8s/base/postgres-schema-apply.yaml

# Wait for jobs to complete
sudo kubectl -n threat-detection wait --for=condition=Complete job/kafka-topic-init --timeout=120s
sudo kubectl -n threat-detection wait --for=condition=Complete job/postgres-schema-apply --timeout=120s 2>/dev/null || true
```

---

## 7. Deploy Application Services

### Layer 5: Config Server (other services depend on it)

```bash
sudo kubectl apply -f k8s/base/config-server.yaml
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=config-server --timeout=120s
```

### Layer 6: Logstash

```bash
sudo kubectl apply -f k8s/base/logstash.yaml
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l app=logstash --timeout=120s
```

### Layer 7: Application Microservices

```bash
# These can be applied in parallel (no inter-dependencies)
sudo kubectl apply -f k8s/base/data-ingestion.yaml
sudo kubectl apply -f k8s/base/customer-management.yaml
sudo kubectl apply -f k8s/base/threat-assessment.yaml
sudo kubectl apply -f k8s/base/alert-management.yaml
sudo kubectl apply -f k8s/base/threat-intelligence.yaml
sudo kubectl apply -f k8s/base/ml-detection.yaml

# Wait for all to be Ready (give Spring Boot services ~60-90s)
sleep 60
sudo kubectl -n threat-detection get pods
```

### Layer 8: API Gateway & Stream Processing

```bash
sudo kubectl apply -f k8s/base/api-gateway.yaml
sudo kubectl apply -f k8s/base/stream-processing.yaml

# Wait for Flink pods
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l component=jobmanager --timeout=180s
sudo kubectl -n threat-detection wait --for=condition=Ready pod -l component=taskmanager --timeout=180s
```

### Final Check

```bash
sudo kubectl get pods -n threat-detection
```

**Expected**: All pods should show `1/1 Running` (init jobs show `0/1 Completed`).

---

## 8. Verify Deployment

### 8.1 All Pods Running

```bash
sudo kubectl get pods -n threat-detection -o wide
```

Expected: 15 Running pods + 1-2 Completed jobs.

### 8.2 Service Connectivity

```bash
# API Gateway health
GW_IP=$(sudo kubectl get svc -n threat-detection api-gateway -o jsonpath='{.spec.clusterIP}')
curl -s http://$GW_IP:8888/actuator/health | jq .status
# Expected: "UP"

# PostgreSQL
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "SELECT count(*) FROM customers;"

# Kafka topics
KAFKA_POD=$(sudo kubectl get pod -n threat-detection -l app=kafka -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-topics --bootstrap-server localhost:9092 --list
# Expected: attack-events, threat-alerts, status-events, attack-events-ml
```

### 8.3 Flink Job Status

```bash
JM_POD=$(sudo kubectl get pod -n threat-detection -l component=jobmanager -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $JM_POD -- curl -s http://localhost:8081/jobs | jq .
# Expected: 1 job with status "RUNNING"
```

---

## 9. Smoke Test

### 9.1 V1 Path: Syslog → Logstash → Kafka → Flink → PostgreSQL

```bash
# Get Logstash NodePort
LOGSTASH_PORT=$(sudo kubectl get svc -n threat-detection logstash -o jsonpath='{.spec.ports[?(@.name=="syslog")].nodePort}')
echo "Logstash syslog port: $LOGSTASH_PORT"

# Send a V1 syslog event
echo '<134>1 2026-03-29T14:00:00Z honeypot1 sentry - - - company_obj_id=smoke-customer-001 deviceSerial=DEV-001 logType=1 subType=1 attackMac=AA:BB:CC:DD:EE:01 attackIp=192.168.1.100 responseIp=10.0.0.50 responsePort=445 lineId=1 ifaceType=0 vlanId=0 logTime=1774793000 ethType=2048 ipType=6' | nc -w3 localhost $LOGSTASH_PORT

# Wait for Flink to process (30s window)
echo "Waiting 35 seconds for Flink window to fire..."
sleep 35

# Check Kafka attack-events
KAFKA_POD=$(sudo kubectl get pod -n threat-detection -l app=kafka -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic attack-events \
  --from-beginning --max-messages 5 --timeout-ms 10000

# Check threat-alerts
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic threat-alerts \
  --from-beginning --max-messages 5 --timeout-ms 10000

# Check PostgreSQL for persisted events
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection \
  -c "SELECT id, customer_id, attack_mac, attack_ip, response_ip FROM attack_events ORDER BY id DESC LIMIT 5;"
```

### 9.2 V2 Path: MQTT → EMQX → data-ingestion → Kafka

```bash
EMQX_IP=$(sudo kubectl get svc -n threat-detection emqx -o jsonpath='{.spec.clusterIP}')

# Send a V2 MQTT event
mosquitto_pub -h $EMQX_IP -p 1883 -t "jz/SMOKE-DEV-001/logs/attack" -q 1 -m '{
  "v": 2,
  "device_id": "SMOKE-DEV-001",
  "seq": 1,
  "type": "attack",
  "ts": "2026-03-29T14:00:00Z",
  "data": {
    "src_mac": "AA:BB:CC:DD:EE:FF",
    "src_ip": "192.168.10.50",
    "guard_ip": "10.0.0.200",
    "dst_port": 8080,
    "ifindex": 2,
    "vlan_id": 100,
    "ethertype": 2048,
    "ip_proto": 6
  }
}'

# Verify in data-ingestion logs
DI_POD=$(sudo kubectl get pod -n threat-detection -l app=data-ingestion -o jsonpath='{.items[0].metadata.name}')
sudo kubectl logs -n threat-detection $DI_POD --tail=10 | grep -i "mqtt\|SMOKE"

# Verify in Kafka
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic attack-events \
  --from-beginning --max-messages 10 --timeout-ms 10000 | grep "SMOKE-DEV"
```

### 9.3 API Gateway Endpoints

```bash
GW_IP=$(sudo kubectl get svc -n threat-detection api-gateway -o jsonpath='{.spec.clusterIP}')

# Health
curl -s http://$GW_IP:8888/actuator/health | jq .status

# List customers
curl -s http://$GW_IP:8888/api/v1/customers | jq '.content | length'

# List alerts
curl -s http://$GW_IP:8888/api/v1/alerts | jq '.totalElements'

# Create a customer
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"customer_id":"test-001","name":"Test Corp","email":"test@corp.com","status":"ACTIVE","subscription_tier":"BASIC","max_devices":10}' \
  http://$GW_IP:8888/api/v1/customers | jq .id
```

---

## 10. Maintenance

### View Logs

```bash
# Specific service
sudo kubectl logs -n threat-detection -l app=data-ingestion --tail=50

# Follow logs in real-time
sudo kubectl logs -n threat-detection -l app=stream-processing -c taskmanager -f
```

### Restart a Service

```bash
sudo kubectl rollout restart deployment -n threat-detection <service-name>
```

### Rebuild and Redeploy a Service

```bash
cd ~/threat-detection-system/services/<service-name>
docker build -t threat-detection/<service-name>:latest .
docker save threat-detection/<service-name>:latest | sudo k3s ctr images import -
sudo kubectl rollout restart deployment -n threat-detection <service-name>
```

### Scale a Service

```bash
sudo kubectl scale deployment -n threat-detection <service-name> --replicas=2
```

### Check Resource Usage

```bash
sudo kubectl top pods -n threat-detection
# Requires metrics-server (K3s includes it by default)
```

### Clean Restart (Nuclear Option)

If things go wrong and you want a fresh start:

```bash
# Delete the entire namespace (destroys everything)
sudo kubectl delete namespace threat-detection

# Wait for cleanup
sleep 30

# Re-deploy from Layer 1
sudo kubectl apply -f k8s/base/namespace.yaml
# ... follow deployment steps above
```

> **Warning**: This deletes all data in PVCs (PostgreSQL data, etc.). Back up first if needed.

---

## Appendix A: Full Deploy Script

```bash
#!/bin/bash
set -e

NS="threat-detection"
K="sudo kubectl"

echo "=== Layer 1: Namespace ==="
$K apply -f k8s/base/namespace.yaml

echo "=== Layer 2: Infrastructure ==="
$K apply -f k8s/base/postgres.yaml
$K apply -f k8s/base/redis.yaml
$K apply -f k8s/base/zookeeper.yaml
$K apply -f k8s/base/emqx.yaml

echo "Waiting for infrastructure..."
$K -n $NS wait --for=condition=Ready pod -l app=postgres --timeout=120s
$K -n $NS wait --for=condition=Ready pod -l app=redis --timeout=60s
$K -n $NS wait --for=condition=Ready pod -l app=zookeeper --timeout=60s
$K -n $NS wait --for=condition=Ready pod -l app=emqx --timeout=60s

echo "=== Layer 3: Kafka ==="
$K apply -f k8s/base/kafka.yaml
$K -n $NS wait --for=condition=Ready pod -l app=kafka --timeout=120s

echo "=== Layer 4: Init Jobs ==="
$K apply -f k8s/base/kafka-topic-init.yaml
$K apply -f k8s/base/postgres-schema-apply.yaml
$K -n $NS wait --for=condition=Complete job/kafka-topic-init --timeout=120s
sleep 10

echo "=== Layer 5: Config Server ==="
$K apply -f k8s/base/config-server.yaml
$K -n $NS wait --for=condition=Ready pod -l app=config-server --timeout=120s

echo "=== Layer 6: Logstash ==="
$K apply -f k8s/base/logstash.yaml
$K -n $NS wait --for=condition=Ready pod -l app=logstash --timeout=120s

echo "=== Layer 7: Application Services ==="
$K apply -f k8s/base/data-ingestion.yaml
$K apply -f k8s/base/customer-management.yaml
$K apply -f k8s/base/threat-assessment.yaml
$K apply -f k8s/base/alert-management.yaml
$K apply -f k8s/base/threat-intelligence.yaml
$K apply -f k8s/base/ml-detection.yaml

echo "Waiting 60s for Spring Boot services..."
sleep 60

echo "=== Layer 8: API Gateway & Stream Processing ==="
$K apply -f k8s/base/api-gateway.yaml
$K apply -f k8s/base/stream-processing.yaml

echo "Waiting for Flink..."
$K -n $NS wait --for=condition=Ready pod -l component=jobmanager --timeout=180s
$K -n $NS wait --for=condition=Ready pod -l component=taskmanager --timeout=180s

echo "=== DEPLOYMENT COMPLETE ==="
$K get pods -n $NS
```

---

## Appendix B: Deployment Order Reference

```
namespace.yaml
  └─ postgres.yaml, redis.yaml, zookeeper.yaml, emqx.yaml
       └─ kafka.yaml
            └─ kafka-topic-init.yaml, postgres-schema-apply.yaml
                 └─ config-server.yaml
                      └─ logstash.yaml
                      └─ data-ingestion.yaml, customer-management.yaml,
                         threat-assessment.yaml, alert-management.yaml,
                         threat-intelligence.yaml, ml-detection.yaml
                           └─ api-gateway.yaml
                           └─ stream-processing.yaml
```

---

## Appendix C: Service Ports

| Service               | Container Port | K8s Service Port | NodePort  |
|-----------------------|---------------|------------------|-----------|
| PostgreSQL            | 5432          | 5432             | —         |
| Redis                 | 6379          | 6379             | —         |
| Zookeeper             | 2181          | 2181             | —         |
| Kafka                 | 9092          | 9092             | —         |
| EMQX (MQTT)          | 1883          | 1883             | —         |
| EMQX (Dashboard)     | 18083         | 18083            | —         |
| Config Server         | 8888          | 8888             | —         |
| Logstash (syslog)    | 9080          | 9080             | **32318** |
| Data Ingestion        | 8080          | 8080             | —         |
| Customer Management   | 8084          | 8084             | —         |
| Threat Assessment     | 8083          | 8083             | —         |
| Alert Management      | 8082          | 8082             | —         |
| Threat Intelligence   | 8085          | 8085             | —         |
| ML Detection          | 8086          | 8086             | —         |
| API Gateway           | 8888          | 8888             | —         |
| Flink JobManager (UI) | 8081          | 8081             | —         |
| Flink JobManager (RPC)| 6123          | 6123             | —         |
| Flink TaskManager     | 6121          | 6121             | —         |
