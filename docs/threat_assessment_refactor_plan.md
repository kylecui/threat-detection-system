# Threat Assessment Service 完整重构计划

**版本**: 2.0  
**创建日期**: 2025-10-20  
**状态**: 🔄 进行中

---

## 一、当前状态评估

### 1.1 已完成功能 ✅

#### 查询服务层 (ThreatQueryService)
- ✅ 统计查询 (getStatistics)
- ✅ 趋势分析 (getThreatTrend - 24小时聚合)
- ✅ 端口分布 (getPortDistribution)
- ✅ 分页查询 (getAssessmentList)
- ✅ 详情查询 (getAssessmentDetail)

#### REST API (AssessmentController)
- ✅ GET /statistics - 威胁统计
- ✅ GET /trend - 趋势数据
- ✅ GET /port-distribution - 端口分布
- ✅ GET /assessments - 分页查询
- ✅ GET /{id} - 详情查询
- ✅ GET /health - 健康检查

#### 前端集成
- ✅ Dashboard页面完全正常
- ✅ ThreatList页面完全正常
- ✅ 数据格式完全对齐 (驼峰命名)
- ✅ 图表渲染正常

#### 基础设施
- ✅ PostgreSQL数据库连接
- ✅ Kafka配置
- ✅ Docker部署
- ✅ API Gateway集成

### 1.2 待重构功能 🔴

#### 核心评分引擎 (当前状态: 已备份为.bak)
- ❌ ThreatAlertConsumer - Kafka消费者 (从threat-alerts读取)
- ❌ ThreatScoreCalculator - 威胁评分计算器
- ❌ RiskAssessmentService - 威胁评估服务

#### 待实现高级功能
- ❌ 端口风险配置表 (219个端口)
- ❌ 网段权重配置 (186个网段)
- ❌ 标签/白名单系统 (743条)
- ❌ 实时威胁评分流处理

### 1.3 现有服务 (需要保留)
- ✅ DeviceHealthAlertConsumer - 设备健康告警消费者
- ✅ DeviceSerialToCustomerMappingService - 设备-客户映射
- ✅ ThreatIntelligenceService - 威胁情报服务
- ✅ RecommendationEngine - 缓解建议引擎

---

## 二、重构目标

### 2.1 核心目标

1. **恢复实时评分功能**
   - 从Kafka消费threat-alerts主题
   - 实时计算威胁分数
   - 写入PostgreSQL数据库
   - 支持多租户隔离

2. **完善威胁评分算法**
   - 实现完整的时间权重 (5个时段)
   - 实现IP权重 (5个级别)
   - 实现端口权重 (6个级别)
   - 实现设备权重 (2个级别)
   - 支持端口风险配置表
   - 支持网段权重配置

3. **增强系统可靠性**
   - 添加熔断器 (Resilience4j)
   - 实现失败重试机制
   - 添加死信队列
   - 完善错误处理和日志

4. **提升代码质量**
   - 恢复单元测试
   - 添加集成测试
   - 完善文档注释
   - 遵循最佳实践

### 2.2 非功能目标

- **性能**: 支持1000+ assessments/s吞吐量
- **延迟**: 端到端延迟 < 4分钟
- **可靠性**: 99.9%可用性
- **可维护性**: 清晰的代码结构和文档

---

## 三、详细重构计划

### Phase 1: 核心评分引擎重构 (优先级: 🔴 最高)

#### 3.1 创建 ThreatScoreCalculator

**文件**: `service/ThreatScoreCalculator.java`

**职责**: 威胁分数计算

