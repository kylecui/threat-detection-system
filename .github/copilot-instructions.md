# GitHub Copilot 项目指令

**项目名称**: Cloud-Native Threat Detection System  
**版本**: 2.0  
**更新日期**: 2025-10-22  
**重要更新**: 基于蜜罐/诱饵机制的正确理解 + 文档导航指南

---

## ⚠️ 核心理解 - 蜜罐机制

### 系统本质

**这是一个内网蜜罐/欺骗防御系统,而非传统的边界防御系统**

### 工作原理

```
终端设备 (dev_serial) 功能:
1. 监控二层网络,收集设备在线情况
2. 识别未使用IP作为诱饵(虚拟哨兵)
3. 主动响应ARP/ICMP,诱导攻击者进一步行动
4. 记录攻击者对诱饵的端口访问尝试(但不再响应)

威胁来源: 内网失陷主机(已被攻破的内部设备)
检测对象: APT横向移动、勒索软件传播、内部渗透
关键特征: 所有访问诱饵IP的行为都是恶意的(误报率极低)
```

### 数据字段正确理解

| 字段 | ❌ 错误理解 | ✅ 正确理解 |
|------|------------|------------|
| `attack_mac` | 外部攻击者MAC | **被诱捕的内网主机MAC** |
| `attack_ip` | 外部攻击源IP | **被诱捕的内网主机IP** |
| `response_ip` | 被攻击的服务器IP | **诱饵IP (不存在的虚拟哨兵)** |
| `response_port` | 被攻击的服务端口 | **攻击者尝试访问的端口 (暴露攻击意图)** |

**关键**: 
- `response_ip` 是**诱饵/蜜罐IP**,不是真实服务器
- `response_port` 反映**攻击意图** (如3389=远程控制, 445=横向移动)
- 任何访问诱饵IP的行为都是**确认的恶意行为**

---

## 项目概述

### 项目目标

将一个传统的基于 C#/Windows/MySQL 的威胁检测系统迁移到**云原生微服务架构**，实现：

1. **跨平台支持**: Windows → Linux (Ubuntu 24.04 LTS)
2. **架构现代化**: 客户端-服务器 → 微服务 + 流处理
3. **性能提升**: 批处理 (10-30分钟延迟) → 实时流处理 (< 4分钟延迟)
4. **可扩展性**: 单机 MySQL → 分布式 Kafka + Flink
5. **功能增强**: 在保持与原系统对齐的基础上增加新检测维度

### 核心技术栈

| 组件 | 技术选型 | 版本要求 |
|------|---------|---------|
| **开发语言** | Java | OpenJDK 21 LTS |
| **应用框架** | Spring Boot | 3.1.5 |
| **消息队列** | Apache Kafka | 3.4+ |
| **流处理引擎** | Apache Flink | 1.17+ |
| **数据库** | PostgreSQL | 15+ |
| **容器化** | Docker | 20.10+ |
| **编排** | Kubernetes | 1.25+ |
| **构建工具** | Maven | 3.8.7+ |

---

## 架构原则

### 1. 微服务设计

**服务清单**:

- **data-ingestion**: 日志摄取和解析
- **stream-processing**: 实时流处理和威胁评分
- **threat-assessment**: 威胁评估和存储
- **alert-management**: 告警管理和通知
- **api-gateway**: API网关和路由
- **config-server**: 配置管理中心

**设计要求**:

```markdown
- 单一职责: 每个服务只负责一个业务域
- 服务自治: 独立部署、独立扩展
- 轻量级通信: REST API (同步) + Kafka (异步)
- 容错设计: 优雅降级、熔断保护
- 可观测性: 结构化日志、Prometheus指标
```

### 2. 数据流架构

```
设备syslog → rsyslog → Data Ingestion → Kafka (attack-events)
                                           ↓
                                    Flink Stream Processing
                                           ↓
                                    Kafka (minute-aggregations)
                                           ↓
                                    Flink Threat Scoring
                                           ↓
                                    Kafka (threat-alerts)
                                           ↓
                            PostgreSQL ← Threat Assessment
```

**要求**:
- 事件驱动: 异步解耦，消息传递
- 流式处理: 窗口聚合，状态管理
- 数据完整性: 端到端可追溯
- 性能目标: < 4分钟端到端延迟

### 3. 多租户隔离

**租户标识**: `customerId` (客户ID)

**隔离策略**:
- Kafka分区: 按 `customerId` 分区
- Flink分组: 按 `customerId:attackMac` 分组
- PostgreSQL: 表级别 `customer_id` 字段

