# IP网段权重系统 V4.0 设计文档 - 双维度权重模型

**版本**: 4.0  
**日期**: 2025-10-24  
**状态**: 设计中  
**关键修正**: 增加蜜罐敏感度权重，修正V3.0的设计缺陷

---

## ⚠️ V3.0的致命缺陷

### 问题场景

**V3.0设计**:
```
威胁评分 = 基础分 × attackSourceWeight (单一维度)
```

**缺陷示例**:
```
IoT设备 (192.168.50.10, 摄像头) 扫描管理区诱饵 (10.0.100.99, 模拟跳板机)

V3.0评分:
  attackSourceWeight = 0.9 (IoT设备本身价值低)
  threatScore = 基础分 × 0.9 = 低威胁 ❌ 错误！

实际风险:
  攻击者劫持IoT设备后，尝试访问管理区诱饵
  → 意图明确：想要控制全网！
  → 应该是 CRITICAL 威胁 ✅
```

**根本问题**: 只考虑"失陷设备的价值"，忽略了"攻击者的意图"

### 蜜罐的核心价值

**关键理解**: 诱饵的**部署位置/敏感度**反映了攻击者的**意图和目标**

| 诱饵类型 | 攻击意图 | 风险等级 |
|---------|---------|---------|
| 管理区诱饵（跳板机/堡垒机） | 尝试控制全网 | CRITICAL |
| 数据库区诱饵（模拟DB服务器） | 尝试窃取数据 | CRITICAL |
| 核心业务服务器诱饵 | 尝试破坏业务 | HIGH |
| 文件服务器诱饵 | 勒索软件目标 | HIGH |
| 办公区诱饵 | 横向移动探测 | MEDIUM |

**结论**: 无论失陷设备是什么（IoT/办公/服务器），只要它尝试访问高敏感度的诱饵，就是高风险行为！

---

## 🎯 V4.0 核心设计

### 1. 双维度权重模型

**完整公式**:
```java
threatScore = (probeCount × honeypotsAccessed × targetPortsDiversity)
            × timeWeight
            × attackSourceWeight         // 维度1: 失陷设备的风险
            × honeypotSensitivityWeight  // 维度2: 攻击者的意图
            × attackIntentWeight         // 基于端口
            × persistenceWeight          // 基于频率
            × deviceCoverageWeight       // 基于设备数
```

**关键变化**:
- ✅ 保留 `attackSourceWeight` (V3.0) - 失陷设备被攻陷的后果有多严重？
- ✅ 新增 `honeypotSensitivityWeight` (V4.0) - 攻击者尝试访问这个诱饵，意图有多严重？
- ✅ 两者相乘 = 综合风险评估

### 2. 维度1: 攻击源权重

**含义**: 该设备被攻陷的后果有多严重？

| 设备网段类型 | 权重 | 风险等级 | 理由 |
|------------|------|---------|------|
| **数据库服务器区** | 3.0 | CRITICAL | 攻陷后可直接访问数据 |
| **管理网段** | 3.0 | CRITICAL | 攻陷后可控制全网 |
| **核心业务服务器区** | 2.8 | CRITICAL | 攻陷后影响业务连续性 |
| **应用服务器区** | 2.5 | CRITICAL | 攻陷后破坏业务逻辑 |
| **文件服务器区** | 2.2 | HIGH | 勒索软件主要目标 |
| **Web服务器/DMZ** | 2.0 | HIGH | 对外暴露，易被攻破 |
| **高管/财务办公区** | 1.8 | HIGH | APT高价值目标 |
| **开发人员办公区** | 1.2 | MEDIUM | 有代码和系统权限 |
| **普通办公区** | 1.0 | LOW | 基准权重 |
| **IoT设备区** | 0.9 | LOW | 本身价值低，权限有限 |
| **访客网络** | 0.6 | VERY_LOW | 物理隔离，危害有限 |

**配置表**: `attack_source_weights`

### 3. 维度2: 蜜罐敏感度权重

**含义**: 攻击者尝试访问这个诱饵，意图有多严重？

