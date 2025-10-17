# 📚 项目文档索引

**最后更新**: 2025-10-17

---

## 🚀 核心规范文档 (必读)

### 1. [DEVELOPMENT_CHEATSHEET.md](./DEVELOPMENT_CHEATSHEET.md) ⭐ 最重要
**用途**: 开发速查手册，每次开发前必须查阅  
**规模**: 9章节，约500行  
**内容**:
- 命名规范 (JSON/数据库/Java/API)
- 容器清单 (6个容器)
- 数据库清单 (14张表)
- API清单 (46个端点)
- 开发测试规范
- 故障排查清单
- 快速参考命令

**何时查阅**:
- ✅ 开始新功能开发前
- ✅ 编写DTO类时
- ✅ 设计API时
- ✅ 遇到问题时
- ✅ 运行测试前

---

### 2. [CODE_AUDIT_REPORT.md](./CODE_AUDIT_REPORT.md)
**用途**: 代码审查报告和修复计划  
**规模**: 7章节，约350行  
**内容**:
- 发现的问题 (1个严重, 3个警告)
- 修复计划 (分4个阶段)
- 验证清单
- 预防措施

**何时查阅**:
- ✅ 执行代码修复前
- ✅ Code Review时
- ✅ 了解已知问题时

---

### 3. [NORMALIZATION_REPORT.md](./NORMALIZATION_REPORT.md)
**用途**: 项目规范化完成报告  
**规模**: 约350行  
**内容**:
- 已完成的规范化工作总结
- 下一步行动计划
- 经验教训和改进措施

**何时查阅**:
- ✅ 了解项目规范化背景
- ✅ 查看下一步任务
- ✅ 回顾经验教训

---

## 📖 其他重要文档

### 项目说明
- [README.md](./README.md) - 项目概述和快速开始
- [USAGE_GUIDE.md](./USAGE_GUIDE.md) - 详细使用指南

### 架构和设计
- [docs/cloud_native_architecture.md](./docs/cloud_native_architecture.md) - 云原生架构
- [docs/data_structures.md](./docs/data_structures.md) - 数据结构定义
- [docs/log_format_analysis.md](./docs/log_format_analysis.md) - 日志格式分析

### 项目指令
- [.github/copilot-instructions.md](./.github/copilot-instructions.md) - GitHub Copilot项目指令

---

## 🔍 快速查找

### 按问题类型查找

| 问题类型 | 查看文档 | 章节 |
|---------|---------|------|
| **JSON字段命名错误** | DEVELOPMENT_CHEATSHEET.md | 第1章 |
| **数据库连接失败** | DEVELOPMENT_CHEATSHEET.md | 第6章 - 问题2 |
| **端口被占用** | DEVELOPMENT_CHEATSHEET.md | 第6章 - 问题1 |
| **API返回404** | DEVELOPMENT_CHEATSHEET.md | 第6章 - 问题3 |
| **PATCH不更新数据** | DEVELOPMENT_CHEATSHEET.md | 第6章 - 问题4 |
| **容器启动失败** | DEVELOPMENT_CHEATSHEET.md | 第2章 |
| **测试失败** | DEVELOPMENT_CHEATSHEET.md | 第5章 |

### 按开发阶段查找

| 开发阶段 | 查看文档 | 章节 |
|---------|---------|------|
| **开始新功能** | DEVELOPMENT_CHEATSHEET.md | 第9章 - 开发前检查 |
| **编写代码** | DEVELOPMENT_CHEATSHEET.md | 第1章 + 第9章 - 编码检查 |
| **编写测试** | DEVELOPMENT_CHEATSHEET.md | 第5章 |
| **运行测试** | DEVELOPMENT_CHEATSHEET.md | 第5章 + 第9章 - 测试前检查 |
| **提交代码** | DEVELOPMENT_CHEATSHEET.md | 第9章 - 提交前检查 |
| **Code Review** | CODE_AUDIT_REPORT.md | 全部 |

### 按信息类型查找

| 信息类型 | 查看文档 | 章节 |
|---------|---------|------|
| **容器信息** | DEVELOPMENT_CHEATSHEET.md | 第2章 |
| **数据库表结构** | DEVELOPMENT_CHEATSHEET.md | 第3章 |
| **API端点** | DEVELOPMENT_CHEATSHEET.md | 第4章 |
| **命名规范** | DEVELOPMENT_CHEATSHEET.md | 第1章 |
| **常用命令** | DEVELOPMENT_CHEATSHEET.md | 第7章 |
| **架构理解** | DEVELOPMENT_CHEATSHEET.md | 第8章 |

---

## 📌 强制要求

### ⚠️ 每次开发前必须做的事

1. **查阅** `DEVELOPMENT_CHEATSHEET.md`
2. **确认**容器都在运行: `docker ps`
3. **清理**测试数据
4. **参考**命名规范章节

### ⚠️ 每次提交前必须做的事

1. **完成**提交前检查清单 (DEVELOPMENT_CHEATSHEET.md 第9章)
2. **运行**完整集成测试
3. **检查**是否符合命名规范
4. **更新**相关文档

---

## 🎯 最常用的命令 (快捷参考)

```bash
# 1. 查看所有容器状态
docker ps --format "table {{.Names}}\t{{.Status}}"

# 2. 重启Customer-Management服务
docker restart customer-management-service

# 3. 查看服务日志
docker logs -f customer-management-service

# 4. 连接数据库
docker exec -it postgres psql -U threat_user -d threat_detection

# 5. 运行集成测试
cd /home/kylecui/threat-detection-system/scripts
bash integration_test_responsibility_separation.sh

# 6. 清理测试数据
docker exec postgres psql -U threat_user -d threat_detection -c \
  "DELETE FROM device_customer_mapping WHERE dev_serial LIKE '%test%';
   DELETE FROM customer_notification_configs WHERE customer_id LIKE '%test%';
   DELETE FROM customers WHERE customer_id LIKE '%test%';"
```

---

## 📞 获取帮助

### 遇到问题时的处理流程

1. **先查阅**: `DEVELOPMENT_CHEATSHEET.md` 第6章 (故障排查清单)
2. **再搜索**: 本文档的快速查找表
3. **后询问**: 团队成员或AI助手

### 文档维护

**问题**: 文档过时或不准确？  
**操作**: 立即更新相应文档并注明更新日期

**问题**: 遇到新的常见问题？  
**操作**: 添加到DEVELOPMENT_CHEATSHEET.md第6章

---

**创建时间**: 2025-10-17  
**维护者**: 开发团队全体成员  
**更新频率**: 持续更新
