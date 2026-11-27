# 3.1.8 APT/dpkg 锁等待修复

日期：2026-09-15

## 问题

Ubuntu 的 `unattended-upgrade` 占用 `/var/lib/dpkg/lock-frontend` 时，安装器安装缺失依赖会立即失败。该失败发生在文件同步和容器启动之前，不会形成半安装状态。

## 修改

- 安装器为 `apt-get update/install` 设置短时 `DPkg::Lock::Timeout` 并在总计 600 秒预算内重试，既覆盖 dpkg frontend lock，也覆盖 APT lists/archive lock，等待系统包管理任务自然完成。
- 超时或其他 APT 故障时列出仍活动的包管理进程，并给出安全的审计、等待和重试命令。
- 明确禁止删除 APT/dpkg lock 文件或在包数据库写入期间强杀进程。
- 可通过安装器进程环境变量 `APT_LOCK_TIMEOUT_SECONDS` 调整等待秒数；非整数立即拒绝。
- 新增发布契约回归检查，保证安装器包含锁等待且不会删除系统 lock 文件。
- 构建依赖同时检查 Docker Buildx：按 APT 源自动选择 `docker-buildx` 或 `docker-buildx-plugin`，并启用 Compose Bake，避免 Compose 2.40 的经典构建器弃用警告。

## 兼容性

- 不改变 Compose、数据库、端口、数据接入协议或业务服务。
- 已安装环境升级时继续保留 `.env` 和 Docker named volumes。
