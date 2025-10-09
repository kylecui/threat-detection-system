# 日志格式分析总结

## 概述
通过分析logstash处理后的JSON日志文件，确定了数据摄入服务的输入数据格式。日志主要分为攻击事件和状态事件两种类型。

## 日志结构
每个日志条目都是JSON格式，包含以下顶级字段：
- `service`: 服务信息
- `host`: 主机信息 (IP和hostname)
- `type`: 类型标识
- `message`: 解析后的关键值对字符串
- `process`: 进程信息
- `@version`: 版本号
- `event.original`: 原始syslog消息
- `@timestamp`: 时间戳
- `log.syslog`: syslog元数据

## 攻击事件 (log_type=1)
**标识**: `log_type=1`

**关键字段**:
- `syslog_version`: syslog版本 (1.10.0)
- `dev_serial`: 设备序列号 (9d262111f2476d34)
- `log_type`: 日志类型 (1=攻击)
- `sub_type`: 子类型 (1)
- `attack_mac`: 攻击者MAC地址
- `attack_ip`: 攻击者IP地址
- `response_ip`: 响应IP地址
- `response_port`: 响应端口
- `line_id`: 线路ID
- `Iface_type`: 接口类型
- `Vlan_id`: VLAN ID
- `log_time`: Unix时间戳
- `eth_type`: 以太网类型
- `ip_type`: IP协议类型 (6=TCP, 1=ICMP)

## 状态事件 (log_type=2)
**标识**: `log_type=2`

**关键字段**:
- `syslog_version`: syslog版本
- `dev_serial`: 设备序列号
- `log_type`: 日志类型 (2=状态)
- `sentry_count`: 哨兵数量
- `real_host_count`: 真实主机数量
- `dev_start_time`: 设备启动时间
- `log_time`: Unix时间戳

## 数据摄入服务设计建议

### 1. 消息格式定义
```java
// 攻击事件
public class AttackEvent {
    private String devSerial;
    private int logType; // =1
    private String attackMac;
    private String attackIp;
    private String responseIp;
    private int responsePort;
    private long logTime;
    private int ipType; // 6=TCP, 1=ICMP
    // ... 其他字段
}

// 状态事件
public class StatusEvent {
    private String devSerial;
    private int logType; // =2
    private int sentryCount;
    private int realHostCount;
    private long devStartTime;
    private long logTime;
}
```

### 2. Kafka主题设计
- `threat-attack-events`: 攻击事件
- `threat-status-events`: 状态事件

### 3. 解析逻辑
- 从`message`字段解析key-value对
- 验证`log_type`字段确定事件类型
- 转换时间戳为标准格式
- 过滤无效或不完整的事件

### 4. 错误处理
- 记录解析失败的事件
- 实现死信队列处理无法解析的消息
- 添加监控指标跟踪解析成功率

## 性能考虑
- 日志量大，需要高效的字符串解析
- 考虑使用多线程处理
- 实现批量发送到Kafka以提高吞吐量

## 下一步开发建议
1. 实现Spring Boot数据摄入服务
2. 配置Kafka生产者
3. 实现日志解析器
4. 添加单元测试和集成测试
5. 配置监控和日志记录