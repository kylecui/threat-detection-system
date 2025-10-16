# 云原生威胁检测系统 - 完整实施路线图 (第3部分)

**接续**: 测试策略、风险管理、团队配置、CI/CD流程

---

## 📋 测试策略

### 1. 测试金字塔

```
           /\
          /  \  E2E测试 (5%)
         /    \  - 完整业务流程
        /------\  - 端到端验证
       /        \ 
      /  集成测试  \ (25%)
     /   - 服务间交互 \
    /    - Kafka/DB集成 \
   /----------------------\
  /       单元测试        \ (70%)
 /  - 业务逻辑             \
/   - 算法准确性            \
/_____________________________\
```

### 2. 单元测试策略

#### 2.1 Flink算法测试

```java
// services/stream-processing/src/test/java/com/threatdetection/stream/ThreatScoreCalculatorTest.java

@ExtendWith(MockitoExtension.class)
class ThreatScoreCalculatorTest {
    
    private ThreatScoreCalculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new ThreatScoreCalculator();
    }
    
    @Test
    @DisplayName("时间权重计算 - 深夜时段应返回1.2")
    void testTimeWeight_MidnightShouldReturn1Point2() {
        // Given
        Instant midnight = Instant.parse("2025-10-11T02:00:00Z");
        
        // When
        double weight = calculator.calculateTimeWeight(midnight);
        
        // Then
        assertEquals(1.2, weight, 0.01, "深夜时段权重应为1.2");
    }
    
    @Test
    @DisplayName("时间权重计算 - 工作时间应返回1.0")
    void testTimeWeight_WorkHoursShouldReturn1Point0() {
        // Given
        Instant workHours = Instant.parse("2025-10-11T10:00:00Z");
        
        // When
        double weight = calculator.calculateTimeWeight(workHours);
        
        // Then
        assertEquals(1.0, weight, 0.01, "工作时间权重应为1.0");
    }
    
    @Test
    @DisplayName("IP权重计算 - 10个IP应返回2.0")
    void testIpWeight_TenIpsShouldReturn2Point0() {
        // When
        double weight = calculator.calculateIpWeight(10);
        
        // Then
        assertEquals(2.0, weight, 0.01, "10个IP权重应为2.0");
    }
    
    @Test
    @DisplayName("端口权重计算 - 20+端口应返回2.0")
    void testPortWeight_MoreThan20PortsShouldReturn2Point0() {
        // When
        double weight = calculator.calculatePortWeight(25);
        
        // Then
        assertEquals(2.0, weight, 0.01, "20+端口权重应为2.0");
    }
    
    @Test
    @DisplayName("威胁评分计算 - 勒索软件场景")
    void testThreatScore_RansomwareScenario() {
        // Given - 模拟勒索软件攻击数据
        AggregatedAttackData data = AggregatedAttackData.builder()
            .attackCount(500)        // 高频攻击
            .uniqueIps(50)           // 大范围扫描
            .uniquePorts(10)         // 多端口
            .uniqueDevices(2)        // 多设备检测
            .timestamp(Instant.parse("2025-10-11T02:00:00Z"))  // 深夜
            .build();
        
        // When
        double score = calculator.calculate(data);
        
        // Then
        // 预期: (500 × 50 × 10) × 1.2 (时间) × 2.0 (IP) × 1.6 (端口) × 1.5 (设备)
        // = 250,000 × 5.76 = 1,440,000
        assertTrue(score > 1_000_000, 
                   "勒索软件场景评分应 > 1,000,000, 实际: " + score);
        assertTrue(score < 2_000_000, 
                   "勒索软件场景评分应 < 2,000,000, 实际: " + score);
    }
    
    @Test
    @DisplayName("威胁评分计算 - 正常扫描场景")
    void testThreatScore_NormalScanScenario() {
        // Given - 模拟正常安全扫描
        AggregatedAttackData data = AggregatedAttackData.builder()
            .attackCount(50)
            .uniqueIps(3)
            .uniquePorts(10)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2025-10-11T14:00:00Z"))  // 白天
            .build();
        
        // When
        double score = calculator.calculate(data);
        
        // Then
        // 预期: (50 × 3 × 10) × 1.0 × 1.3 × 1.6 × 1.0 = 3,120
        assertTrue(score < 5_000, 
                   "正常扫描评分应 < 5,000, 实际: " + score);
    }
    
    @Test
    @DisplayName("威胁等级划分 - CRITICAL")
    void testThreatLevel_CriticalThreshold() {
        // Given
        double criticalScore = 250.0;
        
        // When
        String level = calculator.getThreatLevel(criticalScore);
        
        // Then
        assertEquals("CRITICAL", level, "评分250应为CRITICAL等级");
    }
    
    @Test
    @DisplayName("威胁等级划分 - 边界测试")
    void testThreatLevel_BoundaryTests() {
        assertAll("威胁等级边界测试",
            () -> assertEquals("INFO", calculator.getThreatLevel(5.0)),
            () -> assertEquals("LOW", calculator.getThreatLevel(10.0)),
            () -> assertEquals("MEDIUM", calculator.getThreatLevel(50.0)),
            () -> assertEquals("HIGH", calculator.getThreatLevel(100.0)),
            () -> assertEquals("CRITICAL", calculator.getThreatLevel(200.0))
        );
    }
}
```

