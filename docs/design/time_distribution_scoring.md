# 时间分布对威胁评分的影响分析

**文档版本**: 1.0  
**创建日期**: 2025-10-27  
**作者**: 系统架构团队  
**状态**: 🚧 设计方案 (待实施)

---

## 📋 文档概述

本文档详细说明了威胁检测系统中**时间分布权重**的设计方案，这是一个关键的威胁检测维度，用于区分爆发式攻击和持续扫描行为。

**核心问题**: 当前评分算法完全忽略了事件的时间分布特征，导致无法区分勒索软件的快速横向扫描和常规的端口扫描。

---

## 🎯 核心问题

### 用户关键洞察

> "相同数量的攻击事件，如果集中在一点（非常短的时间段）和平均分布在完整的时间窗口，这一定是不同的行为，应该有不同的评分。而多数评分（真实环境）应该是这两种情况中间移动。"

### 场景对比

#### 场景A: 集中爆发攻击（勒索软件特征）

```
5分钟窗口 [10:00:00, 10:05:00):
┌─────────────────────────────────────────────────┐
│ 10:00:00 ████████████████████ 100条事件         │
│ 10:00:03 ✅ 3秒内完成                           │
│ 10:00:04 - 10:05:00: 沉默期（无事件）          │
│                                                 │
│ 时间跨度: 3秒 (窗口的1%)                        │
│ 爆发强度: 33.3 events/s                        │
│ 威胁特征: 🔴 勒索软件横向扫描                  │
└─────────────────────────────────────────────────┘
```

#### 场景B: 均匀分布攻击（正常扫描）

```
5分钟窗口 [10:00:00, 10:05:00):
┌─────────────────────────────────────────────────┐
│ 10:00:00 ▌ 1条                                  │
│ 10:00:03 ▌ 1条                                  │
│ 10:00:06 ▌ 1条                                  │
│ ...     ... (每3秒1条)                          │
│ 10:04:57 ▌ 1条                                  │
│                                                 │
│ 时间跨度: 297秒 (窗口的99%)                     │
│ 爆发强度: 0.33 events/s                         │
│ 威胁特征: 🟡 常规端口扫描                      │
└─────────────────────────────────────────────────┘
```

### 当前评分算法的问题

```java
// 当前公式
threatScore = (attackCount × uniqueIps × uniquePorts)
            × timeWeight × ipWeight × portWeight × deviceWeight

场景A: score = (100 × 50 × 10) × weights = 730,400
场景B: score = (100 × 50 × 10) × weights = 730,400 ❌

问题: 完全忽略了时间分布特征！
```

**结果**: 勒索软件的快速横向扫描和常规端口扫描得到相同的威胁评分。

---

## 📊 时间分布指标设计

### 指标1: 爆发强度系数 (Burst Intensity Coefficient) ⭐ 推荐

#### 定义

```
BIC = 1 - (事件时间跨度 / 窗口大小)

其中:
  timeSpan = max(timestamp) - min(timestamp)  # 事件的实际时间跨度
  windowSize = 窗口大小
  
取值范围: [0, 1]
  - BIC = 1: 所有事件集中在一个时间点（最高爆发）
  - BIC = 0: 事件均匀分布在整个窗口（无爆发）
  - BIC = 0.5: 事件分布在窗口的一半
```

#### 实际计算示例

**场景A: 集中爆发**

```
窗口: [10:00:00, 10:05:00), windowSize = 300秒
事件: 100条在 [10:00:00, 10:00:03]

timeSpan = 10:00:03 - 10:00:00 = 3秒
BIC = 1 - (3 / 300) = 1 - 0.01 = 0.99 ✅

解释: 99%的时间窗口是空闲的，攻击高度集中
```

**场景B: 均匀分布**

```
窗口: [10:00:00, 10:05:00), windowSize = 300秒
事件: 100条均匀分布在 [10:00:00, 10:04:57]

timeSpan = 10:04:57 - 10:00:00 = 297秒
BIC = 1 - (297 / 300) = 1 - 0.99 = 0.01 ✅

解释: 事件几乎占满整个窗口，无明显爆发
```

#### Java实现

