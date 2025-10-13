# 📚 历史文档归档说明

**归档日期**: 2025-10-11  
**归档原因**: 文档整理和结构优化

---

## 📋 归档文档列表

### 1. cloud_native_architecture.md
- **创建时间**: 2025-10-09
- **归档原因**: 早期云原生架构设计草稿,已被更完善的规范取代
- **关键信息**:
  - Kafka + Flink + ClickHouse 技术栈选型
  - 微服务架构设计理念
  - 数据流设计思路
- **已合并至**: `new_system_architecture_spec.md`
- **是否保留参考价值**: ✅ 中等 - 了解架构演进过程

---

### 2. complete_development_plan_2025.md
- **创建时间**: 2025-10-10
- **归档原因**: 早期开发计划,缺少详细的实施步骤和时间线
- **关键信息**:
  - 日志格式规格详细说明
  - 数据模型定义
  - 环境配置要求
  - 前端系统技术栈 (Vue.js 2.6 + Element UI)
- **已合并至**: 
  - 日志格式 → `log_format_analysis.md`
  - 数据模型 → `data_structures.md`
  - 开发计划 → `IMPLEMENTATION_ROADMAP.md` 系列
- **是否保留参考价值**: ✅ 高 - 前端系统信息、详细日志格式说明

---

### 3. next_action_plan_2025.md
- **创建时间**: 2025-10-08
- **归档原因**: 10-11月短期行动计划,已被8个月完整路线图取代
- **关键信息**:
  - 环境验证脚本 (Ubuntu 24.04.3, Java 21.0.8, Maven 3.8.7)
  - 开发环境快速检查清单
  - rsyslog配置示例
  - 数据摄入服务开发要点
- **已合并至**: `IMPLEMENTATION_ROADMAP.md` (Phase 0)
- **是否保留参考价值**: ✅ 中等 - 环境检查脚本仍可使用

---

### 4. optimization_summary.md
- **创建时间**: 2025-10-11
- **归档原因**: MySQL存储过程优化方案,已不适用于云原生Flink架构
- **关键信息**:
  - MySQL多层聚合性能问题分析
  - 存储过程优化建议 (内存表、增量计算、分区表)
  - 3阶段优化实施计划
- **已合并至**: `analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md` (部分理念)
- **是否保留参考价值**: ⚠️ 低 - 仅作历史参考,技术栈已变更

---

### 5. project_summary.md
- **创建时间**: 2025-10-11
- **归档原因**: 早期项目总结,信息已被INDEX.md和实施路线图覆盖
- **关键信息**:
  - 传统系统痛点分析 (多层聚合延迟、手动离线导入)
  - 威胁评分公式说明
  - 已完成工作清单 (架构设计、离线数据导入方案)
- **已合并至**: `INDEX.md` (项目概览), `IMPLEMENTATION_ROADMAP.md` (详细计划)
- **是否保留参考价值**: ⚠️ 低 - 信息已整合到新文档

---

### 6. README_CORRECTIONS.md
- **创建时间**: 2025-10-08
- **归档原因**: 早期蜜罐机制理解修正,已被更完整的总结文档取代
- **关键信息**:
  - 蜜罐机制核心纠正 (response_ip是诱饵而非真实服务器)
  - 数据字段正确理解 (attack_mac是被诱捕的内网主机)
  - 误报率低的原因分析
- **已合并至**: 
  - `understanding_corrections_summary.md`
  - `FINAL_SUMMARY_HONEYPOT_CORRECTIONS.md`
  - `honeypot_based_threat_scoring.md`
- **是否保留参考价值**: ⚠️ 低 - 信息已完全整合

---

## 🔍 如何使用历史文档

### 适合查阅历史文档的场景

1. **了解架构演进**: 查看 `cloud_native_architecture.md` 了解从传统架构到云原生的思考过程
2. **环境配置参考**: 查看 `next_action_plan_2025.md` 中的环境检查脚本
3. **前端开发**: 查看 `complete_development_plan_2025.md` 中的前端技术栈信息 (Vue.js + Element UI)
4. **日志格式细节**: 查看 `complete_development_plan_2025.md` 中的详细日志字段说明
5. **MySQL优化理念**: 查看 `optimization_summary.md` 了解传统数据库优化思路 (虽不直接适用)

### 不建议查阅历史文档的场景

1. **当前实施指导**: 请使用 `IMPLEMENTATION_ROADMAP.md` 系列 (最新的8个月路线图)
2. **架构设计**: 请使用 `new_system_architecture_spec.md` (最新的架构规范)
3. **蜜罐机制理解**: 请使用 `honeypot_based_threat_scoring.md` 或 `understanding_corrections_summary.md`
4. **数据结构定义**: 请使用 `data_structures.md` (最新的Kafka消息和PostgreSQL表定义)

---

## 📊 关键信息提取

### 从历史文档中保留的核心价值

