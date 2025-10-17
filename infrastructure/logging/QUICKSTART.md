# Logstash 对接快速启动指南

## 快速开始（5分钟）

### 1. 启动 Logstash 容器

```bash
cd /home/kylecui/threat-detection-system/docker
docker-compose up -d logstash
```

### 2. 检查服务状态

```bash
# 测试TCP连接
telnet <LOGSTASH_HOST> 9080
nc -zv <LOGSTASH_HOST> 9080
```

### 3. 发送测试日志

**攻击日志**:
```bash
echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$(date +%s)" | nc localhost 9080
```

**心跳日志**:
```bash
echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6453,real_host_count=673,dev_start_time=$(date +%s),dev_end_time=-1,time=$(date '+%Y-%m-%d %H:%M:%S')" | nc localhost 9080
```

### 4. 验证 Kafka 消息

**攻击日志 topic**:
```bash
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 1 | jq .
```

**心跳日志 topic**:
```bash
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic status-events \
  --from-beginning \
  --max-messages 1 | jq .
```

### 5. 运行自动化测试

```bash
cd /home/kylecui/threat-detection-system
chmod +x scripts/test/test_logstash_ingestion.sh
./scripts/test/test_logstash_ingestion.sh
```

---

## 预期输出

### 攻击日志 (attack-events)

```json
{
  "deviceSerial": "GSFB2204200410007425",
  "attackMac": "00:68:EB:BB:C7:C6",
  "attackIp": "192.168.73.98",
  "responseIp": "192.168.73.37",
  "responsePort": 3389,
  "logTime": 1747274810,
  "timestamp": "2025-01-15T10:06:50Z",
  "syslogVersion": "1.10.0",
  "subType": "1",
  "lineId": "1",
  "ifaceType": "1",
  "vlanId": "0",
  "logstashProcessedAt": "2025-01-15T10:06:51Z",
  "@timestamp": "2025-01-15T10:06:50.000Z"
}
```

**关键字段说明**:
- `attackMac`: 被诱捕者MAC（已标准化为大写+冒号）
- `attackIp`: 被诱捕者IP（内网失陷主机）
- `responseIp`: 诱饵IP（虚拟哨兵）
- `responsePort`: 攻击者尝试的端口（暴露攻击意图，如3389=RDP远程控制）
- `timestamp`: ISO8601格式时间戳（由logTime转换）

### 心跳日志 (status-events)

```json
{
  "deviceSerial": "GSFB2204200410007425",
  "sentryCount": 6453,
  "realHostCount": 673,
  "devStartTime": 1747274780,
  "devEndTime": -1,
  "heartbeatTime": "2025-05-15 10:06:50",
  "syslogVersion": "1.10.0",
  "logstashProcessedAt": "2025-01-15T10:06:51Z",
  "@timestamp": "2025-05-15T10:06:50.000Z"
}
```

**关键字段说明**:
- `sentryCount`: 当前活跃的诱饵IP数量（6453个虚拟哨兵）
- `realHostCount`: 检测到的真实主机数量（673台设备）
- `devStartTime`: 设备启动时间戳
- `devEndTime`: 设备运行截止时间（-1表示无限期）

---

## 字段命名规范

### Logstash Pipeline: snake_case → camelCase 转换

**原始日志 (rsyslog)**:
```
dev_serial=GSFB2204200410007425,attack_mac=00:68:eb:bb:c7:c6
```

**Kafka消息 (camelCase)**:
```json
{
  "deviceSerial": "GSFB2204200410007425",
  "attackMac": "00:68:EB:BB:C7:C6"
}
```

**转换规则**:
- `dev_serial` → `deviceSerial`
- `attack_mac` → `attackMac`
- `attack_ip` → `attackIp`
- `response_ip` → `responseIp`
- `response_port` → `responsePort`
- `log_time` → `logTime`
- `syslog_version` → `syslogVersion`

**为什么使用 camelCase?**
- Kafka消息是Java对象直接序列化
- 符合Java命名规范
- 与HTTP API的snake_case区分开

---

## 故障排查

### Logstash 无法启动

```bash
# 查看错误日志
docker-compose logs logstash | tail -50

# 检查配置语法
docker-compose exec logstash logstash --config.test_and_exit -f /usr/share/logstash/pipeline/attack-events.conf

# 重新构建镜像
docker-compose build --no-cache logstash
docker-compose up -d logstash
```

