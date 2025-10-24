# IP网段权重系统 V3.0 设计文档 - 基于蜜罐机制的正确理解

**版本**: 3.0  
**日期**: 2025-10-24  
**状态**: 设计中  
**重大修正**: 基于对蜜罐机制的正确理解重新设计

---

## ⚠️ 核心理解修正

### V2.0的根本错误

**错误假设**: 
- ❌ 认为系统同时获取"攻击源流量"和"真实服务器流量"
- ❌ 设计了"攻击源网段权重" × "目标网段权重" × "方向性权重"
- ❌ 分类了"办公区"、"服务器区"、"DMZ"等真实网络区域

**实际情况**:
- ✅ 系统只从**蜜罐设备**获取流量
- ✅ **所有 `response_ip` 都是诱饵IP**（不存在的虚拟哨兵）
- ✅ 真实服务器的流量**未被采集**（未来可能加入）

### 正确的数据字段理解

| 字段 | 真实含义 | V2.0错误理解 | 影响 |
|------|---------|-------------|------|
| `attack_ip` | 被诱捕的内网主机IP（失陷主机） | 外部攻击者IP | 中等 |
| `response_ip` | **诱饵IP（不存在的虚拟哨兵）** | ❌ 被攻击的真实服务器IP | **致命** |
| `response_port` | 攻击者尝试的端口（暴露意图） | 被攻击的服务端口 | 严重 |

---

## 🎯 V3.0 设计原则

### 1. 单一维度：攻击源网段权重

**核心逻辑**:
```
威胁评分 = 基础评分 × attack_ip的网段权重

基础评分 = (probeCount × honeypotsAccessed × targetPortsDiversity)
         × timeWeight × attackIntentWeight × persistenceWeight
```

**关键变化**:
- ❌ 删除"目标网段权重"（response_ip全是诱饵，无需分类）
- ❌ 删除"方向性权重"（没有真实服务器，无方向可言）
- ✅ 只保留"攻击源网段权重"（`attack_ip` 的风险等级）

### 2. 网段权重反映攻击源风险

**攻击源风险评估因素**:

#### 2.1 网段类型分类

| 网段类型 | 说明 | 典型权重 | 理由 |
|---------|------|---------|------|
| **办公网段** | 日常办公人员 | 1.0-1.2 | 可能被钓鱼邮件攻陷 |
| **高管/财务网段** | 高价值目标 | 1.5-1.8 | 攻陷后危害大，APT常见目标 |
| **服务器网段** | 应用/Web服务器 | 2.0-2.5 | 服务器被攻陷意味着严重安全事故 |
| **管理网段** | 运维/跳板机 | 2.5-3.0 | 可控制全网，最高危 |
| **DMZ区域** | 对外服务 | 1.8-2.2 | 易被攻破，作为跳板进入内网 |
| **访客网络** | 临时访客 | 0.5-0.8 | 隔离网段，危害有限 |
| **IoT设备网段** | 摄像头/打印机 | 0.8-1.2 | 易被劫持但本身价值低 |

#### 2.2 风险等级定义

**基于业务重要性和攻陷后果**:

| 风险等级 | 权重范围 | 典型网段 | 攻陷后果 |
|---------|---------|---------|---------|
| **VERY_LOW** | 0.5-0.7 | 访客网络、物理隔离区 | 有限危害 |
| **LOW** | 0.8-1.0 | 普通办公区、IoT设备 | 一般威胁 |
| **MEDIUM** | 1.1-1.5 | 重要办公区、开发网段 | 中等威胁 |
| **HIGH** | 1.6-2.0 | 服务器区、高管网段 | 严重威胁 |
| **CRITICAL** | 2.1-3.0 | 管理网段、核心数据库区 | 极严重威胁 |

---

## 🔧 数据库表结构（V3.0）

### ip_segment_weight_config

```sql
CREATE TABLE IF NOT EXISTS ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 网段基本信息
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 攻击源风险评估（简化设计）
    segment_type VARCHAR(50) NOT NULL,      -- OFFICE, SERVER, DMZ, MANAGEMENT, IOT, GUEST
    risk_level VARCHAR(20) NOT NULL,        -- VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 统一权重（1个值）
    
    -- 元数据
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束
    CONSTRAINT uk_customer_segment UNIQUE (customer_id, segment_name)
);

-- 索引
CREATE INDEX idx_customer_active ON ip_segment_weight_config(customer_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_ip_range ON ip_segment_weight_config(customer_id, ip_range_start, ip_range_end);
CREATE INDEX idx_risk_level ON ip_segment_weight_config(risk_level);

COMMENT ON TABLE ip_segment_weight_config IS '蜜罐系统攻击源网段权重配置 - 仅评估attack_ip来源风险';
COMMENT ON COLUMN ip_segment_weight_config.weight IS '攻击源权重倍数（基于网段被攻陷的风险和危害）';
COMMENT ON COLUMN ip_segment_weight_config.segment_type IS '网段类型：OFFICE(办公)、SERVER(服务器)、DMZ(半信任区)、MANAGEMENT(管理)、IOT(物联网)、GUEST(访客)';
COMMENT ON COLUMN ip_segment_weight_config.risk_level IS '风险等级：VERY_LOW、LOW、MEDIUM、HIGH、CRITICAL';
```

