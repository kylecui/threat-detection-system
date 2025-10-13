# 📚 云原生威胁检测系统 - 完整文档索引

**项目**: 云原生威胁检测系统 V2.0  
**更新日期**: 2025-10-11  
**文档版本**: 1.0

---

## 🎯 快速导航

### 🚀 立即开始

1. **新手入门**: 阅读 [README.md](../README.md)
2. **实施路线图**: 阅读 [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) (本文档)
3. **技术分析**: 浏览 [analysis/](analysis/) 目录下的8个专项分析

---

## 📋 文档分类

### 一、实施指南 (最重要)

| 文档 | 描述 | 受众 | 优先级 |
|------|------|------|--------|
| **[IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md)** | 总体路线图、Phase 0-1详细计划 | 全员 | ⭐⭐⭐⭐⭐ |
| **[IMPLEMENTATION_ROADMAP_PART2.md](IMPLEMENTATION_ROADMAP_PART2.md)** | Phase 2-3详细计划 (ML、APT) | 开发团队 | ⭐⭐⭐⭐⭐ |
| **[IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md)** | 测试策略、风险管理、团队配置 | PM、QA、DevOps | ⭐⭐⭐⭐⭐ |

**阅读建议**: 按顺序阅读这3份文档，每份约30-40分钟

---

### 二、技术分析报告 (深度参考)

| 文档 | 主题 | 关键结论 | 优先级 |
|------|------|---------|--------|
| **[analysis/01_time_window_analysis.md](analysis/01_time_window_analysis.md)** | 时间窗口有效性 | 推荐多层级窗口 (30s-24h) | ⭐⭐⭐⭐ |
| **[analysis/02_port_weight_quantification.md](analysis/02_port_weight_quantification.md)** | 端口权重量化 | 5维度科学量化方法 | ⭐⭐⭐⭐⭐ |
| **[analysis/03_additional_scoring_dimensions.md](analysis/03_additional_scoring_dimensions.md)** | 额外评分维度 | 5个新维度,WannaCry评分+46.8x | ⭐⭐⭐⭐ |
| **[analysis/04_apt_persistence_modeling.md](analysis/04_apt_persistence_modeling.md)** | APT持续性建模 | 混合模型 (状态机+时间加权) | ⭐⭐⭐⭐⭐ |
| **[analysis/05_honeypot_specific_optimizations.md](analysis/05_honeypot_specific_optimizations.md)** | 蜜罐专项优化 | 分层蜜罐+动态诱饵 | ⭐⭐⭐ |
| **[analysis/06_false_positive_avoidance.md](analysis/06_false_positive_avoidance.md)** | 误报规避策略 | 规则+ML混合过滤 | ⭐⭐⭐⭐⭐ |
| **[analysis/07_attack_persistence_representation.md](analysis/07_attack_persistence_representation.md)** | 攻击持续性表征 | 持续性评分+演化轨迹 | ⭐⭐⭐⭐ |
| **[analysis/08_switch_flood_data_value.md](analysis/08_switch_flood_data_value.md)** | 洪泛数据价值 | ARP分析提前检测3-30分钟 | ⭐⭐⭐ |
| **[analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md](analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md)** | 综合优化方案 | 汇总所有优化方向 | ⭐⭐⭐⭐⭐ |

**阅读建议**: 根据工作职责选择性阅读

---

### 三、系统架构与核心概念

| 文档 | 描述 | 关键内容 | 优先级 |
|------|------|---------|--------|
| **[new_system_architecture_spec.md](new_system_architecture_spec.md)** | 云原生系统架构规范 | 微服务设计、技术栈、部署架构 | ⭐⭐⭐⭐⭐ |
| **[honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md)** | 蜜罐机制核心理解 | response_ip是诱饵,攻击意图识别 | ⭐⭐⭐⭐⭐ |
| **[understanding_corrections_summary.md](understanding_corrections_summary.md)** | 理解修正总结 | 蜜罐vs边界防御的关键区别 | ⭐⭐⭐⭐ |
| **[FINAL_SUMMARY_HONEYPOT_CORRECTIONS.md](FINAL_SUMMARY_HONEYPOT_CORRECTIONS.md)** | 蜜罐机制最终总结 | 完整的概念修正和数据理解 | ⭐⭐⭐⭐ |
| **[original_system_analysis.md](original_system_analysis.md)** | 原始C#系统分析 | 旧系统架构、迁移对比 | ⭐⭐⭐ |

