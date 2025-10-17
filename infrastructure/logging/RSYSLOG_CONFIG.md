# rsyslog 设备端配置指南

## 概述

本文档说明如何在蜜罐设备终端上配置 rsyslog，将攻击日志和心跳日志转发到 Logstash。

---

## 日志格式

### 攻击日志 (log_type=1)

**格式**: key=value 对，逗号分隔

**示例**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=65536,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274810
```

**字段说明**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `syslog_version` | String | ✅ | 日志协议版本 (如 1.10.0) |
| `dev_serial` | String | ✅ | **设备序列号** (如 GSFB2204200410007425) |
| `log_type` | Integer | ✅ | 日志类型: **1=攻击日志** |
| `sub_type` | Integer | ❌ | 攻击子类型 |
| `attack_mac` | String | ✅ | **被诱捕者MAC地址** (内网失陷主机) |
| `attack_ip` | String | ✅ | **被诱捕者IP地址** |
| `response_ip` | String | ✅ | **诱饵IP** (虚拟哨兵，不存在的IP) |
| `response_port` | Integer | ✅ | **攻击者尝试的端口** (暴露攻击意图) |
| `line_id` | Integer | ❌ | 线路ID |
| `Iface_type` | Integer | ❌ | 接口类型 |
| `Vlan_id` | Integer | ❌ | VLAN ID |
| `log_time` | Long | ✅ | **Unix时间戳 (秒)** |

### 心跳日志 (log_type=2)

**格式**: key=value 对，逗号分隔

**示例**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6453,real_host_count=673,dev_start_time=1747274780,dev_end_time=-1,time=2025-05-15 10:06:50
```

**字段说明**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `syslog_version` | String | ✅ | 日志协议版本 |
| `dev_serial` | String | ✅ | **设备序列号** |
| `log_type` | Integer | ✅ | 日志类型: **2=心跳日志** |
| `sentry_count` | Integer | ✅ | **当前活跃的诱饵IP数量** |
| `real_host_count` | Integer | ✅ | **检测到的真实主机数量** |
| `dev_start_time` | Long | ✅ | 设备启动时间戳 (Unix秒) |
| `dev_end_time` | Long | ✅ | 设备运行截止时间 (-1表示无限) |
| `time` | String | ✅ | 心跳时间 (格式: yyyy-MM-dd HH:mm:ss) |

---

## rsyslog 配置

### 方案1: 直接TCP转发（推荐）

**适用场景**: 设备日志已经是正确的 key=value 格式

**配置文件**: `/etc/rsyslog.d/50-threat-detection.conf`

```conf
# ============================================================================
# 威胁检测系统 - rsyslog配置
# 功能: 将攻击日志和心跳日志转发到Logstash
# ============================================================================

# 1. 加载必需模块
module(load="imfile")   # 文件输入模块
module(load="omfwd")    # TCP/UDP转发模块

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
         action.resumeInterval="10"
         
         # 流控制
         queue.timeoutEnqueue="0"
         queue.timeoutShutdown="1000")
  
  # 本地备份（可选）
  # action(type="omfile" File="/var/log/threat-detection/forwarded.log")
}
```

**配置参数说明**:

| 参数 | 值 | 说明 |
|------|---|------|
| `<LOGSTASH_HOST>` | 替换为实际IP | Logstash服务器地址 |
| `queue.size` | 50000 | 队列大小（50000条日志） |
| `queue.dequeueBatchSize` | 1000 | 批量发送大小 |
| `queue.maxDiskSpace` | 1g | 持久化队列磁盘限制 |
| `action.resumeRetryCount` | -1 | 无限重试 |
| `action.resumeInterval` | 10 | 重试间隔10秒 |

### 方案2: 实时日志流转发

**适用场景**: 从应用程序直接写入syslog

**配置文件**: `/etc/rsyslog.d/50-threat-detection.conf`

```conf
# ============================================================================
# 威胁检测系统 - 实时日志流配置
# ============================================================================

# 1. 加载模块
module(load="imuxsock")  # Unix socket输入
module(load="omfwd")     # TCP转发

# 2. Unix socket监听（供应用程序写入）
input(type="imuxsock" Socket="/var/run/threat-detection.sock"
      CreatePath="on"
      useSpecialParser="off")

# 3. 定义模板
template(name="ThreatDetectionPlainText" type="string"
         string="%msg%\n")

# 4. 过滤和转发
if $programname == 'threat-detection' then {
  action(type="omfwd"
         Target="<LOGSTASH_HOST>"
         Port="5140"
         Protocol="tcp"
         Template="ThreatDetectionPlainText"
         queue.type="LinkedList"
         queue.size="50000"
         action.resumeRetryCount="-1"
         action.resumeInterval="10")
}
```

**应用程序写入示例 (Python)**:

```python
import syslog
import socket
import time

# 连接到rsyslog Unix socket
sock = socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM)
sock.connect("/var/run/threat-detection.sock")

# 发送攻击日志
attack_log = (
    "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,"
    "sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,"
    "response_ip=192.168.73.37,response_port=3389,line_id=1,Iface_type=1,"
    f"Vlan_id=0,log_time={int(time.time())}"
)
sock.send(f"<134>threat-detection: {attack_log}".encode())

# 发送心跳日志
heartbeat_log = (
    "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,"
    "sentry_count=6453,real_host_count=673,dev_start_time=1747274780,"
    f"dev_end_time=-1,time={time.strftime('%Y-%m-%d %H:%M:%S')}"
)
sock.send(f"<134>threat-detection: {heartbeat_log}".encode())

sock.close()
```

---

## 配置步骤

### 1. 安装 rsyslog（如果未安装）

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y rsyslog

# CentOS/RHEL
sudo yum install -y rsyslog
```

### 2. 创建配置文件

```bash
# 创建配置文件
sudo vi /etc/rsyslog.d/50-threat-detection.conf

# 粘贴上述配置内容（方案1或方案2）
# 替换 <LOGSTASH_HOST> 为实际的Logstash服务器IP
```

### 3. 测试配置语法

```bash
# 测试配置文件语法
sudo rsyslogd -N1 -f /etc/rsyslog.d/50-threat-detection.conf

# 输出示例:
# rsyslogd: version 8.2312.0, config validation run (level 1), master config /etc/rsyslog.conf
# rsyslogd: End of config validation run. Bye.
```

### 4. 重启 rsyslog 服务

```bash
# 重启服务
sudo systemctl restart rsyslog

# 检查状态
sudo systemctl status rsyslog

# 查看日志（排查问题）
sudo tail -f /var/log/syslog | grep rsyslog
```

### 5. 创建日志目录和测试文件

```bash
# 创建日志目录
sudo mkdir -p /var/log/threat-detection

# 写入测试攻击日志
echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$(date +%s)" | sudo tee -a /var/log/threat-detection/attack-events.log

# 写入测试心跳日志
echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6453,real_host_count=673,dev_start_time=$(date +%s),dev_end_time=-1,time=$(date '+%Y-%m-%d %H:%M:%S')" | sudo tee -a /var/log/threat-detection/heartbeat.log
```

### 6. 验证日志转发

```bash
# 检查rsyslog统计
sudo systemctl status rsyslog

# 检查rsyslog队列
sudo ls -lh /var/spool/rsyslog/

# 测试TCP连接
telnet <LOGSTASH_HOST> 5140
```

---

## 测试和验证

### 测试1: 手动发送测试日志

```bash
#!/bin/bash

# 发送测试攻击日志
cat << EOF | sudo tee -a /var/log/threat-detection/attack-events.log
syslog_version=1.10.0,dev_serial=TEST-DEVICE-001,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=10.0.0.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=$(date +%s)
EOF

# 等待1秒
sleep 1

# 检查Logstash是否收到（在Logstash服务器上执行）
# docker-compose exec kafka kafka-console-consumer \
#   --bootstrap-server localhost:9092 \
#   --topic attack-events \
#   --from-beginning \
#   --max-messages 1
```

### 测试2: 持续生成测试日志

```bash
#!/bin/bash

# 每秒生成一条攻击日志
while true; do
  TIMESTAMP=$(date +%s)
  DATETIME=$(date '+%Y-%m-%d %H:%M:%S')
  
  # 攻击日志
  echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:$(printf '%02X:%02X:%02X:%02X:%02X' $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256))),attack_ip=192.168.1.$((RANDOM%254+1)),response_ip=10.0.0.$((RANDOM%254+1)),response_port=$((RANDOM%65535+1)),line_id=1,Iface_type=1,Vlan_id=0,log_time=${TIMESTAMP}" >> /var/log/threat-detection/attack-events.log
  
  # 每10秒发送一次心跳日志
  if [ $((TIMESTAMP % 10)) -eq 0 ]; then
    echo "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=$((RANDOM%10000)),real_host_count=$((RANDOM%1000)),dev_start_time=${TIMESTAMP},dev_end_time=-1,time=${DATETIME}" >> /var/log/threat-detection/heartbeat.log
  fi
  
  sleep 1
done
```

### 测试3: 检查Kafka消息

在 Logstash/Kafka 服务器上：

```bash
# 检查攻击日志topic
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 5 | jq .

# 检查心跳日志topic
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic status-events \
  --from-beginning \
  --max-messages 5 | jq .
```

**预期输出 (attack-events)**:
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

**预期输出 (status-events)**:
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

---

## 性能调优

### rsyslog 高频率场景

**场景**: 每秒产生 1000+ 条攻击日志

**优化配置**:

```conf
# 增加队列和批量大小
queue.size="100000"                    # 10万条缓存
queue.dequeueBatchSize="5000"          # 5000条/批
queue.maxDiskSpace="5g"                # 5GB持久化

