# 🎯 最小可行方案 (MVP) 实施计划

**文档版本**: 2.0  
**制定日期**: 2025-10-13  
**基于原则**: 核心功能优先，高风险功能延后或降级  
**项目周期**: 4个月 (原8个月压缩)  
**决策驱动**: 3个关键Go/No-Go决策点

---

## 📋 执行摘要

基于《关键困难与依赖分析》的发现，本计划聚焦于**最小可行方案**：

### 核心目标

```
✅ 必须实现 (MVP核心):
  - 系统完整替代 (C# → Java)
  - 3层时间窗口 (30s / 5min / 15min)
  - 混合端口权重 (50个高危端口用CVSS + 其余用经验值)
  - 基础误报过滤 (规则白名单)
  - 多租户隔离
  - 实时流处理 (延迟 < 4分钟)

🌟 可选增强 (基于决策点):
  - L1 eBPF蜜罐 (Week 4决策 → 高风险)
  - ML误报过滤 (Week 18决策 → 数据成本高)
  - 完整APT状态机 (Week 26决策 → 验证困难)

❌ 明确延后:
  - L3高交互蜜罐 (Phase 2)
  - 洪泛数据分析 (Phase 2)
  - 完整网段权重 (Phase 2)
  - 5层时间窗口 (MVP只实现3层)
```

### 预期成果

| 指标 | 原系统 | MVP目标 | 完整版目标 |
|------|--------|---------|-----------|
| **端到端延迟** | 10-30分钟 | **< 4分钟** | < 2分钟 |
| **检测准确率** | 基准 | **+30%** | +60% |
| **APT覆盖率** | 14% | **14-37%** | 84% |
| **误报率下降** | 基准 | **-40%** (规则) | -70% (ML) |
| **实施周期** | - | **4个月** | 8个月 |
| **技术风险** | - | **低** | 高 |

---

## 🎯 战略原则

### MVP定义

**最小可行产品** = 能够**完全替代原系统**并**显著提升检测能力**的最小功能集

```
MVP核心逻辑:
1. 如果不实现,系统无法运行 → 必须做
2. 如果不实现,检测能力无提升 → 必须做
3. 如果不实现,只是锦上添花 → 延后做
4. 如果风险太高,可能拖延项目 → 降级或延后
```

### 决策树

```
                   系统替代 (Phase 0)
                         ↓
              ┌──────────┴──────────┐
              │  核心优化 (Phase 1)  │
              │  - 3层窗口          │
              │  - 混合端口权重      │
              │  - 规则白名单        │
              └──────────┬──────────┘
                         ↓
            ┌────────────┴────────────┐
            │   Week 4 决策点 #1      │
            │   kSwitch蜜罐可行性     │
            └────┬────────────────┬───┘
                 │                │
          ✅ Go  │                │ ❌ No-Go
                 │                │
         Week 5-8蜜罐开发    继续Phase 1
                 │          不实施蜜罐
                 ↓                ↓
            ┌────┴────────────────┴───┐
            │   Week 18 决策点 #2     │
            │   ML模型效果验证        │
            └────┬────────────────┬───┘
                 │                │
          ✅ Go  │                │ ❌ No-Go
                 │                │
         Week 19-20 ML部署   保持规则过滤
                 │                │
                 ↓                ↓
            ┌────┴────────────────┴───┐
            │   Week 26 决策点 #3     │
            │   APT状态机验证         │
            └────┬────────────────┬───┘
                 │                │
          ✅ Go  │                │ ❌ No-Go
                 │                │
      Week 27-32 APT实现    持续性评分
```

---

## 📅 4个月实施时间表

### Phase 0: 系统替代 (Month 1, Week 1-4)

**目标**: 新系统完全替代旧C#系统，实现功能对齐

#### Week 1-2: 核心功能开发与对齐

**任务清单**:

- [ ] **基础服务部署**
  ```bash
  # Docker环境搭建
  docker-compose up -d kafka zookeeper postgresql
  
  # 服务部署
  docker-compose up -d data-ingestion stream-processing threat-assessment
  ```