---

### 四、数据结构与算法

| 文档 | 描述 | 关键内容 |
|------|------|---------|
| **[data_structures.md](data_structures.md)** | 数据结构定义 | Kafka消息格式、PostgreSQL表结构、字段说明 |
| **[log_format_analysis.md](log_format_analysis.md)** | 日志格式分析 | syslog格式、解析规则、字段映射 |
| **[threat_scoring_solution.md](threat_scoring_solution.md)** | 威胁评分解决方案 | 评分公式、权重计算、分级标准 |

---

### 五、历史文档 (归档)

以下文档已移至 `history/` 目录,仅作历史参考:

| 文档 | 归档原因 | 关键信息已合并至 |
|------|---------|----------------|
| **[cloud_native_architecture.md](history/cloud_native_architecture.md)** | 早期架构设计,已被更新版本取代 | `new_system_architecture_spec.md` |
| **[complete_development_plan_2025.md](history/complete_development_plan_2025.md)** | 早期开发计划,已被实施路线图取代 | `IMPLEMENTATION_ROADMAP.md` 系列 |
| **[next_action_plan_2025.md](history/next_action_plan_2025.md)** | 10-11月短期计划,已过时 | `IMPLEMENTATION_ROADMAP.md` (Phase 0) |
| **[optimization_summary.md](history/optimization_summary.md)** | MySQL优化方案,已不适用云原生架构 | `analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md` |
| **[project_summary.md](history/project_summary.md)** | 早期项目总结,信息已过时 | 本文档 (INDEX.md) |
| **[README_CORRECTIONS.md](history/README_CORRECTIONS.md)** | 早期理解修正,已被整合 | `understanding_corrections_summary.md` |

**注意**: 历史文档中的部分技术细节(如MySQL存储过程优化、传统架构分析)仍有参考价值,但不作为当前实施的依据。

---

## 🗺️ 实施路线图概览

### Phase 0: 系统对齐与替代 (Month 1-2)

**目标**: 新系统完全替代旧C#系统

**关键里程碑**:
- Week 2: 端口权重配置对齐 ✅
- Week 4: 双系统对比验证 ✅
- Week 6: 生产环境准备完成 ✅
- Week 8: 旧系统下线 🎉

**交付物**:
- ✅ 端口权重配置表 (219条)
- ✅ 对比测试报告 (误差 < 5%)
- ✅ 生产K8s配置
- ✅ 监控告警系统