**覆盖率目标**:
- 行覆盖率: > 80%
- 分支覆盖率: > 75%
- 方法覆盖率: > 90%

---

#### 2.2 ML模型测试

```python
# ml-service/tests/test_model.py
"""
ML模型单元测试
"""
import pytest
import numpy as np
from model_training import FalsePositiveClassifier, ThreatFeatureExtractor

class TestFeatureExtractor:
    
    @pytest.fixture
    def extractor(self):
        return ThreatFeatureExtractor()
    
    def test_entropy_calculation_uniform(self, extractor):
        """测试熵计算 - 均匀分布"""
        items = [1, 2, 3, 4, 5]  # 均匀分布
        entropy = extractor._calculate_entropy(items)
        
        # 均匀分布熵值应接近 log2(5) = 2.32
        assert 2.0 < entropy < 2.5
    
    def test_entropy_calculation_concentrated(self, extractor):
        """测试熵计算 - 集中分布"""
        items = [1, 1, 1, 1, 2]  # 高度集中
        entropy = extractor._calculate_entropy(items)
        
        # 集中分布熵值应较低
        assert entropy < 1.0
    
    def test_feature_extraction_windows_update(self, extractor):
        """测试Windows Update特征"""
        # 模拟Windows Update行为
        alert_data = {
            'unique_ports': 1,
            'unique_ips': 3,
            'attack_count': 5,
            'response_ports': [7680, 7680, 7680],  # Windows Update端口
            'response_ips': ['10.0.0.1', '10.0.0.2', '10.0.0.3'],
            'timestamp': pd.Timestamp('2025-10-11 10:00:00'),
            'unique_devices': 1
        }
        
        features = extractor.extract_features(alert_data)
        
        # 验证特征
        assert features['unique_ports'] == 1
        assert features['port_diversity_entropy'] < 0.5  # 低熵
        assert features['has_high_risk_ports'] == 0      # 无高危端口
    
    def test_feature_extraction_ransomware(self, extractor):
        """测试勒索软件特征"""
        alert_data = {
            'unique_ports': 5,
            'unique_ips': 50,
            'attack_count': 500,
            'response_ports': [445, 3389, 22, 1433, 3306],
            'response_ips': [f'10.0.0.{i}' for i in range(50)],
            'timestamp': pd.Timestamp('2025-10-11 02:00:00'),  # 深夜
            'unique_devices': 2
        }
        
        features = extractor.extract_features(alert_data)
        
        assert features['unique_ports'] == 5
        assert features['has_high_risk_ports'] == 1      # 包含445
        assert features['is_night'] == 1                 # 深夜
        assert features['port_diversity_entropy'] > 1.5  # 高熵

class TestFalsePositiveClassifier:
    
    @pytest.fixture
    def classifier(self):
        clf = FalsePositiveClassifier()
        clf.load_model('models')
        return clf
    
    def test_predict_windows_update_as_false_positive(self, classifier):
        """测试Windows Update应被识别为误报"""
        alert_data = {
            'unique_ports': 1,
            'unique_ips': 3,
            'attack_count': 5,
            'response_ports': [7680],
            'timestamp': pd.Timestamp('2025-10-11 10:00:00'),
        }
        
        result = classifier.predict(alert_data)
        
        assert result['is_false_positive'] == True
        assert result['confidence'] > 0.85
    
    def test_predict_ransomware_as_true_threat(self, classifier):
        """测试勒索软件应被识别为真实威胁"""
        alert_data = {
            'unique_ports': 5,
            'unique_ips': 50,
            'attack_count': 500,
            'response_ports': [445, 3389, 22],
            'timestamp': pd.Timestamp('2025-10-11 02:00:00'),
        }
        
        result = classifier.predict(alert_data)
        
        assert result['is_false_positive'] == False
        assert result['confidence'] > 0.90
    
    def test_model_performance_metrics(self, classifier):
        """测试模型性能指标"""
        # 加载测试集
        test_data = load_test_dataset()
        
        predictions = []
        for alert in test_data:
            result = classifier.predict(alert)
            predictions.append(result['is_false_positive'])
        
        # 计算指标
        accuracy = calculate_accuracy(predictions, test_data['labels'])
        
        assert accuracy > 0.95, f"模型准确率应 > 95%, 实际: {accuracy:.2%}"
```

