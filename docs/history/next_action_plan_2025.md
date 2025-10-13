# 下一步行动方案 (2025年10月-11月)

## 总体目标
在未来2个月内完成数据摄入服务和流处理服务的完善，达到可以进行集成测试的阶段，为后续业务服务开发奠定坚实基础。

## 开发环境确认

### 环境要求 (必须满足)
- **操作系统**: Ubuntu 24.04.3 LTS (Noble Numbat)
- **Java版本**: OpenJDK 21.0.8 (Ubuntu官方构建)
- **Maven版本**: 3.8.7
- **Docker**: 20.10+ (支持Docker Compose v2)
- **Kubernetes**: kubectl + minikube (可选，用于K8s测试)

### 环境验证清单
```bash
# ✅ 必须通过的检查
lsb_release -a                    # Ubuntu 24.04.3 LTS
java -version                     # 21.0.8
mvn --version                     # 3.8.7
docker --version                  # 20.10+
docker-compose --version          # v2.x
kubectl version --client          # v1.25+
minikube version                  # v1.28+
```

### 快速环境检查脚本
```bash
#!/bin/bash
# save as check-env.sh and run: bash check-env.sh

echo "=== 威胁检测系统开发环境检查 ==="
echo

# Ubuntu版本检查
echo "1. Ubuntu版本检查:"
if lsb_release -a 2>/dev/null | grep -q "24.04.3"; then
    echo "✅ Ubuntu 24.04.3 LTS - 通过"
else
    echo "❌ 需要Ubuntu 24.04.3 LTS"
fi
echo

# Java版本检查
echo "2. Java版本检查:"
if java -version 2>&1 | grep -q "21.0.8"; then
    echo "✅ OpenJDK 21.0.8 - 通过"
else
    echo "❌ 需要OpenJDK 21.0.8"
fi
echo

# Maven版本检查
echo "3. Maven版本检查:"
if mvn --version | grep -q "3.8.7"; then
    echo "✅ Maven 3.8.7 - 通过"
else
    echo "❌ 需要Maven 3.8.7"
fi
echo

# Docker检查
echo "4. Docker检查:"
if docker --version >/dev/null 2>&1; then
    echo "✅ Docker已安装"
    if docker run hello-world >/dev/null 2>&1; then
        echo "✅ Docker权限正常"
    else
        echo "❌ Docker权限问题，请运行: sudo usermod -aG docker $USER"
    fi
else
    echo "❌ 需要安装Docker"
fi
echo

echo "=== 检查完成 ==="
```

## Phase 1: 技术栈统一和集成完善 (10月第2-4周)

### 🎯 阶段目标
解决技术栈不一致问题，完善核心服务集成，为后续开发奠定坚实基础。

### 📋 关键任务
**负责人**: 后端开发工程师
**时间**: 1周
**具体任务**:
- [ ] 完善LogParserService的错误处理逻辑
- [ ] 添加数据验证和清洗功能
- [ ] 实现批量日志处理接口
- [ ] 配置Kafka连接池和重试机制
- [ ] 添加监控指标 (Micrometer + Prometheus)

**验收标准**:
- 支持JSON和纯文本两种日志格式
- 解析成功率 > 95%
- 异常情况下的优雅降级

**技术指导**:
```java
// 1. 增强LogParserService错误处理
@Service
public class LogParserService {
    
    public Optional<Object> parseLog(String rawLog) {
        try {
            // 现有解析逻辑...
            
            // 新增: 数据验证
            if (!isValidLog(rawLog)) {
                log.warn("Invalid log format: {}", rawLog);
                return Optional.empty();
            }
            
            // 解析逻辑...
            
        } catch (Exception e) {
            // 详细错误日志
            log.error("Failed to parse log: {} with error: {}", 
                     rawLog, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    private boolean isValidLog(String rawLog) {
        // 验证日志格式的业务规则
        return rawLog != null && 
               !rawLog.trim().isEmpty() && 
               rawLog.contains("log_type=");
    }
}

// 2. 实现批量处理接口
@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {
    
    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponse> ingestBatch(
            @RequestBody List<String> logs) {
        
        List<IngestionResult> results = new ArrayList<>();
        int successCount = 0;
        
        for (String log : logs) {
            try {
                Optional<Object> parsedEvent = logParserService.parseLog(log);
                if (parsedEvent.isPresent()) {
                    // 发送到Kafka...
                    results.add(IngestionResult.success("event-id"));
                    successCount++;
                } else {
                    results.add(IngestionResult.error("Parse failed"));
                }
            } catch (Exception e) {
                results.add(IngestionResult.error(e.getMessage()));
            }
        }
        
        return ResponseEntity.ok(BatchIngestionResponse.builder()
                .total(logs.size())
                .successCount(successCount)
                .errorCount(logs.size() - successCount)
                .results(results)
                .build());
    }
}
```

