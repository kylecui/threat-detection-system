# 项目资料主索引

> 当前源码/交付版本：`3.1.39`。Ubuntu 构建与本地回传已完成；未执行生产部署。详情见本版测试记录。
>
> 最后更新：2026-10-30 — 浅色双栏登录及 018/019 修复复查通过，Ubuntu 完整 236 项浏览器回归与发布验证完成，3.1.39 已回传。

## 核心资料

- `PROJECT.md`：项目定位、边界、技术栈和运行方式。
- `ARCHITECTURE.md`：服务组成、数据流、依赖与网络入口。
- `MODULES.md`：源码模块和部署文件职责。
- `REQUIREMENTS.md`：用户需求、状态与验收标准。
- `ISSUES.md`：已知限制、安全事项和后续工作。
- `DEPLOYMENT.md`：正式安装、升级、诊断、卸载和清除。
- `RELEASE_PROCESS.md`：发布包内容、排除项和验证流程。
- `DEVELOPMENT_LOG.md`：按时间追加的开发与验证记录。

## 本次专项记录

- 3.1.39 登录参考样式及发布范围：`records/changes/2026-10-30-login-reference-release-3.1.39.md`
- 3.1.39 本地复查、Ubuntu 构建与交付：`records/tests/2026-10-30-release-3.1.39.md`
- 018 对比度独立复查：`Interaction/018_登录主按钮悬停文字对比度不足/03_复查验收报告.md`
- 019 显隐键重复独立复查：`Interaction/019_密码显隐按钮长按空格重复切换/03_复查验收报告.md`
- 3.1.37 前端工作台分支合并：`records/changes/2026-10-26-frontend-workspace-merge-3.1.37.md`
- 3.1.37 远程构建与发布验证：`records/tests/2026-10-26-release-3.1.37.md`
- 3.1.36 告警详情与威胁列表界面修复：`records/changes/2026-10-24-alert-detail-threat-table-ui-3.1.36.md`
- 3.1.36 远程构建与发布验证：`records/tests/2026-10-24-release-3.1.36.md`
- 3.1.35 告警工作流、情报与 AI 配置修复：`records/changes/2026-10-23-alert-workflow-intel-ai-config-3.1.35.md`
- 3.1.35 远程构建与发布验证：`records/tests/2026-10-23-release-3.1.35.md`
- 3.1.34 会话、告警、情报与 Web 修复：`records/changes/2026-10-23-session-alert-intel-ui-3.1.34.md`
- 3.1.34 远程构建与发布验证：`records/tests/2026-10-23-release-3.1.34.md`
- 3.1.33 情报、AI 与通知运行时修复：`records/changes/2026-10-22-runtime-config-notification-intel-3.1.33.md`
- 3.1.33 远程构建与发布验证：`records/tests/2026-10-22-release-3.1.33.md`
- 3.1.32 浮层滚动稳定性与 Flink 单次安装回退：`records/changes/2026-10-21-modal-scroll-and-flink-fallback.md`
- 3.1.32 本轮远程构建与发布验证：`records/tests/2026-10-21-release-3.1.32.md`
- 3.1.32 单安装脚本变更：`records/changes/2026-09-29-single-installer-3.1.32.md`
- 3.1.32 验证：`records/tests/2026-09-29-release-3.1.32.md`
- 3.1.31 租户总览 SQL 修复：`records/changes/2026-09-29-overview-native-query-cast-3.1.31.md`
- 3.1.31 验证：`records/tests/2026-09-29-release-3.1.31.md`
- 3.1.30 总览、分析窗口与配置运行时整改：`records/changes/2026-09-28-overview-analysis-and-config-runtime-3.1.30.md`
- 3.1.30 验证：`records/tests/2026-09-28-release-3.1.30.md`
- 3.1.29 安装验收 SIGPIPE 修复：`records/changes/2026-09-25-installer-sigpipe-3.1.29.md`
- 3.1.29 验证：`records/tests/2026-09-25-release-3.1.29.md`
- 3.1.28 端口分布与网络标识显示整改：`records/changes/2026-09-25-port-distribution-and-identifiers-3.1.28.md`
- 3.1.28 验证：`records/tests/2026-09-25-release-3.1.28.md`
- 3.1.27 威胁列表攻击者 IP 响应修复：`records/changes/2026-09-25-threat-list-attacker-ip-3.1.27.md`
- 3.1.27 验证：`records/tests/2026-09-25-release-3.1.27.md`

