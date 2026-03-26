# 威胁检测系统数据结构文档

## 1. 数据库表结构

### 1.1 device_customer_mapping 表
**用途**: 存储设备序列号与客户ID的映射关系  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `dev_serial` (VARCHAR(50), NOT NULL, UNIQUE)
- `customer_id` (VARCHAR(100), NOT NULL)
- `created_at` (TIMESTAMP WITH TIME ZONE, DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP WITH TIME ZONE, DEFAULT CURRENT_TIMESTAMP)
- `is_active` (BOOLEAN, DEFAULT true)
- `description` (TEXT)

**索引**:
- `idx_dev_serial` on dev_serial
- `idx_customer_id` on customer_id
- `idx_active_mapping` on is_active WHERE is_active = true

### 1.2 threat_alerts 表
**用途**: 存储从流处理服务接收到的威胁警报  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `alert_id` (VARCHAR, UNIQUE)
- `attack_mac` (VARCHAR)
- `threat_score` (DOUBLE PRECISION)
- `threat_level` (VARCHAR) - 枚举值: CRITICAL, HIGH, MEDIUM, LOW, INFO
- `threat_name` (VARCHAR)
- `timestamp` (TIMESTAMP)
- `window_start` (TIMESTAMP)
- `window_end` (TIMESTAMP)
- `total_aggregations` (INTEGER)
- `processed` (BOOLEAN, DEFAULT false)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

**集合表**:
- `threat_alert_attack_patterns` (alert_id, attack_pattern)
- `threat_alert_affected_assets` (alert_id, affected_asset)

### 1.3 recommendations 表
**用途**: 存储威胁缓解建议  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `action` (VARCHAR) - 枚举值: BLOCK_IP, BLOCK_PORT, BLOCK_MAC, INCREASE_MONITORING, ISOLATE_ASSET, QUARANTINE, ALERT_SECURITY, LOG_ANALYSIS, UPDATE_SIGNATURES, RATE_LIMIT, MONITOR
- `priority` (VARCHAR) - 枚举值: CRITICAL, HIGH, MEDIUM, LOW, INFO
- `description` (TEXT)
- `executed` (BOOLEAN, DEFAULT false)
- `execution_timestamp` (TIMESTAMP)

**集合表**:
- `recommendation_parameters` (recommendation_id, param_key, param_value)

### 1.4 threat_assessments 表
**用途**: 存储完整的威胁风险评估结果  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `assessment_id` (VARCHAR, UNIQUE)
- `alert_id` (VARCHAR)
- `risk_level` (VARCHAR) - 枚举值: CRITICAL, HIGH, MEDIUM, LOW, INFO
- `risk_score` (DOUBLE PRECISION, 0.0-10000.0)
- `confidence` (DOUBLE PRECISION, 0.0-1.0)
- `assessment_timestamp` (TIMESTAMP)
- `processing_duration_ms` (BIGINT)

**关联表**:
- 关联 recommendations 表 (assessment_id)

### 1.5 alerts 表
**用途**: 存储告警管理系统的告警信息  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `title` (VARCHAR(500))
- `description` (VARCHAR(2000))
- `status` (VARCHAR) - 枚举值: NEW, DEDUPLICATED, ENRICHED, NOTIFIED, ESCALATED, RESOLVED, ARCHIVED
- `severity` (VARCHAR) - 枚举值: CRITICAL, HIGH, MEDIUM, LOW, INFO
- `source` (VARCHAR(100), DEFAULT 'threat-detection-system')
- `event_type` (VARCHAR(100))
- `metadata` (TEXT)
- `attack_mac` (VARCHAR(17))
- `threat_score` (DOUBLE PRECISION)
- `assigned_to` (VARCHAR(100))
- `resolution` (VARCHAR(1000))
- `resolved_by` (VARCHAR(100))
- `resolved_at` (TIMESTAMP)
- `last_notified_at` (TIMESTAMP)
- `escalation_level` (INTEGER, DEFAULT 0)
- `escalation_reason` (VARCHAR)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

**索引**:
- `idx_alert_status` on status
- `idx_alert_severity` on severity
- `idx_alert_attack_mac` on attack_mac
- `idx_alert_created_at` on created_at
- `idx_alert_source` on source

**集合表**:
- `alert_affected_assets` (alert_id, asset)
- `alert_recommendations` (alert_id, recommendation)

