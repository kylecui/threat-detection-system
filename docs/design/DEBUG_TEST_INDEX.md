# 📚 调试与测试资源索引

**更新日期**: 2025-10-15  
**目的**: 快速找到调试、测试和故障排查资源

---

## 🎯 快速开始

### 你应该先看什么?

1. **刚开始调试?** → [快速参考卡片](QUICK_REFERENCE.md)
2. **遇到问题?** → [调试与测试指南](debugging_and_testing_guide.md) 
3. **想了解实施过程?** → [数据持久化实施总结](data_persistence_implementation_summary.md)

---

## 📖 文档清单

### 核心调试文档

| 文档 | 用途 | 何时使用 |
|------|------|---------|
| [快速参考卡片](QUICK_REFERENCE.md) | 最常用命令速查 | 📌 **日常使用** |
| [调试与测试指南](debugging_and_testing_guide.md) | 完整问题排查流程 | 🔍 **遇到问题时** |
| [数据持久化实施总结](data_persistence_implementation_summary.md) | 实施过程记录 | 📚 **了解背景** |

### 架构与设计文档

| 文档 | 用途 |
|------|------|
| [蜜罐机制理解](understanding_corrections_summary.md) | 系统核心概念 |
| [基于蜜罐的威胁评分](honeypot_based_threat_scoring.md) | 评分算法详解 |
| [云原生系统架构](new_system_architecture_spec.md) | 系统设计规范 |
| [原始系统分析](original_system_analysis.md) | C#系统分析 |
| [聚合系统性能指标](understanding_aggregation_metrics.md) | ⭐ 理解正确的性能指标 |

### MVP实施文档

| 文档 | 用途 |
|------|------|
| [MVP实施计划](MVP_IMPLEMENTATION_PLAN.md) | 分阶段实施指南 |
| [MVP执行摘要](MVP_EXECUTIVE_SUMMARY.md) | 高层次总结 |
| [MVP快速决策指南](MVP_QUICK_DECISION_GUIDE.md) | 技术选型指导 |

---

## 🛠️ 工具脚本

### 自动化脚本位置

```
scripts/tools/
├── full_restart.sh           # 完整重启流程
├── check_persistence.sh      # 数据持久化检查
└── tail_logs.sh             # 多服务日志查看
```

### 使用方法

```bash
# 代码修改后完整重启
./scripts/tools/full_restart.sh

# 检查数据是否正确保存
./scripts/tools/check_persistence.sh

# 实时查看所有服务日志
./scripts/tools/tail_logs.sh

# 查看特定服务日志
./scripts/tools/tail_logs.sh data-ingestion-service stream-processing
```

---

## 🧪 测试脚本

### E2E测试

```bash
# 完整端到端测试
python3 scripts/test/e2e_mvp_test.py

# 测试内容:
# ✓ 日志解析与摄取
# ✓ Kafka消息传递
# ✓ 3层时间窗口处理
# ✓ 端口权重计算
# ✓ 威胁评分生成
# ✓ 数据库持久化
```

### 测试数据位置

```
tmp/real_test_logs/
├── 2025-05-15.02.02.log  (1000+ 条事件)
├── 2025-05-15.02.03.log  (1000+ 条事件)
└── ... (26个文件)
```

---

## 🚨 常见问题速查

### 问题 → 文档映射

| 问题 | 查看文档 | 章节 |
|------|---------|------|
| 代码修改后容器行为未改变 | [调试指南](debugging_and_testing_guide.md) | Docker容器调试 |
| Kafka消费者未启动 | [调试指南](debugging_and_testing_guide.md) | 常见陷阱#1 |
| 数据未持久化 | [调试指南](debugging_and_testing_guide.md) | 常见陷阱#2 |
| JSONB类型错误 | [实施总结](data_persistence_implementation_summary.md) | 问题3 |
| E2E测试失败 | [调试指南](debugging_and_testing_guide.md) | E2E测试执行 |
| 配置文件不生效 | [实施总结](data_persistence_implementation_summary.md) | 问题2 |

---

## 📊 快速诊断流程

### 1. 数据持久化问题

```bash
# 步骤1: 检查服务状态
cd docker && docker compose ps

# 步骤2: 检查数据库
./scripts/tools/check_persistence.sh

# 步骤3: 查看日志
docker logs data-ingestion-service | grep -i "persisted\|error"

# 步骤4: 如果数据为0
# → 查看 [调试指南](debugging_and_testing_guide.md) > 常见陷阱#2
```

### 2. Kafka消费问题

```bash
# 步骤1: 检查分区分配
docker logs data-ingestion-service | grep "partitions assigned"

# 步骤2: 检查consumer group
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# 步骤3: 如果未分配分区
# → 查看 [实施总结](data_persistence_implementation_summary.md) > 问题2
```

### 3. 流处理问题

```bash
# 步骤1: 检查Flink日志
docker logs stream-processing --tail 100

# 步骤2: 验证告警生成
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic threat-alerts --from-beginning --max-messages 5

# 步骤3: 如果无告警
# → 查看 [调试指南](debugging_and_testing_guide.md) > 流处理问题排查
```

---

## 💡 最佳实践速记

### 开发流程

```
修改代码 → Maven编译 → Docker构建(--no-cache) → 
容器重启 → 查看日志 → 验证数据库 → E2E测试
```

### 调试原则

1. **日志优先**: 先看日志,再查代码
2. **分层验证**: Kafka → 应用 → 数据库
3. **自动化优先**: 使用脚本避免手动重复
4. **文档记录**: 遇到新问题立即记录

### 避免常见错误

- ❌ 修改代码后只重启容器 → ✅ 重新构建镜像
- ❌ 使用`docker compose build` → ✅ 使用`--no-cache`
- ❌ 忽略配置文件优先级 → ✅ 明确覆盖所有配置
- ❌ JSONB直接映射String → ✅ 使用`@JdbcTypeCode`

---

## 🔗 相关资源

### 项目根目录文档

- [README.md](../README.md) - 项目概述
- [USAGE_GUIDE.md](../USAGE_GUIDE.md) - 详细使用说明
- [.github/copilot-instructions.md](../.github/copilot-instructions.md) - 开发规范

### Docker配置

- [docker/docker-compose.yml](../docker/docker-compose.yml) - 服务编排
- [docker/*.sql](../docker/) - 数据库初始化脚本

### 应用配置

```
services/data-ingestion/
└── src/main/resources/
    ├── application.properties
    └── application-docker.yml  ← Docker环境配置
```

---

## 📝 更新记录

| 日期 | 版本 | 更新内容 |
|------|------|---------|
| 2025-10-15 | 1.0 | 初始版本,整理所有调试和测试资源 |

---

**下次遇到问题时,先来这里找答案! 📚**
