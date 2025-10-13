# 云原生威胁检测系统架构重构方案

## 架构概述

采用微服务架构 + 事件驱动 + 流处理的全新设计，替代传统的单体数据库聚合模式。

## 核心组件

### 1. 数据采集服务 (Data Ingestion Service)
- **技术栈**: Spring Boot + Kafka Producer
- **职责**: 接收设备日志，发布到消息队列
- **部署**: Kubernetes Deployment，水平扩展
- **存储**: 不存储数据，直接转发

### 2. 流处理引擎 (Stream Processing Engine)
- **技术栈**: Apache Flink + Kafka Streams
- **职责**:
  - 实时分钟级聚合
  - 实时10分钟威胁评分计算
  - 实时告警规则匹配
- **优势**: 低延迟、高吞吐、状态管理

### 3. 威胁评估服务 (Threat Assessment Service)
- **技术栈**: Spring Boot + Redis
- **职责**:
  - 复杂威胁评分算法
  - 历史数据关联分析
  - 动态规则引擎
- **存储**: Redis缓存 + PostgreSQL配置

### 4. 告警管理服务 (Alert Management Service)
- **技术栈**: Spring Boot + WebSocket
- **职责**:
  - 告警生成和分发
  - 告警升级策略
  - 用户通知管理

### 5. 配置管理服务 (Configuration Service)
- **技术栈**: Spring Cloud Config + Git
- **职责**: 动态配置管理，支持热更新

## 数据架构

### 消息队列 (Event Streaming)
```yaml
topics:
  - raw-logs: 原始日志流
  - minute-aggregations: 分钟聚合结果
  - threat-scores: 威胁评分结果
  - alerts: 告警事件
```

### 存储层 (Storage Layer)
- **时序数据库**: InfluxDB/ClickHouse (分钟级/10分钟级聚合)
- **关系数据库**: PostgreSQL (配置、用户、历史记录)
- **缓存**: Redis Cluster (热点数据、会话、临时聚合)
- **对象存储**: MinIO/S3 (日志归档)

## 部署架构

### Kubernetes集群
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: threat-detection

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-ingestion
  namespace: threat-detection
spec:
  replicas: 3
  selector:
    matchLabels:
      app: data-ingestion
  template:
    metadata:
      labels:
        app: data-ingestion
    spec:
      containers:
      - name: ingestion
        image: threat-detection/ingestion:latest
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

### 服务网格 (Service Mesh)
- Istio: 服务间通信、流量管理、熔断限流
- Kiali: 可视化服务拓扑

## 可观测性

### 监控指标
- Prometheus: 应用指标、服务健康
- Grafana: 可视化仪表板
- AlertManager: 告警管理

### 日志聚合
- Fluentd: 日志收集
- Elasticsearch: 日志存储和搜索
- Kibana: 日志分析界面

### 分布式追踪
- Jaeger: 请求链路追踪
- OpenTelemetry: 标准化观测数据

## 弹性伸缩

### 水平扩展
- HPA (Horizontal Pod Autoscaler): 基于CPU/内存自动扩缩容
- KEDA: 基于事件驱动的扩缩容

### 容错设计
- Circuit Breaker: 熔断保护
- Retry & Timeout: 重试和超时机制
- Graceful Shutdown: 优雅关闭

## 数据流设计

### 实时处理流程
```
原始日志 → Kafka → Flink → 分钟聚合 → Kafka → 威胁评估 → Redis缓存 → 告警判断 → Kafka → 通知服务
```

### 批处理补充
```
历史数据 → Spark → 离线分析 → Elasticsearch → 趋势分析
```

## 配置管理

### 动态配置
```yaml
threat-rules:
  high-threshold: 80
  medium-threshold: 50
  time-weights:
    00:00-05:59: 1.2
    06:00-08:59: 1.1
    09:00-17:59: 0.9
    18:00-23:59: 1.0
```

### 特性开关
- 新算法灰度发布
- 告警规则A/B测试
- 性能优化开关

## 迁移策略

### 渐进式迁移
1. **第一阶段**: 数据采集服务上线，平行运行
2. **第二阶段**: 流处理引擎替换SQL聚合
3. **第三阶段**: 微服务逐步替换原有功能
4. **第四阶段**: 旧系统下线

### 数据迁移
- 双写策略: 新旧系统同时写入
- 数据校验: 对比新旧系统结果一致性
- 灰度切换: 逐步将流量切换到新系统

## 性能优化

### 缓存策略
- 多级缓存: L1(应用内) → L2(Redis) → L3(数据库)
- 缓存预热: 启动时加载热点数据
- 缓存失效: 基于时间+事件驱动

### 数据库优化
- 读写分离: 主库写，从库读
- 分库分表: 按时间/地域分片
- 索引优化: 复合索引，覆盖索引

## 安全考虑

