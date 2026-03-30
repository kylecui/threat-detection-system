# 📚 威胁检测系统文档索引

**版本**: 3.0  
**更新日期**: 2026-03-30  
**文档总数**: 130+ 文档

---

## 🎯 文档导航指南

**请按照标准三步流程使用文档系统：**

### 📋 第一步：项目概况
- **[README.md](../README.md)** — 项目概述、架构、技术栈、快速启动

### 🔍 第二步：本索引导航
- **[DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)** — 您正在阅读的文档，完整目录与分类

### 📂 第三步：深入具体文档
| 目录 | 说明 |
|------|------|
| `docs/api/` | REST API 接口文档 |
| `docs/design/` | 架构设计、领域规则、RBAC、ML、TIRE |
| `docs/deployment/` | K3s 部署指南、FAQ、运维手册 |
| `docs/testing/` | 测试方法和自动化脚本 |
| `docs/build/` | Docker 构建和部署指南 |
| `docs/fixes/` | Bug 修复和技术债务清理 |
| `docs/guides/` | 使用指南、配置指南 |
| `docs/progress/` | 进度报告、路线图 |

---

## ⭐ v3.0 新增文档 (2026-03-30)

| 文档 | 路径 | 说明 |
|------|------|------|
| **K3s 部署指南 + FAQ** | [deployment/k3s_deployment_guide.md](deployment/k3s_deployment_guide.md) | 单节点 K3s 完整部署流程，含 16 条 FAQ |
| **领域规则参考** | [design/domain_rules_reference.md](design/domain_rules_reference.md) | 6 条 V1 日志 + 设备管理 + 租户业务规则 |
| **RBAC 多租户架构** | [design/rbac_multi_tenant_architecture.md](design/rbac_multi_tenant_architecture.md) | 三级角色体系 (SuperAdmin → TenantAdmin → CustomerUser) |
| **ML 流水线训练指南** | [design/ml_pipeline_training_guide.md](design/ml_pipeline_training_guide.md) | PyTorch 3 层自编码器 + BiGRU, ONNX 推理, Kafka 集成 |
| **TIRE/LLM 集成文档** | [design/tire_llm_integration.md](design/tire_llm_integration.md) | 11 款插件配置、LLM 连接验证、Plugin Management UI |

---

## 📖 快速导航

### 🚀 核心文档 (必读)
| 文档 | 说明 | 状态 |
|------|------|------|
| [README.md](../README.md) | 项目概述、架构、部署 | ✅ v3.0 |
| [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) | 本索引 | ✅ v3.0 |
| [DEVELOPMENT_PLAN_AND_PROGRESS.md](DEVELOPMENT_PLAN_AND_PROGRESS.md) | 开发计划及进度 | ✅ |
| [DATA_STRUCTURES_AND_CONNECTIONS.md](DATA_STRUCTURES_AND_CONNECTIONS.md) | 数据结构和数据库连接 | ✅ |
| [CONTAINER_ARCHITECTURE_AND_APIS.md](CONTAINER_ARCHITECTURE_AND_APIS.md) | 容器结构和 API 配置 | ✅ |
| [API_COLLECTION_AND_REFERENCE.md](API_COLLECTION_AND_REFERENCE.md) | API 集合及快速参考 | ✅ |

---

## 📁 文档目录结构

