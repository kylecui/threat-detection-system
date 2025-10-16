# 基于蜜罐机制的威胁评分方案

**版本**: 2.0  
**日期**: 2025-10-11  
**更新原因**: 澄清蜜罐/诱饵机制工作原理

---

## 1. 蜜罐机制工作原理

### 1.1 终端设备功能

终端设备 (`dev_serial`) 在二层网络中执行:

1. **网络监控**: 收集本地网络设备在线情况
2. **诱饵部署**: 识别未使用IP作为虚拟哨兵 (诱饵)
3. **主动诱导**: 响应 ARP/ICMP 请求，诱导攻击者进一步行动
4. **行为记录**: 记录攻击者对诱饵IP的端口访问尝试 (不再响应)

### 1.2 数据字段语义

| 字段 | 含义 | 说明 |
|------|------|------|
| `attack_mac` | **被诱捕者MAC地址** | 内网中潜在的攻击者/失陷主机 |
| `attack_ip` | **被诱捕者IP地址** | 攻击者的真实IP (内网地址) |
| `response_ip` | **诱饵IP** | **不存在的虚拟哨兵IP** |
| `response_port` | **攻击者尝试访问的端口** | 暴露攻击意图 |
| `dev_serial` | **部署设备标识** | 二层网络监控设备 |

### 1.3 威胁模型

**这是内网横向移动检测系统**:

- ✅ 检测已进入内网的威胁 (APT、勒索软件、内鬼)
- ✅ 识别横向移动行为 (扫描、探测)
- ✅ 捕获攻击意图 (端口选择暴露目标)
- ❌ **不是**外部防火墙/IDS系统

**典型攻击场景**:

```
阶段1: 攻击者已通过某种方式进入内网 (钓鱼邮件、供应链攻击等)
阶段2: 失陷主机开始扫描内网寻找高价值目标
       ↓
       尝试访问 10.0.0.50 (诱饵) → ARP请求
       ↓
       终端设备响应 → 诱导攻击者继续
       ↓
       攻击者尝试: 10.0.0.50:3389 (RDP)
                  10.0.0.50:445 (SMB)
                  10.0.0.50:3306 (MySQL)
       ↓
       终端设备记录但不响应 → 生成攻击日志
```

---

## 2. 威胁评分算法修正

### 2.1 核心洞察

**蜜罐系统的特殊性**:

1. **所有访问都是恶意的**: 诱饵IP不存在，任何访问都是异常行为
2. **端口选择暴露意图**: 攻击者尝试的端口反映其攻击目标
3. **扫描广度反映威胁**: 尝试访问多个诱饵IP = 大规模横向移动
4. **时间模式重要**: 深夜扫描 = 隐蔽性攻击

### 2.2 修正后的评分公式

```java
threatScore = (attackCount × honeypotsAccessed × targetPortsDiversity) 
            × timeWeight 
            × honeypotSpreadWeight 
            × attackIntentWeight 
            × deviceCoverageWeight
            × persistenceWeight
```

**公式解读**:

| 因子 | 原名称 | 新名称 | 语义变化 |
|------|--------|--------|---------|
| `attackCount` | 攻击次数 | **探测次数** | 对诱饵的访问尝试次数 |
| `uniqueIps` | 唯一IP数 | **honeypotsAccessed** | 访问的诱饵IP数量 |
| `uniquePorts` | 唯一端口数 | **targetPortsDiversity** | 探测的端口种类 |
| `timeWeight` | 时间权重 | **timeWeight** | 保持不变 |
| `ipWeight` | IP权重 | **honeypotSpreadWeight** | 诱饵扩散权重 |
| `portWeight` | 端口权重 | **attackIntentWeight** | 攻击意图权重 |
| `deviceWeight` | 设备权重 | **deviceCoverageWeight** | 设备覆盖权重 |
| (新增) | - | **persistenceWeight** | 持久性权重 |

### 2.3 权重计算详解

#### 2.3.1 诱饵扩散权重 (honeypotSpreadWeight)

**含义**: 攻击者扫描的诱饵IP范围

```java
private double calculateHoneypotSpreadWeight(int honeypotsAccessed) {
    if (honeypotsAccessed == 1) return 1.0;      // 单个诱饵
    if (honeypotsAccessed <= 3) return 1.5;      // 小范围扫描
    if (honeypotsAccessed <= 5) return 2.0;      // 中等扫描
    if (honeypotsAccessed <= 10) return 2.5;     // 大范围扫描
    return 3.0;                                  // 全网扫描
}
```

