# 云原生威胁检测系统开发速查表

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**系统版本**: v2.1

---

## 目录

1. [项目结构](#1-项目结构)
2. [命名规范](#2-命名规范)
3. [代码模板](#3-代码模板)
4. [常用命令](#4-常用命令)
5. [调试技巧](#5-调试技巧)
6. [性能基准](#6-性能基准)
7. [故障排查](#7-故障排查)

---

## 1. 项目结构

### 1.1 整体架构

```
threat-detection-system/
├── services/                    # 微服务代码
│   ├── data-ingestion/         # 日志摄取服务
│   ├── stream-processing/      # 流处理服务 (Flink)
│   ├── threat-assessment/      # 威胁评估服务
│   ├── alert-management/       # 告警管理服务
│   └── api-gateway/            # API网关
├── docker/                     # Docker配置
│   ├── docker-compose.yml      # 开发环境配置
│   └── *.sql                   # 数据库初始化脚本
├── docs/                       # 文档
├── frontend/                   # 前端应用
├── k8s/                        # Kubernetes配置
├── scripts/                    # 构建和部署脚本
└── pom.xml                     # Maven父项目
```

### 1.2 服务端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| Data Ingestion | 8080 | 日志摄取和解析 |
| Stream Processing | 8081 | Flink Web UI |
| API Gateway | 8082 | 统一API入口 |
| Threat Assessment | 8083 | 威胁评估API |
| Alert Management | 8084 | 告警管理API |
| Kafka | 9092 | Kafka内部端口 |
| Kafka External | 29092 | Kafka外部端口 |
| PostgreSQL | 5432 | 数据库端口 |
| Frontend | 3000 | React开发服务器 |

### 1.3 关键文件路径

```bash
# 核心服务代码
services/data-ingestion/src/main/java/com/threatdetection/data/
services/threat-assessment/src/main/java/com/threatdetection/assessment/
services/alert-management/src/main/java/com/threatdetection/alert/

# 配置文件
services/*/src/main/resources/application.yml
docker/docker-compose.yml

# 数据库脚本
docker/*.sql

# 前端代码
frontend/src/
```

---

## 2. 命名规范

### 2.1 包命名

```java
// 基础包结构
com.threatdetection.{service-name}

// 具体服务包
com.threatdetection.data          // 数据摄取
com.threatdetection.stream        // 流处理
com.threatdetection.assessment    // 威胁评估
com.threatdetection.alert         // 告警管理
com.threatdetection.gateway       // API网关
```

### 2.2 类命名

```java
// 服务类
@Slf4j
@Service
public class DataIngestionService { }

// 控制器类
@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController { }

// 数据模型
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackEvent { }

// 配置类
@Configuration
public class KafkaConfig { }

// 异常类
public class ThreatAssessmentException extends RuntimeException { }
```

### 2.3 方法命名

```java
// 服务方法
public void processLog(String syslogMessage) { }
public CompletableFuture<Void> sendToKafka(AttackEvent event) { }
public ThreatAlert assessThreat(AggregatedData data) { }

// 控制器方法
@PostMapping("/ingest")
public ResponseEntity<IngestionResponse> ingestLog(@RequestBody @Valid LogIngestionRequest request) { }

@GetMapping("/stats")
public ResponseEntity<IngestionStats> getStats() { }
```

### 2.4 变量命名

```java
// Java变量 (camelCase)
String customerId;
Instant timestamp;
int attackCount;

// JSON字段 (snake_case)
{
  "customer_id": "customer-001",
  "attack_count": 150,
  "threat_score": 125.5
}

// 环境变量 (UPPER_SNAKE_CASE)
KAFKA_BOOTSTRAP_SERVERS
SPRING_DATASOURCE_URL
```

### 2.5 Kafka主题命名

```java
// 主题命名 (kebab-case)
attack-events          // 攻击事件
minute-aggregations    // 分钟聚合
threat-alerts          // 威胁告警
```

### 2.6 数据库表命名

```sql
-- 表名 (snake_case)
threat_assessments
alert_notifications
customer_configs

-- 列名 (snake_case)
customer_id
threat_score
created_at
```

---

## 3. 代码模板

### 3.1 Spring Boot服务类模板

```java
package com.threatdetection.assessment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatAssessmentService {

    private final ThreatScoreCalculator calculator;
    private final ThreatAssessmentRepository repository;

    /**
     * 评估威胁等级
     * @param data 聚合攻击数据
     * @return 威胁评估结果
     */
    @CircuitBreaker(name = "threatAssessment", fallbackMethod = "fallbackAssessment")
    public ThreatAlert assessThreat(AggregatedAttackData data) {
        log.info("Assessing threat for customerId={}, attackMac={}",
                 data.getCustomerId(), data.getAttackMac());

        try {
            double score = calculator.calculate(data);
            ThreatLevel level = determineThreatLevel(score);

            ThreatAlert alert = ThreatAlert.builder()
                .customerId(data.getCustomerId())
                .attackMac(data.getAttackMac())
                .threatScore(score)
                .threatLevel(level)
                .attackCount(data.getAttackCount())
                .uniqueIps(data.getUniqueIps())
                .uniquePorts(data.getUniquePorts())
                .uniqueDevices(data.getUniqueDevices())
                .timestamp(Instant.now())
                .build();

            repository.save(alert);

            log.info("Threat assessment completed: customerId={}, score={}, level={}",
                     data.getCustomerId(), score, level);

            return alert;

        } catch (Exception e) {
            log.error("Failed to assess threat: customerId={}, error={}",
                      data.getCustomerId(), e.getMessage(), e);
            throw new ThreatAssessmentException("Assessment failed", e);
        }
    }

    public ThreatAlert fallbackAssessment(AggregatedAttackData data, Exception e) {
        log.warn("Using fallback assessment: customerId={}", data.getCustomerId());
        return ThreatAlert.builder()
            .threatLevel(ThreatLevel.UNKNOWN)
            .threatScore(0.0)
            .build();
    }

    private ThreatLevel determineThreatLevel(double score) {
        if (score >= 1000) return ThreatLevel.CRITICAL;
        if (score >= 500) return ThreatLevel.HIGH;
        if (score >= 200) return ThreatLevel.MEDIUM;
        if (score >= 50) return ThreatLevel.LOW;
        return ThreatLevel.INFO;
    }
}
```

### 3.2 REST控制器模板

```java
package com.threatdetection.assessment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/assessment")
@RequiredArgsConstructor
public class ThreatAssessmentController {

    private final ThreatAssessmentService service;

    @GetMapping("/threats")
    public ResponseEntity<PageResponse<ThreatAlert>> getThreats(
            @RequestParam String customerId,
            @RequestParam(required = false) ThreatLevel threatLevel,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.info("Getting threats: customerId={}, threatLevel={}, limit={}, offset={}",
                 customerId, threatLevel, limit, offset);

        List<ThreatAlert> threats = service.getThreats(customerId, threatLevel, limit, offset);
        int total = service.getThreatCount(customerId, threatLevel);

        PageResponse<ThreatAlert> response = PageResponse.<ThreatAlert>builder()
            .data(threats)
            .total(total)
            .page(offset / limit + 1)
            .size(limit)
            .hasMore((offset + limit) < total)
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/threats/{id}")
    public ResponseEntity<ThreatAlert> getThreat(@PathVariable Long id) {
        log.info("Getting threat by id: {}", id);

        ThreatAlert threat = service.getThreatById(id);
        if (threat == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(threat);
    }

    @PostMapping("/recommendations/{threatId}/execute")
    public ResponseEntity<RecommendationExecutionResult> executeRecommendation(
            @PathVariable Long threatId,
            @PathVariable Long recommendationId) {

        log.info("Executing recommendation: threatId={}, recommendationId={}",
                 threatId, recommendationId);

        RecommendationExecutionResult result = service.executeRecommendation(recommendationId);

        return ResponseEntity.ok(result);
    }
}
```

### 3.3 数据模型模板

```java
package com.threatdetection.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "threat_assessments")
public class ThreatAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "attack_mac", nullable = false)
    private String attackMac;

    @Column(name = "threat_score", nullable = false)
    private Double threatScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_level", nullable = false)
    private ThreatLevel threatLevel;

    @Column(name = "attack_count", nullable = false)
    private Integer attackCount;

    @Column(name = "unique_ips", nullable = false)
    private Integer uniqueIps;

    @Column(name = "unique_ports", nullable = false)
    private Integer uniquePorts;

    @Column(name = "unique_devices", nullable = false)
    private Integer uniqueDevices;

    @Column(name = "assessment_time", nullable = false)
    private Instant assessmentTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 3.4 Kafka消费者模板

```java
package com.threatdetection.assessment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreatAlertConsumer {

    private final ThreatAssessmentService service;

    @KafkaListener(
        topics = "threat-alerts",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "threatAlertKafkaListenerContainerFactory"
    )
    public void consumeThreatAlert(
            @Payload ThreatAlert alert,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received threat alert: customerId={}, attackMac={}, score={}, partition={}, offset={}",
                 alert.getCustomerId(), alert.getAttackMac(), alert.getThreatScore(), partition, offset);

        try {
            service.processThreatAlert(alert);
            acknowledgment.acknowledge();

            log.debug("Successfully processed threat alert: customerId={}, id={}",
                      alert.getCustomerId(), alert.getId());

        } catch (Exception e) {
            log.error("Failed to process threat alert: customerId={}, error={}",
                      alert.getCustomerId(), e.getMessage(), e);

            // 根据业务需求决定是否重新投递或发送到死信队列
            // acknowledgment.acknowledge(); // 确认消费
            // 或抛出异常让框架处理重试
            throw new ThreatAlertProcessingException("Failed to process threat alert", e);
        }
    }
}
```

### 3.5 单元测试模板

```java
package com.threatdetection.assessment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreatAssessmentServiceTest {

    @Mock
    private ThreatScoreCalculator calculator;

    @Mock
    private ThreatAssessmentRepository repository;

    @InjectMocks
    private ThreatAssessmentService service;

    @Test
    void testAssessThreat_Success() {
        // Given
        AggregatedAttackData data = createTestData();
        when(calculator.calculate(data)).thenReturn(125.5);
        when(repository.save(any(ThreatAlert.class))).thenAnswer(invocation -> {
            ThreatAlert alert = invocation.getArgument(0);
            alert.setId(1L);
            return alert;
        });

        // When
        ThreatAlert result = service.assessThreat(data);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getThreatScore()).isEqualTo(125.5);
        assertThat(result.getThreatLevel()).isEqualTo(ThreatLevel.HIGH);
        assertThat(result.getCustomerId()).isEqualTo("test-customer");
    }

    @Test
    void testAssessThreat_CalculatorFailure() {
        // Given
        AggregatedAttackData data = createTestData();
        when(calculator.calculate(data)).thenThrow(new RuntimeException("Calculation failed"));

        // When/Then
        assertThatThrownBy(() -> service.assessThreat(data))
            .isInstanceOf(ThreatAssessmentException.class)
            .hasMessage("Assessment failed");
    }

    private AggregatedAttackData createTestData() {
        return AggregatedAttackData.builder()
            .customerId("test-customer")
            .attackMac("AA:BB:CC:DD:EE:FF")
            .attackCount(100)
            .uniqueIps(3)
            .uniquePorts(5)
            .uniqueDevices(1)
            .timestamp(Instant.now())
            .build();
    }
}
```

---

## 4. 常用命令

### 4.1 Docker Compose命令

```bash
# 启动所有服务
docker-compose up -d

# 启动特定服务
docker-compose up -d data-ingestion threat-assessment

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
docker-compose logs -f data-ingestion --tail=100

# 重启服务
docker-compose restart data-ingestion

# 停止服务
docker-compose down

# 重新构建
docker-compose build --no-cache data-ingestion
docker-compose up -d data-ingestion

# 扩展服务实例
docker-compose up -d --scale threat-assessment=3
```

### 4.2 Maven命令

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn clean package -DskipTests

# 运行特定服务
cd services/data-ingestion
mvn spring-boot:run

# 查看依赖树
mvn dependency:tree

# 更新依赖
mvn versions:display-dependency-updates
```

### 4.3 Kafka命令

```bash
# 连接到Kafka容器
docker exec -it kafka bash

# 查看主题列表
kafka-topics --list --bootstrap-server localhost:9092

# 查看主题详情
kafka-topics --describe --topic attack-events --bootstrap-server localhost:9092

# 创建主题
kafka-topics --create --topic test-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# 消费消息
kafka-console-consumer --bootstrap-server localhost:9092 --topic attack-events --from-beginning

# 查看消费者组
kafka-consumer-groups --bootstrap-server localhost:9092 --list
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group threat-detection-group
```

### 4.4 PostgreSQL命令

```bash
# 连接到数据库
docker exec -it postgres psql -U postgres -d threat_detection

# 查看表结构
\d threat_assessments

# 查询数据
SELECT * FROM threat_assessments WHERE customer_id = 'customer-001' LIMIT 10;

# 查看表大小
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables WHERE schemaname = 'public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

# 备份数据库
pg_dump -U postgres threat_detection > backup.sql

# 恢复数据库
psql -U postgres threat_detection < backup.sql
```

### 4.5 Flink命令

```bash
# 提交作业
docker exec flink-jobmanager flink run -c com.threatdetection.stream.StreamProcessingJob /opt/flink/jobs/stream-processing.jar

# 查看作业状态
curl http://localhost:8081/jobs

# 取消作业
curl -X PATCH http://localhost:8081/jobs/{job-id} -d '{"targetState": "CANCELLED"}'

# 查看作业详情
curl http://localhost:8081/jobs/{job-id}
```

### 4.6 网络调试命令

```bash
# 测试服务连通性
curl http://localhost:8080/actuator/health
curl http://localhost:8083/api/v1/assessment/threats?customerId=test

# 查看端口占用
netstat -tlnp | grep :8080

# 测试Kafka连接
telnet localhost 9092

# 查看容器网络
docker network ls
docker network inspect threat-detection_default
```

---

## 5. 调试技巧

### 5.1 日志配置

```yaml
# application.yml
logging:
  level:
    com.threatdetection: DEBUG
    org.springframework.kafka: INFO
    org.apache.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - customerId=%X{customerId} - %msg%n"
```

### 5.2 远程调试

```bash
# JVM调试参数
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# IDEA远程调试配置
# Host: localhost
# Port: 5005
```

### 5.3 Kafka调试

```bash
# 查看消息内容
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --property print.key=true \
  --property key.separator=": "

# 查看消费者延迟
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group threat-detection-group
```

### 5.4 数据库调试

```sql
-- 查看慢查询
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - pg_stat_activity.query_start > interval '1 second'
ORDER BY duration DESC;

-- 查看索引使用情况
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- 查看表统计信息
ANALYZE threat_assessments;
SELECT * FROM pg_stat_user_tables WHERE schemaname = 'public';
```

### 5.5 性能分析

```bash
# JVM堆转储
jmap -dump:live,format=b,file=heap.hprof <pid>

# 线程转储
jstack <pid> > thread.dump

# 内存使用分析
jstat -gc <pid> 1000

# CPU分析
jstat -gccause <pid> 1000
```

---

## 6. 性能基准

### 6.1 吞吐量基准

| 组件 | 目标吞吐量 | 当前基准 | 测试方法 |
|------|-----------|---------|----------|
| Data Ingestion | 1000 events/s | 1200 events/s | JMeter压力测试 |
| Stream Processing | 1000 events/s | 1100 events/s | Flink Web UI |
| Threat Assessment | 100 queries/s | 150 queries/s | Apache Bench |
| Alert Management | 50 notifications/s | 75 notifications/s | 自定义测试 |

### 6.2 延迟基准

| 操作 | 目标延迟 | 当前基准 | 测试方法 |
|------|---------|---------|----------|
| 日志摄取 | < 10ms | 8ms | 应用指标 |
| 聚合计算 | < 30s | 25s | Flink指标 |
| 威胁评分 | < 2min | 1.8min | 端到端测试 |
| API响应 | < 100ms | 80ms | JMeter |
| 通知发送 | < 1s | 500ms | 应用指标 |

### 6.3 资源使用基准

| 服务 | CPU (cores) | 内存 (GB) | 磁盘 I/O |
|------|-------------|-----------|----------|
| Data Ingestion | 0.5 | 1.0 | 低 |
| Stream Processing | 2.0 | 4.0 | 中 |
| Threat Assessment | 1.0 | 2.0 | 中 |
| Alert Management | 0.5 | 1.0 | 低 |
| PostgreSQL | 1.0 | 2.0 | 高 |
| Kafka | 1.0 | 2.0 | 高 |

### 6.3 扩展策略

```yaml
# 水平扩展配置
services:
  threat-assessment:
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '1.0'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 1G
```

---

## 7. 故障排查

### 7.1 服务启动失败

**症状**: 容器无法启动或立即退出

**检查步骤**:
```bash
# 查看容器日志
docker-compose logs data-ingestion

# 检查端口冲突
netstat -tlnp | grep :8080

# 检查环境变量
docker exec data-ingestion env | grep KAFKA

# 检查依赖服务
docker-compose ps kafka postgres
```

**常见解决方案**:
- 检查配置文件语法
- 验证环境变量设置
- 确保依赖服务已启动
- 检查磁盘空间和权限

### 7.2 Kafka连接失败

**症状**: 服务日志显示Kafka连接错误

**检查步骤**:
```bash
# 检查Kafka状态
docker-compose ps kafka
docker-compose logs kafka

# 测试网络连接
docker exec data-ingestion telnet kafka 9092

# 检查主题是否存在
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 验证配置
docker exec data-ingestion cat /app/config/application.yml
```

**解决方案**:
```bash
# 重启Kafka
docker-compose restart kafka

# 重新创建主题
docker exec kafka kafka-topics --create --topic attack-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# 检查消费者组
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group threat-detection-group --reset-offsets --to-earliest --execute --all-topics
```

### 7.3 数据库连接失败

**症状**: 服务无法连接到PostgreSQL

**检查步骤**:
```bash
# 检查数据库状态
docker-compose ps postgres
docker-compose logs postgres

# 测试连接
docker exec postgres psql -U postgres -d threat_detection -c "SELECT 1;"

# 检查连接池配置
curl http://localhost:8083/actuator/metrics/db.connection.pool.active

# 查看数据库日志
docker-compose logs postgres | grep ERROR
```

**解决方案**:
```bash
# 重启数据库
docker-compose restart postgres

# 检查数据库初始化
docker exec postgres psql -U postgres -d threat_detection -c "\dt"

# 验证连接字符串
docker exec threat-assessment env | grep DATASOURCE
```

### 7.4 数据不一致

**症状**: Kafka有数据但数据库为空

**检查步骤**:
```bash
# 检查消费者状态
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group threat-detection-group

# 查看服务日志
docker-compose logs threat-assessment | grep ERROR

# 检查消息格式
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic threat-alerts --from-beginning --max-messages 1

# 验证序列化配置
docker exec threat-assessment cat /app/config/application.yml | grep serializer
```

**解决方案**:
```bash
# 重置消费者偏移量 (仅开发环境)
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group threat-detection-group --reset-offsets --to-earliest --execute --topic threat-alerts

# 重启消费者服务
docker-compose restart threat-assessment

# 检查消息格式兼容性
```

### 7.5 性能问题

**症状**: 服务响应慢或CPU/内存使用高

**检查步骤**:
```bash
# 查看系统资源
docker stats

# 检查JVM指标
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# 查看线程状态
jstack <pid> | head -50

# 检查数据库性能
docker exec postgres psql -U postgres -d threat_detection -c "SELECT * FROM pg_stat_activity;"

# 查看Kafka性能
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group threat-detection-group
```

**解决方案**:
- 增加JVM内存
- 优化数据库查询
- 增加服务实例
- 调整Kafka分区数

### 7.6 快速诊断脚本

```bash
#!/bin/bash
# diagnostics.sh

echo "=== System Diagnostics ==="

echo "1. Service Status:"
docker-compose ps

echo -e "\n2. Health Checks:"
for port in 8080 8083 8084; do
    status=$(curl -s http://localhost:$port/actuator/health | jq -r .status 2>/dev/null || echo "DOWN")
    echo "Port $port: $status"
done

echo -e "\n3. Kafka Topics:"
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null || echo "Kafka not accessible"

echo -e "\n4. Database Tables:"
docker exec postgres psql -U postgres -d threat_detection -c "\dt" 2>/dev/null || echo "Database not accessible"

echo -e "\n5. Recent Errors:"
docker-compose logs --since 5m | grep ERROR | tail -10

echo -e "\n=== End Diagnostics ==="
```

---

## 附录

### A. 常用端口速查

| 端口 | 服务 | 用途 |
|------|------|------|
| 8080 | Data Ingestion | 日志摄取API |
| 8081 | Flink | 流处理Web UI |
| 8082 | API Gateway | 统一API入口 |
| 8083 | Threat Assessment | 威胁评估API |
| 8084 | Alert Management | 告警管理API |
| 9092 | Kafka | Kafka内部 |
| 29092 | Kafka | Kafka外部 |
| 5432 | PostgreSQL | 数据库 |
| 2181 | Zookeeper | Kafka协调 |

### B. 环境变量速查

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 | Kafka连接 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://postgres:5432/threat_detection | 数据库连接 |
| `SPRING_PROFILES_ACTIVE` | development | Spring配置 |
| `FLINK_PARALLELISM` | 4 | Flink并行度 |
| `LOG_LEVEL` | INFO | 日志级别 |

### C. 快捷命令别名

```bash
# 添加到 ~/.bashrc 或 ~/.zshrc
alias dc='docker-compose'
alias dcl='docker-compose logs -f'
alias dcu='docker-compose up -d'
alias dcd='docker-compose down'
alias dcr='docker-compose restart'
alias dcb='docker-compose build --no-cache'

# 服务特定别名
alias logs-ingestion='docker-compose logs -f data-ingestion'
alias logs-assessment='docker-compose logs -f threat-assessment'
alias restart-all='docker-compose restart'
```

---

**文档结束**
kafka                   "/etc/confluent/dock…"   kafka               running             0.0.0.0:9092->9092/tcp, 0.0.0.0:29092->29092/tcp
postgres                "docker-entrypoint.s…"   postgres            running             0.0.0.0:5432->5432/tcp
data-ingestion          "java -jar app.jar"      data-ingestion      running             0.0.0.0:8080->8080/tcp
stream-processing       "/opt/flink/bin/sta…"   stream-processing   running             0.0.0.0:8081->8081/tcp
threat-assessment       "java -jar app.jar"      threat-assessment   running             0.0.0.0:8083->8083/tcp
alert-management        "java -jar app.jar"      alert-management    running             0.0.0.0:8084->8084/tcp
api-gateway             "java -jar app.jar"      api-gateway         running             0.0.0.0:8082->8082/tcp
```

### 1.3 验证安装

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health

# 检查Kafka主题
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 检查数据库连接
docker exec postgres psql -U postgres -d threat_detection -c "SELECT COUNT(*) FROM threat_assessments;"
```

---

## 2. 系统架构概述

### 2.1 核心组件

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Data Ingestion│    │  Stream Processing│   │ Threat Assessment│
│     (8080)      │    │     (Flink)      │   │     (8083)       │
│                 │    │                 │   │                 │
│ • 日志解析      │    │ • 实时聚合      │   │ • 威胁评估      │
│ • 数据验证      │    │ • 威胁评分      │   │ • 情报关联      │
│ • Kafka生产     │    │ • 窗口计算      │   │ • 缓解建议      │
└─────────┬───────┘    └─────────┬───────┘   └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │       Kafka集群         │
                    │    (9092, 29092)       │
                    │                        │
                    │ Topics:                │
                    │ • attack-events        │
                    │ • minute-aggregations  │
                    │ • threat-alerts        │
                    └────────────┬───────────┘
                                 │
                    ┌────────────▼────────────┐
                    │     PostgreSQL         │
                    │       (5432)           │
                    └────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Alert Management     │
                    │       (8084)           │
                    │                        │
                    │ • 多通道通知          │
                    │ • 告警去重            │
                    │ • 智能升级            │
                    └────────────────────────┘
```

### 2.2 数据流

1. **日志摄取**: rsyslog → Data Ingestion Service
2. **事件发布**: Data Ingestion → Kafka (attack-events)
3. **实时聚合**: Flink → Kafka (minute-aggregations)
4. **威胁评分**: Flink → Kafka (threat-alerts)
5. **评估存储**: Threat Assessment → PostgreSQL
6. **告警通知**: Alert Management → Email/SMS/Webhook

### 2.3 关键特性

- **实时处理**: < 4分钟端到端延迟
- **高可用**: 容器化部署，支持水平扩展
- **多租户**: 基于customerId的数据隔离
- **可观测性**: 健康检查、指标监控、结构化日志

---

## 3. API使用指南

### 3.1 基础信息

- **Base URL**: `http://localhost:8082/api/v1` (通过API网关)
- **认证**: 暂无 (开发环境)
- **数据格式**: JSON
- **字符编码**: UTF-8

### 3.2 Data Ingestion API

#### 3.2.1 单条日志摄取

```bash
POST /api/v1/logs/ingest
Content-Type: application/json

{
  "devSerial": "DEV-001",
  "logType": 1,
  "attackMac": "AA:BB:CC:DD:EE:FF",
  "attackIp": "192.168.1.100",
  "responseIp": "10.0.0.1",
  "responsePort": 3306,
  "customerId": "customer-001"
}
```

**响应**:
```json
{
  "success": true,
  "message": "Log ingested successfully",
  "eventId": "DEV-001_1705315800_1"
}
```

#### 3.2.2 批量日志摄取

```bash
POST /api/v1/logs/batch
Content-Type: application/json

[
  {
    "devSerial": "DEV-001",
    "logType": 1,
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "attackIp": "192.168.1.100",
    "responseIp": "10.0.0.1",
    "responsePort": 3306,
    "customerId": "customer-001"
  }
]
```

#### 3.2.3 获取摄取统计

```bash
GET /api/v1/logs/stats
```

**响应**:
```json
{
  "totalIngested": 1250,
  "successfulIngested": 1245,
  "failedIngested": 5,
  "lastIngestTime": "2024-01-15T10:30:00Z",
  "averageProcessingTimeMs": 45.2
}
```

### 3.3 Threat Assessment API

#### 3.3.1 查询威胁评估

```bash
GET /api/v1/assessment/threats?customerId=customer-001&limit=10&offset=0
```

**响应**:
```json
{
  "data": [
    {
      "id": 1,
      "customerId": "customer-001",
      "attackMac": "AA:BB:CC:DD:EE:FF",
      "threatScore": 125.5,
      "threatLevel": "HIGH",
      "attackCount": 150,
      "uniqueIps": 5,
      "uniquePorts": 3,
      "uniqueDevices": 2,
      "assessmentTime": "2024-01-15T10:32:00Z"
    }
  ],
  "total": 25,
  "page": 1,
  "size": 10
}
```

#### 3.3.2 获取特定威胁详情

```bash
GET /api/v1/assessment/threats/1
```

#### 3.3.3 获取缓解建议

```bash
GET /api/v1/assessment/recommendations/1
```

**响应**:
```json
{
  "id": 1,
  "assessmentId": 1,
  "action": "BLOCK_IP",
  "priority": "HIGH",
  "description": "Block the attacking IP address 192.168.1.100",
  "parameters": {
    "ip": "192.168.1.100",
    "duration": "24h"
  },
  "executed": false
}
```

### 3.4 Alert Management API

#### 3.4.1 查询告警

```bash
GET /api/v1/alerts?customerId=customer-001&status=NEW&limit=10
```

#### 3.4.2 发送Email通知

```bash
POST /api/v1/alerts/notify/email
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "admin@company.com",
  "subject": "High Threat Detected",
  "body": "A high threat has been detected from MAC AA:BB:CC:DD:EE:FF"
}
```

#### 3.4.3 发送SMS通知

```bash
POST /api/v1/alerts/notify/sms
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "recipient": "+1234567890",
  "message": "High threat detected from MAC AA:BB:CC:DD:EE:FF"
}
```

#### 3.4.4 发送Webhook通知

```bash
POST /api/v1/alerts/notify/webhook
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "webhookUrl": "https://hooks.slack.com/services/...",
  "payload": {
    "text": "High threat detected",
    "threatLevel": "HIGH",
    "attackMac": "AA:BB:CC:DD:EE:FF"
  }
}
```

#### 3.4.5 批量发送通知

```bash
POST /api/v1/alerts/notify/batch
Content-Type: application/json

{
  "alertId": 1,
  "customerId": "customer-001",
  "notifications": [
    {
      "channel": "EMAIL",
      "recipient": "admin@company.com"
    },
    {
      "channel": "SMS",
      "recipient": "+1234567890"
    }
  ]
}
```

### 3.5 健康检查API

#### 3.5.1 服务健康检查

```bash
GET /actuator/health
```

**响应**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 1073741824,
        "free": 536870912,
        "threshold": 10485760
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "kafka-cluster-1"
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "SELECT 1"
      }
    }
  }
}
```

#### 3.5.2 指标监控

```bash
GET /actuator/metrics
GET /actuator/metrics/jvm.memory.used
GET /actuator/metrics/kafka.producer.record.send.total
```

---

## 4. 数据流说明

### 4.1 完整数据流示例

```bash
# 1. 发送攻击事件
curl -X POST http://localhost:8082/api/v1/logs/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "devSerial": "DEV-001",
    "logType": 1,
    "attackMac": "AA:BB:CC:DD:EE:FF",
    "attackIp": "192.168.1.100",
    "responseIp": "10.0.0.1",
    "responsePort": 3306,
    "customerId": "customer-001"
  }'

