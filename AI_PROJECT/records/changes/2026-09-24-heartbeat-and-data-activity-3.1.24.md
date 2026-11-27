# 3.1.24 心跳与数据活跃状态整改

## 发现的问题

- 客户端 v1.0.4 的 V1 Syslog 心跳默认每 1800 秒发送一次，平台却在最后心跳超过 5 分钟后直接显示“离线”。设备因此在每个 30 分钟周期中最多约 5 分钟显示在线。
- 客户端“数据服务器已连接”表示 `9080/tcp` 可达；平台旧“连接状态”表示最近心跳；“最后数据”又可能来自攻击日志。不同语义使用相似名称，形成“客户端已连接、平台离线、分析链路正常”的表面矛盾。

## 原因

- Customer Management 将 `Duration.ofMinutes(5)` 写死为心跳离线阈值，没有使用客户端 V1 Syslog 的真实周期。
- API 只有 `connectionState` 与 `pipelineState`，没有独立的数据活跃状态，页面无法表达“攻击日志仍在进入但心跳已经延迟”。

## 修改

- 新增 `DeviceTelemetryPolicy`，默认期望周期 1800 秒、延迟阈值 2100 秒、离线阈值 3900 秒、数据活跃窗口 2100 秒，并对非法阈值在启动时直接报错。
- 心跳状态改为 `DISABLED/NEVER_SEEN/ONLINE/DELAYED/OFFLINE`；数据活跃状态为 `DISABLED/NEVER_SEEN/ACTIVE/IDLE`。
- API 返回 `heartbeatState`、`dataActivityState`、期望周期、延迟阈值和离线阈值；旧 `connectionState` 暂时保留为心跳状态的兼容别名。
- 前端分别展示绑定、心跳、数据活跃和分析链路；设备详情显示当前心跳判定规则，并扩大设备抽屉避免新增列挤压为竖排文字。

## 配置

以下变量由安装器生成的 `.env` 传入 Customer Management，默认无需人工修改：

```text
DEVICE_EXPECTED_HEARTBEAT_INTERVAL_SECONDS=1800
DEVICE_HEARTBEAT_DELAYED_AFTER_SECONDS=2100
DEVICE_HEARTBEAT_OFFLINE_AFTER_SECONDS=3900
DEVICE_DATA_ACTIVE_WINDOW_SECONDS=2100
```

如果以后调整客户端 V1 Syslog 心跳周期，必须同步调整这些变量，且满足：延迟阈值不少于一个心跳周期，离线阈值大于延迟阈值。

## 兼容边界

- 不改变客户端、Logstash、Kafka、攻击日志、威胁评估或 MQTT 链路。
- 不改变数据库结构，不需要新增迁移脚本。
- 不把攻击日志到达伪装成心跳在线；它只会使数据活跃状态为 `ACTIVE`。
