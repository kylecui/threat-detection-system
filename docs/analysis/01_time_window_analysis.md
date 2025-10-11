# 时间窗口分析与优化建议

**问题**: 10分钟统计聚合是否合适？是否有更优的时间窗口？

**版本**: 1.0  
**日期**: 2025-10-11

---

## 1. 当前方案评估

### 1.1 原系统设计

- **聚合窗口**: 10分钟
- **基于**: 实践经验
- **原型**: 2分钟 (仅为测试方便)

### 1.2 10分钟窗口的优缺点

**优点** ✅:
- **数据平滑**: 减少噪音，避免单次扫描触发告警
- **资源效率**: 降低计算频率，减少系统负载
- **趋势稳定**: 足够长的时间观察攻击模式
- **经验验证**: 已在生产环境验证

**缺点** ⚠️:
- **响应延迟**: 最坏情况下延迟10分钟才能检测到威胁
- **快速攻击**: 无法及时检测闪电式攻击（如自动化勒索软件）
- **细节丢失**: 可能掩盖短时间内的高频攻击模式
- **APT检测**: 对于低频持续性攻击可能不够敏感

---

## 2. 威胁类型与时间窗口需求

### 2.1 不同威胁的时间特征

| 威胁类型 | 典型持续时间 | 攻击频率 | 理想检测窗口 |
|---------|-------------|---------|-------------|
| **自动化扫描工具** | 1-5分钟 | 极高 (100+次/分钟) | **30秒-2分钟** |
| **勒索软件传播** | 2-10分钟 | 高 (50+次/分钟) | **1-5分钟** |
| **人工渗透** | 10-60分钟 | 中 (5-20次/分钟) | **5-15分钟** |
| **APT横向移动** | 数小时-数天 | 低 (1-5次/分钟) | **15分钟-1小时** |
| **内部侦察** | 30分钟-2小时 | 低-中 (5-15次/分钟) | **10-30分钟** |

**关键洞察**: 
- 10分钟窗口对**勒索软件**检测较慢
- 对**APT攻击**来说可能过短（需要更长期趋势）

### 2.2 实际攻击案例时间线

**案例1: WannaCry勒索软件 (2017)**
```
T+0:00  → 初始感染
T+0:01  → 开始扫描445端口
T+0:03  → 横向移动到3-5台主机
T+0:05  → 感染10+台主机
T+0:10  → 大规模爆发 (已经太晚)
```
**结论**: 10分钟窗口对快速传播的勒索软件**太慢**

**案例2: APT29 (Cozy Bear)**
```
Day 1   → 初始立足点建立
Day 2-3 → 内网侦察 (低频扫描)
Day 4-7 → 横向移动 (每天2-3次尝试)
Week 2  → 权限提升
Week 3+ → 数据窃取
```
**结论**: 10分钟窗口对APT攻击**太短**，需要跨天分析

---

## 3. 推荐方案: 多层级时间窗口架构

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│           多层级时间窗口检测架构                         │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  实时层 (30秒)                                    │  │
│  │  - 检测: 自动化扫描、快速勒索软件                 │  │
│  │  - 阈值: 极高频率 (50+次/分钟)                    │  │
│  │  - 告警: CRITICAL (立即响应)                      │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↓                              │
│  ┌──────────────────────────────────────────────────┐  │
│  │  短期层 (5分钟)                                   │  │
│  │  - 检测: 勒索软件、快速横向移动                   │  │
│  │  - 阈值: 高频率 (20+次/分钟)                      │  │
│  │  - 告警: HIGH (快速响应)                          │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↓                              │
│  ┌──────────────────────────────────────────────────┐  │
│  │  中期层 (15分钟) ← 主检测层                       │  │
│  │  - 检测: 人工渗透、系统性扫描                     │  │
│  │  - 阈值: 中频率 (5-20次/分钟)                     │  │
│  │  - 告警: MEDIUM/HIGH (常规响应)                   │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↓                              │
│  ┌──────────────────────────────────────────────────┐  │
│  │  长期层 (1小时)                                   │  │
│  │  - 检测: 持续性扫描、慢速APT                      │  │
│  │  - 阈值: 低频率 (1-5次/分钟)                      │  │
│  │  - 告警: MEDIUM (持续监控)                        │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↓                              │
│  ┌──────────────────────────────────────────────────┐  │
│  │  趋势层 (24小时)                                  │  │
│  │  - 检测: APT演变、长期趋势                        │  │
│  │  - 阈值: 模式变化                                 │  │
│  │  - 告警: INFO/MEDIUM (趋势分析)                   │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 3.2 各层级详细配置

