# 场景感知日志导入系统

## 概述

场景感知日志导入系统支持根据不同的生产需求采用合适的日志处理策略，解决了传统批量导入中的去重、合并和隔离问题。

## 支持的导入场景

### 1. 系统迁移 (Migration)
**适用场景**: 从旧系统迁移到新系统，需要保持历史数据的连续性

**特点**:
- 合并历史APT积累数据
- 保持威胁评估的时间序列连续性
- 支持跨时间段数据整合
- 自动去重防止重复处理

**使用方法**:
```bash
python bulk_log_import.py /path/to/migration/logs --mode migration
```

### 2. 数据补全 (Completion)
**适用场景**: 补全特定客户的历史数据，修复数据丢失或不完整的情况

**特点**:
- 客户数据隔离处理
- 避免影响其他客户数据
- 增量数据补全策略
- 按客户ID过滤和合并

**使用方法**:
```bash
python bulk_log_import.py /path/to/customer/logs --mode completion --customer-id customer-001
```

### 3. 离线分析 (Offline)
**适用场景**: 安全研究和威胁情报分析，不影响生产系统

**特点**:
- 独立处理，不影响生产数据
- 生成全局威胁关联分析
- 支持复杂的安全研究查询
- 专门的分析数据库存储

**使用方法**:
```bash
python bulk_log_import.py /path/to/research/logs --mode offline
```

## 核心特性

### 事件去重
- 使用Redis实现24小时去重窗口
- 支持全局和客户特定去重策略
- 防止重复威胁分析和数据污染

### 场景路由
- 根据导入模式自动选择处理逻辑
- 不同的合并和聚合策略
- 性能和隔离性优化

### 数据合并策略

#### APT积累合并 (Migration模式)
- 合并历史时序累积数据
- 保持威胁评分的时间连续性
- 支持复杂的时间序列分析

#### 威胁评估合并 (Migration/Completion模式)
- 智能合并重复评估记录
- 保留更完整和最新的数据
- 累加攻击统计信息

#### 全局威胁关联 (Offline模式)
- 生成跨客户的威胁关联分析
- 支持安全研究和情报工作
- 独立存储，不影响生产数据

## API接口

### REST API端点

#### 通用场景导入
```http
POST /api/v1/import/scenario
Content-Type: application/json

{
  "mode": "migration|completion|offline",
  "customerId": "customer-001",  // completion模式时必需
  "logs": ["syslog_line_1", "syslog_line_2", ...]
}
```

#### 专用模式导入
```http
POST /api/v1/import/migration
POST /api/v1/import/completion/{customerId}
POST /api/v1/import/offline
```

#### 获取支持模式
```http
GET /api/v1/import/modes
```

## 使用示例

### 1. 系统迁移
```bash
# 干运行检查
python bulk_log_import.py migration_logs/ --mode migration --dry-run

# 执行迁移
python bulk_log_import.py migration_logs/ --mode migration --batch-size 200
```

### 2. 客户数据补全
```bash
# 为特定客户补全数据
python bulk_log_import.py customer_data/ --mode completion --customer-id customer-001

# 批量补全多个客户
for customer in customer-001 customer-002 customer-003; do
    python bulk_log_import.py customer_data/${customer}/ --mode completion --customer-id ${customer}
done
```

### 3. 离线安全研究
```bash
# 导入研究数据
python bulk_log_import.py research_logs/ --mode offline --host analysis.example.com

# 大批量导入
python bulk_log_import.py research_logs/ --mode offline --batch-size 500 --delay 2.0
```

## 向后兼容

系统保持对传统Logstash TCP导入的兼容：

```bash
# 传统模式 (仍支持)
python bulk_log_import.py logs/ --legacy-logstash --host logstash.example.com --port 9080
```

## 性能优化

### 批量处理
- 默认批量大小: 100条日志
- 可配置批量大小和延迟
- 自动错误重试和熔断

### 去重机制
- Redis分布式缓存
- 24小时去重窗口
- 低延迟键值操作

### 监控指标
- 处理成功率统计
- 去重率监控
- 性能时间指标

## 故障排查

### 常见问题

#### 连接失败
```
错误: 无法连接到Data Ingestion Service
解决: 检查服务是否运行，端口是否正确
```

#### 客户ID缺失
```
错误: completion模式必须指定--customer-id
解决: 为completion模式提供customer-id参数
```

#### 日志格式错误
```
警告: 无法提取有效的syslog内容
解决: 检查日志格式是否符合syslog标准
```

### 调试模式

使用`--dry-run`参数进行调试：
```bash
python bulk_log_import.py logs/ --mode migration --dry-run --max-batches 1
```

## 架构说明

### 服务组件

```
Data Ingestion Service
├── ScenarioAwareImportService     # 场景感知导入服务
├── ThreatAssessmentMergeService   # 威胁评估合并服务
├── AptTemporalAccumulationService # APT积累服务
├── ImportController              # REST API控制器
└── RedisTemplate                 # 去重缓存
```

### 数据流

```
日志文件 → 解析 → 去重过滤 → 场景路由 → 相应处理 → 结果返回
```

## 扩展开发

### 添加新的导入模式

1. 在`ImportMode`枚举中添加新模式
2. 在`ScenarioAwareImportService`中实现处理逻辑
3. 在`ImportController`中添加对应的API端点
4. 更新`bulk_log_import.py`脚本支持

### 自定义合并策略

继承`ThreatAssessmentMergeService`并重写合并方法，实现自定义的数据合并逻辑。

## 安全考虑

- 客户数据隔离：不同客户的补全操作完全隔离
- 审计日志：所有导入操作都会记录详细的审计信息
- 访问控制：API端点支持基于角色的访问控制
- 数据加密：敏感数据在传输和存储时进行加密

## 监控和告警

系统提供全面的监控指标：
- 导入成功率
- 处理延迟
- 错误率统计
- 资源使用情况

建议设置告警规则监控关键指标的异常情况。