---

### 3. 集成测试策略

#### 3.1 Kafka集成测试

```java
// services/data-ingestion/src/test/java/com/threatdetection/ingestion/KafkaIntegrationTest.java

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"attack-events", "status-events"}
)
class KafkaIntegrationTest {
    
    @Autowired
    private KafkaTemplate<String, AttackEvent> kafkaTemplate;
    
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    
    @Test
    @DisplayName("集成测试: 日志摄取 → Kafka发布 → 消费验证")
    void testEndToEndLogIngestionToKafka() throws Exception {
        // Given - 模拟syslog日志
        String syslogMessage = "Oct 11 10:00:00 dev_001 honeypot: " +
            "attack_mac=00:11:22:33:44:55 " +
            "attack_ip=192.168.1.100 " +
            "response_ip=10.0.0.1 " +
            "response_port=3306 " +
            "log_time=1728640800";
        
        // When - 发送到摄取服务
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:8080/api/v1/ingest/syslog",
            syslogMessage,
            String.class
        );
        
        // Then - 验证Kafka消息
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // 消费Kafka消息
        ConsumerRecord<String, AttackEvent> record = 
            KafkaTestUtils.getSingleRecord(consumer, "attack-events", 5000);
        
        assertNotNull(record);
        assertEquals("00:11:22:33:44:55", record.value().getAttackMac());
        assertEquals(3306, record.value().getResponsePort());
    }
    
    @Test
    @DisplayName("集成测试: 批量日志处理")
    void testBatchLogProcessing() throws Exception {
        // Given - 100条日志
        List<String> logs = generateTestLogs(100);
        
        // When
        for (String log : logs) {
            restTemplate.postForEntity(
                "http://localhost:8080/api/v1/ingest/syslog",
                log,
                String.class
            );
        }
        
        // Then - 验证所有消息都发布到Kafka
        List<ConsumerRecord<String, AttackEvent>> records = 
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        
        assertEquals(100, records.size(), "应收到100条Kafka消息");
    }
}
```

#### 3.2 PostgreSQL集成测试

```java
@SpringBootTest
@Testcontainers
class PostgreSQLIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("threat_detection_test")
        .withUsername("test_user")
        .withPassword("test_pass");
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private ThreatAssessmentRepository repository;
    
    @Test
    @DisplayName("集成测试: 威胁评估数据持久化")
    void testThreatAssessmentPersistence() {
        // Given
        ThreatAssessment assessment = ThreatAssessment.builder()
            .customerId("test-customer")
            .attackMac("00:11:22:33:44:55")
            .threatScore(125.5)
            .threatLevel("HIGH")
            .attackCount(150)
            .uniqueIps(5)
            .uniquePorts(3)
            .build();
        
        // When
        ThreatAssessment saved = repository.save(assessment);
        
        // Then
        assertNotNull(saved.getId());
        
        Optional<ThreatAssessment> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(125.5, found.get().getThreatScore(), 0.01);
    }
    
    @Test
    @DisplayName("集成测试: 按客户ID查询威胁")
    void testFindByCustomerId() {
        // Given - 插入5条记录
        for (int i = 0; i < 5; i++) {
            repository.save(ThreatAssessment.builder()
                .customerId("test-customer")
                .attackMac("00:11:22:33:44:5" + i)
                .threatScore(100.0 + i * 10)
                .build());
        }
        
        // When
        List<ThreatAssessment> results = 
            repository.findByCustomerIdOrderByThreatScoreDesc("test-customer");
        
        // Then
        assertEquals(5, results.size());
        assertEquals(140.0, results.get(0).getThreatScore(), 0.01);  // 最高分
    }
}
```

