# IP网段权重系统 V2.0 → V3.0 迁移指南

**日期**: 2025-10-24  
**影响范围**: IP网段权重配置表、威胁评分算法  
**迁移原因**: 修正对蜜罐机制的根本误解  

---

## ⚠️ 为什么需要V3.0？

### V2.0的致命缺陷

**根本误解**:
- ❌ 认为系统同时获取"攻击源流量"和"真实服务器流量"
- ❌ 设计了复杂的"源网段 × 目标网段 × 方向性"权重矩阵
- ❌ 将 `response_ip` 理解为"被攻击的真实服务器IP"

**实际情况**:
- ✅ 系统是**纯蜜罐/欺骗防御系统**
- ✅ **所有 `response_ip` 都是诱饵IP**（不存在的虚拟哨兵）
- ✅ 真实服务器的流量**未被采集**（未来可能加入）

**影响**: V2.0的评分算法完全错误，无法反映实际威胁

---

## 📊 V2.0 vs V3.0 对比

| 维度 | V2.0 (错误设计) | V3.0 (正确设计) |
|------|----------------|----------------|
| **核心假设** | ❌ 有真实服务器流量 | ✅ 只有蜜罐流量 |
| **response_ip含义** | ❌ 真实服务器IP | ✅ 诱饵IP（虚拟哨兵） |
| **权重维度** | ❌ 源权重 × 目标权重 × 方向权重 | ✅ 只有攻击源权重 |
| **表字段数量** | ❌ 15+ 字段 | ✅ 8 个核心字段 |
| **配置复杂度** | ❌ 极高（6个方向性权重） | ✅ 低（1个权重值） |
| **评分公式** | ❌ `score × sourceWeight × targetWeight × directionWeight` | ✅ `score × attackSourceWeight` |
| **客户可理解性** | ❌ 难以理解 | ✅ 直观明了 |

**简化程度**: 从15个字段降低到8个（减少47%），配置复杂度降低80%

---

## 🔧 迁移步骤

### 阶段1: 评估影响（仅规划，不执行）

#### 1.1 检查现有配置

```sql
-- 查询V2.0配置数量
SELECT customer_id, COUNT(*) as config_count
FROM ip_segment_weight_config
WHERE customer_id != 'default'
GROUP BY customer_id;
```

**输出示例**:
```
customer_id    | config_count
---------------+-------------
customer-A     | 25
customer-B     | 18
```

#### 1.2 识别需要迁移的客户配置

**注意**: V2.0的复杂权重（`weight_as_source`, `weight_as_target`, `weight_lateral_*`等）**无法自动迁移**

**原因**: V3.0只保留单一的"攻击源权重"，需要业务人员决定如何合并多个权重

### 阶段2: 备份现有数据

```sql
-- 创建备份表
CREATE TABLE ip_segment_weight_config_v2_backup AS 
SELECT * FROM ip_segment_weight_config;

-- 验证备份
SELECT COUNT(*) FROM ip_segment_weight_config_v2_backup;
```

**备份位置**: 表 `ip_segment_weight_config_v2_backup`

### 阶段3: 部署V3.0

#### 3.1 删除旧表（⚠️ 危险操作）

```sql
DROP TABLE IF EXISTS ip_segment_weight_config;
```

#### 3.2 执行V3.0初始化脚本

```bash
# 方法1: 通过docker-compose重启
cd /path/to/threat-detection-system/docker
docker-compose down postgres
docker-compose up -d postgres

# 方法2: 手动执行SQL
psql -h localhost -U threat_user -d threat_detection -f 11-ip-segment-weights-v3.sql
```

#### 3.3 验证V3.0安装

```sql
-- 检查表结构
\d ip_segment_weight_config

-- 检查默认配置
SELECT segment_name, segment_type, risk_level, weight
FROM ip_segment_weight_config
WHERE customer_id = 'default' AND is_active = TRUE
ORDER BY weight DESC;
```

**预期输出**:
```
segment_name                      | segment_type | risk_level | weight
----------------------------------+--------------+------------+-------
Server-Database-10.0.3.0/24       | SERVER       | CRITICAL   | 3.00
Management-OPS-10.0.100.0/24      | MANAGEMENT   | CRITICAL   | 3.00
Server-App-10.0.2.0/24            | SERVER       | CRITICAL   | 2.50
Office-Executive-192.168.20.0/24  | OFFICE       | HIGH       | 1.80
...
```