#### 1. 环境配置脚本 (来自 next_action_plan_2025.md)

```bash
#!/bin/bash
# 开发环境快速检查脚本
echo "=== 威胁检测系统开发环境检查 ==="

# Ubuntu版本
if lsb_release -a 2>/dev/null | grep -q "24.04"; then
    echo "✅ Ubuntu 24.04 LTS"
else
    echo "❌ 需要Ubuntu 24.04 LTS"
fi

# Java版本
if java -version 2>&1 | grep -q "21"; then
    echo "✅ OpenJDK 21"
else
    echo "❌ 需要OpenJDK 21"
fi

# Maven版本
if mvn --version 2>&1 | grep -q "3.8"; then
    echo "✅ Maven 3.8+"
else
    echo "❌ 需要Maven 3.8+"
fi

# Docker版本
if docker --version 2>&1 | grep -q "20"; then
    echo "✅ Docker 20.10+"
else
    echo "❌ 需要Docker 20.10+"
fi
```

**用途**: Phase 0开发环境准备时可直接使用

---

#### 2. 前端系统信息 (来自 complete_development_plan_2025.md)

- **技术栈**: Vue.js 2.6 + Element UI
- **功能模块**: 设备管理、威胁监控、告警管理、用户权限管理
- **基础**: 基于景治客户管理系统的定制开发
- **API对接**: 需要与新系统的 `api-gateway` 服务对接

**用途**: 如果未来需要重构前端系统,可参考这些信息

---

#### 3. 详细日志格式说明 (来自 complete_development_plan_2025.md)

**攻击事件字段 (log_type=1)**:
- `syslog_version`: 日志协议版本 (如1.10.0)
- `dev_serial`: 设备序列号 (如9d262111f2476d34)
- `attack_mac`: 被诱捕主机MAC地址 (内网失陷主机)
- `attack_ip`: 被诱捕主机IP地址
- `response_ip`: 诱饵IP (不存在的虚拟哨兵)
- `response_port`: 攻击者尝试的端口 (暴露攻击意图)
- `log_time`: Unix时间戳
- `eth_type`: 以太网协议类型 (2048=IPv4)
- `ip_type`: IP协议类型 (6=TCP, 17=UDP, 1=ICMP)

**状态事件字段 (log_type=2)**:
- `dev_serial`: 设备序列号
- `dev_status`: 设备状态 (0=正常, 1=异常)
- `attack_cnt_1min`: 1分钟攻击计数
- `attack_cnt_total`: 累计攻击计数

**用途**: 开发日志解析服务时的详细参考

---

#### 4. 传统系统痛点 (来自 project_summary.md)

| 痛点 | 具体表现 | 影响 |
|------|---------|------|
| 多层聚合延迟 | 原始数据 → 1分钟视图 → 10分钟视图 → 总分视图 | 至少10分钟处理延迟 |
| 手动离线导入 | 需要手动下载文件、解压、导入MySQL | 数小时操作,易出错 |
| 复杂SQL维护 | 存储过程、触发器、多层视图嵌套 | 难以调试和扩展 |
| 单体架构 | 所有逻辑集中在MySQL | 无法水平扩展 |

**用途**: 向利益相关者解释为什么要迁移到云原生架构

---

#### 5. MySQL优化理念 (来自 optimization_summary.md)

虽然不直接适用于Flink架构,但以下优化理念仍有参考价值:

| 理念 | MySQL实现 | Flink对应实现 |
|------|-----------|--------------|
| 减少聚合层级 | 直接10分钟聚合 | ✅ 多层级窗口 (30s/5min/15min) |
| 增量计算 | 内存表缓存 | ✅ Flink状态管理 |
| 配置驱动 | 配置表管理权重 | ✅ PostgreSQL配置表 + 服务动态加载 |
| 内存优化 | Memory表 | ✅ RocksDB状态后端 |

**用途**: 理解优化思路的演进

---

## 🗑️ 未来清理计划

### 可以删除的文档 (保留6个月后)

以下文档的信息已完全整合,且无独特参考价值:
- `README_CORRECTIONS.md` (2026年4月后可删除)
- `project_summary.md` (2026年4月后可删除)

### 建议长期保留的文档

以下文档具有历史参考价值,建议长期保留:
- `complete_development_plan_2025.md` (前端信息、详细日志格式)
- `next_action_plan_2025.md` (环境配置脚本)
- `cloud_native_architecture.md` (架构演进思路)
- `optimization_summary.md` (优化理念对比)

---

## 📞 文档维护

- **维护责任人**: 项目经理 / 技术负责人
- **更新频率**: 每季度审查一次
- **删除策略**: 归档满1年且无参考价值的文档可删除

---

**最后更新**: 2025-10-11  
**归档文档数量**: 6份  
**保留参考价值**: 中等 (主要为历史追溯和特定场景参考)