---

### 4. 端到端测试

```python
# scripts/test/e2e_test.py
"""
端到端测试: 完整业务流程验证
"""
import requests
import time
import psycopg2
from kafka import KafkaConsumer
import json

class E2ETest:
    
    def __init__(self):
        self.ingestion_url = "http://localhost:8080"
        self.assessment_url = "http://localhost:8083"
        self.db_conn = psycopg2.connect(
            host='localhost',
            database='threat_detection',
            user='threat_user',
            password='threat_password'
        )
    
    def test_full_pipeline(self):
        """
        测试完整数据管道:
        syslog → 摄取 → Kafka → Flink → 评估 → PostgreSQL → 告警
        """
        print("🧪 开始端到端测试...")
        
        # Step 1: 发送测试日志
        print("📤 Step 1: 发送测试日志")
        test_logs = self._generate_attack_logs(count=200)
        
        for log in test_logs:
            response = requests.post(
                f"{self.ingestion_url}/api/v1/ingest/syslog",
                data=log,
                headers={'Content-Type': 'text/plain'}
            )
            assert response.status_code == 200, f"日志摄取失败: {response.text}"
        
        print(f"   ✅ 成功发送 {len(test_logs)} 条日志")
        
        # Step 2: 验证Kafka消息
        print("📨 Step 2: 验证Kafka消息")
        kafka_consumer = KafkaConsumer(
            'attack-events',
            bootstrap_servers='localhost:9092',
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        kafka_count = 0
        for message in kafka_consumer:
            kafka_count += 1
        
        assert kafka_count >= 200, f"Kafka消息不足: 期望200, 实际{kafka_count}"
        print(f"   ✅ Kafka消息验证通过: {kafka_count}条")
        
        # Step 3: 等待Flink处理 (2分钟评分窗口 + 缓冲)
        print("⏳ Step 3: 等待Flink处理 (180秒)...")
        time.sleep(180)
        
        # Step 4: 验证威胁评估结果
        print("🔍 Step 4: 验证威胁评估结果")
        cursor = self.db_conn.cursor()
        cursor.execute("""
            SELECT COUNT(*) 
            FROM threat_assessments 
            WHERE customer_id = 'test-customer'
              AND created_at >= NOW() - INTERVAL '5 minutes'
        """)
        
        assessment_count = cursor.fetchone()[0]
        assert assessment_count > 0, "未找到威胁评估记录"
        print(f"   ✅ 威胁评估记录: {assessment_count}条")
        
        # Step 5: 验证高危告警
        cursor.execute("""
            SELECT attack_mac, threat_score, threat_level
            FROM threat_assessments
            WHERE customer_id = 'test-customer'
              AND threat_level IN ('HIGH', 'CRITICAL')
              AND created_at >= NOW() - INTERVAL '5 minutes'
            ORDER BY threat_score DESC
            LIMIT 1
        """)
        
        result = cursor.fetchone()
        if result:
            attack_mac, score, level = result
            print(f"   ✅ 检测到高危威胁: MAC={attack_mac}, 评分={score:.2f}, 等级={level}")
        
        # Step 6: 验证API查询
        print("🌐 Step 6: 验证API查询")
        api_response = requests.get(
            f"{self.assessment_url}/api/v1/assessment/customer/test-customer"
        )
        
        assert api_response.status_code == 200, "API查询失败"
        data = api_response.json()
        assert len(data) > 0, "API返回空结果"
        print(f"   ✅ API查询成功: 返回{len(data)}条记录")
        
        print("\n🎉 端到端测试全部通过!")
        
        cursor.close()
    
    def _generate_attack_logs(self, count=200):
        """生成测试攻击日志"""
        logs = []
        base_time = int(time.time())
        
        for i in range(count):
            log = (
                f"Oct 11 10:00:{i % 60:02d} dev_001 honeypot: "
                f"attack_mac=00:11:22:33:44:55 "
                f"attack_ip=192.168.1.100 "
                f"response_ip=10.0.0.{i % 50 + 1} "
                f"response_port={[445, 3389, 22, 3306, 1433][i % 5]} "
                f"log_time={base_time + i}"
            )
            logs.append(log)
        
        return logs

if __name__ == '__main__':
    test = E2ETest()
    test.test_full_pipeline()
```

