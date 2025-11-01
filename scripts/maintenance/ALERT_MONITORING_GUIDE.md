# 告警信息拉取脚本使用指南

## 概述

`fetch_recent_alerts.py` 脚本用于拉取和查看威胁检测系统的最新告警信息。该脚本在生产环境中非常有用，可以帮助监控系统运行状态和威胁检测效果。

## 功能特性

- ✅ 拉取最近N小时内的所有告警
- ✅ 按客户ID过滤告警信息
- ✅ 显示详细的告警信息（ID、标题、状态、严重程度、威胁分数等）
- ✅ 显示告警统计信息
- ✅ 支持JSON格式的元数据显示

## 使用方法

### 基本用法

```bash
# 拉取最近24小时的告警（默认）
python3 scripts/fetch_recent_alerts.py

# 拉取最近48小时的告警
python3 scripts/fetch_recent_alerts.py --hours 48

# 最多显示20条告警
python3 scripts/fetch_recent_alerts.py --limit 20
```

### 按客户过滤

```bash
# 查看特定客户的告警
python3 scripts/fetch_recent_alerts.py --customer customer_a

# 查看客户最近7天的告警
python3 scripts/fetch_recent_alerts.py --customer customer_b --hours 168
```

### 查看帮助

```bash
python3 scripts/fetch_recent_alerts.py --help
```

## 输出示例

### 全局告警查询

```
🔍 拉取最新告警信息
============================================================
📊 找到 3 条告警记录 (过去 24 小时)

🚨 告警 #1
   ID: 1
   标题: 高危威胁检测 - 端口扫描
   状态: NEW
   严重程度: HIGH
   来源: threat-detection-system
   攻击MAC: 00:11:22:33:44:55
   威胁分数: 85.5
   创建时间: 2025-10-31 11:03:17.909617
   更新时间: 2025-10-31 13:03:17.909617
   描述: 检测到来自内网设备的端口扫描行为，威胁分数: 85.5
   元数据: {
     "customer_id": "customer_a",
     "attack_ip": "192.168.1.100",
     "response_ip": "10.0.0.1",
     "response_port": 3389,
     "unique_ips": 3,
     "unique_ports": 5,
     "attack_count": 120
   }
------------------------------------------------------------
📈 统计信息:
   按严重程度: {'HIGH': 1, 'CRITICAL': 1, 'MEDIUM': 1}
   按状态: {'NEW': 3}
```

### 客户特定查询

```
🔍 拉取客户 customer_a 的最新告警信息
============================================================
📊 找到 1 条告警记录

🚨 告警 #1
   ID: 1
   客户ID: customer_a
   标题: 高危威胁检测 - 端口扫描
   状态: NEW
   严重程度: HIGH
   来源: threat-detection-system
   攻击MAC: 00:11:22:33:44:55
   威胁分数: 85.5
   创建时间: 2025-10-31 11:03:17.909617
   描述: 检测到来自内网设备的端口扫描行为，威胁分数: 85.5
------------------------------------------------------------
```

## 告警信息说明

### 告警字段

- **ID**: 告警的唯一标识符
- **标题**: 告警的简要描述
- **状态**: 告警状态 (NEW/ACKNOWLEDGED/IN_PROGRESS/RESOLVED/CLOSED/SUPPRESSED)
- **严重程度**: 威胁级别 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
- **来源**: 告警来源系统
- **攻击MAC**: 攻击者的MAC地址
- **威胁分数**: 威胁评估分数
- **创建时间**: 告警生成时间
- **描述**: 详细的告警描述
- **元数据**: 包含客户ID、攻击详情等额外信息

### 严重程度说明

- **CRITICAL**: 严重威胁，需要立即处理
- **HIGH**: 高危威胁，需要优先处理
- **MEDIUM**: 中危威胁，需要关注
- **LOW**: 低危威胁，可定期处理
- **INFO**: 信息级别，仅供参考

## 注意事项

1. **邮件通知已禁用**: 由于客户邮箱信息是mock数据，系统不会发送邮件通知
2. **数据隔离**: 每个客户的告警数据完全隔离
3. **时间范围**: 默认查询最近24小时的告警，可通过 `--hours` 参数调整
4. **性能考虑**: 查询会按创建时间倒序排列，最新的告警优先显示

## 故障排除

### 没有找到告警

如果查询结果显示"没有发现告警"，可能的原因：

1. 时间范围太短：尝试增加 `--hours` 参数
2. 系统尚未产生告警：确认威胁检测服务正在运行
3. 客户ID错误：确认客户ID格式正确

### 数据库连接失败

如果出现数据库连接错误：

1. 确认PostgreSQL服务正在运行
2. 确认数据库连接参数正确
3. 确认用户权限足够

## 定期监控建议

建议设置定时任务定期运行此脚本：

```bash
# 每小时检查一次最新告警
0 * * * * cd /home/kylecui/threat-detection-system && python3 scripts/fetch_recent_alerts.py --hours 1

# 每天检查所有客户的告警汇总
0 9 * * * cd /home/kylecui/threat-detection-system && python3 scripts/fetch_recent_alerts.py --hours 24
```