# 增加工作线程
queue.workerThreads="4"                # 4个工作线程

# TCP优化
queue.timeoutEnqueue="0"               # 非阻塞入队
action.resumeRetryCount="3"            # 重试3次后丢弃（避免阻塞）
```

### 网络延迟优化

**场景**: Logstash服务器距离较远或网络不稳定

**优化配置**:

```conf
# TCP keepalive
queue.timeoutActionCompletion="60000"  # 60秒超时

# 压缩传输（需要Logstash支持）
compression.mode="stream:always"

# 本地备份（防止丢失）
action(type="omfile" 
       File="/var/log/threat-detection/backup-%$year%-%$month%-%$day%.log"
       queue.type="LinkedList"
       queue.size="10000")
```

---

## 故障排查

### 问题1: 日志未转发

**症状**: Kafka中没有收到消息

**排查步骤**:

1. **检查rsyslog状态**:
```bash
sudo systemctl status rsyslog
sudo tail -100 /var/log/syslog | grep rsyslog
```

2. **检查TCP连接**:
```bash
# 查看rsyslog连接
sudo netstat -anp | grep rsyslog

# 测试Logstash端口
telnet <LOGSTASH_HOST> 9080
```

3. **检查日志文件权限**:
```bash
sudo ls -l /var/log/threat-detection/
# 确保rsyslog可读
sudo chmod 644 /var/log/threat-detection/*.log
```

4. **启用rsyslog调试日志**:
```bash
# 临时启用调试
sudo rsyslogd -N1 -d

# 或修改配置
echo '$DebugLevel 2' | sudo tee -a /etc/rsyslog.conf
sudo systemctl restart rsyslog
```

### 问题2: 日志格式错误

**症状**: Logstash解析失败，日志进入failed_events

**排查步骤**:

1. **检查Logstash错误日志**:
```bash
# 在Logstash服务器上
docker-compose logs logstash | grep -i error

# 查看失败日志
docker-compose exec logstash cat /usr/share/logstash/logs/failed_events_$(date +%Y-%m-%d).log | jq .
```

2. **验证日志格式**:
```bash
# 检查原始日志
tail -1 /var/log/threat-detection/attack-events.log

# 手动解析测试
echo "syslog_version=1.10.0,dev_serial=TEST,log_type=1" | \
  awk -F',' '{for(i=1;i<=NF;i++){split($i,a,"="); print a[1]": "a[2]}}'
```

3. **常见格式错误**:
- ❌ `attack_mac=00-68-eb-bb-c7-c6` (使用连字符)  
  ✅ `attack_mac=00:68:eb:bb:c7:c6` (应使用冒号)
  
- ❌ `log_time=2025-01-15 10:06:50` (字符串时间)  
  ✅ `log_time=1747274810` (Unix时间戳)
  
- ❌ `response_port=` (空值)  
  ✅ `response_port=3389` (必须有值)

### 问题3: 日志延迟过高

**症状**: 日志从设备到Kafka延迟 > 10秒

**排查步骤**:

1. **检查rsyslog队列**:
```bash
# 查看队列积压
sudo ls -lh /var/spool/rsyslog/

# 增加批量大小
# queue.dequeueBatchSize="5000"
```

2. **检查Logstash性能**:
```bash
# Pipeline统计
curl http://localhost:9600/_node/stats/pipelines | jq '.pipelines.main.events'

# 堆内存使用
curl http://localhost:9600/_node/stats/jvm | jq '.jvm.mem.heap_used_percent'
```

3. **检查网络延迟**:
```bash
# Ping测试
ping -c 10 <LOGSTASH_HOST>

# TCP延迟测试
time echo "test" | nc <LOGSTASH_HOST> 5140
```

---

## TLS 配置（未来）

### rsyslog TLS配置

```conf
# 加载TLS模块
module(load="ossl")

# TLS配置
global(
  defaultNetstreamDriver="ossl"
  defaultNetstreamDriverCAFile="/etc/ssl/certs/ca.crt"
  defaultNetstreamDriverCertFile="/etc/ssl/certs/client.crt"
  defaultNetstreamDriverKeyFile="/etc/ssl/private/client.key"
)

# TLS转发
action(type="omfwd"
       Target="<LOGSTASH_HOST>"
       Port="9081"
       Protocol="tcp"
       StreamDriver="ossl"
       StreamDriverMode="1"
       StreamDriverAuthMode="x509/name"
       StreamDriverPermittedPeers="logstash.example.com"
       Template="ThreatDetectionPlainText")
```

---

## 相关文档

- **[Logstash配置指南](./README.md)** - Logstash服务端配置
- **[数据摄取API文档](../../docs/api/data_ingestion_api.md)** - API详细说明
- **[日志格式分析](../../docs/log_format_analysis.md)** - 日志字段详解

---

**文档版本**: 1.0  
**更新日期**: 2025-10-17  
**维护者**: Cloud-Native Threat Detection Team
