# 💾 数据结构、字段定义及数据库连接

**更新日期**: 2025-10-22
**数据库**: PostgreSQL 15
**状态**: ✅ 生产就绪

---

## 🗄️ 数据库连接配置

### 开发环境 (Docker)
```properties
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/threat_detection
    username: threat_user
    password: threat_password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

### 生产环境 (Kubernetes)
```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: database-config
data:
  DB_HOST: "threat-detection-postgres"
  DB_PORT: "5432"
  DB_NAME: "threat_detection"
  DB_USERNAME: "threat_user"
  # Password stored in Secret
```

---

## 📊 核心数据表结构

### 1. 攻击事件表 (attack_events)

```sql
CREATE TABLE attack_events (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(15) NOT NULL,
    response_ip VARCHAR(15) NOT NULL,  -- 诱饵IP (关键!)
    response_port INTEGER NOT NULL,    -- 攻击意图端口
    device_serial VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    log_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_customer_timestamp (customer_id, timestamp),
    INDEX idx_attack_mac (attack_mac),
    INDEX idx_response_ip (response_ip)
);
```

**字段说明**:
- `response_ip`: **诱饵IP** (虚拟哨兵，不是真实服务器)
- `response_port`: **攻击意图** (如 3389=RDP, 445=SMB, 3306=MySQL)
- `attack_mac`: **被诱捕的内网主机MAC**
- `attack_ip`: **被诱捕的内网主机IP**

### 2. 威胁评估表 (threat_assessments)

```sql
CREATE TABLE threat_assessments (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    assessment_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_customer_mac (customer_id, attack_mac),
    INDEX idx_assessment_time (assessment_time),
    INDEX idx_threat_level (threat_level)
);
```

**威胁等级划分**:
| 等级 | 分数范围 | 说明 |
|------|---------|------|
| INFO | < 10 | 信息级别 |
| LOW | 10-50 | 低危威胁 |
| MEDIUM | 50-100 | 中危威胁 |
| HIGH | 100-200 | 高危威胁 |
| CRITICAL | > 200 | 严重威胁 |

### 3. 告警表 (alerts)

```sql
CREATE TABLE alerts (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'NEW',
    attack_mac VARCHAR(17),
    threat_score DECIMAL(10,2),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_customer_status (customer_id, status),
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at)
);
```

### 4. 客户表 (customers)

```sql
CREATE TABLE customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    subscription_tier VARCHAR(50) DEFAULT 'BASIC',
    max_devices INTEGER DEFAULT 10,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 5. 设备映射表 (device_customer_mapping)

```sql
CREATE TABLE device_customer_mapping (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    dev_serial VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(customer_id, dev_serial),
    INDEX idx_customer (customer_id),
    INDEX idx_device (dev_serial)
);
```

---

## 🔄 Kafka 消息格式

### 1. 攻击事件 (attack-events)

```json
{
  "attackMac": "00:11:22:33:44:55",
  "attackIp": "192.168.1.100",
  "responseIp": "10.0.0.1",
  "responsePort": 3306,
  "deviceSerial": "DEV-001",
  "customerId": "customer-001",
  "timestamp": "2024-01-15T10:30:00Z",
  "logTime": 1705315800
}
```

### 2. 分钟聚合 (minute-aggregations)

```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "attackCount": 150,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 3. 威胁告警 (threat-alerts)

```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 125.5,
  "threatLevel": "HIGH",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "timestamp": "2024-01-15T10:32:00Z"
}
```

---

## 🧮 威胁评分算法

### 核心公式
```java
threatScore = (attackCount × uniqueIps × uniquePorts)
            × timeWeight × ipWeight × portWeight × deviceWeight
