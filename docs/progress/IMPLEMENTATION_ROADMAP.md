# 云原生威胁检测系统 - 完整实施路线图

**文档版本**: 1.0  
**制定日期**: 2025-10-11  
**项目周期**: 8个月  
**文档类型**: 可执行实施方案

---

## 📋 执行摘要

本文档提供从**现有系统平稳替代**到**功能增强升级**的完整路线图。基于对8个专项技术分析的综合考虑，采用**渐进式演进策略**，确保:

- ✅ **零停机迁移**: 旧系统与新系统并行运行
- ✅ **功能对齐优先**: 先替代现有功能，再增强新能力
- ✅ **风险可控**: 每个阶段独立验证和回滚
- ✅ **持续交付价值**: 每2周交付可用功能

---

## 🎯 整体战略

### 战略原则

```
阶段0: 系统对齐与替代 (Month 1-2)
  目标: 新系统完全替代旧系统,功能对齐
  交付: 生产环境切换,旧系统下线

阶段1: 核心优化 (Month 3-4)
  目标: 提升检测准确性和实时性
  交付: 多窗口架构,科学端口权重

阶段2: 智能增强 (Month 5-6)
  目标: 引入机器学习和高级分析
  交付: ML误报过滤,端口序列识别

阶段3: 高级建模 (Month 7-8)
  目标: APT检测和持续性威胁追踪
  交付: 状态机模型,演化轨迹分析
```

### 实施策略对比

| 策略 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **Big Bang (一次性替换)** | 快速完成 | 风险极高,难回滚 | ❌ 不推荐 |
| **Strangler Fig (绞杀者)** | 风险低,可逐步迁移 | 需要维护双系统 | ⚠️ 复杂度高 |
| **渐进式演进 (推荐)** | 平滑过渡,持续交付 | 需要严格规划 | ✅ **强烈推荐** |

**选择: 渐进式演进**
- 阶段0完成系统替代
- 阶段1-3逐步增强功能
- 每个阶段独立验证

---

## 📅 详细时间表

### Phase 0: 系统对齐与生产替代 (Month 1-2, Week 1-8)

**目标**: 新系统完全替代旧C#系统,实现功能对齐

#### Week 1-2: 功能对齐验证

**任务清单**:

- [ ] **对比测试环境搭建**
  ```bash
  # 部署新系统 (Docker)
  cd docker && docker-compose up -d
  
  # 配置双写 (旧系统+新系统同时接收数据)
  # 修改rsyslog配置
  sudo vim /etc/rsyslog.d/threat-detection.conf
  ```
  
  ```conf
  # rsyslog双写配置
  # 旧系统 (C#/Windows)
  *.* @@old-system:514
  
  # 新系统 (Java/Linux)
  *.* @@new-system:9080
  ```

- [ ] **功能对齐检查表**

| 功能 | 旧系统 | 新系统 | 对齐状态 | 差异说明 |
|------|--------|--------|---------|---------|
| 日志解析 | ✅ C# Regex | ✅ Java Regex | ✅ 对齐 | - |
| 客户隔离 | ✅ company_obj_id | ✅ customerId | ✅ 对齐 | 字段名变化 |
| 时间权重 | ✅ 5时段 | ✅ 5时段 | ✅ 对齐 | 相同逻辑 |
| IP统计 | ✅ sum_ip | ✅ uniqueIps | ✅ 对齐 | - |
| 攻击计数 | ✅ count_attack | ✅ attackCount | ✅ 对齐 | - |
| 端口权重 | ✅ 经验表 | ⚠️ 简化算法 | ⚠️ 待对齐 | **Week 2修复** |
| 威胁分级 | ✅ 3级 | ✅ 5级 | ✅ 增强 | 向下兼容 |

- [ ] **端口权重对齐实现**
  
  **任务**: 将旧系统219个端口配置迁移到新系统
  
  ```sql
  -- 创建端口权重配置表 (PostgreSQL)
  CREATE TABLE port_weights_legacy (
      port INTEGER PRIMARY KEY,
      weight DECIMAL(3,2) NOT NULL,
      description VARCHAR(200),
      source VARCHAR(50) DEFAULT 'legacy_system',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  
  -- 导入旧系统端口配置
  INSERT INTO port_weights_legacy (port, weight, description) VALUES
  (445, 2.9, 'SMB - 高危'),
  (3389, 2.7, 'RDP - 高危'),
  (22, 2.4, 'SSH'),
  (3306, 2.5, 'MySQL'),
  (1433, 2.6, 'SQL Server'),
  -- ... 其余214个端口
  ;
  ```
  
  ```java
  // Flink作业更新 - 查询端口权重
  public class PortWeightLoader {
      private static Map<Integer, Double> portWeights = new HashMap<>();
      
      static {
          // 从数据库加载端口权重
          loadPortWeightsFromDB();
      }
      
      public static double getPortWeight(int port) {
          return portWeights.getOrDefault(port, 1.0);
      }
      
      private static void loadPortWeightsFromDB() {
          // JDBC查询PostgreSQL
          String sql = "SELECT port, weight FROM port_weights_legacy";
          // ... 加载逻辑
      }
  }
  ```

