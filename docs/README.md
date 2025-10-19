# 威胁检测系统文档中心

**项目**: Cloud-Native Threat Detection System  
**版本**: 2.0  
**最后更新**: 2025-01-16

---

## 📚 文档导航

### 🔌 [API文档](./api/) - REST API接口文档

完整的API文档，包含curl和Java代码示例。

⚠️ **重要**: 所有服务使用 **snake_case** 字段命名（如 `customer_id`, `subscription_tier`）。详见 [字段命名问题分析](./api/FIELD_NAMING_INCONSISTENCY.md)。

#### API Gateway (统一入口)
- **[API Gateway实现文档](./api/api_gateway_current.md)** - 路由管理、安全控制、熔断降级、监控告警
- **[API Gateway测试报告](./api/API_GATEWAY_TEST_REPORT.md)** - 部署测试结果、问题解决、性能指标 ⭐ 最新

#### 客户管理服务 (Customer Management)
- **[客户管理API](./api/customer_management_api.md)** - 客户CRUD、设备绑定、通知配置（26个端点）

#### 数据摄取服务 (Data Ingestion)
- **[数据摄取API](./api/data_ingestion_api.md)** - 日志摄取、批量处理、统计监控

#### 威胁评估服务 (Threat Assessment)
- **[威胁评估概述](./api/threat_assessment_overview.md)** - 系统架构、评分算法、数据模型
- **[评估操作API](./api/threat_assessment_evaluation_api.md)** - 执行评估、缓解措施
- **[查询分析API](./api/threat_assessment_query_api.md)** - 评估详情、趋势分析
- **[客户端指南](./api/threat_assessment_client_guide.md)** - Java客户端、最佳实践

#### 告警管理服务 (Alert Management)
- **[告警管理概述](./api/alert_management_overview.md)** - 系统架构、数据模型
- **[告警CRUD API](./api/alert_crud_api.md)** - 创建、查询、列表
- **[生命周期API](./api/alert_lifecycle_api.md)** - 状态更新、解决、分配、归档
- **[升级管理API](./api/alert_escalation_api.md)** - 手动升级、取消升级
- **[分析统计API](./api/alert_analytics_api.md)** - 告警统计、通知统计、升级统计
- **[通知管理API](./api/alert_notification_api.md)** - 手动发送邮件通知
- **[维护操作API](./api/alert_maintenance_api.md)** - 归档旧告警

#### 集成测试
- **[集成测试API](./api/integration_test_api.md)** - 测试统计、状态查询

#### 配置管理
- **[邮件通知配置](./api/email_notification_configuration.md)** - SMTP配置、客户通知设置、动态配置

---

### 🏗️ [设计文档](./design/) - 系统架构与设计

系统架构、数据结构、算法设计和技术规范。

#### 核心架构
- **[新系统架构规范](./design/new_system_architecture_spec.md)** - 云原生微服务架构设计
- **[原系统分析](./design/original_system_analysis.md)** - 原C#系统分析和对比
- **[数据结构定义](./design/data_structures.md)** - Kafka消息、数据库表结构
- **[日志格式分析](./design/log_format_analysis.md)** - Syslog格式、字段说明

#### 蜜罐机制与威胁评分
- **[蜜罐威胁评分方案](./design/honeypot_based_threat_scoring.md)** ⭐ - 基于蜜罐的评分算法详解
- **[威胁评分解决方案](./design/threat_scoring_solution.md)** - 评分公式和权重计算
- **[聚合指标理解](./design/understanding_aggregation_metrics.md)** - uniqueIps、uniquePorts含义
- **[理解修正总结](./design/understanding_corrections_summary.md)** - 概念纠正和机制说明
- **[蜜罐快速参考](./design/HONEYPOT_QUICK_REFERENCE.md)** - 快速查阅手册

#### 开发规范与指南
- **[项目标准](./design/PROJECT_STANDARDS.md)** - 编码规范、命名约定
- **[数据库初始化指南](./design/database_initialization_guide.md)** - 数据库部署和初始化
- **[调试测试指南](./design/debugging_and_testing_guide.md)** - 调试方法和测试策略
- **[快速参考](./design/QUICK_REFERENCE.md)** - 常用命令和配置
- **[调试测试索引](./design/DEBUG_TEST_INDEX.md)** - 测试工具索引

---

### 🔧 [问题修复](./fixes/) - 问题分析与修复记录

系统问题分析、修复方案和经验总结。

#### 最新发现 (2025-01-16)
- **[字段命名不一致问题](./api/FIELD_NAMING_INCONSISTENCY.md)** ⚠️ P0 - API文档与实际实现字段命名不一致
  - **问题**: 文档使用camelCase，实际服务使用snake_case
  - **影响**: API集成、前端开发、测试脚本
  - **解决方案**: 统一使用snake_case（推荐）
  - **状态**: 🟡 进行中