### 1.2 性能优化和压力测试 (优先级: 高)
**负责人**: 后端开发工程师 + DevOps
**时间**: 1周
**具体任务**:
- [ ] 实现异步日志处理 (CompletableFuture)
- [ ] 优化Kafka生产者配置 (批量发送、压缩)
- [ ] 进行压力测试 (JMeter或自定义测试)
- [ ] 内存和CPU使用优化
- [ ] 数据库连接池配置 (如果需要)

**验收标准**:
- 吞吐量达到 1000 logs/秒
- 平均延迟 < 100ms
- 内存使用 < 512MB (峰值)
- CPU使用 < 70% (在满负载时)

**技术指导**:
```java
// 1. 异步处理优化
@Service
public class AsyncLogIngestionService {
    
    @Autowired
    private KafkaProducerService kafkaProducerService;
    
    @Async
    public CompletableFuture<IngestionResult> processLogAsync(String rawLog) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Object> parsedEvent = logParserService.parseLog(rawLog);
                if (parsedEvent.isPresent()) {
                    Object event = parsedEvent.get();
                    if (event instanceof AttackEvent) {
                        kafkaProducerService.sendAttackEvent((AttackEvent) event);
                    } else if (event instanceof StatusEvent) {
                        kafkaProducerService.sendStatusEvent((StatusEvent) event);
                    }
                    return IngestionResult.success("event-id");
                } else {
                    return IngestionResult.error("Parse failed");
                }
            } catch (Exception e) {
                log.error("Async processing failed: {}", e.getMessage());
                return IngestionResult.error(e.getMessage());
            }
        });
    }
}

// 2. Kafka生产者配置优化
@Configuration
public class KafkaConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                       "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                       StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                       JsonSerializer.class);
        
        // 性能优化配置
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
}
```

### 1.3 Docker和K8s部署完善 (优先级: 中)
**负责人**: DevOps工程师
**时间**: 0.5周
**具体任务**:
- [ ] 完善Dockerfile (多阶段构建、优化镜像大小)
- [ ] 配置Kubernetes Deployment和Service
- [ ] 添加健康检查和就绪探针
- [ ] 配置ConfigMap和Secret管理
- [ ] 实现滚动更新策略

**验收标准**:
- Docker镜像大小 < 200MB
- Kubernetes部署成功率 100%
- 支持水平扩展 (replicas: 3)