### 网络安全
- 网络策略(Network Policy): 限制服务间通信
- mTLS: 服务间加密通信
- API网关: 统一入口，身份认证

### 数据安全
- 数据加密: 传输加密+存储加密
- 访问控制: RBAC + ABAC
- 审计日志: 所有操作可追溯

## 运维效率

### CI/CD流水线
- GitOps: 基础设施即代码
- 自动化测试: 单元测试、集成测试、性能测试
- 蓝绿部署/金丝雀发布

### 灾难恢复
- 多区域部署: 跨AZ容灾
- 数据备份: 自动备份和恢复
- 故障演练: 定期进行故障注入测试

## 成本优化

### 资源利用
- 按需伸缩: 根据实际负载调整资源
- Spot实例: 使用成本更低的Spot实例
- 存储分层: 热数据SSD，冷数据HDD/对象存储

### 监控成本
- 指标采样: 高频指标降采样
- 日志压缩: 日志自动压缩和清理
- 存储生命周期: 自动删除过期数据

## 实施路线图

### Phase 1 (1-2个月): 基础架构搭建
- Kubernetes集群部署
- CI/CD流水线建设
- 基础监控体系

### Phase 2 (2-3个月): 核心服务开发
- 数据采集服务
- 流处理引擎
- 基础威胁评估

### Phase 3 (1-2个月): 高级功能
- 告警管理
- 配置管理
- 用户界面

### Phase 4 (1个月): 测试和优化
- 性能测试
- 稳定性测试
- 安全评估

### Phase 5 (1个月): 上线和运维
- 灰度发布
- 监控调优
- 文档完善

## 预期收益

### 性能提升
- 响应时间: 从分钟级降到秒级
- 吞吐量: 提升10-100倍
- 资源利用: 降低50%成本

### 可维护性
- 代码复杂度: 大幅降低
- 部署频率: 从月级到日级
- 故障恢复: 从小时级到分钟级

### 业务价值
- 实时性: 威胁检测从延迟到实时
- 准确性: 更复杂的算法和规则
- 扩展性: 轻松应对业务增长

## 离线数据导入解决方案

### 痛点分析
当前传统架构的离线数据导入存在以下问题：
- **手动操作**: 需要人工干预数据导入过程
- **聚合触发**: 导入后需要手动触发复杂的聚合计算
- **数据一致性**: 难以保证导入数据与实时数据的时序一致性
- **错误恢复**: 导入失败后难以回滚和重试
- **成本高昂**: 每次导入都需要专门的人力投入

### 云原生离线数据导入架构

#### 1. 数据导入服务 (Data Import Service)
- **技术栈**: Spring Boot + Apache Camel
- **支持格式**: CSV, JSON, Parquet, 数据库导出文件
- **导入方式**: 
  - 文件上传 (Web界面)
  - SFTP/FTP服务器
  - 云存储 (S3/MinIO)
  - 数据库直连

#### 2. 数据验证和预处理
```java
@Service
public class DataValidationService {
    
    public ValidationResult validateAndPreprocess(ImportRequest request) {
        // 1. 格式验证
        // 2. 数据质量检查
        // 3. 时间范围验证
        // 4. 去重处理
        // 5. 数据标准化
    }
}
```

#### 3. 流数据回放 (Data Replay Engine)
- **基于Kafka**: 将历史数据重新发布到消息队列
- **时序控制**: 按照原始时间戳顺序回放，保证时序正确性
- **速度控制**: 可调节回放速度，避免系统过载

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-replay-engine
spec:
  template:
    spec:
      containers:
      - name: replay
        image: threat-detection/replay:latest
        env:
        - name: REPLAY_SPEED
          value: "10x"  # 10倍速回放
        - name: START_TIME
          value: "2023-01-01T00:00:00Z"
        - name: END_TIME
          value: "2023-01-01T23:59:59Z"
```

#### 4. 增量同步机制
- **变更数据捕获 (CDC)**: 监听源数据库变更
- **增量导入**: 只导入新增/修改的数据
- **断点续传**: 支持导入中断后从断点继续

### 导入流程设计

#### 自动导入流程
```
文件上传 → 格式验证 → 数据预处理 → 写入临时存储 → 
触发回放引擎 → 流式处理 → 结果验证 → 清理临时数据
```

#### 手动导入流程 (Web界面)
```typescript
// 前端界面
@Component
export class DataImportComponent {
  async importData(file: File, options: ImportOptions) {
    // 1. 文件上传
    const uploadId = await this.uploadService.upload(file);
    
    // 2. 配置导入参数
    const config = {
      replaySpeed: options.speed,
      startTime: options.timeRange.start,
      endTime: options.timeRange.end,
      deduplication: true
    };
    
    // 3. 启动导入任务
    const taskId = await this.importService.startImport(uploadId, config);
    
    // 4. 监控进度
    this.monitorProgress(taskId);
  }
}
```

### 数据一致性保证

#### 时序一致性
- **时间戳保留**: 保持原始数据的时间戳
- **水位线管理**: 使用Flink的水位线机制处理乱序数据
- **重复数据处理**: 基于时间戳和内容去重

#### 状态一致性
- **快照机制**: 导入前创建系统状态快照
- **回滚支持**: 导入失败时可回滚到快照状态
- **版本控制**: 数据版本管理和冲突解决

### 性能优化

#### 并行处理
```yaml
# Kubernetes Job for batch import
apiVersion: batch/v1
kind: Job
metadata:
  name: data-import-batch
