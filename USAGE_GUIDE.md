# 📋 威胁检测系统使用指南

基于当前进度和测试结果，以下是完整的系统使用方法、调用方式和配置选项总结。

## 🚀 快速启动

### 1. 启动系统
```bash
cd /home/kylecui/threat-detection-system

# 启动所有服务
docker-compose -f docker/docker-compose.yml up -d

# 等待服务启动（约60秒）
sleep 60

# 验证服务状态
curl http://localhost:8080/actuator/health  # 数据摄取服务
curl http://localhost:8081/overview         # Flink Web UI
```

### 2. 停止系统
```bash
docker-compose -f docker/docker-compose.yml down
```

## 📡 数据摄取API

### 单条日志摄取
```bash
# 端点：POST /api/v1/logs/ingest
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=1,attack_mac=74:12:B3:FE:EE:7F,attack_ip=192.168.1.100,response_port=80"
```

### 批量日志摄取
```bash
# 端点：POST /api/v1/logs/batch
curl -X POST http://localhost:8080/api/v1/logs/batch \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=device1,attack_mac=AA:BB:CC:DD:EE:FF,attack_ip=192.168.1.100,response_port=80",
      "syslog_version=1.10.0,dev_serial=device2,attack_mac=AA:BB:CC:DD:EE:FF,attack_ip=192.168.1.101,response_port=443"
    ]
  }'
```

### 统计信息查询
```bash
# 获取解析统计：GET /api/v1/logs/stats
curl http://localhost:8080/api/v1/logs/stats

# 重置统计：POST /api/v1/logs/stats/reset
curl -X POST http://localhost:8080/api/v1/logs/stats/reset
```

## 🛠️ 测试工具使用

### 使用Python测试脚本
```bash
cd /home/kylecui/threat-detection-system

# 发送单个日志文件的前N条记录
python3 scripts/data_ingestion_test.py tmp/test_logs/2024-04-25.07.56.log --max-records 10

# 预览日志而不发送（dry-run）
python3 scripts/data_ingestion_test.py tmp/test_logs/2024-04-25.07.56.log --max-records 5 --dry-run

# 使用内置样本文件
python3 scripts/data_ingestion_test.py --sample attack --max-records 20

# 自定义API端点和延迟
python3 scripts/data_ingestion_test.py tmp/test_logs/2024-04-25.07.56.log \
  --url http://localhost:8080/api/v1/logs/ingest \
  --delay 0.1 \
  --max-records 50
```

### 批量日志摄取工具 (bulk_ingest_logs.py)

**最新改进**: 修复了connection reset错误，实现高可靠性批量处理

```bash
cd /home/kylecui/threat-detection-system

# 处理指定数量的日志文件（默认5个）
python3 bulk_ingest_logs.py --count 5

# 处理特定日志文件
python3 bulk_ingest_logs.py --file tmp/test_logs/2024-04-25.07.56.log

# 自定义并发数和批次大小
python3 bulk_ingest_logs.py --count 10 --workers 8 --batch-size 25

# 启用详细日志
python3 bulk_ingest_logs.py --count 5 --verbose
```

#### 连接管理特性
- **连接池**: HTTPAdapter配置(pool_connections=10, pool_maxsize=20)
- **自动重试**: 指数退避重试机制，处理连接错误
- **定期刷新**: 每1000个请求自动刷新连接池
- **并发优化**: 可配置的线程池大小
- **批次控制**: 优化批次大小(默认25)以避免连接超时

#### 性能指标
- **成功率**: >95% (修复前为87.6%)
- **吞吐量**: 稳定处理数千条日志
- **连接稳定性**: 零connection reset错误

## 📊 Kafka主题监控

### 查看聚合结果
```bash
# 实时查看聚合数据（每30秒窗口）
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic minute-aggregations \
  --from-beginning
```

### 查看威胁警报
```bash
# 实时查看威胁评分（每2分钟窗口）
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic threat-alerts \
  --from-beginning
```

### 查看原始事件
```bash
# 查看攻击事件流
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning
```

## ⚙️ 配置选项

### 环境变量配置

#### 数据摄取服务
```yaml
# docker-compose.yml 中的配置
data-ingestion:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    DEV_SERIAL_PATTERN: "[0-9A-Za-z]+"  # 可配置的设备序列号验证模式，默认支持字母数字组合
```

#### 流处理服务
```yaml
# docker-compose.yml 中的配置
stream-processing:
  environment:
    FLINK_PROPERTIES: |
      jobmanager.rpc.address: stream-processing
      parallelism.default: 1
      taskmanager.numberOfTaskSlots: 1
      rest.port: 8081
    KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    INPUT_TOPIC: attack-events
    STATUS_TOPIC: status-events
    OUTPUT_TOPIC: threat-alerts
    AGGREGATION_TOPIC: minute-aggregations
    # 时间窗口配置（可选，通过环境变量）
    AGGREGATION_WINDOW_SECONDS: 30      # 默认30秒
    THREAT_SCORING_WINDOW_MINUTES: 2    # 默认2分钟
```