---

### 5. 性能测试

#### 5.1 吞吐量测试

```python
# scripts/test/performance_test.py
"""
性能测试: 吞吐量和延迟
"""
import time
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
import statistics

class PerformanceTest:
    
    def test_throughput(self, target_rps=1000, duration_seconds=60):
        """
        吞吐量测试
        
        目标: 10000 events/s
        """
        print(f"🚀 吞吐量测试: 目标={target_rps} RPS, 持续={duration_seconds}秒")
        
        url = "http://localhost:8080/api/v1/ingest/syslog"
        
        total_requests = 0
        successful_requests = 0
        latencies = []
        
        start_time = time.time()
        end_time = start_time + duration_seconds
        
        with ThreadPoolExecutor(max_workers=50) as executor:
            while time.time() < end_time:
                batch_start = time.time()
                
                # 提交一批请求
                futures = []
                for _ in range(target_rps // 10):  # 每0.1秒提交一批
                    future = executor.submit(self._send_request, url)
                    futures.append(future)
                
                # 等待完成
                for future in as_completed(futures):
                    total_requests += 1
                    success, latency = future.result()
                    
                    if success:
                        successful_requests += 1
                        latencies.append(latency)
                
                # 控制速率
                elapsed = time.time() - batch_start
                sleep_time = 0.1 - elapsed
                if sleep_time > 0:
                    time.sleep(sleep_time)
        
        actual_duration = time.time() - start_time
        actual_rps = total_requests / actual_duration
        success_rate = successful_requests / total_requests * 100
        
        print(f"\n📊 测试结果:")
        print(f"   总请求数: {total_requests}")
        print(f"   成功请求: {successful_requests}")
        print(f"   实际RPS: {actual_rps:.2f}")
        print(f"   成功率: {success_rate:.2f}%")
        print(f"   平均延迟: {statistics.mean(latencies):.2f}ms")
        print(f"   P50延迟: {statistics.median(latencies):.2f}ms")
        print(f"   P95延迟: {self._percentile(latencies, 95):.2f}ms")
        print(f"   P99延迟: {self._percentile(latencies, 99):.2f}ms")
        
        # 验证性能目标
        assert actual_rps >= target_rps * 0.9, \
            f"RPS未达标: 期望{target_rps}, 实际{actual_rps:.2f}"
        assert success_rate >= 99.0, \
            f"成功率未达标: 期望99%, 实际{success_rate:.2f}%"
    
    def _send_request(self, url):
        """发送单个请求并测量延迟"""
        log = self._generate_log()
        
        start = time.time()
        try:
            response = requests.post(
                url,
                data=log,
                headers={'Content-Type': 'text/plain'},
                timeout=5
            )
            latency_ms = (time.time() - start) * 1000
            success = response.status_code == 200
            return success, latency_ms
        except Exception as e:
            latency_ms = (time.time() - start) * 1000
            return False, latency_ms
    
    def _generate_log(self):
        """生成随机测试日志"""
        import random
        return (
            f"Oct 11 10:00:00 dev_001 honeypot: "
            f"attack_mac=00:11:22:33:44:55 "
            f"attack_ip=192.168.1.{random.randint(1, 254)} "
            f"response_ip=10.0.0.{random.randint(1, 254)} "
            f"response_port={random.choice([445, 3389, 22, 3306, 1433])} "
            f"log_time={int(time.time())}"
        )
    
    def _percentile(self, data, percentile):
        """计算百分位数"""
        sorted_data = sorted(data)
        index = int(len(sorted_data) * percentile / 100)
        return sorted_data[min(index, len(sorted_data)-1)]

if __name__ == '__main__':
    test = PerformanceTest()
    test.test_throughput(target_rps=1000, duration_seconds=60)
```

**性能基准**:

| 指标 | 目标 | 实际 | 状态 |
|------|------|------|------|
| 摄取吞吐量 | 10000 events/s | TBD | ⏳ |
| P50延迟 | < 50ms | TBD | ⏳ |
| P95延迟 | < 200ms | TBD | ⏳ |
| P99延迟 | < 500ms | TBD | ⏳ |
| 端到端延迟 | < 4分钟 | TBD | ⏳ |

---

## 🚨 风险管理

### 1. 风险识别矩阵