spec:
  parallelism: 5  # 并行度
  completions: 10 # 总任务数
  template:
    spec:
      containers:
      - name: importer
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "1000m"
            memory: "2Gi"
```

#### 资源管理
- **队列管理**: 限制并发导入任务数量
- **资源配额**: 根据数据量动态分配计算资源
- **智能调度**: 根据系统负载调整导入速度

### 监控和告警

#### 导入监控指标
```prometheus
# 导入进度
data_import_progress{task_id="12345"} 0.75

# 导入速度
data_import_speed{task_id="12345"} 1000

# 错误统计
data_import_errors_total{task_id="12345", type="validation"} 5

# 数据质量
data_import_quality{task_id="12345", metric="duplicates"} 0.02
```

#### 告警规则
```yaml
groups:
- name: data-import
  rules:
  - alert: DataImportFailed
    expr: data_import_status{status="failed"} > 0
    for: 5m
    labels:
      severity: critical
    
  - alert: DataImportSlow
    expr: data_import_speed < 100
    for: 10m
    labels:
      severity: warning
```

### 安全考虑

#### 数据安全
- **加密传输**: TLS 1.3加密数据传输
- **存储加密**: 数据在MinIO中加密存储
- **访问控制**: 基于角色的导入权限管理

#### 审计日志
```json
{
  "event": "data_import_started",
  "task_id": "import-20231005-001",
  "user": "admin",
  "file": "threat_logs_202309.csv",
  "size": "2.5GB",
  "timestamp": "2023-10-05T10:00:00Z"
}
```

### 成本优化

#### 存储成本
- **压缩存储**: 使用Parquet格式压缩历史数据
- **分层存储**: 热数据SSD，冷数据对象存储
- **生命周期管理**: 自动清理过期临时文件

#### 计算成本
- **按需扩缩**: 导入期间自动增加计算资源
- **Spot实例**: 使用成本更低的Spot实例处理批量导入
- **智能调度**: 避开业务高峰期执行大型导入任务

### 实施案例

#### 场景1: 设备离线数据补录
```
问题: 某设备网络故障3天，积累了大量日志
解决方案: 
1. 导出设备本地日志文件
2. 上传到导入服务
3. 设置回放速度为5x（加快处理）
4. 自动触发威胁重新评估
5. 生成补充告警报告
```

#### 场景2: 历史数据迁移
```
问题: 从老系统迁移5年历史数据
解决方案:
1. 分批次导入（按月）
2. 并行处理多个批次
3. 增量更新威胁评估结果
4. 验证数据完整性
5. 生成迁移报告
```

### API设计

#### 导入任务管理
```http
# 创建导入任务
POST /api/v1/import/tasks
{
  "source": "file",
  "fileId": "upload-123",
  "options": {
    "replaySpeed": "2x",
    "timeRange": {
      "start": "2023-01-01T00:00:00Z",
      "end": "2023-01-02T00:00:00Z"
    }
  }
}

# 查询任务状态
GET /api/v1/import/tasks/{taskId}

# 取消任务
DELETE /api/v1/import/tasks/{taskId}
```

### 集成测试

#### 导入流程测试
```java
@Test
public void testDataImportWorkflow() {
    // 1. 准备测试数据
    File testFile = createTestDataFile();
    
    // 2. 上传文件
    String uploadId = importService.uploadFile(testFile);
    
    // 3. 创建导入任务
    ImportTask task = importService.createTask(uploadId, importOptions);
    
    // 4. 等待完成
    await().atMost(5, MINUTES).until(() -> 
        importService.getTaskStatus(task.getId()) == COMPLETED);
    
    // 5. 验证结果
    verifyImportedData(task.getId());
}
```

### 运维指南

#### 日常监控
- 导入任务队列长度
- 平均导入速度
- 失败任务数量
- 存储使用情况

#### 故障处理
- **导入失败**: 检查文件格式，重新上传
- **处理超时**: 调整回放速度，增加资源
- **数据不一致**: 使用快照回滚，重新导入

#### 性能调优
- 根据数据量调整并行度
- 监控系统资源使用情况
- 定期清理临时文件和日志

这个离线数据导入解决方案完全解决了传统架构的痛点，将原来需要人工干预的高成本操作变成了自动化、可监控、可扩展的服务。