# 云原生威胁检测系统 - 完整开发方案 (2025年10月更新)

## 项目概述

本项目基于传统MySQL-based威胁检测系统的重构，采用云原生微服务架构，实现从分钟级延迟到秒级响应的性能提升。项目已完成架构设计和部分核心服务开发，目前正处于开发实施阶段。

## 传统系统分析 (✅ 已完成)

### 原始系统架构
- **技术栈**: MySQL + PHP/Vue.js前后端
- **核心问题**:
  - 多层聚合导致至少10分钟延迟
  - 手动离线数据导入效率低下
  - 复杂SQL存储过程难以维护
  - 单体架构扩展性差

### 数据库结构分析
- **核心表**: `jz_base_system`, `jz_base_ga_port_setting`, `jz_base_gc_attack_setting`
- **威胁算法**: `(端口权重 × IP数量 × 攻击数量) × 时间权重`
- **时间权重**: 0-5h:1.2x, 5-9h:1.1x, 9-17h:1.0x, 17-21h:0.9x, 21-24h:0.8x

### 前端系统
- **技术栈**: Vue.js 2.6 + Element UI
- **功能**: 设备管理、威胁监控、告警管理
- **特点**: 基于景治客户管理系统的定制开发

## 数据模型和接口规范

### 日志格式规格 (独立开发必读)

#### 输入日志格式
系统接收两种格式的syslog日志：

**1. JSON格式 (推荐)**
```json
{
  "@timestamp": "2025-10-09T10:00:00.000Z",
  "host": "threat-sensor-01",
  "message": "syslog_version=1.10.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=1,attack_mac=0a:7e:57:24:5b:60,attack_ip=192.168.2.59,response_ip=192.168.2.159,response_port=7,line_id=1,Iface_type=1,Vlan_id=0,log_time=1717554108,eth_type=2048,ip_type=6",
  "type": "threat-log",
  "service": "threat-detection"
}
```

**2. 纯文本格式 (兼容)**
```
syslog_version=1.10.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=1,attack_mac=0a:7e:57:24:5b:60,attack_ip=192.168.2.59,response_ip=192.168.2.159,response_port=7,line_id=1,Iface_type=1,Vlan_id=0,log_time=1717554108,eth_type=2048,ip_type=6
```

#### 攻击事件数据模型 (log_type=1)
```java
@Data
@Builder
public class AttackEvent {
    private String devSerial;      // 设备序列号
    private int logType;          // =1 (攻击事件)
    private int subType;          // 子类型
    private String attackMac;     // 攻击者MAC地址
    private String attackIp;      // 攻击者IP地址
    private String responseIp;    // 响应IP地址
    private int responsePort;     // 响应端口
    private int lineId;           // 线路ID
    private int ifaceType;        // 接口类型
    private int vlanId;           // VLAN ID
    private long logTime;         // Unix时间戳
    private int ethType;          // 以太网类型
    private int ipType;           // IP协议类型 (6=TCP, 1=ICMP)
    private LocalDateTime eventTime; // 事件时间
}
```

#### 状态事件数据模型 (log_type=2)
```java
@Data
@Builder
public class StatusEvent {
    private String devSerial;      // 设备序列号
    private int logType;          // =2 (状态事件)
    private int sentryCount;      // 哨兵数量
    private int realHostCount;    // 真实主机数量
    private long devStartTime;    // 设备启动时间
    private long logTime;         // Unix时间戳
    private LocalDateTime eventTime; // 事件时间
}
```

### Kafka主题设计
```properties
# 生产主题 (数据摄入服务写入)
kafka.topics.raw-logs=raw-logs
kafka.topics.attack-events=attack-events
kafka.topics.status-events=status-events

# 消费主题 (流处理服务读取)
input.topic=attack-events
status.topic=status-events

# 输出主题 (流处理服务写入)
output.topic=threat-alerts
aggregation.topic=minute-aggregations
```