- [ ] **端口权重快速迁移** (混合策略)
  
  **高危端口 (50个) - CVSS驱动**:
  ```sql
  -- 创建端口权重配置表
  CREATE TABLE port_weights (
      port INTEGER PRIMARY KEY,
      weight DECIMAL(3,2) NOT NULL,
      source VARCHAR(50),  -- 'cvss_api' 或 'legacy'
      cvss_score DECIMAL(3,1),
      last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  
  -- 高危端口列表 (从原系统219个中选出top 50)
  INSERT INTO port_weights (port, weight, source) VALUES
  (445, 2.9, 'legacy'),    -- SMB
  (3389, 2.7, 'legacy'),   -- RDP
  (22, 2.4, 'legacy'),     -- SSH
  (3306, 2.5, 'legacy'),   -- MySQL
  (1433, 2.6, 'legacy'),   -- MSSQL
  (135, 2.5, 'legacy'),    -- RPC
  (139, 2.4, 'legacy'),    -- NetBIOS
  (5900, 2.3, 'legacy'),   -- VNC
  -- ... 其余42个
  ;
  ```

- [ ] **Flink 聚合作业 (简化版 - 3层窗口)**
  
  ```java
  // MinuteAggregationJob.java - 3层时间窗口
  public class MinuteAggregationJob {
      
      public static void main(String[] args) throws Exception {
          StreamExecutionEnvironment env = 
              StreamExecutionEnvironment.getExecutionEnvironment();
          
          // 从Kafka读取攻击事件
          DataStream<AttackEvent> events = env
              .addSource(new FlinkKafkaConsumer<>("attack-events", ...));
          
          // 窗口1: 30秒窗口 (勒索软件快速检测)
          DataStream<AggregatedData> window30s = events
              .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
              .window(TumblingEventTimeWindows.of(Time.seconds(30)))
              .aggregate(new AttackAggregator())
              .name("30s-window");
          
          // 窗口2: 5分钟窗口 (主窗口)
          DataStream<AggregatedData> window5min = events
              .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
              .window(TumblingEventTimeWindows.of(Time.minutes(5)))
              .aggregate(new AttackAggregator())
              .name("5min-window");
          
          // 窗口3: 15分钟窗口 (APT慢速扫描)
          DataStream<AggregatedData> window15min = events
              .keyBy(e -> e.getCustomerId() + ":" + e.getAttackMac())
              .window(TumblingEventTimeWindows.of(Time.minutes(15)))
              .aggregate(new AttackAggregator())
              .name("15min-window");
          
          // 合并3层窗口结果
          window30s.union(window5min, window15min)
              .addSink(new FlinkKafkaProducer<>("minute-aggregations", ...));
          
          env.execute("3-Tier Window Aggregation");
      }
  }
  ```

- [ ] **威胁评分计算 (核心逻辑)**
  
  ```java
  // ThreatScoreCalculator.java
  public class ThreatScoreCalculator {
      
      private Map<Integer, Double> portWeights = new HashMap<>();
      
      public ThreatScoreCalculator() {
          loadPortWeights();  // 从PostgreSQL加载
      }
      
      public ThreatAlert calculate(AggregatedData data) {
          // 基础分数
          double baseScore = data.getAttackCount() 
                           * data.getUniqueIps() 
                           * data.getUniquePorts();
          
          // 时间权重 (5时段)
          double timeWeight = calculateTimeWeight(data.getTimestamp());
          
          // IP权重 (5档)
          double ipWeight = calculateIpWeight(data.getUniqueIps());
          
          // 端口权重 (6档)
          double portWeight = calculatePortWeight(data.getUniquePorts());
          
          // 设备权重 (2档)
          double deviceWeight = data.getUniqueDevices() > 1 ? 1.5 : 1.0;
          
          // 最终评分
          double threatScore = baseScore * timeWeight * ipWeight 
                             * portWeight * deviceWeight;
          
          // 威胁等级
          String level = classifyThreatLevel(threatScore);
          
          return ThreatAlert.builder()
              .customerId(data.getCustomerId())
              .attackMac(data.getAttackMac())
              .threatScore(threatScore)
              .threatLevel(level)
              .attackCount(data.getAttackCount())
              .uniqueIps(data.getUniqueIps())
              .uniquePorts(data.getUniquePorts())
              .uniqueDevices(data.getUniqueDevices())
              .timestamp(data.getTimestamp())
              .build();
      }
      
      private double calculateTimeWeight(Instant timestamp) {
          LocalTime time = LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
          int hour = time.getHour();
          
          if (hour >= 0 && hour < 6) return 1.2;   // 深夜
          if (hour >= 6 && hour < 9) return 1.1;   // 早晨
          if (hour >= 9 && hour < 17) return 1.0;  // 工作时间
          if (hour >= 17 && hour < 21) return 0.9; // 傍晚
          return 0.8;  // 夜间
      }
      
      private double calculateIpWeight(int uniqueIps) {
          if (uniqueIps == 1) return 1.0;
          if (uniqueIps <= 3) return 1.3;
          if (uniqueIps <= 5) return 1.5;
          if (uniqueIps <= 10) return 1.7;
          return 2.0;
      }
      
      private double calculatePortWeight(int uniquePorts) {
          if (uniquePorts == 1) return 1.0;
          if (uniquePorts <= 3) return 1.2;
          if (uniquePorts <= 5) return 1.4;
          if (uniquePorts <= 10) return 1.6;
          if (uniquePorts <= 20) return 1.8;
          return 2.0;
      }
      
      private String classifyThreatLevel(double score) {
          if (score < 10) return "INFO";
          if (score < 50) return "LOW";
          if (score < 100) return "MEDIUM";
          if (score < 200) return "HIGH";
          return "CRITICAL";
      }
  }
  ```