**要求**:
- 所有数据操作必须带 `customerId`
- 查询必须过滤 `customer_id`
- 避免跨租户数据泄露

---

## 威胁评分算法

### 核心公式

```java
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × ipWeight 
            × portWeight 
            × deviceWeight
```

### 权重计算标准

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 说明 |
|--------|------|------|
| 0:00-6:00 | 1.2 | 深夜异常行为 |
| 6:00-9:00 | 1.1 | 早晨时段 |
| 9:00-17:00 | 1.0 | 工作时间基准 |
| 17:00-21:00 | 0.9 | 傍晚时段 |
| 21:00-24:00 | 0.8 | 夜间时段 |

**实现**:
```java
private double calculateTimeWeight(Instant timestamp) {
    LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
    int hour = time.getHour();
    
    if (hour >= 0 && hour < 6) return 1.2;
    if (hour >= 6 && hour < 9) return 1.1;
    if (hour >= 9 && hour < 17) return 1.0;
    if (hour >= 17 && hour < 21) return 0.9;
    return 0.8;
}
```

#### IP权重 (ipWeight)

| 唯一IP数量 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单一目标 |
| 2-3 | 1.3 | 小范围扫描 |
| 4-5 | 1.5 | 中等扫描 |
| 6-10 | 1.7 | 广泛扫描 |
| 10+ | 2.0 | 大规模扫描 |

#### 端口权重 (portWeight)

| 唯一端口数量 | 权重 | 说明 |
|-------------|------|------|
| 1 | 1.0 | 单端口攻击 |
| 2-3 | 1.2 | 小范围探测 |
| 4-5 | 1.4 | 中等扫描 |
| 6-10 | 1.6 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 全端口扫描 |

#### 设备权重 (deviceWeight)

| 唯一设备数量 | 权重 | 说明 |
|-------------|------|------|
| 1 | 1.0 | 单一设备 |
| 2+ | 1.5 | 多设备攻击 (横向移动) |

### 威胁等级划分

| 等级 | 分数范围 | 说明 |
|------|---------|------|
| INFO | < 10 | 信息级别 |
| LOW | 10-50 | 低危威胁 |
| MEDIUM | 50-100 | 中危威胁 |
| HIGH | 100-200 | 高危威胁 |
| CRITICAL | > 200 | 严重威胁 |

---

## 数据结构规范

### Kafka消息格式

#### attack-events (攻击事件)

```json
{
  "attackMac": "00:11:22:33:44:55",      // 被诱捕者MAC (内网失陷主机)
  "attackIp": "192.168.1.100",           // 被诱捕者IP (内网地址)
  "responseIp": "10.0.0.1",              // 诱饵IP (不存在的虚拟哨兵)
  "responsePort": 3306,                  // 攻击者尝试的端口 (暴露攻击意图: 数据窃取)
  "deviceSerial": "DEV-001",             // 终端蜜罐设备序列号
  "customerId": "customer-001",
  "timestamp": "2024-01-15T10:30:00Z",
  "logTime": 1705315800
}
```

#### minute-aggregations (1分钟聚合)

```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",     // 被诱捕者MAC
  "uniqueIps": 5,                       // 访问的诱饵IP数量 (横向移动范围)
  "uniquePorts": 3,                     // 尝试的端口种类 (攻击意图多样性)
  "uniqueDevices": 2,                   // 检测到该攻击者的设备数
  "attackCount": 150,                   // 探测次数
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### threat-alerts (威胁告警)

```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 125.5,
  "threatLevel": "HIGH",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "timestamp": "2024-01-15T10:32:00Z"
}
```

### PostgreSQL表结构

#### threat_assessments (威胁评估)

```sql
CREATE TABLE threat_assessments (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    assessment_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_mac (customer_id, attack_mac),
    INDEX idx_assessment_time (assessment_time),
    INDEX idx_threat_level (threat_level)
);
```

---

## 开发规范

### 代码风格

**Java代码**:
```java
// 使用 Lombok 简化代码
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackEvent {
    private String attackMac;
    private String attackIp;
    private String responseIp;
    private int responsePort;
    private String deviceSerial;
    private String customerId;
    private Instant timestamp;
    private long logTime;
}

// 使用 SLF4J 日志
@Slf4j
public class DataIngestionService {
    public void processLog(String syslogMessage) {
        log.info("Processing syslog: customerId={}, size={}", 
                 customerId, syslogMessage.length());
        try {
            // 业务逻辑
        } catch (Exception e) {
            log.error("Failed to process log: customerId={}", customerId, e);
        }
    }
}