### ClickHouse表结构
```sql
-- 攻击事件表
CREATE TABLE attack_events (
    dev_serial String,
    attack_mac String,
    attack_ip String,
    response_ip String,
    response_port UInt16,
    log_time UInt64,
    ip_type UInt8,
    event_time DateTime,
    processed_time DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (dev_serial, event_time)
TTL event_time + INTERVAL 1 YEAR;

-- 威胁评分表
CREATE TABLE threat_scores (
    dev_serial String,
    time_window_start DateTime,
    time_window_end DateTime,
    attack_count UInt32,
    unique_ips UInt32,
    port_weights AggregateFunction(sum, Float64),
    threat_score Float64,
    calculated_time DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(time_window_start)
ORDER BY (dev_serial, time_window_start)
TTL time_window_start + INTERVAL 1 YEAR;
```

### 威胁评分算法
```java
// 核心算法实现
public double calculateThreatScore(AttackEvent event) {
    // 1. 端口权重 (基于jz_base_ga_port_setting表)
    double portWeight = getPortWeight(event.getResponsePort());
    
    // 2. 时间权重 (基于jz_base_ge_time_weighting_setting表)
    double timeWeight = getTimeWeight(event.getLogTime());
    
    // 3. 计算威胁分数
    // 威胁分数 = 端口权重 × 时间权重
    // (IP数量和攻击数量在分钟级聚合中计算)
    return portWeight * timeWeight;
}

// 端口权重映射 (示例)
private double getPortWeight(int port) {
    switch (port) {
        case 22: return 8.0;   // SSH - 高危
        case 23: return 8.0;   // Telnet - 高危
        case 80: return 6.0;   // HTTP - 中高危
        case 443: return 6.0;  // HTTPS - 中高危
        case 3389: return 8.0; // RDP - 高危
        case 21: return 7.0;   // FTP - 高危
        case 25: return 6.0;   // SMTP - 中高危
        case 53: return 5.0;   // DNS - 中危
        case 110: return 6.0;  // POP3 - 中高危
        case 143: return 6.0;  // IMAP - 中高危
        case 3306: return 8.0; // MySQL - 高危
        case 5432: return 8.0; // PostgreSQL - 高危
        default: return 3.0;   // 默认权重
    }
}

// 时间权重映射 (示例)
private double getTimeWeight(long timestamp) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    int hour = dateTime.getHour();
    
    if (hour >= 0 && hour < 5) return 1.2;    // 0-5h: 提高警惕
    else if (hour >= 5 && hour < 9) return 1.1; // 5-9h: 略微提高
    else if (hour >= 9 && hour < 17) return 1.0; // 9-17h: 正常
    else if (hour >= 17 && hour < 21) return 0.9; // 17-21h: 略微降低
    else return 0.8; // 21-24h: 降低警惕
}
```

### API接口规范

#### 数据摄入服务 API
```http
# 单个日志摄入
POST /api/v1/logs/ingest
Content-Type: application/json

{
  "@timestamp": "2025-10-09T10:00:00.000Z",
  "host": "threat-sensor-01",
  "message": "syslog_version=1.10.0,dev_serial=9d262111f2476d34,log_type=1,...",
  "type": "threat-log"
}

# 批量日志摄入
POST /api/v1/logs/batch
Content-Type: application/json

[
  { "日志对象1" },
  { "日志对象2" }
]

# 健康检查
GET /actuator/health
```

#### 响应格式
```json
// 成功响应
{
  "success": true,
  "eventId": "attack-12345",
  "timestamp": "2025-10-09T10:00:00.000Z"
}

// 错误响应
{
  "success": false,
  "error": "PARSE_ERROR",
  "message": "Failed to parse log message",
  "timestamp": "2025-10-09T10:00:00.000Z"
}
```

## 云原生架构设计 (✅ 已完成)

### 整体架构
```
日志源 → 数据摄入服务 → Kafka → 流处理服务 → ClickHouse
                           → 威胁评估服务 → Redis缓存
                           → 告警管理服务 → WebSocket推送
```