**关键简化**:
- ❌ 删除 `zone_type`, `zone_level` (V2.0的复杂分类)
- ❌ 删除 `weight_as_source`, `weight_as_target` (不需要方向性)
- ❌ 删除 `weight_lateral_same_zone`, `weight_lateral_cross_zone`, `weight_escalation`, `weight_exfiltration` (无真实服务器)
- ✅ 只保留 `segment_type`, `risk_level`, `weight` (单一权重)

---

## 📊 默认配置示例

### 1. 办公网段（OFFICE）

```sql
-- 普通办公区（低风险）
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
) VALUES
    ('default', 'Office-General-192.168.10.0/24', '192.168.10.0', '192.168.10.255',
     'OFFICE', 'LOW', 1.0,
     '普通办公区 - 可能被钓鱼攻陷，但危害可控', 50),
    
    -- 高管/财务办公区（高风险）
    ('default', 'Office-Executive-192.168.20.0/24', '192.168.20.0', '192.168.20.255',
     'OFFICE', 'HIGH', 1.8,
     '高管/财务办公区 - APT高价值目标，攻陷后危害大', 70),
    
    -- 访客网络（极低风险）
    ('default', 'Office-Guest-192.168.100.0/24', '192.168.100.0', '192.168.100.255',
     'GUEST', 'VERY_LOW', 0.6,
     '访客网络 - 物理隔离，危害有限', 30);
```

**权重理由**:
- 普通办公区: 1.0 (基准，最常见的失陷场景)
- 高管办公区: 1.8 (高价值目标，APT常攻击对象)
- 访客网络: 0.6 (隔离网段，即使失陷也难以横向移动)

### 2. 服务器网段（SERVER）

```sql
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
) VALUES
    -- Web服务器（高风险）
    ('default', 'Server-Web-10.0.1.0/24', '10.0.1.0', '10.0.1.255',
     'SERVER', 'HIGH', 2.0,
     'Web服务器区 - 对外暴露，易被攻破后作为跳板', 80),
    
    -- 应用服务器（关键风险）
    ('default', 'Server-App-10.0.2.0/24', '10.0.2.0', '10.0.2.255',
     'SERVER', 'CRITICAL', 2.5,
     '应用服务器区 - 核心业务系统，攻陷后果严重', 90),
    
    -- 数据库服务器（极严重）
    ('default', 'Server-Database-10.0.3.0/24', '10.0.3.0', '10.0.3.255',
     'SERVER', 'CRITICAL', 3.0,
     '数据库服务器区 - 数据核心，被攻陷意味着数据泄露', 100);
```

**权重理由**:
- Web服务器: 2.0 (对外暴露，易被攻破)
- 应用服务器: 2.5 (核心业务，攻陷后影响业务连续性)
- 数据库服务器: 3.0 (最高权重，数据泄露/勒索软件加密的主要目标)

### 3. 管理网段（MANAGEMENT）

```sql
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
) VALUES
    ('default', 'Management-OPS-10.0.100.0/24', '10.0.100.0', '10.0.100.255',
     'MANAGEMENT', 'CRITICAL', 3.0,
     '运维管理网段 - 跳板机/堡垒机，攻陷后可控制全网', 100);
```

**权重理由**:
- 管理网段: 3.0 (与数据库同等最高权重，可控制全网资产)

### 4. DMZ区域（DMZ）

```sql
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
) VALUES
    ('default', 'DMZ-Public-172.16.1.0/24', '172.16.1.0', '172.16.1.255',
     'DMZ', 'HIGH', 2.0,
     'DMZ区域 - 半信任区，易被攻陷后作为进入内网的跳板', 80);
```

**权重理由**:
- DMZ: 2.0 (对外服务，被攻陷后作为跳板进入内网)

### 5. IoT设备网段（IOT）

```sql
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
) VALUES
    ('default', 'IoT-Devices-192.168.50.0/24', '192.168.50.0', '192.168.50.255',
     'IOT', 'LOW', 0.9,
     'IoT设备网段 - 摄像头/打印机，易被劫持但危害有限', 40);
```

