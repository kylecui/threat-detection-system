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
curl http://localhost:8082/actuator/health  # 告警管理服务
curl http://localhost:8083/api/v1/assessment/health  # 威胁评估服务
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

## 🛡️ 威胁评估API

### 威胁评估查询
```bash
# 获取所有威胁评估：GET /api/v1/assessment/threats
curl http://localhost:8083/api/v1/assessment/threats

# 按风险等级过滤：GET /api/v1/assessment/threats?riskLevel=HIGH
curl "http://localhost:8083/api/v1/assessment/threats?riskLevel=HIGH"

# 按时间范围查询：GET /api/v1/assessment/threats?startTime=2024-01-01T00:00:00&endTime=2024-12-31T23:59:59
curl "http://localhost:8083/api/v1/assessment/threats?startTime=2024-01-01T00:00:00&endTime=2024-12-31T23:59:59"

# 获取特定威胁详情：GET /api/v1/assessment/threats/{id}
curl http://localhost:8083/api/v1/assessment/threats/1
```

### 威胁情报查询
```bash
# 获取威胁情报：GET /api/v1/assessment/intelligence
curl http://localhost:8083/api/v1/assessment/intelligence

# 按威胁类型过滤：GET /api/v1/assessment/intelligence?threatType=DDoS
curl "http://localhost:8083/api/v1/assessment/intelligence?threatType=DDoS"
```

### 风险评估统计
```bash
# 获取风险统计：GET /api/v1/assessment/stats
curl http://localhost:8083/api/v1/assessment/stats

# 响应示例：
{
  "totalAssessments": 921,
  "criticalCount": 15,
  "highCount": 89,
  "mediumCount": 234,
  "lowCount": 456,
  "infoCount": 127,
  "averageRiskScore": 245.67
}
```

### 缓解建议查询
```bash
# 获取缓解建议：GET /api/v1/assessment/recommendations/{threatId}
curl http://localhost:8083/api/v1/assessment/recommendations/1

# 响应示例：
{
  "threatId": 1,
  "recommendations": [
    {
      "action": "BLOCK_IP",
      "description": "阻止可疑IP地址",
      "priority": "HIGH",
      "estimatedEffectiveness": 0.95
    },
    {
      "action": "RATE_LIMIT",
      "description": "实施速率限制",
      "priority": "MEDIUM",
      "estimatedEffectiveness": 0.78
    }
  ]
}
```

## � 告警管理API

### 发送通知
```bash
# 发送Email通知：POST /api/v1/alerts/notify/email
curl -X POST http://localhost:8082/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "admin@example.com",
    "subject": "威胁检测告警",
    "content": "检测到高危威胁，风险分数：856.7",
    "threatId": 1
  }'

# 发送SMS通知：POST /api/v1/alerts/notify/sms
curl -X POST http://localhost:8082/api/v1/alerts/notify/sms \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "+8613800000000",
    "content": "威胁告警：检测到SQL注入攻击",
    "threatId": 1
  }'

# 发送Webhook通知：POST /api/v1/alerts/notify/webhook
curl -X POST http://localhost:8082/api/v1/alerts/notify/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "https://webhook.example.com/alert",
    "content": "新威胁检测到",
    "threatId": 1
  }'

# 发送Slack通知：POST /api/v1/alerts/notify/slack
curl -X POST http://localhost:8082/api/v1/alerts/notify/slack \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "https://hooks.slack.com/services/...",
    "content": "威胁告警：高危攻击检测",
    "threatId": 1
  }'

# 发送Teams通知：POST /api/v1/alerts/notify/teams
curl -X POST http://localhost:8082/api/v1/alerts/notify/teams \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "https://outlook.office.com/webhook/...",
    "subject": "安全告警",
    "content": "检测到严重威胁",
    "threatId": 1
  }'
```

### 批量发送通知
```bash
# 批量发送通知：POST /api/v1/alerts/notify/batch
curl -X POST http://localhost:8082/api/v1/alerts/notify/batch \
  -H "Content-Type: application/json" \
  -d '{
    "notifications": [
      {
        "channel": "EMAIL",
        "recipient": "admin@example.com",
        "subject": "威胁告警",
        "content": "检测到高危威胁"
      },
      {
        "channel": "SMS",
        "recipient": "+8613800000000",
        "content": "威胁告警：检测到SQL注入攻击"
      }
    ]
  }'
```

