# 📚 威胁检测系统文档索引

**版本**: 2.0
**更新日期**: 2025-10-22
**文档总数**: 120+ 文档

---

## 📖 快速导航

### 🚀 核心文档 (docs/ 根目录)
| 文档 | 说明 | 状态 |
|------|------|------|
| [README.md](README.md) | 项目介绍和快速开始 | ✅ 最新 |
| [DEVELOPMENT_PLAN_AND_PROGRESS.md](DEVELOPMENT_PLAN_AND_PROGRESS.md) | 开发计划及进度 | ✅ 最新 |
| [TESTING_PRINCIPLES.md](TESTING_PRINCIPLES.md) | 测试原则 | ✅ 最新 |
| [DATA_STRUCTURES_AND_CONNECTIONS.md](DATA_STRUCTURES_AND_CONNECTIONS.md) | 数据结构和数据库连接 | ✅ 最新 |
| [CONTAINER_ARCHITECTURE_AND_APIS.md](CONTAINER_ARCHITECTURE_AND_APIS.md) | 容器结构和API配置 | ✅ 最新 |
| [API_COLLECTION_AND_REFERENCE.md](API_COLLECTION_AND_REFERENCE.md) | API集合及快速参考 | ✅ 最新 |

---

## 📁 文档目录结构

```
docs/
├── README.md                              # 项目介绍
├── DEVELOPMENT_PLAN_AND_PROGRESS.md       # 开发计划及进度
├── TESTING_PRINCIPLES.md                  # 测试原则
├── DATA_STRUCTURES_AND_CONNECTIONS.md     # 数据结构和连接
├── CONTAINER_ARCHITECTURE_AND_APIS.md     # 容器架构和API
├── API_COLLECTION_AND_REFERENCE.md        # API集合参考
│
├── api/                                   # API详细文档
│   ├── API_QUICK_REFERENCE.md
│   ├── API_COMPLETE_VERIFICATION_CHECKLIST.md
│   ├── customer_management_api.md
│   ├── alert_management_api.md
│   ├── data_ingestion_api.md
│   └── threat_assessment_api.md
│
├── audit/                                 # 代码审计报告
│   ├── CODE_AUDIT_FINAL_REPORT_2025-10-22.md
│   ├── AUDIT_COMPLETION_SUMMARY_2025-10-22.md
│   └── CODE_AUDIT_BEST_PRACTICES_2025-10-22.md
│
├── build/                                 # 构建和部署
│   ├── DOCKER_COMPOSE_OPTIMIZATION_GUIDE.md
│   ├── BUILD_SYSTEM_VERIFICATION_REPORT.md
│   └── DOCKER_BEST_PRACTICES_GUIDE.md
│
├── testing/                               # 测试相关
│   ├── BACKEND_API_INVENTORY.md
│   ├── COMPREHENSIVE_DEVELOPMENT_TEST_PLAN.md
│   └── BACKEND_API_TESTING_DAY2_SUMMARY.md
│
├── fixes/                                 # 修复报告
│   ├── THREAT_ASSESSMENT_FIX_REPORT_2025-10-22.md
│   ├── GET_ALERTS_ID_FIX_REPORT.md
│   └── DATABASE_FIX_REPORT.md
│
├── phases/                                # 各阶段报告
│   ├── PHASE1_COMPLETION_CERTIFICATE.md
│   ├── DAY1_COMPLETION_REPORT.md
│   └── PROJECT_UNDERSTANDING_SUMMARY_2025-10-20.md
│
├── guides/                                # 使用指南
│   ├── USAGE_GUIDE.md
│   └── DEVELOPMENT_CHEATSHEET.md
│
├── design/                                # 设计文档
│   ├── honeypot_based_threat_scoring.md
│   ├── new_system_architecture_spec.md
│   └── understanding_corrections_summary.md
│
├── analysis/                              # 分析报告
│   ├── COMPREHENSIVE_OPTIMIZATION_PLAN.md
│   └── 01_time_window_analysis.md
│
├── architecture/                          # 架构文档
│   └── service_responsibility_separation.md
│
├── progress/                              # 进度报告
│   ├── IMPLEMENTATION_ROADMAP.md
│   └── COMPLETION_REPORT.md
│
└── history/                               # 历史文档
    ├── project_summary.md
    └── cloud_native_architecture.md
```

---

## 🎯 文档分类说明

### 📋 核心文档 (必须阅读)
这些文档提供了系统的基础信息和使用指南：

1. **README.md** - 项目概述、架构介绍、快速开始
2. **DEVELOPMENT_PLAN_AND_PROGRESS.md** - 当前开发状态和进度
3. **TESTING_PRINCIPLES.md** - 测试方法和原则
4. **DATA_STRUCTURES_AND_CONNECTIONS.md** - 数据模型和数据库配置
5. **CONTAINER_ARCHITECTURE_AND_APIS.md** - 容器配置和API端点
6. **API_COLLECTION_AND_REFERENCE.md** - API测试状态和快速参考