**权重理由**:
- IoT设备: 0.9 (易被劫持，但通常权限有限，难以横向移动)

---

## 🔢 威胁评分公式（V3.0）

### 完整公式

```java
threatScore = (probeCount × honeypotsAccessed × targetPortsDiversity)
            × timeWeight
            × attackSourceWeight        // ← 唯一的网段权重
            × attackIntentWeight        // 基于端口
            × persistenceWeight         // 基于频率
            × deviceCoverageWeight      // 基于设备数
```

### 权重计算详解

#### 1. 攻击源网段权重（attackSourceWeight）

**核心逻辑**: 查询 `attack_ip` 所属网段，获取权重

```java
public double getAttackSourceWeight(String customerId, String attackIp) {
    // 1. 查询数据库获取匹配网段
    Optional<IpSegmentWeightConfig> segment = repository.findByCustomerIdAndIpRange(
        customerId, attackIp
    );
    
    // 2. 返回权重（默认1.0）
    return segment.map(IpSegmentWeightConfig::getWeight).orElse(1.0);
}
```

**SQL查询**:
```sql
SELECT weight FROM ip_segment_weight_config
WHERE customer_id = ?
  AND is_active = TRUE
  AND CAST(? AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
ORDER BY priority DESC
LIMIT 1;
```

#### 2. 其他权重（保持不变）

- **timeWeight**: 时间权重 (深夜1.5，工作时间1.0)
- **attackIntentWeight**: 攻击意图权重 (基于端口，RDP/SMB=3.0)
- **persistenceWeight**: 持久性权重 (高频扫描2.0)
- **deviceCoverageWeight**: 设备覆盖权重 (多设备检测2.5)

---

## 📈 评分示例

### 场景1: 数据库服务器被攻陷后横向移动

**攻击行为**:
- 攻击源: 10.0.3.50 (数据库服务器区)
- 访问诱饵: 5个
- 目标端口: RDP (3389), SMB (445)
- 探测次数: 200次 (2分钟)
- 时间: 凌晨2:00

**评分计算**:
```
基础分 = 200 × 5 × 2 = 2000

timeWeight = 1.5 (深夜)
attackSourceWeight = 3.0 (数据库服务器区 ← 关键！)
attackIntentWeight = 3.0 (RDP/SMB高危)
persistenceWeight = 2.0 (100次/分钟)
deviceCoverageWeight = 1.0 (单设备)

threatScore = 2000 × 1.5 × 3.0 × 3.0 × 2.0 × 1.0
            = 2000 × 27
            = 54,000

威胁等级: CRITICAL ⚠️⚠️⚠️
```

**分析**: 
- ✅ 数据库服务器被攻陷 (3.0权重) = 极严重
- ✅ 高危端口 + 深夜扫描 = APT级威胁
- ✅ 评分准确反映严重性

### 场景2: 普通办公区零星探测

**攻击行为**:
- 攻击源: 192.168.10.50 (普通办公区)
- 访问诱饵: 1个
- 目标端口: HTTP (80)
- 探测次数: 5次
- 时间: 下午3:00

**评分计算**:
```
基础分 = 5 × 1 × 1 = 5

timeWeight = 1.0 (工作时间)
attackSourceWeight = 1.0 (普通办公区)
attackIntentWeight = 1.5 (HTTP低危)
persistenceWeight = 1.0 (低频)
deviceCoverageWeight = 1.0 (单设备)

threatScore = 5 × 1.0 × 1.0 × 1.5 × 1.0 × 1.0
            = 7.5

威胁等级: INFO
```

**分析**: 
- ✅ 普通办公区 (1.0权重) = 正常风险
- ✅ 零星探测 = 低威胁
- ✅ 评分合理

### 场景3: 访客网络扫描

**攻击行为**:
- 攻击源: 192.168.100.20 (访客网络)
- 访问诱饵: 3个
- 目标端口: 多端口扫描
- 探测次数: 50次

**评分计算**:
```
基础分 = 50 × 3 × 5 = 750

timeWeight = 1.0
attackSourceWeight = 0.6 (访客网络 ← 降低权重)
attackIntentWeight = 2.0 (多端口)
persistenceWeight = 1.3
deviceCoverageWeight = 1.0

threatScore = 750 × 1.0 × 0.6 × 2.0 × 1.3 × 1.0
            = 1170

威胁等级: MEDIUM
```

**分析**: 
- ✅ 访客网络 (0.6权重) = 有限危害
- ✅ 虽有扫描行为，但隔离网段降低威胁
- ✅ 评分反映实际风险

---

## 🔄 客户自定义配置