#### 重大修复记录
- **[数据库修复报告](./fixes/DATABASE_FIX_REPORT.md)** - 数据库初始化问题修复
- **[邮件通知问题](./fixes/EMAIL_NOTIFICATION_ISSUE.md)** - SMTP配置和通知系统修复
- **[修复完成总结](./fixes/FIX_COMPLETE.md)** - 主要问题修复汇总
- **[启动稳定性改进](./fixes/startup_stability_improvements.md)** - 服务启动问题修复

#### 关键挑战分析
- **[蜜罐机制修正总结](./fixes/FINAL_SUMMARY_HONEYPOT_CORRECTIONS.md)** - 核心概念纠正
- **[关键挑战分析](./fixes/CRITICAL_CHALLENGES_ANALYSIS.md)** - 技术难点和解决方案
- **[项目不一致性分析](./fixes/PROJECT_INCONSISTENCIES_ANALYSIS.md)** - 代码和文档一致性问题
- **[P0优先级修复清单](./fixes/P0_FIX_CHECKLIST.md)** - 必须修复的问题列表

---

### 📊 [进度报告](./progress/) - 开发进度与里程碑

项目实施进度、阶段总结和里程碑报告。

#### 实施路线图
- **[实施路线图 Part 1](./progress/IMPLEMENTATION_ROADMAP.md)** - 第一阶段计划
- **[实施路线图 Part 2](./progress/IMPLEMENTATION_ROADMAP_PART2.md)** - 第二阶段计划
- **[实施路线图 Part 3](./progress/IMPLEMENTATION_ROADMAP_PART3.md)** - 第三阶段计划

#### MVP开发
- **[MVP执行摘要](./progress/MVP_EXECUTIVE_SUMMARY.md)** - MVP项目总览
- **[MVP实施计划](./progress/MVP_IMPLEMENTATION_PLAN.md)** - MVP开发计划
- **[MVP快速决策指南](./progress/MVP_QUICK_DECISION_GUIDE.md)** - MVP决策参考
- **[MVP README](./progress/MVP_README.md)** - MVP说明文档
- **[MVP时间线可视化](./progress/MVP_TIMELINE_VISUALIZATION.md)** - MVP进度时间线

#### 功能实施总结
- **[数据持久化实施总结](./progress/data_persistence_implementation_summary.md)** - 数据库集成实施
- **[设备健康监控实施](./progress/device_health_monitoring_implementation.md)** - 心跳监控功能
- **[蜜罐执行摘要](./progress/HONEYPOT_EXECUTIVE_SUMMARY.md)** - 蜜罐机制实施总结

#### 测试与分析报告
- **[端到端测试报告](./progress/E2E_TEST_REPORT_2025-10-15.md)** - 完整系统测试结果
- **[启动分析总结](./progress/STARTUP_ANALYSIS_SUMMARY.md)** - 启动流程分析
- **[文档清理报告](./progress/DOCUMENTATION_CLEANUP_REPORT.md)** - 文档整理记录

---

## 🗂️ 其他目录

- **[analysis/](./analysis/)** - 深度分析文档和研究报告
- **[history/](./history/)** - 历史文档和废弃内容

---

## 🚀 快速开始

### 新开发者入门
1. 阅读 **[新系统架构规范](./design/new_system_architecture_spec.md)** 了解系统架构
2. 阅读 **[蜜罐威胁评分方案](./design/honeypot_based_threat_scoring.md)** 理解核心机制
3. 阅读 **[数据摄取API](./api/data_ingestion_api.md)** 了解API使用方法
4. 参考 **[调试测试指南](./design/debugging_and_testing_guide.md)** 开始开发

### API集成开发
1. 查看 **[API文档目录](./api/)** 选择需要的服务
2. 每个API文档都包含完整的curl和Java示例
3. 参考 **[客户端指南](./api/threat_assessment_client_guide.md)** 了解最佳实践

### 问题排查
1. 查看 **[问题修复目录](./fixes/)** 寻找类似问题
2. 参考 **[调试测试指南](./design/debugging_and_testing_guide.md)** 
3. 检查 **[P0修复清单](./fixes/P0_FIX_CHECKLIST.md)** 确认已知问题

---

## 📖 文档编写规范

### API文档标准
- ✅ 包含curl和Java两种示例
- ✅ 提供完整的请求/响应JSON
- ✅ 说明错误码和异常处理
- ✅ 包含实际使用场景

### 设计文档标准
- ✅ 清晰的架构图和数据流图
- ✅ 详细的算法说明和公式
- ✅ 包含代码示例和配置示例
- ✅ 说明设计决策和权衡

### 修复文档标准
- ✅ 问题描述和复现步骤
- ✅ 根本原因分析
- ✅ 解决方案和验证方法
- ✅ 预防措施和最佳实践

---

## 🔗 相关链接

- **GitHub Repository**: [threat-detection-system](https://github.com/kylecui/threat-detection-system)
- **主项目README**: [../README.md](../README.md)
- **使用指南**: [../USAGE_GUIDE.md](../USAGE_GUIDE.md)

---

**文档中心最后更新**: 2025-01-16  
**维护者**: Kyle Cui  
**联系方式**: kylecui@outlook.com