| 诱饵部署位置 | 权重 | 风险等级 | 理由 |
|------------|------|---------|------|
| **管理区诱饵** | **3.5** | CRITICAL | 尝试控制全网（跳板机/堡垒机） |
| **数据库区诱饵** | **3.5** | CRITICAL | 尝试窃取数据（数据库服务器） |
| **核心业务服务器诱饵** | 3.0 | HIGH | 尝试破坏业务 |
| **文件服务器诱饵** | 2.5 | HIGH | 勒索软件目标 |
| **应用服务器诱饵** | 2.0 | HIGH | 业务系统探测 |
| **Web服务器/DMZ诱饵** | 1.8 | MEDIUM | 半信任区探测 |
| **办公区诱饵** | 1.5 | MEDIUM | 横向移动探测 |
| **低价值诱饵** | 1.0 | LOW | 基准权重 |

**配置表**: `honeypot_sensitivity_weights`

### 4. 综合权重计算

**Java伪代码**:
```java
// 1. 查询失陷设备的风险权重
double attackSourceWeight = attackSourceWeightsRepo
    .findWeightByCustomerIdAndIp(customerId, attackIp)
    .orElse(1.0);  // 默认基准权重

// 2. 查询诱饵的敏感度权重
double honeypotSensitivityWeight = honeypotSensitivityWeightsRepo
    .findWeightByCustomerIdAndIp(customerId, honeypotIp)  // responseIp
    .orElse(1.0);  // 默认基准权重

// 3. 综合权重
double combinedWeight = attackSourceWeight × honeypotSensitivityWeight;

// 4. 最终评分
threatScore = baseScore × timeWeight × combinedWeight 
            × attackIntentWeight × persistenceWeight;
```

---

## 🔧 数据库表结构

### 表1: attack_source_weights (攻击源权重)

```sql
CREATE TABLE IF NOT EXISTS attack_source_weights (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 网段基本信息
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 失陷设备风险评估
    device_type VARCHAR(50) NOT NULL,    -- OFFICE, SERVER, DMZ, MANAGEMENT, IOT, GUEST
    risk_level VARCHAR(20) NOT NULL,     -- VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    
    -- 元数据
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_attack_source_segment UNIQUE (customer_id, segment_name)
);

CREATE INDEX IF NOT EXISTS idx_attack_source_customer_active 
    ON attack_source_weights(customer_id, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_attack_source_ip_range 
    ON attack_source_weights(customer_id, ip_range_start, ip_range_end);

COMMENT ON TABLE attack_source_weights IS '攻击源网段权重配置 - 评估失陷设备被攻陷的后果严重性';
COMMENT ON COLUMN attack_source_weights.weight IS '失陷设备权重（0.5-3.0），反映该设备被攻陷后的危害程度';
```

### 表2: honeypot_sensitivity_weights (蜜罐敏感度权重)

```sql
CREATE TABLE IF NOT EXISTS honeypot_sensitivity_weights (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 诱饵基本信息
    honeypot_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 诱饵敏感度评估
    honeypot_tier VARCHAR(50) NOT NULL,      -- MANAGEMENT, DATABASE, CORE_SERVER, FILE_SERVER, WEB_SERVER, OFFICE, LOW_VALUE
    sensitivity_level VARCHAR(20) NOT NULL,   -- VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    
    -- 诱饵元数据
    simulated_service VARCHAR(100),  -- 模拟的服务类型（如"Bastion Host", "Database Server", "File Share"）
    deployment_zone VARCHAR(50),     -- 部署区域（如"Management Zone", "Database Zone"）
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_honeypot_sensitivity_name UNIQUE (customer_id, honeypot_name)
);

CREATE INDEX IF NOT EXISTS idx_honeypot_sensitivity_customer_active 
    ON honeypot_sensitivity_weights(customer_id, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_honeypot_sensitivity_ip_range 
    ON honeypot_sensitivity_weights(customer_id, ip_range_start, ip_range_end);

COMMENT ON TABLE honeypot_sensitivity_weights IS '蜜罐敏感度权重配置 - 评估攻击者访问该诱饵的意图严重性';
COMMENT ON COLUMN honeypot_sensitivity_weights.weight IS '蜜罐敏感度权重（1.0-3.5），反映攻击者访问该诱饵的意图严重性';
COMMENT ON COLUMN honeypot_sensitivity_weights.simulated_service IS '诱饵模拟的服务类型，帮助理解攻击意图';
```

