# 日志摄取配置指南

## 概述

本目录包含威胁检测系统的日志摄取基础设施配置，包括：
- **Logstash**: 日志解析和转发到 Kafka
- **rsyslog**: 设备端日志采集和转发
- **TLS**: 安全传输配置（未来）

---

## 架构

```
[设备终端] → rsyslog (TCP JSON) → Logstash (5140) → Kafka (attack-events)
```

### 数据流

1. **设备终端** (`dev_serial`) 生成攻击事件日志
2. **rsyslog** 将日志格式化为 JSON 并通过 TCP 发送到 Logstash
3. **Logstash** 解析、验证、标准化日志，然后发送到 Kafka
4. **Kafka** 分发事件到 Stream Processing 服务进行实时分析

---

## Logstash 配置

### 端口

| 端口 | 协议 | 用途 | 状态 |
|------|------|------|------|
| 9080 | TCP | rsyslog 无 TLS | ✅ 已配置 |
| 9081 | TCP | rsyslog TLS | 🔜 预留 |
| 9600 | HTTP | 监控 API | ✅ 已配置 |

### 启动 Logstash

```bash
cd /home/kylecui/threat-detection-system/docker
docker-compose up -d logstash
```

### 检查状态

```bash
# 查看容器状态
docker-compose ps logstash

# 查看日志
docker-compose logs -f logstash

# 检查健康状态
curl http://localhost:9600/_node/stats
```

### 监控

```bash
# Logstash 节点信息
curl http://localhost:9600/_node

# Pipeline 统计
curl http://localhost:9600/_node/stats/pipelines

# JVM 堆内存使用
curl http://localhost:9600/_node/stats/jvm
```

---

## rsyslog 设备端配置

### 日志格式

系统支持两种日志类型，均为 **key=value** 格式，逗号分隔：

**攻击日志 (log_type=1)**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=65536,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274810
```

**心跳日志 (log_type=2)**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6453,real_host_count=673,dev_start_time=1747274780,dev_end_time=-1,time=2025-05-15 10:06:50
```

### 配置文件位置

在设备终端上创建配置文件：
```bash
/etc/rsyslog.d/50-threat-detection.conf
```

### 配置示例

```conf
# /etc/rsyslog.d/50-threat-detection.conf
# 威胁检测系统 - rsyslog 配置

# 1. 加载必需模块
module(load="imfile")   # 文件输入
module(load="omfwd")    # TCP/UDP 转发

# 2. 定义模板 - 纯文本格式（保持原始key=value格式）
template(name="ThreatDetectionPlainText" type="string"
         string="%msg%\n")

# 3. 监控攻击事件日志文件
input(type="imfile"
      File="/var/log/threat-detection/attack-events.log"
      Tag="attack-event"
      Severity="info"
      Facility="local0"
      addMetadata="on"
      reopenOnTruncate="on"
      readMode="0"
      maxLinesAtOnce="1000"
      maxSubmitAtOnce="1000")

# 4. 监控心跳日志文件
input(type="imfile"
      File="/var/log/threat-detection/heartbeat.log"
      Tag="heartbeat"
      Severity="info"
      Facility="local0"
      addMetadata="on"
      reopenOnTruncate="on"
      readMode="0"
      maxLinesAtOnce="100"
      maxSubmitAtOnce="100")

# 5. 转发到 Logstash (TCP 9080, 无 TLS)
if $syslogtag contains 'attack-event' or $syslogtag contains 'heartbeat' then {
  action(type="omfwd"
         Target="<LOGSTASH_HOST>"
         Port="9080"
         Protocol="tcp"
         Template="ThreatDetectionPlainText"
         
         # 队列配置（防止日志丢失）
         queue.type="LinkedList"
         queue.size="50000"
         queue.dequeueBatchSize="1000"
         queue.maxDiskSpace="1g"
         queue.saveOnShutdown="on"
         
         # 重试配置
         action.resumeRetryCount="-1"
         action.resumeInterval="10")
}
```

### 配置参数说明

| 参数 | 说明 |
|------|------|
| `<LOGSTASH_HOST>` | Logstash 服务器 IP 或域名 |
| `File` | 攻击事件/心跳日志文件路径 |
| `queue.size` | 队列大小（50000条日志） |
| `queue.dequeueBatchSize` | 批量发送大小（1000条/批） |
| `queue.maxDiskSpace` | 持久化队列磁盘限制（1GB） |
| `action.resumeRetryCount` | 重试次数（-1 = 无限） |
| `action.resumeInterval` | 重试间隔（10秒） |

### 应用配置

```bash
# 1. 测试配置语法
sudo rsyslogd -N1 -f /etc/rsyslog.d/50-threat-detection.conf

# 2. 重启 rsyslog
sudo systemctl restart rsyslog

# 3. 检查状态
sudo systemctl status rsyslog

# 4. 查看日志
sudo tail -f /var/log/syslog

# 5. 创建测试日志
echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$(date +%s)" | nc localhost 9080
```

**详细配置说明请参考**: [RSYSLOG_CONFIG.md](./RSYSLOG_CONFIG.md)

---

## 测试

### 1. 测试 Logstash TCP 连接

```bash
# 使用 netcat 发送测试 JSON
echo '{"attackMac":"00:11:22:33:44:55","attackIp":"192.168.1.100","responseIp":"10.0.0.1","responsePort":3306,"deviceSerial":"DEV-001","customerId":"customer-001","timestamp":"2024-01-15T10:30:00Z","logTime":1705315800}' | nc localhost 5140
```

### 2. 测试脚本

参见 `/scripts/test/test_logstash_ingestion.sh`

```bash
cd /home/kylecui/threat-detection-system
./scripts/test/test_logstash_ingestion.sh
```

