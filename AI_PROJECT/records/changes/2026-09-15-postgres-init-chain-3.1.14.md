# 3.1.14 PostgreSQL 初始化链整改

## 现象

3.1.13 在全新目标机启动 Compose 时，PostgreSQL 初始化失败，安装器随后回滚。失败诊断中同时出现 PostgreSQL、Kafka 状态异常，容易被 Compose 进度汇总误导。

## 根因

- `docker/03-port-weights.sql` 实际只有一行 `port_weights_migration.sql`，不是合法 SQL。
- PostgreSQL 官方入口以 `ON_ERROR_STOP` 执行初始化目录，因而在第 3 个脚本立即中断，07 号及后续表未创建。
- `restart: unless-stopped` 可能让 PostgreSQL 以已部分初始化的数据目录重新启动并变为 healthy，但不会重新执行初始化目录，形成“容器健康、数据库结构不完整”的假象。
- Kafka 日志没有发现独立业务错误；其 unhealthy 状态发生在较长的首次启动阶段。

## 修改

- Compose 直接将真实的 `docker/port_weights_migration.sql` 挂载为容器内 `03-port-weights.sql`。
- 删除无效的同名占位文件，保持迁移内容只有一个权威来源。
- 安装器在任何事务性变更前校验全部 PostgreSQL 初始化挂载，拒绝缺失、空文件、非法路径、目标重名和仅含 SQL 文件名的占位内容。
- 发布契约检查每个挂载源的实际内容；Docker 集成回归在空卷执行完整脚本链并检查日志、重启次数及核心表。
- PostgreSQL 健康探针从单独 `pg_isready` 加强为连接加四张关键表检查，部分初始化不会再被容器重启掩盖。
- Kafka 继续用 `kafka-topics --list` 验证真实可用性，但将首次冷启动宽限调整为 180 秒、随后最多重试 10 次，以覆盖 100.8 上整套服务并发启动的资源竞争；超时仍会失败回滚。

## 边界

本次不修改业务表设计、服务 API、数据入口、端口或已有数据卷迁移策略。`docker-entrypoint-initdb.d` 仍只负责全新数据卷；已有卷升级仍需独立迁移机制。