- 需求：`records/requirements/REQ-001-standard-release-and-install.md`
- 修改：`records/changes/2026-09-15-standard-release-3.1.7.md`
- 测试：`records/tests/2026-09-15-release-3.1.7.md`
- 安装器锁等待修复：`records/changes/2026-09-15-apt-lock-wait-3.1.8.md`
- 3.1.8 验证：`records/tests/2026-09-15-release-3.1.8.md`
- 镜像预拉取整改：`records/changes/2026-09-15-sequential-image-prefetch-3.1.9.md`
- 3.1.9 验证：`records/tests/2026-09-15-release-3.1.9.md`
- Docker 国内镜像策略：`records/changes/2026-09-15-docker-mirror-policy-3.1.10.md`
- 3.1.10 验证：`records/tests/2026-09-15-release-3.1.10.md`
- 跨平台归档权限修复：`records/changes/2026-09-15-release-executable-mode-3.1.11.md`
- 3.1.11 验证：`records/tests/2026-09-15-release-3.1.11.md`
- 镜像提示触发条件修正：`records/changes/2026-09-15-docker-mirror-prompt-3.1.12.md`
- 3.1.12 验证：`records/tests/2026-09-15-release-3.1.12.md`
- Compose 端口冲突整改：`records/changes/2026-09-15-compose-port-preflight-3.1.13.md`
- 3.1.13 验证：`records/tests/2026-09-15-release-3.1.13.md`
- PostgreSQL 初始化链整改：`records/changes/2026-09-15-postgres-init-chain-3.1.14.md`
- 3.1.14 验证：`records/tests/2026-09-15-release-3.1.14.md`
- EMQX 单机健康整改：`records/changes/2026-09-16-emqx-node-health-3.1.15.md`
- 3.1.15 验证：`records/tests/2026-09-16-release-3.1.15.md`
- 3.1.16 性能与空数据整改：`records/changes/2026-09-16-performance-readiness-3.1.16.md`
- 3.1.16 验证：`records/tests/2026-09-16-release-3.1.16.md`
- 3.1.17 控制面整改：`records/changes/2026-09-16-control-plane-resilience-3.1.17.md`
- 3.1.17 验证：`records/tests/2026-09-16-release-3.1.17.md`
- 3.1.18 运行时一致性与表单弹窗整改：`records/changes/2026-09-22-runtime-consistency-and-form-layout-3.1.18.md`
- 3.1.18 验证：`records/tests/2026-09-22-release-3.1.18.md`
- 3.1.19 客户生命周期与租户隔离整改：`records/changes/2026-09-23-customer-lifecycle-and-tenant-isolation-3.1.19.md`
- 3.1.19 验证：`records/tests/2026-09-23-release-3.1.19.md`
- 3.1.20 网关认证响应生命周期整改：`records/changes/2026-09-23-gateway-auth-response-lifecycle-3.1.20.md`
- 3.1.20 验证：`records/tests/2026-09-23-release-3.1.20.md`
- 3.1.21 客户创建与可选账号原子化整改：`records/changes/2026-09-23-customer-atomic-account-provisioning-3.1.21.md`
- 3.1.21 验证：`records/tests/2026-09-23-release-3.1.21.md`
- 3.1.22 多租户作用域与权限整改：`records/changes/2026-09-23-multitenant-scope-rbac-3.1.22.md`
- 3.1.22 验证：`records/tests/2026-09-23-release-3.1.22.md`
- 3.1.23 数据可观测性与控制面性能整改：`records/changes/2026-09-24-device-flow-and-control-plane-3.1.23.md`
- 3.1.23 验证：`records/tests/2026-09-24-release-3.1.23.md`
- 3.1.24 心跳与数据活跃状态整改：`records/changes/2026-09-24-heartbeat-and-data-activity-3.1.24.md`
- 3.1.24 验证：`records/tests/2026-09-24-release-3.1.24.md`
- 3.1.25 设备绑定一致性与写操作防重：`records/changes/2026-09-24-device-binding-integrity-3.1.25.md`
- 3.1.25 验证：`records/tests/2026-09-24-release-3.1.25.md`
- 3.1.26 数据库迁移账本整改：`records/changes/2026-09-25-database-migration-ledger-3.1.26.md`
- 3.1.26 验证：`records/tests/2026-09-25-release-3.1.26.md`

## 目录约定

- `AI_PROJECT/` 只保存供人和 AI 协作的架构、需求、变更、分析和测试记录。
- `outputs/v<version>/` 只保存可交付归档、外部校验、包内容清单和发布说明。
- `AI_PROJECT/`、开发测试、历史资料和 `outputs/` 不进入生产 tar.gz。
