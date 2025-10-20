# Phase 2 - 端口风险配置使用指南

**版本**: 2.0  
**状态**: ✅ 已完成  
**日期**: 2025-10-20

---

## 📖 目录

1. [功能概述](#功能概述)
2. [快速开始](#快速开始)
3. [数据库部署](#数据库部署)
4. [核心API文档](#核心api文档)
5. [权重计算详解](#权重计算详解)
6. [使用示例](#使用示例)
7. [性能优化](#性能优化)
8. [扩展指南](#扩展指南)
9. [常见问题](#常见问题)

---

## 功能概述

Phase 2在Phase 1基础评分引擎之上,增加了**端口风险配置系统**,实现:

### 核心特性

1. **50个预配置端口**: 涵盖高危/中危/低危端口
2. **混合权重策略**: 配置权重 + 多样性权重
3. **自动初始化**: 首次启动自动创建默认配置
4. **缓存优化**: Spring Cache减少数据库查询
5. **批量查询**: 一次查询多个端口
6. **高危端口筛选**: 快速定位高危端口

### 架构图

```
┌──────────────────────────────────────────┐
│         数据库表 (PostgreSQL)            │
│      port_risk_config (50个端口)         │
│    - portNumber (1-65535)               │
│    - riskScore (0.0-5.0)                │
│    - category, description              │
└──────────────┬───────────────────────────┘
               ↓
┌──────────────────────────────────────────┐
│      PortRiskService (服务层)           │
│  - 查询单个端口风险评分                  │
│  - 批量查询端口评分                      │
│  - 计算混合权重 (配置+多样性)            │
└──────────────┬───────────────────────────┘
               ↓
┌──────────────────────────────────────────┐
│  ThreatScoreCalculator (评分引擎)       │
│  - calculateEnhancedPortWeight()        │
│  - calculateThreatScore()               │
└──────────────────────────────────────────┘
```

---

## 快速开始

### 1. 数据库部署

```bash
# 方法1: Docker容器内执行
cd /home/kylecui/threat-detection-system
docker exec -i postgres psql -U postgres -d threat_detection < docker/init-db.sql.phase2

# 方法2: psql客户端执行
psql -h localhost -U postgres -d threat_detection -f docker/init-db.sql.phase2

# 验证
docker exec -it postgres psql -U postgres -d threat_detection -c "SELECT COUNT(*) FROM port_risk_config;"
# 预期输出: 50
```

### 2. 服务配置

在`application.yml`中启用缓存:

```yaml
spring:
  cache:
    type: simple  # 或使用 redis
    cache-names:
      - portRiskScores
      - portRiskBatch
```

### 3. 使用服务

```java
@Autowired
private PortRiskService portRiskService;

// 查询单个端口风险评分
double rdpScore = portRiskService.getPortRiskScore(3389);  // 3.0

// 批量查询
List<Integer> ports = Arrays.asList(3389, 445, 22);
Map<Integer, Double> scores = portRiskService.getBatchPortRiskScores(ports);

// 计算端口权重
double weight = portRiskService.calculatePortRiskWeight(ports, ports.size());
```

---

## 数据库部署

### 表结构

```sql
CREATE TABLE port_risk_config (
    id SERIAL PRIMARY KEY,
    port_number INTEGER NOT NULL UNIQUE CHECK (port_number >= 1 AND port_number <= 65535),
    port_name VARCHAR(100) NOT NULL,
    risk_score DECIMAL(5,2) NOT NULL CHECK (risk_score >= 0.0 AND risk_score <= 5.0),
    category VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_port_number ON port_risk_config(port_number);
CREATE INDEX idx_category ON port_risk_config(category);
CREATE INDEX idx_risk_score ON port_risk_config(risk_score);
```

### 查询示例

```sql
-- 查询RDP端口
SELECT * FROM port_risk_config WHERE port_number = 3389;

-- 查询所有高危端口 (评分 >= 2.5)
SELECT * FROM port_risk_config 
WHERE risk_score >= 2.5 
ORDER BY risk_score DESC;

-- 按分类统计
SELECT category, COUNT(*), AVG(risk_score) as avg_score
FROM port_risk_config
GROUP BY category
ORDER BY avg_score DESC;
```

### 添加自定义端口

```sql
-- 添加新端口
INSERT INTO port_risk_config 
(port_number, port_name, risk_score, category, description)
VALUES 
(8080, 'HTTP-Proxy', 1.5, 'WEB', 'HTTP代理端口');

-- 更新端口评分
UPDATE port_risk_config 
SET risk_score = 3.5, description = '关键漏洞端口'
WHERE port_number = 445;

-- 删除端口
DELETE FROM port_risk_config WHERE port_number = 8080;
```

---

## 核心API文档

### PortRiskService

#### 1. 查询单个端口风险评分

```java
/**
 * 查询单个端口的风险评分 (带缓存优化)
 * 
 * @param portNumber 端口号 (1-65535)
 * @return 风险评分 (0.0-5.0), 未配置端口返回默认值1.0
 */
@Cacheable(value = "portRiskScores", key = "#portNumber")
public double getPortRiskScore(int portNumber)
```

**示例**:
```java
double rdpScore = portRiskService.getPortRiskScore(3389);
// 返回: 3.0

double unknownScore = portRiskService.getPortRiskScore(12345);
// 返回: 1.0 (默认评分)
```

#### 2. 批量查询端口评分

```java
/**
 * 批量查询端口风险评分
 * 
 * @param portNumbers 端口号列表
 * @return Map<端口号, 风险评分>
 */
public Map<Integer, Double> getBatchPortRiskScores(List<Integer> portNumbers)
```

**示例**:
```java
List<Integer> ports = Arrays.asList(3389, 445, 22, 12345);
Map<Integer, Double> scores = portRiskService.getBatchPortRiskScores(ports);

// 结果:
// {3389=3.0, 445=3.0, 22=2.0, 12345=1.0}
```

#### 3. 计算端口权重 (混合策略)

```java
/**
 * 计算端口权重 (混合策略)
 * 
 * @param portNumbers 端口号列表
 * @param uniquePortCount 唯一端口数量
 * @return 端口权重 (1.0-2.0)
 */
public double calculatePortRiskWeight(List<Integer> portNumbers, int uniquePortCount)
```

**示例**:
```java
// 高危端口组合
List<Integer> highRiskPorts = Arrays.asList(3389, 445, 23);
double weight1 = portRiskService.calculatePortRiskWeight(highRiskPorts, 3);
// 返回: 1.534

// 低危端口但数量多
List<Integer> manyPorts = IntStream.range(1, 26).boxed().collect(Collectors.toList());
double weight2 = portRiskService.calculatePortRiskWeight(manyPorts, 25);
// 返回: 2.0
```

#### 4. 查询高危端口

```java
/**
 * 查询高危端口列表
 * 
 * @param threshold 风险阈值 (>=该阈值的端口)
 * @return 高危端口配置列表
 */
public List<PortRiskConfig> getHighRiskPorts(double threshold)
```

**示例**:
```java
// 查询评分>=2.5的端口
List<PortRiskConfig> highRiskPorts = portRiskService.getHighRiskPorts(2.5);
// 返回: [RDP, SMB, Telnet, Redis, FTP, ...]
```

#### 5. 初始化默认配置

```java
/**
 * 初始化50个默认端口配置
 * 
 * 自动检测: 如果已有配置则跳过
 */
@PostConstruct
public void initializeDefaultPorts()
```

**示例**:
```java
// 应用启动时自动调用
// 日志输出:
// INFO: Checking if port risk configurations are initialized...
// INFO: Initializing default port risk configurations...
// INFO: ✅ Initialized 50 default port risk configurations
```

### ThreatScoreCalculator (增强)

#### 增强的端口权重计算

```java
/**
 * 计算增强的端口权重 (Phase 2新方法)
 * 
 * 混合策略:
 * 1. 如果提供端口列表,使用PortRiskService计算配置权重
 * 2. 同时考虑端口多样性权重
 * 3. 取max(配置权重, 多样性权重)
 * 
 * @param portNumbers 端口号列表
 * @param uniquePorts 唯一端口数量
 * @return 端口权重 (1.0-2.0)
 */
public double calculateEnhancedPortWeight(List<Integer> portNumbers, int uniquePorts)
```

**示例**:
```java
@Autowired
private ThreatScoreCalculator calculator;

// 使用增强方法
List<Integer> ports = Arrays.asList(3389, 445, 22);
double enhancedWeight = calculator.calculateEnhancedPortWeight(ports, 3);
// 返回: 1.534

// 向后兼容: 仍可使用基础方法
double basicWeight = calculator.calculatePortWeight(3);
// 返回: 1.2
```

---

## 权重计算详解

### 混合策略算法

```java
最终权重 = max(配置权重, 多样性权重)
```

#### 配置权重计算

```java
配置权重 = 1.0 + (平均风险评分 / 5.0)

// 示例1: 高危端口组合 [RDP, SMB, SSH]
avgScore = (3.0 + 3.0 + 2.0) / 3 = 2.67
configWeight = 1.0 + (2.67 / 5.0) = 1.534

// 示例2: 低危端口组合 [HTTP, HTTPS]
avgScore = (1.5 + 1.2) / 2 = 1.35
configWeight = 1.0 + (1.35 / 5.0) = 1.27
```

#### 多样性权重计算

```java
if (uniquePorts >= 20) → 2.0
else if (uniquePorts >= 11) → 1.8
else if (uniquePorts >= 6) → 1.6
else if (uniquePorts >= 4) → 1.4
else if (uniquePorts >= 2) → 1.2
else → 1.0
```

### 实战场景

#### 场景1: APT横向移动 (高危端口组合)

```java
输入:
  portNumbers = [3389, 445, 139, 135]  // RDP + SMB + NetBIOS + RPC
  uniquePorts = 4

计算:
  端口评分: RDP(3.0), SMB(3.0), NetBIOS(2.5), RPC(2.5)
  平均评分 = (3.0 + 3.0 + 2.5 + 2.5) / 4 = 2.75
  配置权重 = 1.0 + (2.75 / 5.0) = 1.55
  多样性权重 = 1.4 (4个端口)
  最终权重 = max(1.55, 1.4) = 1.55

结果: ✅ 识别为高危攻击 (权重1.55 > 1.4)
```

#### 场景2: 勒索软件传播 (SMB扫描)

```java
输入:
  portNumbers = [445]  // 仅SMB端口
  uniquePorts = 1

计算:
  端口评分: SMB(3.0)
  平均评分 = 3.0
  配置权重 = 1.0 + (3.0 / 5.0) = 1.6
  多样性权重 = 1.0 (1个端口)
  最终权重 = max(1.6, 1.0) = 1.6

结果: ✅ 识别为高危攻击 (权重1.6 > 1.0)
```

#### 场景3: 全端口扫描 (低危端口但数量多)

```java
输入:
  portNumbers = [80, 443, 8080, ..., 共25个低危端口]
  uniquePorts = 25

计算:
  平均评分 = 1.3 (多数为低危端口)
  配置权重 = 1.0 + (1.3 / 5.0) = 1.26
  多样性权重 = 2.0 (25个端口)
  最终权重 = max(1.26, 2.0) = 2.0

结果: ✅ 识别为扫描行为 (权重2.0达到上限)
```

#### 场景4: Web服务探测 (HTTP/HTTPS)

```java
输入:
  portNumbers = [80, 443]
  uniquePorts = 2

计算:
  端口评分: HTTP(1.5), HTTPS(1.2)
  平均评分 = (1.5 + 1.2) / 2 = 1.35
  配置权重 = 1.0 + (1.35 / 5.0) = 1.27
  多样性权重 = 1.2 (2个端口)
  最终权重 = max(1.27, 1.2) = 1.27

结果: ✅ 识别为低危探测 (权重1.27略高于基础)
```

### 优势总结

| 策略 | 优势 | 场景 |
|------|------|------|
| **配置权重** | 识别高危端口组合 | APT攻击、勒索软件 |
| **多样性权重** | 识别大规模扫描 | 全端口扫描、探测 |
| **混合策略** | 全面覆盖威胁类型 | 所有攻击场景 |

---

## 使用示例

### 完整评分流程

```java
@Service
public class ThreatAssessmentService {
    
    @Autowired
    private ThreatScoreCalculator calculator;
    
    @Autowired
    private PortRiskService portRiskService;
    
    public ThreatAlert performAssessment(AggregatedAttackData data) {
        // 1. 提取端口列表 (实际场景需要从数据中获取)
        List<Integer> portNumbers = extractPortNumbers(data);
        
        // 2. 计算各项权重
        double timeWeight = calculator.calculateTimeWeight(data.getTimestamp());
        double ipWeight = calculator.calculateIpWeight(data.getUniqueIps());
        
        // 3. 使用增强的端口权重 (Phase 2)
        double portWeight = calculator.calculateEnhancedPortWeight(
            portNumbers, 
            data.getUniquePorts()
        );
        
        double deviceWeight = calculator.calculateDeviceWeight(data.getUniqueDevices());
        
        // 4. 计算威胁评分
        double threatScore = calculator.calculateThreatScore(data);
        
        // 5. 确定威胁等级
        ThreatLevel level = calculator.determineThreatLevel(threatScore);
        
        // 6. 生成告警
        return ThreatAlert.builder()
            .customerId(data.getCustomerId())
            .attackMac(data.getAttackMac())
            .threatScore(threatScore)
            .threatLevel(level)
            .timeWeight(timeWeight)
            .ipWeight(ipWeight)
            .portWeight(portWeight)
            .deviceWeight(deviceWeight)
            .build();
    }
}
```

### 查询端口风险

```java
@RestController
@RequestMapping("/api/port-risk")
public class PortRiskController {
    
    @Autowired
    private PortRiskService portRiskService;
    
    @GetMapping("/{port}")
    public ResponseEntity<PortRiskResponse> getPortRisk(@PathVariable int port) {
        double score = portRiskService.getPortRiskScore(port);
        return ResponseEntity.ok(new PortRiskResponse(port, score));
    }
    
    @PostMapping("/batch")
    public ResponseEntity<Map<Integer, Double>> batchQuery(@RequestBody List<Integer> ports) {
        Map<Integer, Double> scores = portRiskService.getBatchPortRiskScores(ports);
        return ResponseEntity.ok(scores);
    }
    
    @GetMapping("/high-risk")
    public ResponseEntity<List<PortRiskConfig>> getHighRiskPorts(
        @RequestParam(defaultValue = "2.5") double threshold
    ) {
        List<PortRiskConfig> ports = portRiskService.getHighRiskPorts(threshold);
        return ResponseEntity.ok(ports);
    }
}
```

---

## 性能优化

### 缓存配置

#### 1. Spring Cache (简单场景)

```yaml
spring:
  cache:
    type: simple
    cache-names:
      - portRiskScores
```

#### 2. Redis Cache (生产环境)

```yaml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
    database: 1
    cache:
      time-to-live: 3600000  # 1小时
```

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new StringRedisSerializer()
            ))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()
            ));
    }
}
```

### 批量查询优化

```java
// ❌ 低效: N次数据库查询
List<Double> scores = new ArrayList<>();
for (int port : portNumbers) {
    scores.add(portRiskService.getPortRiskScore(port));
}

// ✅ 高效: 1次数据库查询
Map<Integer, Double> scoreMap = portRiskService.getBatchPortRiskScores(portNumbers);
```

### 性能监控

```java
@Service
public class PortRiskService {
    
    @Timed(value = "port.risk.query", description = "Port risk query time")
    public double getPortRiskScore(int portNumber) {
        // 实现
    }
    
    @Timed(value = "port.risk.batch", description = "Port risk batch query time")
    public Map<Integer, Double> getBatchPortRiskScores(List<Integer> portNumbers) {
        // 实现
    }
}
```

---

## 扩展指南

### 添加更多端口

#### 方式1: SQL脚本

```sql
-- 添加Docker Swarm端口
INSERT INTO port_risk_config 
(port_number, port_name, risk_score, category, description)
VALUES 
(2377, 'Docker Swarm', 2.8, 'CONTAINER', 'Docker Swarm集群管理');

-- 批量添加
INSERT INTO port_risk_config 
(port_number, port_name, risk_score, category, description)
VALUES 
(8443, 'HTTPS-Alt', 1.5, 'WEB', '备用HTTPS端口'),
(9200, 'Elasticsearch', 2.5, 'DATABASE', 'Elasticsearch HTTP'),
(27017, 'MongoDB', 2.5, 'DATABASE', 'MongoDB数据库');
```

#### 方式2: 管理API

```java
@Service
public class PortRiskManagementService {
    
    @Autowired
    private PortRiskConfigRepository repository;
    
    public PortRiskConfig addPortConfig(PortRiskConfigRequest request) {
        PortRiskConfig config = PortRiskConfig.builder()
            .portNumber(request.getPortNumber())
            .portName(request.getPortName())
            .riskScore(request.getRiskScore())
            .category(request.getCategory())
            .description(request.getDescription())
            .build();
        
        return repository.save(config);
    }
}
```

### 扩展到完整219个端口

参考原系统端口配置表,批量导入:

```sql
-- 从CSV文件导入
COPY port_risk_config (port_number, port_name, risk_score, category, description)
FROM '/path/to/ports.csv'
DELIMITER ','
CSV HEADER;
```

### 自定义风险评分规则

```java
@Service
public class CustomPortRiskService extends PortRiskService {
    
    @Override
    public double getPortRiskScore(int portNumber) {
        // 自定义逻辑: 根据时间段动态调整评分
        double baseScore = super.getPortRiskScore(portNumber);
        
        LocalTime now = LocalTime.now();
        if (now.getHour() >= 0 && now.getHour() < 6) {
            // 深夜时段,提高风险评分
            return Math.min(baseScore * 1.2, 5.0);
        }
        
        return baseScore;
    }
}
```

---

## 常见问题

### Q1: 为什么使用混合权重策略?

**A**: 单一策略无法覆盖所有威胁场景:

- **仅配置权重**: 无法识别大量低危端口的扫描行为
- **仅多样性权重**: 无法识别单个高危端口的针对性攻击
- **混合策略**: 取max确保两种威胁都能被准确评分

### Q2: 未配置端口返回什么评分?

**A**: 返回默认评分`DEFAULT_RISK_SCORE = 1.0`,表示未知端口具有基础风险。

### Q3: 如何调整端口风险评分?

**A**: 直接更新数据库:

```sql
UPDATE port_risk_config 
SET risk_score = 3.5, description = '发现新漏洞,提高评分'
WHERE port_number = 445;
```

缓存会自动失效(如果使用TTL),或手动清除缓存。

### Q4: 端口权重计算的性能如何?

**A**: 
- **单次查询**: ~5ms (带缓存: ~0.1ms)
- **批量查询** (50个端口): ~10ms
- **权重计算**: ~1ms

总延迟: < 20ms (可忽略)

### Q5: 支持IPv6端口吗?

**A**: 端口号是协议无关的(1-65535),配置同时适用于IPv4和IPv6。

### Q6: 如何禁用端口风险配置?

**A**: 使用基础方法:

```java
// 使用基础多样性权重 (不查询端口配置)
double weight = calculator.calculatePortWeight(uniquePorts);
```

### Q7: 端口配置更新后如何生效?

**A**: 
1. **无缓存**: 立即生效
2. **Simple Cache**: 重启应用
3. **Redis Cache**: 等待TTL过期,或手动清除缓存

```java
@Autowired
private CacheManager cacheManager;

public void clearPortCache() {
    cacheManager.getCache("portRiskScores").clear();
}
```

### Q8: 测试环境如何快速验证?

**A**:

```bash
# 1. 部署数据库
docker exec -i postgres psql -U postgres -d threat_detection < docker/init-db.sql.phase2

# 2. 运行测试
cd services/threat-assessment
mvn test -Dtest=PortRiskServiceTest

# 3. 查询验证
docker exec -it postgres psql -U postgres -d threat_detection
SELECT * FROM port_risk_config WHERE port_number = 3389;
```

---

## 📞 支持与反馈

- **测试状态**: ✅ 10/10测试用例通过
- **文档版本**: 2.0
- **联系团队**: Security Team

---

**文档结束**

*最后更新: 2025-10-20*