### 1.6 notifications 表
**用途**: 存储告警通知记录  
**位置**: PostgreSQL数据库  
**字段**:
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `alert_id` (BIGINT, FOREIGN KEY -> alerts.id)
- `channel` (VARCHAR) - 枚举值: EMAIL, SMS, WEBHOOK, SLACK, TEAMS
- `recipient` (VARCHAR)
- `subject` (VARCHAR(1000))
- `content` (TEXT)
- `status` (VARCHAR) - 枚举值: PENDING, SENT, FAILED, RETRYING
- `error_message` (VARCHAR(500))
- `retry_count` (INTEGER, DEFAULT 0)
- `max_retries` (INTEGER, DEFAULT 3)
- `sent_at` (TIMESTAMP)
- `created_at` (TIMESTAMP)

**索引**:
- `idx_notification_alert_id` on alert_id
- `idx_notification_channel` on channel
- `idx_notification_status` on status
- `idx_notification_created_at` on created_at

## 2. Kafka消息结构

### 2.1 attack-events 主题
**消息类型**: AttackEvent  
**用途**: 原始攻击事件数据  
**字段**:
- `id` (String) - 复合ID: devSerial_logTime_lineId
- `devSerial` (String) - 设备序列号
- `logType` (int) - 日志类型
- `subType` (int) - 子类型
- `attackMac` (String) - 攻击MAC地址
- `attackIp` (String) - 攻击IP地址
- `responseIp` (String) - 响应IP地址
- `responsePort` (int) - 响应端口
- `lineId` (int) - 行ID
- `ifaceType` (int) - 接口类型
- `vlanId` (int) - VLAN ID
- `logTime` (long) - 日志时间戳
- `ethType` (int) - 以太网类型
- `ipType` (int) - IP类型
- `severity` (String) - 严重程度
- `description` (String) - 描述
- `rawLog` (String) - 原始日志
- `customerId` (String) - 客户ID
- `timestamp` (LocalDateTime) - 处理时间戳
**示例**:
```json
{"host":{"ip":"122.228.50.86","hostname":"jzzn-R86S"},"log":{"syslog":{"priority":174,"severity":{"code":6,"name":"Informational"},"facility":{"code":21,"name":"local5"}}},"type":"new","message":"syslog_version=1.10.0,dev_serial=caa0beea29676c6d,log_type=1,sub_type =1,attack_mac=f0:2f:74:b2:9f:5e,attack_ip=10.68.5.141,response_ip=10.68.5.240,response_port=515,line_id=1,Iface_type=1,Vlan_id=30,log_time=1747274577,eth_type =2048,ip_type = 6\n","process":{"name":"sniff"},"@version":"1","service":{"type":"system"},"@timestamp":"2025-05-15T02:02:57Z","event":{"original":"<174>May 15 10:02:57 jzzn-R86S sniff: syslog_version=1.10.0,dev_serial=caa0beea29676c6d,log_type=1,sub_type =1,attack_mac=f0:2f:74:b2:9f:5e,attack_ip=10.68.5.141,response_ip=10.68.5.240,response_port=515,line_id=1,Iface_type=1,Vlan_id=30,log_time=1747274577,eth_type =2048,ip_type = 6\n"}}
```

### 2.2 status-events 主题
**消息类型**: StatusEvent  
**用途**: 设备状态事件  
**字段**:
- `id` (String) - 复合ID: devSerial_devStartTime_devEndTime
- `devSerial` (String) - 设备序列号
- `logType` (int) - 日志类型
- `sentryCount` (int) - 哨兵数量
- `realHostCount` (int) - 真实主机数量
- `devStartTime` (long) - 设备启动时间
- `devEndTime` (long) - 设备结束时间
- `time` (String) - 时间字符串
- `status` (String) - 状态
- `description` (String) - 描述
- `rawLog` (String) - 原始日志
- `timestamp` (LocalDateTime) - 处理时间戳
**示例**:
```json
{"host":{"ip":"218.22.66.34","hostname":"jzzn-R86S"},"log":{"syslog":{"priority":174,"severity":{"code":6,"name":"Informational"},"facility":{"code":21,"name":"local5"}}},"type":"new","message":"syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6723,real_host_count=656,dev_start_time=1747274547,dev_end_time=1778688000,time=2025-05-15 10:02:57\n","process":{"name":"sniff"},"@version":"1","service":{"type":"system"},"@timestamp":"2025-05-15T02:02:57Z","event":{"original":"<174>May 15 10:02:57 jzzn-R86S sniff: syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=6723,real_host_count=656,dev_start_time=1747274547,dev_end_time=1778688000,time=2025-05-15 10:02:57\n"}}
```