**交付物**:
- ✅ 端口权重配置表 (219条记录)
- ✅ Flink作业更新 (动态查询权重)
- ✅ 对比测试报告 (新旧系统评分差异 < 5%)

**验收标准**:
- 相同输入数据,新旧系统威胁评分误差 < 5%
- 告警触发一致性 > 95%

---

#### Week 3-4: 并行运行与数据对比

**任务清单**:

- [ ] **双系统并行运行**
  
  ```
  数据流:
  
  rsyslog → 双写
      ├─→ 旧系统 (C#) → MySQL → 旧告警系统
      └─→ 新系统 (Java) → Kafka → PostgreSQL → 新告警系统
  
  对比工具 → 每小时对比告警差异
  ```

- [ ] **自动化对比脚本**
  
  ```python
  # scripts/tools/system_comparison.py
  """
  自动对比新旧系统告警结果
  """
  import pymysql
  import psycopg2
  from datetime import datetime, timedelta
  
  def compare_alerts(time_window_hours=1):
      # 1. 连接旧系统MySQL
      old_conn = pymysql.connect(
          host='old-mysql-server',
          user='user',
          password='pass',
          database='threat_db'
      )
      
      # 2. 连接新系统PostgreSQL
      new_conn = psycopg2.connect(
          host='localhost',
          user='threat_user',
          password='threat_password',
          database='threat_detection'
      )
      
      # 3. 查询最近1小时的告警
      end_time = datetime.now()
      start_time = end_time - timedelta(hours=time_window_hours)
      
      old_alerts = fetch_old_alerts(old_conn, start_time, end_time)
      new_alerts = fetch_new_alerts(new_conn, start_time, end_time)
      
      # 4. 对比分析
      comparison = {
          'old_count': len(old_alerts),
          'new_count': len(new_alerts),
          'matched': 0,
          'only_old': [],
          'only_new': [],
          'score_diff': []
      }
      
      # ... 对比逻辑
      
      # 5. 生成报告
      generate_report(comparison)
      
      return comparison
  
  if __name__ == '__main__':
      # 每小时运行一次
      compare_alerts(time_window_hours=1)
  ```

- [ ] **差异分析与调优**
  
  **预期差异类型**:
  
  | 差异类型 | 原因 | 解决方案 |
  |----------|------|---------|
  | 评分偏差5-10% | 浮点数计算精度 | ✅ 可接受 |
  | 新系统漏报 | 端口权重缺失 | ⚠️ 补充权重配置 |
  | 新系统多报 | 时间窗口差异 | ⚠️ 调整窗口参数 |
  | 告警等级不一致 | 阈值差异 | ⚠️ 对齐阈值配置 |

**交付物**:
- ✅ 自动对比脚本 (每小时运行)
- ✅ 差异分析报告 (7天数据)
- ✅ 调优参数表

**验收标准**:
- 告警匹配率 > 95%
- 评分误差 < 5%
- 无重大漏报

---

#### Week 5-6: 生产环境准备

**任务清单**:

- [ ] **Kubernetes生产环境部署**
  
  ```bash
  # 1. 创建生产命名空间
  kubectl create namespace threat-detection-prod
  
  # 2. 配置生产环境变量
  kubectl create secret generic prod-secrets \
    --from-literal=db-password='PROD_PASSWORD' \
    --from-literal=kafka-password='KAFKA_PASSWORD' \
    -n threat-detection-prod
  
  # 3. 部署应用
  kubectl apply -k k8s/overlays/production
  
  # 4. 验证部署
  kubectl get pods -n threat-detection-prod
  kubectl get svc -n threat-detection-prod
  ```

- [ ] **生产环境配置优化**
  
  ```yaml
  # k8s/overlays/production/kustomization.yaml
  resources:
    - ../../base
  
  replicas:
    - name: data-ingestion
      count: 3  # 3副本高可用
    - name: stream-processing
      count: 2
    - name: threat-assessment
      count: 2
  
  patches:
    # 生产资源配置
    - target:
        kind: Deployment
        name: data-ingestion
      patch: |-
        - op: replace
          path: /spec/template/spec/containers/0/resources/limits/cpu
          value: "4000m"
        - op: replace
          path: /spec/template/spec/containers/0/resources/limits/memory
          value: "8Gi"
  ```

