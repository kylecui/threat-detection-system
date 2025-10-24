# IP网段权重系统 V2.0 设计文档

**版本**: 2.0  
**日期**: 2025-10-24  
**状态**: 设计中  

---

## 📋 变更原因

### 原设计的问题
1. ❌ 将内网网段权重设置过低（0.5-0.8），不符合蜜罐检测的实际场景
2. ❌ 采用全局统一配置，无法适应不同客户的网络拓扑
3. ❌ 忽略了攻击方向性，横向移动的危害程度与方向密切相关

### 核心理解修正

**系统本质**: 这是一个**内网蜜罐/诱饵防御系统**，而非传统边界防御

| 维度 | 原设计理解 | ✅ 正确理解 |
|------|-----------|------------|
| **检测对象** | 外部攻击者 | **内网失陷主机**（已被攻破的设备） |
| **威胁来源** | 互联网 | **内网横向移动** |
| **response_ip** | 被攻击的服务器 | **诱饵IP（蜜罐）** |
| **攻击特征** | 扫描、探测 | **横向移动、权限提升** |
| **风险评估** | 基于攻击来源地理位置 | **基于内网区域和移动方向** |

---

## 🎯 新设计原则

### 1. 多租户架构

**需求**: 每个客户的网络拓扑不同

```
客户A:
  - 办公区: 192.168.100.0/24
  - 服务器区: 10.100.0.0/16

客户B:
  - 办公区: 172.16.0.0/16
  - 数据中心: 10.0.0.0/8
```

**解决方案**: 在配置表中增加 `customer_id` 字段，支持客户自定义

### 2. 方向性权重

**核心创新**: 同一个IP在不同场景下权重不同

#### 2.1 作为攻击源 vs 攻击目标

| 网段类型 | 作为源（被攻陷） | 作为目标（蜜罐） |
|---------|----------------|----------------|
| 办公区 | 0.80（正常办公流量多） | 1.00（诱捕价值中等） |
| 数据库服务器 | 2.00（核心资产被攻陷） | 2.00（高价值诱饵） |
| 蜜罐网段 | 0.00（不可能，是诱饵） | 3.00（访问即恶意） |

#### 2.2 横向移动方向

**场景1: 同区域横向移动**
```
办公区A (192.168.10.10) → 办公区B (192.168.10.20)
权重: 0.90（相对正常，可能是同事间协作）
```

**场景2: 权限提升（低→高）**
```
办公区 (192.168.10.10) → 数据库服务器 (10.0.2.100)
权重: 2.50（严重威胁！办公终端不应访问数据库）
```

**场景3: 数据外泄（高→低）**
```
数据库服务器 (10.0.2.100) → 办公区 (192.168.10.10)
权重: 2.30（可疑！数据库主动连接办公终端）
```

**场景4: 跨区域横向**
```
Web服务器 (10.0.3.50) → 应用服务器 (10.0.1.100)
权重: 1.80（中高危，需要审计）
```

**场景5: 访问蜜罐**
```
任意IP → 蜜罐网段 (10.0.99.50)
权重: 3.00（确认恶意！蜜罐不应被访问）
```

### 3. 企业网络分区

基于零信任架构的区域划分：

| 区域类型 | 说明 | 典型权重 |
|---------|------|---------|
| **OFFICE** | 办公网段 | 0.8-1.2 |
| **SERVER** | 服务器区 | 1.5-2.0 |
| **DMZ** | 半信任区 | 1.2-1.6 |
| **MANAGEMENT** | 管理网段 | 1.8-2.0 |
| **PRODUCTION** | 生产网络（OT） | 2.0-3.0 |
| **IOT** | 物联网设备 | 0.8-1.5 |
| **GUEST** | 访客网络 | 0.5-0.8 |

**安全等级**:
- **LOW**: 一般办公、访客
- **MEDIUM**: 重要办公、Web服务器
- **HIGH**: 应用服务器、DMZ
- **CRITICAL**: 数据库、管理网段、生产系统

---

## 🔧 技术实现

### 数据库表结构

```sql
CREATE TABLE ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,           -- 多租户
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    zone_type VARCHAR(50) NOT NULL,             -- 区域类型
    zone_level VARCHAR(20) NOT NULL,            -- 安全等级
    
    weight_as_source DECIMAL(3,2) DEFAULT 1.00, -- 作为源
    weight_as_target DECIMAL(3,2) DEFAULT 1.00, -- 作为目标
    
    weight_lateral_same_zone DECIMAL(3,2),      -- 同区域横向
    weight_lateral_cross_zone DECIMAL(3,2),     -- 跨区域横向
    weight_escalation DECIMAL(3,2),             -- 权限提升
    weight_exfiltration DECIMAL(3,2),           -- 数据外泄
    
    is_honeypot BOOLEAN DEFAULT FALSE,          -- 蜜罐标记
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    CONSTRAINT uk_customer_segment UNIQUE (customer_id, segment_name)
);
```