### 2.3 threat-alerts 主题
**消息类型**: ThreatAlertMessage  
**用途**: 威胁警报消息  
**字段**:
- `alertId` (String) - 警报ID
- `attackMac` (String) - 攻击MAC地址
- `threatScore` (double) - 威胁分数
- `threatLevel` (String) - 威胁等级
- `threatName` (String) - 威胁名称
- `timestamp` (Long) - 时间戳
- `windowStart` (Long) - 窗口开始时间
- `windowEnd` (Long) - 窗口结束时间
- `totalAggregations` (int) - 总聚合数



### 2.4 minute-aggregations 主题
**消息类型**: String (JSON格式)  
**用途**: 分钟级聚合数据（中间主题）  
**格式**: `{"customerId:attackMac": count, ...}`  
**键格式**: customerId:attackMac  
**值**: 该时间窗口内该客户-攻击源的攻击事件数量

> **⚠️ 状态说明 (2026-03-26)**:
> 此主题由 stream-processing (Flink) 的 `StreamProcessingJob` 生产（参见 `StreamProcessingJob.java` line 90），
> 但**当前没有消费者**。架构文档中描述 threat-assessment 为预期消费者，但该消费者尚未实现。
>
> **当前行为**: 数据持续写入 Kafka 但不被读取。Kafka 默认保留策略（7天/1GB）会自动清理过期数据，不会无限增长。
>
> **路线图**: 当 threat-assessment 需要按分钟回溯攻击趋势时，将实现消费者。
> 此主题也可作为未来 ClickHouse 时序分析的数据源（参见 `docs/design/adr/001-postgresql-over-clickhouse.md`）。

## 3. Java枚举和嵌入类

### 3.1 枚举类

#### RiskLevel
- `CRITICAL` - 严重威胁，需要立即行动
- `HIGH` - 高风险威胁
- `MEDIUM` - 中风险威胁
- `LOW` - 低风险威胁
- `INFO` - 信息级别

#### MitigationAction
- `BLOCK_IP` - 阻止源IP地址
- `BLOCK_PORT` - 阻止特定端口
- `BLOCK_MAC` - 阻止源MAC地址
- `INCREASE_MONITORING` - 增加受影响资产的监控
- `ISOLATE_ASSET` - 将受影响资产隔离到网络
- `QUARANTINE` - 隔离可疑流量
- `ALERT_SECURITY` - 警报安全团队
- `LOG_ANALYSIS` - 执行详细日志分析
- `UPDATE_SIGNATURES` - 更新威胁签名
- `RATE_LIMIT` - 应用速率限制
- `MONITOR` - 密切监控威胁

#### Priority
- `CRITICAL` - 立即执行
- `HIGH` - 1小时内执行
- `MEDIUM` - 24小时内执行
- `LOW` - 方便时执行
- `INFO` - 仅监控

#### AlertStatus
- `NEW` - 新建
- `DEDUPLICATED` - 去重
- `ENRICHED` - 已丰富
- `NOTIFIED` - 已通知
- `ESCALATED` - 已升级
- `RESOLVED` - 已解决
- `ARCHIVED` - 已归档

#### AlertSeverity
- `CRITICAL` (阈值: 1000) - 严重
- `HIGH` (阈值: 500) - 高危
- `MEDIUM` (阈值: 100) - 中危
- `LOW` (阈值: 10) - 低危
- `INFO` (阈值: 0) - 信息

#### NotificationChannel
- `EMAIL` - 邮件
- `SMS` - 短信
- `WEBHOOK` - Webhook
- `SLACK` - Slack
- `TEAMS` - Teams

#### NotificationStatus
- `PENDING` - 待发送
- `SENT` - 已发送
- `FAILED` - 发送失败
- `RETRYING` - 重试中

### 3.2 嵌入类

#### ThreatIntelligence
**用途**: 威胁情报信息，嵌入在ThreatAssessment中  
**字段**:
- `knownAttacker` (boolean) - 是否为已知攻击者
- `campaignId` (String) - 活动ID
- `similarIncidents` (int) - 类似事件数量
- `threatActor` (String) - 威胁行为者
- `malwareFamily` (String) - 恶意软件家族
- `cveReferences` (String) - CVE引用

## 4. 数据流向总结

1. **数据摄取**: 日志文件 → AttackEvent/StatusEvent → Kafka (attack-events/status-events)
2. **流处理**: Kafka消费 → Flink聚合 → ThreatAlertMessage → Kafka (threat-alerts)
3. **威胁评估**: Kafka消费 → ThreatAlert/ThreatAssessment/Recommendation → PostgreSQL
4. **告警管理**: 威胁评估结果 → Alert/Notification → PostgreSQL + 多渠道通知

这个系统的数据结构设计支持了从原始日志摄取到威胁检测、评估和告警通知的完整流程。

---
*文档生成时间: 2025年10月10日*
*系统版本: 威胁检测系统 v1.0*