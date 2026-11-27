# 3.1.16 空数据、启动稳定性与性能整改

## 原因

- 旧初始化链写入默认管理员和演示业务数据，无法做到真正从零上线。
- 容器 healthy 早于业务依赖完全可用，首次进入页面可能遇到 500/503。
- Kafka 单分区、同步请求等待、固定 Flink 并行度、热点多次聚合和逐事件日志不适合每天数百万条的目标负载。

## 修改

- 安装流程统一只由 systemd 启动 Compose，移除安装器中重复的直接 `compose up`。
- 前端启动依赖从 Gateway `service_healthy` 调整为 `service_started`，避免低配主机上 Gateway 启动期的瞬时 unhealthy 直接中断 Compose；安装器仍执行最终健康与 readiness 验收。
- 清空生产业务 seed，保留系统字典；新增并发安全的一次性首管理员 bootstrap 与 Web 引导。
- Gateway 新增聚合 readiness；安装器要求 readiness/bootstrap 连续成功、Kafka 分区正确、空业务库及容器稳定。
- Kafka 默认 12 分区并启用批量/zstd/幂等，接入使用有界反压和异步 ACK，Flink Sink 并行度可配置并持久 checkpoint。
- 增加 PostgreSQL 组合/BRIN 索引、autovacuum 参数和单次统计聚合；降低热路径日志量。
- 前端按路由拆包、有限重试并隔离首页局部失败。
- Kubernetes production overlay 增加无状态多副本、PDB/HPA；文档明确有状态集群和长期归档仍是生产 HA 前置项。
- 正式包首次全栈验收发现 Alert Management 显式连接测试语句在 Hikari 连接池启动后触发 sealed 配置错误；删除多余配置并回归锁定为 JDBC4 连接验证。
- 持久化 Flink checkpoint 卷增加一次性最小权限初始化服务，启动前将目录设为运行用户 `9999:9999`，并在安装验收中检查初始化退出码、属主和可写性。

## 不变边界

- 不改变客户端、控制面 Broker 或配置平台代码。
- 保留 HTTP、V1 Syslog、V2 MQTT 数据接入协议和端口。
- 不对 `192.168.100.8` 执行 purge 或部署；3.1.16 只在 `192.168.100.9` 构建和全新安装验证。