```java
public double calculateBurstIntensity(List<AttackEvent> events, long windowSize) {
    if (events.size() < 2) {
        return 0.0;  // 单个事件，无法判断分布
    }
    
    // 计算事件的实际时间跨度
    long minTime = events.stream()
        .map(AttackEvent::getTimestamp)
        .min(Comparator.naturalOrder())
        .get().toEpochMilli();
    
    long maxTime = events.stream()
        .map(AttackEvent::getTimestamp)
        .max(Comparator.naturalOrder())
        .get().toEpochMilli();
    
    long timeSpan = maxTime - minTime;
    
    // 计算爆发强度系数
    double burstIntensity = 1.0 - (double) timeSpan / windowSize;
    
    return Math.max(0.0, burstIntensity);  // 确保非负
}
```

---

### 指标2: 标准差系数 (Standard Deviation Coefficient)

#### 定义

```
计算事件到达时间间隔的标准差

步骤:
  1. 计算相邻事件的时间间隔: intervals = [t2-t1, t3-t2, ..., tn-t(n-1)]
  2. 计算间隔的平均值: avgInterval = sum(intervals) / count(intervals)
  3. 计算标准差: stdDev = sqrt(sum((interval - avgInterval)^2) / count)
  4. 归一化: SDC = stdDev / avgInterval

取值:
  - SDC ≈ 0: 事件间隔非常均匀（定时扫描）
  - SDC > 1: 事件间隔波动大（爆发式攻击）
```

#### 实际计算示例

**场景A: 集中爆发**

```
事件时间戳: [0s, 0.01s, 0.02s, ..., 3s]
间隔: [0.01s, 0.01s, ..., 0.01s] (爆发期间)
      然后是297秒的空窗期

avgInterval = (3s + 297s) / 事件数
stdDev 很大 (因为有长空窗期)
SDC > 1 ✅
```

**场景B: 均匀分布**

```
事件时间戳: [0s, 3s, 6s, 9s, ..., 297s]
间隔: [3s, 3s, 3s, ..., 3s]

avgInterval = 3s
stdDev ≈ 0 (间隔完全均匀)
SDC ≈ 0 ✅
```

#### Java实现

```java
public double calculateIntervalStdDev(List<AttackEvent> events) {
    if (events.size() < 3) {
        return 0.0;
    }
    
    // 排序事件
    List<AttackEvent> sorted = events.stream()
        .sorted(Comparator.comparing(AttackEvent::getTimestamp))
        .collect(Collectors.toList());
    
    // 计算间隔
    List<Long> intervals = new ArrayList<>();
    for (int i = 1; i < sorted.size(); i++) {
        long interval = sorted.get(i).getTimestamp().toEpochMilli() 
                      - sorted.get(i-1).getTimestamp().toEpochMilli();
        intervals.add(interval);
    }
    
    // 计算平均值
    double avgInterval = intervals.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0.0);
    
    if (avgInterval == 0) return 0.0;
    
    // 计算标准差
    double variance = intervals.stream()
        .mapToDouble(interval -> Math.pow(interval - avgInterval, 2))
        .average()
        .orElse(0.0);
    
    double stdDev = Math.sqrt(variance);
    
    // 归一化
    return stdDev / avgInterval;
}
```

---

### 指标3: 事件密度方差 (Event Density Variance)

#### 定义

```
将窗口分成N个时间槽，计算每个槽内事件数的方差

步骤:
  1. 将窗口分成N个等长时间槽（如10个槽，每槽30秒）
  2. 统计每个槽内的事件数: counts = [c1, c2, ..., cN]
  3. 计算平均值: avg = sum(counts) / N
  4. 计算方差: variance = sum((ci - avg)^2) / N
  5. 归一化: EDV = variance / avg^2

取值:
  - EDV = 0: 事件在所有时间槽均匀分布
  - EDV > 1: 事件高度集中在少数时间槽
```

#### 实际计算示例

**场景A: 集中爆发**

```
窗口分成10个槽 (每槽30秒):
槽1 [00:00-00:30]: 100条 ✅
槽2 [00:30-01:00]: 0条
槽3 [01:00-01:30]: 0条
...
槽10 [04:30-05:00]: 0条

counts = [100, 0, 0, 0, 0, 0, 0, 0, 0, 0]
avg = 10
variance = ((100-10)^2 + 9×(0-10)^2) / 10 = (8100 + 900) / 10 = 900
EDV = 900 / 10^2 = 9 ✅

解释: 方差远大于平均值，高度不均匀
```

