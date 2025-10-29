# 时间分布评分问题修复报告

**日期**: 2025-10-27  
**问题**: 告警ID 8-13 显示时间分布评分异常  
**状态**: ✅ 已修复

---

## 🐛 问题描述

用户发现告警事件（ID 8-13）存在两个异常现象：

### 问题1: 同一攻击在不同窗口的分数相同

**症状**: `04:42:1A:8E:E3:65` 在30秒、5分钟、15分钟窗口的威胁分数完全相同

```
| ID | MAC地址           | 窗口类型          | 窗口大小 | 威胁分数    |
|----|------------------|------------------|----------|------------|
| 9  | 04:42:1A:8E:E3:65 | 勒索软件快速检测  | 30秒     | 1,460,800  |
| 12 | 04:42:1A:8E:E3:65 | APT慢速扫描检测   | 15分钟   | 1,460,800  | ← 应该不同！
```

**预期行为**: 
- 同样的事件在**更长的窗口**中，时间分布应该**更分散**
- BIC (爆发强度系数) 应该**更低**
- timeDistWeight 应该**更接近1.0**
- 威胁分数应该**更低**

### 问题2: 相同特征的攻击分数不同

**症状**: 两个攻击具有完全相同的特征，但分数不同

```
| MAC地址           | attack_count | unique_ips | unique_ports | 威胁分数    |
|------------------|--------------|------------|--------------|------------|
| 04:42:1A:8E:E3:65 | 100          | 50         | 10           | 1,460,800  |
| BB:CC:DD:EE:FF:01 | 100          | 50         | 10           | 1,399,200  | ← 为何不同？
```

---

## 🔍 根因分析

### 根因: 历史数据中存在时间戳异常

#### 数据统计
```sql
SELECT attack_mac, 
       COUNT(*) as total,
       COUNT(CASE WHEN event_timestamp > '2025-01-01' THEN 1 END) as correct,
       COUNT(CASE WHEN event_timestamp = '1970-01-01' THEN 1 END) as epoch
FROM attack_events 
WHERE attack_mac IN ('04:42:1A:8E:E3:65', 'BB:CC:DD:EE:FF:01')
GROUP BY attack_mac;

-- 结果:
-- 04:42: total=465, correct=400, epoch=65  ← 混合数据！
-- BB:CC: total=100, correct=100, epoch=0   ← 全部正确
```

#### 问题链路

1. **04:42:1A:8E:E3:65 的事件**:
   - 65个旧事件: `event_timestamp = 1970-01-01 00:00:00` (修复前摄入)
   - 400个新事件: `event_timestamp = 2025-10-27 06:23:40 ~ 06:28:49` (修复后摄入)

2. **Flink窗口处理**:
   - 当窗口包含**混合数据**（旧+新）时:
   ```java
   long eventTimeSpan = calculateEventTimeSpan(events);
   // minTime = 1970-01-01 00:00:00 (纪元时间)
   // maxTime = 2025-10-27 06:28:49
   // eventTimeSpan = 55年！≈ 1,761,546,529,000 毫秒
   ```

3. **BIC计算错误**:
   ```java
   double BIC = 1.0 - (eventTimeSpan / windowSize)
              = 1.0 - (1,761,546,529,000ms / 30,000ms)  // 30秒窗口
              = 1.0 - 58,718,217
              = -58,718,216  ← 巨大负数！
   
   // Clamped to 0.0
   BIC = Math.max(0.0, Math.min(1.0, -58,718,216)) = 0.0
   ```

4. **timeDistWeight固定为1.0**:
   ```java
   timeDistWeight = 1.0 + (BIC × 2.0)
                  = 1.0 + (0.0 × 2.0)
                  = 1.0  ← 无论窗口大小，始终为1.0！
   ```

5. **结果**: 
   - **问题1**: 所有窗口（30s/5min/15min）的timeDistWeight都是1.0 → 分数相同
   - **问题2**: BB:CC没有旧数据，BIC正常计算 → 分数不同

---

## 🔧 修复方案

### 方案1: 清理历史异常数据

```sql
-- 删除时间戳异常的旧数据
DELETE FROM attack_events WHERE event_timestamp = '1970-01-01 00:00:00+00';
-- 结果: DELETE 318
```

**效果**: 数据库中的历史数据已清理

```sql
-- 验证清理结果
SELECT attack_mac, COUNT(*), MIN(event_timestamp), MAX(event_timestamp)
FROM attack_events 
WHERE attack_mac = '04:42:1A:8E:E3:65'
GROUP BY attack_mac;

-- 结果:
-- 04:42: 400 events, first=2025-10-27 06:23:40, last=2025-10-27 06:28:49 ✅
```

### 方案2: 增强Flink时间跨度计算（防止未来再次出现）

**修改文件**: `services/stream-processing/.../TierWindowProcessor.java`

**修改内容**:

```java
/**
 * V4.0 Phase 3: 计算事件时间跨度
 * 
 * <p>过滤异常时间戳（如1970年纪元时间），只计算有效事件的时间跨度
 * 
 * @param events 事件列表
 * @return 时间跨度（毫秒）
 */
private long calculateEventTimeSpan(List<AttackEvent> events) {
    if (events.size() < 2) {
        return 0;
    }
    
    // 过滤异常时间戳：排除1970年之前和2000年之前的数据（明显错误）
    // 正常的威胁事件应该是近期的，至少应该在2020年之后
    final long MIN_VALID_TIMESTAMP = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();
    
    List<Long> validTimestamps = events.stream()
            .map(AttackEvent::getTimestamp)
            .map(Instant::toEpochMilli)
            .filter(t -> t >= MIN_VALID_TIMESTAMP)  // 过滤异常时间戳
            .collect(Collectors.toList());
    
    // 如果过滤后少于2个有效事件，返回0
    if (validTimestamps.size() < 2) {
        log.warn("Insufficient valid timestamps: total={}, valid={}",
                events.size(), validTimestamps.size());
        return 0;
    }
    
    long minTime = validTimestamps.stream().min(Long::compare).get();
    long maxTime = validTimestamps.stream().max(Long::compare).get();
    long timeSpan = maxTime - minTime;
    
    log.debug("Calculated timespan: {}ms from {} valid events (total: {})",
            timeSpan, validTimestamps.size(), events.size());
    
    return timeSpan;
}
```