**理由**: 
- 访问多个诱饵IP = 系统性扫描行为
- 典型APT/勒索软件特征
- 比原系统更高权重，因为这是**确认的恶意行为**

#### 2.3.2 攻击意图权重 (attackIntentWeight)

**含义**: 基于目标端口推断攻击意图严重性

```java
private double calculateAttackIntentWeight(Set<Integer> targetPorts) {
    double maxIntentScore = 0.0;
    
    // 关键端口意图分析
    Map<Integer, Double> portIntentMap = Map.of(
        3389, 3.0,  // RDP - 远程控制意图 (最高危)
        445,  3.0,  // SMB - 横向移动/勒索软件传播
        22,   2.5,  // SSH - 远程登录
        3306, 2.5,  // MySQL - 数据窃取
        5432, 2.5,  // PostgreSQL - 数据窃取
        1433, 2.5,  // MSSQL - 数据窃取
        6379, 2.0,  // Redis - 数据/权限窃取
        27017, 2.0, // MongoDB - 数据窃取
        135,  2.0,  // RPC - Windows渗透
        139,  2.0,  // NetBIOS - 横向移动
        443,  1.5,  // HTTPS - Web攻击
        80,   1.5,  // HTTP - Web攻击
        21,   1.5,  // FTP - 文件窃取
        23,   2.0   // Telnet - 远程登录
    );
    
    // 找出最严重的意图
    for (int port : targetPorts) {
        maxIntentScore = Math.max(maxIntentScore, 
                                  portIntentMap.getOrDefault(port, 1.0));
    }
    
    // 多端口组合提升权重
    int portCount = targetPorts.size();
    double diversityMultiplier = 1.0;
    if (portCount > 5) diversityMultiplier = 1.3;      // 广泛扫描
    else if (portCount > 10) diversityMultiplier = 1.5; // 全端口扫描
    
    return maxIntentScore * diversityMultiplier;
}
```

**关键改变**:
- ✅ 端口权重更高 (3.0 vs 原系统10.0，但乘数效应更强)
- ✅ 组合检测: 高危端口 + 多端口扫描 = 更高分
- ✅ 反映真实威胁: RDP/SMB是勒索软件的典型目标

#### 2.3.3 持久性权重 (persistenceWeight) - 新增

**含义**: 攻击者在时间维度上的持续性

```java
private double calculatePersistenceWeight(
    int attackCount, 
    Duration timeWindow
) {
    // 计算每分钟攻击频率
    double attacksPerMinute = attackCount / (timeWindow.toMinutes() + 1.0);
    
    if (attacksPerMinute > 50) return 2.0;   // 自动化扫描工具
    if (attacksPerMinute > 20) return 1.7;   // 高频扫描
    if (attacksPerMinute > 10) return 1.5;   // 中频扫描
    if (attacksPerMinute > 5) return 1.3;    // 低频扫描
    return 1.0;                              // 零星尝试
}
```

**理由**: 
- 自动化工具 (如勒索软件) 会持续高频扫描
- 人工渗透通常频率较低
- 持久性攻击威胁更大

#### 2.3.4 设备覆盖权重 (deviceCoverageWeight)

**含义**: 攻击者是否在多个网络位置活动

```java
private double calculateDeviceCoverageWeight(int uniqueDevices) {
    // uniqueDevices = 检测到该攻击者的终端设备数量
    if (uniqueDevices == 1) return 1.0;      // 单一网络位置
    if (uniqueDevices <= 3) return 1.8;      // 跨子网活动
    return 2.5;                              // 全网活动
}
```

**理由**: 
- 多个终端设备检测到同一攻击者 = 跨网段移动
- APT典型特征
- 比原系统权重更高 (1.8-2.5 vs 1.0-1.5)

#### 2.3.5 时间权重 (timeWeight)

**保持原有逻辑**，但加强深夜权重:

```java
private double calculateTimeWeight(Instant timestamp) {
    LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
    int hour = time.getHour();
    
    if (hour >= 0 && hour < 6) return 1.5;      // 深夜 (提高)
    if (hour >= 6 && hour < 9) return 1.2;      // 早晨
    if (hour >= 9 && hour < 17) return 1.0;     // 工作时间
    if (hour >= 17 && hour < 21) return 1.1;    // 傍晚
    return 1.3;                                 // 夜间
}
```