# 2. 等待30秒聚合
sleep 30

# 3. 检查聚合结果 (通过Kafka控制台或API)
# minute-aggregations主题将包含聚合数据

# 4. 等待2分钟威胁评分
sleep 90

# 5. 检查威胁评估结果
curl http://localhost:8082/api/v1/assessment/threats?customerId=customer-001

# 6. 检查告警通知
curl http://localhost:8082/api/v1/alerts?customerId=customer-001
```

### 4.2 数据流时间线

```
时间点    | 事件                    | 数据状态
----------|-------------------------|----------
T=0      | 攻击事件到达            | attack-events主题
T=30s    | 30秒聚合完成            | minute-aggregations主题
T=2m     | 威胁评分完成            | threat-alerts主题
T=2m+    | 威胁评估存储            | PostgreSQL threat_assessments表
T=2m+    | 告警生成                | PostgreSQL alerts表
T=2m+    | 通知发送                | 外部通知渠道
```

### 4.3 数据验证

#### 4.3.1 Kafka消息验证

```bash
# 查看attack-events主题消息
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 5

# 查看threat-alerts主题消息
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic threat-alerts \
  --from-beginning \
  --max-messages 5
```

#### 4.3.2 数据库数据验证

```bash
# 连接数据库
docker exec -it postgres psql -U postgres -d threat_detection