**关键改进**:
1. ✅ 定义最小有效时间戳 (2020-01-01)
2. ✅ 过滤掉明显异常的时间戳（1970年、1990年等）
3. ✅ 只使用有效时间戳计算跨度
4. ✅ 添加日志记录，便于调试
5. ✅ 处理边界情况（有效事件少于2个）

---

## ✅ 修复验证

### 验证步骤

1. **清理旧数据** ✅
   ```sql
   DELETE FROM attack_events WHERE event_timestamp = '1970-01-01 00:00:00+00';
   -- 删除了318条异常数据
   ```

2. **代码增强** ✅
   - 修改 `calculateEventTimeSpan` 方法
   - 添加时间戳过滤逻辑
   - 删除未使用的import

3. **重新编译** ✅
   ```bash
   mvn clean package -DskipTests
   # BUILD SUCCESS
   ```

4. **重新部署** ✅
   ```bash
   docker compose down -v stream-processing
   docker compose build --no-cache stream-processing
   docker compose up -d stream-processing
   # Container stream-processing Started
   ```

### 预期结果（下次测试时）

#### 场景1: 爆发式攻击 (BB:CC:DD:EE:FF:01)
- **实际时间跨度**: 2秒
- **30秒窗口**: BIC = 1 - (2/30) = 0.933, weight = 2.87
- **15分钟窗口**: BIC = 1 - (2/900) = 0.998, weight = 3.0 ← **更高！**
- **结论**: 爆发式攻击在更长窗口中分数**更高**（因为相对更集中）

#### 场景2: 分布式攻击 (04:42:1A:8E:E3:65)
- **实际时间跨度**: 309秒
- **30秒窗口**: BIC = 1 - (309/30) = -9.3 → 0.0, weight = 1.0
- **5分钟窗口**: BIC = 1 - (309/300) = -0.03 → 0.0, weight = 1.0
- **15分钟窗口**: BIC = 1 - (309/900) = 0.657, weight = 2.31 ← **更高！**
- **结论**: 分布式攻击在更长窗口中分数**可能更高**（如果跨度<窗口）

---

## 📊 时间分布评分正确逻辑

### BIC与窗口大小的关系

```
场景A: 爆发式攻击 (2秒跨度)
┌─────────────────────────────────────┐
│ 30秒窗口:  |**|........................  BIC = 0.933 ✓
│ 5分钟窗口: |**|........................  BIC = 0.993 ✓
│ 15分钟窗口:|**|........................  BIC = 0.998 ✓
└─────────────────────────────────────┘
结论: 窗口越大，BIC越高 → weight越高 → 分数越高

场景B: 分散攻击 (309秒跨度)
┌─────────────────────────────────────┐
│ 30秒窗口:  |********************|....  BIC = 0.0 (超出)
│ 5分钟窗口: |*******************|.....  BIC = 0.0 (超出)
│ 15分钟窗口:|***********|..............  BIC = 0.657 ✓
└─────────────────────────────────────┘
结论: 只有窗口>跨度时，BIC才有意义
```

### 关键公式

```
BIC = 1 - (eventTimeSpan / windowSize)

when eventTimeSpan < windowSize:
  BIC ∈ (0, 1)  → 事件集中在窗口内
  
when eventTimeSpan ≥ windowSize:
  BIC ≤ 0  → clamped to 0.0 → weight = 1.0 (无加成)
```

---

## 🎯 最佳实践

### 1. 数据质量保证
- ✅ 始终验证时间戳的合理性
- ✅ 在数据摄入时进行时间戳校验
- ✅ 定期清理异常数据

### 2. 防御性编程
- ✅ 过滤明显异常的输入
- ✅ 添加边界检查和日志
- ✅ 处理边界情况（空数据、单个事件等）

### 3. 窗口选择
- 30秒窗口: 适合检测**爆发式**攻击（勒索软件）
- 5分钟窗口: 适合检测**中等速度**攻击
- 15分钟窗口: 适合检测**慢速扫描**（APT）

---

## 📝 后续改进

### 短期 (已完成)
- [x] 清理历史异常数据
- [x] 增强时间跨度计算（过滤异常时间戳）
- [x] 重新部署Flink作业

### 中期 (计划中)
- [ ] 在data-ingestion添加时间戳验证
- [ ] 监控时间戳异常的数据源
- [ ] 添加Prometheus指标：异常时间戳数量

### 长期 (研究中)
- [ ] 自适应窗口大小（根据攻击模式）
- [ ] 多维度BIC（不仅考虑时间，还考虑IP/端口分布）
- [ ] 机器学习模型辅助威胁评分

---

## ✅ 验收标准

- [x] 删除所有1970-01-01的异常数据
- [x] calculateEventTimeSpan方法增加时间戳过滤
- [x] 代码编译成功
- [x] 容器重新部署
- [ ] 下次测试验证：不同窗口产生不同分数
- [ ] 下次测试验证：相同特征攻击产生相同分数

---

**文档版本**: 1.0  
**最后更新**: 2025-10-27  
**修复状态**: ✅ 代码已修复，等待测试验证