```
docs/
├── DOCUMENTATION_INDEX.md                 # ⭐ 本索引 (v3.0)
├── README.md                              # 文档中心入口
├── DEVELOPMENT_PLAN_AND_PROGRESS.md       # 开发计划及进度
├── TESTING_PRINCIPLES.md                  # 测试原则
├── DATA_STRUCTURES_AND_CONNECTIONS.md     # 数据结构和连接
├── CONTAINER_ARCHITECTURE_AND_APIS.md     # 容器架构和 API
├── API_COLLECTION_AND_REFERENCE.md        # API 集合参考
│
├── api/                                   # REST API 详细文档
│   ├── customer_management_api.md
│   ├── alert_management_api.md
│   ├── data_ingestion_api.md
│   ├── threat_assessment_api.md
│   └── ...
│
├── design/                                # 架构设计与领域规范
│   ├── domain_rules_reference.md          # ⭐ v3.0 领域规则参考 (6 条核心规则)
│   ├── rbac_multi_tenant_architecture.md  # ⭐ v3.0 RBAC 多租户架构
│   ├── ml_pipeline_training_guide.md      # ⭐ v3.0 ML 流水线训练指南
│   ├── tire_llm_integration.md            # ⭐ v3.0 TIRE/LLM 集成文档
│   ├── flink_stream_processing_architecture.md
│   ├── honeypot_based_threat_scoring.md
│   ├── new_system_architecture_spec.md
│   ├── v2_event_schemas.md
│   ├── mqtt_ingestion_architecture.md
│   ├── net_weighting_strategy.md
│   └── ...
│
├── deployment/                            # 部署与运维
│   ├── k3s_deployment_guide.md            # ⭐ v3.0 K3s 单节点部署 + 16 FAQ
│   └── ...
│
├── guides/                                # 使用指南
│   ├── USAGE_GUIDE.md
│   ├── TIME_WINDOW_CONFIGURATION.md
│   └── CONTAINER_REBUILD_WORKFLOW.md
│
├── build/                                 # 构建文档
│   ├── DOCKER_COMPOSE_OPTIMIZATION_GUIDE.md
│   └── DOCKER_BEST_PRACTICES_GUIDE.md
│
├── testing/                               # 测试文档
│   ├── COMPREHENSIVE_DEVELOPMENT_TEST_PLAN.md
│   └── BACKEND_API_TESTING_DAY2_SUMMARY.md
│
├── fixes/                                 # 修复报告
│   ├── THREAT_ASSESSMENT_FIX_REPORT_2025-10-22.md
│   └── DATABASE_FIX_REPORT.md
│
├── audit/                                 # 代码审计
│   └── CODE_AUDIT_FINAL_REPORT_2025-10-22.md
│
├── progress/                              # 进度报告
│   ├── IMPLEMENTATION_ROADMAP.md
│   ├── COMPLETION_REPORT.md
│   └── NEXT_DEVELOPMENT_PLAN.md
│
├── analysis/                              # 分析报告
│   └── COMPREHENSIVE_OPTIMIZATION_PLAN.md
│
├── architecture/                          # 架构文档
│   └── service_responsibility_separation.md
│
└── history/                               # 历史文档
    └── ...
```

---

## 🎯 文档分类详解

### 🏗️ 设计文档 (design/)

#### ⭐ v3.0 新增
| 文档 | 说明 |
|------|------|
| [domain_rules_reference.md](design/domain_rules_reference.md) | 6 条核心业务领域规则：设备归属、多设备支持、心跳日志、设备转移、TIRE/LLM 配置、租户分销商模型 |
| [rbac_multi_tenant_architecture.md](design/rbac_multi_tenant_architecture.md) | SUPER_ADMIN → TENANT_ADMIN → CUSTOMER_USER 三级角色体系, JWT 认证, 权限范围, 前端角色 UI |
| [ml_pipeline_training_guide.md](design/ml_pipeline_training_guide.md) | 3 层自编码器 (30s/5min/15min) + BiGRU, alpha=0.6 混合, mlWeight 0.5-3.0, ONNX 推理, 训练命令, Kafka 主题 |
| [tire_llm_integration.md](design/tire_llm_integration.md) | 11 款插件 (AbuseIPDB, VirusTotal, OTX...), system_config 表, 插件管理 API, LLM 验证, 前端 Settings UI |

#### 核心架构
| 文档 | 说明 |
|------|------|
| [flink_stream_processing_architecture.md](design/flink_stream_processing_architecture.md) | Flink 流处理架构详解 |
| [new_system_architecture_spec.md](design/new_system_architecture_spec.md) | 云原生系统架构规范 |
| [honeypot_based_threat_scoring.md](design/honeypot_based_threat_scoring.md) | 蜜罐威胁评分方案 |
| [data_structures.md](design/data_structures.md) | 数据结构定义 (Kafka, PostgreSQL) |
| [v2_event_schemas.md](design/v2_event_schemas.md) | V2 哨兵 7 种 JSON 事件格式 |
| [mqtt_ingestion_architecture.md](design/mqtt_ingestion_architecture.md) | EMQX MQTT 摄取架构 |
| [net_weighting_strategy.md](design/net_weighting_strategy.md) | 网段权重策略 |

### 🚀 部署文档 (deployment/)

| 文档 | 说明 |
|------|------|
| [k3s_deployment_guide.md](deployment/k3s_deployment_guide.md) | ⭐ K3s 单节点完整部署指南: 前置条件, 18+ pods 部署, 镜像构建, 数据库初始化, 16 条 FAQ |

### 🔌 API 文档 (api/)

| 文档 | 说明 |
|------|------|
| [customer_management_api.md](api/customer_management_api.md) | 客户 CRUD、设备绑定、通知配置 (26 端点) |
| [data_ingestion_api.md](api/data_ingestion_api.md) | 日志摄取、批量处理、统计监控 |
| [threat_assessment_overview.md](api/threat_assessment_overview.md) | 威胁评估架构、评分算法 |
| [alert_management_overview.md](api/alert_management_overview.md) | 告警管理、通知系统 |