**核心方法**:
```java
public class ThreatScoreCalculator {
    /**
     * 计算威胁分数
     * 公式: (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
     */
    public double calculateThreatScore(AggregatedAttackData data) {
        double baseScore = data.getAttackCount() * data.getUniqueIps() * data.getUniquePorts();
        double timeWeight = calculateTimeWeight(data.getTimestamp());
        double ipWeight = calculateIpWeight(data.getUniqueIps());
        double portWeight = calculatePortWeight(data.getUniquePorts());
        double deviceWeight = calculateDeviceWeight(data.getUniqueDevices());
        
        return baseScore * timeWeight * ipWeight * portWeight * deviceWeight;
    }
    
    // 时间权重: 0-6: 1.2, 6-9: 1.1, 9-17: 1.0, 17-21: 0.9, 21-24: 0.8
    private double calculateTimeWeight(Instant timestamp);
    
    // IP权重: 1: 1.0, 2-3: 1.3, 4-5: 1.5, 6-10: 1.7, 10+: 2.0
    private double calculateIpWeight(int uniqueIps);
    
    // 端口权重: 1: 1.0, 2-3: 1.2, 4-5: 1.4, 6-10: 1.6, 11-20: 1.8, 20+: 2.0
    private double calculatePortWeight(int uniquePorts);
    
    // 设备权重: 1: 1.0, 2+: 1.5
    private double calculateDeviceWeight(int uniqueDevices);
    
    // 威胁等级判定
    public ThreatLevel determineThreatLevel(double score);
}
```

**输入**: AggregatedAttackData (来自Flink或直接从数据库查询)
**输出**: double threatScore + ThreatLevel

**对齐原系统**:
- ✅ 时间权重: 完全对齐 (5个时段)
- ✅ IP权重: 完全对齐 (5个级别)
- ✅ 端口权重: 简化版 (6个级别，暂不使用219个端口配置)
- ✅ 设备权重: 增强功能 (原系统无)

#### 3.2 创建 ThreatAssessmentService

**文件**: `service/ThreatAssessmentService.java`

**职责**: 威胁评估业务逻辑

**核心方法**:
```java
@Service
@Slf4j
public class ThreatAssessmentService {
    
    private final ThreatScoreCalculator calculator;
    private final ThreatAssessmentRepository repository;
    private final RecommendationEngine recommendationEngine;
    
    /**
     * 评估威胁 (同步方法)
     */
    @CircuitBreaker(name = "threatAssessment", fallbackMethod = "fallbackAssessment")
    public ThreatAssessment assessThreat(AggregatedAttackData data) {
        log.info("Assessing threat: customerId={}, attackMac={}", 
                 data.getCustomerId(), data.getAttackMac());
        
        try {
            // 1. 计算威胁分数
            double score = calculator.calculateThreatScore(data);
            ThreatLevel level = calculator.determineThreatLevel(score);
            
            // 2. 生成缓解建议
            List<String> recommendations = recommendationEngine.generateRecommendations(
                data.getAttackMac(), level, data.getUniquePorts()
            );
            
            // 3. 创建评估记录
            ThreatAssessment assessment = ThreatAssessment.builder()
                .customerId(data.getCustomerId())
                .attackMac(data.getAttackMac())
                .attackIp(data.getAttackIp())
                .threatScore(score)
                .threatLevel(level.name())
                .attackCount(data.getAttackCount())
                .uniqueIps(data.getUniqueIps())
                .uniquePorts(data.getUniquePorts())
                .uniqueDevices(data.getUniqueDevices())
                .assessmentTime(Instant.now())
                .portList(String.join(",", data.getPortList()))
                .mitigationRecommendations(recommendations)
                .build();
            
            // 4. 保存到数据库
            return repository.save(assessment);
            
        } catch (Exception e) {
            log.error("Threat assessment failed: customerId={}, attackMac={}", 
                      data.getCustomerId(), data.getAttackMac(), e);
            throw new ThreatAssessmentException("Assessment failed", e);
        }
    }
    
    /**
     * 熔断降级方法
     */
    public ThreatAssessment fallbackAssessment(AggregatedAttackData data, Exception e) {
        log.warn("Using fallback assessment: customerId={}, attackMac={}", 
                 data.getCustomerId(), data.getAttackMac());
        
        return ThreatAssessment.builder()
            .customerId(data.getCustomerId())
            .attackMac(data.getAttackMac())
            .threatScore(0.0)
            .threatLevel(ThreatLevel.UNKNOWN.name())
            .assessmentTime(Instant.now())
            .build();
    }
}
```

#### 3.3 创建 ThreatAlertConsumer

**文件**: `service/ThreatAlertConsumer.java`

