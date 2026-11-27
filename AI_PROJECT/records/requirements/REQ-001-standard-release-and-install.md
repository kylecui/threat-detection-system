# REQ-001 统一发布、安装与协作资料

日期：2026-09-15

## 原始目标

参照客户端、MQTT Broker 和配置平台，给 Threat Detection System 增加 `AI_PROJECT/` 与 `outputs/` 设计，并优先统一正式安装脚本和 tar.gz 内部结构。构建测试固定在 `192.168.100.9` 执行。

## 实施边界

- 不修改客户端、Broker 或配置平台。
- 不重写威胁检测业务算法和 API。
- 保留现有 Compose 微服务架构、HTTP/Syslog/MQTT 数据入口和历史数据库初始化逻辑。
- 仅修复发布安装所必需的秘密、Compose 依赖和同机命名问题。

## 验收标准

见 `AI_PROJECT/REQUIREMENTS.md` 的 REQ-001。
