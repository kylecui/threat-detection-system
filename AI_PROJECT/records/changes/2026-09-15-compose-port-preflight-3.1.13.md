# 3.1.13 Compose 端口冲突整改

## 问题

3.1.12 首次安装启动 Compose 时，EMQX 已发布宿主 `8083/tcp`，随后 Threat Assessment 也尝试发布同一端口，Docker 返回 `Bind for 0.0.0.0:8083 failed: port is already allocated`。进度汇总同时显示 Kafka Error，但该汇总不能证明 Kafka 是本次安装退出的直接原因；明确的致命错误是宿主端口冲突。

## 修改

- Threat Assessment 继续使用宿主和容器 `8083/tcp`。
- EMQX WebSocket 继续使用容器 `8083`，宿主端口改为 `${EMQX_WS_HOST_PORT:-18084}`。
- `.env.example` 和自动环境准备器增加、校验 `EMQX_WS_HOST_PORT`。
- 安装器在任何镜像拉取、旧服务停止、备份或文件同步前解析最终 Compose JSON，拒绝重复发布端口。
- 安装器同时检查非本项目 Docker 容器和宿主监听进程占用；本项目升级时允许当前项目容器继续占用自身端口。
- 安装失败诊断补充 Compose 日志和 `ss -ltnup`，用于区分端口、Kafka及其他依赖故障。

## 兼容性

HTTP、Syslog、MQTT、API 和前端入口不变。仅 EMQX WebSocket 的宿主入口从冲突且不可用的 `8083` 调整为 `18084`。已有 `.env` 未设置该字段时自动采用 `18084`；若人工设置为冲突端口，安装器会在事务前安全拒绝。