**改变理由**:
- 深夜扫描更可疑 (内网横向移动通常在非工作时间)
- 从1.2提高到1.5

---

## 3. 数据结构修正

### 3.1 AttackEvent 模型

**修正前**:
```java
public class AttackEvent {
    private String attackMac;       // 攻击者MAC
    private String attackIp;        // 攻击者IP
    private String responseIp;      // 响应IP (误解为服务器IP)
    private int responsePort;       // 响应端口
}
```

**修正后**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackEvent {
    // === 攻击者信息 (内网失陷主机) ===
    private String attackMac;           // 被诱捕者MAC地址
    private String attackIp;            // 被诱捕者IP地址
    
    // === 诱饵信息 (蜜罐) ===
    private String honeypotIp;          // 诱饵IP (原 responseIp)
    private int targetPort;             // 攻击者尝试访问的端口 (原 responsePort)
    
    // === 协议信息 ===
    private String ethType;             // 以太网类型
    private String ipType;              // IP协议类型
    
    // === 设备和租户信息 ===
    private String deviceSerial;        // 终端设备序列号
    private String customerId;          // 客户ID
    
    // === 时间信息 ===
    private Instant timestamp;          // 事件时间戳
    private long logTime;               // 日志时间 (Unix时间戳)
    
    // === 元数据 ===
    private String eventType;           // 事件类型 (HONEYPOT_ACCESS)
    private boolean isFirstContact;     // 是否首次接触诱饵
}
```

**关键变化**:
1. `responseIp` → `honeypotIp` (语义明确)
2. `responsePort` → `targetPort` (强调攻击意图)
3. 新增 `eventType` (区分事件类型)
4. 新增 `isFirstContact` (首次接触标记)

### 3.2 AggregatedAttackData 模型

**修正后**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedAttackData {
    // === 聚合键 ===
    private String customerId;          // 客户ID
    private String attackMac;           // 攻击者MAC
    private String attackIp;            // 攻击者IP
    
    // === 诱饵访问统计 ===
    private int honeypotsAccessed;      // 访问的诱饵IP数量 (原 uniqueIps)
    private Set<String> honeypotList;   // 诱饵IP列表
    
    // === 端口探测统计 ===
    private int targetPortsDiversity;   // 探测的端口种类 (原 uniquePorts)
    private Set<Integer> targetPortList;// 目标端口列表
    
    // === 设备覆盖统计 ===
    private int devicesCovered;         // 检测设备数量 (原 uniqueDevices)
    private Set<String> deviceList;     // 设备列表
    
    // === 攻击行为统计 ===
    private int probeCount;             // 探测次数 (原 attackCount)
    private int firstContactCount;      // 首次接触诱饵次数
    
    // === 时间信息 ===
    private Instant windowStart;        // 窗口开始时间
    private Instant windowEnd;          // 窗口结束时间
    private Instant timestamp;          // 聚合时间戳
    
    // === 持久性分析 ===
    private double probesPerMinute;     // 每分钟探测次数
    private Duration activeDuration;    // 活跃时长
}
```

### 3.3 ThreatAlert 模型

**修正后**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAlert {
    // === 基本信息 ===
    private String alertId;             // 告警ID
    private String customerId;          // 客户ID
    
    // === 攻击者信息 ===
    private String attackMac;           // 攻击者MAC
    private String attackIp;            // 攻击者IP
    
    // === 威胁评分 ===
    private double threatScore;         // 威胁总分
    private ThreatLevel threatLevel;    // 威胁等级
    
    // === 攻击特征 ===
    private int honeypotsAccessed;      // 访问的诱饵数量
    private int targetPortsDiversity;   // 目标端口多样性
    private int probeCount;             // 探测次数
    private int devicesCovered;         // 覆盖设备数
    
    // === 攻击意图分析 ===
    private AttackIntent primaryIntent; // 主要攻击意图 (新增)
    private List<Integer> criticalPorts;// 关键端口列表 (新增)
    
    // === 权重详情 ===
    private double timeWeight;
    private double honeypotSpreadWeight;
    private double attackIntentWeight;
    private double deviceCoverageWeight;
    private double persistenceWeight;
    
    // === 时间信息 ===
    private Instant firstSeen;          // 首次发现时间
    private Instant lastSeen;           // 最后发现时间
    private Instant timestamp;          // 告警时间
}