// 使用 CompletableFuture 异步处理
public CompletableFuture<Void> sendToKafka(AttackEvent event) {
    return CompletableFuture.runAsync(() -> {
        kafkaTemplate.send("attack-events", event.getCustomerId(), event);
    }, executorService);
}
```

### 错误处理

**要求**:
1. 所有异常必须记录日志 (包含 `customerId`)
2. 关键服务使用熔断器 (Resilience4j)
3. Kafka消费者: 失败重试 + 死信队列
4. API响应: 统一错误格式

**示例**:
```java
@CircuitBreaker(name = "threatAssessment", fallbackMethod = "fallbackAssessment")
public ThreatAlert assessThreat(AggregatedData data) {
    try {
        // 威胁评估逻辑
        return calculator.calculate(data);
    } catch (Exception e) {
        log.error("Threat assessment failed: customerId={}, error={}", 
                  data.getCustomerId(), e.getMessage(), e);
        throw new ThreatAssessmentException("Assessment failed", e);
    }
}

public ThreatAlert fallbackAssessment(AggregatedData data, Exception e) {
    log.warn("Using fallback assessment: customerId={}", data.getCustomerId());
    // 返回默认评估结果
    return ThreatAlert.builder()
        .threatLevel("UNKNOWN")
        .threatScore(0.0)
        .build();
}
```

### 测试要求

**单元测试**:
```java
@SpringBootTest
class ThreatScoreCalculatorTest {
    
    @Test
    void testTimeWeightCalculation() {
        ThreatScoreCalculator calculator = new ThreatScoreCalculator();
        
        // 深夜时段
        Instant midnight = Instant.parse("2024-01-15T02:00:00Z");
        assertEquals(1.2, calculator.calculateTimeWeight(midnight));
        
        // 工作时段
        Instant workHour = Instant.parse("2024-01-15T10:00:00Z");
        assertEquals(1.0, calculator.calculateTimeWeight(workHour));
    }
    
    @Test
    void testPortWeightCalculation() {
        ThreatScoreCalculator calculator = new ThreatScoreCalculator();
        
        assertEquals(1.0, calculator.calculatePortWeight(1));
        assertEquals(1.2, calculator.calculatePortWeight(3));
        assertEquals(1.6, calculator.calculatePortWeight(10));
        assertEquals(2.0, calculator.calculatePortWeight(25));
    }
    
    @Test
    void testThreatScoreCalculation() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .attackCount(100)
            .uniqueIps(5)
            .uniquePorts(3)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2024-01-15T02:00:00Z"))  // 深夜
            .build();
        
        ThreatScoreCalculator calculator = new ThreatScoreCalculator();
        double score = calculator.calculate(data);
        
        // 预期: (100 × 5 × 3) × 1.2 (time) × 1.5 (ip) × 1.2 (port) × 1.0 (device)
        // = 1500 × 2.16 = 3240
        assertTrue(score > 3000);
    }
}
```

**集成测试**:
```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"attack-events", "threat-alerts"})
class StreamProcessingIntegrationTest {
    
    @Autowired
    private KafkaTemplate<String, AttackEvent> kafkaTemplate;
    
    @Test
    void testEndToEndProcessing() throws Exception {
        // 1. 发送测试事件
        AttackEvent event = AttackEvent.builder()
            .customerId("test-customer")
            .attackMac("00:11:22:33:44:55")
            .attackIp("192.168.1.100")
            .responseIp("10.0.0.1")
            .responsePort(3306)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("attack-events", event.getCustomerId(), event).get();
        
        // 2. 等待处理
        Thread.sleep(5000);
        
        // 3. 验证结果
        List<ThreatAlert> alerts = threatAlertRepository.findByCustomerId("test-customer");
        assertFalse(alerts.isEmpty());
    }
}
```

---

## 性能要求

### 吞吐量

| 服务 | 目标吞吐量 | 当前性能 |
|------|-----------|---------|
| **Data Ingestion** | 10000+ events/s | 验证中 |
| **Stream Processing** | 10000+ events/s | 验证中 |
| **Threat Assessment** | 1000+ assessments/s | 验证中 |

### 延迟

| 指标 | 目标 | 当前 |
|------|------|------|
| **端到端延迟** | < 4分钟 | < 4分钟 ✅ |
| **聚合窗口** | 30秒 | 30秒 ✅ |
| **评分窗口** | 2分钟 | 2分钟 ✅ |
| **API响应** | < 1秒 | < 1秒 ✅ |

### 资源限制

**Docker容器**:
```yaml
services:
  data-ingestion:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