# 查询威胁评估
SELECT * FROM threat_assessments WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;

# 查询告警
SELECT * FROM alerts WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;

# 查询通知
SELECT * FROM notifications WHERE customer_id = 'customer-001' ORDER BY created_at DESC LIMIT 5;
```

---

## 5. 威胁评分算法详解

### 5.1 算法公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
              × timeWeight × ipWeight × portWeight × deviceWeight
```

### 5.2 权重计算示例

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 当前时间示例 |
|--------|------|--------------|
| 0:00-5:00 | 1.2 | 02:00 → 1.2 (深夜异常) |
| 5:00-9:00 | 1.1 | 07:00 → 1.1 (早晨) |
| 9:00-17:00 | 1.0 | 14:00 → 1.0 (工作时间) |
| 17:00-21:00 | 0.9 | 18:00 → 0.9 (傍晚) |
| 21:00-24:00 | 0.8 | 22:00 → 0.8 (夜间) |

#### IP权重 (ipWeight)

```java
ipWeight = uniqueIps > 1 ? 2.0 : 1.0
```

#### 端口权重 (portWeight)

| 唯一端口数 | 权重 | 说明 |
|-----------|------|------|
| 1 | 1.0 | 单端口扫描 |
| 2-5 | 1.2 | 目标扫描 |
| 6-10 | 1.5 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 复杂攻击 |