### 技术栈选择
- **数据摄入**: Spring Boot 3.1+ + Kafka Producer
- **消息队列**: Apache Kafka 3.4+
- **流处理**: Apache Flink 1.17+
- **时序存储**: ClickHouse 23+
- **缓存**: Redis 7+
- **容器化**: Docker + Kubernetes 1.25+
- **服务网格**: Istio 1.18+
- **监控**: Prometheus + Grafana
- **Java版本**: OpenJDK 21 LTS

### 核心组件设计
1. **数据摄入服务**: 解析syslog日志，发布到Kafka
2. **流处理服务**: 实时分钟级聚合和威胁评分
3. **威胁评估服务**: 复杂规则引擎和历史关联分析
4. **告警管理服务**: 告警生成、分发和升级策略
5. **API网关**: 统一入口，身份认证和路由
6. **配置服务**: 动态配置管理

## 当前开发进度 (2025年10月)

### ✅ 已完成模块

#### 1. 架构设计和分析 (100%)
- [x] 传统系统性能瓶颈分析
- [x] 云原生架构设计文档
- [x] 日志格式规格定义
- [x] 数据库schema分析
- [x] 威胁算法逻辑梳理

#### ⚠️ 技术栈一致性问题
**发现问题**: 项目中存在技术栈版本不一致
- **数据摄入服务**: Java 21 + Spring Boot 3.1.0 ✅
- **流处理服务**: Java 11 + Flink 1.17.1 ⚠️ (与根项目Java 21不一致)
- **根项目配置**: pom.xml定义Java 21，但stream-processing模块覆盖为Java 11

**影响评估**: 
- 可能导致构建和运行时兼容性问题
- 增加维护复杂度
- 建议统一到Java 21 + 验证Flink兼容性

**解决建议**: 
- 将stream-processing服务升级到Java 21
- 测试Flink 1.17.1在Java 21上的兼容性
- 如不兼容，考虑升级到Flink 1.18+或1.19+

#### 2. 数据摄入服务 (90% 完成)
- [x] Spring Boot项目结构搭建
- [x] Kafka生产者服务实现
- [x] 日志解析服务 (支持攻击事件和状态事件)
- [x] REST API接口设计 (`/api/v1/logs/ingest`)
- [x] 数据模型定义 (AttackEvent, StatusEvent)
- [x] Docker镜像配置和多阶段构建
- [x] 基础单元测试和TestContainers集成
- [x] 健康检查端点
- [x] 错误处理和日志记录
- [ ] 集成测试和性能测试
- [ ] 批量处理API扩展
- [ ] 监控指标集成 (Micrometer/Prometheus)

#### 3. 流处理服务 (85% 完成)
- [x] Apache Flink作业框架搭建
- [x] Kafka消费者配置和主题管理
- [x] 分钟级聚合逻辑实现 (按设备序列号+端口)
- [x] 威胁评分算法实现 (端口权重×IP数量×攻击数量×时间权重)
- [x] 10分钟威胁评分聚合
- [x] 事件时间处理和水位线管理
- [x] Docker容器化配置
- [x] 容错和重启策略
- [x] Kafka主题自动创建和验证
- [ ] ClickHouse数据汇集成
- [ ] 状态后端优化 (RocksDB)
- [ ] 性能调优和基准测试

#### 4. 基础设施 (60% 完成)
- [x] Docker Compose开发环境
- [x] Kubernetes基础配置
- [x] ClickHouse表结构设计
- [x] Kafka主题配置
- [x] 基础监控配置
- [ ] Helm Chart完善
- [ ] CI/CD流水线配置
- [ ] 生产环境部署配置

### 🚧 开发中模块

#### 5. 威胁评估服务 (20% 完成)
- [x] 项目结构初始化
- [ ] 复杂威胁评分算法
- [ ] Redis缓存集成
- [ ] 历史数据关联分析

#### 6. 告警管理服务 (10% 完成)
- [x] 项目结构初始化
- [ ] 告警规则引擎
- [ ] WebSocket推送机制
- [ ] 告警升级策略