**交付物**:
- ✅ 3个微服务运行 (data-ingestion, stream-processing, threat-assessment)
- ✅ 3层时间窗口实现
- ✅ 混合端口权重配置表 (50个高危端口)
- ✅ 威胁评分算法实现

**验收标准**:
- 端到端延迟 < 4分钟
- 与原系统评分差异 < 10% (使用相同测试数据)
- Kafka消息无丢失

---

#### Week 3-4: 对比测试与生产切换

**任务清单**:

- [ ] **双写对比测试**
  ```bash
  # rsyslog配置双写
  sudo vim /etc/rsyslog.d/threat-detection.conf
  ```
  
  ```conf
  # 同时发送给旧系统和新系统
  *.* @@old-system:514
  *.* @@new-system:9080
  ```

- [ ] **对比测试 (7天)**
  ```python
  # scripts/test/compare_systems.py
  """对比新旧系统的告警结果"""
  
  import psycopg2
  import pymysql
  import pandas as pd
  
  # 连接新系统 (PostgreSQL)
  new_db = psycopg2.connect(
      host="localhost", database="threat_detection",
      user="postgres", password="password"
  )
  
  # 连接旧系统 (MySQL)
  old_db = pymysql.connect(
      host="old-system", database="jzthreat",
      user="root", password="password"
  )
  
  # 提取最近7天的告警
  new_alerts = pd.read_sql("""
      SELECT customer_id, attack_mac, threat_score, threat_level
      FROM threat_assessments
      WHERE created_at >= NOW() - INTERVAL '7 days'
  """, new_db)
  
  old_alerts = pd.read_sql("""
      SELECT company_obj_id as customer_id, 
             attack_mac, 
             total_score as threat_score,
             threat_grade as threat_level
      FROM attack_summary
      WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
  """, old_db)
  
  # 对比分析
  print(f"新系统告警数: {len(new_alerts)}")
  print(f"旧系统告警数: {len(old_alerts)}")
  
  # 评分相关性
  merged = pd.merge(new_alerts, old_alerts, 
                    on=['customer_id', 'attack_mac'],
                    suffixes=('_new', '_old'))
  
  correlation = merged['threat_score_new'].corr(merged['threat_score_old'])
  print(f"评分相关性: {correlation:.2f}")
  ```