#### 设备权重 (deviceWeight)

```java
deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0
```

### 5.3 评分示例

**场景**: 工作时间内，攻击者从3个不同IP扫描5个端口，攻击2台设备，共100次攻击

**计算过程**:
```
基础分数 = 100 × 3 × 5 = 1500
时间权重 = 1.0 (工作时间)
IP权重 = 2.0 (多IP)
端口权重 = 1.2 (2-5端口)
设备权重 = 1.5 (多设备)

threatScore = 1500 × 1.0 × 2.0 × 1.2 × 1.5 = 5400
威胁等级 = CRITICAL (5400 >= 1000)
```

### 5.4 威胁等级划分

| 等级 | 分数范围 | 颜色 | 说明 |
|------|----------|------|------|
| INFO | < 50 | 蓝色 | 信息级别 |
| LOW | 50-199 | 绿色 | 低危威胁 |
| MEDIUM | 200-499 | 黄色 | 中危威胁 |
| HIGH | 500-999 | 橙色 | 高危威胁 |
| CRITICAL | ≥ 1000 | 红色 | 严重威胁 |

---

## 6. Docker部署指南

### 6.1 开发环境部署

```bash
cd docker
docker-compose up -d
```

### 6.2 生产环境部署

```bash
# 使用生产配置
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 或使用Kubernetes
kubectl apply -k k8s/overlays/production
```

