# 项目规范锁定文档

**版本**: 1.0  
**生效日期**: 2025-10-15  
**状态**: 🔒 **已锁定** - 所有代码必须遵循此规范

---

## 🎯 核心原则

**本文档定义的所有命名、结构、配置为项目标准**

任何修改需要:
1. 更新此文档
2. 全项目搜索替换
3. 更新所有测试
4. 文档review

---

## 📊 Kafka主题标准 (已锁定)

### 官方主题列表

| 主题名 | 用途 | 分区数 | 副本数 | 生产者 | 消费者 | 状态 |
|--------|------|-------|--------|--------|--------|------|
| `attack-events` | 攻击事件原始流 | 1 | 1 | data-ingestion | stream-processing | 🔒 锁定 |
| `status-events` | 设备状态心跳流 | 1 | 1 | data-ingestion | stream-processing | 🔒 锁定 |
| `threat-alerts` | **威胁告警流** (统一) | 1 | 1 | stream-processing,<br>threat-assessment | alert-management | 🔒 锁定 |
| `minute-aggregations` | 分钟聚合中间流 | 1 | 1 | stream-processing | stream-processing | 🔒 锁定 |
| `device-health-alerts` | 设备健康告警流 | 1 | 1 | stream-processing | threat-assessment | 🔒 锁定 |

### ❌ 废弃的主题

| 主题名 | 原用途 | 废弃原因 | 替代方案 |
|--------|--------|---------|---------|
| `threat-events` | 风险评估事件 | 与threat-alerts功能重复 | 使用 `threat-alerts` |

### 主题命名规范

**规则**:
- 全小写
- 单词间用连字符 `-`
- 名词复数形式 (`events`, `alerts`)
- 格式: `<resource>-<type>` (如 `attack-events`)

**禁止**:
- 驼峰命名 `attackEvents` ❌
- 下划线 `attack_events` ❌
- 单数形式 `attack-event` ❌

### Kafka配置环境变量

| 环境变量名 | 默认值 | 用途 | 状态 |
|-----------|-------|------|------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 | Kafka服务器地址 | 🔒 锁定 |
| `INPUT_TOPIC` | attack-events | Flink输入主题 | 🔒 锁定 |
| `STATUS_TOPIC` | status-events | Flink状态输入 | 🔒 锁定 |
| `OUTPUT_TOPIC` | threat-alerts | Flink输出主题 | 🔒 锁定 |
| `AGGREGATION_TOPIC` | minute-aggregations | Flink聚合主题 | 🔒 锁定 |

---

## 🗄️ 数据库表标准 (已锁定)

### 持久化业务表 (6个)

**特征**: 存储运行时产生的业务数据,重启后必须保留

| 表名 | 用途 | 创建语句 | 索引数 | 状态 |
|------|------|---------|--------|------|
| `attack_events` | 攻击事件原始数据 | `CREATE TABLE IF NOT EXISTS` | 7 | 🔒 锁定 |
| `threat_alerts` | 威胁告警历史 (流处理输出) | `CREATE TABLE IF NOT EXISTS` | 9 | 🔒 锁定 |
| `threat_assessments` | 威胁评估结果 | `CREATE TABLE IF NOT EXISTS` | 6 | 🔒 锁定 |
| `device_status_history` | 设备状态心跳历史 | `CREATE TABLE IF NOT EXISTS` | 7 | 🔒 锁定 |
| `alerts` | 告警管理记录 (Alert Mgmt) | `CREATE TABLE IF NOT EXISTS` | 5 | 🔒 锁定 |
| `notifications` | 通知发送记录 | `CREATE TABLE IF NOT EXISTS` | 4 | 🔒 锁定 |

### 配置/元数据表 (2个)

**特征**: 存储配置数据,可以DROP后重建

| 表名 | 用途 | 创建语句 | 幂等性 | 状态 |
|------|------|---------|--------|------|
| `device_customer_mapping` | 设备客户映射配置 | `DROP TABLE` + `INSERT ON CONFLICT` | ✅ | 🔒 锁定 |
| `port_risk_configs` | 端口权重配置 | `DROP TABLE` + `INSERT ON CONFLICT` | ✅ | 🔒 锁定 |

### 表命名规范

**规则**:
- 全小写
- 单词间用下划线 `_`
- 名词复数形式
- 避免缩写