---

## 📊 默认配置示例

### 攻击源权重配置 (attack_source_weights)

```sql
-- 高危失陷设备
INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    device_type, risk_level, weight, description, priority
) VALUES
    ('default', 'DB-Server-Zone', '10.0.3.0', '10.0.3.255',
     'SERVER', 'CRITICAL', 3.00, '数据库服务器区 - 攻陷后可直接访问数据', 100),
    
    ('default', 'Management-Zone', '10.0.100.0', '10.0.100.255',
     'MANAGEMENT', 'CRITICAL', 3.00, '运维管理网段 - 攻陷后可控制全网', 100),
    
    ('default', 'Core-App-Server-Zone', '10.0.2.0', '10.0.2.255',
     'SERVER', 'CRITICAL', 2.80, '核心业务服务器区 - 攻陷后影响业务连续性', 95),
    
    ('default', 'App-Server-Zone', '10.0.1.0', '10.0.1.255',
     'SERVER', 'CRITICAL', 2.50, '应用服务器区 - 攻陷后破坏业务逻辑', 90),
    
    ('default', 'File-Server-Zone', '10.0.4.0', '10.0.4.255',
     'SERVER', 'HIGH', 2.20, '文件服务器区 - 勒索软件主要目标', 85);

-- 中危失陷设备
INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    device_type, risk_level, weight, description, priority
) VALUES
    ('default', 'Web-DMZ-Zone', '172.16.1.0', '172.16.1.255',
     'DMZ', 'HIGH', 2.00, 'Web服务器/DMZ - 对外暴露，易被攻破', 80),
    
    ('default', 'Executive-Office', '192.168.20.0', '192.168.20.255',
     'OFFICE', 'HIGH', 1.80, '高管/财务办公区 - APT高价值目标', 70),
    
    ('default', 'Dev-Office', '192.168.30.0', '192.168.30.255',
     'OFFICE', 'MEDIUM', 1.20, '开发人员办公区 - 有代码和系统权限', 60);

-- 低危失陷设备
INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    device_type, risk_level, weight, description, priority
) VALUES
    ('default', 'General-Office', '192.168.10.0', '192.168.10.255',
     'OFFICE', 'LOW', 1.00, '普通办公区 - 基准权重', 50),
    
    ('default', 'IoT-Devices', '192.168.50.0', '192.168.50.255',
     'IOT', 'LOW', 0.90, 'IoT设备区 - 本身价值低，权限有限', 40),
    
    ('default', 'Guest-Network', '192.168.100.0', '192.168.100.255',
     'GUEST', 'VERY_LOW', 0.60, '访客网络 - 物理隔离，危害有限', 30);
```

### 蜜罐敏感度权重配置 (honeypot_sensitivity_weights)