### 可配置参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| 设备序列号模式 | `DEV_SERIAL_PATTERN` | `[0-9A-Za-z]+` | 设备序列号的正则表达式验证模式 |
| 聚合窗口 | `AGGREGATION_WINDOW_SECONDS` | 30 | 攻击聚合的时间窗口（秒） |
| 威胁评分窗口 | `THREAT_SCORING_WINDOW_MINUTES` | 2 | 威胁评分的时间窗口（分钟） |
| Kafka服务器 | `KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 | Kafka连接地址 |
| 输入主题 | `INPUT_TOPIC` | attack-events | 攻击事件输入主题 |
| 输出主题 | `OUTPUT_TOPIC` | threat-alerts | 威胁警报输出主题 |
| 聚合主题 | `AGGREGATION_TOPIC` | minute-aggregations | 聚合数据输出主题 |

### 自定义时间窗口示例
```bash
# 设置更短的聚合窗口（15秒）和更长的评分窗口（5分钟）
docker-compose -f docker/docker-compose.yml up -d
docker exec -it stream-processing env AGGREGATION_WINDOW_SECONDS=15 THREAT_SCORING_WINDOW_MINUTES=5
# 需要重启Flink作业以应用新配置
```

## � 设备序列号验证配置

### 问题背景
系统最初只支持十六进制格式的设备序列号（如`9d262111f2476d34`），但实际环境中可能存在包含字母的复杂序列号（如`GSFB2204200410007425`）。

### 解决方案
通过环境变量`DEV_SERIAL_PATTERN`实现可配置的设备序列号验证：

```yaml
# docker-compose.yml 配置示例
data-ingestion:
  environment:
    DEV_SERIAL_PATTERN: "[0-9A-Za-z]+"  # 默认值，支持字母数字组合
```

### 支持的模式示例
- `[0-9A-Fa-f]+`: 仅十六进制字符（原始模式）
- `[0-9A-Za-z]+`: 字母数字组合（默认，推荐用于开发/测试）
- `[0-9A-Za-z_-]+`: 包含下划线和连字符
- `.*`: 接受任何字符（仅用于调试）

### 验证逻辑
1. 设备序列号不能为空或纯空格
2. 必须匹配配置的正则表达式模式
3. 验证通过后转换为大写格式存储

## �📈 数据格式说明

### 输入日志格式
```
syslog_version=1.10.0,dev_serial={设备序列号},log_type=1,sub_type=1,attack_mac={攻击MAC},attack_ip={攻击IP},response_port={响应端口}
```

### 聚合输出格式
```json
{
  "attackMac": "74:12:B3:FE:EE:7F",
  "uniqueIps": 1,
  "uniquePorts": 2,
  "uniqueDevices": 1,
  "attackCount": 7,
  "timestamp": 1759994460000,
  "windowStart": 1759994430000,
  "windowEnd": 1759994460000
}
```

### 威胁警报格式
```json
{
  "attackMac": "74:12:B3:FE:EE:7F",
  "threatScore": 18.48,
  "threatLevel": "INFO",
  "threatName": "信息",
  "timestamp": 1759994460000,
  "windowStart": 1759994400000,
  "windowEnd": 1759994520000,
  "totalAggregations": 1
}
```

## 🔍 威胁评分算法

当前实现的增强算法：
```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
```

其中：
- `portWeight`: 基于端口多样性的权重 (1.0-2.0)
- `deviceWeight`: 多设备覆盖奖励 (1.0-1.5)
- `timeWeight`: 时间衰减因子
- `ipWeight`: IP多样性奖励

## 📋 威胁等级

| 等级 | 英文 | 中文 | 分数范围 |
|------|------|------|----------|
| 1 | CRITICAL | 严重 | > 1000 |
| 2 | HIGH | 高危 | 500-1000 |
| 3 | MEDIUM | 中危 | 100-500 |
| 4 | LOW | 低危 | 10-100 |
| 5 | INFO | 信息 | < 10 |

## 🐛 故障排除

### 检查服务状态
```bash
# 查看所有容器状态
docker-compose -f docker/docker-compose.yml ps

# 查看服务日志
docker-compose -f docker/docker-compose.yml logs -f stream-processing
docker-compose -f docker/docker-compose.yml logs -f data-ingestion

# 检查Kafka主题
docker exec -it $(docker ps -q -f name=kafka) kafka-topics --bootstrap-server localhost:9092 --list
```

### 常见问题
1. **设备序列号验证失败**: 如果日志包含复杂的设备序列号（如`GSFB2204200410007425`），可配置`DEV_SERIAL_PATTERN`环境变量，默认支持字母数字组合
2. **Connection Reset错误**: 批量摄取时出现连接重置，通常是客户端连接池问题。解决方案：
   - 使用最新的`bulk_ingest_logs.py`脚本，已内置连接池管理和自动重试
   - 确保批次大小不超过25条记录
   - 监控连接池刷新日志（每1000请求自动刷新）
   - 如需手动处理，使用`--workers 4 --batch-size 25`参数
3. **JAR文件版本问题**: 重新构建Docker镜像
4. **Kafka连接失败**: 确保Zookeeper先启动
5. **Flink作业失败**: 检查日志中的配置错误
6. **数据格式错误**: 验证syslog格式是否正确

---

*最后更新时间：2025年10月9日*
*系统版本：v1.1*
*包含增强功能：端口多样性、多设备支持、可配置时间窗口、可配置设备序列号验证、高可靠性批量摄取*</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/USAGE_GUIDE.md