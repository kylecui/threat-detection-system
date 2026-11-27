# 项目概况

## 定位

Threat Detection System 是 JZ 客户端的数据接收与威胁分析平台。客户端“数据服务器”配置指向本平台后，平台接收攻击日志和设备心跳，并完成消息缓冲、流处理、威胁评估、存储、告警和 Web 展示。

它不属于配置控制链路，不替代 `jz-cloud-mqtt-server` 或 `server_for_mqtt`：

- `jz-cloud-mqtt-server`：账号、控制消息和 MQTT/mTLS 中转。
- `server_for_mqtt`：配置、命令、状态和 ACK 管控。
- `jz_sniff_rn`：设备侧采集、防护、状态与数据上报。
- `threat-detection-system`：攻击日志/心跳接入和威胁分析。

## 当前版本

- 当前源码/交付版本：`3.1.39`（已构建并回传，未部署；验证边界见 `records/tests/2026-10-30-release-3.1.39.md`）
- 安装目标：Ubuntu 24.04 x86_64
- 安装目录：`/opt/threat-detection-system`
- 正式入口：发布包根目录 `sudo ./install.sh`
- 自启单元：`threat-detection-compose.service`
- 运行时看门狗：`threat-detection-runtime-watchdog.timer`
- 诊断入口：`sudo tdsdiag`

## 技术栈

- Java 21、Spring Boot、Spring Cloud Gateway
- Kafka、ZooKeeper、Flink
- PostgreSQL 15、Redis 7
- Logstash、EMQX 5.5
- Python/FastAPI、PyTorch/ONNX Runtime
- React/Vite/Nginx
- Docker Engine 与 Docker Compose v2

## 数据入口

- HTTP：`:8080/api/v1/logs/ingest`、`:8080/api/v1/logs/batch`
- V1 Syslog：`:9080/tcp`
- V2 MQTT：`:1883`
- 心跳与攻击事件分别进入 `status-events` 和 `attack-events`。