- [ ] **规则白名单实现**
  ```java
  // WhitelistFilter.java - 基础规则过滤
  public class WhitelistFilter {
      
      private Set<String> whitelistedMacs = new HashSet<>();
      private Set<String> whitelistedIps = new HashSet<>();
      private Map<String, Set<String>> businessSystemIps = new HashMap<>();
      
      public WhitelistFilter() {
          loadWhitelists();
      }
      
      public boolean shouldFilter(ThreatAlert alert) {
          String attackMac = alert.getAttackMac();
          String attackIp = alert.getAttackIp();
          
          // 规则1: Windows Update特征
          if (isWindowsUpdate(alert)) {
              return true;
          }
          
          // 规则2: 已知扫描工具
          if (isKnownScanner(alert)) {
              return true;
          }
          
          // 规则3: 业务系统白名单
          if (isBusinessSystem(alert)) {
              return true;
          }
          
          // 规则4: 手动白名单
          if (whitelistedMacs.contains(attackMac) || 
              whitelistedIps.contains(attackIp)) {
              return true;
          }
          
          return false;
      }
      
      private boolean isWindowsUpdate(ThreatAlert alert) {
          // Windows Update特征: 访问80/443端口，攻击计数低
          Set<Integer> ports = alert.getResponsePorts();
          return (ports.contains(80) || ports.contains(443))
                 && alert.getAttackCount() < 50
                 && alert.getUniquePorts() <= 2;
      }
      
      private boolean isKnownScanner(ThreatAlert alert) {
          // 已知扫描工具: nmap, masscan的MAC地址前缀
          String mac = alert.getAttackMac();
          return mac.startsWith("00:0C:29")  // VMware
                 || mac.startsWith("08:00:27");  // VirtualBox
      }
      
      private boolean isBusinessSystem(ThreatAlert alert) {
          String customerId = alert.getCustomerId();
          String attackIp = alert.getAttackIp();
          
          Set<String> businessIps = businessSystemIps.get(customerId);
          return businessIps != null && businessIps.contains(attackIp);
      }
  }
  ```

**交付物**:
- ✅ 7天对比测试报告
- ✅ 规则白名单实现 (预期减少40%误报)
- ✅ 生产切换方案

**验收标准**:
- 告警数量差异 < 15%
- 评分相关性 > 0.85
- 无系统故障

---

### 🚦 决策点 #1: kSwitch蜜罐可行性验证 (Week 4 末)

**评估任务**:

- [ ] **kSwitch编译与基础测试**
  ```bash
  # 克隆kSwitch项目
  git clone https://github.com/your-org/kSwitch.git
  cd kSwitch
  
  # 编译
  make
  
  # 验证基础功能
  sudo ./kSwitchLoader -i eth0 -m promiscuous
  ```

- [ ] **eBPF专家评估**
  - [ ] 团队是否有2年以上eBPF/XDP经验的工程师？
  - [ ] 是否能在4周内实现TCP SYN-ACK响应？
  - [ ] 性能测试: 吞吐量下降 < 5%？

**决策矩阵**:

| 评估项 | 权重 | Go条件 | No-Go条件 |
|--------|------|--------|-----------|
| **技术可行性** | 40% | kSwitch编译成功，基础功能正常 | 编译失败或无法运行 |
| **团队能力** | 30% | 有eBPF专家 | 无相关经验 |
| **时间成本** | 20% | 预估 ≤ 4周 | 预估 > 6周 |
| **风险评估** | 10% | 有降级方案 | 风险不可控 |

**决策结果**:

✅ **Go Decision** (继续蜜罐开发):
- Week 5-8: 实施L1 eBPF蜜罐
- 预期APT覆盖率: 14% → 37%

❌ **No-Go Decision** (降级方案):
- **立即切换到容器蜜罐**:
  ```bash
  # 部署Dionaea (中交互蜜罐)
  docker run -d --name dionaea \
    --network host \
    -v /var/log/dionaea:/var/log/dionaea \
    dinotools/dionaea:latest
  ```
- 预期APT覆盖率: 14% → 64% (跳过L1，直接L2)
- 实施时间: Week 5-6 (2周)

**关键指标**:
- 如果选择No-Go，项目周期缩短2周
- MVP功能不受影响

---

### Phase 1: 核心优化 (Month 2, Week 5-8)

**两条路径**:

#### 路径A: kSwitch蜜罐开发 (如果决策点#1 = Go)

**Week 5-6: L1 eBPF蜜罐实现**

