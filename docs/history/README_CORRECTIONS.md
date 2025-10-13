# 系统理解修正说明

**日期**: 2025-10-11  
**版本**: 2.0  
**修正原因**: 基于蜜罐/诱饵机制的工作原理澄清

---

## 🎯 核心修正

### 系统本质

**修正前**: 边界防御系统 (IDS/IPS类似)  
**修正后**: **内网蜜罐/欺骗防御系统**

### 工作原理

```
终端设备 (dev_serial) 功能:
1. 监控二层网络,识别未使用IP
2. 将未使用IP作为诱饵(虚拟哨兵)
3. 主动响应ARP/ICMP,诱导攻击者
4. 记录后续端口访问尝试(不再响应)

威胁来源: 内网失陷主机/APT横向移动
检测对象: 已进入内网的威胁(勒索软件、内部渗透等)
```

### 数据字段语义修正

| 字段 | 修正前理解 | 正确理解 |
|------|-----------|---------|
| `attack_mac` | 外部攻击者 | **被诱捕的内网主机MAC** |
| `attack_ip` | 外部攻击源 | **被诱捕的内网主机IP** |
| `response_ip` | 被攻击的服务器IP | **诱饵IP(不存在的虚拟哨兵)** |
| `response_port` | 被攻击的服务端口 | **攻击者尝试访问的端口(暴露意图)** |

---

## 📚 文档修正清单

以下文档已基于蜜罐机制进行修正:

### ✅ 已创建新文档

1. **docs/honeypot_based_threat_scoring.md**
   - 完整的蜜罐机制说明
   - 修正后的威胁评分算法
   - 修正后的数据结构设计
   - 实施路线图

2. **docs/understanding_corrections_summary.md**
   - 关键误解总结
   - 修正前后对比
   - 影响分析
   - 修正建议

### ⚠️ 需要更新的现有文档

以下文档中部分描述基于错误理解,需要参考新文档进行理解:

1. **.github/copilot-instructions.md**
   - 描述基本准确,但未明确说明蜜罐机制
   - response_ip/port字段语义需澄清

2. **docs/new_system_architecture_spec.md**
   - 架构设计基本正确
   - 数据流描述准确
   - 但对"response_ip"的理解有偏差

3. **docs/original_system_analysis.md**
   - 数据库结构分析正确
   - 但威胁模型描述不够准确

4. **docs/threat_scoring_solution.md**
   - 端口权重理解有偏差
   - 需要结合蜜罐机制重新理解

---

## 🔑 关键理解要点

### 1. 所有访问都是恶意的

因为诱饵IP不存在真实服务,任何访问尝试都是**确认的异常行为**,误报率极低。

### 2. 端口 = 攻击意图

攻击者尝试的端口暴露其攻击目标:
- 3389 (RDP) → 远程控制意图
- 445 (SMB) → 横向移动/勒索软件传播
- 3306 (MySQL) → 数据窃取

### 3. 多诱饵访问 = 横向移动

- 1个诱饵: 零星探测
- 3个诱饵: 系统扫描
- 5+诱饵: 横向移动
- 10+诱饵: APT级威胁

### 4. 威胁来源是内网

检测的是**已进入内网**的威胁,而非外部攻击。

---

## 📖 如何使用这些文档

### 开发时

优先参考:
1. **docs/honeypot_based_threat_scoring.md** - 最新的完整方案
2. **docs/understanding_corrections_summary.md** - 理解修正

辅助参考:
- .github/copilot-instructions.md - 开发规范(字段语义需澄清)
- docs/new_system_architecture_spec.md - 架构设计(整体正确)

### 理解系统时

阅读顺序:
1. docs/understanding_corrections_summary.md - 了解关键修正
2. docs/honeypot_based_threat_scoring.md - 完整工作原理
3. docs/new_system_architecture_spec.md - 技术实现

---

## ✏️ 字段语义速查

```java
// 攻击事件 (AttackEvent)
攻击者信息:
  - attack_mac: 被诱捕者MAC (内网主机)
  - attack_ip: 被诱捕者IP (内网地址)

诱饵信息:
  - response_ip: 诱饵IP (不存在的虚拟哨兵) ← 关键
  - response_port: 攻击者尝试的端口 ← 反映攻击意图

设备信息:
  - dev_serial: 终端蜜罐设备序列号
```

---

## 🎓 术语对照表

| 旧理解术语 | 正确术语 | 说明 |
|-----------|---------|------|
| 响应IP | 诱饵IP / 蜜罐IP | response_ip字段 |
| 被攻击端口 | 目标端口 / 探测端口 | response_port字段 |
| 攻击者 | 被诱捕者 / 失陷主机 | attack_mac/ip |
| 外部威胁 | 内网威胁 / 横向移动 | 威胁来源 |
| 攻击次数 | 探测次数 / 访问尝试 | attackCount |
| 唯一IP数 | 诱饵访问数 | uniqueIps |

---

**使用建议**: 
- 新文档 (honeypot_based_*.md) 为准
- 旧文档作为技术参考,但需注意字段语义
- 所有"response_*"字段理解为"诱饵/目标"

**文档结束**