// 攻击意图枚举
public enum AttackIntent {
    REMOTE_CONTROL,      // 远程控制 (RDP, SSH, Telnet)
    LATERAL_MOVEMENT,    // 横向移动 (SMB, RPC)
    DATA_EXFILTRATION,   // 数据窃取 (MySQL, PostgreSQL, FTP)
    RECONNAISSANCE,      // 侦察扫描 (多端口低频)
    AUTOMATED_ATTACK,    // 自动化攻击 (高频多端口)
    UNKNOWN              // 未知意图
}
```

### 3.4 PostgreSQL表结构

**修正后的 threat_assessments 表**:

```sql
CREATE TABLE threat_assessments (
    id SERIAL PRIMARY KEY,
    alert_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- 租户和攻击者信息
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45) NOT NULL,
    
    -- 威胁评分
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    
    -- 蜜罐攻击统计
    honeypots_accessed INTEGER NOT NULL,           -- 访问的诱饵IP数量
    target_ports_diversity INTEGER NOT NULL,       -- 目标端口多样性
    probe_count INTEGER NOT NULL,                  -- 探测次数
    devices_covered INTEGER NOT NULL,              -- 覆盖设备数
    
    -- 攻击意图分析
    primary_attack_intent VARCHAR(30),             -- 主要攻击意图
    critical_ports TEXT,                           -- 关键端口JSON数组
    
    -- 权重详情
    time_weight DECIMAL(5,2),
    honeypot_spread_weight DECIMAL(5,2),
    attack_intent_weight DECIMAL(5,2),
    device_coverage_weight DECIMAL(5,2),
    persistence_weight DECIMAL(5,2),
    
    -- 持久性分析
    probes_per_minute DECIMAL(8,2),                -- 每分钟探测次数
    active_duration_seconds INTEGER,               -- 活跃时长(秒)
    
    -- 时间信息
    first_seen TIMESTAMP NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    assessment_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_mac (customer_id, attack_mac),
    INDEX idx_assessment_time (assessment_time),
    INDEX idx_threat_level (threat_level),
    INDEX idx_attack_intent (primary_attack_intent),
    INDEX idx_first_seen (first_seen)
);

-- 蜜罐访问详情表 (新增)
CREATE TABLE honeypot_access_details (
    id SERIAL PRIMARY KEY,
    alert_id VARCHAR(50) NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    
    honeypot_ip VARCHAR(45) NOT NULL,              -- 诱饵IP
    target_port INTEGER NOT NULL,                  -- 目标端口
    access_count INTEGER NOT NULL,                 -- 访问次数
    first_access TIMESTAMP NOT NULL,
    last_access TIMESTAMP NOT NULL,
    
    FOREIGN KEY (alert_id) REFERENCES threat_assessments(alert_id),
    INDEX idx_alert (alert_id),
    INDEX idx_honeypot (customer_id, honeypot_ip)
);
```

---

## 4. 威胁等级重新划分

基于蜜罐特性，重新定义威胁等级:

| 等级 | 分数范围 | 特征 | 典型场景 |
|------|---------|------|---------|
| **INFO** | < 10 | 零星探测 | 单个诱饵，1-2个端口，低频 |
| **LOW** | 10-50 | 初步扫描 | 单个诱饵，3-5个端口 |
| **MEDIUM** | 50-150 | 系统扫描 | 2-3个诱饵，多端口探测 |
| **HIGH** | 150-300 | 横向移动 | 5+诱饵，包含高危端口 (RDP/SMB) |
| **CRITICAL** | > 300 | APT级威胁 | 10+诱饵，全端口扫描，高频持续 |

**判定逻辑**:

```java
private ThreatLevel determineThreatLevel(
    double threatScore,
    int honeypotsAccessed,
    Set<Integer> targetPorts
) {
    // 关键端口检测
    boolean hasCriticalPorts = targetPorts.stream()
        .anyMatch(p -> p == 3389 || p == 445 || p == 22);
    
    // 综合判定
    if (threatScore > 300 || 
        (honeypotsAccessed > 10 && hasCriticalPorts)) {
        return ThreatLevel.CRITICAL;  // APT级
    }
    
    if (threatScore > 150 || 
        (honeypotsAccessed > 5 && hasCriticalPorts)) {
        return ThreatLevel.HIGH;      // 横向移动
    }
    
    if (threatScore > 50 || honeypotsAccessed > 3) {
        return ThreatLevel.MEDIUM;    // 系统扫描
    }
    
    if (threatScore > 10) {
        return ThreatLevel.LOW;       // 初步扫描
    }
    
    return ThreatLevel.INFO;          // 零星探测
}
```

---

## 5. 评分示例对比

### 场景1: 勒索软件横向移动

**攻击行为**:
- 攻击者: 192.168.1.100
- 访问诱饵: 10.0.0.50, 10.0.0.51, 10.0.0.52 (3个)
- 目标端口: 3389 (RDP), 445 (SMB)
- 探测次数: 120次 (2分钟窗口)
- 时间: 凌晨2:00

**修正前评分**:
```
threatScore = (120 × 3 × 2) × 1.2 × 1.3 × 1.2 × 1.0
            = 720 × 1.872
            = 1347.84