**Kubernetes Pod**:
```yaml
resources:
  limits:
    cpu: "2000m"
    memory: "2Gi"
  requests:
    cpu: "1000m"
    memory: "1Gi"
```

---

## 配置管理

### 环境变量

**必需配置**:
```bash
# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=threat-detection-group

# PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/threat_detection
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-password

# Flink
FLINK_CHECKPOINTING_INTERVAL=60000
FLINK_PARALLELISM=4

# 应用
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
```

### 配置文件

**application.yml**:
```yaml
spring:
  application:
    name: data-ingestion-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
    consumer:
      group-id: ${KAFKA_GROUP_ID}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest

logging:
  level:
    com.threatdetection: INFO
    org.apache.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - customerId=%X{customerId} - %msg%n"
```

---

## 与原系统对齐

### 功能对齐清单

| 功能 | 原系统 | 云原生系统 | 状态 |
|------|--------|-----------|------|
| **日志解析** | ✅ rsyslog + C# | ✅ rsyslog + Java | ✅ 完全对齐 |
| **客户隔离** | ✅ company_obj_id | ✅ customerId | ✅ 完全对齐 |
| **时间权重** | ✅ 5个时段 | ✅ 5个时段 | ✅ 完全对齐 |
| **IP统计** | ✅ sum_ip | ✅ uniqueIps | ✅ 完全对齐 |
| **攻击计数** | ✅ count_attack | ✅ attackCount | ✅ 完全对齐 |
| **端口权重** | ✅ 219个端口配置 | ⚠️ 多样性算法 | ⚠️ 简化实现 |
| **网段权重** | ✅ 186个网段配置 | ❌ 未实现 | 🔴 待实施 |
| **设备多样性** | ❌ 无 | ✅ deviceWeight | ✅ 增强功能 |
| **威胁分级** | ✅ 3级 | ✅ 5级 | ✅ 增强功能 |
| **标签管理** | ✅ 743条 | ❌ 未实现 | 🔴 待实施 |

### 待实施功能

**优先级: 高**
- ❌ 网段权重配置和计算
- ❌ 端口风险配置表 (混合策略)

**优先级: 中**
- ❌ 标签/白名单系统
- ❌ IP/MAC资产管理

**优先级: 低**
- ❌ 报表生成系统
- ❌ 用户权限管理

---

## 迁移策略

### 阶段1: 核心功能 (已完成 ✅)

- ✅ 实时日志摄取
- ✅ 基础威胁评分
- ✅ 多租户隔离
- ✅ 时间权重
- ✅ 威胁分级

### 阶段2: 功能增强 (进行中 🔄)

- 🔄 端口风险配置
- 🔄 网段权重实现
- ⏳ 标签管理系统

### 阶段3: 完整对齐 (计划中 📋)

- 📋 完整端口配置 (219个)
- 📋 网段配置 (186个)
- 📋 报表生成

---

## Copilot 工作指令

### 编写代码时

1. **必须遵循**:
   - 所有服务处理必须包含 `customerId` 参数和日志
   - 使用 Lombok 简化样板代码
   - 使用 SLF4J 进行日志记录
   - 异常必须记录详细上下文 (customerId, 错误详情)
   - 遵循 Spring Boot 3.1+ 最佳实践

2. **威胁评分**:
   - 严格按照公式: `(attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight`
   - 权重计算必须符合规范表格
   - 威胁等级划分必须使用5级分类

3. **数据处理**:
   - Kafka消息必须包含所有必需字段
   - 端口信息必须在聚合过程中保留 (`uniquePorts`)
   - 时间戳必须使用 `Instant` 类型
   - 多租户数据必须隔离

### 审查代码时

**检查清单**:
- [ ] 是否包含 `customerId` 处理
- [ ] 是否有完整的异常处理和日志
- [ ] 是否符合威胁评分公式
- [ ] 是否保留端口信息
- [ ] 是否有单元测试
- [ ] 是否符合性能要求
- [ ] 是否遵循多租户隔离

### 优化代码时

**优先考虑**:
1. 性能优化 (减少延迟)
2. 资源使用优化 (内存、CPU)
3. 代码可读性
4. 错误处理完善性

### 解释代码时

**必须说明**:
- 该代码在整个系统架构中的位置
- 与原系统的对应关系 (如果有)
- 威胁评分逻辑的详细解释
- 多租户隔离如何实现
- **蜜罐机制**:response_ip是诱饵IP,response_port反映攻击意图

---

## 文档规范

### 代码注释