- [ ] **eBPF服务模拟器**
  ```c
  // kSwitchServiceEmulator.bpf.c
  SEC("xdp")
  int service_emulator(struct xdp_md *ctx) {
      void *data_end = (void *)(long)ctx->data_end;
      void *data = (void *)(long)ctx->data;
      
      struct ethhdr *eth = data;
      if ((void *)(eth + 1) > data_end) return XDP_PASS;
      
      // 检查是否是访问诱饵IP
      if (eth->h_proto != htons(ETH_P_IP)) return XDP_PASS;
      
      struct iphdr *ip = (void *)(eth + 1);
      if ((void *)(ip + 1) > data_end) return XDP_PASS;
      
      __u32 dst_ip = ip->daddr;
      
      // 查询诱饵IP表
      int *is_honeypot = bpf_map_lookup_elem(&honeypot_ips, &dst_ip);
      if (!is_honeypot || *is_honeypot == 0) return XDP_PASS;
      
      // TCP协议处理
      if (ip->protocol == IPPROTO_TCP) {
          struct tcphdr *tcp = (void *)ip + (ip->ihl * 4);
          if ((void *)(tcp + 1) > data_end) return XDP_PASS;
          
          // SYN包 → 响应SYN-ACK
          if (tcp->syn && !tcp->ack) {
              send_syn_ack(ctx, eth, ip, tcp);
              log_attack_event(ip->saddr, dst_ip, ntohs(tcp->dest));
              return XDP_DROP;  // 丢弃原始SYN包
          }
      }
      
      return XDP_PASS;
  }
  ```

**Week 7-8: 威胁驱动动态配置**

- [ ] **Java控制器**
  ```java
  // HoneypotController.java
  @Service
  public class HoneypotController {
      
      @KafkaListener(topics = "threat-alerts")
      public void handleThreatAlert(ThreatAlert alert) {
          // 根据威胁评分动态升级蜜罐
          if (alert.getThreatScore() > 100 && alert.getThreatScore() < 200) {
              // 升级到L1 (eBPF蜜罐)
              upgradeToL1Honeypot(alert.getAttackMac(), alert.getResponseIps());
          } else if (alert.getThreatScore() >= 200) {
              // 升级到L2 (容器蜜罐)
              upgradeToL2Honeypot(alert.getAttackMac(), alert.getResponseIps());
          }
      }
      
      private void upgradeToL1Honeypot(String attackMac, Set<String> targetIps) {
          // 通过BPF Maps配置eBPF蜜罐
          for (String ip : targetIps) {
              bpfMapUpdate("honeypot_ips", ip, 1);  // 启用该IP的蜜罐
          }
          log.info("Upgraded to L1 honeypot: mac={}, ips={}", attackMac, targetIps);
      }
  }
  ```

**交付物**:
- ✅ L1 eBPF蜜罐 (TCP SYN-ACK + Banner)
- ✅ 威胁驱动动态配置
- ✅ APT覆盖率提升到 37%

---

#### 路径B: 容器蜜罐部署 (如果决策点#1 = No-Go)

**Week 5-6: Dionaea容器蜜罐**

- [ ] **Docker部署**
  ```yaml
  # docker/honeypot/docker-compose.yml
  version: '3.8'
  
  services:
    dionaea:
      image: dinotools/dionaea:latest
      container_name: dionaea-honeypot
      network_mode: host
      volumes:
        - ./dionaea-logs:/var/log/dionaea
        - ./dionaea-config:/etc/dionaea
      restart: unless-stopped
    
    cowrie:
      image: cowrie/cowrie:latest
      container_name: cowrie-ssh-honeypot
      ports:
        - "2222:2222"  # SSH蜜罐
        - "2223:2223"  # Telnet蜜罐
      volumes:
        - ./cowrie-logs:/cowrie/var/log
      restart: unless-stopped
  ```

- [ ] **日志采集 (Filebeat → Kafka)**
  ```yaml
  # filebeat.yml
  filebeat.inputs:
    - type: log
      paths:
        - /var/log/dionaea/*.json
      json.keys_under_root: true
      json.add_error_key: true
      fields:
        honeypot_type: dionaea
        
    - type: log
      paths:
        - /cowrie/var/log/cowrie.json
      json.keys_under_root: true
      fields:
        honeypot_type: cowrie
  
  output.kafka:
    hosts: ["localhost:9092"]
    topic: "honeypot-events"
    codec.json:
      pretty: false
  ```

