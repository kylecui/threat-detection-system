# 设计文档目录

**Cloud-Native Threat Detection System - 系统架构与设计**

---

## 📖 文档说明

本目录包含系统架构、数据结构、算法设计和技术规范文档。

---

## 📂 文档分类

### 🏗️ 核心架构

| 文档 | 说明 |
|------|------|
| [flink_stream_processing_architecture.md](./flink_stream_processing_architecture.md) | ⭐ **Flink流处理架构详解** - Kafka消费、窗口对齐、数据流分支 (最新) |
| [new_system_architecture_spec.md](./new_system_architecture_spec.md) | **云原生系统架构规范** - 微服务架构、数据流、技术栈 |
| [UNIFIED_FLINK_ARCHITECTURE.md](./UNIFIED_FLINK_ARCHITECTURE.md) | **统一Flink架构** - PostgreSQL持久化移入Flink |
| [original_system_analysis.md](./original_system_analysis.md) | **原系统分析** - C#/Windows系统分析和迁移对比 |
| [data_structures.md](./data_structures.md) | **数据结构定义** - Kafka消息格式、PostgreSQL表结构 |
| [log_format_analysis.md](./log_format_analysis.md) | **日志格式分析** - Syslog格式、字段说明、解析规则 |

---

### 🎯 蜜罐机制与威胁评分

| 文档 | 说明 |
|------|------|
| [honeypot_based_threat_scoring.md](./honeypot_based_threat_scoring.md) | ⭐ **蜜罐威胁评分方案** - 基于蜜罐机制的评分算法详解 |
| [threat_scoring_solution.md](./threat_scoring_solution.md) | **威胁评分解决方案** - 评分公式、权重计算、等级划分 |
| [understanding_aggregation_metrics.md](./understanding_aggregation_metrics.md) | **聚合指标理解** - uniqueIps、uniquePorts、uniqueDevices含义 |
| [understanding_corrections_summary.md](./understanding_corrections_summary.md) | **理解修正总结** - 概念纠正、机制说明 |
| [HONEYPOT_QUICK_REFERENCE.md](./HONEYPOT_QUICK_REFERENCE.md) | **蜜罐快速参考** - 快速查阅手册 |

---

### 📚 开发规范与指南

| 文档 | 说明 |
|------|------|
| [PROJECT_STANDARDS.md](./PROJECT_STANDARDS.md) | **项目标准** - 编码规范、命名约定、Git流程 |
| [database_initialization_guide.md](./database_initialization_guide.md) | **数据库初始化指南** - 数据库部署、初始化脚本 |
| [debugging_and_testing_guide.md](./debugging_and_testing_guide.md) | **调试测试指南** - 调试方法、测试策略、工具使用 |
| [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) | **快速参考** - 常用命令、配置、故障排查 |
| [DEBUG_TEST_INDEX.md](./DEBUG_TEST_INDEX.md) | **调试测试索引** - 测试工具和脚本索引 |

---

## 🎓 学习路径

### 新开发者入门

```
1. 阅读架构文档
   ├── new_system_architecture_spec.md (了解整体架构)
   ├── flink_stream_processing_architecture.md (Flink流处理详解 ⭐ 必读)
   └── data_structures.md (理解数据模型)

2. 理解核心机制
   ├── honeypot_based_threat_scoring.md (蜜罐机制 ⭐ 核心)
   ├── understanding_corrections_summary.md (概念纠正)
   └── log_format_analysis.md (日志格式)

3. 掌握开发规范
   ├── PROJECT_STANDARDS.md (编码规范)
   ├── debugging_and_testing_guide.md (调试方法)
   └── QUICK_REFERENCE.md (常用命令)
```

### 算法研究者

```
1. 威胁评分算法
   ├── honeypot_based_threat_scoring.md (评分算法详解)
   ├── threat_scoring_solution.md (评分公式)
   └── understanding_aggregation_metrics.md (指标含义)

2. 原系统对比
   └── original_system_analysis.md (对比分析)
```

### 运维工程师

```
1. 部署指南
   ├── database_initialization_guide.md (数据库部署)
   └── QUICK_REFERENCE.md (运维命令)

2. 故障排查
   ├── debugging_and_testing_guide.md (调试方法)
   └── DEBUG_TEST_INDEX.md (测试工具)
```

---

## 🔑 核心概念

### 蜜罐机制理解

**关键**: 本系统是内网蜜罐/欺骗防御系统，而非传统边界防御

```
终端设备 (dev_serial) 功能:
1. 监控二层网络，收集设备在线情况
2. 识别未使用IP作为诱饵 (虚拟哨兵)
3. 主动响应ARP/ICMP，诱导攻击者进一步行动
4. 记录攻击者对诱饵的端口访问尝试

关键字段正确理解:
- response_ip: 诱饵IP (不存在的虚拟哨兵)
- response_port: 攻击者尝试的端口 (暴露攻击意图)
- attack_ip: 被诱捕的内网失陷主机IP
- attack_mac: 被诱捕的内网失陷主机MAC

威胁来源: 内网失陷主机 (已被攻破的内部设备)
检测对象: APT横向移动、勒索软件传播、内部渗透
关键特征: 所有访问诱饵IP的行为都是恶意的 (误报率极低)
```

**详细说明**: 参见 [honeypot_based_threat_scoring.md](./honeypot_based_threat_scoring.md)

---

### 威胁评分公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × ipWeight 
            × portWeight 
            × deviceWeight

其中:
- attackCount: 探测次数
- uniqueIps: 访问的诱饵IP数量 (横向移动范围)
- uniquePorts: 尝试的端口种类 (攻击意图多样性)
- uniqueDevices: 检测到该攻击者的蜜罐设备数
- timeWeight: 时间权重 (深夜1.2, 工作时间1.0)
- ipWeight: IP权重 (1个=1.0, 10+个=2.0)
- portWeight: 端口权重 (1个=1.0, 20+个=2.0)
- deviceWeight: 设备权重 (1个=1.0, 2+个=1.5)
```

**详细算法**: 参见 [threat_scoring_solution.md](./threat_scoring_solution.md)

---

### 系统架构

```
┌─────────────┐
│   rsyslog   │ (转发syslog)
└──────┬──────┘
       ↓
┌─────────────────────────────────────────┐
│  Data Ingestion Service (8080)          │
│  - 日志解析                              │
│  - 客户映射                              │
└──────┬──────────────────────────────────┘
       ↓ Kafka (attack-events, status-events)
┌─────────────────────────────────────────┐
│  Stream Processing (Flink)              │
│  - 30秒窗口聚合                          │
│  - 2分钟威胁评分                         │
└──────┬──────────────────────────────────┘
       ↓ Kafka (threat-alerts)
┌─────────────────────────────────────────┐
│  Alert Management Service (8082)        │
│  - 告警管理                              │
│  - 邮件通知                              │
└─────────────────────────────────────────┘
       ↓
┌─────────────────────────────────────────┐
│  PostgreSQL                             │
│  - alerts, notifications                │
│  - threat_assessments                   │
│  - smtp_configs                         │
└─────────────────────────────────────────┘
```

**详细架构**: 参见 [new_system_architecture_spec.md](./new_system_architecture_spec.md)

---

## 🔗 相关资源

- **[API文档](../api/)** - REST API接口文档
- **[问题修复](../fixes/)** - 问题分析和修复记录
- **[进度报告](../progress/)** - 开发进度和里程碑

---

**最后更新**: 2025-10-27