**场景B: 均匀分布**

```
窗口分成10个槽:
槽1: 10条
槽2: 10条
...
槽10: 10条

counts = [10, 10, 10, 10, 10, 10, 10, 10, 10, 10]
avg = 10
variance = 0
EDV = 0 ✅

解释: 完全均匀分布
```

#### Java实现

```java
public double calculateDensityVariance(
        List<AttackEvent> events, 
        long windowStart, 
        long windowSize) {
    
    int NUM_SLOTS = 10;  // 分成10个时间槽
    long slotSize = windowSize / NUM_SLOTS;
    
    int[] slotCounts = new int[NUM_SLOTS];
    
    // 统计每个槽内的事件数
    for (AttackEvent event : events) {
        long offset = event.getTimestamp().toEpochMilli() - windowStart;
        int slotIndex = (int) (offset / slotSize);
        if (slotIndex >= 0 && slotIndex < NUM_SLOTS) {
            slotCounts[slotIndex]++;
        }
    }
    
    // 计算平均值
    double avg = (double) events.size() / NUM_SLOTS;
    
    if (avg == 0) return 0.0;
    
    // 计算方差
    double variance = 0;
    for (int count : slotCounts) {
        variance += Math.pow(count - avg, 2);
    }
    variance /= NUM_SLOTS;
    
    // 归一化方差
    return variance / (avg * avg);
}
```

---

## 🎨 时间分布权重设计

### 方案1: 爆发强度权重 (推荐 ⭐)

#### 权重计算公式

```java
public double calculateBurstWeight(List<AttackEvent> events, long windowSize) {
    if (events.size() < 2) {
        return 1.0;  // 单个事件，无法判断分布
    }
    
    // 计算爆发强度系数
    double burstIntensity = calculateBurstIntensity(events, windowSize);
    
    // 转换为权重 (爆发强度越高，权重越大)
    double burstWeight = 1.0 + (burstIntensity * 2.0);
    // 范围: [1.0, 3.0]
    // - 完全分散: 1.0 (无威胁加成)
    // - 完全集中: 3.0 (勒索软件爆发)
    
    return burstWeight;
}
```

#### 权重映射表

| 时间跨度占比 | 爆发强度 | 权重 | 说明 |
|------------|---------|------|------|
| 100% | 0.00 | 1.0 | 完全分散 (无加成) |
| 75% | 0.25 | 1.5 | 轻微集中 |
| 50% | 0.50 | 2.0 | 中度集中 |
| 25% | 0.75 | 2.5 | 高度集中 |
| 10% | 0.90 | 2.8 | 极度集中 |
| 1% | 0.99 | 3.0 | 瞬间爆发 🔴 勒索软件 |

**设计理由**:
- 简单高效：仅需计算时间跨度
- 直观易懂：爆发强度直接反映威胁程度
- 计算快速：O(n) 复杂度，仅需遍历一次事件列表

---

### 方案2: 混合权重（最佳实践）

#### 公式

```java
public double calculateTimeDistributionWeight(
        List<AttackEvent> events, 
        long windowStart, 
        long windowSize) {
    
    if (events.size() < 2) {
        return 1.0;
    }
    
    // 1. 计算爆发强度权重
    double burstWeight = calculateBurstWeight(events, windowSize);
    
    // 2. 计算密度方差权重
    double densityVariance = calculateDensityVariance(events, windowStart, windowSize);
    double densityWeight = 1.0 + Math.min(densityVariance, 2.0);
    
    // 3. 混合策略（加权平均）
    // 70%爆发强度 + 30%密度方差
    double timeDistWeight = 0.7 * burstWeight + 0.3 * densityWeight;
    
    return timeDistWeight;
}
```

**设计理由**:
- 爆发强度(70%)：主要指标，捕获时间跨度
- 密度方差(30%)：辅助指标，识别复杂模式
- 权重可调：可根据实际数据调整比例

---

## 🔄 更新后的威胁评分公式

### 新公式

```java
threatScore = (attackCount × uniqueIps × uniquePorts)
            × timeWeight              // 时间段权重（深夜高）
            × ipWeight                // IP多样性权重
            × portWeight              // 端口多样性权重
            × deviceWeight            // 设备多样性权重
            × timeDistributionWeight  // ⭐ 新增：时间分布权重
```

