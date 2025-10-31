# 批量日志导入脚本

## 概述

`bulk_log_import.py` 是一个用于将日志文件批量导入到威胁检测系统的脚本。它通过Logstash的TCP输入通道发送日志，支持多种日志格式的自动解析。

## 功能特性

- **多种日志格式支持**: 自动识别和解析纯syslog格式、JSON包装格式等
- **批量发送**: 支持分批发送，避免一次性发送过多日志造成系统压力
- **连接测试**: 发送前自动测试Logstash TCP连接
- **进度显示**: 实时显示发送进度和统计信息
- **错误处理**: 详细的错误信息和失败统计
- **干运行模式**: 可以预览将要发送的日志而不实际发送

## 使用方法

### 基本用法

```bash
# 导入指定目录下的所有日志文件
python scripts/bulk_log_import.py tmp/production_test_logs/2025-09-18

# 或直接运行脚本（需要执行权限）
./scripts/bulk_log_import.py tmp/production_test_logs/2025-09-18
```

### 高级选项

```bash
# 指定Logstash地址和端口
python scripts/bulk_log_import.py tmp/production_test_logs/2025-09-18 \
    --host logstash.example.com \
    --port 9080

# 干运行模式（仅显示日志，不发送）
python scripts/bulk_log_import.py tmp/production_test_logs/2025-09-18 --dry-run

# 自定义批量大小和延迟
python scripts/bulk_log_import.py tmp/production_test_logs/2025-09-18 \
    --batch-size 50 \
    --delay 2.0
```

## 参数说明

| 参数 | 必需 | 默认值 | 说明 |
|------|------|--------|------|
| `directory` | 是 | - | 日志文件目录路径 |
| `--host` | 否 | localhost | Logstash主机地址 |
| `--port` | 否 | 9080 | Logstash TCP端口 |
| `--dry-run` | 否 | False | 仅显示日志，不实际发送 |
| `--batch-size` | 否 | 100 | 每批发送的日志数量 |
| `--delay` | 否 | 1.0 | 批次间延迟秒数 |
| `--timeout` | 否 | 5.0 | TCP连接超时秒数 |

## 日志格式支持

脚本自动识别以下日志格式：

### 1. 纯syslog格式
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=00:68:eb:bb:c7:c6,attack_ip=192.168.73.98,response_ip=192.168.73.37,response_port=3306,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274810
```

### 2. JSON包装格式 (ELK Stack)
```json
{
  "event": {
    "original": "syslog_version=1.10.0,dev_serial=...,log_type=1,..."
  }
}
```

### 3. 简单JSON格式
```json
{
  "message": "syslog_version=1.10.0,dev_serial=...,log_type=1,..."
}
```

## 系统要求

- Python 3.6+
- 网络连接到Logstash TCP端口 (默认9080)
- 日志文件目录可读权限

## 工作流程

1. **目录扫描**: 查找指定目录下的所有 `.log` 文件
2. **日志解析**: 逐个解析日志文件，提取有效的syslog内容
3. **连接测试**: 测试到Logstash的TCP连接
4. **批量发送**: 分批通过TCP发送日志到Logstash
5. **结果统计**: 显示发送成功/失败的统计信息

## 故障排除

### 连接失败
```
错误: 无法连接到Logstash localhost:9080
```
**解决方案**:
- 确保Logstash容器正在运行: `docker-compose ps`
- 检查Logstash配置中的TCP端口
- 验证防火墙设置

### 没有找到日志文件
```
错误: 在目录 tmp/production_test_logs/2025-09-18 中未找到任何 .log 文件
```
**解决方案**:
- 检查目录路径是否正确
- 确保日志文件扩展名为 `.log`
- 验证目录权限

### 日志格式错误
```
警告: file.log:123 - 无法提取有效的syslog内容
```
**解决方案**:
- 检查日志文件格式是否符合预期
- 使用 `--dry-run` 模式查看无法解析的日志
- 确认日志包含必需字段: `syslog_version`, `dev_serial`, `log_type`

## 示例输出

```
找到 3 个日志文件:
  - attack_logs_2025-09-18.log
  - heartbeat_logs_2025-09-18.log
  - mixed_logs_2025-09-18.log

解析日志文件...
处理文件 1/3: attack_logs_2025-09-18.log
  提取到 500 条有效日志
处理文件 2/3: heartbeat_logs_2025-09-18.log
  提取到 10 条有效日志
处理文件 3/3: mixed_logs_2025-09-18.log
  提取到 245 条有效日志

解析完成:
  - 处理文件数: 3
  - 总日志数: 755
  - 攻击日志: 500
  - 心跳日志: 10
  - 其他日志: 245

测试Logstash连接: localhost:9080
连接成功 ✓

开始发送日志...
目标: localhost:9080
批量大小: 100
批次延迟: 1.0秒

发送批次 1/8 (100 条日志)
  批次结果: 成功=100, 失败=0
发送批次 2/8 (100 条日志)
  批次结果: 成功=100, 失败=0
...

发送完成!
总日志数: 755
成功发送: 755
发送失败: 0
总耗时: 8.50秒
平均速度: 88.82 日志/秒

所有日志发送成功 ✓
```

## 集成说明

该脚本与系统的Logstash配置完全兼容：

- **输入**: TCP端口9080 (无TLS)
- **解析**: key=value格式，逗号分隔
- **输出**: 攻击日志→`attack-events` topic，心跳日志→`status-events` topic
- **格式**: 自动转换为camelCase JSON格式

发送完成后，日志将通过完整的流处理管道进行处理，包括威胁评分、告警生成等。