| 风险 | 可能性 | 影响 | 优先级 | 缓解措施 |
|------|--------|------|--------|---------|
| **ML模型过拟合** | 中 | 高 | **P0** | ✅ 交叉验证, A/B测试, 持续监控误报率 |
| **生产切换失败** | 低 | 极高 | **P0** | ✅ 回滚预案, 灰度发布, 双系统并行 |
| **性能瓶颈** | 中 | 高 | **P1** | ✅ 提前压测, 资源预留, 水平扩展 |
| **数据质量问题** | 中 | 中 | **P1** | ✅ 数据验证, 异常监控 |
| **团队人员流失** | 低 | 高 | **P2** | ✅ 知识文档化, 结对编程 |
| **第三方API限流** | 中 | 低 | **P2** | ✅ 缓存策略, 降级方案 |

### 2. 风险应对计划

#### 风险1: ML模型过拟合

**症状**:
- 训练集准确率 > 99%, 测试集准确率 < 90%
- 生产环境误报率突然上升

**监控**:
```python
# ml-service/monitoring.py
def monitor_model_drift():
    """监控模型性能漂移"""
    # 每天计算生产数据的准确率
    production_accuracy = calculate_daily_accuracy()
    
    if production_accuracy < 0.90:
        alert("🚨 模型性能下降: 准确率 = {:.2%}".format(production_accuracy))
        trigger_retraining()
```

**缓解措施**:
1. ✅ 交叉验证 (5折)
2. ✅ 早停策略 (Early Stopping)
3. ✅ 正则化 (L1/L2)
4. ✅ A/B测试 (新模型vs旧模型)
5. ✅ 每周监控性能指标

---

#### 风险2: 生产切换失败

**应急预案**:

```bash
#!/bin/bash
# scripts/emergency_rollback.sh
# 紧急回滚脚本

echo "🚨 执行紧急回滚..."

# Step 1: 停止新系统接收数据
kubectl scale deployment data-ingestion --replicas=0 -n threat-detection-prod
echo "✅ 新系统已停止"

# Step 2: 恢复rsyslog到旧系统
ssh old-system-server << 'EOF'
sudo sed -i 's/^#OLD_SYSTEM//' /etc/rsyslog.d/threat-detection.conf
sudo sed -i 's/^NEW_SYSTEM/#NEW_SYSTEM/' /etc/rsyslog.d/threat-detection.conf
sudo systemctl restart rsyslog
EOF
echo "✅ rsyslog已恢复到旧系统"

# Step 3: 通知团队
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -H 'Content-Type: application/json' \
  -d '{"text":"🚨 紧急回滚已执行,旧系统已恢复"}'

echo "✅ 回滚完成,旧系统已恢复运行"
```

---

### 3. 变更管理流程

```
变更请求 → 影响分析 → 批准 → 实施 → 验证 → 关闭
```

**变更分类**:

| 变更类型 | 审批级别 | 测试要求 | 回滚计划 |
|----------|----------|---------|---------|
| **低风险** (配置调整) | 团队Lead | 单元测试 | 可选 |
| **中风险** (功能更新) | 技术经理 | 集成测试 | 必需 |
| **高风险** (架构变更) | CTO | 完整测试+灰度 | 详细预案 |

---

## 👥 团队配置

### 1. 核心团队

| 角色 | FTE | 技能要求 | 主要职责 |
|------|-----|---------|---------|
| **项目经理** | 1.0 | 项目管理, 敏捷, 沟通 | 整体规划, 进度管理, 风险控制 |
| **架构师** | 0.5 | 系统设计, Kafka/Flink | 技术决策, 架构审查 |
| **Flink开发** | 2.0 | Java, Flink, 流处理 | 流处理逻辑, 算法实现 |
| **ML工程师** | 1.0 | Python, Scikit-learn, 特征工程 | ML模型训练, 部署, 优化 |
| **后端开发** | 1.0 | Java, Spring Boot, PostgreSQL | 微服务开发, API实现 |
| **DevOps工程师** | 0.5 | Docker, K8s, CI/CD | 部署自动化, 监控 |
| **测试工程师** | 1.0 | 自动化测试, 性能测试 | 测试策略, 测试执行 |
| **安全分析师** | 0.5 | 威胁情报, APT分析 | 需求验证, 标注数据 |
| **总计** | **7.5 FTE** | - | - |

### 2. 工作分工