```sql
-- 高敏感度诱饵
INSERT INTO honeypot_sensitivity_weights (
    customer_id, honeypot_name, ip_range_start, ip_range_end,
    honeypot_tier, sensitivity_level, weight,
    simulated_service, deployment_zone, description, priority
) VALUES
    ('default', 'Management-Honeypot-Bastion', '10.0.100.98', '10.0.100.99',
     'MANAGEMENT', 'CRITICAL', 3.50,
     'Bastion Host / Jump Server', 'Management Zone',
     '管理区诱饵 - 模拟跳板机，攻击者尝试控制全网', 100),
    
    ('default', 'Database-Honeypot-Primary', '10.0.3.98', '10.0.3.99',
     'DATABASE', 'CRITICAL', 3.50,
     'Database Server (MySQL/PostgreSQL)', 'Database Zone',
     '数据库区诱饵 - 模拟数据库服务器，攻击者尝试窃取数据', 100),
    
    ('default', 'Core-App-Honeypot', '10.0.2.98', '10.0.2.99',
     'CORE_SERVER', 'HIGH', 3.00,
     'Core Business Application Server', 'Core Server Zone',
     '核心业务服务器诱饵 - 攻击者尝试破坏业务', 90);

-- 中等敏感度诱饵
INSERT INTO honeypot_sensitivity_weights (
    customer_id, honeypot_name, ip_range_start, ip_range_end,
    honeypot_tier, sensitivity_level, weight,
    simulated_service, deployment_zone, description, priority
) VALUES
    ('default', 'FileServer-Honeypot', '10.0.4.98', '10.0.4.99',
     'FILE_SERVER', 'HIGH', 2.50,
     'File Share Server (SMB/NFS)', 'File Server Zone',
     '文件服务器诱饵 - 勒索软件主要目标', 85),
    
    ('default', 'App-Server-Honeypot', '10.0.1.98', '10.0.1.99',
     'WEB_SERVER', 'MEDIUM', 2.00,
     'Application Server', 'App Server Zone',
     '应用服务器诱饵 - 业务系统探测', 80),
    
    ('default', 'DMZ-Web-Honeypot', '172.16.1.98', '172.16.1.99',
     'WEB_SERVER', 'MEDIUM', 1.80,
     'Web Server', 'DMZ Zone',
     'DMZ Web服务器诱饵 - 半信任区探测', 70);

-- 低敏感度诱饵
INSERT INTO honeypot_sensitivity_weights (
    customer_id, honeypot_name, ip_range_start, ip_range_end,
    honeypot_tier, sensitivity_level, weight,
    simulated_service, deployment_zone, description, priority
) VALUES
    ('default', 'Office-Honeypot-General', '192.168.10.98', '192.168.10.99',
     'OFFICE', 'MEDIUM', 1.50,
     'Office Workstation', 'Office Zone',
     '办公区诱饵 - 横向移动探测', 60),
    
    ('default', 'Low-Value-Honeypot', '192.168.200.98', '192.168.200.99',
     'LOW_VALUE', 'LOW', 1.00,
     'Generic Low-Value Target', 'Isolated Zone',
     '低价值诱饵 - 基准权重', 50);
```

---

## 📈 评分示例对比

### 场景1: IoT设备扫描管理区诱饵 ⚠️⚠️⚠️

**攻击行为**:
- 失陷设备: 192.168.50.10 (IoT摄像头)
- 访问诱饵: 10.0.100.99 (管理区诱饵 - 模拟跳板机)
- 目标端口: SSH 22, RDP 3389
- 探测次数: 50次 (2分钟)
- 时间: 凌晨3:00

#### V3.0 评分（错误）

```
attackSourceWeight = 0.9 (IoT设备本身价值低)

baseScore = 50 × 1 × 2 = 100
threatScore = 100 × 1.5 (时间) × 0.9 (攻击源) × 3.0 (意图) × 1.5 (持久性)
            = 100 × 6.075 = 607.5

威胁等级: LOW ❌ 错误！
```

#### V4.0 评分（正确）

```
attackSourceWeight = 0.9 (IoT设备本身价值低)
honeypotSensitivityWeight = 3.5 (管理区诱饵 - 尝试控制全网！)

baseScore = 50 × 1 × 2 = 100
threatScore = 100 × 1.5 (时间) × 0.9 (攻击源) × 3.5 (诱饵敏感度) × 3.0 (意图) × 1.5 (持久性)
            = 100 × 1.5 × 3.15 × 3.0 × 1.5
            = 100 × 21.26 = 2,126

威胁等级: HIGH → CRITICAL ✅ 正确！
```

**分析**: 
- ✅ IoT设备被劫持后尝试攻击管理区 = 意图明确（尝试控制全网）
- ✅ 评分提高 3.5倍（607.5 → 2126）
- ✅ 威胁等级准确（LOW → CRITICAL）

---

### 场景2: 数据库服务器扫描办公区诱饵