### 6.3 服务扩展

```bash
# 扩展Data Ingestion服务
docker-compose up -d --scale data-ingestion=3

# 扩展Threat Assessment服务
docker-compose up -d --scale threat-assessment=2
```

### 6.4 日志查看

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f data-ingestion

# 查看最近100行日志
docker-compose logs --tail=100 threat-assessment
```

### 6.5 容器管理

```bash
# 重启服务
docker-compose restart data-ingestion

# 重新构建服务
docker-compose build --no-cache data-ingestion
docker-compose up -d data-ingestion

# 清理未使用容器
docker system prune -f
```

---

## 7. 监控和故障排查

### 7.1 健康检查

```bash
# 检查所有服务健康状态
for port in 8080 8081 8083 8084 8082; do
  echo "Checking port $port..."
  curl -s http://localhost:$port/actuator/health | jq .status
done
```

### 7.2 性能监控

```bash
# JVM内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Kafka生产者指标
curl http://localhost:8080/actuator/metrics/kafka.producer.record.send.total

# 数据库连接池
curl http://localhost:8083/actuator/metrics/db.connection.pool.active
```

### 7.3 常见问题排查

#### 7.3.1 Kafka连接问题

**症状**: 服务无法连接到Kafka

**检查**:
```bash
# 检查Kafka状态
docker-compose ps kafka