**技术指导**:
```dockerfile
# 多阶段构建Dockerfile
# services/data-ingestion/Dockerfile

# 构建阶段
FROM openjdk:21-jdk-slim as builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

# 运行阶段
FROM openjdk:21-jre-slim
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# Kubernetes配置
# k8s/base/data-ingestion.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-ingestion
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
        image: threat-detection/data-ingestion:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: data-ingestion
spec:
  selector:
    app: data-ingestion
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

## Phase 2: 流处理服务完善 (11月第1-3周)

### 2.1 状态管理和容错 (优先级: 高)
**负责人**: 流处理工程师
**时间**: 1.5周
**具体任务**:
- [ ] 完善Flink状态管理 (StateBackend配置)
- [ ] 实现Checkpoint机制
- [ ] 添加重启策略和容错处理
- [ ] 优化水位线和事件时间处理
- [ ] 实现Exactly-Once语义

**验收标准**:
- 状态大小控制在合理范围内
- Checkpoint成功率 > 99%
- 故障恢复时间 < 30秒

### 2.2 威胁算法优化和扩展 (优先级: 高)
**负责人**: 算法工程师 + 后端开发
**时间**: 1.5周
**具体任务**:
- [ ] 完善威胁评分算法 (考虑更多因素)
- [ ] 实现动态规则配置 (从Redis读取)
- [ ] 添加机器学习模型集成接口
- [ ] 优化时间权重计算逻辑
- [ ] 实现威胁等级分类

**验收标准**:
- 算法准确性与原系统差异 < 5%
- 支持实时规则更新
- 处理延迟 < 10秒

### 2.3 ClickHouse集成和查询优化 (优先级: 中)
**负责人**: 数据工程师
**时间**: 1周
**具体任务**:
- [ ] 设计和创建ClickHouse表结构
- [ ] 实现数据写入连接器 (Flink Connector)
- [ ] 优化查询性能 (索引、分区)
- [ ] 添加数据压缩和TTL策略
- [ ] 实现数据备份和恢复

**验收标准**:
- 写入性能 > 10,000 rows/秒
- 查询响应时间 < 1秒
- 数据压缩率 > 80%

## Phase 3: 基础设施和DevOps (贯穿始终)

### 3.1 监控和可观测性建设 (优先级: 中)
**负责人**: DevOps工程师
**时间**: 持续进行
**具体任务**:
- [ ] 配置Prometheus监控指标收集
- [ ] 创建Grafana仪表板 (业务+技术指标)
- [ ] 实现分布式追踪 (Jaeger)
- [ ] 配置日志聚合 (Fluentd + Elasticsearch)
- [ ] 设置告警规则和通知机制

**验收标准**:
- 核心指标监控覆盖率 100%
- 告警响应时间 < 5分钟
- 可观测性文档完善

### 3.2 CI/CD流水线建设 (优先级: 中)
**负责人**: DevOps工程师
**时间**: 2周
**具体任务**:
- [ ] 配置GitHub Actions工作流
- [ ] 实现自动化测试 (单元+集成)
- [ ] 配置镜像构建和推送
- [ ] 实现自动化部署到K8s
- [ ] 添加安全扫描和代码质量检查

**验收标准**:
- 代码提交到部署的时间 < 15分钟
- 测试覆盖率 > 80%
- 安全扫描通过率 100%

## Phase 4: 集成测试和验证 (11月第4周)

### 4.1 端到端集成测试 (优先级: 高)
**负责人**: 测试工程师 + 全团队
**时间**: 1周
**具体任务**:
- [ ] 搭建完整的集成测试环境
- [ ] 实现端到端测试用例 (日志→Kafka→Flink→ClickHouse)
- [ ] 验证数据一致性和完整性
- [ ] 性能基准测试和对比分析
- [ ] 故障注入测试和恢复验证

**验收标准**:
- 端到端延迟 < 30秒
- 数据准确性 > 99%
- 系统稳定性 (24小时无故障)

### 4.2 文档和知识转移 (优先级: 中)
**负责人**: 技术负责人
**时间**: 0.5周
**具体任务**:
- [ ] 更新架构文档和API文档
- [ ] 编写部署和运维手册
- [ ] 创建故障排查指南
- [ ] 进行团队内部培训
- [ ] 准备项目总结报告

**验收标准**:
- 文档覆盖率 100%
- 团队成员对系统理解达到80%

## 风险控制和应急计划

### 技术风险控制
1. **进度风险**: 每日站会跟踪，提前识别阻塞点
2. **质量风险**: 强制代码审查，自动化测试覆盖
3. **性能风险**: 分阶段性能测试，预留优化时间

### 应急预案
1. **关键路径阻塞**: 准备备用方案和技术支持
2. **人员缺勤**: 交叉培训，确保知识共享
3. **环境故障**: 多套环境备份，快速切换能力

## 资源需求

### 人力投入
- **后端开发**: 2人 (数据摄入 + 流处理)
- **DevOps**: 1人 (基础设施 + CI/CD)
- **测试**: 1人 (质量保证)
- **产品/项目管理**: 0.5人 (协调和进度跟踪)

### 环境需求
- **开发环境**: 本地Docker环境 (已具备)
- **测试环境**: Kubernetes集群 (需要准备)
- **性能测试环境**: 独立的高配环境 (需要申请)

### 预算需求
- **云资源**: 测试环境约 ¥30K/月
- **第三方工具**: 监控和测试工具约 ¥5K/月
- **培训**: 技术培训和认证约 ¥10K

## 里程碑和验收标准

### 里程碑1 (10月25日): 数据摄入服务完善
- [ ] 集成测试通过
- [ ] 性能测试达标
- [ ] Docker部署成功

### 里程碑2 (11月10日): 流处理服务完善
- [ ] 状态管理稳定
- [ ] 算法优化完成
- [ ] ClickHouse集成成功

### 里程碑3 (11月20日): 基础设施完善
- [ ] 监控体系搭建
- [ ] CI/CD流水线运行
- [ ] 集成测试通过

### 里程碑4 (11月30日): 项目总结和移交
- [ ] 完整文档交付
- [ ] 团队培训完成
- [ ] 下一步计划明确

## 沟通计划

### 内部沟通
- **每日站会**: 进度同步和问题解决 (15分钟)
- **周会**: 里程碑回顾和计划调整 (1小时)
- **月会**: 阶段总结和方向确认 (2小时)

### 外部沟通
- **业务方**: 双周汇报项目进展
- **管理层**: 月度汇报和关键里程碑汇报
- **合作伙伴**: 按需沟通技术集成事宜

## 成功衡量标准

### 技术指标
- 代码质量: 测试覆盖率 > 80%, 代码审查通过率 100%
- 性能指标: 达到设计目标的80%以上
- 稳定性: 系统可用性 > 99%

### 项目指标
- 进度达成: 里程碑完成率 100%
- 预算控制: 实际支出不超过预算的110%
- 团队满意度: 通过内部调查评估

### 业务指标
- 功能完整性: 核心功能按时交付
- 质量达标: 通过所有验收测试
- 文档完整性: 技术文档覆盖率 100%

---

*行动方案版本: v2.0*
*制定日期: 2025年10月9日*
*执行周期: 2025年10月-11月*
*预期成果: 核心服务达到生产就绪状态*

---

## 开发资源和模板

### 关键配置文件模板

#### application.yml (Spring Boot配置)
```yaml
server:
  port: 8080