**交付物**:
- ✅ L2 容器蜜罐 (Dionaea + Cowrie)
- ✅ 日志采集流程
- ✅ APT覆盖率提升到 64%

**时间节省**: 2周 (相比eBPF方案)

---

### Month 3: 稳定性优化与生产验证 (Week 9-12)

**目标**: 确保MVP系统稳定可靠

#### Week 9-10: 性能优化

- [ ] **Flink性能调优**
  ```yaml
  # flink-conf.yaml
  state.backend: rocksdb
  state.backend.incremental: true
  state.checkpoints.num-retained: 3
  execution.checkpointing.interval: 60s
  execution.checkpointing.timeout: 300s
  
  # RocksDB配置
  state.backend.rocksdb.block.cache-size: 256m
  state.backend.rocksdb.thread.num: 4
  ```

- [ ] **状态TTL配置**
  ```java
  // 配置状态过期时间
  StateTtlConfig ttlConfig = StateTtlConfig
      .newBuilder(Time.days(7))
      .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
      .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
      .build();
  
  MapStateDescriptor<String, AggregatedData> descriptor = 
      new MapStateDescriptor<>("attack-state", ...);
  descriptor.enableTimeToLive(ttlConfig);
  ```

- [ ] **压力测试**
  ```bash
  # 使用JMeter模拟10000 events/s
  jmeter -n -t stream_performance_test.jmx \
    -Jthreads=100 \
    -Jrampup=30 \
    -Jduration=600
  ```

**验收标准**:
- 吞吐量 > 10000 events/s
- Checkpoint时间 < 30s
- 端到端延迟 < 4分钟 (p99)

#### Week 11-12: 监控与告警

- [ ] **Prometheus监控**
  ```yaml
  # prometheus.yml
  scrape_configs:
    - job_name: 'flink'
      static_configs:
        - targets: ['flink-jobmanager:9249']
    
    - job_name: 'kafka'
      static_configs:
        - targets: ['kafka:9308']
    
    - job_name: 'spring-boot'
      static_configs:
        - targets: ['data-ingestion:8080', 
                    'threat-assessment:8082']
  ```

- [ ] **Grafana仪表盘**
  - Kafka消息积压监控
  - Flink Checkpoint时间
  - 威胁告警趋势
  - 系统资源使用率

**交付物**:
- ✅ 完整监控体系
- ✅ MVP系统生产就绪

---

### 🚦 决策点 #2: ML模型效果验证 (Month 4, Week 18)

**前置工作 (Week 13-17)**:

- [ ] **数据标注 (500条初步验证)**
  ```python
  # 标注工具运行
  python scripts/tools/alert_labeling_tool.py
  
  # 目标: 500条标注数据
  # 时间: 2周 (2人 × 40小时)
  # 成本: $8,000
  ```

- [ ] **初步模型训练**
  ```python
  # 随机森林模型
  from sklearn.ensemble import RandomForestClassifier
  from sklearn.model_selection import train_test_split
  
  # 特征: attackCount, uniqueIps, uniquePorts, threatScore, ...
  X_train, X_test, y_train, y_test = train_test_split(features, labels)
  
  model = RandomForestClassifier(n_estimators=100)
  model.fit(X_train, y_train)
  
  accuracy = model.score(X_test, y_test)
  print(f"准确率: {accuracy:.2%}")
  ```

**Week 18 决策评估**:

| 评估项 | Go条件 | No-Go条件 |
|--------|--------|-----------|
| **模型准确率** | ≥ 90% | < 85% |
| **误报率下降** | ≥ 50% | < 30% |
| **标注成本** | 可接受 | 超预算 |

**决策结果**:

✅ **Go Decision** (部署ML模型):
- Week 19-20: 继续标注到2000条
- Week 21-22: 完整模型训练与部署
- 预期误报率下降: 70%

❌ **No-Go Decision** (保持规则过滤):
- **规则白名单优化**:
  ```java
  // 增强规则逻辑
  - Windows Update: 更精确的特征识别
  - 业务系统: 扩展白名单范围
  - 时间模式: 工作时间扫描 → 低优先级
  ```