### 查询通知历史
```bash
# 获取所有通知：GET /api/v1/alerts/notifications
curl http://localhost:8082/api/v1/alerts/notifications

# 按状态过滤：GET /api/v1/alerts/notifications?status=SENT
curl "http://localhost:8082/api/v1/alerts/notifications?status=SENT"

# 按通道过滤：GET /api/v1/alerts/notifications?channel=EMAIL
curl "http://localhost:8082/api/v1/alerts/notifications?channel=EMAIL"

# 获取通知统计：GET /api/v1/alerts/notifications/stats
curl http://localhost:8082/api/v1/alerts/notifications/stats

# 响应示例：
{
  "totalNotifications": 150,
  "successfulNotifications": 145,
  "failedNotifications": 5,
  "byChannel": {
    "EMAIL": 80,
    "SMS": 45,
    "SLACK": 15,
    "TEAMS": 10
  },
  "byStatus": {
    "SENT": 145,
    "FAILED": 5
  }
}
```

### 重试失败通知
```bash
# 重试所有失败通知：POST /api/v1/alerts/notifications/retry
curl -X POST http://localhost:8082/api/v1/alerts/notifications/retry
```

## 🔄 端到端数据流

### 完整处理流程
1. **数据摄取**: 日志通过数据摄取API进入系统
2. **流处理**: Flink实时聚合攻击事件（30秒窗口）
3. **威胁评分**: 基于多维度算法计算威胁分数（2分钟窗口）
4. **威胁评估**: 高级风险评估和情报关联
5. **告警生成**: 根据威胁等级自动触发通知
6. **多通道通知**: 支持Email、SMS、Webhook、Slack和Teams通知
7. **存储**: 结果存储到PostgreSQL数据库

### 数据流示例
```bash
# 1. 发送攻击日志
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=device1,attack_mac=AA:BB:CC:DD:EE:FF,attack_ip=192.168.1.100,response_port=80"

# 2. 查看实时聚合（30秒后）
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic minute-aggregations \
  --from-beginning --max-messages 1

# 3. 查看威胁警报（2分钟后）
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic threat-alerts \
  --from-beginning --max-messages 1

# 4. 查询威胁评估结果
curl http://localhost:8083/api/v1/assessment/threats

# 5. 发送告警通知（可选）
curl -X POST http://localhost:8082/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "admin@example.com",
    "subject": "威胁检测告警",
    "content": "检测到高危威胁，风险分数：856.7"
  }'
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

#### 告警管理服务
```yaml
# docker-compose.yml 中的配置
alert-management:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    # Email配置
    # SPRING_MAIL_HOST: smtp.gmail.com
    # SPRING_MAIL_PORT: 587
    # SPRING_MAIL_USERNAME: your-email@gmail.com
    # SPRING_MAIL_PASSWORD: your-app-password
    SPRING_MAIL_HOST: smtp.163.com
    SPRING_MAIL_PORT: 25
    SPRING_MAIL_USERNAME: threat_detection@163.com
    SPRING_MAIL_PASSWORD: ${SMTP_PASSWORD}  # Set via environment variable
    # Never commit real SMTP credentials to version control.
    # SMS配置 (Twilio)
    SMS_PROVIDER: twilio
    TWILIO_ACCOUNT_SID: your-twilio-sid
    TWILIO_AUTH_TOKEN: your-twilio-token
    TWILIO_PHONE_NUMBER: +1234567890
    # SMS配置 (阿里云)
    ALIYUN_ACCESS_KEY_ID: your-aliyun-key
    ALIYUN_ACCESS_KEY_SECRET: your-aliyun-secret
    ALIYUN_SMS_SIGN_NAME: your-sign-name
    ALIYUN_SMS_TEMPLATE_CODE: SMS_123456789
    # Slack配置
    SLACK_WEBHOOK_URL: https://hooks.slack.com/services/...
    # Teams配置
    TEAMS_WEBHOOK_URL: https://outlook.office.com/webhook/...
    # 通知配置
    NOTIFICATION_RETRY_MAX_ATTEMPTS: 3
    NOTIFICATION_RETRY_DELAY_SECONDS: 60
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
| 威胁评估启用 | `THREAT_ASSESSMENT_ENABLED` | true | 是否启用威胁评估服务 |
| 情报更新间隔 | `INTELLIGENCE_UPDATE_INTERVAL` | 3600000 | 威胁情报更新间隔（毫秒） |
| 风险评分权重 | `RISK_SCORING_WEIGHTS` | attackCount:1.0,uniqueIps:1.5,uniquePorts:2.0,deviceCount:1.2 | 多维度风险评分权重配置 |
| SMS提供商 | `SMS_PROVIDER` | twilio | SMS服务提供商 (twilio/aliyun) |
| Twilio账户SID | `TWILIO_ACCOUNT_SID` | - | Twilio账户SID |
| Twilio认证令牌 | `TWILIO_AUTH_TOKEN` | - | Twilio认证令牌 |
| Twilio电话号码 | `TWILIO_PHONE_NUMBER` | - | Twilio发送电话号码 |
| 阿里云访问密钥ID | `ALIYUN_ACCESS_KEY_ID` | - | 阿里云访问密钥ID |
| 阿里云访问密钥密钥 | `ALIYUN_ACCESS_KEY_SECRET` | - | 阿里云访问密钥密钥 |
| 阿里云SMS签名 | `ALIYUN_SMS_SIGN_NAME` | - | 阿里云SMS签名名称 |
| 阿里云SMS模板 | `ALIYUN_SMS_TEMPLATE_CODE` | - | 阿里云SMS模板代码 |
| Slack Webhook URL | `SLACK_WEBHOOK_URL` | - | Slack webhook URL |
| Teams Webhook URL | `TEAMS_WEBHOOK_URL` | - | Microsoft Teams webhook URL |
| 通知重试次数 | `NOTIFICATION_RETRY_MAX_ATTEMPTS` | 3 | 通知失败时的最大重试次数 |
| 通知重试延迟 | `NOTIFICATION_RETRY_DELAY_SECONDS` | 60 | 重试间隔时间（秒） |