**Phase 0 (Week 1-8)**:
- PM: 项目计划, 风险管理
- 架构师: 功能对齐验证
- Flink开发: 端口权重迁移
- 后端开发: API对接
- DevOps: 生产环境准备
- 测试: 对比测试

**Phase 1 (Week 9-16)**:
- Flink开发: 多窗口架构, 端口权重, 横向移动
- 后端开发: 权重配置API
- DevOps: 性能监控
- 测试: 集成测试

**Phase 2 (Week 17-24)**:
- ML工程师: 数据标注, 模型训练, 部署
- Flink开发: 端口序列, 行为基线
- 测试: ML模型测试

**Phase 3 (Week 25-32)**:
- Flink开发: APT状态机, 持续性评分
- ML工程师: 洪泛数据分析
- 安全分析师: APT模式验证
- 测试: 端到端验证

---

## 🔄 CI/CD流程

### 1. 代码提交流程

```
开发 → 本地测试 → Git提交 → Pull Request → Code Review
    → 自动化测试 → 合并到main → 自动部署到dev
    → 人工验证 → 部署到staging → 集成测试
    → 批准 → 部署到production
```

### 2. GitHub Actions配置

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      
      - name: Run tests
        run: mvn clean test
      
      - name: Generate coverage report
        run: mvn jacoco:report
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./target/site/jacoco/jacoco.xml
  
  build:
    needs: test
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker images
        run: |
          docker build -t threat-detection/data-ingestion:${{ github.sha }} \
            -f services/data-ingestion/Dockerfile .
          docker build -t threat-detection/stream-processing:${{ github.sha }} \
            -f services/stream-processing/Dockerfile .
      
      - name: Push to registry
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push threat-detection/data-ingestion:${{ github.sha }}
          docker push threat-detection/stream-processing:${{ github.sha }}
  
  deploy-dev:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    
    steps:
      - name: Deploy to dev environment
        run: |
          kubectl set image deployment/data-ingestion \
            data-ingestion=threat-detection/data-ingestion:${{ github.sha }} \
            -n threat-detection-dev
```

---

## 📈 监控与告警

### 1. 关键指标

```yaml
# infrastructure/monitoring/metrics.yml
metrics:
  # 业务指标
  - name: threat_detection_rate
    type: counter
    description: "检测到的威胁数量"
    labels: [customer_id, threat_level]
  
  - name: false_positive_rate
    type: gauge
    description: "误报率"
  
  - name: detection_latency_seconds
    type: histogram
    description: "检测延迟 (秒)"
    buckets: [10, 30, 60, 120, 300]
  
  # 系统指标
  - name: kafka_consumer_lag
    type: gauge
    description: "Kafka消费延迟"
  
  - name: flink_checkpoint_duration_ms
    type: histogram
    description: "Flink检查点时长"
```

### 2. Grafana仪表盘

```json
{
  "dashboard": {
    "title": "威胁检测系统监控",
    "panels": [
      {
        "title": "威胁检测数量 (24小时)",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(threat_detection_rate[24h])) by (threat_level)"
          }
        ]
      },
      {
        "title": "误报率趋势",
        "type": "graph",
        "targets": [
          {
            "expr": "false_positive_rate"
          }
        ],
        "alert": {
          "conditions": [
            {
              "evaluator": {
                "type": "gt",
                "params": [0.02]
              }
            }
          ]
        }
      },
      {
        "title": "检测延迟分布",
        "type": "heatmap",
        "targets": [
          {
            "expr": "detection_latency_seconds"
          }
        ]
      }
    ]
  }
}
```

---

**文档结束** (第3部分)

*完整实施路线图已全部完成*

---

## 📚 附录: 快速参考

### 关键文档索引

1. **IMPLEMENTATION_ROADMAP.md** - 总体路线图和Phase 0-1
2. **IMPLEMENTATION_ROADMAP_PART2.md** - Phase 2-3详细计划
3. **IMPLEMENTATION_ROADMAP_PART3.md** - 测试、风险、团队 (本文档)
4. **docs/analysis/** - 8个专项技术分析
5. **docs/COMPREHENSIVE_OPTIMIZATION_PLAN.md** - 综合优化方案

### 联系方式

- **项目经理**: [PM姓名]
- **技术负责人**: [Tech Lead姓名]
- **紧急联系**: [On-call手机]

---

*文档版本: 1.0*  
*最后更新: 2025-10-11*