- 预期误报率下降: 40%
- 节省成本: $8,700 (剩余标注成本)

---

### 🚦 决策点 #3: APT状态机验证 (Month 5, Week 26)

**前置工作 (Week 23-25)**:

- [ ] **简化版状态机实现 (3阶段)**
  ```java
  public enum APTStage {
      RECONNAISSANCE,  // 侦察: uniquePorts > 5
      EXPLOITATION,    // 利用: exploitSignature != null
      PERSISTENCE      // 持久化: C&C连接特征
  }
  
  public class SimpleAPTStateMachine extends KeyedProcessFunction {
      
      private ValueState<APTStage> currentStage;
      private ValueState<Long> stageStartTime;
      
      @Override
      public void processElement(AggregatedData data, Context ctx, Collector out) {
          APTStage stage = currentStage.value();
          
          // 状态转换逻辑
          if (stage == null && data.getUniquePorts() > 5) {
              // 进入侦察阶段
              currentStage.update(APTStage.RECONNAISSANCE);
              stageStartTime.update(System.currentTimeMillis());
          } else if (stage == APTStage.RECONNAISSANCE && 
                     data.getExploitSignature() != null) {
              // 侦察 → 利用
              currentStage.update(APTStage.EXPLOITATION);
              
              // 发送APT告警
              out.collect(new APTAlert(data, "APT_EXPLOITATION_DETECTED"));
          }
      }
  }
  ```

- [ ] **红队演练验证**
  ```bash
  # 模拟APT攻击场景
  # 1. 侦察: nmap大范围扫描
  nmap -sS 10.0.0.0/24 -p 1-65535
  
  # 2. 利用: Metasploit SMB攻击
  msfconsole
  use exploit/windows/smb/ms17_010_eternalblue
  set RHOST 10.0.0.100
  exploit
  
  # 3. 横向移动: PSExec
  use exploit/windows/smb/psexec
  ```

**Week 26 决策评估**:

| 评估项 | Go条件 | No-Go条件 |
|--------|--------|-----------|
| **APT检出率** | ≥ 80% | < 60% |
| **误报率** | < 5% | > 10% |
| **状态管理性能** | Checkpoint < 1min | > 2min |

**决策结果**:

✅ **Go Decision** (完整状态机):
- Week 27-28: 扩展到6阶段状态机
- 预期APT检出率: 95%

❌ **No-Go Decision** (持续性评分):
- **简化方案**:
  ```java
  // 仅计算攻击持续时间和频率
  double persistenceScore = attackDuration * avgAttackRate;
  
  if (persistenceScore > threshold) {
      alert = "PERSISTENT_THREAT_DETECTED";
  }
  ```
- 预期APT检出率: 70%
- 节省复杂度

---

## 📊 MVP vs 完整版对比

| 功能 | MVP (4个月) | 完整版 (8个月) | 差异 |
|------|------------|---------------|------|
| **系统替代** | ✅ 完成 | ✅ 完成 | - |
| **时间窗口** | ✅ 3层 | ✅ 5层 | -2层 |
| **端口权重** | ✅ 混合 (50+169) | ✅ 完整CVSS | 部分自动化 |
| **误报过滤** | ✅ 规则 | ✅ ML | 精度差30% |
| **蜜罐系统** | ⚠️ L2容器 (决策驱动) | ✅ L1/L2/L3 | APT覆盖率差20% |
| **APT检测** | ⚠️ 简化3阶段 | ✅ 完整6阶段 | 检出率差25% |
| **网段权重** | ❌ 延后 | ✅ 实现 | - |
| **洪泛分析** | ❌ 延后 | ✅ 实现 | - |

---

## 💰 成本效益分析

### MVP成本

| 项目 | MVP (4个月) | 完整版 (8个月) | 节省 |
|------|------------|---------------|------|
| **人力成本** | 4月 × 7.5 FTE = 30人月 | 8月 × 7.5 FTE = 60人月 | -50% |
| **ML标注** | $0-8,000 (决策驱动) | $16,700 | -52% |
| **基础设施** | $920 | $1,840 | -50% |
| **总成本** | **~$300K** | **~$600K** | **-50%** |