#### 层级1: 实时层 (30秒窗口)

**目标**: 捕获极高频自动化攻击

**检测逻辑**:
```java
// 30秒窗口内
if (probeCount > 50 && uniquePorts > 5) {
    // 自动化扫描工具 (如Metasploit, Nmap快速扫描)
    return ThreatLevel.CRITICAL;
}

if (targetPorts.contains(445) && probeCount > 30) {
    // 疑似勒索软件传播 (SMB扫描)
    return ThreatLevel.CRITICAL;
}
```

**示例场景**:
- WannaCry在30秒内扫描445端口50+次
- Metasploit自动化模块快速端口扫描

**告警策略**: 立即告警 + 自动隔离建议

#### 层级2: 短期层 (5分钟窗口)

**目标**: 检测快速横向移动

**检测逻辑**:
```java
// 5分钟窗口内
if (honeypotsAccessed > 5 && hasCriticalPorts(targetPorts)) {
    // 快速横向移动
    threatScore *= 2.0;  // 加速乘数
    return ThreatLevel.HIGH;
}

if (probesPerMinute > 20 && persistsOver(300)) {  // 持续5分钟
    // 持续高频攻击
    return ThreatLevel.HIGH;
}
```

**示例场景**:
- 勒索软件在5分钟内感染5+主机
- 蠕虫病毒快速传播

**告警策略**: 快速告警 + 网络隔离建议

#### 层级3: 中期层 (15分钟窗口) ⭐ 主检测层

**目标**: 平衡检测速度与准确性

**为什么选择15分钟而非10分钟？**

1. **威胁覆盖更全面**:
   - 10分钟: 可能错过12-15分钟的持续攻击模式
   - 15分钟: 覆盖大部分人工渗透和系统性扫描

2. **数据更稳定**:
   - 更长的窗口提供更可靠的统计特征
   - 减少瞬时网络波动影响

3. **APT初期检测**:
   - 15分钟内3-5次尝试 = 低频持续性攻击
   - 10分钟可能太短，无法观察到模式

**检测逻辑**:
```java
// 15分钟窗口内
threatScore = (probeCount × honeypotsAccessed × targetPortsDiversity)
            × timeWeight
            × honeypotSpreadWeight
            × attackIntentWeight
            × deviceCoverageWeight
            × persistenceWeight;

// 根据分数分级
if (threatScore > 200) return ThreatLevel.CRITICAL;
if (threatScore > 100) return ThreatLevel.HIGH;
if (threatScore > 50) return ThreatLevel.MEDIUM;
return ThreatLevel.LOW;
```

**示例场景**:
- 人工渗透测试 (15分钟内10-20次探测)
- 系统性内网扫描

**告警策略**: 常规告警 + 详细分析报告

#### 层级4: 长期层 (1小时窗口)

**目标**: 检测低频持续性攻击

**检测逻辑**:
```java
// 1小时窗口内
if (attackSessions > 3 && averageProbesPerSession < 10) {
    // 低频多会话攻击 (典型APT)
    persistenceScore = attackSessions * sessionDiversity;
    
    if (persistenceScore > threshold) {
        return ThreatLevel.MEDIUM;  // 需要长期监控
    }
}
```

**示例场景**:
- APT攻击者每15分钟尝试1-2次探测
- 慢速扫描工具 (如unicornscan慢速模式)

**告警策略**: 趋势告警 + 长期监控标记

#### 层级5: 趋势层 (24小时窗口)