spring:
  application:
    name: data-ingestion-service
  profiles:
    active: docker  # docker/k8s/prod

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1
      retries: 3
      batch-size: 16384
      linger-ms: 5
      compression-type: snappy
    consumer:
      group-id: threat-detection
      auto-offset-reset: earliest

# 自定义配置
app:
  kafka:
    topics:
      raw-logs: raw-logs
      attack-events: attack-events
      status-events: status-events

logging:
  level:
    com.threatdetection: INFO
    org.springframework.kafka: WARN
    org.apache.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 测试日志样例
```json
// 攻击事件测试样例
{
  "@timestamp": "2025-10-09T10:00:00.000Z",
  "host": "test-sensor-01",
  "message": "syslog_version=1.10.0,dev_serial=test-device-001,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6",
  "type": "threat-log"
}

// 状态事件测试样例
{
  "@timestamp": "2025-10-09T10:00:00.000Z",
  "host": "test-sensor-01", 
  "message": "syslog_version=1.10.0,dev_serial=test-device-001,log_type=2,sentry_count=5,real_host_count=10,dev_start_time=1728462000,log_time=1728465600",
  "type": "threat-log"
}
```

### 性能测试脚本

#### JMeter测试计划 (data-ingestion.jmx)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.5">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Data Ingestion Performance Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments"/>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_define_classpath" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments"/>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Ingestion Load Test">
        <intProp name="ThreadGroup.num_threads">10</intProp>
        <intProp name="ThreadGroup.ramp_time">30</intProp>
        <longProp name="ThreadGroup.duration">300</longProp>
        <longProp name="ThreadGroup.delay">0</longProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /api/v1/logs/ingest">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <stringProp name="HTTPSampler.path">/api/v1/logs/ingest</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
          <stringProp name="HTTPSampler.path">/api/v1/logs/ingest</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{"@timestamp":"2025-10-09T10:00:00.000Z","host":"test-sensor-01","message":"syslog_version=1.10.0,dev_serial=test-device-001,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6","type":"threat-log"}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 故障排查指南