**职责**: 从Kafka消费威胁告警并评估

**核心逻辑**:
```java
@Service
@Slf4j
public class ThreatAlertConsumer {
    
    private final ThreatAssessmentService assessmentService;
    
    /**
     * 消费threat-alerts主题
     */
    @KafkaListener(
        topics = "${kafka.topic.threat-alerts:threat-alerts}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeThreatAlert(
        @Payload ThreatAlertMessage message,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String customerId = message.getCustomerId();
        String attackMac = message.getAttackMac();
        
        log.info("Received threat alert: customerId={}, attackMac={}, partition={}, offset={}", 
                 customerId, attackMac, partition, offset);
        
        try {
            // 转换为聚合数据
            AggregatedAttackData data = AggregatedAttackData.builder()
                .customerId(message.getCustomerId())
                .attackMac(message.getAttackMac())
                .attackIp(message.getAttackIp())
                .attackCount(message.getAttackCount())
                .uniqueIps(message.getUniqueIps())
                .uniquePorts(message.getUniquePorts())
                .uniqueDevices(message.getUniqueDevices())
                .timestamp(message.getTimestamp())
                .portList(parsePortList(message.getPortList()))
                .build();
            
            // 执行威胁评估
            ThreatAssessment assessment = assessmentService.assessThreat(data);
            
            log.info("Threat assessed: id={}, customerId={}, attackMac={}, score={}, level={}", 
                     assessment.getId(), customerId, attackMac, 
                     assessment.getThreatScore(), assessment.getThreatLevel());
                     
        } catch (Exception e) {
            log.error("Failed to process threat alert: customerId={}, attackMac={}", 
                      customerId, attackMac, e);
            // 异常会被Kafka重试机制处理
            throw e;
        }
    }
    
    private List<Integer> parsePortList(String portList) {
        if (portList == null || portList.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(portList.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .collect(Collectors.toList());
    }
}
```

#### 3.4 创建数据模型

**文件**: `dto/AggregatedAttackData.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedAttackData {
    private String customerId;
    private String attackMac;
    private String attackIp;
    private Integer attackCount;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    private Instant timestamp;
    private List<Integer> portList;
}
```

**文件**: `dto/ThreatAlertMessage.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAlertMessage {
    private String customerId;
    private String attackMac;
    private String attackIp;
    private Integer attackCount;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    private Instant timestamp;
    private String portList;  // 逗号分隔的端口号
}
```

---

### Phase 2: 端口风险配置 (优先级: 🟡 高)

#### 3.5 创建端口风险配置表

**数据库表**: `port_risk_config`

```sql
CREATE TABLE port_risk_config (
    id SERIAL PRIMARY KEY,
    port INTEGER NOT NULL UNIQUE,
    port_name VARCHAR(100) NOT NULL,
    risk_score DECIMAL(5,2) NOT NULL,
    category VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_port ON port_risk_config(port);
CREATE INDEX idx_risk_score ON port_risk_config(risk_score);

-- 示例数据 (部分)
INSERT INTO port_risk_config (port, port_name, risk_score, category, description) VALUES
(3389, 'RDP', 95.0, 'Remote Access', 'Remote Desktop Protocol - 高危远程访问'),
(445, 'SMB', 90.0, 'File Sharing', 'Server Message Block - 勒索软件常见目标'),
(22, 'SSH', 85.0, 'Remote Access', 'Secure Shell - 暴力破解常见目标'),
(1433, 'MSSQL', 88.0, 'Database', 'Microsoft SQL Server'),
(3306, 'MySQL', 87.0, 'Database', 'MySQL Database'),
-- ... 继续添加219个端口配置
```

#### 3.6 创建 PortRiskService

**文件**: `service/PortRiskService.java`

