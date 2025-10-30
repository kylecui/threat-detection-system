# Threat Assessment Service - Phase 1 使用指南

**版本**: 2.0  
**更新日期**: 2025-10-16  
**状态**: ✅ Phase 1已完成,可用于测试

---

## 📋 目录

1. [快速开始](#快速开始)
2. [核心组件说明](#核心组件说明)
3. [评分算法使用](#评分算法使用)
4. [Kafka集成](#kafka集成)
5. [API端点](#api端点)
6. [测试指南](#测试指南)
7. [故障排查](#故障排查)

---

## 🚀 快速开始

### 前置条件

```bash
# 环境要求
- Java 21
- Maven 3.8+
- PostgreSQL 15+
- Kafka 3.4+
```

### 启动服务

```bash
# 1. 切换到服务目录
cd services/threat-assessment

# 2. 启动PostgreSQL (Docker)
docker-compose -f ../../docker/docker-compose.yml up -d postgres

# 3. 启动Kafka (Docker)
docker-compose -f ../../docker/docker-compose.yml up -d zookeeper kafka

# 4. 启动Threat Assessment服务
mvn spring-boot:run
```

### 验证服务启动

```bash
# 检查健康状态
curl http://localhost:8081/api/v1/assessment/health

# 预期响应
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

---

## 🧩 核心组件说明

### 1. ThreatScoreCalculator (评分计算器)

**职责**: 计算威胁评分和判定威胁等级

**核心方法**:
```java
// 计算威胁评分
double threatScore = calculator.calculateThreatScore(data);

// 判定威胁等级
String level = calculator.determineThreatLevel(threatScore);
```

**评分公式**:
```
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight (0.8-1.2)
            × ipWeight (1.0-2.0)
            × portWeight (1.0-2.0)
            × deviceWeight (1.0-1.5)
```

### 2. ThreatAssessmentService (核心评估服务)

**职责**: 执行威胁评估和持久化

**使用示例**:
```java
@Autowired
private ThreatAssessmentService assessmentService;

public void evaluateThreat() {
    AggregatedAttackData data = AggregatedAttackData.builder()
        .customerId("customer-001")
        .attackMac("04:42:1a:8e:e3:65")
        .attackIp("192.168.75.188")
        .attackCount(150)
        .uniqueIps(5)
        .uniquePorts(3)
        .uniqueDevices(2)
        .timestamp(Instant.now())
        .build();
    
    ThreatAssessment result = assessmentService.assessThreat(data);
    
    System.out.println("Threat Level: " + result.getThreatLevel());
    System.out.println("Threat Score: " + result.getThreatScore());
}
```

### 3. NewThreatAlertConsumer (Kafka消费者)

**职责**: 从Kafka接收威胁告警并触发评估

**Kafka主题**: `threat-alerts`

**消息格式**:
```json
{
  "customer_id": "customer-001",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "attack_count": 150,
  "unique_ips": 5,
  "unique_ports": 3,
  "unique_devices": 2,
  "timestamp": "2025-01-15T02:30:00Z"
}
```

**工作流程**:
```
Kafka (threat-alerts) 
  → NewThreatAlertConsumer.consumeThreatAlert()
  → ThreatAssessmentService.assessThreat()
  → PostgreSQL (threat_assessments)
```

---

## 🧮 评分算法使用

### 权重计算示例

#### 时间权重
```java
ThreatScoreCalculator calculator = new ThreatScoreCalculator();

// 深夜时段 (00:00-06:00)
Instant midnight = Instant.parse("2025-01-15T02:00:00Z");
double weight = calculator.calculateTimeWeight(midnight);
// 结果: 1.2

// 工作时间 (09:00-17:00)
Instant workHour = Instant.parse("2025-01-15T14:00:00Z");
double weight = calculator.calculateTimeWeight(workHour);
// 结果: 1.0
```

#### IP权重
```java
// 单一目标
calculator.calculateIpWeight(1);    // 1.0

// 小范围横向移动
calculator.calculateIpWeight(3);    // 1.3

// 大规模横向移动
calculator.calculateIpWeight(15);   // 2.0
```

#### 端口权重
```java
// 单端口攻击
calculator.calculatePortWeight(1);   // 1.0

// 中等扫描
calculator.calculatePortWeight(5);   // 1.4

// 全端口扫描
calculator.calculatePortWeight(50);  // 2.0
```

### 完整评分示例

```java
AggregatedAttackData data = AggregatedAttackData.builder()
    .customerId("customer-001")
    .attackMac("04:42:1a:8e:e3:65")
    .attackIp("192.168.75.188")
    .attackCount(150)
    .uniqueIps(5)
    .uniquePorts(3)
    .uniqueDevices(2)
    .timestamp(Instant.parse("2025-01-15T02:30:00Z"))
    .build();

ThreatScoreCalculator calculator = new ThreatScoreCalculator();
double score = calculator.calculateThreatScore(data);

// 计算过程:
// baseScore = 150 × 5 × 3 = 2250
// timeWeight = 1.2 (深夜)
// ipWeight = 1.5 (5个IP)
// portWeight = 1.2 (3个端口)
// deviceWeight = 1.5 (2个设备)
// finalScore = 2250 × 1.2 × 1.5 × 1.2 × 1.5 = 7290.0

String level = calculator.determineThreatLevel(score);
// 结果: "CRITICAL" (> 200)
```

---

## 📡 Kafka集成

### 生产环境配置

**application.yml**:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: threat-assessment-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    listener:
      ack-mode: manual
```

### 发送测试消息

```bash
# 使用Kafka控制台生产者发送测试消息
docker exec -it kafka kafka-console-producer --bootstrap-server localhost:9092 --topic threat-alerts

# 输入JSON消息 (粘贴以下内容并按Enter)
{"customer_id":"customer-001","attack_mac":"04:42:1a:8e:e3:65","attack_ip":"192.168.75.188","attack_count":150,"unique_ips":5,"unique_ports":3,"unique_devices":2,"timestamp":"2025-01-15T02:30:00Z"}
```

### 查看消费者日志

```bash
# 查看应用日志
tail -f logs/threat-assessment.log | grep "Processing threat alert"

# 预期输出:
# INFO  - 📨 Processing threat alert: customerId=customer-001, attackMac=04:42:1a:8e:e3:65, score=0.0, level=null
# INFO  - ✅ Threat alert processed successfully: customerId=customer-001, attackMac=04:42:1a:8e:e3:65
```

---

## 🌐 API端点

### 1. 健康检查

```bash
GET http://localhost:8081/api/v1/assessment/health

# 响应
{
  "status": "UP"
}
```

### 2. 查询评估统计

```bash
GET http://localhost:8081/api/v1/assessment/statistics?customerId=customer-001

# 响应
{
  "totalAssessments": 100,
  "criticalCount": 5,
  "highCount": 15,
  "mediumCount": 30,
  "lowCount": 40,
  "infoCount": 10,
  "averageThreatScore": 125.5
}
```

### 3. 查询威胁趋势

```bash
GET http://localhost:8081/api/v1/assessment/trends?customerId=customer-001&hours=24

# 响应
{
  "dataPoints": [
    {
      "timestamp": "2025-01-15T00:00:00Z",
      "count": 15,
      "averageScore": 45.2
    },
    ...
  ]
}
```

### 4. 获取评估详情

```bash
GET http://localhost:8081/api/v1/assessment/{id}

# 响应
{
  "id": 1,
  "customerId": "customer-001",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "threatScore": 7290.0,
  "threatLevel": "CRITICAL",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "assessmentTime": "2025-01-15T02:30:00Z"
}
```

---

## 🧪 测试指南

### 单元测试

```bash
# 运行所有测试
cd services/threat-assessment
mvn test

# 只运行评分计算器测试
mvn test -Dtest=ThreatScoreCalculatorTest

# 预期输出:
# [INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
```

### 集成测试 (手动)

#### 测试场景1: CRITICAL级别威胁

```bash
# 1. 启动服务
mvn spring-boot:run

# 2. 发送Kafka消息
docker exec -it kafka kafka-console-producer --bootstrap-server localhost:9092 --topic threat-alerts

# 3. 输入测试数据 (深夜大规模横向移动)
{"customer_id":"test-001","attack_mac":"04:42:1a:8e:e3:65","attack_ip":"192.168.75.188","attack_count":150,"unique_ips":5,"unique_ports":3,"unique_devices":2,"timestamp":"2025-01-15T02:30:00Z"}

# 4. 查询评估结果
curl http://localhost:8081/api/v1/assessment/assessments?customerId=test-001

# 预期: threatLevel="CRITICAL", threatScore≈7290.0
```

#### 测试场景2: MEDIUM级别威胁

```bash
# 输入测试数据 (工作时间单目标探测)
{"customer_id":"test-001","attack_mac":"aa:bb:cc:dd:ee:ff","attack_ip":"10.0.1.50","attack_count":30,"unique_ips":2,"unique_ports":1,"unique_devices":1,"timestamp":"2025-01-15T14:30:00Z"}

# 预期: threatLevel="MEDIUM", threatScore≈78.0
```

### 数据库验证

```bash
# 连接PostgreSQL
docker exec -it postgres psql -U threat_user -d threat_detection

# 查询评估记录
SELECT id, customer_id, attack_mac, threat_level, threat_score, 
       attack_count, unique_ips, unique_ports, unique_devices,
       assessment_time
FROM threat_assessments
ORDER BY assessment_time DESC
LIMIT 10;

# 预期输出:
# id | customer_id | attack_mac        | threat_level | threat_score | ...
# 1  | test-001    | 04:42:1a:8e:e3:65 | CRITICAL     | 7290.00      | ...
# 2  | test-001    | aa:bb:cc:dd:ee:ff | MEDIUM       | 78.00        | ...
```

---

## 🔧 故障排查

### 问题1: Kafka消费者无法连接

**症状**:
```
ERROR - Failed to connect to Kafka: Connection refused
```

**排查步骤**:
```bash
# 1. 检查Kafka是否运行
docker ps | grep kafka

# 2. 检查Kafka端口
netstat -an | grep 9092

# 3. 检查配置
cat services/threat-assessment/src/main/resources/application.yml | grep bootstrap-servers

# 4. 重启Kafka
docker-compose -f docker/docker-compose.yml restart kafka
```

---

### 问题2: 评分计算结果为0

**症状**:
```
threatScore = 0.0
```

**排查步骤**:
```bash
# 1. 检查数据完整性
# 确保所有字段都不为null
data.isValid()  // 应返回true

# 2. 检查日志
tail -f logs/threat-assessment.log | grep "Invalid aggregated data"

# 3. 验证字段值
# 确保: attackCount > 0, uniqueIps > 0, uniquePorts > 0, uniqueDevices > 0
```

---

### 问题3: 数据库连接失败

**症状**:
```
ERROR - Unable to obtain JDBC Connection
```

**排查步骤**:
```bash
# 1. 检查PostgreSQL是否运行
docker ps | grep postgres

# 2. 检查数据库表是否存在
docker exec -it postgres psql -U threat_user -d threat_detection -c "\dt"

# 3. 检查连接配置
cat services/threat-assessment/src/main/resources/application.yml | grep datasource

# 4. 重启PostgreSQL
docker-compose -f docker/docker-compose.yml restart postgres
```

---

## 📊 Prometheus指标查询

### 查看评估指标

```bash
# 评估总数
curl http://localhost:8081/actuator/prometheus | grep threat_assessment_total

# CRITICAL威胁数
curl http://localhost:8081/actuator/prometheus | grep threat_assessment_critical

# Kafka消费指标
curl http://localhost:8081/actuator/prometheus | grep kafka_threat_alerts
```

---

## 📚 相关文档

- **完整API文档**: [threat_assessment_evaluation_api.md](../docs/api/threat_assessment_evaluation_api.md)
- **系统概述**: [threat_assessment_overview.md](../docs/api/threat_assessment_overview.md)
- **Phase 1总结**: [phase1_completion_summary.md](../docs/phase1_completion_summary.md)
- **重构计划**: [threat_assessment_refactor_plan.md](../docs/threat_assessment_refactor_plan.md)

---

**文档结束**

*最后更新: 2025-10-16*