### 方向性权重计算函数

```sql
CREATE FUNCTION calculate_directional_weight(
    p_customer_id VARCHAR(50),
    p_source_ip VARCHAR(15),
    p_target_ip VARCHAR(15)
) RETURNS DECIMAL(3,2)
```

**决策逻辑**:
1. **优先级1**: 检查目标是否为蜜罐 → 返回 3.00
2. **优先级2**: 检查是否同区域 → 返回 `weight_lateral_same_zone`
3. **优先级3**: 检查是否权限提升（低→高）→ 返回 `weight_escalation`
4. **优先级4**: 检查是否数据外泄（高→低）→ 返回 `weight_exfiltration`
5. **默认**: 跨区域横向 → 返回 `weight_lateral_cross_zone`

---

## 📊 配置示例

### 默认配置（供客户参考）

#### 1. 办公网段
```sql
-- 普通办公区
('default', 'Office-General', '192.168.10.0', '192.168.10.255',
 'OFFICE', 'LOW',
 0.80, 1.00,              -- 作为源/目标
 0.90, 1.30, 1.50, 1.20,  -- 同区域/跨区域/提权/外泄
 '普通办公区')

-- 高管办公区
('default', 'Office-Executive', '192.168.11.0', '192.168.11.255',
 'OFFICE', 'MEDIUM',
 1.20, 1.40,              -- 高价值目标
 1.00, 1.60, 2.00, 1.80,
 '高管办公区')
```

#### 2. 服务器网段
```sql
-- 应用服务器
('default', 'Server-Application', '10.0.1.0', '10.0.1.255',
 'SERVER', 'HIGH',
 1.80, 1.90,
 1.50, 2.00, 2.50, 2.30,
 '应用服务器区')

-- 数据库服务器
('default', 'Server-Database', '10.0.2.0', '10.0.2.255',
 'SERVER', 'CRITICAL',
 2.00, 2.00,              -- 最高权重
 1.80, 2.50, 3.00, 2.80,
 '数据库服务器区')
```

#### 3. 蜜罐网段
```sql
('default', 'Honeypot-Fake-Finance', '10.0.99.0', '10.0.99.255',
 'SERVER', 'CRITICAL',
 0.00, 3.00,              -- 访问即恶意
 0.00, 3.00, 3.00, 3.00,
 '蜜罐网段 - 虚假财务服务器',
 is_honeypot = TRUE)
```

### 客户自定义配置

```sql
-- 客户A的定制配置
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level, ...
) VALUES
    ('customer-A', 'Office-Beijing', '192.168.100.0', '192.168.100.255',
     'OFFICE', 'LOW', 0.80, 1.00, ...),
    
    ('customer-A', 'Server-Core', '10.100.1.0', '10.100.1.255',
     'SERVER', 'CRITICAL', 2.00, 2.00, ...);
```

---

## 🔄 集成到威胁评分

### 修改威胁评分公式

**原公式**:
```
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × ipWeight  ← 单一IP权重
            × portWeight 
            × deviceWeight
```

**新公式**:
```
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × sourceIpWeight        ← 攻击源网段权重
            × targetIpWeight        ← 目标网段权重
            × directionalWeight     ← 方向性权重
            × portWeight 
            × deviceWeight
```

### Java服务层修改

```java
public class ThreatScoreCalculator {
    
    @Autowired
    private IpSegmentWeightService ipSegmentService;
    
    public double calculateThreatScore(AggregatedAttackData data) {
        // 1. 获取源IP网段权重
        double sourceWeight = ipSegmentService.getWeightAsSource(
            data.getCustomerId(), 
            data.getAttackIp()
        );
        
        // 2. 获取目标IP网段权重（可能是多个蜜罐）
        double targetWeight = data.getResponseIps().stream()
            .mapToDouble(ip -> ipSegmentService.getWeightAsTarget(
                data.getCustomerId(), ip
            ))
            .average()
            .orElse(1.0);
        
        // 3. 计算方向性权重
        double directionalWeight = ipSegmentService.calculateDirectionalWeight(
            data.getCustomerId(),
            data.getAttackIp(),
            data.getResponseIps().get(0)  // 主要目标
        );
        
        // 4. 综合计算
        double baseScore = data.getAttackCount() 
                         * data.getUniqueIps() 
                         * data.getUniquePorts();
        
        return baseScore 
             * calculateTimeWeight(data.getTimestamp())
             * sourceWeight
             * targetWeight
             * directionalWeight
             * calculatePortWeight(data.getUniquePorts())
             * calculateDeviceWeight(data.getUniqueDevices());
    }
}
```