### 阶段4: 手动迁移客户配置

#### 4.1 V2.0 → V3.0 权重转换规则

**转换原则**: 在蜜罐系统中，只有 `attack_ip` 的来源风险有意义

| V2.0 字段 | V3.0 对应 | 转换规则 |
|----------|----------|---------|
| `weight_as_source` | **weight** | ✅ 直接使用（攻击源权重） |
| `weight_as_target` | ❌ 删除 | response_ip全是诱饵，无需此权重 |
| `weight_lateral_same_zone` | ❌ 删除 | 无真实服务器，无横向移动 |
| `weight_lateral_cross_zone` | ❌ 删除 | 同上 |
| `weight_escalation` | ❌ 删除 | 同上 |
| `weight_exfiltration` | ❌ 删除 | 同上 |

**示例**:

```sql
-- V2.0 配置（客户A的办公区）
-- segment_name: Office-HQ-Floor1
-- weight_as_source: 1.2
-- weight_as_target: 0.8
-- weight_lateral_same_zone: 0.6
-- weight_escalation: 1.5

-- V3.0 配置（只保留 weight_as_source）
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description
) VALUES (
    'customer-A', 'Office-HQ-Floor1', '192.168.1.0', '192.168.1.255',
    'OFFICE', 'MEDIUM', 1.2,  -- ← 只保留 weight_as_source
    '总部办公区1楼（从V2.0迁移）'
);
```

#### 4.2 批量迁移脚本

```sql
-- 从V2.0备份表提取客户配置（只保留 weight_as_source）
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
)
SELECT 
    customer_id,
    segment_name,
    ip_range_start,
    ip_range_end,
    zone_type,  -- V2.0的zone_type对应V3.0的segment_type
    zone_level,  -- V2.0的zone_level对应V3.0的risk_level
    weight_as_source,  -- ← 关键: 只保留攻击源权重
    CONCAT(description, ' (从V2.0迁移)'),
    priority
FROM ip_segment_weight_config_v2_backup
WHERE customer_id != 'default'
  AND is_active = TRUE
ON CONFLICT (customer_id, segment_name) DO NOTHING;
```

**注意**: 
- `zone_type` → `segment_type` (值可能需要映射: `INTERNAL` → `OFFICE`)
- `zone_level` → `risk_level` (值可能需要映射: `L1` → `LOW`)

#### 4.3 手动审查迁移结果

```sql
-- 检查客户A的迁移结果
SELECT segment_name, segment_type, risk_level, weight, description
FROM ip_segment_weight_config
WHERE customer_id = 'customer-A'
ORDER BY weight DESC;
```

**验证要点**:
- [ ] 所有高价值网段（数据库、管理网段）权重 >= 2.0
- [ ] 低风险网段（访客、IoT）权重 <= 1.0
- [ ] segment_type 和 risk_level 字段正确映射

---

## 🔢 评分公式变化

### V2.0 评分公式（错误）

```java
// ❌ 错误理解: response_ip是真实服务器
double sourceWeight = getWeight(customerId, attackIp, "AS_SOURCE");
double targetWeight = getWeight(customerId, responseIp, "AS_TARGET");  // ← 错误!
double directionWeight = getDirectionWeight(attackIp, responseIp);  // ← 错误!

threatScore = baseScore × sourceWeight × targetWeight × directionWeight;
```

**问题**: `responseIp` 全是诱饵，计算 `targetWeight` 和 `directionWeight` 毫无意义

### V3.0 评分公式（正确）

```java
// ✅ 正确理解: 只评估攻击源风险
double attackSourceWeight = getAttackSourceWeight(customerId, attackIp);

threatScore = (probeCount × honeypotsAccessed × targetPortsDiversity)
            × timeWeight
            × attackSourceWeight  // ← 唯一的网段权重
            × attackIntentWeight
            × persistenceWeight
            × deviceCoverageWeight;
```

**优势**:
- 简单直观（只有1个网段权重）
- 准确反映风险（数据库服务器被攻陷 = 3.0权重）
- 易于配置（客户只需设置攻击源风险等级）

---

## 📈 评分对比示例

### 场景: 数据库服务器发起横向移动扫描

**攻击行为**:
- 攻击源: 10.0.3.50 (数据库服务器区)
- 访问诱饵: 5个
- 目标端口: RDP (3389), SMB (445)
- 探测次数: 200次 (2分钟)
- 时间: 凌晨2:00