### 配置示例

**客户A的网络拓扑**:
```
总部办公区: 192.168.1.0/24
分公司办公区: 192.168.2.0/24
核心机房: 10.100.0.0/16
开发测试环境: 10.200.0.0/16
```

**配置SQL**:
```sql
-- 客户A的自定义配置
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description
) VALUES
    ('customer-A', 'HQ-Office', '192.168.1.0', '192.168.1.255',
     'OFFICE', 'MEDIUM', 1.2,
     '总部办公区'),
    
    ('customer-A', 'Branch-Office', '192.168.2.0', '192.168.2.255',
     'OFFICE', 'LOW', 0.9,
     '分公司办公区 - 权限较低'),
    
    ('customer-A', 'Core-DataCenter', '10.100.0.0', '10.100.255.255',
     'SERVER', 'CRITICAL', 3.0,
     '核心机房 - 所有生产服务器'),
    
    ('customer-A', 'Dev-Test', '10.200.0.0', '10.200.255.255',
     'SERVER', 'MEDIUM', 1.3,
     '开发测试环境 - 非生产');
```

### 批量导入（CSV）

**CSV格式**:
```csv
customer_id,segment_name,ip_range_start,ip_range_end,segment_type,risk_level,weight,description
customer-B,Office-Floor1,192.168.10.0,192.168.10.255,OFFICE,LOW,1.0,1楼办公区
customer-B,Office-Floor2,192.168.20.0,192.168.20.255,OFFICE,MEDIUM,1.2,2楼高管区
customer-B,Server-Room,10.0.1.0,10.0.1.255,SERVER,CRITICAL,2.8,服务器机房
```

---

## 🎯 与V2.0的对比

| 维度 | V2.0 (错误设计) | V3.0 (正确设计) | 说明 |
|------|----------------|----------------|------|
| **核心假设** | ❌ 有真实服务器流量 | ✅ 只有蜜罐流量 | 致命错误修正 |
| **response_ip理解** | ❌ 真实服务器IP | ✅ 诱饵IP | 根本误解 |
| **权重维度** | ❌ 源权重 × 目标权重 × 方向权重 | ✅ 只有攻击源权重 | 简化为单一维度 |
| **网段分类** | ❌ OFFICE/SERVER/DMZ (真实网络) | ✅ 基于attack_ip来源 | 理解正确 |
| **表字段数量** | ❌ 15+ 字段 | ✅ 8 个核心字段 | 大幅简化 |
| **配置复杂度** | ❌ 极高 (6个方向性权重) | ✅ 低 (1个权重值) | 易于配置 |
| **客户可理解性** | ❌ 难以理解 | ✅ 直观明了 | 实用性提升 |

---

## ✅ 实施检查清单

### 阶段1: 数据库表创建
- [ ] 创建简化的 `ip_segment_weight_config` 表
- [ ] 插入默认配置（6种网段类型）
- [ ] 验证索引和约束

### 阶段2: 服务层实现
- [ ] 实现 `getAttackSourceWeight(customerId, attackIp)` 方法
- [ ] 修改 `ThreatScoreCalculator` 集成攻击源权重
- [ ] 删除V2.0中的方向性权重代码

### 阶段3: API开发
- [ ] CRUD API: `/api/v1/assessment/ip-segments`
- [ ] 批量导入/导出功能
- [ ] 按segment_type/risk_level筛选

### 阶段4: 测试验证
- [ ] 单元测试: IP范围匹配
- [ ] 集成测试: 不同网段的评分差异
- [ ] 端到端测试: 完整流程验证

### 阶段5: 文档更新
- [ ] API文档
- [ ] 客户配置指南
- [ ] 迁移指南

---

## 📚 总结

### V3.0的核心改进

1. **✅ 正确理解蜜罐机制**
   - 只有攻击源信息有意义
   - response_ip全是诱饵，无需分类

2. **✅ 大幅简化设计**
   - 从15+字段降低到8个核心字段
   - 从6个方向性权重简化为1个统一权重
   - 配置复杂度降低80%

3. **✅ 实用性大幅提升**
   - 客户容易理解（攻击源风险等级）
   - 配置简单（只需设置1个权重值）
   - 符合实际业务场景

4. **✅ 评分准确性提升**
   - 数据库服务器被攻陷 = 3.0权重（最高危）
   - 访客网络攻陷 = 0.6权重（有限危害）
   - 准确反映实际风险

---

**作者**: ThreatDetection Team  
**审核**: 待审核  
**批准**: 待批准  

**关键**: 本设计基于对蜜罐机制的正确理解，删除了V2.0中关于"真实服务器网段"的所有假设。