# 检查Kafka日志
docker-compose logs kafka

# 测试连接
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**解决方案**:
```bash
# 重启Kafka
docker-compose restart kafka

# 重新创建主题
docker exec kafka kafka-topics --create --topic attack-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

#### 7.3.2 数据库连接问题

**症状**: 服务无法连接到PostgreSQL

**检查**:
```bash
# 检查数据库状态
docker-compose ps postgres

# 检查数据库日志
docker-compose logs postgres

# 测试连接
docker exec postgres psql -U postgres -d threat_detection -c "SELECT 1;"
```

**解决方案**:
```bash
# 重启数据库
docker-compose restart postgres

# 检查数据库初始化
docker exec postgres psql -U postgres -d threat_detection -c "\dt"
```

#### 7.3.3 Flink作业失败

**症状**: 流处理作业停止或失败

**检查**:
```bash
# 检查Flink状态
curl http://localhost:8081/overview

# 检查作业状态
curl http://localhost:8081/jobs

# 查看Flink日志
docker-compose logs stream-processing
```

**解决方案**:
```bash
# 重启Flink作业
docker-compose restart stream-processing

# 检查Kafka主题是否存在
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

#### 7.3.4 数据不一致

**症状**: Kafka有数据但数据库为空

**检查**:
```bash
# 检查消费者组状态
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group threat-detection-group

# 检查服务日志中的错误
docker-compose logs threat-assessment | grep ERROR
```