### MVP收益

| 指标 | 提升幅度 | 年化收益 |
|------|---------|---------|
| **检测延迟降低** | 10-30min → 4min | $200K |
| **误报率下降** | -40% (规则) | $150K |
| **APT覆盖率提升** | 14% → 37-64% | $300K |
| **总收益** | - | **$650K/年** |

**MVP ROI**: (650K - 300K) / 300K = **117%**

---

## 🎯 关键成功因素

### 1. 决策纪律

```
每个决策点必须在规定周次完成
不允许拖延决策 (最多延后1周)
Go/No-Go必须基于客观数据,非主观判断
```

### 2. 降级意愿

```
接受降级方案 = 接受"足够好"而非"完美"
MVP目标: 替代旧系统 + 30%提升
完美主义会导致项目延期
```

### 3. 范围控制

```
严格控制功能蔓延 (Feature Creep)
任何超出MVP范围的需求 → Phase 2
优先级排序: P0 (必须) > P1 (重要) > P2 (可选)
```

### 4. 持续验证

```
每2周Sprint Review
每月生产验证
及早发现问题,及时调整
```

---

## 📋 MVP验收清单

### Phase 0验收 (Week 4)

- [ ] 新系统完全替代旧系统
- [ ] 3层时间窗口运行正常
- [ ] 混合端口权重配置完成
- [ ] 端到端延迟 < 4分钟
- [ ] 与旧系统评分相关性 > 0.85

### Phase 1验收 (Week 8)

- [ ] 蜜罐系统部署 (L1或L2,取决于决策#1)
- [ ] 规则白名单减少40%误报
- [ ] 性能测试通过 (10000 events/s)
- [ ] 监控体系完整

### MVP最终验收 (Month 4)

- [ ] 生产环境稳定运行30天
- [ ] 检测准确率提升 ≥ 30%
- [ ] 误报率下降 ≥ 40%
- [ ] APT覆盖率 ≥ 37%
- [ ] 客户满意度 ≥ 80%

---

## 🚀 后续演进路径 (Phase 2)

**如果MVP成功,可选增强**:

### Month 5-6: 高级功能

- 完整ML模型 (如果决策#2 = No-Go)
- 完整APT状态机 (如果决策#3 = No-Go)
- 5层时间窗口
- 网段权重系统

### Month 7-8: APT深度建模

- L3高交互蜜罐
- 洪泛数据分析
- 持久化评分
- 威胁情报集成

---

## 📞 团队配置 (MVP)

| 角色 | FTE | 职责 |
|------|-----|------|
| **项目经理** | 1.0 | 进度管理,决策协调 |
| **技术架构师** | 0.5 | 技术选型,风险评估 |
| **Flink开发** | 2.0 | 流处理,状态管理 |
| **后端开发** | 1.0 | 微服务,API |
| **DevOps** | 0.5 | 部署,监控 |
| **QA** | 1.0 | 测试,验证 |
| **安全分析师** | 0.5 | 规则优化,告警验证 |
| **eBPF专家** (可选) | 0.5 | 仅当决策#1=Go |
| **ML工程师** (可选) | 0.5 | 仅当决策#2=Go |
| **总计** | **7.0-8.0** | - |

---

## ✅ 总结

### MVP核心价值

1. **4个月交付** (vs 8个月完整版)
2. **成本减半** ($300K vs $600K)
3. **风险可控** (3个决策点控制高风险功能)
4. **价值确定** (30%检测提升,40%误报下降)

### 实施建议

```
✅ 立即启动 Phase 0 (Week 1)
✅ Week 4 必须完成决策点 #1 (蜜罐)
✅ 接受降级方案 = 接受务实主义
✅ MVP成功后再考虑Phase 2
```

### 风险提示

```
⚠️ 决策延迟会导致项目失控
⚠️ 功能蔓延会导致MVP失败
⚠️ 完美主义会导致永远无法交付
```

---

**文档版本**: 2.0 (MVP版)  
**最后更新**: 2025-10-13  
**下一步**: 管理层批准 → Week 1启动  
**关键原则**: **Done is better than perfect**