**示例**:
- ✅ `attack_events` (正确)
- ❌ `attackEvents` (驼峰)
- ❌ `attack_event` (单数)
- ❌ `atk_events` (缩写)

### 字段命名规范

**SQL脚本**:
```sql
-- 使用 snake_case
attack_mac VARCHAR(17)
threat_score DECIMAL(12,2)
created_at TIMESTAMP
```

**JPA Entity**:
```java
// 使用 camelCase + @Column注解
@Column(name = "attack_mac")
private String attackMac;

@Column(name = "threat_score")
private Double threatScore;

@Column(name = "created_at")
private Instant createdAt;
```

### 关键字段锁定

#### 多租户隔离字段

| 字段名 | 类型 | 约束 | 用途 |
|--------|------|------|------|
| `customer_id` | VARCHAR(100) | NOT NULL | 客户ID,所有业务表必须包含 |

#### 时间戳字段

| 字段名 | 类型 | 默认值 | 用途 |
|--------|------|--------|------|
| `created_at` | TIMESTAMP WITH TIME ZONE | CURRENT_TIMESTAMP | 记录创建时间 |
| `updated_at` | TIMESTAMP WITH TIME ZONE | CURRENT_TIMESTAMP | 记录更新时间 |

#### 蜜罐机制字段

| 字段名 | 类型 | 含义 | 用途 |
|--------|------|------|------|
| `attack_mac` | VARCHAR(17) | 被诱捕者MAC地址 | 内网失陷主机 |
| `attack_ip` | VARCHAR(45) | 被诱捕者IP | 内网地址 |
| `response_ip` | VARCHAR(45) | 诱饵IP | 不存在的虚拟哨兵 |
| `response_port` | INTEGER | 尝试访问的端口 | 暴露攻击意图 |

---

## 字段命名规范 (Field Naming Convention)

- **REST API JSON**: camelCase (e.g. `customerId`, `companyName`, `subscriptionTier`)
- **Java fields**: camelCase (Java standard, Jackson serializes as-is)
- **Python Pydantic models**: camelCase field names with `alias_generator=to_camel` where needed
- **Database columns**: snake_case (SQL standard, mapped via JPA `@Column(name="...")`)
- **Kafka messages**: camelCase JSON with `schemaVersion` field for evolution
- **Backward compatibility**: Consumer models use `@JsonAlias` / `@JsonIgnoreProperties(ignoreUnknown=true)` to accept both formats during transition
- Note: This was unified in April 2026. Previously customer-management and a few other services used snake_case.

## 🌐 REST API标准 (已锁定)

### 服务端口分配

| 服务名 | 内部端口 | 外部端口 | 状态 |
|--------|---------|---------|------|
| data-ingestion | 8080 | 8080 | 🔒 锁定 |
| stream-processing | 8081 | 8081 | 🔒 锁定 |
| alert-management | 8084 | 8082 | 🔒 锁定 |
| threat-assessment | 8083 | 8083 | 🔒 锁定 |

### API路径规范

**格式**: `/api/v{version}/{resource}/{action}`

**示例**:
```
✅ /api/v1/logs/ingest
✅ /api/v1/assessment/health
✅ /api/v1/alerts/list
✅ /api/v1/notifications/send

❌ /logs/ingest (缺少版本)
❌ /api/logs/ingest (缺少版本)
❌ /api/v1/log/ingest (单数)
```

### 关键端点

| 服务 | 端点 | 方法 | 用途 | 状态 |
|------|------|------|------|------|
| data-ingestion | `/api/v1/logs/ingest` | POST | 摄取syslog | 🔒 锁定 |
| alert-management | `/actuator/health` | GET | 健康检查 | 🔒 锁定 |
| threat-assessment | `/api/v1/assessment/health` | GET | 健康检查 | 🔒 锁定 |

---

## 📦 数据模型标准 (已锁定)

### AttackEvent (攻击事件)

**Kafka消息格式** (camelCase):
```json
{
  "id": "string",
  "customerId": "string",
  "devSerial": "string",
  "attackMac": "string",
  "attackIp": "string",
  "responseIp": "string",
  "responsePort": integer,
  "logTime": long,
  "timestamp": "ISO8601"
}
```