**目标**: 发现攻击演变和长期模式

**检测逻辑**:
```java
// 24小时内的行为变化
DailyBehaviorProfile profile = analyzeDailyPattern(attackMac);

if (profile.showsEscalation()) {
    // 攻击强度逐步增加
    // 例如: 00:00-08:00 零星探测 → 08:00-16:00 增加到中频 → 16:00-24:00 高频
    return ThreatLevel.MEDIUM;
}

if (profile.showsPeriodicPattern()) {
    // 定期攻击模式 (每6小时一次)
    // 典型APT特征: 避开监控高峰期
    return ThreatLevel.MEDIUM;
}
```

**示例场景**:
- APT攻击逐步升级
- 定时任务驱动的恶意软件

**告警策略**: 趋势报告 + 深度分析建议

---

## 4. 实施方案

### 4.1 Flink流处理实现

```java
// 多窗口并行处理
DataStream<AttackEvent> events = ...;

// 30秒窗口 - 实时层
DataStream<ThreatAlert> realtimeAlerts = events
    .keyBy(event -> event.getCustomerId() + ":" + event.getAttackMac())
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .process(new RealtimeDetectionFunction());

// 5分钟窗口 - 短期层
DataStream<ThreatAlert> shortTermAlerts = events
    .keyBy(event -> event.getCustomerId() + ":" + event.getAttackMac())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .process(new ShortTermDetectionFunction());

// 15分钟窗口 - 中期层 (主检测)
DataStream<ThreatAlert> midTermAlerts = events
    .keyBy(event -> event.getCustomerId() + ":" + event.getAttackMac())
    .window(TumblingEventTimeWindows.of(Time.minutes(15)))
    .process(new MidTermDetectionFunction());

// 1小时窗口 - 长期层
DataStream<ThreatAlert> longTermAlerts = events
    .keyBy(event -> event.getCustomerId() + ":" + event.getAttackMac())
    .window(TumblingEventTimeWindows.of(Time.hours(1)))
    .process(new LongTermDetectionFunction());

// 24小时滑动窗口 - 趋势层
DataStream<ThreatAlert> trendAlerts = events
    .keyBy(event -> event.getCustomerId() + ":" + event.getAttackMac())
    .window(SlidingEventTimeWindows.of(Time.hours(24), Time.hours(1)))
    .process(new TrendAnalysisFunction());

// 合并所有层级告警
DataStream<ThreatAlert> allAlerts = realtimeAlerts
    .union(shortTermAlerts, midTermAlerts, longTermAlerts, trendAlerts);
```

### 4.2 告警去重与优先级

**问题**: 同一攻击者可能在多个窗口触发告警

**解决方案**: 告警去重与优先级升级

```java
public class AlertDeduplicationFunction 
    extends KeyedProcessFunction<String, ThreatAlert, ThreatAlert> {
    
    // 状态: 最近24小时的告警
    private MapState<String, ThreatAlert> recentAlerts;
    
    @Override
    public void processElement(ThreatAlert alert, Context ctx, Collector<ThreatAlert> out) {
        String alertKey = alert.getCustomerId() + ":" + alert.getAttackMac();
        
        // 检查是否有更高级别的现有告警
        ThreatAlert existingAlert = recentAlerts.get(alertKey);
        
        if (existingAlert != null) {
            if (alert.getThreatLevel().ordinal() > existingAlert.getThreatLevel().ordinal()) {
                // 升级告警
                alert.setAlertType("ESCALATED");
                alert.setPreviousLevel(existingAlert.getThreatLevel());
                out.collect(alert);
            } else if (alert.getWindowType().equals("TREND")) {
                // 趋势告警总是发送 (用于长期监控)
                out.collect(alert);
            }
            // 否则去重,不发送
        } else {
            // 新告警
            out.collect(alert);
        }
        
        // 更新状态
        recentAlerts.put(alertKey, alert);
        
        // 设置清理定时器 (24小时后)
        ctx.timerService().registerEventTimeTimer(
            ctx.timestamp() + TimeUnit.HOURS.toMillis(24)
        );
    }
}
```