- [ ] **监控告警配置**
  
  ```yaml
  # infrastructure/monitoring/prometheus-rules.yml
  groups:
    - name: threat_detection_alerts
      rules:
        # 服务健康检查
        - alert: ServiceDown
          expr: up{job="threat-detection"} == 0
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "服务{{ $labels.instance }}宕机"
        
        # Kafka消费延迟
        - alert: KafkaConsumerLag
          expr: kafka_consumer_lag > 10000
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "Kafka消费延迟过高: {{ $value }}"
        
        # 威胁告警处理延迟
        - alert: ThreatProcessingDelay
          expr: threat_processing_latency_seconds > 300
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "威胁处理延迟超过5分钟"
  ```

**交付物**:
- ✅ 生产K8s配置 (3副本HA)
- ✅ Prometheus监控规则
- ✅ Grafana仪表盘
- ✅ 告警通知配置

**验收标准**:
- 所有服务健康检查通过
- 监控指标正常采集
- 告警规则测试通过

---

#### Week 7-8: 生产切换与旧系统下线

**切换计划**:

```
Day 1 (周一):
  00:00 - 启用新系统生产环境
  00:00 - 双写继续 (新旧系统并行)
  08:00 - 监控首个工作日数据
  18:00 - 第一次日报告 (无重大问题)

Day 2-3 (周二-周三):
  持续监控,收集反馈
  对比新旧系统告警质量

Day 4 (周四):
  如果一切正常:
    - 停止双写
    - 新系统作为主系统
    - 旧系统保持只读

Day 5-7 (周五-周日):
  新系统独立运行
  旧系统待命 (可回滚)

Week 2 (Day 8-14):
  新系统稳定运行
  准备下线旧系统

Day 15:
  ✅ 旧系统下线
  ✅ 数据归档
```

**回滚预案**:

```bash
# 如果新系统出现重大问题,立即回滚

# 1. 停止新系统接收数据
kubectl scale deployment data-ingestion --replicas=0 -n threat-detection-prod

# 2. 恢复旧系统为主系统
sudo vim /etc/rsyslog.d/threat-detection.conf
# 注释新系统行,保留旧系统

# 3. 重启rsyslog
sudo systemctl restart rsyslog

# 4. 通知团队和客户
```

**任务清单**:

- [ ] **切换前检查**
  - [ ] 生产环境3副本全部健康
  - [ ] 监控告警规则生效
  - [ ] 数据备份完成
  - [ ] 回滚预案演练完成
  - [ ] 团队值班安排

- [ ] **切换执行**
  - [ ] D-Day 00:00 启用生产
  - [ ] 实时监控第一个24小时
  - [ ] 每4小时发送状态报告
  - [ ] 第3天决策: 继续或回滚

- [ ] **旧系统下线**
  - [ ] 停止接收新数据
  - [ ] 数据导出和归档
  - [ ] 服务器资源回收

**交付物**:
- ✅ 切换执行checklist
- ✅ 监控报告 (7天)
- ✅ 旧系统归档数据
- ✅ Phase 0验收报告

**验收标准**:
- ✅ 新系统稳定运行7天
- ✅ 无重大告警遗漏
- ✅ 客户满意度 > 90%
- ✅ 旧系统成功下线

**里程碑**: 🎉 **新系统成功替代旧系统**

---

### Phase 1: 核心算法优化 (Month 3-4, Week 9-16)

**目标**: 提升检测准确性和实时性

#### Week 9-10: 多层级时间窗口实现

**技术方案**:

```java
// services/stream-processing/src/main/java/com/threatdetection/stream/MultiTierWindowJob.java

public class MultiTierWindowJob {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = 
            StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 读取attack-events
        DataStream<AttackEvent> events = env
            .addSource(new FlinkKafkaConsumer<>(
                "attack-events",
                new AttackEventSchema(),
                kafkaProps
            ))
            .assignTimestampsAndWatermarks(
                WatermarkStrategy
                    .<AttackEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                    .withTimestampAssigner((event, timestamp) -> 
                        event.getTimestamp().toEpochMilli())
            );
        
        // Tier 1: 30秒快速窗口 (勒索软件检测)
        DataStream<ThreatAlert> tier1Alerts = events
            .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
            .window(SlidingEventTimeWindows.of(
                Time.seconds(30), 
                Time.seconds(10)
            ))
            .process(new RansomwareDetector())
            .name("Tier1-30s-Ransomware");
        
        // Tier 2: 5分钟战术窗口 (扫描检测)
        DataStream<ThreatAlert> tier2Alerts = events
            .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
            .window(SlidingEventTimeWindows.of(
                Time.minutes(5),
                Time.minutes(1)
            ))
            .process(new ScanDetector())
            .name("Tier2-5min-Scan");
        
        // Tier 3: 15分钟主窗口 (综合评分)
        DataStream<ThreatAlert> tier3Alerts = events
            .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
            .window(SlidingEventTimeWindows.of(
                Time.minutes(15),
                Time.minutes(5)
            ))
            .process(new ComprehensiveThreatScorer())
            .name("Tier3-15min-Comprehensive");
        
        // Tier 4: 1小时战略窗口 (APT初步检测)
        DataStream<ThreatAlert> tier4Alerts = events
            .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
            .window(SlidingEventTimeWindows.of(
                Time.hours(1),
                Time.minutes(15)
            ))
            .process(new APTPreliminaryDetector())
            .name("Tier4-1hr-APT");
        
        // 融合所有层级告警
        DataStream<ThreatAlert> fusedAlerts = tier1Alerts
            .union(tier2Alerts, tier3Alerts, tier4Alerts)
            .keyBy(ThreatAlert::getAttackMac)
            .process(new AlertFusionProcessor())
            .name("Alert-Fusion");
        
        // 输出到Kafka
        fusedAlerts.sinkTo(new FlinkKafkaProducer<>(
            "threat-alerts-multi-tier",
            new ThreatAlertSchema(),
            kafkaProps
        ));
        
        env.execute("Multi-Tier Window Job");
    }
}
```

**勒索软件检测器**:

```java
public class RansomwareDetector 
    extends ProcessWindowFunction<AttackEvent, ThreatAlert, String, TimeWindow> {
    
    @Override
    public void process(
        String key,
        Context context,
        Iterable<AttackEvent> events,
        Collector<ThreatAlert> out) {
        
        List<AttackEvent> eventList = StreamSupport
            .stream(events.spliterator(), false)
            .collect(Collectors.toList());
        
        // 勒索软件特征检测
        long attackCount = eventList.size();
        Set<Integer> ports = eventList.stream()
            .map(AttackEvent::getResponsePort)
            .collect(Collectors.toSet());
        
        // 特征1: 高频攻击 (30秒 > 100次)
        boolean highFrequency = attackCount > 100;
        
        // 特征2: 包含SMB 445端口
        boolean hasSMB = ports.contains(445);
        
        // 特征3: 多端口扫描
        boolean multiPort = ports.size() > 3;
        
        if (highFrequency && hasSMB && multiPort) {
            // 检测到疑似勒索软件
            ThreatAlert alert = ThreatAlert.builder()
                .customerId(key.split(":")[0])
                .attackMac(key.split(":")[1])
                .threatType("RANSOMWARE")
                .threatLevel("CRITICAL")
                .threatScore(1000.0)  // 极高评分
                .detectionTier("TIER1_30S")
                .detectionTime(Instant.now())
                .evidences(Map.of(
                    "attackCount", attackCount,
                    "uniquePorts", ports.size(),
                    "hasSMB", true
                ))
                .build();
            
            out.collect(alert);
            
            log.warn("🚨 勒索软件检测: customerId={}, attackMac={}, attackCount={}, ports={}",
                     alert.getCustomerId(), alert.getAttackMac(), attackCount, ports);
        }
    }
}
```

**任务清单**:

- [ ] **开发**
  - [ ] MultiTierWindowJob实现
  - [ ] RansomwareDetector (Tier 1)
  - [ ] ScanDetector (Tier 2)
  - [ ] ComprehensiveThreatScorer (Tier 3)
  - [ ] APTPreliminaryDetector (Tier 4)
  - [ ] AlertFusionProcessor (融合)

- [ ] **测试**
  - [ ] 单元测试 (每个检测器)
  - [ ] 集成测试 (完整流程)
  - [ ] 性能测试 (10000 events/s)
  - [ ] 勒索软件模拟测试

- [ ] **部署**
  - [ ] 创建新Kafka Topic: threat-alerts-multi-tier
  - [ ] 部署新Flink作业
  - [ ] 灰度发布 (10% → 50% → 100%)

**交付物**:
- ✅ MultiTierWindowJob (5个检测器)
- ✅ 测试报告 (覆盖率 > 80%)
- ✅ 性能基准 (延迟 < 1分钟)

**验收标准**:
- 勒索软件检出时间: 10分钟 → **30秒**
- APT初步检出覆盖率: 60% → **75%**

---

#### Week 11-12: 科学端口权重量化

**数据收集方案**:

```python
# scripts/tools/port_weight_generator.py
"""
科学端口权重生成工具
数据源:
1. CVSS漏洞评分 (NVD API)
2. 威胁情报频率 (AlienVault OTX API)
3. 业务关键度 (人工配置)
"""

import requests
import json
from datetime import datetime, timedelta

# 1. CVSS评分获取
def fetch_cvss_scores(ports):
    """
    从NVD数据库查询端口相关漏洞的CVSS评分
    """
    nvd_api = "https://services.nvd.nist.gov/rest/json/cves/1.0"
    
    port_cvss = {}
    for port in ports:
        # 搜索与端口相关的CVE
        response = requests.get(
            nvd_api,
            params={
                'keyword': f'port {port}',
                'resultsPerPage': 20
            }
        )
        
        if response.status_code == 200:
            data = response.json()
            cves = data.get('result', {}).get('CVE_Items', [])
            
            # 计算平均CVSS评分
            cvss_scores = []
            for cve in cves:
                impact = cve.get('impact', {})
                base_metric = impact.get('baseMetricV3', {})
                cvss_v3 = base_metric.get('cvssV3', {})
                score = cvss_v3.get('baseScore', 0)
                if score > 0:
                    cvss_scores.append(score)
            
            avg_cvss = sum(cvss_scores) / len(cvss_scores) if cvss_scores else 5.0
            port_cvss[port] = avg_cvss
        
        # API限流
        time.sleep(1)
    
    return port_cvss

# 2. 威胁情报频率
def fetch_threat_intelligence(ports):
    """
    从AlienVault OTX查询端口攻击频率
    """
    otx_api_key = "YOUR_OTX_API_KEY"
    otx_api = "https://otx.alienvault.com/api/v1/indicators"
    
    port_frequency = {}
    
    for port in ports:
        # 查询最近90天该端口的攻击报告
        response = requests.get(
            f"{otx_api}/IPv4/0.0.0.0/general",
            headers={'X-OTX-API-KEY': otx_api_key},
            params={
                'section': 'general',
                'port': port
            }
        )
        
        if response.status_code == 200:
            data = response.json()
            # 假设返回的pulse数量代表攻击频率
            frequency = len(data.get('pulse_info', {}).get('pulses', []))
            port_frequency[port] = frequency
        
        time.sleep(0.5)
    
    return port_frequency

# 3. 业务关键度 (人工配置)
BUSINESS_CRITICALITY = {
    # 数据库端口 (极高)
    1433: 10,  # SQL Server
    3306: 10,  # MySQL
    5432: 10,  # PostgreSQL
    1521: 10,  # Oracle
    27017: 9,  # MongoDB
    
    # 业务应用端口 (高)
    80: 8,     # HTTP
    443: 8,    # HTTPS
    8080: 7,   # Tomcat
    
    # 运维端口 (中)
    22: 6,     # SSH
    3389: 7,   # RDP
    
    # 其他
    'default': 5
}

# 4. 综合计算
def calculate_port_weight(port, cvss, frequency, business):
    """
    综合计算端口权重
    
    公式: weight = (cvss_weight × 0.4 + freq_weight × 0.3 + business_weight × 0.3)
    归一化到 1.0 - 3.0
    """
    # 归一化CVSS (0-10 → 0-3.0)
    cvss_weight = (cvss / 10.0) * 3.0
    
    # 归一化频率 (假设最高5000次 → 3.0)
    freq_weight = min(frequency / 5000.0, 1.0) * 3.0
    
    # 归一化业务关键度 (0-10 → 0-3.0)
    business_weight = (business / 10.0) * 3.0
    
    # 加权平均
    weight = (cvss_weight * 0.4 + freq_weight * 0.3 + business_weight * 0.3)
    
    # 限制在1.0-3.0
    return max(1.0, min(3.0, weight))

# 5. 生成权重配置
def generate_port_weights(ports):
    """
    生成完整的端口权重配置
    """
    print("🔍 开始收集端口数据...")
    
    cvss_scores = fetch_cvss_scores(ports)
    print(f"✅ CVSS评分收集完成: {len(cvss_scores)}个端口")
    
    frequencies = fetch_threat_intelligence(ports)
    print(f"✅ 威胁情报收集完成: {len(frequencies)}个端口")
    
    port_weights = []
    
    for port in ports:
        cvss = cvss_scores.get(port, 5.0)
        freq = frequencies.get(port, 0)
        business = BUSINESS_CRITICALITY.get(port, BUSINESS_CRITICALITY['default'])
        
        weight = calculate_port_weight(port, cvss, freq, business)
        
        port_weights.append({
            'port': port,
            'weight': round(weight, 2),
            'cvss_score': round(cvss, 1),
            'threat_frequency': freq,
            'business_criticality': business,
            'updated_at': datetime.now().isoformat()
        })
    
    return port_weights

# 6. 导出SQL
def export_to_sql(port_weights, output_file='port_weights.sql'):
    """
    导出为PostgreSQL INSERT语句
    """
    with open(output_file, 'w') as f:
        f.write("-- 端口权重配置 (科学量化)\n")
        f.write("-- 生成时间: {}\n\n".format(datetime.now()))
        
        f.write("CREATE TABLE IF NOT EXISTS port_weights (\n")
        f.write("    port INTEGER PRIMARY KEY,\n")
        f.write("    weight DECIMAL(3,2) NOT NULL,\n")
        f.write("    cvss_score DECIMAL(3,1),\n")
        f.write("    threat_frequency INTEGER,\n")
        f.write("    business_criticality INTEGER,\n")
        f.write("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n")
        f.write(");\n\n")
        
        f.write("INSERT INTO port_weights (port, weight, cvss_score, threat_frequency, business_criticality) VALUES\n")
        
        values = []
        for pw in port_weights:
            values.append(
                f"({pw['port']}, {pw['weight']}, {pw['cvss_score']}, "
                f"{pw['threat_frequency']}, {pw['business_criticality']})"
            )
        
        f.write(",\n".join(values))
        f.write(";\n")
    
    print(f"✅ SQL导出完成: {output_file}")

# 主程序
if __name__ == '__main__':
    # 关键端口列表 (Top 100)
    critical_ports = [
        21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 
        443, 445, 993, 995, 1433, 1521, 3306, 3389, 5432, 5900,
        8080, 8443, 27017,
        # ... 其余端口
    ]
    
    port_weights = generate_port_weights(critical_ports)
    export_to_sql(port_weights)
    
    print("\n📊 Top 10 权重最高的端口:")
    sorted_weights = sorted(port_weights, key=lambda x: x['weight'], reverse=True)
    for pw in sorted_weights[:10]:
        print(f"  端口 {pw['port']}: 权重={pw['weight']}, "
              f"CVSS={pw['cvss_score']}, 频率={pw['threat_frequency']}")
```