**数据库字段** (snake_case):
```sql
customer_id, dev_serial, attack_mac, attack_ip,
response_ip, response_port, event_timestamp, log_time
```

### ThreatAlert (威胁告警)

**Kafka消息格式** (camelCase):
```json
{
  "customerId": "string",
  "attackMac": "string",
  "threatScore": double,
  "threatLevel": "CRITICAL|HIGH|MEDIUM|LOW|INFO",
  "attackCount": integer,
  "uniqueIps": integer,
  "uniquePorts": integer,
  "uniqueDevices": integer,
  "tier": 1|2|3,
  "windowStart": "ISO8601",
  "windowEnd": "ISO8601",
  "alertTimestamp": "ISO8601"
}
```

**数据库字段** (snake_case):
```sql
customer_id, attack_mac, threat_score, threat_level,
attack_count, unique_ips, unique_ports, unique_devices,
tier, window_start, window_end, alert_timestamp
```

### DeviceStatus (设备状态)

**Kafka消息格式** (camelCase):
```json
{
  "devSerial": "string",
  "customerId": "string",
  "sentryCount": integer,
  "realHostCount": integer,
  "devStartTime": long,
  "devEndTime": long,
  "reportTime": "ISO8601"
}
```

---

## ⚙️ 配置文件标准 (已锁定)

### Spring Boot配置层级

**优先级** (从高到低):
1. 环境变量 (Docker Compose)
2. application-{profile}.properties
3. application.properties

### 必需的Profile

| Profile | 用途 | 环境 |
|---------|------|------|
| `default` | 本地开发 | application.properties |
| `docker` | Docker容器 | application-docker.properties |
| `test` | 单元测试 | application-test.properties |

### 环境变量命名规范

**规则**:
- 全大写
- 单词间用下划线
- Spring配置使用点号转下划线

**示例**:
```bash
# Spring配置
spring.kafka.bootstrap-servers
# 转换为环境变量
SPRING_KAFKA_BOOTSTRAP_SERVERS

# 自定义配置
app.kafka.topics.threat-events
# 转换为环境变量
APP_KAFKA_TOPICS_THREAT_EVENTS
```

### 关键配置项

#### Kafka配置

```properties
# 必需配置
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=${GROUP_ID:service-name-group}
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false

# 主题配置
app.kafka.topics.threat-events=${THREAT_ALERTS_TOPIC:threat-alerts}
```

#### 数据库配置

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://postgres:5432/threat_detection
spring.datasource.username=threat_user
spring.datasource.password=threat_password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate  # 生产环境必须用validate或none
spring.jpa.show-sql=false  # 生产环境必须false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

---

## 🎭 威胁评分算法标准 (已锁定)

### 核心公式

```java
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × ipWeight 
            × portWeight 
            × deviceWeight
```

### 权重计算

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 状态 |
|--------|------|------|
| 0:00-6:00 | 1.2 | 🔒 锁定 |
| 6:00-9:00 | 1.1 | 🔒 锁定 |
| 9:00-17:00 | 1.0 | 🔒 锁定 |
| 17:00-21:00 | 0.9 | 🔒 锁定 |
| 21:00-24:00 | 0.8 | 🔒 锁定 |

#### IP权重 (ipWeight)

| 唯一IP数 | 权重 | 状态 |
|---------|------|------|
| 1 | 1.0 | 🔒 锁定 |
| 2-3 | 1.3 | 🔒 锁定 |
| 4-5 | 1.5 | 🔒 锁定 |
| 6-10 | 1.7 | 🔒 锁定 |
| 10+ | 2.0 | 🔒 锁定 |

#### 端口权重 (portWeight)

| 唯一端口数 | 权重 | 状态 |
|-----------|------|------|
| 1 | 1.0 | 🔒 锁定 |
| 2-3 | 1.2 | 🔒 锁定 |
| 4-5 | 1.4 | 🔒 锁定 |
| 6-10 | 1.6 | 🔒 锁定 |
| 11-20 | 1.8 | 🔒 锁定 |
| 20+ | 2.0 | 🔒 锁定 |

#### 设备权重 (deviceWeight)

| 唯一设备数 | 权重 | 状态 |
|-----------|------|------|
| 1 | 1.0 | 🔒 锁定 |
| 2+ | 1.5 | 🔒 锁定 |

### 威胁等级划分

