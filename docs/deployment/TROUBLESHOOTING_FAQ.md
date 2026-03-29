# Threat Detection System — Troubleshooting FAQ

> Compiled from real deployment experience on K3s v1.34, Ubuntu 24.04.
> Last updated: 2026-03-29

---

## Table of Contents

1. [K3s & Kubernetes](#1-k3s--kubernetes)
2. [Kafka](#2-kafka)
3. [PostgreSQL](#3-postgresql)
4. [EMQX (MQTT Broker)](#4-emqx-mqtt-broker)
5. [Flink (Stream Processing)](#5-flink-stream-processing)
6. [Data Ingestion](#6-data-ingestion)
7. [Docker & Images](#7-docker--images)
8. [General Debugging](#8-general-debugging)

---

## 1. K3s & Kubernetes

### Q: `kubectl` returns "permission denied" or "connection refused"

**Symptom**: `kubectl get pods` fails with permission errors.

**Fix**:
```bash
# Option A: Use sudo
sudo kubectl get pods -n threat-detection

# Option B: Copy kubeconfig for non-root access
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
export KUBECONFIG=~/.kube/config
# Add to ~/.bashrc for persistence
```

### Q: `kustomize build` fails or produces wrong output

**Symptom**: `kubectl apply -k k8s/base` adds unwanted labels or fails.

**Root cause**: `commonLabels` in `kustomization.yaml` injects labels into pod selector, which is immutable after first deployment.

**Fix**: Do NOT use `commonLabels` with kustomize. Apply YAML files directly:
```bash
sudo kubectl apply -f k8s/base/namespace.yaml
sudo kubectl apply -f k8s/base/postgres.yaml
# ... (see deployment guide for full order)
```

### Q: Pod stuck in `Terminating` state

**Fix**:
```bash
sudo kubectl delete pod <pod-name> -n threat-detection --force --grace-period=0
```

### Q: "field is immutable" error on `kubectl apply`

**Symptom**: `The Deployment is invalid: spec.selector: Invalid value: ... field is immutable`

**Root cause**: You changed pod labels or selectors after initial deployment.

**Fix**: Delete the deployment first, then re-apply:
```bash
sudo kubectl delete deployment <name> -n threat-detection
sudo kubectl apply -f k8s/base/<name>.yaml
```

### Q: cert-manager CRD errors during `kubectl apply -k`

**Symptom**: Errors about cert-manager CRDs not found.

**Fix**: These are only needed for production TLS. For single-node K3s, remove cert-manager references from kustomization.yaml or simply apply files individually.

---

## 2. Kafka

### Q: Kafka CrashLoopBackOff with "port is deprecated"

**Symptom**: Kafka pod repeatedly crashes. Logs show `port is deprecated`.

**Root cause**: K8s injects environment variables for every Service in the namespace (e.g., `KAFKA_PORT=tcp://10.43.x.x:9092`). Kafka interprets ALL `KAFKA_*` env vars as configuration properties.

**Fix**: Override the problematic env vars explicitly in the Kafka deployment:
```yaml
env:
  - name: KAFKA_PORT
    value: ""
  - name: KAFKA_PORT_9092_TCP
    value: ""
  - name: KAFKA_PORT_9092_TCP_PROTO
    value: ""
  - name: KAFKA_PORT_9092_TCP_PORT
    value: ""
  - name: KAFKA_PORT_9092_TCP_ADDR
    value: ""
```

Also ensure proper listener configuration:
```yaml
- name: KAFKA_LISTENERS
  value: "PLAINTEXT://0.0.0.0:9092"
- name: KAFKA_ADVERTISED_LISTENERS
  value: "PLAINTEXT://kafka:9092"
- name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
  value: "PLAINTEXT:PLAINTEXT"
- name: KAFKA_INTER_BROKER_LISTENER_NAME
  value: "PLAINTEXT"
```

### Q: Kafka topics don't exist

**Symptom**: Flink or data-ingestion can't find topics.

**Fix**: Re-run the topic init job:
```bash
sudo kubectl delete job kafka-topic-init -n threat-detection 2>/dev/null
sudo kubectl apply -f k8s/base/kafka-topic-init.yaml
```

Verify topics:
```bash
KAFKA_POD=$(sudo kubectl get pod -n threat-detection -l app=kafka -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-topics --bootstrap-server localhost:9092 --list
```

Expected topics: `attack-events`, `threat-alerts`, `status-events`, `attack-events-ml`

### Q: Kafka image version compatibility

**Recommendation**: Use `confluentinc/cp-kafka:7.4.0` (NOT 7.0.1). Kafka 7.0.x has issues with newer K8s service-link env vars.

---

## 3. PostgreSQL

### Q: "password authentication failed for user threat_user"

**Symptom**: Application pods can't connect to PostgreSQL.

**Root cause**: PostgreSQL only reads `POSTGRES_PASSWORD` from the Secret on **first initialization** (when the data directory is empty). If you change the Secret after PG has already started, the actual password doesn't change.

**Fix**:

Option A — Change password at runtime:
```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "ALTER USER threat_user WITH PASSWORD 'threat_password';"
```

Option B — Delete PVC and restart (loses data):
```bash
sudo kubectl delete statefulset postgres -n threat-detection
sudo kubectl delete pvc postgres-storage-postgres-0 -n threat-detection
sudo kubectl apply -f k8s/base/postgres.yaml
```

After changing password, restart all pods that use PG:
```bash
sudo kubectl rollout restart deployment -n threat-detection data-ingestion
sudo kubectl rollout restart deployment -n threat-detection customer-management
sudo kubectl rollout restart deployment -n threat-detection threat-assessment
sudo kubectl rollout restart deployment -n threat-detection alert-management
sudo kubectl rollout restart deployment -n threat-detection threat-intelligence
```

### Q: Missing tables (attack_events, customers, etc.)

**Symptom**: Application errors about missing tables.

**Fix**: Re-run the schema init job:
```bash
sudo kubectl delete job postgres-schema-apply -n threat-detection 2>/dev/null
sudo kubectl apply -f k8s/base/postgres-schema-apply.yaml
```

Or apply manually:
```bash
sudo kubectl exec -i -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection < db/init/V1__init_schema.sql
```

### Q: PostgreSQL StatefulSet uses wrong image

**Recommendation**: Use `postgres:15-alpine` (lightweight, stable). Avoid `postgres:latest` in production.

---

## 4. EMQX (MQTT Broker)

### Q: EMQX pod missing or not starting

**Symptom**: `kubectl get pods` doesn't show EMQX.

**Root cause**: The EMQX YAML was missing `namespace: threat-detection` in metadata.

**Fix**: Ensure all resources in `k8s/base/emqx.yaml` have:
```yaml
metadata:
  name: emqx
  namespace: threat-detection
```

### Q: EMQX OOMKilled (Out of Memory)

**Symptom**: EMQX keeps restarting with OOMKilled status.

**Root cause**: Default EMQX memory limit is too low.

**Fix**: Set appropriate resource limits:
```yaml
resources:
  limits:
    memory: "512Mi"
    cpu: "500m"
  requests:
    memory: "256Mi"
    cpu: "100m"
```

And configure EMQX memory limits via env:
```yaml
- name: EMQX_FORCE_SHUTDOWN__MAX_HEAP_SIZE
  value: "256MB"
```

### Q: MQTT messages not reaching data-ingestion

**Diagnosis**:
```bash
# 1. Check EMQX is running
sudo kubectl get pods -n threat-detection -l app=emqx

# 2. Check data-ingestion subscribed
DI_POD=$(sudo kubectl get pod -n threat-detection -l app=data-ingestion -o jsonpath='{.items[0].metadata.name}')
sudo kubectl logs -n threat-detection $DI_POD | grep -i "mqtt.*subscri"

# 3. Test publish
EMQX_IP=$(sudo kubectl get svc -n threat-detection emqx -o jsonpath='{.spec.clusterIP}')
mosquitto_pub -h $EMQX_IP -p 1883 -t "jz/test/logs/attack" -m "test" -q 1

# 4. Check logs again
sudo kubectl logs -n threat-detection $DI_POD --tail=10
```

---

## 5. Flink (Stream Processing)

### Q: TaskManager CrashLoopBackOff — can't register with JobManager

**Symptom**: TaskManager logs show `Could not resolve address for jobmanager` or RPC timeout.

**Root cause**: Missing or incorrect RPC service for JobManager.

**Fix**: Ensure JobManager has an RPC service on port 6123:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: stream-processing-jobmanager
  namespace: threat-detection
spec:
  selector:
    app: stream-processing
    component: jobmanager
  ports:
    - name: rpc
      port: 6123
      targetPort: 6123
    - name: webui
      port: 8081
      targetPort: 8081
```

And in `flink-conf.yaml`:
```yaml
jobmanager.rpc.address: stream-processing-jobmanager
jobmanager.rpc.port: 6123
```

### Q: Flink deserialization errors (JSON parsing)

**Symptom**: Flink logs show `JsonParseException` or `UnrecognizedPropertyException`.

**Root causes**:
1. Field name mismatch between Kafka JSON and Java model
2. String values where numbers expected (e.g., `"responsePort": "445"`)
3. Unknown fields in JSON

**Fix**: In the Java model class:
```java
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignore extra fields
public class AttackEvent {
    @JsonAlias("deviceSerial")              // Accept both names
    private String devSerial;
    
    @JsonAlias("company_obj_id")
    private String customerId;
}
```

In the ObjectMapper configuration:
```java
ObjectMapper mapper = new ObjectMapper();
// Allow string-to-int coercion ("445" → 445)
mapper.coercionConfigFor(LogicalType.Integer)
    .setCoercion(CoercionInputShape.String, CoercionAction.TryConvert);
```

### Q: Flink job shows RUNNING but no windows fire

**Diagnosis**:
```bash
# Check Flink WebUI
JM_POD=$(sudo kubectl get pod -n threat-detection -l component=jobmanager -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $JM_POD -- curl -s http://localhost:8081/jobs | jq .

# Check operator status
JOB_ID=$(sudo kubectl exec -n threat-detection $JM_POD -- curl -s http://localhost:8081/jobs | jq -r '.jobs[0].id')
sudo kubectl exec -n threat-detection $JM_POD -- curl -s "http://localhost:8081/jobs/$JOB_ID" | jq '.vertices[] | {name, status}'
```

If all operators show `RUNNING` but no output, ensure:
1. Events are actually arriving in the `attack-events` Kafka topic
2. Event timestamps are within the window boundaries
3. The watermark is advancing (check Flink metrics)

---

## 6. Data Ingestion

### Q: data-ingestion pod shows 0/1 Ready

**Symptom**: Pod is Running but not Ready (0/1).

**Root cause**: Spring Boot actuator health check includes DB health. If PG connection fails, readiness probe fails.

**Fix**: Either:
- Fix the PG connection (see PostgreSQL section)
- Or change the readiness probe to use TCP:
```yaml
readinessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

### Q: V2 MQTT events not parsed correctly

**Symptom**: MQTT events arrive but aren't forwarded to Kafka.

**Root cause**: V2 parser requires a specific envelope format.

**Required format**:
```json
{
  "v": 2,
  "device_id": "DEVICE-001",
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
}
```

**Common mistakes**:
- Missing envelope fields (`v`, `device_id`, `seq`, `type`, `ts`, `data`)
- Wrong field names inside `data` (e.g., `dst_ip` instead of `guard_ip`, `line_id` instead of `ifindex`)
- MQTT topic must match `jz/+/logs/#` pattern

### Q: V1 syslog events not reaching Kafka

**Diagnosis**:
```bash
# 1. Check Logstash is listening
LOGSTASH_PORT=$(sudo kubectl get svc -n threat-detection logstash -o jsonpath='{.spec.ports[?(@.name=="syslog")].nodePort}')
echo "test" | nc -w3 localhost $LOGSTASH_PORT

# 2. Check Logstash logs
sudo kubectl logs -n threat-detection -l app=logstash --tail=20

# 3. Verify Kafka topic has events
KAFKA_POD=$(sudo kubectl get pod -n threat-detection -l app=kafka -o jsonpath='{.items[0].metadata.name}')
sudo kubectl exec -n threat-detection $KAFKA_POD -- /bin/kafka-console-consumer --bootstrap-server localhost:9092 --topic attack-events --from-beginning --max-messages 5 --timeout-ms 10000
```

---

## 7. Docker & Images

### Q: `ImagePullBackOff` or `ErrImagePull`

**Symptom**: Pod can't pull the Docker image.

**Root cause**: Local images not imported into K3s containerd.

**Fix**:
```bash
# Save from Docker and import to K3s
docker save threat-detection/<service>:latest | sudo k3s ctr images import -

# Verify
sudo k3s ctr images ls | grep <service>

# Ensure manifests use imagePullPolicy: Never
```

### Q: Java service won't start — "UnsupportedClassVersionError"

**Symptom**: Java service crashes with class version error.

**Root cause**: Service compiled with JDK 21 but running on JDK 17 (or vice versa).

**Fix**: Ensure the Dockerfile base image matches the compilation JDK:
```dockerfile
# For services compiled with JDK 17
FROM eclipse-temurin:17-jre-alpine

# For services compiled with JDK 21
FROM eclipse-temurin:21-jre-alpine
```

### Q: Image takes too long to build in China

**Fix**: Use mirror registries in Dockerfile:
```dockerfile
# For Alpine-based images
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# For Maven builds, use Aliyun mirror in settings.xml
```

---

## 8. General Debugging

### Quick diagnostic commands

```bash
# All pods at a glance
sudo kubectl get pods -n threat-detection -o wide

# Events (shows recent scheduling/pulling/starting issues)
sudo kubectl get events -n threat-detection --sort-by='.lastTimestamp' | tail -20

# Resource usage
sudo kubectl top pods -n threat-detection

# Describe a failing pod (shows events, conditions)
sudo kubectl describe pod <pod-name> -n threat-detection

# Get logs from previous crashed container
sudo kubectl logs <pod-name> -n threat-detection --previous

# Port-forward for debugging
sudo kubectl port-forward -n threat-detection svc/api-gateway 8888:8888 &
curl http://localhost:8888/actuator/health
```

### Pod restart count keeps increasing

**Check**:
```bash
# See restart reasons
sudo kubectl describe pod <pod-name> -n threat-detection | grep -A5 "Last State"

# Common causes:
# - OOMKilled: Increase memory limits
# - CrashLoopBackOff: Check logs for application errors
# - Liveness probe failure: Application too slow to start
```

### Adjusting liveness/readiness probes

If Spring Boot services take too long to start:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 90    # Give Spring Boot time to start
  periodSeconds: 15
  failureThreshold: 5
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  failureThreshold: 3
```

### Clean restart procedure

If everything is broken and you want a fresh start:
```bash
# 1. Delete everything
sudo kubectl delete namespace threat-detection
sleep 30

# 2. Re-deploy infrastructure first, then services
# Follow the deployment guide Layer 1-8

# 3. If PVCs are stale, delete them too
sudo kubectl delete pvc --all -n threat-detection
```

---

## Quick Reference: Common Error → Fix

| Error | Fix |
|-------|-----|
| `ImagePullBackOff` | `docker save ... \| sudo k3s ctr images import -` |
| `CrashLoopBackOff` (Kafka) | Clear `KAFKA_PORT*` env vars |
| `password authentication failed` | `ALTER USER` in PG or delete PVC |
| `field is immutable` | Delete deployment, then re-apply |
| `FATAL: role "threat_user" does not exist` | Re-apply postgres.yaml (creates user on init) |
| Flink `UnrecognizedPropertyException` | Add `@JsonIgnoreProperties(ignoreUnknown = true)` |
| data-ingestion `0/1 Ready` | Fix PG password or change to TCP readiness probe |
| EMQX `OOMKilled` | Increase memory limit + set `EMQX_FORCE_SHUTDOWN__MAX_HEAP_SIZE` |
| Missing Kafka topics | Re-run `kafka-topic-init` job |
| Logstash not receiving syslog | Check NodePort: `kubectl get svc logstash -o jsonpath='{.spec.ports[?(@.name=="syslog")].nodePort}'` |