#### 7. API网关和配置服务 (5% 完成)
- [x] 项目结构初始化
- [ ] Spring Cloud Gateway配置
- [ ] 统一认证授权
- [ ] 配置中心搭建

### 📋 待开发模块

#### 8. 前端界面重构 (0% 完成)
- [ ] 基于Vue.js的新前端界面
- [ ] 实时威胁监控大屏
- [ ] 设备管理界面
- [ ] 告警管理界面

#### 9. 离线数据导入服务 (0% 完成)
- [ ] 文件上传和预验证
- [ ] 数据回放引擎
- [ ] 批量处理优化
- [ ] 进度监控和错误恢复

#### 10. 运维和监控 (30% 完成)
- [x] Prometheus监控基础配置
- [x] Grafana仪表板设计
- [ ] 分布式追踪 (Jaeger)
- [ ] 日志聚合 (ELK)
- [ ] 告警规则配置

## 开发环境配置

### WSL开发环境 (✅ 已配置)
```bash
# 准确环境信息 (2025年10月)
- Ubuntu 24.04.3 LTS (Noble Numbat)
- OpenJDK 21.0.8 (Ubuntu官方构建)
- Maven 3.8.7
- Docker & Docker Compose
- Kubernetes (kubectl + minikube)
- VS Code + WSL扩展
```

### 环境验证命令
```bash
# 系统信息
lsb_release -a

# Java版本
java -version
# 期望输出: openjdk version "21.0.8" 2025-07-15

# Maven版本
mvn --version
# 期望输出: Apache Maven 3.8.7

# Docker状态
docker --version
docker-compose --version

# Kubernetes工具
kubectl version --client
minikube version
```

### 本地开发环境配置
```yaml
# docker/docker-compose.yml 已配置包含:
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    # Zookeeper服务配置
    
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on: [zookeeper]
    # Kafka服务配置
    
  clickhouse:
    image: clickhouse/clickhouse-server:latest
    # ClickHouse时序数据库
    
  redis:
    image: redis:7-alpine
    # Redis缓存服务
    
  prometheus:
    image: prom/prometheus:latest
    # 监控指标收集
    
  grafana:
    image: grafana/grafana:latest
    # 可视化仪表板
```

### 项目结构验证
```bash
# 验证项目结构完整性
tree threat-detection-system -L 3

# 期望输出:
threat-detection-system/
├── docker/
│   ├── docker-compose.yml
│   └── Dockerfile
├── k8s/
│   ├── base/
│   └── overlays/
├── services/
│   ├── data-ingestion/
│   ├── stream-processing/
│   └── ...
├── docs/
├── scripts/
└── pom.xml
```

### 开发环境搭建指南

#### 前置要求
- **硬件配置**: 至少8GB RAM, 50GB可用磁盘空间
- **网络要求**: 可以访问Docker Hub和Maven中央仓库
- **权限要求**: 对Docker和WSL有管理员权限

#### 环境搭建步骤

**1. 验证基础环境**
```bash
# 确认Ubuntu版本
lsb_release -a
# 应显示: Ubuntu 24.04.3 LTS

# 确认Java版本
java -version
# 应显示: openjdk version "21.0.8"

# 确认Maven版本
mvn --version
# 应显示: Apache Maven 3.8.7
```

**2. 安装Docker (如果未安装)**
```bash
# 更新包索引
sudo apt update

# 安装必要的包
sudo apt install apt-transport-https ca-certificates curl gnupg lsb-release

# 添加Docker的官方GPG密钥
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# 设置稳定仓库
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装Docker Engine
sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 验证安装
sudo docker run hello-world
```

**3. 配置Docker用户权限**
```bash
# 将当前用户添加到docker组
sudo usermod -aG docker $USER

# 重新登录或运行以下命令使更改生效
newgrp docker

# 验证权限
docker run hello-world
```