**攻击行为**:
- 失陷设备: 10.0.3.50 (数据库服务器)
- 访问诱饵: 192.168.10.99 (办公区诱饵)
- 目标端口: SMB 445
- 探测次数: 30次

#### V3.0 评分

```
attackSourceWeight = 3.0 (数据库服务器)

baseScore = 30 × 1 × 1 = 30
threatScore = 30 × 1.0 × 3.0 × 3.0 × 1.2 = 324

威胁等级: MEDIUM
```

#### V4.0 评分

```
attackSourceWeight = 3.0 (数据库服务器被攻陷 - 严重)
honeypotSensitivityWeight = 1.5 (办公区诱饵 - 横向移动探测)

baseScore = 30 × 1 × 1 = 30
threatScore = 30 × 1.0 × 3.0 × 1.5 × 3.0 × 1.2
            = 30 × 16.2 = 486

威胁等级: MEDIUM → HIGH ✅
```

**分析**: 
- ✅ 数据库服务器被攻陷 (3.0) + 横向移动 (1.5) = 双重风险
- ✅ 评分提高 1.5倍（324 → 486）
- ✅ 威胁等级升级（MEDIUM → HIGH）

---

### 场景3: 办公区扫描办公区诱饵

**攻击行为**:
- 失陷设备: 192.168.10.50 (普通办公电脑)
- 访问诱饵: 192.168.10.99 (同区域办公区诱饵)
- 目标端口: HTTP 80
- 探测次数: 5次

#### V4.0 评分

```
attackSourceWeight = 1.0 (普通办公区)
honeypotSensitivityWeight = 1.5 (办公区诱饵)

baseScore = 5 × 1 × 1 = 5
threatScore = 5 × 1.0 × 1.0 × 1.5 × 1.5 × 1.0
            = 5 × 2.25 = 11.25

威胁等级: LOW ✅
```

**分析**: 
- ✅ 钓鱼邮件攻陷后的零星探测
- ✅ 评分合理（低威胁）
- ✅ 符合预期

---

### 场景4: 访客网络扫描数据库诱饵 ⚠️

**攻击行为**:
- 失陷设备: 192.168.100.20 (访客WiFi设备)
- 访问诱饵: 10.0.3.99 (数据库区诱饵 - 模拟DB服务器)
- 目标端口: MySQL 3306, PostgreSQL 5432
- 探测次数: 100次

#### V4.0 评分

```
attackSourceWeight = 0.6 (访客网络 - 本应隔离)
honeypotSensitivityWeight = 3.5 (数据库诱饵 - 尝试窃取数据！)

baseScore = 100 × 1 × 2 = 200
threatScore = 200 × 1.0 × 0.6 × 3.5 × 2.5 × 1.8
            = 200 × 9.45 = 1,890

威胁等级: CRITICAL ✅
```

**分析**: 
- ✅ 访客网络不应该能访问数据库区诱饵（网络隔离失败）
- ✅ 攻击意图明确（尝试窃取数据）
- ✅ 评分准确反映风险（CRITICAL）

---

## 🔄 V3.0 → V4.0 迁移

### 核心变化

| 维度 | V3.0 | V4.0 | 说明 |
|------|------|------|------|
| **权重维度** | 单一（攻击源） | 双重（攻击源 + 诱饵敏感度） | 新增蜜罐敏感度 |
| **表数量** | 1张表 | 2张表 | 分离关注点 |
| **评分公式** | `baseScore × attackSourceWeight` | `baseScore × attackSourceWeight × honeypotSensitivityWeight` | 两者相乘 |
| **配置复杂度** | 低 | 中等 | 需要配置两组权重 |

### 迁移步骤

#### 1. 备份V3.0配置（如果已部署）

```sql
CREATE TABLE ip_segment_weight_config_v3_backup AS 
SELECT * FROM ip_segment_weight_config;
```

#### 2. 执行V4.0初始化脚本

```bash
psql -h localhost -U threat_user -d threat_detection \
  -f docker/12-ip-segment-weights-v4.sql
```

#### 3. 迁移V3.0配置到V4.0

**V3.0的 `ip_segment_weight_config` → V4.0的 `attack_source_weights`**