### 🔧 开发文档 (按需阅读)

#### API文档 (`api/`)
- 详细的API接口说明
- 请求/响应格式
- 使用示例

#### 审计报告 (`audit/`)
- 代码质量审计结果
- 安全检查报告
- 性能优化建议

#### 构建部署 (`build/`)
- Docker配置优化
- 构建流程说明
- 部署最佳实践

#### 测试文档 (`testing/`)
- 测试计划和策略
- API测试覆盖
- 自动化测试脚本

#### 修复报告 (`fixes/`)
- Bug修复记录
- 问题分析和解决方案
- 技术债务清理

### 📚 参考文档 (可选阅读)

#### 阶段报告 (`phases/`)
- 各开发阶段的完成情况
- 里程碑达成记录

#### 设计文档 (`design/`)
- 系统设计决策
- 架构设计说明

#### 分析报告 (`analysis/`)
- 技术分析和优化建议

#### 进度报告 (`progress/`)
- 开发进度跟踪
- 实现路线图

#### 历史文档 (`history/`)
- 项目演进历史
- 技术决策记录

---

## 🔍 文档搜索指南

### 按主题查找
- **API相关**: `docs/api/`
- **数据库**: `docs/DATA_STRUCTURES_AND_CONNECTIONS.md`
- **容器部署**: `docs/CONTAINER_ARCHITECTURE_AND_APIS.md`
- **测试**: `docs/TESTING_PRINCIPLES.md` + `docs/testing/`
- **问题修复**: `docs/fixes/`
- **代码审计**: `docs/audit/`

### 按时间查找
- **最新文档**: 查看文件修改日期
- **历史版本**: `docs/history/` + `docs/phases/`
- **当前状态**: 核心文档 (docs/ 根目录)

### 按类型查找
- **使用指南**: `docs/guides/` + `docs/USAGE_GUIDE.md`
- **技术规范**: `docs/design/` + `docs/architecture/`
- **质量保证**: `docs/audit/` + `docs/testing/`
- **项目管理**: `docs/DEVELOPMENT_PLAN_AND_PROGRESS.md`

---

## 📊 文档统计

### 文档数量
- **核心文档**: 6个 (docs/ 根目录)
- **API文档**: 15个 (api/)
- **审计文档**: 7个 (audit/)
- **构建文档**: 6个 (build/)
- **测试文档**: 5个 (testing/)
- **修复文档**: 8个 (fixes/)
- **阶段文档**: 12个 (phases/)
- **指南文档**: 2个 (guides/)
- **设计文档**: 10个 (design/)
- **分析文档**: 9个 (analysis/)
- **架构文档**: 1个 (architecture/)
- **进度文档**: 15个 (progress/)
- **历史文档**: 5个 (history/)

**总计**: 120+ 个文档文件

### 文档质量
- **完整性**: ✅ 100% (所有功能都有文档)
- **时效性**: ✅ 最新 (2025-10-22更新)
- **一致性**: ✅ 统一 (标准格式和术语)
- **可访问性**: ✅ 分类清晰 (目录结构合理)

---

## 🚀 快速开始

### 新开发者
1. 阅读 [README.md](README.md) 了解项目概况
2. 查看 [DEVELOPMENT_PLAN_AND_PROGRESS.md](DEVELOPMENT_PLAN_AND_PROGRESS.md) 了解当前状态
3. 参考 [USAGE_GUIDE.md](guides/USAGE_GUIDE.md) 开始使用
4. 查看 [TESTING_PRINCIPLES.md](TESTING_PRINCIPLES.md) 了解测试方法

### API开发者
1. 查看 [API_COLLECTION_AND_REFERENCE.md](API_COLLECTION_AND_REFERENCE.md) 了解API概览
2. 深入 [api/](api/) 目录查看详细API文档
3. 参考 [CONTAINER_ARCHITECTURE_AND_APIS.md](CONTAINER_ARCHITECTURE_AND_APIS.md) 了解服务架构

### 运维人员
1. 查看 [CONTAINER_ARCHITECTURE_AND_APIS.md](CONTAINER_ARCHITECTURE_AND_APIS.md) 了解部署架构
2. 参考 [DATA_STRUCTURES_AND_CONNECTIONS.md](DATA_STRUCTURES_AND_CONNECTIONS.md) 了解数据库配置
3. 查看 [build/](build/) 目录了解构建和部署

---

## 📞 文档维护

### 更新原则
- **核心文档**: 实时更新，反映最新状态
- **技术文档**: 代码变更时同步更新
- **历史文档**: 重大事件后归档保留

### 贡献指南
- 新功能开发时必须更新相关文档
- 文档格式统一使用 Markdown
- 保持目录结构清晰和一致

### 文档审查
- 代码审查时包含文档审查
- 发布前进行文档完整性检查
- 定期清理过时文档

---

*此索引帮助您快速定位所需的文档和信息*