**任务清单**:

- [ ] **数据收集**
  - [ ] 注册NVD API密钥
  - [ ] 注册AlienVault OTX API密钥
  - [ ] 运行port_weight_generator.py
  - [ ] 生成port_weights.sql

- [ ] **数据库部署**
  - [ ] 创建port_weights表
  - [ ] 导入100+端口配置
  - [ ] 验证数据完整性

- [ ] **Flink作业集成**
  ```java
  public class PortWeightService {
      private static Map<Integer, Double> weights = new HashMap<>();
      private static ScheduledExecutorService scheduler = 
          Executors.newScheduledThreadPool(1);
      
      static {
          // 初始加载
          loadWeights();
          
          // 每小时自动更新
          scheduler.scheduleAtFixedRate(
              PortWeightService::loadWeights,
              0, 1, TimeUnit.HOURS
          );
      }
      
      private static void loadWeights() {
          // JDBC查询PostgreSQL
          String sql = "SELECT port, weight FROM port_weights";
          // ... 加载逻辑
          log.info("✅ 端口权重更新完成: {}个端口", weights.size());
      }
      
      public static double getWeight(int port) {
          return weights.getOrDefault(port, 1.0);
      }
  }
  ```

**交付物**:
- ✅ port_weights.sql (100+端口)
- ✅ PortWeightService (自动更新)
- ✅ 权重数据文档

**验收标准**:
- 端口权重准确性提升 > 40%
- 高危端口识别准确率 > 95%

---

#### Week 13-14: 横向移动深度检测

**实现**:

```java
// services/stream-processing/src/main/java/com/threatdetection/stream/LateralMovementDetector.java

public class LateralMovementDetector 
    extends ProcessWindowFunction<AttackEvent, ThreatAlert, String, TimeWindow> {
    
    @Override
    public void process(
        String key,
        Context context,
        Iterable<AttackEvent> events,
        Collector<ThreatAlert> out) {
        
        List<AttackEvent> eventList = StreamSupport
            .stream(events.spliterator(), false)
            .sorted(Comparator.comparing(AttackEvent::getTimestamp))
            .collect(Collectors.toList());
        
        // 1. 提取所有目标IP
        List<String> targetIps = eventList.stream()
            .map(AttackEvent::getResponseIp)
            .distinct()
            .collect(Collectors.toList());
        
        // 2. 分析子网分布
        Map<String, List<String>> subnetMap = new HashMap<>();
        
        for (String ip : targetIps) {
            String subnet = extractSubnet(ip);  // 192.168.1.100 → 192.168.1
            subnetMap.computeIfAbsent(subnet, k -> new ArrayList<>())
                .add(ip);
        }
        
        int subnetCount = subnetMap.size();
        
        // 3. 计算横向移动深度
        int lateralDepth = calculateDepth(subnetCount);
        double lateralWeight = getLateralWeight(lateralDepth);
        
        // 4. 如果检测到跨子网攻击,发出告警
        if (subnetCount >= 2) {
            ThreatAlert alert = ThreatAlert.builder()
                .customerId(key.split(":")[0])
                .attackMac(key.split(":")[1])
                .threatType("LATERAL_MOVEMENT")
                .threatLevel(getLateralThreatLevel(subnetCount))
                .lateralDepth(lateralDepth)
                .subnetCount(subnetCount)
                .affectedSubnets(new ArrayList<>(subnetMap.keySet()))
                .detectionTime(Instant.now())
                .build();
            
            out.collect(alert);
            
            log.warn("🔀 横向移动检测: attackMac={}, 跨{}个子网, 深度={}, 权重={}",
                     alert.getAttackMac(), subnetCount, lateralDepth, lateralWeight);
        }
    }
    
    private String extractSubnet(String ip) {
        // 192.168.1.100 → 192.168.1
        int lastDot = ip.lastIndexOf('.');
        return ip.substring(0, lastDot);
    }
    
    private int calculateDepth(int subnetCount) {
        if (subnetCount >= 5) return 5;  // 极深
        if (subnetCount >= 3) return 4;  // 深
        if (subnetCount >= 2) return 3;  // 中
        return 1;  // 浅
    }
    
    private double getLateralWeight(int depth) {
        switch (depth) {
            case 5: return 3.0;
            case 4: return 2.5;
            case 3: return 2.0;
            case 2: return 1.5;
            default: return 1.0;
        }
    }
    
    private String getLateralThreatLevel(int subnetCount) {
        if (subnetCount >= 5) return "CRITICAL";
        if (subnetCount >= 3) return "HIGH";
        return "MEDIUM";
    }
}
```

**集成到评分公式**:

```java
// 更新ComprehensiveThreatScorer

public class ComprehensiveThreatScorer 
    extends ProcessWindowFunction<AttackEvent, ThreatAlert, String, TimeWindow> {
    
    @Override
    public void process(...) {
        // ... 原有逻辑
        
        // 新增: 横向移动检测
        int subnetCount = calculateSubnetCount(events);
        double lateralWeight = getLateralWeight(subnetCount);
        
        // 更新评分公式
        double threatScore = baseScore
                           × timeWeight
                           × portWeight
                           × ipWeight
                           × deviceWeight
                           × lateralWeight;  // 新增!
        
        // ...
    }
}
```

**任务清单**:

- [ ] 开发LateralMovementDetector
- [ ] 集成到ComprehensiveThreatScorer
- [ ] 单元测试 (跨子网场景)
- [ ] 性能测试

**交付物**:
- ✅ LateralMovementDetector
- ✅ 集成测试报告

**验收标准**:
- APT横向移动检出率 +35%
- 评分准确性提升 +20%

---

#### Week 15-16: Phase 1集成测试与上线

**集成测试方案**:

```python
# scripts/test/phase1_integration_test.py
"""
Phase 1集成测试
验证多窗口架构、端口权重、横向移动检测
"""

import requests
import time
from datetime import datetime

def test_multi_tier_windows():
    """测试多层级时间窗口"""
    print("🧪 测试1: 多层级时间窗口")
    
    # 1. 发送勒索软件模拟数据 (高频SMB攻击)
    for i in range(150):
        send_attack_event({
            'attack_mac': '00:11:22:33:44:55',
            'attack_ip': '192.168.1.100',
            'response_ip': f'10.0.0.{i % 50}',
            'response_port': 445,  # SMB
            'customer_id': 'test-customer',
            'timestamp': int(time.time() * 1000)
        })
        time.sleep(0.2)  # 150次/30秒 = 5次/秒
    
    # 2. 等待Tier 1检测 (30秒窗口)
    time.sleep(40)
    
    # 3. 验证告警
    alerts = get_alerts_by_tier('TIER1_30S', minutes=1)
    
    assert len(alerts) > 0, "❌ Tier 1未检测到勒索软件"
    assert alerts[0]['threat_type'] == 'RANSOMWARE', "❌ 威胁类型错误"
    
    print("✅ 测试1通过: Tier 1在30秒内检测到勒索软件")

def test_port_weights():
    """测试科学端口权重"""
    print("🧪 测试2: 科学端口权重")
    
    # 发送高危端口攻击
    send_attack_event({
        'response_port': 445,  # SMB, 权重应为2.9
    })
    
    # 发送低危端口攻击
    send_attack_event({
        'response_port': 8080,  # Tomcat, 权重应为1.5
    })
    
    time.sleep(60)
    
    # 验证评分差异
    high_risk_alert = get_alert_by_port(445)
    low_risk_alert = get_alert_by_port(8080)
    
    score_ratio = high_risk_alert['threat_score'] / low_risk_alert['threat_score']
    
    assert score_ratio > 1.8, f"❌ 端口权重差异不足: {score_ratio}"
    
    print(f"✅ 测试2通过: 高危端口评分是低危端口的{score_ratio:.1f}倍")

def test_lateral_movement():
    """测试横向移动检测"""
    print("🧪 测试3: 横向移动检测")
    
    # 模拟跨3个子网的攻击
    subnets = ['192.168.1', '192.168.2', '192.168.3']
    
    for subnet in subnets:
        for i in range(10):
            send_attack_event({
                'attack_mac': '00:11:22:33:44:66',
                'response_ip': f'{subnet}.{i + 10}',
                'response_port': 3389,
            })
    
    time.sleep(300)  # 等待5分钟窗口
    
    # 验证横向移动告警
    alerts = get_alerts_by_type('LATERAL_MOVEMENT')
    
    assert len(alerts) > 0, "❌ 未检测到横向移动"
    assert alerts[0]['subnet_count'] >= 3, "❌ 子网计数错误"
    assert alerts[0]['lateral_depth'] >= 3, "❌ 横向深度计算错误"
    
    print("✅ 测试3通过: 成功检测到跨3个子网的横向移动")

if __name__ == '__main__':
    test_multi_tier_windows()
    test_port_weights()
    test_lateral_movement()
    
    print("\n🎉 Phase 1集成测试全部通过!")
```