### 自定义时间窗口示例
```bash
# 设置更短的聚合窗口（15秒）和更长的评分窗口（5分钟）
docker-compose -f docker/docker-compose.yml up -d
docker exec -it stream-processing env AGGREGATION_WINDOW_SECONDS=15 THREAT_SCORING_WINDOW_MINUTES=5
# 需要重启Flink作业以应用新配置
```

##  威胁评分算法

当前实现的增强算法：
```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
```

其中：
- `portWeight`: 基于端口多样性的权重 (1.0-2.0)
- `deviceWeight`: 多设备覆盖奖励 (1.0-1.5)
- `timeWeight`: 时间衰减因子
- `ipWeight`: IP多样性奖励

### 多维度风险评估
威胁评估服务使用以下维度进行综合风险评估：
- **攻击频率**: 单位时间内的攻击次数
- **IP多样性**: 涉及的不同IP地址数量
- **端口分布**: 目标端口的多样性
- **设备覆盖**: 受影响的设备数量
- **时间模式**: 攻击的时间分布特征
- **威胁情报**: 已知威胁模式的匹配度

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
docker-compose -f docker/docker-compose.yml logs -f threat-assessment
docker-compose -f docker/docker-compose.yml logs -f alert-management

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
6. **威胁评估服务连接失败**: 检查PostgreSQL数据库连接和HikariCP配置
7. **数据格式错误**: 验证syslog格式是否正确
8. **SMS发送失败**: 检查SMS提供商配置（Twilio或阿里云）
9. **Email发送失败**: 验证SMTP配置和认证信息
10. **Webhook通知失败**: 检查目标URL的可访问性和响应格式
11. **Slack/Teams通知失败**: 验证webhook URL的有效性

### 数据库查询示例
```bash
# 连接到PostgreSQL
docker-compose exec postgres psql -U threat_user -d threat_detection

# 查看威胁评估结果
SELECT id, attack_mac, risk_score, risk_level, created_at
FROM threat_assessments
ORDER BY risk_score DESC LIMIT 10;

# 查看威胁告警统计
SELECT COUNT(*) as total_alerts FROM threat_alerts;

# 查看威胁情报
SELECT threat_type, severity, description FROM threat_intelligence LIMIT 5;
```

---

*最后更新时间：2025年10月10日*
*系统版本：v2.1*
*包含完整功能：数据摄取、流处理、威胁评估、多通道告警通知系统*
*集成测试结果：成功处理921条威胁评估记录，通知系统集成完成*</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/USAGE_GUIDE.md