**详见**: [IMPLEMENTATION_ROADMAP.md#phase-0](IMPLEMENTATION_ROADMAP.md)

---

### Phase 1: 核心算法优化 (Month 3-4)

**目标**: 提升检测准确性和实时性

**关键功能**:
- ✅ 多层级时间窗口 (30s勒索软件检测)
- ✅ 科学端口权重 (5维度量化)
- ✅ 横向移动检测 (跨子网分析)

**预期效果**:
- 勒索软件检出: 10分钟 → **30秒** (-95%)
- 端口权重准确性: **+40%**
- APT检出覆盖率: **+15%**

**详见**: [IMPLEMENTATION_ROADMAP.md#phase-1](IMPLEMENTATION_ROADMAP.md)

---

### Phase 2: 智能增强 (Month 5-6)

**目标**: 引入机器学习和高级分析

**关键功能**:
- ✅ ML误报过滤 (Random Forest, 96%准确率)
- ✅ 端口序列模式识别 (50+恶意软件指纹)
- ✅ 行为基线建立 (30天Z-score分析)

**预期效果**:
- 误报率: 2% → **0.1%** (-95%)
- 真实威胁检出率: 95% → **98%**
- 人工审核工作量: **-95%**

**详见**: [IMPLEMENTATION_ROADMAP_PART2.md#phase-2](IMPLEMENTATION_ROADMAP_PART2.md)

---

### Phase 3: 高级威胁建模 (Month 7-8)

**目标**: APT检测和持续性威胁追踪

**关键功能**:
- ✅ APT状态机 (6阶段检测)
- ✅ 持续性评分模型
- ✅ 演化轨迹追踪
- ✅ 洪泛数据集成

**预期效果**:
- APT检出率: 60% → **95%** (+58%)
- APT检出时间: 30天 → **7天** (-77%)
- 提前检测: **3-30分钟** (洪泛数据)

**详见**: [IMPLEMENTATION_ROADMAP_PART2.md#phase-3](IMPLEMENTATION_ROADMAP_PART2.md)

---

## 📊 预期成果对比

| 指标 | 现状 | Phase 1 | Phase 2 | Phase 3 | 提升 |
|------|------|---------|---------|---------|------|
| **APT检出率** | 60% | 75% | 85% | **95%** | **+58%** |
| **APT检出时间** | 30天 | 20天 | 14天 | **7天** | **-77%** |
| **误报率** | 2% | 1.5% | **0.1%** | 0.1% | **-95%** |
| **勒索软件检出** | 10分钟 | **30秒** | 30秒 | 30秒 | **-95%** |
| **端到端延迟** | 10分钟 | **4分钟** | 3.5分钟 | 3分钟 | **-70%** |
| **告警数量** | 1000/天 | 800/天 | 400/天 | **50/天** | **-95%** |

---

## 👥 团队与资源

### 核心团队 (7.5 FTE)

| 角色 | FTE | 主要职责 |
|------|-----|---------|
| 项目经理 | 1.0 | 整体规划、进度管理 |
| 架构师 | 0.5 | 技术决策、架构审查 |
| Flink开发 | 2.0 | 流处理逻辑、算法实现 |
| ML工程师 | 1.0 | ML模型训练、部署 |
| 后端开发 | 1.0 | 微服务开发、API实现 |
| DevOps | 0.5 | 部署自动化、监控 |
| 测试工程师 | 1.0 | 测试策略、测试执行 |
| 安全分析师 | 0.5 | 需求验证、数据标注 |

### 预算估算

| 项目 | 成本 | 说明 |
|------|------|------|
| 人力成本 | ~$300K | 7.5 FTE × 8个月 × $5K/月 |
| 云资源 | ~$680 | $85/月 × 8个月 (500台设备) |
| 工具&服务 | ~$2K | NVD API、OTX API、测试工具 |
| **总计** | **~$303K** | 8个月项目周期 |

**ROI**: 
- 安全事件响应时间 -77%
- 误报处理成本 -95%
- APT损失风险 -60%
- **预计1年内回本**

---

## 🚨 关键风险

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|---------|
| ML模型过拟合 | 中 | 高 | 交叉验证、A/B测试、持续监控 |
| 生产切换失败 | 低 | 极高 | 回滚预案、灰度发布、双系统并行 |
| 性能瓶颈 | 中 | 高 | 提前压测、资源预留、水平扩展 |

**详见**: [IMPLEMENTATION_ROADMAP_PART3.md#风险管理](IMPLEMENTATION_ROADMAP_PART3.md)

---

## ✅ 关键成功因素

1. **数据质量**: 确保蜜罐数据和洪泛数据准确性
2. **模型验证**: ML模型必须经过严格A/B测试
3. **分阶段上线**: 避免一次性大规模变更
4. **监控告警**: 实时监控系统性能和告警质量
5. **反馈循环**: 持续收集安全分析师反馈

---

## 📞 联系方式

### 技术问题

- **架构问题**: 参考 [new_system_architecture_spec.md](new_system_architecture_spec.md)
- **算法问题**: 参考 [analysis/](analysis/) 目录
- **实施问题**: 参考 [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md)

### 紧急联系

- **项目经理**: [PM姓名] - [邮箱]
- **技术负责人**: [Tech Lead姓名] - [邮箱]
- **On-call**: [手机号]

---

## 📅 重要日期

| 里程碑 | 日期 | 状态 |
|--------|------|------|
| 项目启动 | 2025-11-01 | ⏳ 计划中 |
| Phase 0完成 (系统替代) | 2025-12-31 | ⏳ 计划中 |
| Phase 1完成 (核心优化) | 2026-02-28 | ⏳ 计划中 |
| Phase 2完成 (智能增强) | 2026-04-30 | ⏳ 计划中 |
| Phase 3完成 (高级建模) | 2026-06-30 | ⏳ 计划中 |
| 项目验收 | 2026-07-15 | ⏳ 计划中 |

---

## 🔄 文档更新记录

| 版本 | 日期 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 1.0 | 2025-10-11 | 初始版本,完整实施路线图 | GitHub Copilot |

---

## 📖 推荐阅读路径

### 🚀 快速入门 (1-2小时)

**目标**: 快速了解项目全貌和实施计划

1. **本文档 (INDEX.md)** - 5分钟了解文档结构
2. **[honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md)** - 10分钟理解蜜罐机制核心
3. **[IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md)** - 40分钟掌握Phase 0-1实施计划
4. **[new_system_architecture_spec.md](new_system_architecture_spec.md)** - 30分钟了解系统架构

---

### 👔 项目经理阅读路径

**目标**: 掌握项目规划、资源需求、风险控制

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | 40分钟 | 整体规划、里程碑、Phase 0-1详细计划 |
| 2 | [IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md) | 30分钟 | 团队配置、风险管理、预算估算 |
| 3 | [analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md](analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md) | 40分钟 | 综合优化方案、ROI分析 |
| 4 | [IMPLEMENTATION_ROADMAP_PART2.md](IMPLEMENTATION_ROADMAP_PART2.md) | 30分钟 | Phase 2-3计划、ML增强方案 |

**可选深入**:
- [analysis/08_switch_flood_data_value.md](analysis/08_switch_flood_data_value.md) - 洪泛数据成本收益分析

---

### 👨‍💻 Flink开发工程师阅读路径

**目标**: 理解算法逻辑、掌握开发任务

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md) | 10分钟 | 蜜罐机制、数据字段正确理解 |
| 2 | [threat_scoring_solution.md](threat_scoring_solution.md) | 20分钟 | 威胁评分公式、权重计算 |
| 3 | [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | 40分钟 | Phase 0端口权重迁移、Phase 1多层级窗口 |
| 4 | [analysis/01_time_window_analysis.md](analysis/01_time_window_analysis.md) | 30分钟 | 时间窗口设计理论 |
| 5 | [analysis/02_port_weight_quantification.md](analysis/02_port_weight_quantification.md) | 40分钟 | 端口权重量化方法 |
| 6 | [analysis/04_apt_persistence_modeling.md](analysis/04_apt_persistence_modeling.md) | 40分钟 | APT状态机设计 |
| 7 | [data_structures.md](data_structures.md) | 20分钟 | Kafka消息格式、状态结构 |

**可选深入**:
- [analysis/03_additional_scoring_dimensions.md](analysis/03_additional_scoring_dimensions.md) - 额外评分维度
- [analysis/07_attack_persistence_representation.md](analysis/07_attack_persistence_representation.md) - 持续性表征

---

### 🤖 ML工程师阅读路径

**目标**: 掌握特征工程、模型训练、部署方案

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md) | 10分钟 | 蜜罐特性、误报来源 |
| 2 | [analysis/06_false_positive_avoidance.md](analysis/06_false_positive_avoidance.md) | 40分钟 | ML过滤策略、特征工程理论 |
| 3 | [IMPLEMENTATION_ROADMAP_PART2.md](IMPLEMENTATION_ROADMAP_PART2.md) | 30分钟 | Week 17-18 ML详细实现方案 |
| 4 | [analysis/03_additional_scoring_dimensions.md](analysis/03_additional_scoring_dimensions.md) | 30分钟 | 端口序列、演化趋势等特征 |
| 5 | [IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md) | 20分钟 | ML模型测试策略 |

**实践任务**:
- 阅读后立即开发数据标注工具 (Flask Web界面)
- 提取16维特征并进行探索性数据分析 (EDA)

---

### 🧪 测试工程师阅读路径

**目标**: 掌握测试策略、编写测试用例

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md) | 40分钟 | 完整测试策略、测试金字塔、代码示例 |
| 2 | [threat_scoring_solution.md](threat_scoring_solution.md) | 20分钟 | 评分公式、边界条件 |
| 3 | [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | 30分钟 | 各Phase的测试章节、验收标准 |
| 4 | [data_structures.md](data_structures.md) | 20分钟 | 数据格式验证 |

**测试场景设计**:
- 勒索软件场景: 1000 attacks/min × 5 IPs × 10 ports → Score > 1,000,000
- 正常扫描场景: 10 attacks/min × 1 IP × 3 ports → Score < 5,000
- 边界条件: 时间权重切换点 (0:00, 6:00, 9:00, 17:00, 21:00)

---

### 🔧 DevOps工程师阅读路径

**目标**: 掌握部署架构、CI/CD流程、监控体系

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [new_system_architecture_spec.md](new_system_architecture_spec.md) | 30分钟 | 微服务架构、K8s部署 |
| 2 | [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | 30分钟 | Week 5-6生产环境准备 |
| 3 | [IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md) | 30分钟 | CI/CD流程、监控体系 |
| 4 | [data_structures.md](data_structures.md) | 15分钟 | Kafka topic配置、数据库表结构 |

**实践任务**:
- 配置GitHub Actions工作流
- 部署Prometheus + Grafana监控栈
- 准备应急回滚脚本

---

### 🛡️ 安全分析师阅读路径

**目标**: 理解威胁模型、提供需求反馈

| 顺序 | 文档 | 阅读时间 | 重点内容 |
|------|------|---------|---------|
| 1 | [honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md) | 15分钟 | 蜜罐机制、检测原理 |
| 2 | [understanding_corrections_summary.md](understanding_corrections_summary.md) | 15分钟 | 蜜罐vs边界防御 |
| 3 | [analysis/04_apt_persistence_modeling.md](analysis/04_apt_persistence_modeling.md) | 40分钟 | APT检测模型 |
| 4 | [analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md](analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md) | 40分钟 | 完整威胁覆盖方案 |
| 5 | [IMPLEMENTATION_ROADMAP_PART2.md](IMPLEMENTATION_ROADMAP_PART2.md) | 20分钟 | Week 17-18数据标注任务 |

**参与任务**:
- Week 17-18: 标注2000+告警样本 (70%真实威胁, 30%误报)
- Week 19-20: 提供50+恶意软件端口序列指纹
- 持续: 验证告警质量,提供反馈

---

### 🎓 新成员入职阅读路径

**目标**: 3天内快速上手项目

**Day 1: 理解业务和架构**
1. [INDEX.md](INDEX.md) - 10分钟了解文档结构
2. [honeypot_based_threat_scoring.md](honeypot_based_threat_scoring.md) - 20分钟理解蜜罐核心
3. [new_system_architecture_spec.md](new_system_architecture_spec.md) - 1小时掌握系统架构
4. [original_system_analysis.md](original_system_analysis.md) - 30分钟了解迁移背景

**Day 2: 掌握实施计划**
1. [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) - 1小时了解Phase 0-1
2. [IMPLEMENTATION_ROADMAP_PART2.md](IMPLEMENTATION_ROADMAP_PART2.md) - 1小时了解Phase 2-3
3. [IMPLEMENTATION_ROADMAP_PART3.md](IMPLEMENTATION_ROADMAP_PART3.md) - 1小时了解测试和风险

**Day 3: 深入技术细节**
1. [data_structures.md](data_structures.md) - 30分钟了解数据格式
2. [threat_scoring_solution.md](threat_scoring_solution.md) - 30分钟了解评分算法
3. 根据岗位选择 `analysis/` 目录下的专项分析文档 - 2小时

---

### 📚 文档维护者阅读路径

**目标**: 保持文档最新和一致性

**定期审查**:
1. 每月检查 `IMPLEMENTATION_ROADMAP` 系列,更新进度状态
2. 每季度审查 `history/` 目录,决定是否删除过时文档
3. 每半年更新 `analysis/COMPREHENSIVE_OPTIMIZATION_PLAN.md`,反映最新优化方向

**文档一致性检查清单**:
- [ ] 评分公式在所有文档中保持一致
- [ ] 蜜罐机制理解在所有文档中统一
- [ ] 时间线和里程碑与实际进度同步
- [ ] 技术栈版本号保持最新
- [ ] 交叉引用链接有效

---

**最后更新**: 2025-10-11  
**文档版本**: 1.0  
**维护者**: 项目团队

🎉 **祝您实施顺利！**