| 等级 | 分数范围 | 颜色 | 状态 |
|------|---------|------|------|
| INFO | < 10 | 蓝色 | 🔒 锁定 |
| LOW | 10-50 | 绿色 | 🔒 锁定 |
| MEDIUM | 50-100 | 黄色 | 🔒 锁定 |
| HIGH | 100-200 | 橙色 | 🔒 锁定 |
| CRITICAL | > 200 | 红色 | 🔒 锁定 |

---

## ⏱️ 时间窗口标准 (已锁定)

### 3层时间窗口

| 层级 | 窗口大小 | 滑动步长 | 检测目标 | 状态 |
|------|---------|---------|---------|------|
| Tier-1 | 30秒 | 30秒 | 勒索软件/快速扫描 | 🔒 锁定 |
| Tier-2 | 5分钟 | 5分钟 | 主要威胁 | 🔒 锁定 |
| Tier-3 | 15分钟 | 15分钟 | APT/持续攻击 | 🔒 锁定 |

---

## 🔧 测试标准 (已锁定)

### 测试数据位置

| 类型 | 位置 | 状态 |
|------|------|------|
| 真实日志 | `tmp/real_test_logs/*.log` | 🔒 锁定 |
| 模拟日志 | `tmp/test_logs/*.log` | 🔒 锁定 |

### 测试脚本

| 脚本 | 用途 | 位置 | 状态 |
|------|------|------|------|
| `full_integration_test.py` | 完整端到端测试 | `scripts/test/` | 🔒 锁定 |
| `e2e_mvp_test.py` | MVP功能测试 | `scripts/test/` | 🔒 锁定 |
| `check_db_init_safety.sh` | 数据库初始化安全检查 | `scripts/tools/` | 🔒 锁定 |

### 测试环境变量

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
DATA_INGESTION_URL=http://localhost:8080
DB_HOST=localhost
DB_PORT=5432
DB_NAME=threat_detection
DB_USER=threat_user
DB_PASSWORD=threat_password
```

---

## 📚 文档标准 (已锁定)

### 文档位置

| 文档类型 | 位置 | 格式 |
|---------|------|------|
| 架构文档 | `docs/` | Markdown |
| API文档 | `docs/api/` | OpenAPI/Swagger |
| 运维文档 | `docs/ops/` | Markdown |
| 代码注释 | 源码中 | JavaDoc |

### 关键文档列表

| 文档 | 用途 | 状态 |
|------|------|------|
| `README.md` | 项目概述 | 🔒 锁定 |
| `USAGE_GUIDE.md` | 使用指南 | 🔒 锁定 |
| `.github/copilot-instructions.md` | AI辅助开发规范 | 🔒 锁定 |
| `docs/PROJECT_STANDARDS.md` | 本文档 | 🔒 锁定 |

---

## ✅ 遵循规范检查清单

### 代码提交前检查

- [ ] 所有Kafka主题名称符合规范 
- [ ] 所有数据库表名使用snake_case
- [ ] 所有Java类使用camelCase
- [ ] 所有环境变量使用UPPER_SNAKE_CASE
- [ ] 所有API端点符合`/api/v1/`格式
- [ ] 所有配置项有文档说明
- [ ] 所有新增字段更新此文档

### 新功能开发检查

- [ ] 确认是否需要新增Kafka主题 (需要review)
- [ ] 确认是否需要新增数据库表 (需要更新SQL脚本)
- [ ] 确认是否需要新增API端点 (需要更新文档)
- [ ] 确认是否影响威胁评分算法 (需要严格review)

---

## 🔒 变更控制

### 锁定字段变更流程

1. **提出变更请求** (包含理由和影响分析)
2. **技术review** (架构师+2名开发)
3. **更新本文档** (标注变更日期和版本)
4. **全项目搜索替换** (使用IDE重构工具)
5. **更新所有测试**
6. **文档更新**
7. **团队通知**

### 紧急变更

**仅在生产事故时允许**,事后24小时内:
- 补充文档
- 代码review
- 添加测试

---

## 📝 版本历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| 1.0 | 2025-10-15 | 初始版本,锁定所有核心规范 | System Architect |

---

**最后更新**: 2025-10-15  
**文档所有者**: Architecture Team  
**状态**: 🔒 **强制执行**  
**违规处理**: Code Review拒绝