---

## 📈 威胁场景示例

### 场景1: 办公终端横向扫描数据库（严重）

```
源IP: 192.168.10.50 (办公区)
目标IP: 10.0.2.100 (数据库服务器)
攻击次数: 150
唯一端口: 5 (3306, 5432, 1433, ...)

计算:
  sourceWeight = 0.80 (办公区被攻陷)
  targetWeight = 2.00 (数据库是高价值目标)
  directionalWeight = 2.50 (权限提升: LOW → CRITICAL)
  
  threatScore = 150 × 1 × 5 
              × 1.2 (深夜)
              × 0.80 × 2.00 × 2.50
              × 1.4 (端口权重)
              × 1.0 (单设备)
            = 3780

  威胁等级: CRITICAL ⚠️
```

### 场景2: 办公区内横向移动（低危）

```
源IP: 192.168.10.50 (办公区A)
目标IP: 192.168.10.80 (办公区B)
攻击次数: 50
唯一端口: 2 (445, 139)

计算:
  sourceWeight = 0.80
  targetWeight = 1.00
  directionalWeight = 0.90 (同区域横向)
  
  threatScore = 50 × 1 × 2 
              × 1.0 (工作时间)
              × 0.80 × 1.00 × 0.90
              × 1.2 (端口权重)
              × 1.0
            = 86.4

  威胁等级: MEDIUM ℹ️
```

### 场景3: 访问蜜罐（确认恶意）

```
源IP: 192.168.10.50 (办公区)
目标IP: 10.0.99.50 (蜜罐网段)
攻击次数: 10
唯一端口: 1 (22)

计算:
  sourceWeight = 0.80
  targetWeight = 3.00 (蜜罐!)
  directionalWeight = 3.00 (访问蜜罐)
  
  threatScore = 10 × 1 × 1 
              × 1.0
              × 0.80 × 3.00 × 3.00
              × 1.0
              × 1.0
            = 72 × 3.00 = 216

  威胁等级: CRITICAL ⚠️⚠️⚠️
  行动: 立即隔离源IP!
```

---

## 🚀 实施计划

### 阶段1: 数据库迁移 ✅
- [x] 创建新表结构 `10-ip-segment-weights-v2.sql`
- [x] 创建方向性权重计算函数
- [x] 插入默认参考配置

### 阶段2: 服务层改造 🔄
- [ ] 修改 `IpSegmentWeightService.java`
  - 增加 `getWeightAsSource()` 方法
  - 增加 `getWeightAsTarget()` 方法
  - 增加 `calculateDirectionalWeight()` 方法
- [ ] 修改 `ThreatScoreCalculator.java`
  - 集成方向性权重计算
  - 调整威胁评分公式

### 阶段3: API开发 📋
- [ ] 网段权重管理API
  - `POST /api/v1/assessment/ip-segments` - 创建配置
  - `GET /api/v1/assessment/ip-segments` - 查询列表
  - `PUT /api/v1/assessment/ip-segments/{id}` - 更新配置
  - `DELETE /api/v1/assessment/ip-segments/{id}` - 删除配置
  - `POST /api/v1/assessment/ip-segments/import` - 批量导入
  - `GET /api/v1/assessment/ip-segments/export` - 导出配置

### 阶段4: 测试验证 🧪
- [ ] 单元测试：方向性权重计算
- [ ] 集成测试：不同场景的威胁评分
- [ ] 端到端测试：完整流程验证

### 阶段5: 文档和部署 📚
- [ ] API文档
- [ ] 客户配置指南
- [ ] 运维手册

---

## 📝 总结

### 核心改进

1. **✅ 多租户支持**: 每个客户可以定制自己的网段配置
2. **✅ 方向性权重**: 同一IP在不同场景下权重不同
3. **✅ 横向移动检测**: 基于内网蜜罐机制的正确理解
4. **✅ 蜜罐集成**: 访问蜜罐网段自动提权
5. **✅ 企业网络分区**: 符合零信任架构最佳实践

### 与原系统对齐

| 原系统 | V2.0 | 状态 |
|--------|------|------|
| 186个网段配置 | 客户自定义（默认12个区域模板） | ✅ 更灵活 |
| 单一IP权重 | 方向性权重（源/目标/方向） | ✅ 更精准 |
| 全局配置 | 多租户配置 | ✅ 更实用 |

---

**作者**: ThreatDetection Team  
**审核**: 待审核  
**批准**: 待批准