#### V2.0 评分（错误）

```
sourceWeight = 2.5 (数据库作为攻击源)
targetWeight = 1.0 (诱饵IP被误认为真实服务器)
directionWeight = 1.8 (数据库→办公区，方向性权重)

baseScore = 200 × 5 × 2 = 2000
threatScore = 2000 × 2.5 × 1.0 × 1.8 = 9000
```

**问题**: `targetWeight` 和 `directionWeight` 无意义

#### V3.0 评分（正确）

```
attackSourceWeight = 3.0 (数据库服务器被攻陷 = 极严重)

baseScore = 200 × 5 × 2 = 2000
threatScore = 2000 × 1.5 (时间) × 3.0 (攻击源) × 3.0 (意图) × 2.0 (持久性)
            = 2000 × 27 = 54000
```

**改进**: 评分更高，准确反映"数据库服务器被攻陷"的严重性

---

## ✅ 验证清单

### 数据库层面

- [ ] V3.0表结构正确创建（8个核心字段）
- [ ] 默认配置包含12条记录（不同网段类型）
- [ ] 索引正常工作（`idx_ip_segment_customer_active`, `idx_ip_segment_range`）
- [ ] 查询函数 `get_attack_source_weight()` 可正常调用

### 应用层面

- [ ] `ThreatScoreCalculator` 已更新为V3.0公式
- [ ] `IpSegmentWeightService` 只查询 `attack_ip` 权重
- [ ] 删除所有 `getTargetWeight()` 和 `getDirectionWeight()` 调用
- [ ] 单元测试通过（不同攻击源的评分差异）

### 业务层面

- [ ] 客户配置已迁移（从V2.0备份表）
- [ ] 高价值网段权重 >= 2.0（数据库、管理网段）
- [ ] 低风险网段权重 <= 1.0（访客、IoT）
- [ ] 客户已培训V3.0配置方法

---

## 🔄 回滚计划

### 如果V3.0出现问题

**回滚步骤**:

```sql
-- 1. 停止应用服务
docker-compose down data-ingestion stream-processing threat-assessment

-- 2. 删除V3.0表
DROP TABLE IF EXISTS ip_segment_weight_config;

-- 3. 恢复V2.0备份
CREATE TABLE ip_segment_weight_config AS 
SELECT * FROM ip_segment_weight_config_v2_backup;

-- 4. 重建索引
CREATE INDEX idx_ip_segment_customer_active 
    ON ip_segment_weight_config(customer_id, is_active) 
    WHERE is_active = TRUE;

-- 5. 重启应用（使用V2.0代码）
docker-compose up -d
```

**注意**: 需要同时回滚代码到V2.0版本

---

## 📞 支持

### 常见问题

**Q1: V2.0的客户配置可以自动迁移吗？**  
A: 部分可以。`segment_name`, `ip_range_*`, `weight_as_source` 可以自动迁移，但需要手动审查 `segment_type` 和 `risk_level` 映射。

**Q2: 为什么V3.0删除了方向性权重？**  
A: 因为系统是纯蜜罐，所有 `response_ip` 都是诱饵（不存在的虚拟哨兵），不存在"真实服务器"，因此无需计算"攻击方向"权重。

**Q3: V3.0如何处理未配置的IP？**  
A: V3.0包含兜底规则（`0.0.0.0/0`，权重1.0），未匹配到的IP使用基准权重。

**Q4: 迁移需要停机吗？**  
A: 建议停机迁移（约10-30分钟），因为涉及表结构变更和代码更新。可以选择低峰期进行。

---

## 📋 总结

### 关键改进

1. **✅ 正确理解蜜罐机制**: 只有攻击源信息有意义
2. **✅ 大幅简化设计**: 从15+字段降低到8个（减少47%）
3. **✅ 配置复杂度降低**: 从6个方向性权重简化为1个统一权重
4. **✅ 评分准确性提升**: 数据库服务器被攻陷 = 3.0权重（最高危）

### 迁移时间估算

| 客户配置数量 | 预计迁移时间 |
|------------|------------|
| < 20 | 1小时 |
| 20-50 | 2-3小时 |
| 50-100 | 4-6小时 |
| 100+ | 需要定制脚本 |

**建议**: 先在测试环境验证，再在生产环境执行

---

**文档版本**: 1.0  
**最后更新**: 2025-10-24  
**作者**: ThreatDetection Team