### 完整实现

```java
// services/stream-processing/src/main/java/com/threatdetection/stream/functions/TierWindowProcessor.java

public class TierWindowProcessor extends ProcessWindowFunction<
        AttackEvent, AggregatedAttackData, String, TimeWindow> {

    @Override
    public void process(
            String key,
            Context context,
            Iterable<AttackEvent> events,
            Collector<AggregatedAttackData> out) throws Exception {
        
        // ⭐ 收集所有事件到List（便于计算时间分布）
        List<AttackEvent> eventList = StreamSupport.stream(events.spliterator(), false)
            .collect(Collectors.toList());
        
        if (eventList.isEmpty()) {
            return;
        }
        
        // 基础统计
        int attackCount = eventList.size();
        Set<String> uniqueIps = new HashSet<>();
        Set<Integer> uniquePorts = new HashSet<>();
        Set<String> uniqueDevices = new HashSet<>();
        String customerId = null;
        String attackMac = null;
        String attackIp = null;
        String mostAccessedIp = null;
        
        for (AttackEvent event : eventList) {
            uniqueIps.add(event.getResponseIp());
            uniquePorts.add(event.getResponsePort());
            uniqueDevices.add(event.getDevSerial());
            customerId = event.getCustomerId();
            attackMac = event.getAttackMac();
            attackIp = event.getAttackIp();
        }
        
        // 基础评分
        double baseScore = attackCount * uniqueIps.size() * uniquePorts.size();
        
        // 时间权重
        Instant windowEnd = Instant.ofEpochMilli(context.window().getEnd());
        double timeWeight = calculateTimeWeight(windowEnd);
        
        // IP权重
        double ipWeight = calculateIpWeight(uniqueIps.size());
        
        // 端口权重
        double portWeight = calculatePortWeight(uniquePorts.size());
        
        // 设备权重
        double deviceWeight = uniqueDevices.size() > 1 ? 1.5 : 1.0;
        
        // ⭐ 时间分布权重（新增）
        long windowStart = context.window().getStart();
        long windowSize = context.window().getEnd() - windowStart;
        long eventTimeSpan = getTimeSpan(eventList);
        double burstIntensity = calculateBurstIntensity(eventList, windowSize);
        double timeDistWeight = calculateBurstWeight(eventList, windowSize);
        
        // ⭐ 最终评分（包含时间分布）
        double threatScore = baseScore 
            * timeWeight 
            * ipWeight 
            * portWeight 
            * deviceWeight
            * timeDistWeight;  // 新增
        
        String threatLevel = determineThreatLevel(threatScore);
        
        // 输出
        AggregatedAttackData result = AggregatedAttackData.builder()
            .customerId(customerId)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .mostAccessedHoneypotIp(mostAccessedIp)
            .attackCount(attackCount)
            .uniqueIps(uniqueIps.size())
            .uniquePorts(uniquePorts.size())
            .uniqueDevices(uniqueDevices.size())
            .threatScore(threatScore)
            .threatLevel(threatLevel)
            .eventTimeSpan(eventTimeSpan)           // 新增
            .burstIntensity(burstIntensity)         // 新增
            .timeDistributionWeight(timeDistWeight) // 新增
            .windowTier(tier)
            .windowName(tierName)
            .timestamp(windowEnd)
            .build();
        
        out.collect(result);
        
        log.info("Tier {} window: customerId={}, attackMac={}, " +
                 "threatScore={}, timeDistWeight={}, burstIntensity={}, " +
                 "count={}, timeSpan={}ms of {}ms window",
            tier, customerId, attackMac, threatScore, timeDistWeight,
            burstIntensity, attackCount, eventTimeSpan, windowSize);
    }
    
    private long getTimeSpan(List<AttackEvent> events) {
        if (events.size() < 2) return 0;
        
        long min = events.stream()
            .mapToLong(e -> e.getTimestamp().toEpochMilli())
            .min().getAsLong();
        
        long max = events.stream()
            .mapToLong(e -> e.getTimestamp().toEpochMilli())
            .max().getAsLong();
        
        return max - min;
    }
    
    private double calculateBurstIntensity(List<AttackEvent> events, long windowSize) {
        if (events.size() < 2 || windowSize == 0) {
            return 0.0;
        }
        
        long timeSpan = getTimeSpan(events);
        double intensity = 1.0 - (double) timeSpan / windowSize;
        
        return Math.max(0.0, Math.min(1.0, intensity));
    }
    
    private double calculateBurstWeight(List<AttackEvent> events, long windowSize) {
        double burstIntensity = calculateBurstIntensity(events, windowSize);
        return 1.0 + (burstIntensity * 2.0);  // 范围: [1.0, 3.0]
    }
}
```