```sql
INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    device_type, risk_level, weight, description, priority
)
SELECT 
    customer_id,
    segment_name,
    ip_range_start,
    ip_range_end,
    segment_type,  -- V3.0: segment_type → V4.0: device_type
    risk_level,
    weight,
    description,
    priority
FROM ip_segment_weight_config_v3_backup
WHERE customer_id != 'default' AND is_active = TRUE
ON CONFLICT (customer_id, segment_name) DO NOTHING;
```

**蜜罐敏感度配置需要手动创建**（V3.0没有此概念）

#### 4. 验证迁移结果

```sql
-- 检查攻击源配置
SELECT COUNT(*) FROM attack_source_weights WHERE customer_id != 'default';

-- 检查蜜罐敏感度配置
SELECT COUNT(*) FROM honeypot_sensitivity_weights WHERE customer_id != 'default';

-- 测试查询
SELECT get_attack_source_weight('default', '192.168.10.50');
SELECT get_honeypot_sensitivity_weight('default', '10.0.100.99');
```

---

## ✅ 优势总结

### V4.0 相比 V3.0 的改进

1. **✅ 修正致命缺陷**
   - V3.0: IoT设备扫描管理区诱饵 = 低威胁 ❌
   - V4.0: IoT设备扫描管理区诱饵 = CRITICAL威胁 ✅

2. **✅ 准确反映攻击意图**
   - 诱饵敏感度权重 = 攻击者的目标和意图
   - 管理区诱饵 (3.5) > 数据库诱饵 (3.5) > 办公区诱饵 (1.5)

3. **✅ 双重风险评估**
   - 失陷设备风险 × 攻击意图 = 综合威胁
   - 数据库服务器 (3.0) × 横向移动 (1.5) = 4.5倍威胁放大

4. **✅ 配置灵活性**
   - 两张表独立配置
   - 攻击源权重 ← 客户网络拓扑
   - 蜜罐敏感度 ← 诱饵部署策略

5. **✅ 向后兼容**
   - 无配置时默认权重1.0
   - 逐步迁移，不影响现有功能

---

## 📋 实施检查清单

### 阶段1: 数据库表创建
- [ ] 创建 `attack_source_weights` 表
- [ ] 创建 `honeypot_sensitivity_weights` 表
- [ ] 插入默认配置（12条攻击源 + 8条蜜罐敏感度）
- [ ] 验证查询函数 `get_attack_source_weight()` 和 `get_honeypot_sensitivity_weight()`

### 阶段2: 服务层实现
- [ ] 实现 `IpSegmentWeightService.getAttackSourceWeight(customerId, attackIp)`
- [ ] 实现 `IpSegmentWeightService.getHoneypotSensitivityWeight(customerId, honeypotIp)`
- [ ] 修改 `ThreatScoreCalculator.calculateThreatScore()` 集成双维度权重

### 阶段3: API开发
- [ ] CRUD API: `/api/v1/assessment/attack-source-weights`
- [ ] CRUD API: `/api/v1/assessment/honeypot-sensitivity-weights`
- [ ] 批量导入/导出功能
- [ ] 筛选查询（按device_type/honeypot_tier）

### 阶段4: 测试验证
- [ ] 单元测试: 双维度权重计算
- [ ] 集成测试: IoT→管理区诱饵（HIGH→CRITICAL）
- [ ] 集成测试: 数据库→办公区诱饵（双重风险）
- [ ] 端到端测试: 完整流程验证

### 阶段5: 文档完善
- [ ] API文档（Swagger）
- [ ] 客户配置指南（如何设置两组权重）
- [ ] 迁移指南（V3.0→V4.0）

---

**作者**: ThreatDetection Team  
**审核**: 待审核  
**批准**: 待批准  

**关键**: V4.0通过增加蜜罐敏感度权重，修正了V3.0只考虑失陷设备风险而忽略攻击意图的致命缺陷。双维度权重模型准确反映了蜜罐系统的核心价值：通过诱饵的部署位置推断攻击者的意图和目标。