### 📚 使用指南 (guides/)

| 文档 | 说明 |
|------|------|
| [USAGE_GUIDE.md](guides/USAGE_GUIDE.md) | 系统使用指南 |
| [TIME_WINDOW_CONFIGURATION.md](guides/TIME_WINDOW_CONFIGURATION.md) | 时间窗口配置 |
| [CONTAINER_REBUILD_WORKFLOW.md](guides/CONTAINER_REBUILD_WORKFLOW.md) | 容器重建流程 |

---

## 🔍 按主题查找

| 主题 | 文档位置 |
|------|----------|
| 项目概述 | [README.md](../README.md) |
| 系统架构 | [design/new_system_architecture_spec.md](design/new_system_architecture_spec.md) |
| 领域规则 | [design/domain_rules_reference.md](design/domain_rules_reference.md) |
| RBAC / 多租户 | [design/rbac_multi_tenant_architecture.md](design/rbac_multi_tenant_architecture.md) |
| ML 检测 | [design/ml_pipeline_training_guide.md](design/ml_pipeline_training_guide.md) |
| TIRE / 威胁情报 | [design/tire_llm_integration.md](design/tire_llm_integration.md) |
| K3s 部署 | [deployment/k3s_deployment_guide.md](deployment/k3s_deployment_guide.md) |
| Flink 流处理 | [design/flink_stream_processing_architecture.md](design/flink_stream_processing_architecture.md) |
| 蜜罐评分 | [design/honeypot_based_threat_scoring.md](design/honeypot_based_threat_scoring.md) |
| V2 MQTT | [design/mqtt_ingestion_architecture.md](design/mqtt_ingestion_architecture.md) |
| API 接口 | [api/](api/) |
| Docker 构建 | [build/](build/) |
| 测试 | [testing/](testing/) |
| 问题修复 | [fixes/](fixes/) |

---

## 🎯 按角色推荐路径

### 新开发者
1. [README.md](../README.md) → 项目概况
2. [本索引](DOCUMENTATION_INDEX.md) → 导航
3. [design/domain_rules_reference.md](design/domain_rules_reference.md) → 领域规则
4. [design/rbac_multi_tenant_architecture.md](design/rbac_multi_tenant_architecture.md) → 权限体系

### 运维人员
1. [README.md](../README.md) → 部署概览
2. [deployment/k3s_deployment_guide.md](deployment/k3s_deployment_guide.md) → K3s 部署 + FAQ
3. [guides/CONTAINER_REBUILD_WORKFLOW.md](guides/CONTAINER_REBUILD_WORKFLOW.md) → 容器重建

### ML 工程师
1. [design/ml_pipeline_training_guide.md](design/ml_pipeline_training_guide.md) → 训练指南
2. [design/flink_stream_processing_architecture.md](design/flink_stream_processing_architecture.md) → 上游数据流

### 威胁情报分析师
1. [design/tire_llm_integration.md](design/tire_llm_integration.md) → TIRE/LLM 集成
2. [design/honeypot_based_threat_scoring.md](design/honeypot_based_threat_scoring.md) → 蜜罐评分

### API 开发者
1. [API_COLLECTION_AND_REFERENCE.md](API_COLLECTION_AND_REFERENCE.md) → API 快速参考
2. [api/](api/) → 具体接口文档

---

## 📊 文档统计

| 分类 | 数量 |
|------|------|
| 核心文档 (docs/ 根目录) | 6 |
| API 文档 (api/) | 15 |
| 设计文档 (design/) | 14 |
| 部署文档 (deployment/) | 1 |
| 使用指南 (guides/) | 3 |
| 构建文档 (build/) | 6 |
| 测试文档 (testing/) | 5 |
| 修复报告 (fixes/) | 8 |
| 审计报告 (audit/) | 7 |
| 进度报告 (progress/) | 15 |
| 分析报告 (analysis/) | 9 |
| 架构文档 (architecture/) | 1 |
| 历史文档 (history/) | 5 |
| **总计** | **130+** |

---

## 📞 文档维护

- **核心文档**: 实时更新，反映最新状态
- **技术文档**: 代码变更时同步更新
- **历史文档**: 重大事件后归档保留
- 新功能开发时必须更新相关文档
- 文档格式统一使用 Markdown
- 保持目录结构清晰和一致

---

*最后更新: 2026-03-30 · 系统版本: v3.0*