### 3. 验证 Kafka 接收

```bash
# 进入 Kafka 容器
docker-compose exec kafka bash

# 消费 attack-events topic
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 10
```

### 4. 端到端测试

```bash
# 1. 发送测试事件到 Logstash
./scripts/test/send_test_events.sh

# 2. 验证 Kafka 消息
./scripts/test/verify_kafka_messages.sh

# 3. 检查 Logstash 统计
curl http://localhost:9600/_node/stats/pipelines/attack-events
```

---

## 性能调优

### Logstash 调优

**JVM 堆内存** (`docker-compose.yml`):
```yaml
environment:
  LS_JAVA_OPTS: "-Xmx1g -Xms1g"  # 根据负载调整
```

**Pipeline 配置** (`logstash.yml`):
```yaml
pipeline.workers: 4                # CPU 核心数
pipeline.batch.size: 125           # 批量大小
pipeline.batch.delay: 50           # 批量延迟 (ms)
```

**队列配置** (`logstash.yml`):
```yaml
queue.type: persisted              # 持久化队列
queue.max_bytes: 1gb               # 队列大小
queue.checkpoint.writes: 1024      # 检查点频率
```

### rsyslog 调优

**队列大小**:
```conf
queue.size="50000"                 # 增加队列（高吞吐量）
queue.dequeueBatchSize="500"       # 增加批量大小
```

**TCP 参数**:
```conf
queue.timeoutEnqueue="0"           # 非阻塞入队
queue.timeoutShutdown="10000"      # 关闭超时 (ms)
```

---

## 故障排查

### Logstash 无法启动

```bash
# 检查配置语法
docker-compose exec logstash logstash --config.test_and_exit

# 查看详细日志
docker-compose logs logstash --tail 100

# 检查端口占用
sudo netstat -tlnp | grep 5140
```

### rsyslog 无法连接

```bash
# 测试 Logstash 端口可达性
telnet <LOGSTASH_HOST> 5140

# 检查防火墙
sudo ufw status
sudo ufw allow 5140/tcp

# 检查 rsyslog 错误日志
sudo tail -f /var/log/syslog | grep rsyslog
```

### 消息未到达 Kafka

```bash
# 1. 检查 Logstash Pipeline 统计
curl http://localhost:9600/_node/stats/pipelines/attack-events | jq '.pipelines.attack-events.events'

# 输出示例：
# {
#   "in": 1000,         # 输入事件数
#   "filtered": 1000,   # 过滤后事件数
#   "out": 950          # 输出到 Kafka 的事件数
# }

# 2. 检查错误日志
docker-compose exec logstash tail -f /usr/share/logstash/logs/logstash-plain.log

# 3. 检查 Kafka Topic
docker-compose exec kafka kafka-topics --describe --topic attack-events --bootstrap-server localhost:9092

# 4. 验证消息格式
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 1 | jq .
```

### 性能问题

```bash
# 1. 检查 Logstash JVM 堆使用
curl http://localhost:9600/_node/stats/jvm | jq '.jvm.mem.heap_used_percent'

# 2. 检查 Pipeline 积压
curl http://localhost:9600/_node/stats/pipelines | jq '.pipelines.attack-events.events.queue_push_duration_in_millis'

# 3. 检查 Kafka 延迟
docker-compose exec kafka kafka-consumer-perf-test \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --messages 1000
```

---

## TLS 配置（未来）

### 1. 生成证书

```bash
cd /home/kylecui/threat-detection-system/infrastructure/logging
./generate_tls_certs.sh
```

### 2. Logstash TLS 配置

编辑 `pipeline/attack-events.conf`:
```conf
input {
  tcp {
    port => 5141
    codec => json_lines
    type => "attack-event"
    ssl_enable => true
    ssl_cert => "/usr/share/logstash/config/certs/logstash.crt"
    ssl_key => "/usr/share/logstash/config/certs/logstash.key"
    ssl_verify => false  # 或 true（需要客户端证书）
  }
}
```

### 3. rsyslog TLS 配置

```conf
# 加载 TLS 模块
module(load="ossl")

# TLS 参数
action(type="omfwd"
       Target="<LOGSTASH_HOST>"
       Port="5141"
       Protocol="tcp"
       StreamDriver="ossl"
       StreamDriverMode="1"
       StreamDriverAuthMode="x509/name"
       StreamDriverPermittedPeers="logstash.example.com")
```

---

## 监控和告警

### Prometheus 指标

Logstash 暴露 JMX 指标，可通过 Prometheus JMX Exporter 采集：

```yaml
# docker-compose.yml
logstash:
  environment:
    LS_JAVA_OPTS: "-javaagent:/usr/share/logstash/jmx_exporter.jar=9101:/usr/share/logstash/jmx_exporter_config.yaml"
  ports:
    - "9101:9101"  # Prometheus metrics
```

### 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `events.in` | 输入事件数 | < 100/s（低流量） |
| `events.out` | 输出事件数 | < events.in（丢失） |
| `heap_used_percent` | JVM 堆使用率 | > 80% |
| `queue_push_duration_in_millis` | 队列推送延迟 | > 1000ms |

---

## 参考资源

- [Logstash 官方文档](https://www.elastic.co/guide/en/logstash/current/index.html)
- [rsyslog 官方文档](https://www.rsyslog.com/doc/master/index.html)
- [Kafka 输出插件](https://www.elastic.co/guide/en/logstash/current/plugins-outputs-kafka.html)
- [TCP 输入插件](https://www.elastic.co/guide/en/logstash/current/plugins-inputs-tcp.html)

---

**文档版本**: 1.0  
**更新日期**: 2025-10-17  
**维护者**: Cloud-Native Threat Detection Team