```java
/**
 * 计算威胁评分
 * 
 * <p>公式: threatScore = (attackCount × uniqueIps × uniquePorts) 
 *                     × timeWeight × ipWeight × portWeight × deviceWeight
 * 
 * <p>蜜罐机制说明:
 * - uniqueIps = 攻击者访问的诱饵IP数量 (横向移动范围)
 * - uniquePorts = 攻击者尝试的端口种类 (攻击意图多样性)
 * - attackCount = 对诱饵的探测次数 (所有访问都是恶意的)
 * 
 * <p>对齐原系统: total_score = count_port × sum_ip × count_attack × score_weighting
 * 
 * @param data 聚合攻击数据 (包含 attackCount, uniqueIps, uniquePorts, uniqueDevices)
 * @return 威胁评分 (0.0 - 无限大，通常 < 1000)
 * @throws ThreatAssessmentException 评分计算失败时抛出
 */
public double calculateThreatScore(AggregatedAttackData data) {
    // 实现
}
```

### README文档

每个服务必须包含:
- 服务用途说明
- 环境变量配置
- 启动方法
- API端点文档
- 故障排查指南

---

## 📚 文档导航指南 (AI助手必读)

### 🎯 标准文档使用流程

**AI助手必须始终遵循以下三步文档导航流程**，确保为用户提供准确、完整的项目信息：

#### 📋 第一步：项目概况 (README.md)
**位置**: `/README.md` (项目根目录)
**目的**: 了解项目整体架构、技术栈、快速启动
**内容**: 
- 系统架构介绍
- 技术栈说明
- 快速启动指南
- 核心特性说明
- 部署选项

#### 🔍 第二步：文档导航定位 (DOCUMENTATION_INDEX.md)
**位置**: `docs/DOCUMENTATION_INDEX.md`
**目的**: 获取完整的文档目录，快速定位所需信息
**内容**:
- 完整的文档分类目录
- 文档搜索指南
- 使用频率统计
- 文档质量评估

#### 📂 第三步：深入具体文档
**按需选择相应子目录**:
- **API文档**: `docs/api/` - REST API接口详细说明
- **设计规范**: `docs/design/` - 架构设计和技术规范
- **测试指南**: `docs/testing/` - 测试方法和自动化脚本
- **构建部署**: `docs/build/` - Docker构建和部署指南
- **审计报告**: `docs/audit/` - 代码质量和安全审计
- **修复记录**: `docs/fixes/` - Bug修复和技术债务清理

### 🚨 AI助手工作要求

#### 文档引用规范
1. **必须引用最新文档**: 优先使用 `docs/` 目录下的文档
2. **遵循导航流程**: 在回答问题前，先确认用户是否已阅读基础文档
3. **提供完整路径**: 引用文档时提供相对路径和简要说明
4. **引导用户导航**: 如果用户直接询问具体问题，引导他们先阅读基础文档

#### 回答问题时的流程
```
用户提问 → 检查是否需要基础知识 → 引导阅读相应文档 → 提供具体答案
```

**示例**:
- 用户问"如何启动系统？" → 引导阅读 `README.md` 的快速启动部分
- 用户问"API怎么用？" → 引导先看 `docs/DOCUMENTATION_INDEX.md`，再去 `docs/api/`
- 用户问"测试怎么做？" → 引导查看 `docs/testing/` 目录

#### 文档更新同步
- 文档结构变更时，必须同步更新此导航指南
- 新增重要文档时，更新相应的导航路径
- 确保导航指南始终反映最新的文档组织结构

---

## 参考文档

### 项目文档

- **README.md**: 项目概述和快速开始
- **USAGE_GUIDE.md**: 详细使用指南
- **docs/honeypot_based_threat_scoring.md**: 基于蜜罐机制的威胁评分方案 ⭐ 最新
- **docs/understanding_corrections_summary.md**: 理解修正总结 ⭐ 重要
- **docs/new_system_architecture_spec.md**: 云原生系统架构规范
- **docs/original_system_analysis.md**: 原始系统分析报告
- **docs/threat_scoring_solution.md**: 威胁评分解决方案
- **docs/data_structures.md**: 数据结构定义
- **docs/log_format_analysis.md**: 日志格式分析

**注意**: 优先参考 `honeypot_based_threat_scoring.md` 和 `understanding_corrections_summary.md`

### 外部资源

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Apache Flink Documentation](https://flink.apache.org/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

---

**文档结束**

*保持此文档与项目演进同步更新*
*AI助手必须遵循文档导航三步流程：README.md → DOCUMENTATION_INDEX.md → 具体子目录*
