## 🚀 v3.1.1 — 全功能运维发布 (Full Operations Release)

### ⚡ 新增功能

**部署工具链**
- `scripts/k3s-quick-update.sh` — 增量部署脚本，支持：
  - `--changed` 自动检测 git diff 变更的服务
  - `--all` 重建所有服务
  - 指定服务名快速更新（build → import → rollout restart）
- `k3s-build-images.sh` 现覆盖全部 11 个服务（新增 frontend + tire）
- `k3s-deploy.sh` Phase 6 补齐 tire-secret、tire、frontend

**TIRE 服务源码入库**
- `services/tire/` — 完整 Python 源码 + Dockerfile，11 款威胁情报插件
- 支持从源码重建 TIRE Docker 镜像

### 🔧 Bug 修复（v3.1.0 内容）

- **端口攻击分布** — 端到端修复：Flink → Kafka → threat-assessment → DB → API → 前端
- **威胁趋势（24小时）** — 前后端 hours 参数联动，Dashboard/Analytics 图表正常显示
- **Flink 字段映射** — `@JsonAlias` 修复 `customerId` / `devSerial` 反序列化
- **portList 传递** — stream-processing `AggregatedAttackData` 新增 portList 字段

### 📋 运维手册

- `docs/OPERATIONS_MANUAL_v3.1.0.md` — 938 行中文运维手册，覆盖 18 个章节

### 🏗️ 系统状态

| 组件 | 状态 |
|------|------|
| 全部 18 Pods | ✅ Running 1/1 |
| Dashboard 图表 | ✅ 数据正常 |
| Analytics 图表 | ✅ 数据正常 |
| ML 检测 | ✅ 6 ONNX 模型加载 |
| TIRE | ✅ 11 插件可用 |
| 告警通知 | ✅ 邮件健康检查启用 |

### 📦 服务清单（全部可构建）

**Java 服务** (8)：data-ingestion, stream-processing, threat-assessment, alert-management, customer-management, threat-intelligence, api-gateway, config-server

**Python 服务** (2)：ml-detection, tire

**前端** (1)：frontend (React SPA)

### 🔑 快速部署

```bash
# 全量部署（从零开始）
sudo bash scripts/k3s-deploy.sh

# 全量重建镜像
sudo bash scripts/k3s-build-images.sh

# 增量更新（仅变更的服务）
sudo bash scripts/k3s-quick-update.sh --changed

# 指定服务更新
sudo bash scripts/k3s-quick-update.sh threat-assessment frontend
```

### 📝 完整变更日志

从 v3.0 到 v3.1.1 的主要提交：
- `d80c824` feat: add TIRE source, quick-update script, and complete build/deploy coverage
- `6ac7da9` docs: add v3.1.0 Chinese operations manual for testers
- `2c7ddc4` fix: populate port distribution and threat trend charts end-to-end