**任务清单**:

- [ ] 执行集成测试
- [ ] 性能压测 (10000 events/s)
- [ ] 灰度发布 (10% → 50% → 100%)
- [ ] 监控7天稳定性

**交付物**:
- ✅ 集成测试报告
- ✅ 性能测试报告
- ✅ 灰度发布记录
- ✅ Phase 1验收报告

**里程碑**: 🎉 **Phase 1核心优化完成**

---

### Phase 2: 智能增强 (Month 5-6, Week 17-24)

**目标**: 引入机器学习和高级分析

*(详细实施计划见下一部分...)*

---

## 📦 交付件模板

### 1. 周报模板

```markdown
# 威胁检测系统 - 开发周报

**周次**: Week X  
**日期**: 2025-XX-XX ~ 2025-XX-XX  
**阶段**: Phase X

## 本周完成

- [x] 任务1: XXX
- [x] 任务2: XXX

## 本周问题

1. **问题**: XXX
   - **影响**: XXX
   - **解决方案**: XXX
   - **状态**: ✅ 已解决 / ⏳ 进行中

## 下周计划

- [ ] 任务1: XXX
- [ ] 任务2: XXX

## 指标

| 指标 | 本周 | 目标 | 状态 |
|------|------|------|------|
| 代码覆盖率 | 82% | 80% | ✅ |
| 单元测试 | 150个 | 100+ | ✅ |

## 风险

- ⚠️ 风险1: XXX (可能性: 中, 影响: 高)
```

### 2. 测试报告模板

```markdown
# 测试报告 - Phase X Week X

## 测试概要

- **测试类型**: 单元测试 / 集成测试 / 性能测试
- **测试时间**: 2025-XX-XX
- **测试环境**: Development / Staging / Production

## 测试结果

| 测试项 | 用例数 | 通过 | 失败 | 通过率 |
|--------|--------|------|------|--------|
| 单元测试 | 150 | 148 | 2 | 98.7% |
| 集成测试 | 45 | 45 | 0 | 100% |

## 失败用例分析

### 失败用例1: XXX

- **原因**: XXX
- **修复方案**: XXX
- **状态**: ✅ 已修复

## 性能指标

| 指标 | 实际值 | 目标值 | 状态 |
|------|--------|--------|------|
| 吞吐量 | 9500 events/s | 10000 events/s | ⚠️ |
| 延迟 | 45s | < 60s | ✅ |

## 结论

✅ 测试通过,可以进入下一阶段
```

---

## 🎯 成功标准

### Phase 0成功标准

- ✅ 新系统稳定运行14天
- ✅ 告警匹配率 > 95%
- ✅ 评分误差 < 5%
- ✅ 旧系统成功下线

### Phase 1成功标准

- ✅ 勒索软件检出 < 1分钟
- ✅ 端口权重准确性 +40%
- ✅ APT检出覆盖率 +15%
- ✅ 系统稳定性 99.5%+

### 总体成功标准 (8个月后)

- ✅ APT检出率 60% → 95%
- ✅ 误报率 2% → 0.1%
- ✅ 检出时间 30天 → 7天
- ✅ 客户满意度 > 90%

---

**文档结束** (第1部分)

*下一部分: Phase 2-3详细计划、风险管理、团队配置*