#### 常见问题及解决方案

**问题1: Kafka连接失败**
```
错误: org.apache.kafka.common.errors.TimeoutException: Failed to update metadata
```
**解决方案**:
```bash
# 检查Kafka服务状态
docker-compose -f docker/docker-compose.yml ps kafka

# 检查Kafka日志
docker-compose -f docker/docker-compose.yml logs kafka

# 验证Kafka主题
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# 如果主题不存在，创建主题
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --topic attack-events --partitions 3 --replication-factor 1
```

**问题2: 应用启动失败**
```
错误: Port 8080 already in use
```
**解决方案**:
```bash
# 查找占用端口的进程
sudo lsof -i :8080

# 杀死进程或更改端口
# 在application.yml中修改server.port
```

**问题3: 内存不足**
```
错误: java.lang.OutOfMemoryError: Java heap space
```
**解决方案**:
```bash
# 增加JVM内存
java -Xmx2g -Xms1g -jar app.jar

# 或在Dockerfile中设置
ENV JAVA_OPTS="-Xmx2g -Xms1g"
```

**问题4: ClickHouse连接失败**
```
错误: ru.yandex.clickhouse.except.ClickHouseException: Connect to localhost:8123 failed
```
**解决方案**:
```bash
# 检查ClickHouse服务
docker-compose -f docker/docker-compose.yml ps clickhouse

# 检查端口映射
docker-compose -f docker/docker-compose.yml port clickhouse 8123

# 测试连接
curl http://localhost:8123/
```

### 代码质量检查清单

#### 提交前检查
- [ ] 所有单元测试通过 (`mvn test`)
- [ ] 代码覆盖率 > 80% (`mvn jacoco:report`)
- [ ] 代码风格检查通过 (`mvn spotless:check`)
- [ ] 安全扫描通过 (`mvn dependency-check:check`)
- [ ] 集成测试通过 (针对修改的功能)

#### 性能检查
- [ ] 内存泄漏检查 (使用VisualVM或JProfiler)
- [ ] 线程安全检查 (并发访问测试)
- [ ] 资源使用监控 (CPU/内存/网络)
- [ ] 错误日志监控 (异常处理完善)

#### 部署检查
- [ ] Docker镜像构建成功
- [ ] 容器健康检查正常
- [ ] Kubernetes部署成功
- [ ] 服务间通信正常

### 学习资源推荐

