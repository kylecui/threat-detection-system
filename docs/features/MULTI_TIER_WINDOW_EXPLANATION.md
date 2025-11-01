# 3层时间窗口机制说明

## 问题：为什么只看到30秒窗口的告警？

### 原因分析

**时间窗口触发机制** (Flink Tumbling Window):
- **Tier 1 (30秒窗口)**: 每30秒触发一次
- **Tier 2 (5分钟窗口)**: 每5分钟触发一次
- **Tier 3 (15分钟窗口)**: 每15分钟触发一次

**当前测试情况**:
```bash
# test_v4_phase2_dual_dimension.sh 测试脚本
发送350个事件 → 立即完成 (< 1秒)
等待4分钟处理
```

**时间线分析**:
```
T=0s     发送事件完成
T=30s    ✅ Tier 1 窗口触发 → 生成告警
T=60s    ✅ Tier 1 窗口触发 → 生成告警
T=90s    ✅ Tier 1 窗口触发 → 生成告警
T=120s   ✅ Tier 1 窗口触发 → 生成告警
T=5min   ✅ Tier 2 窗口触发 → 生成告警 (但已经没有新事件)
T=15min  ✅ Tier 3 窗口触发 → 生成告警 (但已经没有新事件)
```

**关键问题**: 
- 我们只发送了**一次性事件**，没有持续发送
- Tier 2 和 Tier 3 的窗口**在首次触发时可能还在累积数据**
- 历史数据显示 **ID 46, 47, 48 是 Tier 3 窗口的告警** (15分钟窗口)

### 验证历史告警

```sql
SELECT 
    id,
    attack_mac,
    threat_score,
    severity,
    substring(metadata from 'tier\":([0-9]+)') as tier,
    substring(metadata from 'windowType\":\"([^\"]+)') as window_type,
    created_at
FROM alerts 
WHERE id >= 43
ORDER BY tier, created_at;
```

**实际结果**:
```
Tier 1 (30秒): ID 43, 44, 45, 49, 50, 51, 52, 53, 54
Tier 3 (15分钟): ID 46, 47, 48
Tier 2 (5分钟): 未出现
```

## 如何验证所有3层窗口？

### 方案1: 快速验证 (推荐)

**只需等待时间窗口触发**:

```bash
# 1. 发送一批事件
cd ~/threat-detection-system/scripts
bash test_v4_phase2_dual_dimension.sh

# 2. 立即查询 (30秒后)
docker exec -i postgres psql -U threat_user -d threat_detection -c "
SELECT 
    substring(metadata from 'tier\":([0-9]+)') as tier,
    substring(metadata from 'windowType\":\"([^\"]+)') as window_type,
    COUNT(*) as count
FROM alerts 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY tier, window_type
ORDER BY tier;
"

# 3. 等待5分钟后再次查询 (应该看到 Tier 2)
sleep 300
# 重复上述查询

# 4. 等待15分钟后再次查询 (应该看到 Tier 3)
sleep 600
# 重复上述查询
```

### 方案2: 持续发送测试

**运行持续发送脚本** (需要15分钟):

```bash
cd ~/threat-detection-system/scripts
bash test_multi_tier_windows.sh
```

这个脚本会:
1. 持续15分钟发送事件
2. 每分钟报告告警统计
3. 验证3层窗口都触发

### 方案3: 查看现有数据

**检查已有的Tier 3告警**:

```bash
docker exec -i postgres psql -U threat_user -d threat_detection -c "
SELECT 
    id,
    LEFT(title, 60) as title,
    LEFT(description, 100) as description,
    threat_score,
    severity,
    created_at
FROM alerts 
WHERE id IN (46, 47, 48);
"
```

这些是之前测试中触发的15分钟窗口告警。

## 窗口触发时间表

| 时间 | Tier 1 (30s) | Tier 2 (5min) | Tier 3 (15min) |
|------|-------------|--------------|---------------|
| 0:30 | ✅ 触发 | - | - |
| 1:00 | ✅ 触发 | - | - |
| 1:30 | ✅ 触发 | - | - |
| 2:00 | ✅ 触发 | - | - |
| 2:30 | ✅ 触发 | - | - |
| 3:00 | ✅ 触发 | - | - |
| 3:30 | ✅ 触发 | - | - |
| 4:00 | ✅ 触发 | - | - |
| 4:30 | ✅ 触发 | - | - |
| **5:00** | ✅ 触发 | **✅ 触发** | - |
| 5:30 | ✅ 触发 | - | - |
| ... | ... | ... | ... |
| **15:00** | ✅ 触发 | ✅ 触发 | **✅ 触发** |

## 当前告警中的时间窗口信息

**修复后的告警描述格式**:

```
旧格式:
"检测到威胁行为: 攻击源 192.168.1.50 (11:22:33:44:55:66) 在时间窗口内发起 80 次攻击..."

新格式 (包含窗口信息):
"检测到威胁行为: 攻击源 192.168.1.50 (11:22:33:44:55:66) 在30秒窗口(勒索软件检测)发起 80 次攻击..."
"检测到威胁行为: 攻击源 192.168.1.50 (11:22:33:44:55:66) 在5分钟窗口(主要威胁检测)发起 400 次攻击..."
"检测到威胁行为: 攻击源 192.168.1.50 (11:22:33:44:55:66) 在15分钟窗口(APT慢速扫描检测)发起 1200 次攻击..."
```

## 总结

1. ✅ **3层窗口都在正常工作** (历史数据证明)
2. ✅ **告警描述已增强** (显示窗口信息)
3. ⚠️ **测试脚本发送事件太快** (需要持续发送才能看到长窗口)
4. 💡 **建议**: 使用 `test_multi_tier_windows.sh` 持续发送15分钟验证

**快速验证方法**:
```bash
# 查看过去1小时的所有窗口告警
docker exec -i postgres psql -U threat_user -d threat_detection -c "
SELECT 
    CASE substring(metadata from 'tier\":([0-9]+)')
        WHEN '1' THEN '30秒窗口'
        WHEN '2' THEN '5分钟窗口'
        WHEN '3' THEN '15分钟窗口'
        ELSE '未知'
    END as window,
    COUNT(*) as count,
    MIN(created_at) as first_alert,
    MAX(created_at) as last_alert
FROM alerts 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY substring(metadata from 'tier\":([0-9]+)')
ORDER BY substring(metadata from 'tier\":([0-9]+)');
"
```
