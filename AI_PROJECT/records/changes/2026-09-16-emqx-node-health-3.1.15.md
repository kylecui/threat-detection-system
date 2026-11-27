# 3.1.15 EMQX 单机节点健康整改

## 现象

3.1.14 在 `192.168.100.8` 完成容器启动后，PostgreSQL、Kafka、API、前端及其他服务均健康，但安装器等待 EMQX 600 秒后失败并回滚。

## 根因

- EMQX 日志明确显示服务已经运行，TCP、TLS、WebSocket 和 Dashboard 监听均已启动。
- Compose 设置 `EMQX_NAME=threat-detection-emqx` 与 `EMQX_HOST=emqx`，最终节点名为 `threat-detection-emqx@emqx`。
- 容器内执行 `emqx ctl status` 返回 1，提示该节点不响应 ping，因此 Docker 健康状态永久为 unhealthy。
- 100.9 对照测试确认 `emqx@emqx` 和 `threat_detection_emqx@emqx` 同样失败；不强制节点名时，镜像生成 IP 型节点名并正常响应控制命令。
- 目标机曾出现高 CPU/内存告警，但资源压力只影响启动速度，不会解释节点命令在压力解除后仍持续失败。

## 修改

- 从单机 Compose 删除 `EMQX_NAME` 和 `EMQX_HOST`，由 EMQX 5.5.1 镜像根据当前容器地址生成节点名。
- 保留 `emqx ctl status` 健康检查，继续验证 Erlang 节点和控制面真实可用，而不是降低为端口检查。
- 保留现有 `1883`、`8883`、宿主 `18084 -> 8083`、`18083` 映射和 EMQX 数据卷。
- 增加静态发布契约与 Docker 发布包级回归，验证单节点 EMQX healthy、CLI 返回 0、节点 started 且重启次数为 0。

## 边界

本次仅修复单机节点命名和健康检查兼容性，不启用集群、不调整认证/ACL/TLS策略，也不修改客户端日志与心跳接入协议。
