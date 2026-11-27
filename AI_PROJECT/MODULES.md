# 模块说明

| 模块 | 路径 | 职责 |
|---|---|---|
| Data Ingestion | `services/data-ingestion/` | 接收 HTTP、Syslog/MQTT 转换后的攻击日志与心跳，写入 Kafka/数据库 |
| Stream Processing | `services/stream-processing/` | Flink 实时窗口、聚合、评分与事件输出 |
| Threat Assessment | `services/threat-assessment/` | 威胁评分、风险评估和历史分析 |
| Alert Management | `services/alert-management/` | 告警生命周期、去重、通知与升级 |
| Customer Management | `services/customer-management/` | 客户、设备、多租户和配置管理 |
| Threat Intelligence | `services/threat-intelligence/` | IOC、信誉和外部情报源 |
| TIRE | `services/tire/` | 外部情报插件执行、查询缓存与情报运行时 API |
| API Gateway | `services/api-gateway/` | Web/API 统一入口、JWT 与 RBAC |
| Config Server | `services/config-server/` | 基于 `config-repo/` 的服务配置 |
| ML Detection | `services/ml-detection/` | Python 模型推理和 Kafka 异步检测 |
| Frontend | `frontend/` | React 管理界面，Nginx 静态部署 |
| Logstash | `infrastructure/logging/logstash/` | V1 Syslog 接入与结构化处理 |
| Compose | `docker/docker-compose.yml` | 基础设施、应用服务、网络、卷和健康检查 |
| Installer | `install.sh` | 唯一安装脚本，负责校验、依赖、自动配置、构建、回滚、自启、卸载与 purge |
| Diagnostics | `scripts/tdsdiag.sh` | 一键只读运行诊断 |
| Runtime watchdog | `scripts/runtime-watchdog.sh`、`deploy/systemd/threat-detection-runtime-watchdog.*` | 识别 Docker/containerd 状态失同步并定向重建异常服务 |
| Release | `scripts/pack_release.py` | 生成最小生产归档、内部/外部 SHA-256 和内容清单 |