```

**修正后评分**:
```
honeypotsAccessed = 3
targetPortsDiversity = 2
probeCount = 120
probesPerMinute = 60

honeypotSpreadWeight = 1.5      (3个诱饵)
attackIntentWeight = 3.0 × 1.0  (RDP/SMB高危，2端口)
persistenceWeight = 2.0          (60次/分钟)
timeWeight = 1.5                 (凌晨)
deviceCoverageWeight = 1.0       (单设备)

threatScore = (120 × 3 × 2) × 1.5 × 1.5 × 3.0 × 1.0 × 2.0
            = 720 × 13.5
            = 9720

威胁等级: CRITICAL ✅
```

**分析**: 修正后评分更高，准确反映勒索软件威胁

### 场景2: 零星探测

**攻击行为**:
- 访问诱饵: 10.0.0.60 (1个)
- 目标端口: 80 (HTTP)
- 探测次数: 5次
- 时间: 下午3:00

**修正后评分**:
```
honeypotSpreadWeight = 1.0      (单个诱饵)
attackIntentWeight = 1.5        (HTTP低危)
persistenceWeight = 1.0          (低频)
timeWeight = 1.0                 (工作时间)
deviceCoverageWeight = 1.0       (单设备)

threatScore = (5 × 1 × 1) × 1.0 × 1.0 × 1.5 × 1.0 × 1.0
            = 5 × 1.5
            = 7.5

威胁等级: INFO ✅
```

---

## 6. 实施路线图

### 阶段1: 数据模型修正 (1-2天)

- [ ] 修改 `AttackEvent.java` 字段命名
- [ ] 修改 `AggregatedAttackData.java` 语义
- [ ] 修改 `ThreatAlert.java` 添加意图分析
- [ ] 创建 `AttackIntent` 枚举
- [ ] 更新 PostgreSQL 表结构

### 阶段2: 评分算法更新 (2-3天)

- [ ] 重构 `ThreatScoreCalculator`
- [ ] 实现 `calculateHoneypotSpreadWeight()`
- [ ] 实现 `calculateAttackIntentWeight()`
- [ ] 实现 `calculatePersistenceWeight()`
- [ ] 更新 `calculateTimeWeight()` 权重值
- [ ] 实现 `determineThreatLevel()` 逻辑

### 阶段3: 聚合逻辑调整 (2-3天)

- [ ] 修改 `AttackAggregationProcessFunction`
- [ ] 添加持久性分析 (probesPerMinute)
- [ ] 添加首次接触标记
- [ ] 收集完整端口列表和诱饵列表

### 阶段4: 文档更新 (1天)

- [ ] 更新所有文档反映蜜罐机制
- [ ] 更新 API 文档
- [ ] 更新配置说明

### 阶段5: 测试验证 (2-3天)

- [ ] 单元测试更新
- [ ] 集成测试场景
- [ ] 性能测试
- [ ] 与原系统对比验证

---

## 7. 与原系统对齐检查

| 维度 | 原系统 | 修正前理解 | 修正后理解 | 状态 |
|------|--------|-----------|-----------|------|
| **威胁来源** | 内网失陷主机 | ❌ 外部攻击者 | ✅ 内网失陷主机 | 已修正 |
| **response_ip** | 诱饵IP | ❌ 服务器IP | ✅ 诱饵IP | 已修正 |
| **response_port** | 攻击意图 | ❌ 服务端口 | ✅ 攻击意图 | 已修正 |
| **端口权重** | 意图严重性 | ⚠️ 多样性 | ✅ 意图严重性 | 已修正 |
| **IP多样性** | 横向移动 | ⚠️ 攻击范围 | ✅ 横向移动 | 已修正 |
| **威胁模型** | APT/勒索软件 | ❌ DDoS/扫描 | ✅ APT/勒索软件 | 已修正 |

---

**文档结束**