### 无法连接 TCP 5140

```bash
# 检查端口
docker-compose ps logstash
netstat -tlnp | grep 9080

# 测试连接
telnet localhost 9080
nc -zv localhost 9080

# 检查防火墙
sudo ufw status
sudo ufw allow 9080/tcp
```

### Kafka 无消息

```bash
# 检查 Logstash Pipeline 统计
curl http://localhost:9600/_node/stats/pipelines | jq '.pipelines.main.events'

# 查看 Logstash 错误日志
docker-compose exec logstash cat /usr/share/logstash/logs/logstash-plain.log | tail -50

# 查看失败日志
docker-compose exec logstash cat /usr/share/logstash/logs/failed_events_$(date +%Y-%m-%d).log | jq .

# 检查 Kafka topic
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
docker-compose exec kafka kafka-topics --describe --topic attack-events --bootstrap-server localhost:9092
```

### 消息格式错误

```bash
# 查看原始消息
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 5

# 检查字段命名
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 1 | jq 'keys'

# 预期输出（camelCase）:
# ["@timestamp", "attackIp", "attackMac", "deviceSerial", "logTime", 
#  "logstashProcessedAt", "responseIp", "responsePort", "syslogVersion", "timestamp"]
```

---

## 性能监控

### Logstash 指标

```bash
# 节点统计
curl http://localhost:9600/_node/stats | jq .

# Pipeline 统计
curl http://localhost:9600/_node/stats/pipelines | jq '.pipelines.main.events'

# JVM 堆内存
curl http://localhost:9600/_node/stats/jvm | jq '.jvm.mem.heap_used_percent'
```

### Kafka 性能

```bash
# Topic 消息数量
docker-compose exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic attack-events

# Consumer lag
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group threat-detection-group
```

---

## 下一步

### 1. 配置真实设备 rsyslog

参考文档: [RSYSLOG_CONFIG.md](./RSYSLOG_CONFIG.md)

```bash
# 在设备上安装rsyslog
sudo apt-get install rsyslog

# 创建配置
sudo vi /etc/rsyslog.d/50-threat-detection.conf

# 重启rsyslog
sudo systemctl restart rsyslog
```

### 2. 启用 TLS 加密（生产环境）

```bash
# 生成证书
cd /home/kylecui/threat-detection-system/infrastructure/logging
./generate_tls_certs.sh

# 更新 Logstash 配置（取消注释TLS部分）
vi logstash/pipeline/attack-events.conf

# 重启服务
docker-compose restart logstash
```

### 3. 集成 Data Ingestion Service

Data Ingestion Service 将从 Kafka 消费消息，查询 `customerId`，并进行威胁评分。

**数据流向**:
```
设备rsyslog → Logstash (TCP 5140) → Kafka (attack-events/status-events)
    ↓
Data Ingestion (消费Kafka) → 查询customerId → 发布到minute-aggregations
    ↓
Stream Processing (Flink) → 威胁评分 → 发布到threat-alerts
    ↓
Threat Assessment → PostgreSQL → Alert Management → 邮件通知
```

### 4. 性能测试

```bash
# 大量日志压力测试
for i in {1..10000}; do
  echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:$(printf '%02X:%02X:%02X:%02X:%02X' $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256))),attack_ip=192.168.73.$((RANDOM%254+1)),response_ip=192.168.73.$((RANDOM%254+1)),response_port=$((RANDOM%65535+1)),line_id=$i,Iface_type=1,Vlan_id=0,log_time=$(date +%s)" | nc localhost 9080
done

# 检查处理速度
curl http://localhost:9600/_node/stats/pipelines | jq '.pipelines.main.events.in'
```

---

## 参考文档

- **[README.md](./README.md)** - Logstash 配置总览
- **[RSYSLOG_CONFIG.md](./RSYSLOG_CONFIG.md)** - rsyslog 详细配置
- **[数据摄取 API 文档](../../docs/api/data_ingestion_api.md)** - Data Ingestion Service API
- **[蜜罐威胁评分方案](../../docs/honeypot_based_threat_scoring.md)** - 威胁评分算法

---

**文档版本**: 1.0  
**更新日期**: 2025-10-17  
**状态**: ✅ 测试就绪