```java
@Service
@Slf4j
public class PortRiskService {
    
    private final PortRiskConfigRepository repository;
    private final Map<Integer, PortRiskConfig> portRiskCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadPortRiskConfig() {
        log.info("Loading port risk configuration...");
        List<PortRiskConfig> configs = repository.findAll();
        configs.forEach(config -> portRiskCache.put(config.getPort(), config));
        log.info("Loaded {} port risk configurations", configs.size());
    }
    
    /**
     * 计算端口风险分数
     */
    public double calculatePortRiskScore(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) {
            return 0.0;
        }
        
        double totalRisk = ports.stream()
            .map(port -> portRiskCache.getOrDefault(port, getDefaultConfig()).getRiskScore())
            .reduce(0.0, Double::sum);
        
        return totalRisk / ports.size();  // 平均风险分数
    }
    
    /**
     * 获取高危端口列表
     */
    public List<Integer> getHighRiskPorts(List<Integer> ports) {
        return ports.stream()
            .filter(port -> {
                PortRiskConfig config = portRiskCache.get(port);
                return config != null && config.getRiskScore() >= 80.0;
            })
            .collect(Collectors.toList());
    }
    
    private PortRiskConfig getDefaultConfig() {
        return PortRiskConfig.builder()
            .port(0)
            .portName("Unknown")
            .riskScore(50.0)
            .build();
    }
}
```

---

### Phase 3: 网段权重配置 (优先级: 🟡 高)

#### 3.7 创建网段权重配置表

**数据库表**: `ip_segment_weight_config`

```sql
CREATE TABLE ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    segment_cidr VARCHAR(50) NOT NULL UNIQUE,
    weight DECIMAL(5,2) NOT NULL,
    category VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_segment_cidr ON ip_segment_weight_config(segment_cidr);

-- 示例数据
INSERT INTO ip_segment_weight_config (segment_cidr, weight, category, description) VALUES
('10.0.0.0/8', 1.0, 'Private', '内网A类地址'),
('172.16.0.0/12', 1.0, 'Private', '内网B类地址'),
('192.168.0.0/16', 1.0, 'Private', '内网C类地址'),
('0.0.0.0/8', 1.5, 'Reserved', '保留地址段'),
('127.0.0.0/8', 0.5, 'Loopback', '本地回环地址'),
-- ... 继续添加186个网段配置
```

#### 3.8 创建 IpSegmentWeightService

**文件**: `service/IpSegmentWeightService.java`

```java
@Service
@Slf4j
public class IpSegmentWeightService {
    
    private final IpSegmentWeightConfigRepository repository;
    private final List<IpSegmentWeight> segmentWeights = new ArrayList<>();
    
    @PostConstruct
    public void loadSegmentWeights() {
        log.info("Loading IP segment weight configuration...");
        List<IpSegmentWeightConfig> configs = repository.findAll();
        
        configs.forEach(config -> {
            try {
                SubnetUtils utils = new SubnetUtils(config.getSegmentCidr());
                segmentWeights.add(new IpSegmentWeight(
                    utils.getInfo(),
                    config.getWeight()
                ));
            } catch (Exception e) {
                log.error("Invalid CIDR: {}", config.getSegmentCidr(), e);
            }
        });
        
        log.info("Loaded {} IP segment weight configurations", segmentWeights.size());
    }
    
    /**
     * 计算IP权重
     */
    public double calculateIpWeight(String ip) {
        if (ip == null || ip.isEmpty() || "N/A".equals(ip)) {
            return 1.0;
        }
        
        for (IpSegmentWeight segment : segmentWeights) {
            if (segment.getInfo().isInRange(ip)) {
                return segment.getWeight();
            }
        }
        
        return 1.0;  // 默认权重
    }
    
    @Data
    @AllArgsConstructor
    private static class IpSegmentWeight {
        private SubnetUtils.SubnetInfo info;
        private double weight;
    }
}
```

---

### Phase 4: 标签/白名单系统 (优先级: 🟢 中)

#### 3.9 创建标签管理表

**数据库表**: `threat_labels`

```sql
CREATE TABLE threat_labels (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    label_type VARCHAR(20) NOT NULL,  -- 'WHITELIST', 'BLACKLIST', 'WATCH'
    target_type VARCHAR(20) NOT NULL, -- 'MAC', 'IP', 'SEGMENT'
    target_value VARCHAR(100) NOT NULL,
    reason TEXT,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    UNIQUE (customer_id, target_type, target_value)
);

-- 索引
CREATE INDEX idx_customer_label ON threat_labels(customer_id, label_type);
CREATE INDEX idx_target ON threat_labels(target_type, target_value);
```

