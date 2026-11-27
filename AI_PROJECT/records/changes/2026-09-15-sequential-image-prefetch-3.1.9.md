# 3.1.9 镜像预拉取与顺序构建整改

日期：2026-09-15

## 现象与根因

全新 Ubuntu 24.04 目标机完成 APT、Docker、Compose 和 Buildx 安装后，在 Compose Bake 并行解析多个 Docker Hub 基础镜像时出现 `registry-1.docker.io:443 i/o timeout`。失败属于外部镜像仓库连通性/并发压力，不是 Java、Python、前端或 Compose 业务配置错误；3.1.8 已自动删除失败的新安装状态。

## 修改

- 在创建回滚快照、停止旧服务和同步安装目录之前，顺序预拉取 14 个基础及运行时镜像。
- 已存在于本机的完整镜像直接复用；缺失镜像默认拉取 4 次，每次超时 600 秒并递增退避。
- 11 个本地构建目标改为逐服务执行，单服务默认最多构建 3 次。
- 预拉取失败立即退出，此时不会修改 `/opt/threat-detection-system`、systemd 或业务容器。
- `--no-start` 不预拉取或构建镜像，继续只安装文件。
- 修正 Compose 本地镜像 fallback 版本为 `3.1.9`。

## 可调参数

- `DOCKER_PULL_RETRIES`：默认 4。
- `DOCKER_PULL_TIMEOUT_SECONDS`：默认 600。
- `DOCKER_BUILD_RETRIES`：默认 3。

这些参数通过执行安装器时的进程环境设置，不写入业务 `.env`。

## 不变范围

- 不修改业务代码、数据库结构、数据接入协议、端口、MQTT、HTTP 或告警处理逻辑。