---

## 5. 对比分析

### 5.1 单窗口 vs 多窗口

| 维度 | 单窗口 (10分钟) | 多窗口 (30秒-24小时) |
|------|----------------|---------------------|
| **快速威胁检测** | ⚠️ 延迟10分钟 | ✅ 30秒检测 |
| **APT长期追踪** | ❌ 窗口太短 | ✅ 24小时趋势 |
| **误报率** | ✅ 较低 | ⚠️ 需去重逻辑 |
| **资源消耗** | ✅ 低 | ⚠️ 中-高 (5个窗口) |
| **威胁覆盖** | ⚠️ 中等 | ✅ 全面 |
| **实施复杂度** | ✅ 简单 | ⚠️ 复杂 |

### 5.2 性能影响评估

**假设**: 1000 events/s 吞吐量

| 窗口配置 | 状态大小 | CPU负载 | 内存消耗 | 告警延迟 |
|---------|---------|---------|---------|---------|
| 单窗口 10分钟 | ~600K events | 低 | 2GB | 10分钟 |
| 多窗口 (5层) | ~3M events | 中-高 | 8GB | 30秒-24小时 |

**优化建议**:
1. 使用RocksDB状态后端 (减少内存压力)
2. 短窗口只计算关键指标 (减少计算量)
3. 长窗口使用抽样 (减少数据量)

---

## 6. 推荐配置

### 6.1 生产环境建议

**阶段1: 渐进式部署** (推荐)

```yaml
phase_1:  # 第1-2周
  windows:
    - 15分钟 (主窗口, 替代原10分钟)
  reason: 比10分钟覆盖更全,延迟可接受
  
phase_2:  # 第3-4周
  windows:
    - 15分钟 (主窗口)
    - 5分钟 (快速威胁)
  reason: 添加勒索软件快速检测
  
phase_3:  # 第5-8周
  windows:
    - 30秒 (实时)
    - 5分钟 (快速)
    - 15分钟 (主窗口)
    - 1小时 (长期)
    - 24小时 (趋势)
  reason: 完整多层级架构
```

### 6.2 不同场景的窗口选择

**场景1: 高安全性环境 (金融、医疗)**
- 主窗口: **5分钟** (快速响应)
- 辅助: 30秒实时层
- 理由: 勒索软件风险高,需要极快检测

**场景2: 一般企业环境**
- 主窗口: **15分钟** (平衡性能与检测)
- 辅助: 5分钟快速层 + 1小时长期层
- 理由: 平衡成本与安全

**场景3: 研究/测试环境**
- 主窗口: **10分钟** (原方案)
- 理由: 低风险,资源有限

---

## 7. 总结与建议

### 7.1 关键结论

1. **10分钟窗口并非最优**:
   - 对勒索软件太慢 (应5分钟或更短)
   - 对APT太短 (需要小时-天级别)

2. **15分钟是更好的主窗口**:
   - 比10分钟覆盖更全面
   - 对大多数威胁提供合理响应时间
   - 数据更稳定,误报更少

3. **多层级窗口是最佳方案**:
   - 覆盖从秒级到天级的威胁
   - 不同威胁不同响应速度
   - 符合安全运营最佳实践

### 7.2 实施建议

**立即行动** (P0):
- ✅ 将主窗口从10分钟调整为15分钟

**近期实施** (P1, 1-2月):
- ⚠️ 添加5分钟快速检测层
- ⚠️ 添加1小时长期监控层

**长期规划** (P2, 3-6月):
- ⏳ 实现完整的5层窗口架构
- ⏳ 添加告警去重与优先级机制
- ⏳ 实现24小时趋势分析

### 7.3 度量指标

**成功标准**:
- 勒索软件检测时间: < 5分钟 (当前可能10分钟+)
- APT检测率: +30% (通过长期窗口)
- 误报率: 保持不变或降低
- 系统资源: CPU < 80%, Memory < 16GB

---

**文档结束**