#### 3.10 创建 ThreatLabelService

**文件**: `service/ThreatLabelService.java`

```java
@Service
@Slf4j
public class ThreatLabelService {
    
    private final ThreatLabelRepository repository;
    
    /**
     * 检查是否在白名单中
     */
    public boolean isWhitelisted(String customerId, String attackMac) {
        return repository.existsByCustomerIdAndLabelTypeAndTargetValue(
            customerId, LabelType.WHITELIST, attackMac
        );
    }
    
    /**
     * 添加白名单
     */
    public ThreatLabel addWhitelist(String customerId, String targetValue, String reason) {
        ThreatLabel label = ThreatLabel.builder()
            .customerId(customerId)
            .labelType(LabelType.WHITELIST)
            .targetType(TargetType.MAC)
            .targetValue(targetValue)
            .reason(reason)
            .createdAt(Instant.now())
            .build();
        
        return repository.save(label);
    }
}
```

---

### Phase 5: 测试完善 (优先级: 🟢 中)

#### 3.11 单元测试

**文件**: `test/service/ThreatScoreCalculatorTest.java`

```java
@SpringBootTest
class ThreatScoreCalculatorTest {
    
    @Autowired
    private ThreatScoreCalculator calculator;
    
    @Test
    void testTimeWeightCalculation() {
        // 深夜时段 (0-6)
        Instant midnight = Instant.parse("2025-01-15T02:00:00Z");
        assertEquals(1.2, calculator.calculateTimeWeight(midnight), 0.01);
        
        // 工作时段 (9-17)
        Instant workHour = Instant.parse("2025-01-15T10:00:00Z");
        assertEquals(1.0, calculator.calculateTimeWeight(workHour), 0.01);
    }
    
    @Test
    void testIpWeightCalculation() {
        assertEquals(1.0, calculator.calculateIpWeight(1), 0.01);
        assertEquals(1.3, calculator.calculateIpWeight(2), 0.01);
        assertEquals(1.5, calculator.calculateIpWeight(5), 0.01);
        assertEquals(2.0, calculator.calculateIpWeight(15), 0.01);
    }
    
    @Test
    void testThreatScoreCalculation() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .customerId("test-customer")
            .attackMac("00:11:22:33:44:55")
            .attackCount(100)
            .uniqueIps(5)
            .uniquePorts(3)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2025-01-15T02:00:00Z"))  // 深夜
            .build();
        
        double score = calculator.calculateThreatScore(data);
        
        // 预期: (100 × 5 × 3) × 1.2 × 1.5 × 1.2 × 1.0 = 1500 × 2.16 = 3240
        assertEquals(3240, score, 100);
    }
}
```

#### 3.12 集成测试

**文件**: `test/integration/ThreatAssessmentIntegrationTest.java`

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"threat-alerts"})
class ThreatAssessmentIntegrationTest {
    
    @Autowired
    private KafkaTemplate<String, ThreatAlertMessage> kafkaTemplate;
    
    @Autowired
    private ThreatAssessmentRepository repository;
    