```

### 权重计算

#### 时间权重 (timeWeight)
| 时间段 | 权重 | 说明 |
|--------|------|------|
| 0:00-6:00 | 1.2 | 深夜异常行为 |
| 6:00-9:00 | 1.1 | 早晨时段 |
| 9:00-17:00 | 1.0 | 工作时间基准 |
| 17:00-21:00 | 0.9 | 傍晚时段 |
| 21:00-24:00 | 0.8 | 夜间时段 |

#### IP权重 (ipWeight)
| 唯一IP数量 | 权重 |
|-----------|------|
| 1 | 1.0 |
| 2-3 | 1.3 |
| 4-5 | 1.5 |
| 6-10 | 1.7 |
| 10+ | 2.0 |

#### 端口权重 (portWeight)
| 唯一端口数量 | 权重 |
|-------------|------|
| 1 | 1.0 |
| 2-3 | 1.2 |
| 4-5 | 1.4 |
| 6-10 | 1.6 |
| 11-20 | 1.8 |
| 20+ | 2.0 |

#### 设备权重 (deviceWeight)
| 唯一设备数量 | 权重 |
|-------------|------|
| 1 | 1.0 |
| 2+ | 1.5 |

---

## 🏗️ 数据库初始化脚本

### 初始化顺序
1. `01-init-db.sql` - 基础表结构
2. `02-attack-events-storage.sql` - 攻击事件存储
3. `03-port-weights.sql` - 端口权重配置
4. `04-alert-management-tables.sql` - 告警管理表
5. `05-notification-config-tables.sql` - 通知配置表
6. `06-ip-segment-weights.sql` - IP段权重配置
7. `07-whitelist-config.sql` - 白名单配置
8. `08-threat-labels.sql` - 威胁标签配置

### 关键配置表
- **port_risk_configs**: 端口风险配置 (219 个端口)
- **ip_segment_weight_config**: IP段权重配置 (19 个网段)
- **threat_labels**: 威胁标签 (40 个标签)
- **whitelist_config**: IP/MAC 白名单

---

## 🔐 安全配置

### 数据库用户权限
```sql
-- 只读用户 (用于报表)
CREATE USER report_user WITH PASSWORD 'report_password';
GRANT SELECT ON ALL TABLES IN SCHEMA public TO report_user;

-- 应用用户 (读写权限)
CREATE USER threat_user WITH PASSWORD 'threat_password';
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO threat_user;
```

### 连接池配置
```properties
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
```

---

## 📊 数据流向

```
rsyslog → Data Ingestion Service → Kafka (attack-events)
                                      ↓
                            Flink Stream Processing
                                      ↓
                            Kafka (minute-aggregations)
                                      ↓
                            Flink Threat Scoring
                                      ↓
                            Kafka (threat-alerts)
                                      ↓
                    PostgreSQL ← Threat Assessment Service
                                      ↓
                    PostgreSQL ← Alert Management Service
```

---

## 🔍 数据完整性约束

### 外键约束
- `attack_events.customer_id` → `customers.customer_id`
- `threat_assessments.customer_id` → `customers.customer_id`
- `alerts.customer_id` → `customers.customer_id`
- `device_customer_mapping.customer_id` → `customers.customer_id`

### 数据验证规则
- `attack_mac`: 正则表达式 `^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$`
- `attack_ip`: 有效的 IPv4 地址
- `response_ip`: 有效的 IPv4 地址 (诱饵IP)
- `response_port`: 1-65535
- `threat_score`: >= 0

---

## 📈 性能优化

### 索引策略
- **时间索引**: 所有时间字段都有索引
- **客户索引**: 多租户查询优化
- **复合索引**: `(customer_id, timestamp)`, `(customer_id, attack_mac)`

### 分区策略
- **时间分区**: 按月分区历史数据
- **客户分区**: 大客户独立分区

### 缓存策略
- **Redis缓存**: 频繁查询的配置数据
- **应用缓存**: 威胁评分结果缓存

---

## 📋 维护命令

### 数据库连接测试
```bash
# Docker 环境
docker exec -it postgres psql -U threat_user -d threat_detection -c "SELECT version();"

# 外部连接
psql -h localhost -p 5432 -U threat_user -d threat_detection
```

### 数据清理
```sql
-- 删除旧数据 (保留最近30天)
DELETE FROM attack_events WHERE created_at < NOW() - INTERVAL '30 days';
DELETE FROM threat_assessments WHERE assessment_time < NOW() - INTERVAL '30 days';
```

### 统计查询
```sql
-- 威胁评分分布
SELECT threat_level, COUNT(*) FROM threat_assessments
GROUP BY threat_level ORDER BY threat_level;

-- 活跃客户统计
SELECT customer_id, COUNT(*) as event_count
FROM attack_events
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY customer_id ORDER BY event_count DESC;
```

---

*此文档定义了系统的数据结构、连接配置和业务规则*