---

## 📈 实际效果对比

### 场景A: 勒索软件爆发（3秒内100条事件）

```
窗口: Tier 2 (5分钟 = 300秒)
事件: 100条在3秒内

基础评分:
  baseScore = 100 × 50 × 10 = 50,000

传统权重:
  timeWeight = 1.0
  ipWeight = 2.0
  portWeight = 1.6
  deviceWeight = 1.0
  
传统评分 = 50,000 × 1.0 × 2.0 × 1.6 × 1.0
         = 160,000

新增时间分布权重:
  timeSpan = 3秒
  burstIntensity = 1 - (3 / 300) = 0.99
  timeDistWeight = 1 + (0.99 × 2) = 2.98 ✅

新评分 = 160,000 × 2.98
       = 476,800 🔴

提升: 476,800 / 160,000 = 2.98倍
威胁等级: CRITICAL
告警: ⚡ 检测到勒索软件横向扫描特征！
```

### 场景B: 正常扫描（均匀分布在5分钟）

```
窗口: Tier 2 (5分钟 = 300秒)
事件: 100条均匀分布在297秒

基础评分:
  baseScore = 100 × 50 × 10 = 50,000

传统权重:
  (和场景A相同)
  
传统评分 = 160,000 (和场景A相同 ❌)

新增时间分布权重:
  timeSpan = 297秒
  burstIntensity = 1 - (297 / 300) = 0.01
  timeDistWeight = 1 + (0.01 × 2) = 1.02 ✅

新评分 = 160,000 × 1.02
       = 163,200 🟡

提升: 163,200 / 160,000 = 1.02倍
威胁等级: HIGH
告警: 持续扫描行为
```

### 评分差异分析

```
场景对比:
  场景A (爆发): 476,800 🔴 CRITICAL
  场景B (均匀): 163,200 🟡 HIGH
  
差异倍数: 476,800 / 163,200 = 2.92倍 ✅

结论:
  ✅ 成功区分爆发式攻击和持续扫描
  ✅ 勒索软件特征被正确识别为更高威胁
  ✅ 评分更符合实际威胁程度
```

---

## 🎯 不同攻击模式的评分

### 模式对照表

| 攻击模式 | 时间跨度 | 爆发强度 | 权重 | 威胁等级 | 典型场景 |
|---------|---------|---------|------|---------|---------|
| 瞬间爆发 | 1秒 (0.3%) | 0.997 | 2.99 | 🔴 CRITICAL | 勒索软件横向扫描 |
| 短期集中 | 30秒 (10%) | 0.90 | 2.8 | 🔴 CRITICAL | 快速端口扫描 |
| 中度集中 | 150秒 (50%) | 0.50 | 2.0 | 🟡 HIGH | 常规攻击 |
| 高度分散 | 270秒 (90%) | 0.10 | 1.2 | 🟢 MEDIUM | 慢速扫描 |
| 完全均匀 | 299秒 (99.7%) | 0.003 | 1.006 | 🟢 LOW | 定时探测 |

### 详细模式分析

#### 模式1: 瞬间爆发 (勒索软件)

```
特征: 100条事件在1秒内
timeSpan = 1s / 300s = 0.003
burstIntensity = 0.997
timeDistWeight = 2.99 🔴

威胁等级: CRITICAL
告警: ⚡ 勒索软件横向扫描！
```

#### 模式2: 短期集中 (快速扫描)

```
特征: 100条事件在30秒内
timeSpan = 30s / 300s = 0.1
burstIntensity = 0.9
timeDistWeight = 2.8 🔴

威胁等级: CRITICAL
告警: ⚡ 快速端口扫描
```

#### 模式3: 中等分散 (常规攻击)