**4. 安装Kubernetes工具**
```bash
# 安装kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# 安装minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# 验证安装
kubectl version --client
minikube version
```

**5. 启动本地开发环境**
```bash
# 进入项目目录
cd threat-detection-system

# 启动所有服务
docker-compose -f docker/docker-compose.yml up -d

# 验证服务状态
docker-compose -f docker/docker-compose.yml ps

# 查看服务日志
docker-compose -f docker/docker-compose.yml logs kafka
```

**6. 验证开发环境完整性**
```bash
# 测试Kafka连接
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# 测试ClickHouse连接
docker exec -it clickhouse clickhouse-client --query "SELECT 1"

# 测试Redis连接
docker exec -it redis redis-cli ping
```

#### 常见问题解决

**问题1: Docker权限不足**
```bash
# 解决方案
sudo usermod -aG docker $USER
newgrp docker
# 重新启动终端会话
```

**问题2: 端口冲突**
```bash
# 检查端口占用
sudo netstat -tulpn | grep :9092

# 修改docker-compose.yml中的端口映射
# 将9092:9092改为9093:9092
```

**问题3: 内存不足**
```bash
# 检查可用内存
free -h

# 减少服务资源分配或增加WSL内存
# 在.wslconfig中设置:
# [wsl2]
# memory=8GB
```