**解决方案**:
```bash
# 重置消费者组偏移量 (仅开发环境)
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group threat-detection-group --reset-offsets --to-earliest --execute --topic threat-alerts

# 重启消费者服务
docker-compose restart threat-assessment
```

### 7.4 日志分析

```bash
# 查看错误日志
docker-compose logs | grep ERROR

# 查看特定时间段日志
docker-compose logs --since "1h" data-ingestion

# 搜索特定错误
docker-compose logs | grep "Connection refused"
```

---

## 8. 性能优化

### 8.1 基准性能

| 指标 | 目标值 | 当前值 |
|------|--------|--------|
| 日志摄取吞吐量 | 1000+/秒 | 1200/秒 |
| 端到端延迟 | < 4分钟 | < 3.5分钟 |
| 威胁评估响应 | < 100ms | 80ms |
| 通知发送延迟 | < 1秒 | 500ms |
| 系统可用性 | > 99.9% | 99.95% |

### 8.2 优化建议

#### 8.2.1 Kafka优化

```yaml
# docker-compose.yml
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 6
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
    KAFKA_OFFSETS_RETENTION_MINUTES: 1440
    KAFKA_LOG_RETENTION_HOURS: 168
```

#### 8.2.2 Flink优化

```yaml
# application.yml
flink:
  parallelism: 4
  checkpointing:
    interval: 60000
  state:
    backend: rocksdb
```