```
特征: 100条事件在150秒内
timeSpan = 150s / 300s = 0.5
burstIntensity = 0.5
timeDistWeight = 2.0 🟡

威胁等级: HIGH
告警: 持续攻击行为
```

#### 模式4: 高度分散 (慢速扫描)

```
特征: 100条事件在270秒内
timeSpan = 270s / 300s = 0.9
burstIntensity = 0.1
timeDistWeight = 1.2 🟢

威胁等级: MEDIUM
告警: 慢速端口扫描
```

#### 模式5: 完全均匀 (定时探测)

```
特征: 100条事件均匀分布在300秒
timeSpan = 299s / 300s = 0.997
burstIntensity = 0.003
timeDistWeight = 1.006 🟢

威胁等级: MEDIUM/LOW
告警: 定时探测行为
```

---

## 🚀 实施路线图

### Phase 1: 基础实现（推荐立即实施）

**目标**: 添加爆发强度权重到威胁评分算法

**步骤**:

1. **修改TierWindowProcessor.java**
   ```java
   // 将Iterable<AttackEvent>转换为List
   List<AttackEvent> eventList = StreamSupport.stream(events.spliterator(), false)
       .collect(Collectors.toList());
   
   // 计算时间分布权重
   long windowStart = context.window().getStart();
   long windowSize = context.window().getEnd() - windowStart;
   double timeDistWeight = calculateBurstWeight(eventList, windowSize);
   
   // 更新最终评分
   double threatScore = baseScore * ... * timeDistWeight;
   ```

2. **扩展AggregatedAttackData.java**
   ```java
   @Data
   @Builder
   public class AggregatedAttackData {
       // ... 现有字段 ...
       
       // ⭐ 新增字段
       private long eventTimeSpan;           // 事件时间跨度（毫秒）
       private double burstIntensity;        // 爆发强度系数 [0, 1]
       private double timeDistributionWeight; // 时间分布权重 [1.0, 3.0]
   }
   ```

3. **更新日志输出**
   ```java
   log.info("Tier {} window: customerId={}, attackMac={}, " +
            "threatScore={}, timeDistWeight={}, burstIntensity={}, " +
            "count={}, timeSpan={}ms of {}ms window",
       tier, customerId, attackMac, threatScore, timeDistWeight,
       burstIntensity, attackCount, eventTimeSpan, windowSize);
   ```

4. **更新Kafka消息格式**
   - threat-alerts topic添加新字段
   - alert-management服务识别新字段

**预计工作量**: 2-3小时

---

### Phase 2: 数据验证与调优

**目标**: 收集真实数据，验证和优化权重参数

**步骤**:

1. **收集真实数据**
   - 记录各种攻击模式的时间分布
   - 统计爆发强度分布
   - 分析误报/漏报案例

2. **调整权重范围**
   - 当前: `[1.0, 3.0]`
   - 可能需要调整为 `[1.0, 2.5]` 或 `[1.0, 4.0]`
   - 根据实际效果微调

3. **A/B测试**
   - 对比新旧评分的准确性
   - 验证告警质量
   - 优化威胁等级划分

**预计工作量**: 1-2周

---

### Phase 3: 高级特征（可选）

**目标**: 引入更复杂的时间模式识别

**功能**:

1. **事件密度方差权重**
   - 将窗口分成N个时间槽
   - 计算每个槽内事件数的方差
   - 识别更复杂的时间模式

2. **混合权重策略**
   ```java
   double timeDistWeight = 0.7 * burstWeight + 0.3 * densityWeight;
   ```

3. **时间模式识别**
   - 周期性检测（定时扫描）
   - 加速度检测（攻击速率变化）
   - 多峰检测（多次爆发）

4. **机器学习优化**
   - 训练模型识别时间模式
   - 自动调整权重参数
   - 异常时间模式检测

**预计工作量**: 1-2月

---

## 📊 数据结构更新

### AggregatedAttackData扩展