    @Test
    void testEndToEndThreatAssessment() throws Exception {
        // 1. 发送威胁告警消息
        ThreatAlertMessage message = ThreatAlertMessage.builder()
            .customerId("test-customer")
            .attackMac("00:11:22:33:44:55")
            .attackIp("192.168.1.100")
            .attackCount(100)
            .uniqueIps(5)
            .uniquePorts(3)
            .uniqueDevices(1)
            .timestamp(Instant.now())
            .portList("3389,445,22")
            .build();
        
        kafkaTemplate.send("threat-alerts", "test-customer", message).get();
        
        // 2. 等待处理
        Thread.sleep(5000);
        
        // 3. 验证结果
        List<ThreatAssessment> assessments = repository.findByCustomerIdAndAttackMac(
            "test-customer", "00:11:22:33:44:55"
        );
        
        assertFalse(assessments.isEmpty());
        ThreatAssessment assessment = assessments.get(0);
        assertTrue(assessment.getThreatScore() > 0);
        assertNotNull(assessment.getThreatLevel());
    }
}
```

---

## 四、实施时间表

| 阶段 | 任务 | 优先级 | 预计时间 | 状态 |
|------|------|--------|---------|------|
| **Phase 1** | 核心评分引擎重构 | 🔴 最高 | 4小时 | ⏳ 待开始 |
| 1.1 | ThreatScoreCalculator | 🔴 | 1小时 | ⏳ |
| 1.2 | ThreatAssessmentService | 🔴 | 1小时 | ⏳ |
| 1.3 | ThreatAlertConsumer | 🔴 | 1小时 | ⏳ |
| 1.4 | 数据模型和配置 | 🔴 | 1小时 | ⏳ |
| **Phase 2** | 端口风险配置 | 🟡 高 | 3小时 | ⏳ 待开始 |
| 2.1 | 数据库表和配置 | 🟡 | 1小时 | ⏳ |
| 2.2 | PortRiskService | 🟡 | 1小时 | ⏳ |
| 2.3 | 集成到评分引擎 | 🟡 | 1小时 | ⏳ |
| **Phase 3** | 网段权重配置 | 🟡 高 | 3小时 | ⏳ 待开始 |
| 3.1 | 数据库表和配置 | 🟡 | 1小时 | ⏳ |
| 3.2 | IpSegmentWeightService | 🟡 | 1小时 | ⏳ |
| 3.3 | 集成到评分引擎 | 🟡 | 1小时 | ⏳ |
| **Phase 4** | 标签/白名单系统 | 🟢 中 | 2小时 | ⏳ 计划中 |
| **Phase 5** | 测试完善 | 🟢 中 | 2小时 | ⏳ 计划中 |

**总计**: 约14小时

---

## 五、成功标准

### 5.1 功能标准

- ✅ Kafka消费者正常工作，能从threat-alerts主题消费消息
- ✅ 威胁评分计算准确，符合算法规范
- ✅ 威胁等级判定正确 (5级分类)
- ✅ 数据正确写入PostgreSQL
- ✅ 多租户隔离正常工作
- ✅ 端口风险配置生效
- ✅ 网段权重配置生效
- ✅ 白名单功能正常

### 5.2 性能标准

- ✅ 吞吐量 >= 1000 assessments/s
- ✅ 端到端延迟 < 4分钟
- ✅ API响应时间 < 1秒
- ✅ CPU使用率 < 80%
- ✅ 内存使用率 < 2GB

### 5.3 质量标准

- ✅ 单元测试覆盖率 >= 80%
- ✅ 集成测试通过率 100%
- ✅ 无严重bug
- ✅ 日志完整清晰
- ✅ 文档完善

---

## 六、风险和缓解措施

### 6.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Kafka消费者性能不足 | 高 | 中 | 增加分区数，优化批处理 |
| 评分算法不准确 | 高 | 低 | 充分测试，与原系统对比 |
| 数据库写入瓶颈 | 中 | 中 | 批量写入，连接池优化 |
| 内存溢出 | 高 | 低 | 增加堆内存，优化缓存 |

### 6.2 业务风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 误报率过高 | 高 | 中 | 优化阈值，增加白名单 |
| 漏报风险 | 高 | 低 | 降低阈值，增强监控 |
| 数据不一致 | 中 | 低 | 事务保证，定期校验 |

---

## 七、下一步行动

### 立即开始 (Phase 1)

1. **创建 ThreatScoreCalculator**
   - 实现5个权重计算方法
   - 实现威胁分数计算
   - 实现威胁等级判定

2. **创建 ThreatAssessmentService**
   - 实现assessThreat方法
   - 添加熔断器
   - 集成RecommendationEngine

3. **创建 ThreatAlertConsumer**
   - 实现Kafka监听器
   - 添加错误处理
   - 添加日志记录

4. **创建数据模型**
   - AggregatedAttackData
   - ThreatAlertMessage

5. **单元测试**
   - 测试所有权重计算
   - 测试评分计算
   - 测试等级判定

---

**文档结束**

*请确认此计划是否符合预期，然后我们开始Phase 1的实施。*