#### 8.2.3 JVM优化

```bash
# JVM参数
JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 8.3 容量规划

| 负载级别 | CPU | 内存 | Kafka分区 | Flink并行度 |
|----------|-----|------|-----------|-------------|
| 小 (1000/秒) | 2核 | 4GB | 3 | 2 |
| 中 (5000/秒) | 4核 | 8GB | 6 | 4 |
| 大 (10000/秒) | 8核 | 16GB | 12 | 8 |

---

## 9. 常见问题

### 9.1 Q: 如何添加新客户？

**A**: 
```sql
-- 在数据库中添加客户映射
INSERT INTO device_customer_mapping (device_serial, customer_id, device_name) 
VALUES ('DEV-002', 'customer-002', 'New Device');

-- 重启相关服务
docker-compose restart data-ingestion threat-assessment alert-management
```

### 9.2 Q: 如何修改威胁评分权重？

**A**: 
```yaml
# 修改application.yml
app:
  threat-scoring:
    time-weights:
      "00-05": 1.3  # 提高深夜权重
    port-weights:
      "20+": 2.5    # 提高大范围扫描权重
```

### 9.3 Q: 如何添加新的通知渠道？

**A**: 在Alert Management服务中实现新的通知器：

```java
@Service
public class TeamsNotifier implements Notifier {
    @Override
    public NotificationResult send(NotificationRequest request) {
        // 实现Teams通知逻辑
    }
}
```

### 9.4 Q: 如何处理数据积压？

**A**: 
```bash
# 增加消费者实例
docker-compose up -d --scale threat-assessment=3

# 增加Kafka分区
docker exec kafka kafka-topics --alter --topic attack-events --partitions 6 --bootstrap-server localhost:9092

# 增加Flink并行度
# 修改application.yml中的flink.parallelism
```

### 9.5 Q: 如何备份和恢复数据？

**A**: 
```bash
# 备份数据库
docker exec postgres pg_dump -U postgres threat_detection > backup.sql

# 恢复数据库
docker exec -i postgres psql -U postgres threat_detection < backup.sql

# 备份Kafka数据 (如果需要)
# 注意: Kafka数据通常是临时性的，重新处理即可恢复
```

---

## 附录

### A. 端口映射表

| 服务 | 内部端口 | 外部端口 | 协议 |
|------|----------|----------|------|
| Data Ingestion | 8080 | 8080 | HTTP |
| Stream Processing | 8081 | 8081 | HTTP |
| API Gateway | 8082 | 8082 | HTTP |
| Threat Assessment | 8083 | 8083 | HTTP |
| Alert Management | 8084 | 8084 | HTTP |
| Kafka | 9092 | 9092 | TCP |
| Kafka (外部) | 29092 | 29092 | TCP |
| PostgreSQL | 5432 | 5432 | TCP |
| Zookeeper | 2181 | 2181 | TCP |

### B. 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | kafka:29092 | Kafka连接地址 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://postgres:5432/threat_detection | 数据库连接URL |
| `SPRING_PROFILES_ACTIVE` | development | Spring配置文件 |
| `FLINK_PARALLELISM` | 4 | Flink并行度 |

### C. 故障排查命令

```bash
# 快速健康检查
curl -s http://localhost:8080/actuator/health | jq .status
curl -s http://localhost:8083/actuator/health | jq .status
curl -s http://localhost:8084/actuator/health | jq .status

# 检查服务日志
docker-compose logs --tail=50 data-ingestion
docker-compose logs --tail=50 threat-assessment

# 检查Kafka主题
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 检查数据库表
docker exec postgres psql -U postgres -d threat_detection -c "\dt"
```

---

**文档结束**</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/reviewers/USAGE_GUIDE.md