#### 官方文档
- [Spring Boot 3.1文档](https://docs.spring.io/spring-boot/docs/3.1.x/reference/html/)
- [Apache Kafka文档](https://kafka.apache.org/documentation/)
- [Apache Flink文档](https://nightlies.apache.org/flink/flink-docs-release-1.17/)
- [ClickHouse文档](https://clickhouse.com/docs)

#### 最佳实践
- [Spring Kafka指南](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
- [Docker最佳实践](https://docs.docker.com/develop/dev-best-practices/)
- [Kubernetes最佳实践](https://kubernetes.io/docs/concepts/configuration/overview/)

#### 调试工具
- [Kafka Tool](https://www.kafkatool.com/) - Kafka管理界面
- [ClickHouse客户端](https://clickhouse.com/docs/en/integrations/language-clients/java) - 数据库客户端
- [VisualVM](https://visualvm.github.io/) - JVM监控工具

---

**重要提醒**: 
- 开发前务必运行环境检查脚本
- 提交代码前完成所有质量检查
- 遇到问题先查阅故障排查指南
- 保持代码与文档同步更新

---

## 架构改进建议

### 🔧 技术栈统一和代码重构

#### 1. 共享模型库 (推荐: 高优先级)
**问题**: AttackEvent和StatusEvent在两个服务中重复定义
**解决方案**: 
- 创建`threat-detection-models`共享模块
- 使用Maven多模块结构统一管理
- 确保模型版本一致性和向后兼容

**实施步骤**:
```xml
<!-- 在根pom.xml中添加 -->
<modules>
  <module>shared-models</module>
  <module>services/data-ingestion</module>
  <module>services/stream-processing</module>
  <!-- ... -->
</modules>
```

#### 2. Java版本统一 (推荐: 高优先级)
**当前状态**: data-ingestion (Java 21) vs stream-processing (Java 11)
**建议方案**: 
- 统一升级到Java 21
- 测试Flink 1.17.1+在Java 21上的兼容性
- 如有兼容性问题，考虑升级Flink到1.18+或1.19+

#### 3. 配置中心化 (推荐: 中优先级)
**当前缺失**: 各服务配置分散，难以统一管理
**建议方案**:
- 引入Spring Cloud Config
- 实现配置热更新
- 支持多环境配置隔离

### 🏗️ 架构增强

#### 4. 服务间通信机制 (推荐: 高优先级)
**当前缺失**: 服务间没有标准化通信接口
**建议方案**:
- 定义服务间API契约
- 实现服务发现和注册
- 添加熔断和限流保护

#### 5. 事件驱动架构完善 (推荐: 中优先级)
**当前状态**: 仅实现基础Kafka集成
**建议方案**:
- 引入事件版本控制
- 实现事件模式演化
- 添加事件溯源能力

#### 6. 数据一致性保障 (推荐: 高优先级)
**当前风险**: 分布式系统数据一致性问题
**建议方案**:
- 实现Saga模式处理分布式事务
- 添加数据校验和修复机制
- 建立数据质量监控

### 📊 可观测性增强

#### 7. 分布式追踪 (推荐: 中优先级)
**当前缺失**: 请求链路追踪能力
**建议方案**:
- 集成OpenTelemetry
- 实现全链路追踪
- 添加业务指标监控

#### 8. 业务监控指标 (推荐: 高优先级)
**当前状态**: 仅技术指标监控
**建议方案**:
- 定义业务KPI指标
- 实现实时业务监控
- 建立业务告警体系

### 🔒 安全和合规

#### 9. API安全加固 (推荐: 高优先级)
**当前缺失**: API访问控制和安全防护
**建议方案**:
- 实现JWT认证授权
- 添加API限流和防护
- 建立安全审计日志

#### 10. 数据安全加密 (推荐: 中优先级)
**当前状态**: 数据传输和存储安全不足
**建议方案**:
- 实现数据传输加密
- 添加敏感数据脱敏
- 建立数据安全合规检查

### 🚀 性能和扩展性

#### 11. 缓存策略优化 (推荐: 中优先级)
**当前缺失**: 系统级缓存架构
**建议方案**:
- 实现多级缓存体系
- 添加缓存预热机制
- 建立缓存监控和失效策略

#### 12. 异步处理增强 (推荐: 高优先级)
**当前状态**: 同步处理为主，性能瓶颈明显
**建议方案**:
- 引入消息队列缓冲
- 实现异步处理模式
- 添加背压和流控机制

### 📋 实施路线图建议

#### Phase 1A: 基础重构 (1-2周)
- [ ] 创建共享模型库
- [ ] 统一Java版本到21
- [ ] 重构重复代码

#### Phase 1B: 架构完善 (2-3周)
- [ ] 实现服务间通信
- [ ] 添加配置中心
- [ ] 完善事件驱动架构

#### Phase 1C: 可观测性建设 (1-2周)
- [ ] 集成分布式追踪
- [ ] 完善监控指标
- [ ] 建立业务告警体系

#### Phase 1D: 安全加固 (1周)
- [ ] 实现API安全
- [ ] 添加数据加密
- [ ] 建立安全审计

**预期收益**:
- 代码复用率提升30%
- 系统可维护性显著改善
- 安全性和稳定性大幅提升
- 为后续扩展奠定坚实基础</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/next_action_plan_2025.md