**问题4: Maven依赖下载失败**
```bash
# 配置Maven镜像
# 编辑 ~/.m2/settings.xml
<mirrors>
  <mirror>
    <id>aliyunmaven</id>
    <mirrorOf>*</mirrorOf>
    <name>阿里云公共仓库</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

## 关键技术决策

### 1. 实时性 vs 准确性平衡
- **选择**: 优先实时性，接受小幅准确性trade-off
- **理由**: 威胁检测场景下，及时发现比绝对准确更重要

### 2. 存储策略选择
- **选择**: ClickHouse (列式存储) + Redis (缓存)
- **理由**: 时序数据查询优化，热点数据快速访问

### 3. 微服务拆分粒度
- **选择**: 按业务领域拆分 (数据流、威胁评估、告警管理)
- **理由**: 独立部署、独立扩展、职责清晰

## 性能目标和验收标准

### 功能验收标准
- [ ] 支持攻击事件和状态事件实时处理
- [ ] 威胁评分算法与原系统结果一致性 > 95%
- [ ] 支持离线数据批量导入
- [ ] 完整的设备管理和监控界面

### 性能验收标准
- [ ] 处理延迟: < 30秒 (从日志接收到威胁告警)
- [ ] 吞吐量: > 1000 logs/秒
- [ ] 系统可用性: > 99.9%
- [ ] 扩展性: 支持水平扩展到10+节点

### 非功能验收标准
- [ ] 代码覆盖率: > 80%
- [ ] 文档完整性: 100%
- [ ] 部署自动化: 一键部署到K8s
- [ ] 监控覆盖: 全链路可观测

## 风险评估和应对策略

### 技术风险
1. **新架构学习曲线**
   - 应对: 核心团队已完成技术调研，制定培训计划

2. **性能达标风险**
   - 应对: 分阶段性能测试，预留优化时间

3. **数据一致性风险**
   - 应对: 建立数据校验机制，准备回滚方案

### 业务风险
1. **需求理解偏差**
   - 应对: 定期与业务方确认，原型验证

2. **迁移切换风险**
   - 应对: 灰度发布策略，A/B测试验证

### 项目风险
1. **进度延误风险**
   - 应对: 敏捷开发模式，里程碑管控

2. **人员流动风险**
   - 应对: 知识文档化，交叉培训

## 实施路线图 (2025年10月-2026年3月)

### Phase 1: 核心服务完善 (10月-11月)
**目标**: 完成数据摄入和流处理服务
**关键任务**:
- 数据摄入服务集成测试和性能优化
- 流处理服务状态管理和容错完善
- ClickHouse存储优化和查询接口
- 基础监控和告警体系搭建

**验收标准**:
- 数据摄入服务通过性能测试 (1000 logs/sec)
- 流处理服务稳定运行24小时无故障
- 威胁评分结果与原系统差异 < 5%

### Phase 2: 业务服务开发 (12月-1月)
**目标**: 完成威胁评估和告警管理服务
**关键任务**:
- 威胁评估服务复杂算法实现
- 告警管理服务规则引擎开发
- API网关和配置服务搭建
- 前端界面基础功能开发

**验收标准**:
- 支持动态威胁规则配置
- 告警实时推送延迟 < 5秒
- 前端界面完成设备监控功能

### Phase 3: 高级功能和优化 (2月-3月)
**目标**: 完善系统功能和性能优化
**关键任务**:
- 离线数据导入服务开发
- 前端界面完整功能实现
- 系统性能调优和压力测试
- 生产环境部署配置完善

**验收标准**:
- 离线导入效率提升10倍
- 系统整体性能达到设计目标
- 生产环境成功部署并稳定运行

### Phase 4: 上线和运维 (3月-4月)
**目标**: 生产环境部署和运维体系建设
**关键任务**:
- 灰度发布和A/B测试
- 运维文档和监控体系完善
- 用户培训和知识转移
- 生产环境稳定性保障

## 资源需求

### 人力配置
- **后端开发**: 2-3人 (Java/Spring Boot, Flink)
- **前端开发**: 1-2人 (Vue.js)
- **DevOps**: 1人 (Kubernetes, Docker)
- **测试**: 1人 (自动化测试, 性能测试)

### 基础设施需求
- **开发环境**: WSL + Docker Desktop
- **测试环境**: Kubernetes集群 (3节点)
- **生产环境**: 云原生环境 (AWS EKS/GCP GKE)

### 预算考虑
- **云资源**: 开发测试环境约 ¥50K/月
- **第三方服务**: Kafka/ClickHouse云服务约 ¥20K/月
- **工具许可**: IDE和监控工具约 ¥10K/年

## 质量保证计划

### 代码质量
- **代码审查**: 所有PR需要至少1人审查
- **单元测试**: 核心业务逻辑覆盖率 > 80%
- **集成测试**: 自动化E2E测试覆盖主要流程
- **性能测试**: 定期进行压力测试和基准测试

### 发布管理
- **分支策略**: Git Flow (main/develop/feature branches)
- **CI/CD**: GitHub Actions自动化构建部署
- **版本管理**: Semantic Versioning
- **回滚策略**: 支持快速回滚到上一个稳定版本

### 监控和告警
- **应用监控**: Prometheus + Grafana
- **基础设施监控**: Kubernetes metrics
- **业务监控**: 自定义业务指标
- **告警策略**: 分级告警，自动升级

## 成功衡量指标

### 技术指标
1. **性能指标**: 延迟、吞吐量、资源利用率
2. **质量指标**: 代码覆盖率、缺陷密度、MTTR
3. **可用性指标**: SLA达成率、故障恢复时间

### 业务指标
1. **功能完整性**: 需求完成率、用户满意度
2. **效率提升**: 处理速度提升倍数、成本节约
3. **创新价值**: 新功能 adoption率、技术债务减少

### 项目管理指标
1. **进度达成**: 里程碑完成率、预算控制
2. **团队效率**: 开发速度、协作效率
3. **知识积累**: 文档完整性、培训覆盖

## 总结

本项目已完成架构设计和核心组件的基础开发，具备了良好的技术基础和清晰的实施路径。通过云原生重构，将实现从传统单体架构到现代化微服务架构的华丽转身，显著提升系统的性能、可维护性和扩展性。

**当前状态**: 核心技术验证完成，进入大规模开发实施阶段
**关键成功因素**: 技术选型合理、团队经验丰富、需求理解清晰
**预期收益**: 性能提升10倍以上，维护成本降低50%，业务价值显著提升

---
*文档版本: v2.0*
*更新日期: 2025年10月9日*
*文档状态: 正式发布*</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/complete_development_plan_2025.md