```java
// services/stream-processing/src/main/java/com/threatdetection/stream/model/AggregatedAttackData.java

package com.threatdetection.stream.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedAttackData {
    // 租户和攻击者信息
    private String customerId;
    private String attackMac;
    private String attackIp;
    private String mostAccessedHoneypotIp;
    
    // 聚合指标
    private int attackCount;
    private int uniqueIps;
    private int uniquePorts;
    private int uniqueDevices;
    
    // 威胁评分
    private double threatScore;
    private String threatLevel;
    
    // ⭐ 新增：时间分布相关字段
    private long eventTimeSpan;           // 事件时间跨度（毫秒）
    private double burstIntensity;        // 爆发强度系数 [0, 1]
    private double timeDistributionWeight; // 时间分布权重 [1.0, 3.0]
    
    // 窗口信息
    private Instant timestamp;
    private int windowTier;
    private String windowName;
}
```

### Kafka消息格式

```json
{
  "customerId": "customer_c",
  "attackMac": "04:42:1A:8E:E3:65",
  "attackIp": "192.168.100.100",
  "mostAccessedHoneypotIp": "192.168.100.27",
  "attackCount": 100,
  "uniqueIps": 50,
  "uniquePorts": 10,
  "uniqueDevices": 1,
  "threatScore": 476800.0,
  "threatLevel": "CRITICAL",
  "eventTimeSpan": 3000,
  "burstIntensity": 0.99,
  "timeDistributionWeight": 2.98,
  "timestamp": "2025-10-27T10:05:00Z",
  "windowTier": 2,
  "windowName": "主要威胁检测"
}
```

### PostgreSQL表更新（可选）

如果需要持久化时间分布信息：

```sql
ALTER TABLE threat_assessments 
ADD COLUMN event_time_span BIGINT,
ADD COLUMN burst_intensity DECIMAL(5,4),
ADD COLUMN time_distribution_weight DECIMAL(5,2);

COMMENT ON COLUMN threat_assessments.event_time_span IS '事件时间跨度（毫秒）';
COMMENT ON COLUMN threat_assessments.burst_intensity IS '爆发强度系数 [0, 1]';
COMMENT ON COLUMN threat_assessments.time_distribution_weight IS '时间分布权重 [1.0, 3.0]';
```

---

## ✅ 价值总结

### 当前问题

❌ **时间分布信息完全丢失**
- 无法区分爆发式攻击和持续扫描
- 无法识别勒索软件的关键特征（快速横向移动）
- 相同count产生相同评分，不合理

### 解决后效果

✅ **准确识别攻击模式**
- 爆发式攻击: 评分提升3倍（权重2.98）
- 持续扫描: 评分基本不变（权重1.02）
- 差异倍数: **2.92倍**

✅ **核心优势**
1. **快速检测勒索软件**: 爆发特征自动提升评分
2. **减少误报**: 定时探测不会产生高分告警
3. **符合真实场景**: 大多数攻击在两种极端之间
4. **简单高效**: 计算开销极小（仅计算时间跨度）
5. **立即可用**: 无需机器学习，基于数学公式

### 业务价值

1. **提升检测准确性**: 30-50%的准确率提升（预估）
2. **降低误报率**: 减少对正常扫描的过度告警
3. **快速响应勒索软件**: 识别爆发特征，缩短响应时间
4. **增强竞争力**: 行业领先的威胁检测能力

---

## 📚 相关文档

- **Flink流处理架构**: `docs/design/flink_stream_processing_architecture.md`
- **威胁评分算法**: `docs/design/honeypot_based_threat_scoring.md`
- **数据结构定义**: `docs/design/data_structures.md`
- **测试方法**: `docs/testing/COMPREHENSIVE_DEVELOPMENT_TEST_PLAN.md`

---

## 🔧 故障排查

### 常见问题

**Q1: 时间分布权重始终为1.0**

检查步骤:
1. 确认events已转换为List
2. 检查windowSize计算是否正确
3. 验证eventTimeSpan是否为0
4. 查看日志输出的burstIntensity值

**Q2: 评分过高或过低**

调整方法:
1. 调整权重范围（当前1.0-3.0）
2. 修改权重公式系数（当前×2.0）
3. 使用混合权重策略

**Q3: 性能影响**

优化方案:
1. 爆发强度计算为O(n)，性能影响极小
2. 如使用密度方差，可减少时间槽数量
3. 仅在count > 阈值时计算时间分布权重

---

**文档结束**

*最后更新: 2025-10-27*  
*维护者: 系统架构团队*  
*版本: 1.0*  
*状态: 🚧 设计方案 (待实施)*
